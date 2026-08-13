
# PCR Event Grid Webhook Ingestion Implementation Plan

> **Superseded, 12 Aug 2026:** this plan's Service-Bus-to-direct-webhook goal was carried out and
> then itself superseded — Event Grid no longer delivers directly to this service.
> `pcr-eventgrid-relay-function` now owns the subscription/handshake and relays
> `Hearing_Resulted` here as a plain internal call. Every `*Webhook*` name this plan introduces has
> since been renamed: `HearingResultedWebhookController`/`HearingResultedWebhookService` →
> `HearingResultedEventController`/`HearingResultedEventService`; the generated
> `HearingResultedWebhookEvent`/`HearingResultedWebhookEventData` models (and the
> `receiveHearingResultedWebhook` operationId that produced them) → `HearingResultedEvent`/
> `HearingResultedEventData`/`receiveHearingResultedEvent`; `WebhookAck` was removed entirely
> (the operation now returns a plain `200` with no body). The code snippets below still show the
> original names — retained as a historical record of the work this plan actually executed, not
> as current instructions.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Service Bus queue ingestion path with a direct Azure Event Grid webhook
(`POST /internal/pcr/hearingResults`), per
`docs/designs/2026-07-29-pcr-eventgrid-webhook-ingestion-design.md` and
`docs/pipeline/adrs/007-AMP-892-pcr-eventgrid-webhook-ingestion.md`.

**Architecture:** Add a new operation to the `api-cp-crime-results-pcr` OpenAPI spec (separate
repo, separate PR), generating an `InternalApi` interface. Implement a thin
`HearingResultedWebhookController` + `HearingResultedWebhookService` in
`service-cp-crime-results-pcr` that replaces `HearingResultedProcessorService`, add the
originally-designed in-process completeness retry to `ResultsIngestionService`, map the two new
error cases in `GlobalExceptionHandler`, then delete every Service-Bus-specific class,
dependency, and env var.

**Tech Stack:** Spring Boot 4.1.0, Java 25, JUnit 5 + Mockito, OpenAPI Generator 7.24.0 (spring
generator, `interfaceOnly`, `useTags`), WireMock for controller integration tests.

## Global Constraints

- Java 25, `-Werror` (compiler warnings fail the build) — no `@SuppressWarnings` without cause.
- Test method naming: `subject_should_doOutcome_whenCondition` (mixed styles not permitted in one class).
- `@Mock` fields for collaborators, `@InjectMocks` for the class under test; `@Spy @InjectMocks`
  together when a test needs to stub one real method on the class under test while keeping the
  rest real (used in Task 2 to avoid real `Thread.sleep` in tests).
- Fixed `UUID.fromString(...)` test fixtures only — never `UUID.randomUUID()`.
- No comments unless the WHY is genuinely non-obvious; never explain WHAT code does.
- `ERROR` for unexpected failures, `WARN` for expected business errors — per this repo's
  log-level convention (`shared-code-rules.md`).
- **Cross-repo dependency:** `service-cp-crime-results-pcr`'s `build.gradle` pins
  `uk.gov.hmcts.cp:api-cp-crime-results-pcr:3.0.2`, a real published artifact (GitHub
  Packages/Azure Artifacts), not a local project dependency. Task 1 publishes the new spec
  version to the **local** Maven cache only (`./gradlew publishToMavenLocal`, version `0.0.999`
  by default) so Tasks 2–9 can compile and test against the new `InternalApi` interface
  immediately. **This is a temporary, local-only pin** — `build.gradle`'s dependency line must be
  bumped to the real released version (e.g. `3.1.0`) once the `api-cp-crime-results-pcr` PR
  (opened in Task 1) is merged and released via that repo's own release process. Task 10 notes
  this as a required follow-up; it cannot be completed inside this plan because it depends on
  another repo's PR merging.

---

## Task 1: API spec change — `api-cp-crime-results-pcr` (separate repo, separate PR)

**Files:**
- Modify: `../api-cp-crime-results-pcr/src/main/resources/openapi/openapi-spec.yml`
- Verify (generated, do not edit): `../api-cp-crime-results-pcr/build/generated/src/main/java/uk/gov/hmcts/cp/openapi/api/InternalApi.java`

**Interfaces:**
- Produces: a generated `InternalApi` interface with one operation,
  `receiveHearingResultedWebhook(List<HearingResultedWebhookEvent> events)` (exact generated
  method name/param type confirmed empirically in Step 3 below — do not assume before generating),
  returning `ResponseEntity<WebhookAck>`. Task 6's controller consumes this exact signature.

- [ ] **Step 1: Create a branch off `main` in the spec repo**

```bash
cd ../api-cp-crime-results-pcr
git status --short   # confirm clean before switching
git checkout main
git pull --ff-only
git checkout -b feature/AMP-892-pcr-webhook-ingestion
```

- [ ] **Step 2: Add the new path, tag, and schemas to `openapi-spec.yml`**

Add `Internal` to the `tags:` list (after the existing `pcr` tag):

```yaml
tags:
  - name: pcr
    description: Prison Court Register(s) results per case, hearing and defendant
  - name: Internal
    description: Internal, platform-only operations — not exposed via the public APIM gateway
```

Add the new path (after the existing two paths, before `components:`):

```yaml
  /internal/pcr/hearingResults:
    post:
      summary: Receive Hearing_Resulted events from Azure Event Grid
      description: >-
        Internal webhook endpoint. Azure Event Grid delivers Hearing_Resulted
        pointer events here directly (EventGridSchema, delivered as a JSON
        array), replacing the self-provisioned Service Bus queue this service
        previously consumed. Also receives Event Grid's subscription
        validation handshake on first subscribe.


        Not reachable via the public APIM gateway — secured by network
        isolation, not application-level auth. See ADR-007 in
        service-cp-crime-results-pcr.
      operationId: receiveHearingResultedWebhook
      tags:
        - Internal
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: array
              items:
                $ref: "#/components/schemas/HearingResultedWebhookEvent"
      responses:
        '200':
          description: >-
            Event accepted (Hearing_Resulted processed, or subscription
            validation handshake echoed back).
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/WebhookAck"
        '400':
          description: Malformed payload or unrecognized eventType — Event Grid will not retry.
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
        '503':
          description: >-
            Hearing details not yet complete after in-process retries —
            Event Grid should redeliver per its own retry policy.
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
```

