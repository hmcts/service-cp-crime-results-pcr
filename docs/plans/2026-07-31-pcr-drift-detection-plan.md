# PCR Drift Detection Integration Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up the first drift-detection fixture, `pcr-multiple-defendants-multiple-offences`,
as a parameterized integration test, per
`docs/designs/2026-07-31-pcr-drift-detection-design.md` and
`docs/pipeline/adrs/008-AMP-898-pcr-drift-detection-integration-test.md`.

**Architecture:** New fixture root `src/test/resources/drift-detection/<hearing-name>/`
(`event.json`, `now-subscriptions.json`, `expected/<defendantId>.json`, `reference/*.pdf`). New
`FixtureProvider` (JUnit `ArgumentsProvider`) scans that root. New
`PcrDriftDetectionIntegrationTest` (`@ParameterizedTest`, extends the existing
`IngestionE2ETestBase`) replays each fixture's event through the real ingestion endpoint →
generation-gate → persistence path, then diffs each defendant's `GET /pcr` response against its
expected file with
`JSONAssert.assertEquals(..., JSONCompareMode.STRICT)`.

**Tech Stack:** Spring Boot 4.1.0, Java 25, JUnit 5 (`@ParameterizedTest`/`@ArgumentsSource`),
`org.skyscreamer.jsonassert` (already transitive via `spring-boot-starter-test` — confirmed, no
new dependency), WireMock (already a test dependency), real Postgres/Redis via
`docker compose up -d postgres redis`.

## Global Constraints

- Java 25, `-Werror` — no `@SuppressWarnings` without cause.
- Test method naming: `subject_should_doOutcome_whenCondition`.
- Fixed `UUID.fromString(...)` literals only — never `UUID.randomUUID()`. This plan uses the real
  defendant/hearing UUIDs from the actual fixture data throughout, which are already fixed values.
- No comments unless the WHY is genuinely non-obvious.
- This plan adds **no production code** — `ResultsIngestionService`, the generation gate, and
  `PcrResultsMapper` are exercised unmodified. Every file this plan touches lives under
  `src/test/`.
- Requires `docker compose up -d postgres redis` running locally before any step that runs the
  Spring context (`PostgresInitialise`/`RedisInitialise` fail fast with instructions otherwise).

---

## Task 1: Fixture directory — copy the existing raw material into `drift-detection/`

**Files:**
- Create: `src/test/resources/drift-detection/multiple-defendants-multiple-offences/event.json`
- Create: `src/test/resources/drift-detection/multiple-defendants-multiple-offences/now-subscriptions.json`
- Create: `src/test/resources/drift-detection/multiple-defendants-multiple-offences/reference/multiple-defendants-multiple-offences-def1.pdf`
- Create: `src/test/resources/drift-detection/multiple-defendants-multiple-offences/reference/multiple-defendants-multiple-offences-def2.pdf`

**Interfaces:** none — this task only stages fixture files that Tasks 2–4 read.

- [ ] **Step 1: Copy the hearing payload and PDFs**

```bash
mkdir -p src/test/resources/drift-detection/multiple-defendants-multiple-offences/reference

cp src/test/resources/pcr-multiple-defendants-multiple-offences/multiple-defendants-multiple-offences.json \
   src/test/resources/drift-detection/multiple-defendants-multiple-offences/event.json

cp src/test/resources/pcr-multiple-defendants-multiple-offences/multiple-defendants-multiple-offences-def1.pdf \
   src/test/resources/pcr-multiple-defendants-multiple-offences/multiple-defendants-multiple-offences-def2.pdf \
   src/test/resources/drift-detection/multiple-defendants-multiple-offences/reference/
```

Do **not** delete the original `pcr-multiple-defendants-multiple-offences/` directory — it is
untouched raw material other future fixtures may still reference, and deleting it is out of this
plan's scope.

- [ ] **Step 2: Confirm the copied event.json's identifying fields**

```bash
python3 -c "
import json
d = json.load(open('src/test/resources/drift-detection/multiple-defendants-multiple-offences/event.json'))
h = d['hearing']
print('hearingId:', h['id'])
print('hearingDay:', h['hearingDays'][0]['sittingDay'][:10])
print('caseURN:', h['prosecutionCases'][0]['prosecutionCaseIdentifier']['caseURN'])
"
```

Expected output — confirms these against Task 4's parsed values:
```
hearingId: f15a49db-812e-43d2-bfc5-7f3c7ee9ede1
hearingDay: 2026-07-31
caseURN: AS231157673
```

