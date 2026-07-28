# PCR Persistence Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `ResultsIngestionService` (proves completeness) → `CPVocabularyService`/`CPResultsPcrOrchestrator` (the generation gate) → a new `CPVersionEntityMapper` → the 7 existing JPA repositories, so a `Hearing_Resulted` event results in a `cp_version` row (plus children) for every defendant the legacy Function App would have generated a PCR for.

**Architecture:** `HearingResultedProcessorService` calls a new `ResultsIngestionService.ingestAndPersist(...)` method instead of discarding `ingestHearingResults(...)`'s result. For each prosecution case's defendants, it runs the existing (previously-uncalled) orchestrator gate, and on a match, finds-or-creates the shared `cp_case_hearing` row and persists a fresh `cp_version` row plus its children via a new `CPVersionEntityMapper`.

**Tech Stack:** Spring Boot 4.1.0, Spring Data JPA (existing entities/repositories, no schema change), JUnit 5 + Mockito, `PostgresInitialise`-based integration tests (already established in this repo) — no new dependencies.

**Design doc:** `docs/designs/2026-07-28-pcr-persistence-wiring-design.md` — read in full before starting; every task below implements one part of it.

## Global Constraints

- Java 25, Spring Boot 4.1.0, Jakarta EE — no `javax`.
- `-Werror` — no compiler warnings.
- Method size: ~20 lines target, 40 hard limit — extract private helpers rather than exceed it.
- No comments except non-obvious WHY (a hidden constraint, a workaround, a design-doc cross-reference) — never WHAT.
- No error handling for scenarios that cannot happen; validate only at real boundaries.
- Test method naming: `subject_should_doOutcome` or `subject_should_doOutcome_whenCondition` — no mixed styles in one class.
- Test fixtures: fixed, deterministic `UUID.fromString(...)` constants — never `UUID.randomUUID()` in test data.
- `@Mock` fields ordered before `@InjectMocks`; `@Captor`-annotated fields, never raw `ArgumentCaptor.forClass`, in any new test code.
- No `lenient()` unless the stub is genuinely conditionally exercised.
- Repository tests: `RepositoryIntegrationTestBase` (`@SpringBootTest` + `PostgresInitialise`) — never `@DataJpaTest`/Testcontainers. Requires `docker compose up -d postgres` running locally before executing (`jdbc:postgresql://localhost:5432/pcrdb`).
- No new Gradle dependencies — `spring-boot-starter-data-jpa`/`-flyway`/`postgresql` are already wired.
- Every new/changed production method needs a real, behavior-asserting test — no test that only proves a Lombok-generated getter/builder round-trips.

---

### Task 1: Domain model additions + `CPCaseHearingRepository.findByCaseUrnAndHearingId`

**Files:**
- Modify: `src/main/java/uk/gov/hmcts/cp/domain/HearingDetailsResponse.java`
- Modify: `src/main/java/uk/gov/hmcts/cp/repositories/CPCaseHearingRepository.java`
- Modify: `src/test/java/uk/gov/hmcts/cp/repositories/CPCaseHearingRepositoryTest.java`

**Interfaces:**
- Produces: `HearingDetailsResponse.JudicialResult.getOrderedDate(): LocalDate`, `HearingDetailsResponse.PersonDefendant.getPersonDetails(): PersonDetails`, `HearingDetailsResponse.PersonDetails` (getters: `getTitle/getFirstName/getMiddleName/getLastName/getDateOfBirth/getAddress`), `HearingDetailsResponse.Address` (getters: `getAddress1/getAddress2/getAddress3/getPostcode`), `CPCaseHearingRepository.findByCaseUrnAndHearingId(String, UUID): Optional<CPCaseHearingEntity>` — Task 4 depends on all of these.

- [ ] **Step 1: Add `orderedDate` to `JudicialResult`, and the `PersonDetails`/`Address` nested classes to `HearingDetailsResponse`**

In `src/main/java/uk/gov/hmcts/cp/domain/HearingDetailsResponse.java`, change the `JudicialResult` class:

```java
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class JudicialResult {
        private String cjsCode;
        private String label;
        private boolean isFinancialResult;
        private boolean isConvictedResult;
        // publishedForNows: the PCR eligibility flag (orchestrator design doc §3) — boxed,
        // not primitive, see CourtCentre.welshCourtCentre for why.
        private Boolean publishedForNows;
        // orderedDate: sourced for the persistence-wiring design's resolveActiveAt (design
        // doc §4.2) — needs a real fixture check, same caveat as publishedForNows was under.
        private LocalDate orderedDate;
        private NextHearing nextHearing;
        private List<JudicialResultPrompt> judicialResultPrompts;
    }
```

Change `PersonDefendant`:

```java
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class PersonDefendant {
        private CustodialEstablishment custodialEstablishment;
        // personDetails: confirmed present on CP's own hearing payload (ADR-004, updated
        // 28 Jul 2026) — no longer "deliberately absent".
        private PersonDetails personDetails;
    }
```

Add two new nested classes, after `CustodialEstablishment`:

```java
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class PersonDetails {
        private String title;
        private String firstName;
        private String middleName;
        private String lastName;
        private LocalDate dateOfBirth;
        private Address address;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Address {
        private String address1;
        private String address2;
        private String address3;
        private String postcode;
    }
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (pure additive field/class changes — nothing consumes them yet, so no other file breaks).

- [ ] **Step 3: Write the failing repository test for `findByCaseUrnAndHearingId`**

In `src/test/java/uk/gov/hmcts/cp/repositories/CPCaseHearingRepositoryTest.java`, add (after the existing `save_should_persistAndReturnEveryField_whenFindById` test):

```java
    @Transactional
    @Test
    void findByCaseUrnAndHearingId_should_returnEntity_whenMatchExists() {
        final CPCaseHearingEntity entity = CPCaseHearingEntity.builder()
                .id(ID)
                .caseUrn("ABCD1234567")
                .hearingId(HEARING_ID)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).withNano(0))
                .build();
        cpCaseHearingRepository.save(entity);

        final Optional<CPCaseHearingEntity> found =
                cpCaseHearingRepository.findByCaseUrnAndHearingId("ABCD1234567", HEARING_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(ID);
    }

    @Transactional
    @Test
    void findByCaseUrnAndHearingId_should_returnEmpty_whenNoMatch() {
        final Optional<CPCaseHearingEntity> found =
                cpCaseHearingRepository.findByCaseUrnAndHearingId("NOMATCH1234", HEARING_ID);

        assertThat(found).isEmpty();
    }