Add the new schemas under `components: schemas:`. Use **one combined schema**, not `oneOf` — every
field optional except `eventType`/`data`, since the real Event Grid wire format for both event
kinds shares the same envelope shape and the service branches on `eventType` in code, not via a
JSON Schema discriminator (avoids depending on this generator version's `oneOf`/composed-schema
codegen behaviour, which is not otherwise used anywhere in this org's specs — confirmed by
grepping every sibling `api-cp-*` repo):

```yaml
    HearingResultedWebhookEvent:
      type: object
      required: [id, eventType, data]
      description: >-
        Covers both Event Grid's subscription-validation handshake
        (eventType Microsoft.EventGrid.SubscriptionValidationEvent) and the
        real Hearing_Resulted event — the service branches on eventType.
      properties:
        id:
          type: string
        eventType:
          type: string
          example: Hearing_Resulted
        subject:
          type: string
        eventTime:
          type: string
          format: date-time
        dataVersion:
          type: string
        metadataVersion:
          type: string
        topic:
          type: string
        data:
          type: object
          description: >-
            validationCode/validationUrl present only for the validation
            handshake; hearingId/hearingDay/userId present only for a real
            Hearing_Resulted event.
          properties:
            validationCode:
              type: string
            validationUrl:
              type: string
            hearingId:
              type: string
              format: uuid
            hearingDay:
              type: string
              format: date
            userId:
              type: string
              format: uuid

    WebhookAck:
      type: object
      properties:
        validationResponse:
          type: string
          description: Only present when acknowledging a subscription validation handshake.
```

- [ ] **Step 3: Regenerate and inspect the actual generated interface**

```bash
./gradlew openApiGenerate
cat build/generated/src/main/java/uk/gov/hmcts/cp/openapi/api/InternalApi.java
cat build/generated/src/main/java/uk/gov/hmcts/cp/openapi/model/HearingResultedWebhookEvent.java
cat build/generated/src/main/java/uk/gov/hmcts/cp/openapi/model/WebhookAck.java
```

Confirm: `InternalApi` has exactly one method, `receiveHearingResultedWebhook`, taking a
`List<HearingResultedWebhookEvent>` (or `@RequestBody`-annotated equivalent) and returning
`ResponseEntity<WebhookAck>`. Confirm `security: []` produced no `@SecurityRequirement`
annotations on this operation (unlike `createNotification` in HRDS's `InternalApi`, which has
two). If the generated method signature differs from this, update Task 6's controller code to
match the real signature — do not force the design to match a guess.

- [ ] **Step 4: Lint and build**

```bash
spectral lint "src/main/resources/openapi/*.{yml,yaml}"
./gradlew build -x apiTest
```

Expected: no spectral errors, build passes (existing tests untouched, this is purely additive).

- [ ] **Step 5: Publish to local Maven cache**

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/uk/gov/hmcts/cp/api-cp-crime-results-pcr/0.0.999/
```

Expected: a `.jar` for version `0.0.999` now exists locally — this is what Task 2 onward compiles
against.

- [ ] **Step 6: Commit and push**

```bash
git add src/main/resources/openapi/openapi-spec.yml
git commit -m "$(cat <<'EOF'
feat(pcr): add internal Event Grid webhook endpoint for hearing results

Adds POST /internal/pcr/hearingResults, tagged Internal, security: []
(network-isolated, not APIM-gateway-fronted) — replaces the Service Bus
queue ingestion path per service-cp-crime-results-pcr's AMP-892 design.

AMP-892
EOF
)"
git push -u origin feature/AMP-892-pcr-webhook-ingestion
```

- [ ] **Step 7: Open the PR (separate repo, separate PR from #20)**

```bash
gh pr create --repo hmcts/api-cp-crime-results-pcr \
  --title "[AMP-892] Add internal Event Grid webhook endpoint for hearing results" \
  --body "$(cat <<'EOF'
## Summary
- Adds `POST /internal/pcr/hearingResults`, tagged `Internal`, `security: []` — receives Azure Event Grid's Hearing_Resulted webhook delivery directly, replacing the Service Bus queue this service previously consumed.
- Additive only — the two existing `/pcrs/...` read endpoints are unchanged.
- Companion service-side implementation: hmcts/service-cp-crime-results-pcr#20.
- See `docs/designs/2026-07-29-pcr-eventgrid-webhook-ingestion-design.md` and ADR-007 in the service repo for the full design.