- [ ] **Step 3: Add the now-subscriptions stub**

Copy the existing shared fixture as this fixture's own self-contained copy (per design doc §3 —
each fixture owns its stub, not coupled to a file another test could change independently):

```bash
cp src/test/resources/referencedata/now-subscriptions-prison-court-register-fixture.json \
   src/test/resources/drift-detection/multiple-defendants-multiple-offences/now-subscriptions.json
```

This will be validated empirically in Task 3 (if the ingestion doesn't persist any defendant when
this stub is used, the vocabulary doesn't match this hearing and the stub's `subscriptionVocabulary`
block needs adjusting before continuing — see Task 3 Step 1's expected outcome).

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/drift-detection/
git commit -m "$(cat <<'EOF'
test(pcr): stage drift-detection fixture for multiple-defendants-multiple-offences

Copies the existing hearing payload and reference PDFs into the new
drift-detection/ fixture root (AMP-898) — raw material only, no test
code yet.

AMP-898
EOF
)"
```

---

## Task 2: `FixtureProvider` — discovers fixture folders under `drift-detection/`

**Files:**
- Create: `src/test/java/uk/gov/hmcts/cp/integration/e2e/driftdetection/FixtureProvider.java`
- Test: `src/test/java/uk/gov/hmcts/cp/integration/e2e/driftdetection/FixtureProviderTest.java`

**Interfaces:**
- Produces: `FixtureProvider implements ArgumentsProvider`, yielding one `DriftFixture` per
  subdirectory of `drift-detection/` on the classpath. `DriftFixture` is a small record:
  `record DriftFixture(String name, Path root) {}` (or equivalent) — Task 4's `@ParameterizedTest`
  consumes this exact type.

- [ ] **Step 1: Write the failing test**

```java
package uk.gov.hmcts.cp.integration.e2e.driftdetection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureProviderTest {

    @Test
    void provideArguments_should_discoverMultipleDefendantsMultipleOffencesFixture() throws Exception {
        final FixtureProvider provider = new FixtureProvider();

        final List<DriftFixture> fixtures = provider.provideArguments(null)
                .map(Arguments::get)
                .map(args -> (DriftFixture) args[0])
                .toList();

        assertThat(fixtures)
                .extracting(DriftFixture::name)
                .contains("multiple-defendants-multiple-offences");
    }

    @Test
    void provideArguments_should_resolveRootContainingEventJson() throws Exception {
        final FixtureProvider provider = new FixtureProvider();

        final DriftFixture fixture = provider.provideArguments(null)
                .map(Arguments::get)
                .map(args -> (DriftFixture) args[0])
                .filter(f -> "multiple-defendants-multiple-offences".equals(f.name()))
                .findFirst()
                .orElseThrow();

        assertThat(fixture.root().resolve("event.json")).exists();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.integration.e2e.driftdetection.FixtureProviderTest'
```

Expected: FAIL — `FixtureProvider`/`DriftFixture` don't exist yet.

- [ ] **Step 3: Implement**

```java
package uk.gov.hmcts.cp.integration.e2e.driftdetection;

import java.nio.file.Path;

public record DriftFixture(String name, Path root) {
}
```

```java
package uk.gov.hmcts.cp.integration.e2e.driftdetection;

import lombok.SneakyThrows;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FixtureProvider implements ArgumentsProvider {

    private static final String FIXTURE_ROOT = "drift-detection";

    @Override
    @SneakyThrows
    public Stream<? extends Arguments> provideArguments(final Object context) {
        final URL resource = getClass().getClassLoader().getResource(FIXTURE_ROOT);
        final Path root = Path.of(resource.toURI());
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory)
                    .map(dir -> Arguments.of(new DriftFixture(dir.getFileName().toString(), dir)))
                    .toList()
                    .stream();
        }
    }
}
```

Note: the real `ArgumentsProvider.provideArguments` signature takes an
`ExtensionContext`, not `Object` — the test above passes `null` because this implementation never
reads the context. Confirm the exact import (`org.junit.jupiter.api.extension.ExtensionContext`)
and method signature against the JUnit 5 version already on the classpath
(`./gradlew dependencies --configuration testImplementation | grep junit-jupiter`) before writing
this file for real, and adjust both the interface method signature and the test's `null` argument
type accordingly — do not guess if it differs.

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.integration.e2e.driftdetection.FixtureProviderTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/uk/gov/hmcts/cp/integration/e2e/driftdetection/
git commit -m "$(cat <<'EOF'
test(pcr): add FixtureProvider to discover drift-detection fixture folders

Scans src/test/resources/drift-detection/* so a future hearing fixture
is "add a folder", not new Java (AMP-898).

AMP-898
EOF
)"
```

