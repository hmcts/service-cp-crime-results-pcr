# 010. Scale the Service Bus completeness-retry ceiling to PCR's own failure mode

**Status:** Accepted, 28 Aug 2026

**Jira:** AMP-1030 — extending the retry schedule ADR-009 deferred as out of scope

## Context

ADR-009 relocated `ResultsIngestionService`'s completeness check onto a scheduled Service Bus
message, but deliberately left the retry ceiling unrevised — the consumer dead-lettered at
`ResultsIngestionService.MAX_COMPLETENESS_RETRIES` (3), a constant that exists to bound the
*synchronous webhook path's* in-process blocking retry (~14s total), not to size the queue path's
own escalation window. Reusing it for the queue path meant a message could reach the DLQ after
three retries spanning only seconds — needing manual intervention for what may just be ordinary
viewstore replication lag.

A prior attempt (PR #91) proposed mirroring `service-cp-crime-hearing-results-document-subscription`
(HRDS)'s exact schedule verbatim — `retry-durations` of 24 entries tailing off at 4h, `max-tries`
108, a ~14.5-day worst-case window. That PR was closed pending further discussion: HRDS's schedule
is tuned for a downed *external subscriber callback*, a days-long-outage failure mode. PCR's actual
completeness failure mode — the REST viewstore hasn't caught up with Redis yet — is a
seconds-to-low-minutes problem, not a days problem.

## Decision

`service-bus.retry-durations` and a new `service-bus.max-tries` are both configurable, decoupled
from `ResultsIngestionService.MAX_COMPLETENESS_RETRIES` (which stays unchanged and keeps governing
the webhook path only):

- `retry-durations`: `0s,1s,2s,5s,10s,30s,1m,2m,5m,5m,5m,10m,10m,30m,30m,1h` (16 entries, same
  graduated fast-then-slow shape as HRDS's schedule).
- `max-tries`: `24` — after the 16 listed durations, `ServiceBusRetryService` clamps to the last
  configured delay (1h) for the remaining 8 tries.
- Total worst-case window before dead-lettering: ~10.6 hours, not HRDS's ~14.5 days.

This is deliberately not a mirror of HRDS's numbers — it mirrors the *shape* (graduated,
self-healing, tunable via config) while sizing the *ceiling* to PCR's own failure mode. A message
still failing after half a working day is very unlikely to be replication lag and is worth a human
looking at the DLQ; a message failing after fourteen days is a false sense of self-healing that
just delays that same human ever finding out.

`HearingResultedServiceBusConsumer.handleIncomplete()` reads `properties.getMaxTries()` at
dead-letter-decision time rather than a compile-time constant, so the ceiling is tunable per
environment via `SERVICE_BUS_MAX_TRIES` without a code change.