## Test plan
- [ ] `spectral lint` passes
- [ ] `./gradlew build` passes
- [ ] Once merged and released, service-cp-crime-results-pcr#20 bumps its pinned version off this
EOF
)"
```

---

## Task 2: `ResultsIngestionService` — implement the missing in-process completeness retry

**Files:**
- Modify: `src/main/java/uk/gov/hmcts/cp/services/ResultsIngestionService.java`
- Test: `src/test/java/uk/gov/hmcts/cp/services/ResultsIngestionServiceTest.java`

**Interfaces:**
- Consumes: existing `cacheClient.get(UUID, String)`, `resultsClient.getHearingDetails(UUID)`.
- Produces: `ingestHearingResults(UUID, String)` unchanged signature, now retries up to 3 times
  (2s/4s/8s) before throwing `IncompleteHearingDetailsException`. New package-private method
  `sleepUninterruptibly(Duration)` — Task 6/7 do not call this directly, only tests stub it.

- [ ] **Step 1: Write the failing tests**

Replace the two single-check tests (`ingest_should_throwIncompleteHearingDetailsException_whenFirstResponseIsIncomplete`
already exists but asserts single-call behaviour — update it) with retry-aware versions. Add
`@Spy` on the class under test so `sleepUninterruptibly` can be stubbed to a no-op (real sleeping
would make this test take 14+ seconds):

```java
// Replace the existing @InjectMocks field with:
@Spy
@InjectMocks
private ResultsIngestionService ingestionService;
```

```java
@Test
void ingest_should_returnResponse_whenSecondRestAttemptIsComplete() {
    doNothing().when(ingestionService).sleepUninterruptibly(any());
    when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
    when(resultsClient.getHearingDetails(HEARING_ID))
            .thenReturn(incompleteResponse())
            .thenReturn(completeResponse());

    final HearingDetailsResponse result = ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY);

    assertThat(result.getHearing().getProsecutionCases()).hasSize(1);
    verify(resultsClient, times(2)).getHearingDetails(HEARING_ID);
}

@Test
void ingest_should_throwIncompleteHearingDetailsException_whenAllThreeAttemptsAreIncomplete() {
    doNothing().when(ingestionService).sleepUninterruptibly(any());
    when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
    when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(incompleteResponse());

    assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
            .isInstanceOf(IncompleteHearingDetailsException.class);

    verify(resultsClient, times(3)).getHearingDetails(HEARING_ID);
}

@Test
void ingest_should_sleepWithExponentialBackoff_betweenRetries() {
    doNothing().when(ingestionService).sleepUninterruptibly(any());
    when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
    when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(incompleteResponse());

    assertThatThrownBy(() -> ingestionService.ingestHearingResults(HEARING_ID, HEARING_DAY))
            .isInstanceOf(IncompleteHearingDetailsException.class);

    final ArgumentCaptor<Duration> captor = ArgumentCaptor.forClass(Duration.class);
    verify(ingestionService, times(2)).sleepUninterruptibly(captor.capture());
    assertThat(captor.getAllValues()).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
}
```

Note: `ingest_should_returnCachedPayload_whenRedisHit` and
`ingest_should_throwIncompleteHearingDetailsException_whenCachedPayloadIsIncomplete` (Redis-hit
paths) are unaffected — a Redis hit never enters the retry loop at all, so those two existing
tests need no change. Delete
`ingest_should_fetchViaRest_whenRedisMiss_andFirstResponseIsComplete`'s duplicate coverage only if
it becomes redundant with the new attempt-2 test — it isn't; keep both (attempt-1-complete and
attempt-2-complete are different branches).

Add `@Captor private ArgumentCaptor<Duration> durationCaptor;` is not needed since the test above
uses `ArgumentCaptor.forClass` locally per existing test-file convention in this class (this file
already uses local `ArgumentCaptor.forClass(ServiceBusMessage.class)` — Task 4 removes that usage
when the Service-Bus tests are deleted, so introduce a `@Captor` field here instead, per
`shared-code-rules.md`'s type-safety rule):

```java
@Captor
private ArgumentCaptor<Duration> durationCaptor;
```

And use `durationCaptor` in place of the local `captor` in the backoff test above.

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.services.ResultsIngestionServiceTest'
```

Expected: FAIL — `sleepUninterruptibly` doesn't exist yet, and the retry-count assertions don't
match today's single-check behaviour.

- [ ] **Step 3: Implement the retry loop**

In `ResultsIngestionService.java`, replace `ingestHearingResults`:

```java
private static final int MAX_COMPLETENESS_RETRIES = 3;
private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);

public HearingDetailsResponse ingestHearingResults(final UUID hearingId, final String hearingDay) {
    for (int attempt = 1; attempt <= MAX_COMPLETENESS_RETRIES; attempt++) {
        final HearingDetailsResponse response = cacheClient.get(hearingId, hearingDay)
                .map(this::deserializeCachedHearingResults)
                .orElseGet(() -> resultsClient.getHearingDetails(hearingId));
        if (isComplete(response)) {
            return response;
        }
        log.warn("Incomplete hearing details for hearingId:{} on attempt {}/{} — viewstore may not have caught up yet",
                hearingId, attempt, MAX_COMPLETENESS_RETRIES);
        if (attempt < MAX_COMPLETENESS_RETRIES) {
            sleepUninterruptibly(backoffFor(attempt));
        }
    }
    throw new IncompleteHearingDetailsException(hearingId);
}

private Duration backoffFor(final int attempt) {
    return INITIAL_BACKOFF.multipliedBy((long) Math.pow(2, attempt - 1));
}

/* default */ void sleepUninterruptibly(final Duration duration) {
    try {
        Thread.sleep(duration);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

Note: a Redis hit re-checks `cacheClient.get` on every loop iteration in this shape, which is
correct — but since a Redis hit always returns on attempt 1 in practice (Redis is written
synchronously before the event fires, per this repo's CLAUDE.md), the loop only ever re-queries
`resultsClient` on retries in the Redis-miss case. This matches the two new tests above, which
both stub `cacheClient.get` to return `Optional.empty()` throughout.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.services.ResultsIngestionServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/services/ResultsIngestionService.java \
        src/test/java/uk/gov/hmcts/cp/services/ResultsIngestionServiceTest.java
git commit -m "$(cat <<'EOF'
feat(pcr): implement in-process completeness retry in ResultsIngestionService

Builds the 2s/4s/8s exponential-backoff retry the original ingestion
design specified but never shipped — ingestHearingResults checked
completeness exactly once before this change.

AMP-892
EOF
)"
```

---

## Task 3: `GlobalExceptionHandler` — map the two new webhook error cases