```

- [ ] **Step 4: Run test to verify it fails**

Run: `docker compose up -d postgres && ./gradlew test --tests 'uk.gov.hmcts.cp.repositories.CPCaseHearingRepositoryTest'`
Expected: COMPILE FAILURE — `findByCaseUrnAndHearingId` is not a method on `CPCaseHearingRepository`.

- [ ] **Step 5: Add the method to the repository**

Replace the contents of `src/main/java/uk/gov/hmcts/cp/repositories/CPCaseHearingRepository.java`:

```java
package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CPCaseHearingRepository extends JpaRepository<CPCaseHearingEntity, UUID> {

    Optional<CPCaseHearingEntity> findByCaseUrnAndHearingId(String caseUrn, UUID hearingId);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.repositories.CPCaseHearingRepositoryTest'`
Expected: PASS (both new tests, plus the existing `save_should_persistAndReturnEveryField_whenFindById`).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/domain/HearingDetailsResponse.java \
        src/main/java/uk/gov/hmcts/cp/repositories/CPCaseHearingRepository.java \
        src/test/java/uk/gov/hmcts/cp/repositories/CPCaseHearingRepositoryTest.java
git commit -m "feat(pcr): add orderedDate/PersonDetails to domain model, case-hearing lookup"
```

---

### Task 2: `NoOrderedDateFoundException` + `CPVersionEntityMapper` (case-hearing, case-marker, eligible-results)

**Files:**
- Create: `src/main/java/uk/gov/hmcts/cp/exceptions/NoOrderedDateFoundException.java`
- Create: `src/main/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapper.java`
- Create: `src/test/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapperTest.java`

**Interfaces:**
- Consumes: `HearingDetailsResponse.{ProsecutionCase,HearingDetail,Defendant,JudicialResult}` (Task 1/existing), `CPCaseHearingEntity`, `CPCaseMarkerEntity` (existing entities).
- Produces: `CPVersionEntityMapper.toCaseHearingEntity(ProsecutionCase, HearingDetail, UUID hearingId, OffsetDateTime createdAt): CPCaseHearingEntity`, `.toCaseMarkerEntities(ProsecutionCase, UUID caseHearingId): List<CPCaseMarkerEntity>`, `.eligibleResults(Defendant, HearingDetail): List<JudicialResult>` — Task 4 depends on all three. `NoOrderedDateFoundException(UUID hearingId)` — Task 4 depends on this.

- [ ] **Step 1: Write the failing mapper test**

Create `src/test/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapperTest.java`:

```java
package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplicationCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Respondent;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CPVersionEntityMapperTest {

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final String MASTER_DEFENDANT_ID = "33333333-3333-3333-3333-333333333333";
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-07-28T10:00:00Z").withOffsetSameInstant(ZoneOffset.UTC);

    @Mock
    private JudicialResultPromptParser promptParser;

    @InjectMocks
    private CPVersionEntityMapper mapper;

    @Test
    void toCaseHearingEntity_should_mapCaseUrnCourtHouseAndHearingDate() {
        final ProsecutionCase prosecutionCase = minimalProsecutionCase();
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().code("B01LY").name("Leeds Crown Court").build())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .courtApplications(List.of())
                .prosecutionCases(List.of())
                .build();

        final CPCaseHearingEntity result = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, CREATED_AT);

        assertThat(result.getCaseUrn()).isEqualTo(CASE_URN);
        assertThat(result.getHearingId()).isEqualTo(HEARING_ID);
        assertThat(result.getCourtHouseCode()).isEqualTo("B01LY");
        assertThat(result.getCourtHouseName()).isEqualTo("Leeds Crown Court");
        assertThat(result.getHearingDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(result.getHearingOutcome()).isNull();
        assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void toCaseMarkerEntities_should_mapEachMarkerCode() {
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of(CaseMarker.builder().markerTypeCode("DomesticViolence").build()))
                .defendants(List.of())
                .build();

        final List<CPCaseMarkerEntity> result = mapper.toCaseMarkerEntities(prosecutionCase, CASE_HEARING_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(result.get(0).getCode()).isEqualTo("DomesticViolence");
    }

    @Test
    void eligibleResults_should_includeDirectOffenceResults() {
        final JudicialResult result = JudicialResult.builder().cjsCode("1200").judicialResultPrompts(List.of()).build();
        final Offence offence = Offence.builder().judicialResults(List.of(result)).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final List<JudicialResult> eligible = mapper.eligibleResults(defendant, hearing);

        assertThat(eligible).containsExactly(result);
    }

    @Test
    void eligibleResults_should_includeLinkedCourtApplicationResults_whenMasterDefendantIdMatches() {
        final JudicialResult applicationResult = JudicialResult.builder().cjsCode("APP1").judicialResultPrompts(List.of()).build();
        final JudicialResult linkedOffenceResult = JudicialResult.builder().cjsCode("APP2").judicialResultPrompts(List.of()).build();
        final Offence linkedOffence = Offence.builder().judicialResults(List.of(linkedOffenceResult)).build();
        final CourtApplication matching = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .respondents(List.of(Respondent.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()))
                .courtApplicationCases(List.of(CourtApplicationCase.builder().offences(List.of(linkedOffence)).build()))
                .judicialResults(List.of(applicationResult))
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(matching)).build();

        final List<JudicialResult> eligible = mapper.eligibleResults(defendant, hearing);

        assertThat(eligible).containsExactlyInAnyOrder(applicationResult, linkedOffenceResult);
    }

    @Test
    void eligibleResults_should_excludeCourtApplication_whenMasterDefendantIdDoesNotMatch() {
        final CourtApplication other = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e60")
                .respondents(List.of(Respondent.builder().masterDefendantId("99999999-9999-9999-9999-999999999999").build()))
                .courtApplicationCases(List.of())
                .judicialResults(List.of(JudicialResult.builder().cjsCode("X").judicialResultPrompts(List.of()).build()))
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(other)).build();

        final List<JudicialResult> eligible = mapper.eligibleResults(defendant, hearing);

        assertThat(eligible).isEmpty();
    }

    private ProsecutionCase minimalProsecutionCase() {
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of())
                .build();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.mappers.CPVersionEntityMapperTest'`
Expected: COMPILE FAILURE — `CPVersionEntityMapper` doesn't exist yet.

- [ ] **Step 3: Create `NoOrderedDateFoundException`**

Create `src/main/java/uk/gov/hmcts/cp/exceptions/NoOrderedDateFoundException.java`:

```java
package uk.gov.hmcts.cp.exceptions;

import java.util.UUID;

// Legacy's own getOrderedDate has no designed fallback (PrisonCourtRegisterSubscriptions/
// index.js:52-57) — its .find() returns undefined when nobody has an orderedDate, and the
// next line throws a TypeError, silently swallowed by the enclosing try/catch. This replicates
// that failure outcome explicitly rather than inventing a fallback date (design doc §4.2).
public class NoOrderedDateFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoOrderedDateFoundException(final UUID hearingId) {
        super("No judicial result with an orderedDate found anywhere on hearingId " + hearingId);
    }
}
```

- [ ] **Step 4: Create `CPVersionEntityMapper` (case-hearing/case-marker/eligible-results only for now)**

Create `src/main/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapper.java`:

```java
package uk.gov.hmcts.cp.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class CPVersionEntityMapper {

    private final JudicialResultPromptParser promptParser;

    public CPCaseHearingEntity toCaseHearingEntity(final ProsecutionCase prosecutionCase, final HearingDetail hearing,
                                                    final UUID hearingId, final OffsetDateTime createdAt) {
        return CPCaseHearingEntity.builder()
                .id(UUID.randomUUID())
                .caseUrn(prosecutionCase.getProsecutionCaseIdentifier().getCaseURN())
                .hearingId(hearingId)
                .courtHouseCode(hearing.getCourtCentre() == null ? null : hearing.getCourtCentre().getCode())
                .courtHouseName(hearing.getCourtCentre() == null ? null : hearing.getCourtCentre().getName())
                .hearingDate(hearing.getHearingDays().isEmpty() ? null
                        : LocalDate.parse(hearing.getHearingDays().get(0).getSittingDay()))
                .createdAt(createdAt)
                .build();
        // hearingOutcome: left unset (null) — no confirmed CP source, data-store design doc §3
    }

    public List<CPCaseMarkerEntity> toCaseMarkerEntities(final ProsecutionCase prosecutionCase, final UUID caseHearingId) {
        return prosecutionCase.getCaseMarkers().stream()
                .map(m -> toCaseMarkerEntity(m, caseHearingId))
                .toList();
    }

    private CPCaseMarkerEntity toCaseMarkerEntity(final CaseMarker marker, final UUID caseHearingId) {
        return CPCaseMarkerEntity.builder()
                .id(UUID.randomUUID())
                .caseHearingId(caseHearingId)
                .code(marker.getMarkerTypeCode())
                .build();
    }

    public List<JudicialResult> eligibleResults(final Defendant defendant, final HearingDetail hearing) {
        final Stream<JudicialResult> direct = defendant.getOffences().stream()
                .flatMap(o -> o.getJudicialResults().stream());
        final Stream<JudicialResult> linked = matchingCourtApplications(defendant, hearing).stream()
                .flatMap(this::allResultsOf);
        return Stream.concat(direct, linked).toList();
    }

    private Stream<JudicialResult> allResultsOf(final CourtApplication application) {
        final Stream<JudicialResult> ownResults = application.getJudicialResults().stream();
        final Stream<JudicialResult> linkedOffenceResults = application.getCourtApplicationCases().stream()
                .flatMap(c -> c.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream());
        return Stream.concat(ownResults, linkedOffenceResults);
    }

    // Same masterDefendantId filter as PcrVersionMapper.toCourtApplications — kept consistent
    // with the phase-1 read path (design doc §4.3).
    private List<CourtApplication> matchingCourtApplications(final Defendant defendant, final HearingDetail hearing) {
        return hearing.getCourtApplications().stream()
                .filter(app -> app.getRespondents().stream()
                        .anyMatch(r -> defendant.getMasterDefendantId() != null
                                && defendant.getMasterDefendantId().equals(r.getMasterDefendantId())))
                .toList();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.mappers.CPVersionEntityMapperTest'`
