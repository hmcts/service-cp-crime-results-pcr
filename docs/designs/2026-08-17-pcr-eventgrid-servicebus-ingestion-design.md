# PCR Event Grid → Service Bus Ingestion Design

**Status:** Agreed, 25 Aug 2026.
**ADR:** [009-AMP-1030](../pipeline/adrs/009-AMP-1030-pcr-eventgrid-servicebus-queue-ingestion.md) — Accepted.

**Context:** `pcr-eventgrid-relay-function` (the Function App relaying `Hearing_Resulted` to
`/internal/hearing-results`) is being retired. This document replaces it: Event Grid delivers
directly to a Service Bus **Queue** owned solely by this service, `pcr.hearing-resulted`, via its
own independent Event Grid event subscription.

**Cutover is staged, not immediate** — `pcr-eventgrid-relay-function` stays live in production
through the coming release; the Service Bus path is built and proven in parallel, behind a switch,
before either environment cuts over.

**Headline decisions:**

- **One dedicated Service Bus Queue per consuming service.** PCR owns `pcr.hearing-resulted`
  outright; NOW will own its own separate queue when built. No Service Bus resource is shared
  between them.
- **Each queue is fed by its own independent Event Grid event subscription** off the same
  `Hearing_Resulted` source event — Event Grid's native support for multiple event subscriptions
  provides the fan-out.
- **Payload unchanged:** `hearingId`, `hearingDay`, `userId` — the same
  `HearingResultedWebhookEventData` fields the relay already forwards. No new shared key.

---

## 1. Retry mechanism

**Native `maxDeliveryCount` + dead-lettering.** Event Grid delivers straight to PCR's own
`pcr.hearing-resulted` queue — no relay code in between. PCR:

- Receives via **peek-lock**.
- `complete()` on success.
- Throws / lets the lock expire on failure → Service Bus redelivers natively, no app code
  involved.
- `maxDeliveryCount` exceeded → auto-moved to the queue's own dead-letter sub-queue (durable,
  queryable — not silently lost).

---

## 2. Proposed architecture

```mermaid
flowchart LR
    EG["Azure Event Grid<br/>Hearing_Resulted topic"]
    Q["Service Bus Queue<br/>pcr.hearing-resulted"]
    DLQ["Dead-letter sub-queue"]
    Consumer["service-cp-crime-results-pcr"]
    Ingest["ResultsIngestionService<br/>ingestAndPersist — unchanged"]

    EG -->|"PCR's own Event Grid event subscription"| Q
    Q -->|"peek-lock"| Consumer
    Consumer -->|"complete on success"| Q
    Consumer -->|"abandon or throw on outright failure"| Q
    Consumer -->|"complete + schedule retry on incomplete data"| Q
    Q -.->|"maxDeliveryCount exceeded, or explicit dead-letter"| DLQ
    Consumer --> Ingest
```

NOW's own queue and Event Grid event subscription, when built, are entirely separate resources
provisioned on its own timeline — nothing here depends on or coordinates with PCR's.

One of three outcomes per message, all handled on PCR's own queue:

1. **Success** — hearing data complete, PCR persisted it. `complete()`.
2. **Outright failure** — unexpected break. Consumer abandons; native redelivery retries,
   then dead-letters once `maxDeliveryCount` is exceeded.
3. **Incomplete data** — not ready yet. Consumer completes the message and schedules a
   follow-up.

Scoped to the ingress/trigger mechanism only — generation gate, persistence, `GET /pcr`, and the
public contract are unaffected.

### 2.1 Security

- Event Grid → Service Bus: a system-assigned managed identity on PCR's own Event Grid event
  subscription, granted `Azure Service Bus Data Sender` on `pcr.hearing-resulted`.
- Service Bus access via Managed Identity.

### 2.2 Provisioning

**Both the Event Grid event subscription and the Service Bus queue are provisioned via
Terraform** (IaC). The app never creates the queue itself — at startup it only verifies the queue
exists (`ServiceBusProvisioningService.queueExists`) and fails startup if it doesn't, naming
Terraform as the expected owner. Uses the existing shared per-environment Service Bus namespace —
only the queue itself belongs solely to PCR, not the namespace.

| Property | Value for `pcr.hearing-resulted` | Why |
| --- | --- | --- |
| Receive mode | Peek-lock (client-side, not a queue property) | Required for at-least-once delivery and for `maxDeliveryCount`/dead-lettering to apply |
| `LockDuration` | `1 minute` | Only needs to cover one completeness check |
| `MaxDeliveryCount` | `10` | Native redelivery for outright failures — separate from `service-bus.max-tries` below |
| `DefaultMessageTimeToLive` | `10 minutes` | Fine for a first-attempt message, too short for the retry tail — see below |
| `DeadLetteringOnMessageExpiration` | `true` | Without it an expired message is deleted with no trace |