**Files:**
- Modify: `src/main/java/uk/gov/hmcts/cp/exceptions/GlobalExceptionHandler.java`
- Test: `src/test/java/uk/gov/hmcts/cp/exceptions/GlobalExceptionHandlerTest.java` (create if it
  doesn't exist — check first: `ls src/test/java/uk/gov/hmcts/cp/exceptions/`)

**Interfaces:**
- Consumes: `IncompleteHearingDetailsException` (existing, `uk.gov.hmcts.cp.exceptions`),
  `IllegalArgumentException` (JDK).
- Produces: `503`/`400` `ErrorResponse` bodies. Task 5 relies on these two exception types being
  handled here — `HearingResultedWebhookService` throws them directly, never catches them itself.

- [ ] **Step 1: Check for an existing test file**

```bash
ls src/test/java/uk/gov/hmcts/cp/exceptions/GlobalExceptionHandlerTest.java 2>&1
```

If it exists, read it fully and follow its existing `@Mock`/`@InjectMocks` field order and
`buildErrorResponse` assertion style for the two new tests below. If it doesn't exist, create it
following the same shape as the class's existing handler methods (mock `Tracer`, `ClockService`).

- [ ] **Step 2: Write the failing tests**

```java
@Test
void handleIncompleteHearingDetails_should_return503_withWarnLog() {
    final IncompleteHearingDetailsException exception =
            new IncompleteHearingDetailsException(UUID.fromString("00000000-0000-0000-0000-000000000011"));

    final ResponseEntity<ErrorResponse> response = handler.handleIncompleteHearingDetails(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
}

@Test
void handleMalformedWebhookPayload_should_return400() {
    final IllegalArgumentException exception = new IllegalArgumentException("Unrecognized eventType: bogus");

    final ResponseEntity<ErrorResponse> response = handler.handleMalformedWebhookPayload(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.exceptions.GlobalExceptionHandlerTest'
```

Expected: FAIL — `handleIncompleteHearingDetails`/`handleMalformedWebhookPayload` don't exist yet.

- [ ] **Step 4: Add the two handlers**

In `GlobalExceptionHandler.java`, add (after `handleResponseStatusException`, before
`handleServerException`), plus the two new imports:

```java
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
```

```java
@ExceptionHandler(IncompleteHearingDetailsException.class)
public ResponseEntity<ErrorResponse> handleIncompleteHearingDetails(final IncompleteHearingDetailsException e) {
    log.warn("GlobalExceptionHandler handleIncompleteHearingDetails: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(buildErrorResponse(e.getMessage()));
}

@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ErrorResponse> handleMalformedWebhookPayload(final IllegalArgumentException e) {
    log.warn("GlobalExceptionHandler handleMalformedWebhookPayload: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(buildErrorResponse(e.getMessage()));
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.exceptions.GlobalExceptionHandlerTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/exceptions/GlobalExceptionHandler.java \
        src/test/java/uk/gov/hmcts/cp/exceptions/GlobalExceptionHandlerTest.java
git commit -m "$(cat <<'EOF'
feat(pcr): map IncompleteHearingDetailsException and malformed webhook
payloads to 503/400 in GlobalExceptionHandler

AMP-892
EOF
)"
```

---

## Task 4: Bump `build.gradle` to the locally-published spec version

**Files:**
- Modify: `build.gradle:34`

**Interfaces:**
- Produces: `uk.gov.hmcts.cp.openapi.api.InternalApi` on the compile classpath — Task 6 depends
  on this being resolvable.

- [ ] **Step 1: Change the dependency version**

```groovy
  // TEMPORARY: pinned to the locally-published 0.0.999 build (Task 1, Step 5) until
  // api-cp-crime-results-pcr's feature/AMP-892-pcr-webhook-ingestion PR merges and releases —
  // bump to the real released version at that point (see plan Task 10).
  implementation('uk.gov.hmcts.cp:api-cp-crime-results-pcr:0.0.999')
```

- [ ] **Step 2: Verify it resolves and the new interface is visible**

```bash
./gradlew build -x test -x apiTest --refresh-dependencies
find ~/.gradle/caches -iname "InternalApi.class" -path "*api-cp-crime-results-pcr*" 2>/dev/null
```

Expected: build succeeds (existing code still compiles — nothing consumes `InternalApi` yet).

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "$(cat <<'EOF'
chore(pcr): temporarily pin api-cp-crime-results-pcr to local 0.0.999 build

Local-only pin to develop against the new internal webhook endpoint
before its spec PR merges and releases for real. Must be bumped to the
real released version before this branch is considered mergeable.

AMP-892
EOF
)"
```

---

## Task 5: `HearingResultedWebhookService` — new service, replacing `HearingResultedProcessorService`

**Files:**
- Create: `src/main/java/uk/gov/hmcts/cp/services/HearingResultedWebhookService.java`
- Test: `src/test/java/uk/gov/hmcts/cp/services/HearingResultedWebhookServiceTest.java`

**Interfaces:**
- Consumes: generated `uk.gov.hmcts.cp.openapi.model.HearingResultedWebhookEvent`,
  `uk.gov.hmcts.cp.openapi.model.WebhookAck` (Task 1), `ResultsIngestionService.ingestAndPersist(UUID, String)`
  (existing, unchanged).
- Produces: `HearingResultedWebhookService.handle(List<HearingResultedWebhookEvent>): ResponseEntity<WebhookAck>`
  — Task 6's controller calls this exact method.

**No new domain envelope types needed:** the design doc's §4.4 mentions replacing
`servicebus/model/CPHearingResultedEventEnvelope`/`CPHearingResultedEventData` with new
`domain/EventGridEnvelope`/`domain/EventGridEventData` records — but the generated
`HearingResultedWebhookEvent` model (Task 1) already represents that exact wire shape. Adding a
hand-written duplicate would violate YAGNI for no benefit; this task consumes the generated model
directly instead.

- [ ] **Step 1: Write the failing tests**

```java
package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.exceptions.IncompleteHearingDetailsException;
import uk.gov.hmcts.cp.openapi.model.HearingResultedWebhookEvent;
import uk.gov.hmcts.cp.openapi.model.WebhookAck;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HearingResultedWebhookServiceTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String HEARING_DAY = "2026-07-23";

    @Mock
    private ResultsIngestionService ingestionService;

    @InjectMocks
    private HearingResultedWebhookService webhookService;

    @Test
    void handle_should_echoValidationCode_whenSubscriptionValidationEvent() {
        final HearingResultedWebhookEvent event = validationEvent("abc123");

        final ResponseEntity<WebhookAck> response = webhookService.handle(List.of(event));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getValidationResponse()).isEqualTo("abc123");
    }

    @Test
    void handle_should_ingestAndReturn200_whenHearingResultedEvent() {
        final HearingResultedWebhookEvent event = hearingResultedEvent();

        final ResponseEntity<WebhookAck> response = webhookService.handle(List.of(event));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(ingestionService).ingestAndPersist(HEARING_ID, HEARING_DAY);
    }

    @Test
    void handle_should_propagateIncompleteHearingDetailsException_whenIngestionThrows() {
        final HearingResultedWebhookEvent event = hearingResultedEvent();
        doThrow(new IncompleteHearingDetailsException(HEARING_ID))
                .when(ingestionService).ingestAndPersist(HEARING_ID, HEARING_DAY);

        assertThatThrownBy(() -> webhookService.handle(List.of(event)))
                .isInstanceOf(IncompleteHearingDetailsException.class);
    }

    @Test
    void handle_should_throwIllegalArgumentException_whenEventTypeUnrecognized() {
        final HearingResultedWebhookEvent event = new HearingResultedWebhookEvent()
                .id("evt-1").eventType("Some_Other_Event");

        assertThatThrownBy(() -> webhookService.handle(List.of(event)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(ingestionService, org.mockito.Mockito.never()).ingestAndPersist(any(), any());
    }

    private HearingResultedWebhookEvent validationEvent(final String validationCode) {
        return new HearingResultedWebhookEvent()
                .id("evt-1")
                .eventType("Microsoft.EventGrid.SubscriptionValidationEvent")
                .data(java.util.Map.of("validationCode", validationCode));
    }

    private HearingResultedWebhookEvent hearingResultedEvent() {
        return new HearingResultedWebhookEvent()
                .id("evt-2")
                .eventType("Hearing_Resulted")
                .data(java.util.Map.of("hearingId", HEARING_ID.toString(), "hearingDay", HEARING_DAY, "userId", "00000000-0000-0000-0000-000000000099"));
    }
}
```

**Note on the `data` field's generated type:** the exact generated type of
`HearingResultedWebhookEvent.data` depends on Task 1 Step 3's actual generated model — an
untyped `data: {}` schema with no `properties` type restriction typically generates as
`Map<String, Object>` or `Object`. Before writing this test file for real, run:

```bash
cat ~/.m2/repository/uk/gov/hmcts/cp/api-cp-crime-results-pcr/0.0.999/api-cp-crime-results-pcr-0.0.999-sources.jar 2>&1 || \
  unzip -p ~/.m2/repository/uk/gov/hmcts/cp/api-cp-crime-results-pcr/0.0.999/api-cp-crime-results-pcr-0.0.999.jar \
  uk/gov/hmcts/cp/openapi/model/HearingResultedWebhookEvent.class | javap -p -
```

and adjust the test's `.data(...)` calls and the implementation's unwrapping logic (Step 3 below)
to the real generated type/accessor names.

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.services.HearingResultedWebhookServiceTest'
```

Expected: FAIL — `HearingResultedWebhookService` doesn't exist yet.

- [ ] **Step 3: Implement the service**

```java
package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.HearingResultedWebhookEvent;
import uk.gov.hmcts.cp.openapi.model.WebhookAck;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HearingResultedWebhookService {

    private static final String VALIDATION_EVENT_TYPE = "Microsoft.EventGrid.SubscriptionValidationEvent";
    private static final String HEARING_RESULTED_EVENT_TYPE = "Hearing_Resulted";

    private final ResultsIngestionService ingestionService;

    public ResponseEntity<WebhookAck> handle(final List<HearingResultedWebhookEvent> events) {
        final HearingResultedWebhookEvent event = firstEvent(events);
        return switch (event.getEventType()) {
            case VALIDATION_EVENT_TYPE -> echoValidation(event);
            case HEARING_RESULTED_EVENT_TYPE -> ingest(event);
            default -> throw new IllegalArgumentException("Unrecognized eventType: " + event.getEventType());
        };
    }

    private HearingResultedWebhookEvent firstEvent(final List<HearingResultedWebhookEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Empty Event Grid delivery — expected at least one event");
        }
        return events.get(0);
    }

    private ResponseEntity<WebhookAck> echoValidation(final HearingResultedWebhookEvent event) {
        final Map<?, ?> data = (Map<?, ?>) event.getData();
        final String validationCode = String.valueOf(data.get("validationCode"));
        log.info("Echoing Event Grid subscription validation handshake");
        return ResponseEntity.ok(new WebhookAck().validationResponse(validationCode));
    }

    private ResponseEntity<WebhookAck> ingest(final HearingResultedWebhookEvent event) {
        final Map<?, ?> data = (Map<?, ?>) event.getData();
        final UUID hearingId = UUID.fromString(String.valueOf(data.get("hearingId")));
        final String hearingDay = String.valueOf(data.get("hearingDay"));
        ingestionService.ingestAndPersist(hearingId, hearingDay);
        return ResponseEntity.ok(new WebhookAck());
    }
}
```

Adjust the `(Map<?, ?>) event.getData()` casts per the real generated type confirmed in Step 1's
note — if `getData()` returns a strongly-typed generated class instead of `Map`, replace the
`.get("...")` calls with the real generated getters.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.services.HearingResultedWebhookServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/services/HearingResultedWebhookService.java \
        src/test/java/uk/gov/hmcts/cp/services/HearingResultedWebhookServiceTest.java
git commit -m "$(cat <<'EOF'
feat(pcr): add HearingResultedWebhookService

Handles Event Grid's subscription-validation handshake and the real
Hearing_Resulted event, delegating ingestion to the unchanged
ResultsIngestionService.ingestAndPersist. Replaces
HearingResultedProcessorService (deleted in a later commit once the
controller wiring lands).

AMP-892
EOF
)"
```

---

## Task 6: `HearingResultedWebhookController` — implements generated `InternalApi`

**Files:**
- Create: `src/main/java/uk/gov/hmcts/cp/controllers/HearingResultedWebhookController.java`
- Test: `src/test/java/uk/gov/hmcts/cp/integration/HearingResultedWebhookControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `HearingResultedWebhookService.handle(List<HearingResultedWebhookEvent>)` (Task 5),
  generated `InternalApi` (Task 1).