Expected: PASS (all 5 tests). Note: `promptParser` is unused by these methods but stays injected — Task 3 needs it for `toWriteBundle`; Mockito's strict stubbing won't complain since it's never stubbed, only injected.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/exceptions/NoOrderedDateFoundException.java \
        src/main/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapper.java \
        src/test/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapperTest.java
git commit -m "feat(pcr): add NoOrderedDateFoundException, case-hearing/case-marker/eligible-results mapping"
```

---

### Task 3: `CPVersionEntityMapper.toWriteBundle` (cp_version + PII + court applications + offences + judicial results + prompts)

**Files:**
- Create: `src/main/java/uk/gov/hmcts/cp/mappers/CPVersionWriteBundle.java`
- Modify: `src/main/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapper.java`
- Modify: `src/test/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapperTest.java`

**Interfaces:**
- Consumes: `CPVersionEntity`, `CPCourtApplicationEntity`, `CPOffenceEntity`, `CPJudicialResultEntity`, `CPJudicialResultPromptEntity`, `CPNextHearingEmbeddable` (all existing entities), `JudicialResultPromptParser` (existing).
- Produces: `CPVersionWriteBundle(CPVersionEntity version, List<CPCourtApplicationEntity> courtApplications, List<CPOffenceEntity> offences, List<CPJudicialResultEntity> judicialResults, List<CPJudicialResultPromptEntity> judicialResultPrompts)`, `CPVersionEntityMapper.toWriteBundle(Defendant, HearingDetail, UUID caseHearingId, OffsetDateTime createdAt, OffsetDateTime expiresAt): CPVersionWriteBundle` — Task 4 depends on both.

- [ ] **Step 1: Write the failing tests**

Append to `src/test/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapperTest.java` (add these imports too: `java.math.BigDecimal`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDetails`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.Address`, `uk.gov.hmcts.cp.mappers.CPVersionWriteBundle`, and `import static org.mockito.Mockito.when;`):

```java
    private static final OffsetDateTime EXPIRES_AT = CREATED_AT.plusDays(30);

    @Test
    void toWriteBundle_should_setSurrogatePkAndCaseHearingIdAndTimestamps() {
        final Defendant defendant = minimalDefendant();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPVersionWriteBundle bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getCpVersionPk()).isNotNull();
        assertThat(bundle.version().getCaseHearingId()).isEqualTo(CASE_HEARING_ID);
        assertThat(bundle.version().getDefendantId()).isEqualTo(DEFENDANT_ID);
        assertThat(bundle.version().getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(bundle.version().getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(bundle.version().getSourceId()).isNull();
    }

    @Test
    void toWriteBundle_should_mapPersonDetailsAndAddress() {
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder()
                        .personDetails(PersonDetails.builder()
                                .title("Mr").firstName("John").middleName("Q").lastName("Doe")
                                .dateOfBirth(LocalDate.of(1990, 1, 31))
                                .address(Address.builder().address1("1 Example Street").address2("Townville")
                                        .address3("Countyshire").postcode("AB1 2CD").build())
                                .build())
                        .build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPVersionWriteBundle bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getTitle()).isEqualTo("Mr");
        assertThat(bundle.version().getFirstName()).isEqualTo("John");
        assertThat(bundle.version().getMiddleName()).isEqualTo("Q");
        assertThat(bundle.version().getLastName()).isEqualTo("Doe");
        assertThat(bundle.version().getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 31));
        assertThat(bundle.version().getAddressLine1()).isEqualTo("1 Example Street");
        assertThat(bundle.version().getAddressLine2()).isEqualTo("Townville");
        assertThat(bundle.version().getAddressLine3()).isEqualTo("Countyshire");
        assertThat(bundle.version().getAddressLine4()).isNull();
        assertThat(bundle.version().getPostCode()).isEqualTo("AB1 2CD");
    }

    @Test
    void toWriteBundle_should_leavePiiNull_whenNoPersonDetails() {
        final CPVersionWriteBundle bundle = mapper.toWriteBundle(minimalDefendant(),
                HearingDetail.builder().courtApplications(List.of()).build(), CASE_HEARING_ID, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.version().getFirstName()).isNull();
        assertThat(bundle.version().getDateOfBirth()).isNull();
    }

    @Test
    void toWriteBundle_should_mapDirectOffenceAndItsJudicialResultAndPrompts_withSurrogateOffenceId() {
        when(promptParser.fineAmount(any())).thenReturn(null);
        final JudicialResultPrompt prompt = JudicialResultPrompt.builder().promptReference("prisonOrganisationName").value("HMP Dovegate").build();
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").label("Imprisonment")
                .isFinancialResult(false).isConvictedResult(true)
                .judicialResultPrompts(List.of(prompt))
                .build();
        final Offence offence = Offence.builder().offenceCode("TH68001").listingNumber(1).judicialResults(List.of(result)).build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of()).build();

        final CPVersionWriteBundle bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.offences()).hasSize(1);
        final CPOffenceEntity offenceEntity = bundle.offences().get(0);
        assertThat(offenceEntity.getId()).isNotNull();
        assertThat(offenceEntity.getVersionPk()).isEqualTo(bundle.version().getCpVersionPk());
        assertThat(offenceEntity.getCourtApplicationId()).isNull();
        assertThat(offenceEntity.getCode()).isEqualTo("TH68001");
        assertThat(bundle.judicialResults()).hasSize(1);
        final CPJudicialResultEntity resultEntity = bundle.judicialResults().get(0);
        assertThat(resultEntity.getOffenceId()).isEqualTo(offenceEntity.getId());
        assertThat(resultEntity.getCourtApplicationId()).isNull();
        assertThat(resultEntity.getResultCode()).isEqualTo("1200");
        assertThat(resultEntity.getFinancial()).isFalse();
        assertThat(resultEntity.getConvicted()).isTrue();
        assertThat(bundle.judicialResultPrompts()).hasSize(1);
        assertThat(bundle.judicialResultPrompts().get(0).getJudicialResultId()).isEqualTo(resultEntity.getId());
        assertThat(bundle.judicialResultPrompts().get(0).getPromptReference()).isEqualTo("prisonOrganisationName");
    }

    @Test
    void toWriteBundle_should_mapLinkedCourtApplicationAndItsOffenceAndOwnResult() {
        when(promptParser.fineAmount(any())).thenReturn(null);
        final JudicialResult linkedOffenceResult = JudicialResult.builder().cjsCode("LINK1").judicialResultPrompts(List.of()).build();
        final Offence linkedOffence = Offence.builder().offenceCode("LINKOFF").judicialResults(List.of(linkedOffenceResult)).build();
        final JudicialResult applicationResult = JudicialResult.builder().cjsCode("APP1").judicialResultPrompts(List.of()).build();
        final CourtApplication application = CourtApplication.builder()
                .id("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f")
                .applicationReference("REF1").type("Bail")
                .respondents(List.of(Respondent.builder().masterDefendantId(MASTER_DEFENDANT_ID).build()))
                .courtApplicationCases(List.of(CourtApplicationCase.builder().offences(List.of(linkedOffence)).build()))
                .judicialResults(List.of(applicationResult))
                .build();
        final Defendant defendant = Defendant.builder()
                .id(DEFENDANT_ID.toString())
                .masterDefendantId(MASTER_DEFENDANT_ID)
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final HearingDetail hearing = HearingDetail.builder().courtApplications(List.of(application)).build();

        final CPVersionWriteBundle bundle = mapper.toWriteBundle(defendant, hearing, CASE_HEARING_ID, CREATED_AT, EXPIRES_AT);

        assertThat(bundle.courtApplications()).hasSize(1);
        final CPCourtApplicationEntity applicationEntity = bundle.courtApplications().get(0);
        assertThat(applicationEntity.getId()).isEqualTo(UUID.fromString("a9b8c7d6-e5f4-4321-9876-0a1b2c3d4e5f"));
        assertThat(applicationEntity.getVersionPk()).isEqualTo(bundle.version().getCpVersionPk());
        assertThat(applicationEntity.getReference()).isEqualTo("REF1");
        assertThat(bundle.offences()).hasSize(1);
        assertThat(bundle.offences().get(0).getCourtApplicationId()).isEqualTo(applicationEntity.getId());
        assertThat(bundle.offences().get(0).getVersionPk()).isNull();
        assertThat(bundle.judicialResults()).hasSize(2);
        assertThat(bundle.judicialResults()).extracting("resultCode").containsExactlyInAnyOrder("LINK1", "APP1");
        final CPJudicialResultEntity applicationLevelResult = bundle.judicialResults().stream()
                .filter(r -> "APP1".equals(r.getResultCode())).findFirst().orElseThrow();
        assertThat(applicationLevelResult.getCourtApplicationId()).isEqualTo(applicationEntity.getId());
        assertThat(applicationLevelResult.getOffenceId()).isNull();
    }