**Completeness retry** — a non-blocking schedule, separate from `ResultsIngestionService`'s own
2s/4s/8s in-process retry (that one only governs the synchronous webhook path, ~14s total).
On an incomplete result the consumer completes the message and sends one scheduled follow-up
(`ScheduledEnqueueTimeUtc`), carrying the attempt count. `service-bus.retry-durations`
(`0s,1s,2s,5s,10s,30s,1m,2m,5m,5m,5m,10m,10m,30m,30m,1h`) sets each delay; `service-bus.max-tries`
(24) sets when to give up and dead-letter explicitly. Sized for PCR's own failure mode — viewstore
replication lag, minutes not days — not copied from HRDS's schedule (108 tries, 4h tail, ~14.5
days), which is tuned for a downed external subscriber. Worst case here is ~10.6 hours.

Follow-up messages set their own 24-hour TTL rather than inheriting the queue's 10-minute default —
otherwise a longer-delayed retry could auto-expire into the DLQ before `max-tries` ever gets to
decide.

### 2.3 Local dev / test story

Matches this repo's real-infrastructure testing convention (`PostgresInitialise`, `docker-compose`
Redis) — same discipline for Service Bus: **the official Azure Service Bus emulator**, run via
`docker-compose`, provisioning the same `pcr.hearing-resulted` queue as production.

---

## 3. Sequence diagrams

### 3.1 Happy path — Redis cache hit

```mermaid
sequenceDiagram
    participant EG as Event Grid
    participant Q as pcr.hearing-resulted queue
    participant C as service-cp-crime-results-pcr
    participant R as Redis
    participant P as Generation gate + Persistence

    EG->>Q: Hearing_Resulted event (hearingId, hearingDay, userId)
    Q->>C: deliver
    C->>R: check hearing cache
    R-->>C: complete hearing data
    C->>P: ingestAndPersist
    P-->>C: persisted
    C->>Q: complete()
```

### 3.2 Completeness retry, then success

```mermaid
sequenceDiagram
    participant EG as Event Grid
    participant Q as pcr.hearing-resulted queue
    participant C as service-cp-crime-results-pcr
    participant R as Redis
    participant RQ as Results Query API
    participant P as Generation gate + Persistence

    EG->>Q: Hearing_Resulted event (hearingId, hearingDay, userId)
    Q->>C: deliver, attempt 1
    C->>R: check hearing cache
    R-->>C: miss
    C->>RQ: GET hearing details
    RQ-->>C: incomplete
    C->>Q: complete() original message
    C->>Q: schedule follow-up message (now + 2s, attempt = 2)
    Note over Q: message parked on the same queue until its scheduled time
    Q->>C: deliver follow-up message, attempt 2
    C->>R: check hearing cache
    R-->>C: complete hearing data
    C->>P: ingestAndPersist
    P-->>C: persisted
    C->>Q: complete()
```

### 3.3 Completeness retries exhausted — explicit dead-letter

```mermaid
sequenceDiagram
    participant Q as pcr.hearing-resulted queue
    participant C as service-cp-crime-results-pcr
    participant R as Redis
    participant RQ as Results Query API
    participant DLQ as Dead-letter sub-queue (pcr.hearing-resulted)

    Q->>C: deliver, attempt 1
    C->>R: check hearing cache
    R-->>C: miss
    C->>RQ: GET hearing details
    RQ-->>C: incomplete
    C->>Q: complete() + schedule next follow-up (2s)
    Q->>C: deliver follow-up, attempt 2
    C->>RQ: GET hearing details
    RQ-->>C: still incomplete
    C->>Q: complete() + schedule next follow-up (4s)
    Q->>C: deliver follow-up, attempt 3
    C->>RQ: GET hearing details
    RQ-->>C: still incomplete
    Note over Q: attempt 3 still incomplete - budget exhausted (~14s elapsed)
    C->>Q: deadLetterMessage(reason: "IncompleteHearingDetailsException after 3 attempts")
    Q->>DLQ: move message
```

### 3.4 Outright processing failure — native redelivery exhausts `maxDeliveryCount`

```mermaid
sequenceDiagram
    participant Q as pcr.hearing-resulted queue
    participant C as service-cp-crime-results-pcr
    participant DLQ as Dead-letter sub-queue (pcr.hearing-resulted)

    loop up to maxDeliveryCount attempts
        Q->>C: deliver
        C->>C: processing throws unexpected exception
        C--xQ: lock expires / message abandoned
        Note over Q: Service Bus increments delivery count natively, on this queue only
    end
    Q->>DLQ: move message (maxDeliveryCount exceeded)
```

---

## 4. Migration outline

1. Provision PCR's own queue and its own Event Grid event subscription (§2.2) — both channels now
   receive events in parallel. NOW provisions its own queue and event subscription independently,
   on its own timeline — no coordination with PCR needed.
2. Deploy the Service Bus consumer behind a switch, off by default in every environment.
3. Enable the switch in lower environments only to validate end-to-end — never both channels active
   in the same environment.
4. Once proven, flip the switch in all env's, disabling the relay function at the same
   time.
5. Decommission `pcr-eventgrid-relay-function` and its Event Grid webhook subscription;
   `/internal/hearing-results` retires with it.