---

## Task 3: Bootstrap `expected/*.json` — capture real output, verify against the PDFs

**Files:**
- Create (temporary, deleted at end of this task): `src/test/java/uk/gov/hmcts/cp/integration/e2e/driftdetection/BootstrapExpectedFixturesTest.java`
- Create (kept): `src/test/resources/drift-detection/multiple-defendants-multiple-offences/expected/0f8306f4-b997-4d4c-81d6-999a646a7031.json`
- Create (kept): `src/test/resources/drift-detection/multiple-defendants-multiple-offences/expected/dd34ddd7-4f06-4ba7-be69-ee242c0f97bc.json`
- Create (kept): `src/test/resources/drift-detection/multiple-defendants-multiple-offences/expected/9cd40664-9497-4334-8caf-bdf0ac44ac9f.json`

**Interfaces:** none — this task produces test *data*, not test code that survives.

This is a one-time manual step per fixture (design doc §7) — not something Task 4's permanent
test does at run time.

- [ ] **Step 1: Write a throwaway test that runs the real path and prints the actual response**

```java
package uk.gov.hmcts.cp.integration.e2e.driftdetection;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.gov.hmcts.cp.integration.e2e.IngestionE2ETestBase;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

class BootstrapExpectedFixturesTest extends IngestionE2ETestBase {

    // Steps 2-4 of the design doc §4 flow, hardcoded for this one fixture, printing the
    // response bodies to stdout so they can be captured into expected/*.json by hand.
    // Reuses the same Redis-seed/WireMock-stub/event-POST helpers Task 4's real test will have
    // — write this test's body first, then Task 4 lifts the reusable parts out.

    @Test
    void printActualPcrResponses_forAllThreeDefendants() throws Exception {
        // seed Redis, stub now-subscriptions, POST /internal/hearing-results — same as
        // HearingResultedIngestionE2EIntegrationTest's given_*/when_* helpers, pointed at this
        // fixture's event.json/now-subscriptions.json

        for (final String defendantId : java.util.List.of(
                "0f8306f4-b997-4d4c-81d6-999a646a7031",
                "dd34ddd7-4f06-4ba7-be69-ee242c0f97bc",
                "9cd40664-9497-4334-8caf-bdf0ac44ac9f")) {
            mockMvc.perform(MockMvcRequestBuilders.get(
                            "/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                            "AS231157673", "f15a49db-812e-43d2-bfc5-7f3c7ee9ede1", defendantId))
                    .andDo(print());
        }
    }
}
```

- [ ] **Step 2: Run it and capture stdout**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.integration.e2e.driftdetection.BootstrapExpectedFixturesTest' --info \
  | tee /tmp/bootstrap-output.txt