```

Add these imports at the top: `uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt`, `uk.gov.hmcts.cp.entities.CPCourtApplicationEntity`, `uk.gov.hmcts.cp.entities.CPJudicialResultEntity`, `uk.gov.hmcts.cp.entities.CPOffenceEntity`, and `static org.mockito.ArgumentMatchers.any`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.mappers.CPVersionEntityMapperTest'`
Expected: COMPILE FAILURE — `toWriteBundle` and `CPVersionWriteBundle` don't exist yet.

- [ ] **Step 3: Create the `CPVersionWriteBundle` record**

Create `src/main/java/uk/gov/hmcts/cp/mappers/CPVersionWriteBundle.java`:

```java
package uk.gov.hmcts.cp.mappers;

import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;

import java.util.List;

public record CPVersionWriteBundle(
        CPVersionEntity version,
        List<CPCourtApplicationEntity> courtApplications,
        List<CPOffenceEntity> offences,
        List<CPJudicialResultEntity> judicialResults,
        List<CPJudicialResultPromptEntity> judicialResultPrompts) {
}
```

- [ ] **Step 4: Implement `toWriteBundle` and its private helpers on `CPVersionEntityMapper`**

Add these imports to `src/main/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapper.java`: `uk.gov.hmcts.cp.domain.HearingDetailsResponse.Address`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.CustodialEstablishment`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDetails`, `uk.gov.hmcts.cp.entities.CPCourtApplicationEntity`, `uk.gov.hmcts.cp.entities.CPJudicialResultEntity`, `uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity`, `uk.gov.hmcts.cp.entities.CPNextHearingEmbeddable`, `uk.gov.hmcts.cp.entities.CPOffenceEntity`, `uk.gov.hmcts.cp.entities.CPVersionEntity`, `java.math.BigDecimal`, `java.util.ArrayList`, `java.util.Objects`.

Add this method and its private helpers to the class:

```java
    public CPVersionWriteBundle toWriteBundle(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                               final OffsetDateTime createdAt, final OffsetDateTime expiresAt) {
        final CPVersionEntity version = toVersionEntity(defendant, hearing, caseHearingId, createdAt, expiresAt);
        final List<CourtApplication> linkedApplications = matchingCourtApplications(defendant, hearing);
        final List<CPCourtApplicationEntity> courtApplications = linkedApplications.stream()
                .map(a -> toCourtApplicationEntity(a, version.getCpVersionPk()))
                .toList();
        final List<CPOffenceEntity> offences = new ArrayList<>();
        final List<CPJudicialResultEntity> judicialResults = new ArrayList<>();
        final List<CPJudicialResultPromptEntity> prompts = new ArrayList<>();
        defendant.getOffences().forEach(o -> addDirectOffence(o, version.getCpVersionPk(), offences, judicialResults, prompts));
        for (int i = 0; i < linkedApplications.size(); i++) {
            addLinkedApplicationContent(linkedApplications.get(i), courtApplications.get(i).getId(), offences, judicialResults, prompts);
        }
        return new CPVersionWriteBundle(version, courtApplications, offences, judicialResults, prompts);
    }

    private void addLinkedApplicationContent(final CourtApplication application, final UUID courtApplicationId,
                                              final List<CPOffenceEntity> offences, final List<CPJudicialResultEntity> judicialResults,
                                              final List<CPJudicialResultPromptEntity> prompts) {
        application.getCourtApplicationCases().stream()
                .flatMap(c -> c.getOffences().stream())
                .forEach(o -> addLinkedOffence(o, courtApplicationId, offences, judicialResults, prompts));
        application.getJudicialResults().forEach(r -> addResult(r, null, courtApplicationId, judicialResults, prompts));
    }

    private CPVersionEntity toVersionEntity(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                             final OffsetDateTime createdAt, final OffsetDateTime expiresAt) {
        final CPVersionEntity.CPVersionEntityBuilder builder = CPVersionEntity.builder()
                .cpVersionPk(UUID.randomUUID())
                .sourceId(null) // no event-correlation pipeline yet — data-store design doc §3
                .defendantId(UUID.fromString(defendant.getId()))
                .caseHearingId(caseHearingId)
                .custodyLocation(toCustodyLocation(defendant))
                .masterDefendantId(masterDefendantId(defendant))
                .nextHearing(toNextHearingEmbeddable(hearing))
                .createdAt(createdAt)
                .expiresAt(expiresAt);
        applyPersonDetails(builder, defendant.getPersonDefendant().getPersonDetails());
        return builder.build();
    }

    private UUID masterDefendantId(final Defendant defendant) {
        return defendant.getMasterDefendantId() == null ? null : UUID.fromString(defendant.getMasterDefendantId());
    }

    private String toCustodyLocation(final Defendant defendant) {
        final CustodialEstablishment establishment = defendant.getPersonDefendant().getCustodialEstablishment();
        return establishment == null ? null : establishment.getName();
    }

    private void applyPersonDetails(final CPVersionEntity.CPVersionEntityBuilder builder, final PersonDetails personDetails) {
        if (personDetails == null) {
            return;
        }
        builder.title(personDetails.getTitle())
                .firstName(personDetails.getFirstName())
                .middleName(personDetails.getMiddleName())
                .lastName(personDetails.getLastName())
                .dateOfBirth(personDetails.getDateOfBirth());
        applyAddress(builder, personDetails.getAddress());
    }

    private void applyAddress(final CPVersionEntity.CPVersionEntityBuilder builder, final Address address) {
        if (address == null) {
            return;
        }
        builder.addressLine1(address.getAddress1())
                .addressLine2(address.getAddress2())
                .addressLine3(address.getAddress3())
                .postCode(address.getPostcode());
        // addressLine4/addressLine5: left null — no 4th/5th address line upstream
    }

    private CPNextHearingEmbeddable toNextHearingEmbeddable(final HearingDetail hearing) {
        // Same provisional, hearing-wide "first non-null nextHearing found" scan as
        // PcrVersionMapper.findNextHearing — kept consistent with phase-1's read path,
        // not re-scoped per-defendant (design doc §4.5/§10 still calls this unconfirmed).
        return hearing.getProsecutionCases().stream()
                .flatMap(c -> c.getDefendants().stream())
                .flatMap(d -> d.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream())
                .map(JudicialResult::getNextHearing)
                .filter(Objects::nonNull)
                .findFirst()
                .map(n -> CPNextHearingEmbeddable.builder().date(n.getDate()).build())
                .orElse(null);
    }

    private CPCourtApplicationEntity toCourtApplicationEntity(final CourtApplication application, final UUID versionPk) {
        return CPCourtApplicationEntity.builder()
                .id(UUID.fromString(application.getId()))
                .versionPk(versionPk)
                .reference(application.getApplicationReference())
                .type(application.getType())
                .build();
        // decision/decisionDate/response/responseDate: no confirmed CP source, same as PcrVersionMapper.toCourtApplication
    }

    private void addDirectOffence(final Offence offence, final UUID versionPk, final List<CPOffenceEntity> offences,
                                   final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        final CPOffenceEntity offenceEntity = toOffenceEntity(offence, versionPk, null);
        offences.add(offenceEntity);
        offence.getJudicialResults().forEach(r -> addResult(r, offenceEntity.getId(), null, judicialResults, prompts));
    }

    private void addLinkedOffence(final Offence offence, final UUID courtApplicationId, final List<CPOffenceEntity> offences,
                                   final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        final CPOffenceEntity offenceEntity = toOffenceEntity(offence, null, courtApplicationId);
        offences.add(offenceEntity);
        offence.getJudicialResults().forEach(r -> addResult(r, offenceEntity.getId(), null, judicialResults, prompts));
    }

    private CPOffenceEntity toOffenceEntity(final Offence offence, final UUID versionPk, final UUID courtApplicationId) {
        return CPOffenceEntity.builder()
                .id(UUID.randomUUID()) // surrogate for now — CP's real offence id isn't sourceable yet, design doc §5
                .versionPk(versionPk)
                .courtApplicationId(courtApplicationId)
                .code(offence.getOffenceCode())
                .startDate(offence.getStartDate())
                .endDate(offence.getEndDate())
                .listingNumber(offence.getListingNumber())
                .convictionDate(offence.getConvictionDate())
                .build();
        // title/wording/pleaValue/pleaDate/verdictCode: left unset, same as PcrVersionMapper.toOffence
    }

    private void addResult(final JudicialResult result, final UUID offenceId, final UUID courtApplicationId,
                            final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        final CPJudicialResultEntity resultEntity = toJudicialResultEntity(result, offenceId, courtApplicationId);
        judicialResults.add(resultEntity);
        prompts.addAll(toPromptEntities(result, resultEntity.getId()));
    }

    private CPJudicialResultEntity toJudicialResultEntity(final JudicialResult result, final UUID offenceId, final UUID courtApplicationId) {
        final Double fineAmount = promptParser.fineAmount(result);
        return CPJudicialResultEntity.builder()
                .id(UUID.randomUUID())
                .offenceId(offenceId)
                .courtApplicationId(courtApplicationId)
                .resultCode(result.getCjsCode())
                .resultText(result.getLabel())
                .financial(result.isFinancialResult())
                .convicted(result.isConvictedResult())
                .concurrent(promptParser.concurrent(result))
                .consecutiveToDate(promptParser.consecutiveToDate(result))
                .consecutiveToCourtName(promptParser.consecutiveToCourtName(result))
                .fineAmount(fineAmount == null ? null : BigDecimal.valueOf(fineAmount))
                .imprisonmentPeriod(promptParser.imprisonmentPeriod(result))
                .totalCustodialPeriod(promptParser.totalCustodialPeriod(result))
                .build();
        // postHearingCustodyStatus/category: need a real ResultDefinition lookup — left null,
        // same as PcrVersionMapper.toJudicialResult
    }

    private List<CPJudicialResultPromptEntity> toPromptEntities(final JudicialResult result, final UUID judicialResultId) {
        return result.getJudicialResultPrompts().stream()
                .map(p -> CPJudicialResultPromptEntity.builder()
                        .id(UUID.randomUUID())
                        .judicialResultId(judicialResultId)
                        .promptReference(p.getPromptReference())
                        .value(p.getValue())
                        .build())
                .toList();
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.mappers.CPVersionEntityMapperTest'`
Expected: PASS (all tests, including the 5 from Task 2 and the 5 new ones).

- [ ] **Step 6: Run PMD and Spotless**

Run: `./gradlew pmdMain spotlessCheck`
Expected: no violations. `CPVersionEntityMapper` will be a long file (~15 private methods) — this is expected and matches `PcrVersionMapper`'s own established shape, not a new pattern.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/mappers/CPVersionWriteBundle.java \
        src/main/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapper.java \
        src/test/java/uk/gov/hmcts/cp/mappers/CPVersionEntityMapperTest.java
git commit -m "feat(pcr): map cp_version + PII + court applications + offences + results to entities"
```

---

### Task 4: `ResultsIngestionService.ingestAndPersist` — orchestrator gate, find-or-create, persist

**Files:**
- Modify: `src/main/java/uk/gov/hmcts/cp/services/ResultsIngestionService.java`
- Modify: `src/test/java/uk/gov/hmcts/cp/services/ResultsIngestionServiceTest.java`

**Interfaces:**
- Consumes: `CPVersionEntityMapper` (Task 2/3), `NoOrderedDateFoundException` (Task 2), `CPCaseHearingRepository.findByCaseUrnAndHearingId` (Task 1), `CPVocabularyService.compute(Defendant, HearingDetail): CPVocabulary` (existing), `CPResultsPcrOrchestrator.excludePublishedForNows(List<JudicialResult>): List<JudicialResult>` / `.isPrisonCourtRegisterRequired(CPVocabulary, List<JudicialResult>, LocalDate): boolean` (existing), `ClockService.nowOffsetUTC(): OffsetDateTime` (existing), all 7 repositories (existing, bare `JpaRepository`).
- Produces: `ResultsIngestionService.ingestAndPersist(UUID hearingId, String hearingDay): void` — Task 5 depends on this.

- [ ] **Step 1: Write the failing tests**

In `src/test/java/uk/gov/hmcts/cp/services/ResultsIngestionServiceTest.java`, add these imports: `org.mockito.Spy`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier`, `uk.gov.hmcts.cp.domain.orchestrator.CPVocabulary`, `uk.gov.hmcts.cp.entities.CPCaseHearingEntity`, `uk.gov.hmcts.cp.entities.CPVersionEntity`, `uk.gov.hmcts.cp.exceptions.NoOrderedDateFoundException`, `uk.gov.hmcts.cp.mappers.CPVersionEntityMapper`, `uk.gov.hmcts.cp.mappers.CPVersionWriteBundle`, `uk.gov.hmcts.cp.repositories.CPCaseHearingRepository`, `uk.gov.hmcts.cp.repositories.CPCaseMarkerRepository`, `uk.gov.hmcts.cp.repositories.CPCourtApplicationRepository`, `uk.gov.hmcts.cp.repositories.CPJudicialResultPromptRepository`, `uk.gov.hmcts.cp.repositories.CPJudicialResultRepository`, `uk.gov.hmcts.cp.repositories.CPOffenceRepository`, `uk.gov.hmcts.cp.repositories.CPVersionRepository`, `uk.gov.hmcts.cp.services.orchestrator.CPResultsPcrOrchestrator`, `uk.gov.hmcts.cp.services.orchestrator.CPVocabularyService`, `java.time.Clock`, `java.time.Instant`, `java.time.LocalDate`, `java.time.ZoneOffset`, and `static org.mockito.ArgumentMatchers.eq`.

Add these `@Mock`/`@Spy` fields, inserted before the existing `@InjectMocks private ResultsIngestionService ingestionService;`:

```java
    @Mock
    private CPVocabularyService vocabularyService;
    @Mock
    private CPResultsPcrOrchestrator orchestrator;
    @Mock
    private CPVersionEntityMapper entityMapper;
    @Spy
    private ClockService clockService = new ClockService(Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC));
    @Mock
    private CPCaseHearingRepository caseHearingRepository;
    @Mock
    private CPCaseMarkerRepository caseMarkerRepository;
    @Mock
    private CPVersionRepository versionRepository;
    @Mock
    private CPCourtApplicationRepository courtApplicationRepository;
    @Mock
    private CPOffenceRepository offenceRepository;
    @Mock
    private CPJudicialResultRepository judicialResultRepository;
    @Mock
    private CPJudicialResultPromptRepository judicialResultPromptRepository;
```

Add these test methods and fixture helpers to the class:

```java
    private static final String CASE_URN = "ABCD1234567";
    private static final UUID CASE_HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000044");
    private static final CPVocabulary VOCABULARY = CPVocabulary.builder()
            .prosecutorMajorCreditor(List.of()).nonProsecutorMajorCreditor(List.of()).build();

    @Test
    void ingestAndPersist_should_createCaseHearingAndPersistVersion_whenRequiredAndNotYetCreated() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithOneDefendant());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(orchestrator.excludePublishedForNows(any())).thenReturn(List.of());
        when(orchestrator.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.empty());
        final CPCaseHearingEntity caseHearingEntity = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(entityMapper.toCaseHearingEntity(any(), any(), eq(HEARING_ID), any())).thenReturn(caseHearingEntity);
        when(entityMapper.toCaseMarkerEntities(any(), eq(CASE_HEARING_ID))).thenReturn(List.of());
        final CPVersionWriteBundle bundle = emptyBundle();
        when(entityMapper.toWriteBundle(any(), any(), eq(CASE_HEARING_ID), any(), any())).thenReturn(bundle);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(caseHearingRepository).save(caseHearingEntity);
        verify(versionRepository).save(bundle.version());
        verify(courtApplicationRepository).saveAll(bundle.courtApplications());
        verify(offenceRepository).saveAll(bundle.offences());
        verify(judicialResultRepository).saveAll(bundle.judicialResults());
        verify(judicialResultPromptRepository).saveAll(bundle.judicialResultPrompts());
    }

    @Test
    void ingestAndPersist_should_reuseExistingCaseHearing_whenAlreadyFound() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithOneDefendant());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(orchestrator.excludePublishedForNows(any())).thenReturn(List.of());
        when(orchestrator.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(true);
        final CPCaseHearingEntity existing = CPCaseHearingEntity.builder().id(CASE_HEARING_ID).build();
        when(caseHearingRepository.findByCaseUrnAndHearingId(CASE_URN, HEARING_ID)).thenReturn(Optional.of(existing));
        when(entityMapper.toWriteBundle(any(), any(), eq(CASE_HEARING_ID), any(), any())).thenReturn(emptyBundle());

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(caseHearingRepository, never()).save(any());
        verify(caseMarkerRepository, never()).saveAll(any());
    }

    @Test
    void ingestAndPersist_should_skipDefendant_whenPcrNotRequired() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithOneDefendant());
        when(vocabularyService.compute(any(), any())).thenReturn(VOCABULARY);
        when(entityMapper.eligibleResults(any(), any())).thenReturn(List.of());
        when(orchestrator.excludePublishedForNows(any())).thenReturn(List.of());
        when(orchestrator.isPrisonCourtRegisterRequired(any(), any(), any())).thenReturn(false);

        ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY);

        verify(caseHearingRepository, never()).findByCaseUrnAndHearingId(any(), any());
        verify(versionRepository, never()).save(any());
    }

    @Test
    void ingestAndPersist_should_throwNoOrderedDateFoundException_whenNoResultHasOrderedDate() {
        when(cacheClient.get(HEARING_ID, HEARING_DAY)).thenReturn(Optional.empty());
        when(resultsClient.getHearingDetails(HEARING_ID)).thenReturn(hearingWithNoOrderedDate());

        assertThatThrownBy(() -> ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY))
                .isInstanceOf(NoOrderedDateFoundException.class);

        verify(vocabularyService, never()).compute(any(), any());
    }

    private CPVersionWriteBundle emptyBundle() {
        return new CPVersionWriteBundle(
                CPVersionEntity.builder().cpVersionPk(UUID.fromString("00000000-0000-0000-0000-000000000055")).build(),
                List.of(), List.of(), List.of(), List.of());
    }

    private HearingDetailsResponse hearingWithOneDefendant() {
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").orderedDate(LocalDate.of(2026, 7, 15)).judicialResultPrompts(List.of()).build();
        final Offence offence = Offence.builder().judicialResults(List.of(result)).build();
        final Defendant defendant = Defendant.builder()
                .id("11111111-1111-1111-1111-111111111111")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of(defendant))
                .build();
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .courtCentre(CourtCentre.builder().build())
                        .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                        .prosecutionCases(List.of(prosecutionCase))
                        .courtApplications(List.of())
                        .build())
                .build();
    }

    private HearingDetailsResponse hearingWithNoOrderedDate() {
        final Defendant defendant = Defendant.builder()
                .id("11111111-1111-1111-1111-111111111111")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of())
                .build();
        final ProsecutionCase prosecutionCase = ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .caseMarkers(List.of())
                .defendants(List.of(defendant))
                .build();
        return HearingDetailsResponse.builder()
                .hearing(HearingDetailsResponse.HearingDetail.builder()
                        .prosecutionCases(List.of(prosecutionCase))
                        .courtApplications(List.of())
                        .build())
                .build();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.services.ResultsIngestionServiceTest'`
Expected: COMPILE FAILURE — `ingestAndPersist` doesn't exist on `ResultsIngestionService`, and the constructor doesn't accept the new mock fields yet.

- [ ] **Step 3: Implement `ingestAndPersist` on `ResultsIngestionService`**

Add these imports to `src/main/java/uk/gov/hmcts/cp/services/ResultsIngestionService.java`: `org.springframework.transaction.annotation.Transactional`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult`, `uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase`, `uk.gov.hmcts.cp.domain.orchestrator.CPVocabulary`, `uk.gov.hmcts.cp.entities.CPCaseHearingEntity`, `uk.gov.hmcts.cp.exceptions.NoOrderedDateFoundException`, `uk.gov.hmcts.cp.mappers.CPVersionEntityMapper`, `uk.gov.hmcts.cp.mappers.CPVersionWriteBundle`, `uk.gov.hmcts.cp.repositories.CPCaseHearingRepository`, `uk.gov.hmcts.cp.repositories.CPCaseMarkerRepository`, `uk.gov.hmcts.cp.repositories.CPCourtApplicationRepository`, `uk.gov.hmcts.cp.repositories.CPJudicialResultPromptRepository`, `uk.gov.hmcts.cp.repositories.CPJudicialResultRepository`, `uk.gov.hmcts.cp.repositories.CPOffenceRepository`, `uk.gov.hmcts.cp.repositories.CPVersionRepository`, `uk.gov.hmcts.cp.services.orchestrator.CPResultsPcrOrchestrator`, `uk.gov.hmcts.cp.services.orchestrator.CPVocabularyService`, `java.time.LocalDate`, `java.time.OffsetDateTime`, `java.util.Objects`.

Add these fields (as additional `final` fields alongside the existing 5 — `@RequiredArgsConstructor` regenerates the constructor automatically):

```java
    private final CPVocabularyService vocabularyService;
    private final CPResultsPcrOrchestrator orchestrator;
    private final CPVersionEntityMapper entityMapper;
    private final ClockService clockService;
    private final CPCaseHearingRepository caseHearingRepository;
    private final CPCaseMarkerRepository caseMarkerRepository;
    private final CPVersionRepository versionRepository;
    private final CPCourtApplicationRepository courtApplicationRepository;
    private final CPOffenceRepository offenceRepository;
    private final CPJudicialResultRepository judicialResultRepository;
    private final CPJudicialResultPromptRepository judicialResultPromptRepository;