- Produces: the live `/internal/pcr/hearingResults` endpoint.

- [ ] **Step 1: Check the existing integration test base class**

```bash
cat src/test/java/uk/gov/hmcts/cp/integration/IntegrationTestBase.java
```

Follow its exact `@SpringBootTest`/`mockMvc` exposure pattern for Step 2 below.

- [ ] **Step 2: Write the failing integration test**

```java
package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.services.ResultsIngestionService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HearingResultedWebhookControllerIntegrationTest extends IntegrationTestBase {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @MockBean
    private ResultsIngestionService ingestionService;

    @Test
    void receiveHearingResultedWebhook_should_echoValidationCode() throws Exception {
        final String body = """
                [{
                  "id": "evt-1",
                  "eventType": "Microsoft.EventGrid.SubscriptionValidationEvent",
                  "data": { "validationCode": "abc123" }
                }]
                """;

        mockMvc.perform(post("/internal/pcr/hearingResults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"validationResponse\":\"abc123\"}"));
    }

    @Test
    void receiveHearingResultedWebhook_should_ingestAndReturn200_whenHearingResultedEvent() throws Exception {
        final String body = """
                [{
                  "id": "evt-2",
                  "eventType": "Hearing_Resulted",
                  "data": { "hearingId": "00000000-0000-0000-0000-000000000011", "hearingDay": "2026-07-23", "userId": "00000000-0000-0000-0000-000000000099" }
                }]
                """;

        mockMvc.perform(post("/internal/pcr/hearingResults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(ingestionService).ingestAndPersist(eq(HEARING_ID), eq("2026-07-23"));
    }

    @Test
    void receiveHearingResultedWebhook_should_return400_whenEventTypeUnrecognized() throws Exception {
        final String body = """
                [{ "id": "evt-3", "eventType": "Some_Other_Event", "data": {} }]
                """;

        mockMvc.perform(post("/internal/pcr/hearingResults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
```