```

If the response for `9cd40664-...` is **not** `[]`, the `now-subscriptions.json` stub (Task 1
Step 3) matched this defendant's vocabulary when it shouldn't have — re-check the stub's
`subscriptionVocabulary` block against this defendant's actual judicial results before
continuing; do not proceed with a stub that doesn't reproduce the real exclusion.

If the response for `0f8306f4-...` or `dd34ddd7-...` **is** `[]`, the stub didn't match when it
should have — same check, opposite direction.

- [ ] **Step 3: Verify each captured response against its PDF, field by field**

For `0f8306f4-b997-4d4c-81d6-999a646a7031` (`reference/multiple-defendants-multiple-offences-def1.pdf`
— MechTommie MachLarkin), confirm the captured JSON's values against the PDF text:

| PDF field | PDF value | Expected JSON path |
|---|---|---|
| Defendant name | MechTommie MachLarkin | `defendant.firstName`/`defendant.lastName` (confirm split matches `personDetails` in `event.json`, not the concatenated PDF display string) |
| Date of birth | 30/07/1969 | `defendant.dateOfBirth` = `1969-07-30` |
| Defendant address | 18 George Square, George Square, Glasgow, Glasgow City, G2 1QU | `defendant.address.*` |
| Case reference | AS231157673 | `caseURN` |
| Date of hearing | 31/07/2026 | `hearing.hearingDate` |
| Offence TH68013A | Attempt theft of motor vehicle, GUILTY 31/07/2026, conviction date 31/07/2026 | `offences[0].code/title/pleaValue/pleaDate/convictionDate` |
| Offence CA03012 | Possess/control TV set..., GUILTY 31/07/2026, Summary-only offence, conviction date 31/07/2026 | `offences[1].*` |
| Both offences' result text | `RI - Remanded in custody` + full remand text | `offences[*].resultTexts[0].resultText` |

For `dd34ddd7-4f06-4ba7-be69-ee242c0f97bc` (`reference/multiple-defendants-multiple-offences-def2.pdf`
— DroidAlan DotMacejkovic), same cross-check against:

| PDF field | PDF value |
|---|---|
| Defendant name | DroidAlan DotMacejkovic |
| Date of birth | 30/07/1981 |
| Defendant address | 40 Market Place, Market Place, Bristol, BS1 1AA |
| Offence FS13012 | Keep cooked or reheated food..., NOT_GUILTY 31/07/2026, no conviction date, result `WDRN - Withdrawn` / "Complaint withdrawn." |
| Offence BC90005 | Offer to supply a foreign satellite programme, NOT_GUILTY 31/07/2026, no conviction date, result `RI - Remanded in custody` + full remand text |

Note the two offences on this defendant have **different** result texts (`WDRN` vs `RI`) —
confirm the captured JSON's two `judicialResults[0].resultText` values differ accordingly; do not
assume both offences share one result.

For `9cd40664-9497-4334-8caf-bdf0ac44ac9f` (no PDF), confirm the captured response is exactly `[]`.

- [ ] **Step 4: Save the verified responses as the expected files**

```bash
mkdir -p src/test/resources/drift-detection/multiple-defendants-multiple-offences/expected
# extract each defendant's response body from /tmp/bootstrap-output.txt (or re-run with
# .andReturn().getResponse().getContentAsString() written straight to a file instead of print(),
# whichever is less error-prone) into:
#   expected/0f8306f4-b997-4d4c-81d6-999a646a7031.json
#   expected/dd34ddd7-4f06-4ba7-be69-ee242c0f97bc.json
#   expected/9cd40664-9497-4334-8caf-bdf0ac44ac9f.json   (literally: [])
```

Pretty-print each file for reviewability (`python3 -m json.tool < raw.json > expected/<id>.json`).

- [ ] **Step 5: Delete the throwaway bootstrap test**

```bash
git rm src/test/java/uk/gov/hmcts/cp/integration/e2e/driftdetection/BootstrapExpectedFixturesTest.java
```

- [ ] **Step 6: Commit the expected fixtures only**

```bash
git add src/test/resources/drift-detection/multiple-defendants-multiple-offences/expected/
git commit -m "$(cat <<'EOF'
test(pcr): capture verified expected PCR output for multiple-defendants-multiple-offences

Each expected/<defendantId>.json is the real GET /pcr response,
captured once and verified field-by-field against the archived PDFs
(AMP-898) — the 9cd40664 defendant's file is [] since no PDF was ever
generated for them.