```

Add these methods (after the existing `ingestHearingResults` and before `deserializeCachedHearingResults`):

```java
    @Transactional
    public void ingestAndPersist(final UUID hearingId, final String hearingDay) {
        final HearingDetailsResponse hearingDetails = ingestHearingResults(hearingId, hearingDay);
        final HearingDetail hearing = hearingDetails.getHearing();
        final LocalDate activeAt = resolveActiveAt(hearing, hearingId);
        hearing.getProsecutionCases().forEach(c -> processProsecutionCase(c, hearing, hearingId, activeAt));
    }

    private void processProsecutionCase(final ProsecutionCase prosecutionCase, final HearingDetail hearing,
                                         final UUID hearingId, final LocalDate activeAt) {
        UUID caseHearingId = null;
        for (final Defendant defendant : prosecutionCase.getDefendants()) {
            if (!isPcrRequired(defendant, hearing, activeAt)) {
                log.info("PCR not required for hearingId:{} defendantId:{} — skipping", hearingId, defendant.getId());
                continue;
            }
            caseHearingId = caseHearingId == null ? findOrCreateCaseHearing(prosecutionCase, hearing, hearingId) : caseHearingId;
            persistVersion(defendant, hearing, caseHearingId);
        }
    }

    private boolean isPcrRequired(final Defendant defendant, final HearingDetail hearing, final LocalDate activeAt) {
        final CPVocabulary vocabulary = vocabularyService.compute(defendant, hearing);
        final List<JudicialResult> eligibleResults = orchestrator.excludePublishedForNows(entityMapper.eligibleResults(defendant, hearing));
        return orchestrator.isPrisonCourtRegisterRequired(vocabulary, eligibleResults, activeAt);
    }

    private UUID findOrCreateCaseHearing(final ProsecutionCase prosecutionCase, final HearingDetail hearing, final UUID hearingId) {
        final String caseUrn = prosecutionCase.getProsecutionCaseIdentifier().getCaseURN();
        return caseHearingRepository.findByCaseUrnAndHearingId(caseUrn, hearingId)
                .map(CPCaseHearingEntity::getId)
                .orElseGet(() -> createCaseHearing(prosecutionCase, hearing, hearingId));
    }

    private UUID createCaseHearing(final ProsecutionCase prosecutionCase, final HearingDetail hearing, final UUID hearingId) {
        final CPCaseHearingEntity entity = entityMapper.toCaseHearingEntity(prosecutionCase, hearing, hearingId, clockService.nowOffsetUTC());
        caseHearingRepository.save(entity);
        caseMarkerRepository.saveAll(entityMapper.toCaseMarkerEntities(prosecutionCase, entity.getId()));
        return entity.getId();
    }

    private void persistVersion(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId) {
        final OffsetDateTime createdAt = clockService.nowOffsetUTC();
        final CPVersionWriteBundle bundle = entityMapper.toWriteBundle(defendant, hearing, caseHearingId, createdAt, createdAt.plusDays(30));
        versionRepository.save(bundle.version());
        courtApplicationRepository.saveAll(bundle.courtApplications());
        offenceRepository.saveAll(bundle.offences());
        judicialResultRepository.saveAll(bundle.judicialResults());
        judicialResultPromptRepository.saveAll(bundle.judicialResultPrompts());
    }

    private LocalDate resolveActiveAt(final HearingDetail hearing, final UUID hearingId) {
        return hearing.getProsecutionCases().stream()
                .flatMap(c -> c.getDefendants().stream())
                .flatMap(d -> d.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream())
                .map(JudicialResult::getOrderedDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new NoOrderedDateFoundException(hearingId));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.services.ResultsIngestionServiceTest'`
Expected: PASS (all tests, existing + 4 new).

- [ ] **Step 5: Run PMD and Spotless**

Run: `./gradlew pmdMain spotlessCheck`
Expected: no violations.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/services/ResultsIngestionService.java \
        src/test/java/uk/gov/hmcts/cp/services/ResultsIngestionServiceTest.java
git commit -m "feat(pcr): wire ingestAndPersist — orchestrator gate, find-or-create, entity persistence"
```

---

### Task 5: Wire `HearingResultedProcessorService` to call `ingestAndPersist`

**Files:**
- Modify: `src/main/java/uk/gov/hmcts/cp/servicebus/services/HearingResultedProcessorService.java:63`
- Modify: `src/test/java/uk/gov/hmcts/cp/servicebus/services/HearingResultedProcessorServiceTest.java`

**Interfaces:**
- Consumes: `ResultsIngestionService.ingestAndPersist(UUID, String): void` (Task 4).

- [ ] **Step 1: Update the failing assertions in the existing test**

In `src/test/java/uk/gov/hmcts/cp/servicebus/services/HearingResultedProcessorServiceTest.java`, change all four references from `ingestHearingResults` to `ingestAndPersist`:

```java
    @Test
    void onMessage_should_unwrapEnvelopeAndCompleteMessage_whenIngestSucceeds() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(ENVELOPE_JSON));

        processorService.onMessage(context);

        verify(ingestionService).ingestAndPersist(HEARING_ID, HEARING_DAY);
        verify(context).complete();
    }

    @Test
    void onMessage_should_delegateToScheduleRetry_whenIngestThrowsIncompleteHearingDetailsException() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(ENVELOPE_JSON));
        when(ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY))
                .thenThrow(new IncompleteHearingDetailsException(HEARING_ID));

        processorService.onMessage(context);

        verify(ingestionService).escalateOrDeadLetter(eq(context), any(HearingResultedPointer.class));
        verify(context, never()).complete();
        verify(context, never()).deadLetter();
    }

    @Test
    void onMessage_should_deadLetter_whenIngestThrowsUnexpectedException() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(ENVELOPE_JSON));
        when(ingestionService.ingestAndPersist(HEARING_ID, HEARING_DAY)).thenThrow(new RuntimeException("boom"));

        processorService.onMessage(context);

        verify(context).deadLetter();
        verify(context, never()).complete();
    }

    @Test
    void onMessage_should_deadLetter_whenEnvelopeIsMalformed() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString("not-json"));

        processorService.onMessage(context);

        verify(context).deadLetter();
        verify(context, never()).complete();
        verify(ingestionService, never()).ingestAndPersist(any(), any());
    }
```

Note: `ingestAndPersist` returns `void`, so `when(ingestionService.ingestAndPersist(...)).thenThrow(...)` won't compile as written above — Mockito needs `doThrow(...).when(ingestionService).ingestAndPersist(...)` for void methods. Use this form instead for the two throwing tests:

```java
    @Test
    void onMessage_should_delegateToScheduleRetry_whenIngestThrowsIncompleteHearingDetailsException() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(ENVELOPE_JSON));
        doThrow(new IncompleteHearingDetailsException(HEARING_ID))
                .when(ingestionService).ingestAndPersist(HEARING_ID, HEARING_DAY);

        processorService.onMessage(context);

        verify(ingestionService).escalateOrDeadLetter(eq(context), any(HearingResultedPointer.class));
        verify(context, never()).complete();
        verify(context, never()).deadLetter();
    }

    @Test
    void onMessage_should_deadLetter_whenIngestThrowsUnexpectedException() {
        when(context.getMessage()).thenReturn(message);
        when(message.getBody()).thenReturn(BinaryData.fromString(ENVELOPE_JSON));
        doThrow(new RuntimeException("boom")).when(ingestionService).ingestAndPersist(HEARING_ID, HEARING_DAY);

        processorService.onMessage(context);

        verify(context).deadLetter();
        verify(context, never()).complete();
    }
```

Add `import static org.mockito.Mockito.doThrow;` to the imports.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.servicebus.services.HearingResultedProcessorServiceTest'`
Expected: COMPILE FAILURE — `ingestAndPersist` isn't called by production code yet, only stubbed/verified in the test.

- [ ] **Step 3: Update `HearingResultedProcessorService.processIngestion`**