Adjust the JSON fixtures' field names if Task 1 Step 3's generated model uses different casing or
structure than assumed here — confirm against the real generated `HearingResultedWebhookEvent`.

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.integration.HearingResultedWebhookControllerIntegrationTest'
```

Expected: FAIL — no controller/mapping exists for this path yet (404).

- [ ] **Step 4: Implement the controller**

```java
package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.InternalApi;
import uk.gov.hmcts.cp.openapi.model.HearingResultedWebhookEvent;
import uk.gov.hmcts.cp.openapi.model.WebhookAck;
import uk.gov.hmcts.cp.services.HearingResultedWebhookService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class HearingResultedWebhookController implements InternalApi {

    private final HearingResultedWebhookService webhookService;

    @Override
    public ResponseEntity<WebhookAck> receiveHearingResultedWebhook(final List<HearingResultedWebhookEvent> events) {
        return webhookService.handle(events);
    }
}
```

Adjust the method name/signature to match whatever Task 1 Step 3 actually generated if it differs
(e.g. a different parameter name, or the generator wrapping the array in a request-body model
class rather than a bare `List<...>`).

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.integration.HearingResultedWebhookControllerIntegrationTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/controllers/HearingResultedWebhookController.java \
        src/test/java/uk/gov/hmcts/cp/integration/HearingResultedWebhookControllerIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat(pcr): add HearingResultedWebhookController implementing InternalApi

Wires POST /internal/pcr/hearingResults to HearingResultedWebhookService.

AMP-892
EOF
)"
```

---

## Task 7: Delete the Service Bus ingestion path

**Files:**
- Delete: `src/main/java/uk/gov/hmcts/cp/servicebus/` (entire package: `HearingResultedProcessorService.java`, `model/CPHearingResultedEventEnvelope.java`, `model/CPHearingResultedEventData.java`)
- Delete: `src/test/java/uk/gov/hmcts/cp/servicebus/services/HearingResultedProcessorServiceTest.java`
- Delete: `src/main/java/uk/gov/hmcts/cp/clients/HearingResultedServiceBusClientFactory.java`
- Delete: `src/main/java/uk/gov/hmcts/cp/clients/HearingResultedQueueProvisioner.java`
- Delete: `src/main/java/uk/gov/hmcts/cp/config/RetryServiceConfig.java`
- Delete: `src/main/java/uk/gov/hmcts/cp/config/ServiceBusProperties.java`
- Delete: any existing tests for the four classes above (check first: `find src/test -iname "*ServiceBus*" -o -iname "*RetryServiceConfig*" -o -iname "*QueueProvisioner*"`)
- Delete: `src/main/java/uk/gov/hmcts/cp/domain/HearingResultedPointer.java` — superseded by the generated `HearingResultedWebhookEvent` model consumed directly (Task 5), no replacement domain type needed; check first whether anything besides the deleted classes still references it (`grep -rn HearingResultedPointer src/main`)
- Modify: `src/main/java/uk/gov/hmcts/cp/services/ResultsIngestionService.java` — remove `escalateOrDeadLetter` and its Service-Bus-only imports/fields
- Modify: `src/test/java/uk/gov/hmcts/cp/services/ResultsIngestionServiceTest.java` — remove the two `escalateOrDeadLetter_*` tests and all Service-Bus-only mocks/imports
- Modify: `build.gradle` — remove the two Service Bus dependency lines and their comment block
- Modify: `src/main/resources/application.yaml` — remove the `service-bus:` block
- Modify: `CLAUDE.md` — remove the 5 Service Bus rows from the Environment Variables table, update the "Infrastructure" table row and "Source Structure" bullets that reference `HearingResultedProcessorService`/the Service Bus clients (point them at the new webhook classes instead)

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new — this task only removes.