AMP-898
EOF
)"
```

---

## Task 4: `PcrDriftDetectionIntegrationTest` — the permanent parameterized test

**Files:**
- Create: `src/test/java/uk/gov/hmcts/cp/integration/e2e/driftdetection/PcrDriftDetectionIntegrationTest.java`

**Interfaces:**
- Consumes: `FixtureProvider`/`DriftFixture` (Task 2), the fixture files staged in Tasks 1 and 3.
- Produces: the permanent, reusable drift-detection test — this is the task's actual deliverable.

- [ ] **Step 1: Write the test**

```java
package uk.gov.hmcts.cp.integration.e2e.driftdetection;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.gov.hmcts.cp.integration.e2e.IngestionE2ETestBase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PcrDriftDetectionIntegrationTest extends IngestionE2ETestBase {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    void replayedHearing_should_matchExpectedPcrOutput_forEveryDefendant(final DriftFixture fixture) throws Exception {
        final HearingIdentity identity = parseIdentity(fixture.root());

        stubNowSubscriptions(fixture.root());
        seedRedis(fixture.root(), identity);
        postHearingResultedEvent(identity);

        try (Stream<Path> expectedFiles = Files.list(fixture.root().resolve("expected"))) {
            for (final Path expectedFile : expectedFiles.toList()) {
                final String defendantId = expectedFile.getFileName().toString().replace(".json", "");
                assertMatchesExpected(identity, defendantId, expectedFile);
            }
        }
    }

    private void stubNowSubscriptions(final Path fixtureRoot) throws Exception {
        WireMock.stubFor(get(urlPathEqualTo("/referencedata-query-api/query/api/rest/referencedata/now-subscriptions"))
                .willReturn(aResponse()
                        .withStatus(HTTP_OK)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Files.readString(fixtureRoot.resolve("now-subscriptions.json")))));
    }

    private void seedRedis(final Path fixtureRoot, final HearingIdentity identity) throws Exception {
        final String cacheKey = "INT_" + identity.hearingId() + "_" + identity.hearingDay() + "_result_";
        redisTemplate.opsForValue().set(cacheKey, Files.readString(fixtureRoot.resolve("event.json")));
    }

    private void postHearingResultedEvent(final HearingIdentity identity) throws Exception {
        final String body = """
                [{
                  "id": "evt-1",
                  "eventType": "Hearing_Resulted",
                  "subject": "hearing/%s",
                  "eventTime": "2026-07-31T09:00:00.000Z",
                  "data": { "hearingId": "%s", "hearingDay": "%s", "userId": "00000000-0000-0000-0000-000000000099" }
                }]
                """.formatted(identity.hearingId(), identity.hearingId(), identity.hearingDay());

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/hearing-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void assertMatchesExpected(final HearingIdentity identity, final String defendantId,
                                        final Path expectedFile) throws Exception {
        final String expectedJson = Files.readString(expectedFile);
        final String actualJson = mockMvc.perform(MockMvcRequestBuilders.get(
                        "/cases/{caseURN}/hearings/{hearingId}/defendants/{defendantId}",
                        identity.caseUrn(), identity.hearingId(), defendantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.STRICT);
    }

    private HearingIdentity parseIdentity(final Path fixtureRoot) throws Exception {
        // parse hearing.id / hearing.hearingDays[0].sittingDay (date portion) /
        // hearing.prosecutionCases[0].prosecutionCaseIdentifier.caseURN out of
        // fixtureRoot.resolve("event.json") — implement with the same JSON library already used
        // elsewhere in this repo (confirm: com.fasterxml.jackson vs tools.jackson) rather than
        // introducing a new one.
    }

    private record HearingIdentity(String hearingId, String hearingDay, String caseUrn) {
    }
}
```

Confirm which Jackson package (`com.fasterxml.jackson.*` vs `tools.jackson.*`) the rest of this
repo's test code uses before implementing `parseIdentity` (`grep -rn "import.*jackson" src/test`)
— per `shared-code-rules.md`'s note that sibling repos are not guaranteed to be on the same
Jackson major version; don't assume.

- [ ] **Step 2: Run it**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.integration.e2e.driftdetection.PcrDriftDetectionIntegrationTest'
```

Expected: PASS for all three defendants — the two with a real PDF matching their captured-and-verified
`expected/*.json`, and the third matching `[]`. If it fails, the diff is between Task 3's captured
snapshot and a fresh run — re-check whether something about the ingestion path is non-deterministic
(it shouldn't be, per ADR-008's determinism check) before assuming the fixture itself is wrong.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/uk/gov/hmcts/cp/integration/e2e/driftdetection/PcrDriftDetectionIntegrationTest.java
git commit -m "$(cat <<'EOF'
test(pcr): add PcrDriftDetectionIntegrationTest

Parameterized over every drift-detection/* fixture folder — replays
the real hearing event through the ingestion/generation-gate/persistence
path and diffs each defendant's GET /pcr response against its verified
expected/*.json with a strict JSONAssert comparison (AMP-898).

AMP-898
EOF
)"
```

---

## Task 5: Full verification pass

**Files:** none (verification only)

- [ ] **Step 1: Run the complete test suite**

```bash
docker compose up -d postgres redis
./gradlew build -x apiTest
```

Expected: all tests pass, including the three new/modified test classes.

- [ ] **Step 2: PMD and format checks**

```bash
./gradlew pmdMain spotlessCheck
```

Expected: both pass with zero violations.

- [ ] **Step 3: Confirm the throwaway bootstrap test from Task 3 is gone**

```bash
find src/test -iname "BootstrapExpectedFixturesTest*"
```

Expected: no output.

- [ ] **Step 4: Confirm the original untouched fixture directory is still present**

```bash
ls src/test/resources/pcr-multiple-defendants-multiple-offences/
```

Expected: unchanged — this plan only added `drift-detection/`, it never modified or removed the
original directory (Task 1 Step 1's note).