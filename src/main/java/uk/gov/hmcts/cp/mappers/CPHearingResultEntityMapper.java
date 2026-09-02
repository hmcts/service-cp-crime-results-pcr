package uk.gov.hmcts.cp.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Address;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.AttendanceDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtOrderOffence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CustodialEstablishment;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.DefendantJudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Offence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.PersonDetails;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPNextHearingEmbeddable;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class CPHearingResultEntityMapper {

    private static final String NOT_APPLICABLE = "Not Applicable";
    // Matches legacy's own LevelTypeEnum literally: {DEFENDANT:'D', CASE:'C', OFFENCE:'O', APPLICATION:'A'}.
    private static final String LEVEL_DEFENDANT = "D";
    private static final String LEVEL_CASE = "C";

    private final CPJudicialResultPromptParser promptParser;

    public CPCaseHearingEntity toCaseHearingEntity(final ProsecutionCase prosecutionCase, final HearingDetail hearing,
                                                    final UUID hearingId, final OffsetDateTime createdAt) {
        final CPCaseHearingEntity.CPCaseHearingEntityBuilder builder = CPCaseHearingEntity.builder()
                .id(UUID.randomUUID())
                .caseUrn(prosecutionCase.getProsecutionCaseIdentifier().getCaseURN())
                .hearingId(hearingId)
                .courtHouseId(hearing.getCourtCentre() == null || hearing.getCourtCentre().getId() == null
                        ? null : UUID.fromString(hearing.getCourtCentre().getId()))
                .courtHouseCode(hearing.getCourtCentre() == null ? null : hearing.getCourtCentre().getCode())
                .courtHouseName(hearing.getCourtCentre() == null ? null : hearing.getCourtCentre().getName())
                .hearingDate(hearing.getHearingDays().isEmpty() ? null
                        : toSittingDay(hearing.getHearingDays().get(0).getSittingDay()))
                .hearingType(hearing.getType() == null ? null : hearing.getType().getDescription())
                .jurisdiction(hearing.getJurisdictionType())
                .ljaName(toLjaName(hearing))
                .createdAt(createdAt);
        // hearingOutcome: left unset (null) — no confirmed CP source, data-store design doc §3
        applyCourtAddress(builder, hearing);
        return builder.build();
    }

    private String toLjaName(final HearingDetail hearing) {
        return hearing.getCourtCentre() == null || hearing.getCourtCentre().getLja() == null
                ? null
                : hearing.getCourtCentre().getLja().getLjaName();
    }

    private void applyCourtAddress(final CPCaseHearingEntity.CPCaseHearingEntityBuilder builder, final HearingDetail hearing) {
        final Address address = hearing.getCourtCentre() == null ? null : hearing.getCourtCentre().getAddress();
        if (address == null) {
            return;
        }
        builder.courtAddressLine1(address.getAddress1())
                .courtAddressLine2(address.getAddress2())
                .courtAddressLine3(address.getAddress3())
                .courtAddressLine4(address.getAddress4())
                .courtAddressLine5(address.getAddress5())
                .courtPostCode(address.getPostcode());
    }

    // Real CP payload sends a full ISO-8601 datetime with offset (e.g.
    // "2026-07-23T09:00:00.000Z"), not the plain date "2026-07-23" the field's own type once
    // assumed — DateTimeParseException on every real hearing until this fallback was added.
    private LocalDate toSittingDay(final String sittingDay) {
        LocalDate parsed;
        try {
            parsed = OffsetDateTime.parse(sittingDay).toLocalDate();
        } catch (DateTimeParseException e) {
            parsed = LocalDate.parse(sittingDay);
        }
        return parsed;
    }

    public List<CPCaseMarkerEntity> toCaseMarkerEntities(final ProsecutionCase prosecutionCase, final UUID caseHearingId) {
        // caseMarkers absent entirely on a real CP payload that has none (confirmed against a
        // real hearing fixture) — not always an empty list.
        return Stream.ofNullable(prosecutionCase.getCaseMarkers()).flatMap(List::stream)
                .map(m -> toCaseMarkerEntity(m, caseHearingId))
                .toList();
    }

    private CPCaseMarkerEntity toCaseMarkerEntity(final CaseMarker marker, final UUID caseHearingId) {
        return CPCaseMarkerEntity.builder()
                .id(UUID.randomUUID())
                .caseHearingId(caseHearingId)
                .code(marker.getMarkerTypeCode())
                .description(marker.getMarkerTypeDescription())
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
        final Stream<JudicialResult> linkedOffenceResults = linkedOffencesOf(application)
                .flatMap(o -> o.getJudicialResults().stream());
        return Stream.concat(ownResults, linkedOffenceResults);
    }

    // A real courtApplicationCase can omit "offences" entirely (confirmed against a real hearing
    // fixture) — not always an empty list. courtOrder is only present on breach/resentencing
    // applications and carries the original order's own offence, a sibling concept to the
    // case-linked offences above, not a member of them.
    private Stream<Offence> linkedOffencesOf(final CourtApplication application) {
        final Stream<Offence> caseOffences = application.getCourtApplicationCases().stream()
                .flatMap(c -> Stream.ofNullable(c.getOffences()).flatMap(List::stream));
        final Stream<Offence> courtOrderOffences = application.getCourtOrder() == null
                ? Stream.empty()
                : Stream.ofNullable(application.getCourtOrder().getCourtOrderOffences()).flatMap(List::stream)
                        .map(CourtOrderOffence::getOffence);
        return Stream.concat(caseOffences, courtOrderOffences);
    }

    // `subject` is the only party role used for defendant-linkage — confirmed against
    // cpp-context-azure-legalaidagency's DefendantContextBaseService.js, which reads only
    // `subject.masterDefendant.masterDefendantId` for this same hearing-wide merge (same rule
    // as CPVocabularyService).
    private List<CourtApplication> matchingCourtApplications(final Defendant defendant, final HearingDetail hearing) {
        final String masterDefendantId = defendant.getMasterDefendantId();
        // courtApplications absent entirely on a real hearing that has none (confirmed against
        // a real hearing fixture) — not always an empty list.
        return masterDefendantId == null
                ? List.of()
                : Stream.ofNullable(hearing.getCourtApplications()).flatMap(List::stream)
                        .filter(app -> masterDefendantId.equals(subjectMasterDefendantId(app)))
                        .toList();
    }

    private String subjectMasterDefendantId(final CourtApplication application) {
        return application.getSubject() == null || application.getSubject().getMasterDefendant() == null
                ? null
                : application.getSubject().getMasterDefendant().getMasterDefendantId();
    }

    public CPEntitySet toWriteBundle(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                               final Instant sharedTime, final OffsetDateTime createdAt, final OffsetDateTime expiresAt) {
        final CPVersionEntity version = toVersionEntity(defendant, hearing, caseHearingId, sharedTime, createdAt, expiresAt);
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
        addDefendantAndCaseLevelResults(defendant, hearing, version.getCpVersionPk(), judicialResults, prompts);
        return new CPEntitySet(version, courtApplications, offences, judicialResults, prompts);
    }

    // defendantResults (level DEFENDANT, hearing-wide, matched by masterDefendantId) and
    // caseResults (level CASE — the same defendantCaseJudicialResults already read for
    // populatePostHearingCustodyStatus, now also persisted as their own content) — the two
    // remaining PDF content collections, confirmed via
    // PrisonCourtRegisterPdfPayloadGenerator.buildDefendantResults/buildCaseResults.
    // excludePublishedForNows applies here (see its own comment for why it applies uniformly).
    private void addDefendantAndCaseLevelResults(final Defendant defendant, final HearingDetail hearing, final UUID versionPk,
                                                  final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        excludePublishedForNows(matchingDefendantJudicialResults(defendant, hearing))
                .forEach(r -> addResult(r, null, null, versionPk, LEVEL_DEFENDANT, judicialResults, prompts));
        excludePublishedForNows(Stream.ofNullable(defendant.getDefendantCaseJudicialResults()).flatMap(List::stream))
                .forEach(r -> addResult(r, null, null, versionPk, LEVEL_CASE, judicialResults, prompts));
    }

    // Mirrors RegisterFragmentService.js's filterJudicialResultsApplicableForRegisters — same
    // rule as CPResultsPcrFilter.excludePublishedForNows, kept local here rather than injecting
    // that service's heavier ReferenceDataClient/subscription-matcher dependencies into this
    // mapper for one field check. Confirmed against DefendantContextBaseService.js that OFFENCE
    // and APPLICATION level results are pushed into the exact same defendantBase.results array
    // as DEFENDANT/CASE level ones, and the filter runs on that single combined array before any
    // level-specific mapper reads from it — so this applies to every level's content, not just
    // defendantResults/caseResults.
    private Stream<JudicialResult> excludePublishedForNows(final Stream<JudicialResult> results) {
        return results.filter(r -> !Boolean.TRUE.equals(r.getPublishedForNows()));
    }

    private Stream<JudicialResult> matchingDefendantJudicialResults(final Defendant defendant, final HearingDetail hearing) {
        final String masterDefendantId = defendant.getMasterDefendantId();
        return masterDefendantId == null
                ? Stream.empty()
                : Stream.ofNullable(hearing.getDefendantJudicialResults()).flatMap(List::stream)
                        .filter(r -> masterDefendantId.equals(r.getMasterDefendantId()))
                        .map(DefendantJudicialResult::getJudicialResult);
    }

    private void addLinkedApplicationContent(final CourtApplication application, final UUID courtApplicationId,
                                              final List<CPOffenceEntity> offences, final List<CPJudicialResultEntity> judicialResults,
                                              final List<CPJudicialResultPromptEntity> prompts) {
        linkedOffencesOf(application)
                .forEach(o -> addLinkedOffence(o, courtApplicationId, offences, judicialResults, prompts));
        excludePublishedForNows(application.getJudicialResults().stream())
                .forEach(r -> addResult(r, null, courtApplicationId, judicialResults, prompts));
    }

    private CPVersionEntity toVersionEntity(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                             final Instant sharedTime, final OffsetDateTime createdAt, final OffsetDateTime expiresAt) {
        final CPVersionEntity.CPVersionEntityBuilder builder = CPVersionEntity.builder()
                .cpVersionPk(UUID.randomUUID())
                .eventId(null) // no event-correlation pipeline yet — data-store design doc §3
                .defendantId(UUID.fromString(defendant.getId()))
                .caseHearingId(caseHearingId)
                .custodyLocation(toCustodyLocation(defendant))
                .custodyType(toCustodyType(defendant))
                .masterDefendantId(masterDefendantId(defendant))
                .nextHearing(toNextHearingEmbeddable(hearing))
                .sharedTime(sharedTime == null ? null : sharedTime.atOffset(ZoneOffset.UTC))
                .postHearingCustodyStatus(populatePostHearingCustodyStatus(defendant))
                .defendantAppearanceDetails(toDefendantAppearanceDetails(defendant, hearing))
                .createdAt(createdAt)
                .expiresAt(expiresAt);
        applyPersonDetails(builder, defendant.getPersonDefendant().getPersonDetails());
        return builder.build();
    }

    // Ports the legacy PCR pipeline's own DefendantMapper.js:populatePostHearingCustodyStatus
    // exactly: the first case-level result (not tied to any specific offence) whose status
    // isn't already "Not Applicable", defaulting to "Not Applicable" otherwise. A real
    // judicial result can omit judicialResultPrompts/defendantCaseJudicialResults entirely —
    // not always an empty list.
    private String populatePostHearingCustodyStatus(final Defendant defendant) {
        return Stream.ofNullable(defendant.getDefendantCaseJudicialResults()).flatMap(List::stream)
                .map(JudicialResult::getPostHearingCustodyStatus)
                .filter(status -> status != null && !NOT_APPLICABLE.equals(status))
                .findFirst()
                .orElse(NOT_APPLICABLE);
    }

    // Ports legacy's HearingMapper.js:getDefendantAppearanceDetails, with its `=` (assignment,
    // always matches the first attendance entry) corrected to `equals` — the intent is "this
    // defendant's own attendance record", not "whichever defendant happens to be first".
    private String toDefendantAppearanceDetails(final Defendant defendant, final HearingDetail hearing) {
        return hearing.getDefendantAttendance() == null || hearing.getHearingDays().isEmpty()
                ? null
                : sittingDayAttendanceType(defendant, hearing).map(this::toAppearanceDisplay).orElse(null);
    }

    private Optional<String> sittingDayAttendanceType(final Defendant defendant, final HearingDetail hearing) {
        final String defendantId = defendant.getId() != null ? defendant.getId() : defendant.getMasterDefendantId();
        final String sittingDay = toSittingDay(hearing.getHearingDays().get(0).getSittingDay()).toString();
        return hearing.getDefendantAttendance().stream()
                .filter(a -> defendantId != null && defendantId.equals(a.getDefendantId()))
                .flatMap(a -> Stream.ofNullable(a.getAttendanceDays()).flatMap(List::stream))
                .filter(d -> sittingDay.equals(d.getDay()))
                .map(AttendanceDay::getAttendanceType)
                .findFirst();
    }

    // Matches HearingMapper.js's own translation table verbatim; any other/unrecognised raw
    // attendanceType value falls through to null, same as the legacy mapper's implicit
    // "no matching branch" undefined.
    private String toAppearanceDisplay(final String attendanceType) {
        return switch (attendanceType) {
            case "IN_PERSON" -> "In person";
            case "BY_VIDEO" -> "By video link";
            case "NOT_PRESENT" -> "Not present";
            default -> null;
        };
    }

    private UUID masterDefendantId(final Defendant defendant) {
        return defendant.getMasterDefendantId() == null ? null : UUID.fromString(defendant.getMasterDefendantId());
    }

    private String toCustodyLocation(final Defendant defendant) {
        final CustodialEstablishment establishment = defendant.getPersonDefendant().getCustodialEstablishment();
        return establishment == null ? null : establishment.getName();
    }

    private String toCustodyType(final Defendant defendant) {
        final CustodialEstablishment establishment = defendant.getPersonDefendant().getCustodialEstablishment();
        return establishment == null ? null : establishment.getCustody();
    }

    private void applyPersonDetails(final CPVersionEntity.CPVersionEntityBuilder builder, final PersonDetails personDetails) {
        if (personDetails == null) {
            return;
        }
        builder.title(personDetails.getTitle())
                .firstName(personDetails.getFirstName())
                .middleName(personDetails.getMiddleName())
                .lastName(personDetails.getLastName())
                .dateOfBirth(personDetails.getDateOfBirth())
                .gender(personDetails.getGender())
                .nationality(personDetails.getNationalityDescription());
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
        return Stream.ofNullable(hearing.getProsecutionCases()).flatMap(List::stream)
                .flatMap(c -> c.getDefendants().stream())
                .flatMap(d -> d.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream())
                .map(JudicialResult::getNextHearing)
                .filter(Objects::nonNull)
                .findFirst()
                .map(this::buildNextHearingEmbeddable)
                .orElse(null);
    }

    private CPNextHearingEmbeddable buildNextHearingEmbeddable(final HearingDetailsResponse.NextHearing nextHearing) {
        final OffsetDateTime listedStart = nextHearing.getListedStartDateTime() == null
                ? null : nextHearing.getListedStartDateTime().atOffset(ZoneOffset.UTC);
        final CourtCentre courtCentre = nextHearing.getCourtCentre();
        return CPNextHearingEmbeddable.builder()
                .date(listedStart == null ? null : listedStart.toLocalDate())
                .time(listedStart == null ? null : listedStart.toLocalTime().toString())
                .courtHouseId(courtCentre == null || courtCentre.getId() == null ? null : UUID.fromString(courtCentre.getId()))
                .courtHouseCode(courtCentre == null ? null : courtCentre.getCode())
                .courtHouseName(courtCentre == null ? null : courtCentre.getName())
                .id(nextHearing.getBookingReference() == null ? null : UUID.fromString(nextHearing.getBookingReference()))
                .build();
    }

    private CPCourtApplicationEntity toCourtApplicationEntity(final CourtApplication application, final UUID versionPk) {
        return CPCourtApplicationEntity.builder()
                .id(UUID.randomUUID()) // surrogate — one row per version, CP's real application id can repeat across versions (design doc §4.3) so can't be the PK
                .versionPk(versionPk)
                .sourceApplicationId(UUID.fromString(application.getId()))
                .reference(application.getApplicationReference())
                .type(application.getType() == null ? null : application.getType().getType())
                .build();
        // decision/decisionDate/response/responseDate: no confirmed CP source, same as PcrVersionMapper.toCourtApplication
    }

    private void addDirectOffence(final Offence offence, final UUID versionPk, final List<CPOffenceEntity> offences,
                                   final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        final CPOffenceEntity offenceEntity = toOffenceEntity(offence, versionPk, null);
        offences.add(offenceEntity);
        excludePublishedForNows(offence.getJudicialResults().stream())
                .forEach(r -> addResult(r, offenceEntity.getId(), null, judicialResults, prompts));
    }

    private void addLinkedOffence(final Offence offence, final UUID courtApplicationId, final List<CPOffenceEntity> offences,
                                   final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        final CPOffenceEntity offenceEntity = toOffenceEntity(offence, null, courtApplicationId);
        offences.add(offenceEntity);
        excludePublishedForNows(offence.getJudicialResults().stream())
                .forEach(r -> addResult(r, offenceEntity.getId(), null, judicialResults, prompts));
    }

    private CPOffenceEntity toOffenceEntity(final Offence offence, final UUID versionPk, final UUID courtApplicationId) {
        return CPOffenceEntity.builder()
                .id(UUID.randomUUID()) // surrogate — CP's real offence id can repeat across versions, kept as sourceOffenceId only
                .versionPk(versionPk)
                .courtApplicationId(courtApplicationId)
                .sourceOffenceId(offence.getId() == null ? null : UUID.fromString(offence.getId()))
                .code(offence.getOffenceCode())
                .title(offence.getOffenceTitle())
                .wording(offence.getWording())
                .startDate(offence.getStartDate())
                .endDate(offence.getEndDate())
                .listingNumber(offence.getListingNumber())
                .convictionDate(offence.getConvictionDate())
                .pleaValue(offence.getPlea() == null ? null : offence.getPlea().getPleaValue())
                .pleaDate(offence.getPlea() == null ? null : offence.getPlea().getPleaDate())
                .offenceLegislation(offence.getOffenceLegislation())
                .verdict(toVerdict(offence))
                .allocationDecision(offence.getAllocationDecision() == null ? null : offence.getAllocationDecision().getMotReasonDescription())
                .indicatedPleaValue(offence.getIndicatedPlea() == null ? null : offence.getIndicatedPlea().getIndicatedPleaValue())
                .build();
    }

    // Legacy's OffenceMapper.js sources its "verdictCode" output from verdictType.description, a
    // human-readable value (e.g. "Found guilty"), not CP's own verdict code — matches the api-cp
    // contract's Offence.verdict, sourced the same way.
    private String toVerdict(final Offence offence) {
        return offence.getVerdict() == null || offence.getVerdict().getVerdictType() == null
                ? null
                : offence.getVerdict().getVerdictType().getDescription();
    }

    private void addResult(final JudicialResult result, final UUID offenceId, final UUID courtApplicationId,
                            final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        addResult(result, offenceId, courtApplicationId, null, null, judicialResults, prompts);
    }

    private void addResult(final JudicialResult result, final UUID offenceId, final UUID courtApplicationId,
                            final UUID versionPk, final String level,
                            final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        final CPJudicialResultEntity resultEntity = toJudicialResultEntity(result, offenceId, courtApplicationId, versionPk, level);
        judicialResults.add(resultEntity);
        prompts.addAll(toPromptEntities(result, resultEntity.getId()));
    }

    private CPJudicialResultEntity toJudicialResultEntity(final JudicialResult result, final UUID offenceId, final UUID courtApplicationId,
                                                           final UUID versionPk, final String level) {
        final Double fineAmount = promptParser.fineAmount(result);
        return CPJudicialResultEntity.builder()
                .id(UUID.randomUUID())
                .offenceId(offenceId)
                .courtApplicationId(courtApplicationId)
                .versionPk(versionPk)
                .level(level)
                .resultCode(result.getCjsCode())
                .resultText(result.getResultText())
                .category(result.getCategory())
                .financial(result.isFinancialResult())
                .convicted(result.isConvictedResult())
                .concurrent(promptParser.concurrent(result))
                .consecutiveToDate(promptParser.consecutiveToDate(result))
                .consecutiveToCourtName(promptParser.consecutiveToCourtName(result))
                .fineAmount(fineAmount == null ? null : BigDecimal.valueOf(fineAmount))
                .imprisonmentPeriod(promptParser.imprisonmentPeriod(result))
                .totalCustodialPeriod(promptParser.totalCustodialPeriod(result))
                .build();
    }

    private List<CPJudicialResultPromptEntity> toPromptEntities(final JudicialResult result, final UUID judicialResultId) {
        // judicialResultPrompts absent entirely on a real judicial result that has none
        // (confirmed against a real hearing fixture) — not always an empty list.
        return Stream.ofNullable(result.getJudicialResultPrompts()).flatMap(List::stream)
                .map(p -> CPJudicialResultPromptEntity.builder()
                        .id(UUID.randomUUID())
                        .judicialResultId(judicialResultId)
                        .promptReference(p.getPromptReference())
                        .value(p.getValue())
                        .label(p.getLabel())
                        .type(p.getType())
                        .build())
                .toList();
    }
}