- [ ] **Step 1: Find every remaining reference before deleting**

```bash
grep -rln "servicebus\|ServiceBus\|RetryServiceConfig\|HearingResultedPointer\|HearingResultedQueueProvisioner" \
  src/main src/test --include="*.java" | sort
```

Confirm the only hits are the files listed above (plus `ResultsIngestionService.java`/
`ResultsIngestionServiceTest.java`, which Steps 3–4 modify rather than delete). If anything else
references them, stop and re-check the design doc's §4.4 deletion list against that file before
proceeding.

- [ ] **Step 2: Delete the files**

```bash
git rm -r src/main/java/uk/gov/hmcts/cp/servicebus
git rm src/test/java/uk/gov/hmcts/cp/servicebus/services/HearingResultedProcessorServiceTest.java
git rm src/main/java/uk/gov/hmcts/cp/clients/HearingResultedServiceBusClientFactory.java
git rm src/main/java/uk/gov/hmcts/cp/clients/HearingResultedQueueProvisioner.java
git rm src/main/java/uk/gov/hmcts/cp/config/RetryServiceConfig.java
git rm src/main/java/uk/gov/hmcts/cp/config/ServiceBusProperties.java
git rm src/main/java/uk/gov/hmcts/cp/domain/HearingResultedPointer.java
# Delete any matching test files found in Step 1 for the classes above, e.g.:
git rm src/test/java/uk/gov/hmcts/cp/config/RetryServiceConfigTest.java 2>/dev/null || true
git rm src/test/java/uk/gov/hmcts/cp/clients/HearingResultedServiceBusClientFactoryTest.java 2>/dev/null || true
git rm src/test/java/uk/gov/hmcts/cp/clients/HearingResultedQueueProvisionerTest.java 2>/dev/null || true
```

- [ ] **Step 3: Remove `escalateOrDeadLetter` from `ResultsIngestionService`**