In `src/main/java/uk/gov/hmcts/cp/servicebus/services/HearingResultedProcessorService.java`, change line 63:

```java
    private void processIngestion(final ServiceBusReceivedMessageContext context, final HearingResultedPointer hearingResultedPointer) {
        try {
            ingestionService.ingestAndPersist(hearingResultedPointer.hearingId(), hearingResultedPointer.hearingDay());
            context.complete();
        } catch (IncompleteHearingDetailsException _) {
            ingestionService.escalateOrDeadLetter(context, hearingResultedPointer);
        } catch (Exception e) {
            log.error("Unrecoverable failure ingesting hearingId:{}", hearingResultedPointer.hearingId(), e);
            context.deadLetter();
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'uk.gov.hmcts.cp.servicebus.services.HearingResultedProcessorServiceTest'`
Expected: PASS (all 6 tests).

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew test`
Expected: PASS across the whole module (confirms no other test still asserts against `ingestHearingResults` being the terminal call).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/servicebus/services/HearingResultedProcessorService.java \
        src/test/java/uk/gov/hmcts/cp/servicebus/services/HearingResultedProcessorServiceTest.java
git commit -m "feat(pcr): wire Service Bus listener to ingestAndPersist instead of discarding the result"
```

---

### Task 6: End-to-end integration test — full hearing → persisted FK graph, via real Postgres

**Files:**
- Create: `src/test/java/uk/gov/hmcts/cp/repositories/CPVersionPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: `CPVersionEntityMapper` (Task 2/3), all 7 repositories (existing), `RepositoryIntegrationTestBase` (existing).

This test bypasses `ResultsIngestionService`/`HearingResultedProcessorService` deliberately — those need a live Service Bus client factory bean, which `RepositoryIntegrationTestBase`'s Spring context doesn't provide (see `IntegrationTestBase`'s own `DataSourceAutoConfiguration` exclusion note for the parallel reasoning). This test proves the FK graph the mapper produces round-trips correctly through real Postgres, using `CPVersionEntityMapper` and the repositories directly — the same objects `ResultsIngestionService` calls, already proven correct against real hearing data by Task 3/4's unit tests.

- [ ] **Step 1: Write the test**

Create `src/test/java/uk/gov/hmcts/cp/repositories/CPVersionPersistenceIntegrationTest.java`:

```java
package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResultPrompt;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDefendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.mappers.CPVersionEntityMapper;
import uk.gov.hmcts.cp.mappers.CPVersionWriteBundle;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CPVersionPersistenceIntegrationTest extends RepositoryIntegrationTestBase {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000066");

    @Autowired
    private CPVersionEntityMapper mapper;
    @Autowired
    private CPCaseHearingRepository caseHearingRepository;
    @Autowired
    private CPCaseMarkerRepository caseMarkerRepository;
    @Autowired
    private CPVersionRepository versionRepository;
    @Autowired
    private CPOffenceRepository offenceRepository;
    @Autowired
    private CPJudicialResultRepository judicialResultRepository;
    @Autowired
    private CPJudicialResultPromptRepository judicialResultPromptRepository;

    @Transactional
    @Test
    void persistedGraph_should_beReadableWithCorrectForeignKeys_afterFullWrite() {
        final ProsecutionCase prosecutionCase = prosecutionCaseWithOneOffenceOneResultOnePrompt();
        final HearingDetail hearing = HearingDetail.builder()
                .courtCentre(CourtCentre.builder().code("B01LY").name("Leeds Crown Court").build())
                .hearingDays(List.of(HearingDay.builder().sittingDay("2026-07-23").build()))
                .prosecutionCases(List.of(prosecutionCase))
                .courtApplications(List.of())
                .build();
        final OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

        final CPCaseHearingEntity caseHearing = mapper.toCaseHearingEntity(prosecutionCase, hearing, HEARING_ID, createdAt);
        caseHearingRepository.save(caseHearing);
        caseMarkerRepository.saveAll(mapper.toCaseMarkerEntities(prosecutionCase, caseHearing.getId()));
        final CPVersionWriteBundle bundle = mapper.toWriteBundle(
                prosecutionCase.getDefendants().get(0), hearing, caseHearing.getId(), createdAt, createdAt.plusDays(30));
        versionRepository.save(bundle.version());
        offenceRepository.saveAll(bundle.offences());
        judicialResultRepository.saveAll(bundle.judicialResults());
        judicialResultPromptRepository.saveAll(bundle.judicialResultPrompts());

        assertThat(caseMarkerRepository.findAll()).extracting("caseHearingId").contains(caseHearing.getId());
        assertThat(versionRepository.findById(bundle.version().getCpVersionPk())).isPresent();
        final var savedOffence = offenceRepository.findById(bundle.offences().get(0).getId()).orElseThrow();
        assertThat(savedOffence.getVersionPk()).isEqualTo(bundle.version().getCpVersionPk());
        final var savedResult = judicialResultRepository.findById(bundle.judicialResults().get(0).getId()).orElseThrow();
        assertThat(savedResult.getOffenceId()).isEqualTo(savedOffence.getId());
        final var savedPrompt = judicialResultPromptRepository.findById(bundle.judicialResultPrompts().get(0).getId()).orElseThrow();
        assertThat(savedPrompt.getJudicialResultId()).isEqualTo(savedResult.getId());
    }

    private ProsecutionCase prosecutionCaseWithOneOffenceOneResultOnePrompt() {
        final JudicialResultPrompt prompt = JudicialResultPrompt.builder()
                .promptReference("prisonOrganisationName").value("HMP Dovegate").build();
        final JudicialResult result = JudicialResult.builder()
                .cjsCode("1200").label("Imprisonment")
                .isFinancialResult(false).isConvictedResult(true)
                .orderedDate(LocalDate.of(2026, 7, 15))
                .judicialResultPrompts(List.of(prompt))
                .build();
        final Offence offence = Offence.builder().offenceCode("TH68001").judicialResults(List.of(result)).build();
        final Defendant defendant = Defendant.builder()
                .id("11111111-1111-1111-1111-111111111111")
                .personDefendant(PersonDefendant.builder().build())
                .offences(List.of(offence))
                .build();
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN("ABCD1234567").build())
                .caseMarkers(List.of(CaseMarker.builder().markerTypeCode("DomesticViolence").build()))
                .defendants(List.of(defendant))
                .build();
    }
}
```

- [ ] **Step 2: Run the test**

Run: `docker compose up -d postgres && ./gradlew test --tests 'uk.gov.hmcts.cp.repositories.CPVersionPersistenceIntegrationTest'`
Expected: PASS. If it fails with `relation "cp_version" does not exist` or similar, Flyway migrations haven't run — verify `spring.flyway.enabled: true` in `application.yaml` and that `spring-boot-starter-flyway` (not just `flyway-core`) is present in `build.gradle` (already confirmed present — see Global Constraints).

- [ ] **Step 3: Run the full test suite one final time**

Run: `./gradlew test`
Expected: PASS across the whole module.

- [ ] **Step 4: Run PMD, Spotless, and full build**

Run: `./gradlew pmdMain spotlessCheck build -x apiTest`
Expected: BUILD SUCCESSFUL. (`-x apiTest` skips the Docker-orchestrated API test suite — this design doesn't touch `GET /pcr` or any WireMock-based test, so no `apiTest` changes are needed; the flag just avoids requiring the full Docker Compose stack for this plan's own verification.)

- [ ] **Step 5: Commit**

```bash
git add src/test/java/uk/gov/hmcts/cp/repositories/CPVersionPersistenceIntegrationTest.java
git commit -m "test(pcr): integration-test the full write-path FK graph against real Postgres"
```

---

## Post-implementation note (not a task — no code)

This plan implements the design doc's §1–§7 in full. §8 (bumping `api-cp-crime-results-pcr` to its latest released contract and switching `GET /pcr` to read from this data store) is explicitly out of scope, tracked as a separate follow-on spec.