Remove the `escalateOrDeadLetter`, `sendRetryMessage`, `newRetryMessage`, `retryCountOf` methods
and the `HearingResultedServiceBusClientFactory clientFactory`, `RetryServiceConfig
retryServiceConfig` fields and their constructor-injection (Lombok `@RequiredArgsConstructor`
picks this up automatically once the fields are removed). Remove the now-unused imports:
`com.azure.messaging.servicebus.*`, `uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory`,
`uk.gov.hmcts.cp.config.RetryServiceConfig`, `uk.gov.hmcts.cp.domain.HearingResultedPointer`,
`java.time.Duration` (only if nothing else in the file uses it — Task 2 already added
`Duration` usage for the completeness backoff, so confirm it's still needed before removing).

- [ ] **Step 4: Remove the corresponding tests**

In `ResultsIngestionServiceTest.java`, remove:
`escalateOrDeadLetter_should_completeMessageAndSendRetryMessage_whenUnderMaxRetries`,
`escalateOrDeadLetter_should_deadLetter_whenMaxScheduledRetriesExceeded`, the `POINTER` constant,
the `@Mock HearingResultedServiceBusClientFactory clientFactory`, `@Mock
ServiceBusReceivedMessageContext context`, `@Mock ServiceBusReceivedMessage message`, `@Mock
ServiceBusSenderClient senderClient`, `@Spy RetryServiceConfig retryServiceConfig` fields, and
their now-unused imports (`com.azure.messaging.servicebus.*`,
`uk.gov.hmcts.cp.clients.HearingResultedServiceBusClientFactory`,
`uk.gov.hmcts.cp.config.RetryServiceConfig`, `uk.gov.hmcts.cp.domain.HearingResultedPointer`,
`org.mockito.ArgumentCaptor` if nothing else in the file uses the raw form — Task 2 already
introduced a `@Captor` field, so the raw `ArgumentCaptor.forClass` import may no longer be needed;
confirm with a search before removing).

- [ ] **Step 5: Remove the Gradle dependencies**

In `build.gradle`, delete lines 82–88 (the entire `--- Hearing event ingestion ---` comment block
and the two `com.azure:azure-messaging-servicebus`/`com.azure:azure-identity` lines). Leave the
`spring-boot-starter-data-redis` line — Redis is still used by `HearingResultedCacheClient`.

- [ ] **Step 6: Remove the `service-bus:` config block**

In `src/main/resources/application.yaml`, delete the entire `service-bus:` block (the last 6
lines of the file).

- [ ] **Step 7: Update `CLAUDE.md`**

Remove these rows from the Environment Variables table: `AZURE_SERVICE_BUS_ADMIN_URI`,
`AZURE_SERVICE_BUS_URI`, `PCR_HEARING_RESULTED_QUEUE`, `SERVICE_BUS_RETRY_DURATION`,
`SERVICE_BUS_MAX_TRIES`. Update the "Infrastructure" table's ingestion-trigger row and the
"Source Structure" bullets that name `HearingResultedProcessorService`,
`HearingResultedServiceBusClientFactory`, `HearingResultedQueueProvisioner`, and
`CPHearingResultedEventEnvelope`/`CPHearingResultedEventData` to instead describe
`HearingResultedWebhookController`/`HearingResultedWebhookService` (consuming the generated
`HearingResultedWebhookEvent` model directly — no hand-written envelope type) and the new
`/internal/pcr/hearingResults` endpoint. Update the
"Repo-Specific Architecture Rules" bullet about "Redis-first, REST-fallback-with-retry is
mandatory" to note the in-process retry is now actually implemented (Task 2), not just designed.

- [ ] **Step 8: Run the full build**

```bash
./gradlew spotlessApply
./gradlew build -x apiTest
```

Expected: build passes — no leftover references to deleted classes, no unused-import warnings
(`-Werror` would fail the build on those).

- [ ] **Step 9: Commit**

```bash
git add -A
git status   # review before committing — confirm only expected deletions/modifications
git commit -m "$(cat <<'EOF'
refactor(pcr): remove Service Bus ingestion path, superseded by Event Grid webhook

Deletes HearingResultedProcessorService, HearingResultedServiceBusClientFactory,
HearingResultedQueueProvisioner, RetryServiceConfig, ServiceBusProperties, the
Service Bus Gradle dependencies, and the related env vars — all replaced by
HearingResultedWebhookController/HearingResultedWebhookService (previous commits).

AMP-892
EOF
)"
```

---

## Task 8: Full verification pass

**Files:** none (verification only)

- [ ] **Step 1: Run the complete test suite**

```bash
./gradlew build -x apiTest
```

Expected: all tests pass, no PMD violations, spotless check clean.

- [ ] **Step 2: Run PMD and format checks explicitly**

```bash
./gradlew pmdMain spotlessCheck
```

Expected: both pass with zero violations.

- [ ] **Step 3: Confirm no dangling references to anything deleted in Task 7**

```bash
grep -rn "servicebus\|ServiceBus\|RetryServiceConfig\|HearingResultedPointer" src/main src/test --include="*.java"
```

Expected: no output.

- [ ] **Step 4: Manually verify the webhook end-to-end against a local run (optional but recommended)**

```bash
./gradlew bootRun &
sleep 5
curl -s -X POST http://localhost:8082/internal/pcr/hearingResults \
  -H "Content-Type: application/json" \
  -d '[{"id":"evt-1","eventType":"Microsoft.EventGrid.SubscriptionValidationEvent","data":{"validationCode":"test123"}}]'
# Expected: {"validationResponse":"test123"}
kill %1
```

---

## Task 9: Push all commits to PR #20

**Files:** none

- [ ] **Step 1: Confirm branch and history**

```bash
git log --oneline main..HEAD
git status --short
```

Expected: 6 commits ahead of `main` (Tasks 2, 3, 4, 5, 6, 7 — Task 1's commits live in the
sibling `api-cp-crime-results-pcr` repo, not here), clean working tree.

- [ ] **Step 2: Push**

```bash
git push origin feature/AMP-892-pcr-webhook-ingestion
```

- [ ] **Step 3: Update PR #20's description**

```bash
gh pr edit 20 --body "$(cat <<'EOF'
## Summary
- Implements the Event Grid webhook ingestion design (docs/designs/2026-07-29-pcr-eventgrid-webhook-ingestion-design.md, ADR-007): new `HearingResultedWebhookController`/`HearingResultedWebhookService` handle `POST /internal/pcr/hearingResults`, replacing `HearingResultedProcessorService`.
- `ResultsIngestionService` now implements the in-process 2s/4s/8s completeness retry that was originally designed but never shipped.
- `GlobalExceptionHandler` maps `IncompleteHearingDetailsException` → 503 and malformed webhook payloads → 400.
- Deletes the Service Bus ingestion path entirely: `servicebus/` package, `HearingResultedServiceBusClientFactory`, `HearingResultedQueueProvisioner`, `RetryServiceConfig`, `ServiceBusProperties`, the two Service Bus Gradle dependencies, and the related env vars.

## Depends on
- hmcts/api-cp-crime-results-pcr#<PR number from Task 1> — **not yet merged**. This branch currently pins `api-cp-crime-results-pcr` to a locally-published `0.0.999` build (`build.gradle`) as a temporary measure so this PR is reviewable and its tests are runnable now. **Before this PR can merge**, that spec PR must merge and release, and this branch's `build.gradle` pin must be bumped to the real released version.

## Test plan
- [ ] `./gradlew build` passes
- [ ] `./gradlew pmdMain spotlessCheck` pass
- [ ] Manual webhook smoke test (validation handshake + real event) against a local run
- [ ] Once the spec PR merges/releases: bump `build.gradle`'s pinned version off `0.0.999`, re-run the full build
EOF
)"
```

---

## Task 10: Follow-up (blocked, cannot complete in this plan)

Once `api-cp-crime-results-pcr`'s `feature/AMP-892-pcr-webhook-ingestion` PR (Task 1) merges and
that repo cuts a release (e.g. via its own `release` skill invocation, producing a real `v3.1.0`
tag and published artifact):

- [ ] Bump `build.gradle:34` in this repo from `api-cp-crime-results-pcr:0.0.999` to the real
      released version (e.g. `3.1.0`).
- [ ] Re-run `./gradlew build -x apiTest --refresh-dependencies` to confirm it resolves against
      the real published artifact, not the local one.
- [ ] Commit and push: `git commit -m "chore(pcr): bump api-cp-crime-results-pcr to released 3.1.0" -- build.gradle`
- [ ] Only once this lands should PR #20 be considered mergeable — flag this explicitly in the PR
      description (already done in Task 9, Step 3) so a reviewer doesn't merge against the
      temporary local pin.
- [ ] Manual ops step, not code (design doc §6): once the webhook path is confirmed live in dev,
      delete the self-provisioned `pcr.hearing-resulted` Service Bus queue and any
      Service-Bus-destination Event Grid subscription still routing to it.