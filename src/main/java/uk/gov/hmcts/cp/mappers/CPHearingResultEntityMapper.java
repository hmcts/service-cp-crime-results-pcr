package uk.gov.hmcts.cp.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Address;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.AttendanceDay;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplicationCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtOrderOffence;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CustodialEstablishment;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.DefendantCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.DefendantJudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.MasterDefendant;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class CPHearingResultEntityMapper {

    private static final String NOT_APPLICABLE = "Not Applicable";
    // Matches CP Azure Legal Aid Agency's LevelTypeEnum: DEFENDANT='D', CASE='C', OFFENCE='O', APPLICATION='A'.
    private static final String LEVEL_DEFENDANT = "D";
    private static final String LEVEL_CASE = "C";
    // Ported from cpp-context-progression's PrisonCourtRegisterHandler.getDefendantType.
    private static final String DEFENDANT_TYPE_DEFENDANT = "Defendant";
    private static final String DEFENDANT_TYPE_APPLICANT = "Applicant";
    private static final String DEFENDANT_TYPE_APPELLANT = "Appellant";
    private static final String DEFENDANT_TYPE_RESPONDENT = "Respondent";
    private static final int SINGLE_DEFENDANT_CASE = 1;

    private final CPJudicialResultPromptParser promptParser;

    public CPCaseHearingEntity toCaseHearingEntity(final ProsecutionCase prosecutionCase, final HearingDetail hearing,
                                                    final UUID hearingId, final OffsetDateTime createdAt) {
        return toCaseHearingEntity(prosecutionCase.getProsecutionCaseIdentifier().getCaseURN(), hearing, hearingId, createdAt,
                prosecutionCase.getProsecutionCaseIdentifier().getProsecutionAuthorityName(),
                prosecutionCase.getId() == null ? null : UUID.fromString(prosecutionCase.getId()));
    }

    // Overload for an application-only case, which has no ProsecutionCase to read a caseURN, case
    // id, or prosecutor name off — the caller resolves prosecutorName via prosecutorNameOf(CourtApplication)
    // and passes a null caseId, since there is no internal CP case identifier for it to carry.
    public CPCaseHearingEntity toCaseHearingEntity(final String caseUrn, final HearingDetail hearing,
                                                    final UUID hearingId, final OffsetDateTime createdAt,
                                                    final String prosecutorName, final UUID caseId) {
        final CPCaseHearingEntity.CPCaseHearingEntityBuilder builder = CPCaseHearingEntity.builder()
                .id(UUID.randomUUID())
                .caseUrn(caseUrn)
                .caseId(caseId)
                .prosecutorName(prosecutorName)
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
        // hearingOutcome: left unset — no confirmed CP source.
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

    // CP sends either a plain date or a full datetime — try both.
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
        // caseMarkers can be absent entirely, not just an empty list.
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

    // courtOrder (breach/resentencing only) carries a separate offence, not part of the case-linked ones.
    private Stream<Offence> linkedOffencesOf(final CourtApplication application) {
        final Stream<Offence> caseOffences = application.getCourtApplicationCases().stream()
                .flatMap(c -> Stream.ofNullable(c.getOffences()).flatMap(List::stream));
        final Stream<Offence> courtOrderOffences = application.getCourtOrder() == null
                ? Stream.empty()
                : Stream.ofNullable(application.getCourtOrder().getCourtOrderOffences()).flatMap(List::stream)
                        .map(CourtOrderOffence::getOffence);
        return Stream.concat(caseOffences, courtOrderOffences);
    }

    // `subject` is the only party role used for defendant-linkage (same rule as CPVocabularyService).
    private List<CourtApplication> matchingCourtApplications(final Defendant defendant, final HearingDetail hearing) {
        final String masterDefendantId = defendant.getMasterDefendantId();
        // courtApplications can be absent entirely, not just an empty list.
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

    // For a defendant only reached via courtApplications. Empty when no defendant is named or
    // defendantId can't be resolved unambiguously.
    public Optional<Defendant> applicationOnlyDefendant(final CourtApplication application) {
        final MasterDefendant masterDefendant = application.getSubject() == null
                ? null : application.getSubject().getMasterDefendant();
        return masterDefendant == null
                ? Optional.empty()
                : resolveDefendantId(masterDefendant, application)
                        .map(defendantId -> buildApplicationOnlyDefendant(defendantId, masterDefendant));
    }

    private Defendant buildApplicationOnlyDefendant(final String defendantId, final MasterDefendant masterDefendant) {
        return Defendant.builder()
                .id(defendantId)
                .masterDefendantId(masterDefendant.getMasterDefendantId())
                .isYouth(masterDefendant.getIsYouth())
                .personDefendant(masterDefendant.getPersonDefendant())
                .offences(List.of())
                .build();
    }

    // Falls back to the sole defendantCase entry, else matches by prosecutionCaseId.
    private Optional<String> resolveDefendantId(final MasterDefendant masterDefendant, final CourtApplication application) {
        final List<DefendantCase> defendantCases = Stream.ofNullable(masterDefendant.getDefendantCase())
                .flatMap(List::stream).toList();
        return defendantCases.size() == SINGLE_DEFENDANT_CASE
                ? Optional.ofNullable(defendantCases.get(0).getDefendantId())
                : matchingDefendantId(defendantCases, application);
    }

    private Optional<String> matchingDefendantId(final List<DefendantCase> defendantCases, final CourtApplication application) {
        final Set<String> applicationCaseIds = Stream.ofNullable(application.getCourtApplicationCases())
                .flatMap(List::stream)
                .map(CourtApplicationCase::getProsecutionCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return defendantCases.stream()
                .filter(dc -> applicationCaseIds.contains(dc.getCaseId()))
                .map(DefendantCase::getDefendantId)
                .findFirst();
    }

    // Sourced from the linked case's own identifier, not the applicant/respondent parties.
    public String prosecutorNameOf(final CourtApplication application) {
        return Stream.ofNullable(application.getCourtApplicationCases())
                .flatMap(List::stream)
                .map(CourtApplicationCase::getProsecutionCaseIdentifier)
                .filter(Objects::nonNull)
                .map(HearingDetailsResponse.ProsecutionCaseIdentifier::getProsecutionAuthorityName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // Sourced the same way as prosecutorNameOf — a court application not linked to any
    // prosecution case has no id to carry; one linked via courtApplicationCases does.
    public UUID caseIdOf(final CourtApplication application) {
        return Stream.ofNullable(application.getCourtApplicationCases())
                .flatMap(List::stream)
                .map(CourtApplicationCase::getProsecutionCaseId)
                .filter(Objects::nonNull)
                .findFirst()
                .map(UUID::fromString)
                .orElse(null);
    }

    // Ports PrisonCourtRegisterHandler.getDefendantType verbatim, quirks included — the applicant
    // branch never checks whose masterDefendant it is, unlike the respondent branch, which does.
    public String defendantType(final CourtApplication application, final String masterDefendantId) {
        final MasterDefendant applicantMasterDefendant = application.getApplicant() == null
                ? null : application.getApplicant().getMasterDefendant();
        return applicantMasterDefendant != null
                ? applicantDefendantType(application)
                : respondentDefendantType(application, masterDefendantId);
    }

    private String applicantDefendantType(final CourtApplication application) {
        final boolean isAppeal = application.getType() != null
                && Boolean.TRUE.equals(application.getType().getAppealFlag())
                && Boolean.TRUE.equals(application.getType().getApplicantAppellantFlag());
        return isAppeal ? DEFENDANT_TYPE_APPELLANT : DEFENDANT_TYPE_APPLICANT;
    }

    private String respondentDefendantType(final CourtApplication application, final String masterDefendantId) {
        final boolean isRespondent = Stream.ofNullable(application.getRespondents()).flatMap(List::stream)
                .filter(respondent -> respondent.getMasterDefendant() != null)
                .anyMatch(respondent -> masterDefendantId.equals(respondent.getMasterDefendant().getMasterDefendantId()));
        return isRespondent ? DEFENDANT_TYPE_RESPONDENT : DEFENDANT_TYPE_APPLICANT;
    }

    public CPEntitySet toWriteBundle(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                               final Instant sharedTime, final OffsetDateTime createdAt, final OffsetDateTime expiresAt) {
        return toWriteBundle(defendant, hearing, caseHearingId, sharedTime, createdAt, expiresAt, DEFENDANT_TYPE_DEFENDANT);
    }

    // Overload for a court-application-only defendant, whose computed label the caller passes in.
    public CPEntitySet toWriteBundle(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                               final Instant sharedTime, final OffsetDateTime createdAt, final OffsetDateTime expiresAt,
                                               final String defendantType) {
        final CPVersionEntity version = toVersionEntity(defendant, hearing, caseHearingId, sharedTime, createdAt, expiresAt, defendantType);
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

    // Hearing-wide defendant results (level DEFENDANT) and case-level results (level CASE).
    private void addDefendantAndCaseLevelResults(final Defendant defendant, final HearingDetail hearing, final UUID versionPk,
                                                  final List<CPJudicialResultEntity> judicialResults, final List<CPJudicialResultPromptEntity> prompts) {
        excludePublishedForNows(matchingDefendantJudicialResults(defendant, hearing))
                .forEach(r -> addResult(r, null, null, versionPk, LEVEL_DEFENDANT, judicialResults, prompts));
        excludePublishedForNows(Stream.ofNullable(defendant.getDefendantCaseJudicialResults()).flatMap(List::stream))
                .forEach(r -> addResult(r, null, null, versionPk, LEVEL_CASE, judicialResults, prompts));
    }

    // Same rule as CPResultsPcrFilter.excludePublishedForNows — kept local to avoid a heavier dependency for one field check.
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
                                             final Instant sharedTime, final OffsetDateTime createdAt, final OffsetDateTime expiresAt,
                                             final String defendantType) {
        final CPVersionEntity.CPVersionEntityBuilder builder = CPVersionEntity.builder()
                .cpVersionPk(UUID.randomUUID())
                .eventId(null) // no event-correlation pipeline yet — data-store design doc §3
                .defendantId(UUID.fromString(defendant.getId()))
                .caseHearingId(caseHearingId)
                .custodyLocation(toCustodyLocation(defendant))
                .custodyType(toCustodyType(defendant))
                .masterDefendantId(masterDefendantId(defendant))
                .defendantType(defendantType)
                .nextHearing(toNextHearingEmbeddable(hearing))
                .sharedTime(sharedTime == null ? null : sharedTime.atOffset(ZoneOffset.UTC))
                .postHearingCustodyStatus(populatePostHearingCustodyStatus(defendant))
                .defendantAppearanceDetails(toDefendantAppearanceDetails(defendant, hearing))
                .createdAt(createdAt)
                .expiresAt(expiresAt);
        applyPersonDetails(builder, defendant.getPersonDefendant().getPersonDetails());
        return builder.build();
    }

    // Ports CP Azure Legal Aid Agency's populatePostHearingCustodyStatus: first case-level result
    // whose status isn't already "Not Applicable", defaulting to "Not Applicable".
    private String populatePostHearingCustodyStatus(final Defendant defendant) {
        return Stream.ofNullable(defendant.getDefendantCaseJudicialResults()).flatMap(List::stream)
                .map(JudicialResult::getPostHearingCustodyStatus)
                .filter(status -> status != null && !NOT_APPLICABLE.equals(status))
                .findFirst()
                .orElse(NOT_APPLICABLE);
    }

    // Ports CP Azure Legal Aid Agency's getDefendantAppearanceDetails, fixing its `=` bug that
    // always matched the first attendance entry instead of this defendant's own.
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

    // Matches CP Azure Legal Aid Agency's translation table; unrecognised values fall through to null.
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
        // Provisional hearing-wide "first non-null nextHearing found" scan, not re-scoped per-defendant.
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
                .id(UUID.randomUUID()) // surrogate — CP's application id can repeat across versions, can't be the PK (design doc §4.3)
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
                .id(UUID.randomUUID()) // surrogate — CP's offence id can repeat across versions, kept as sourceOffenceId only
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

    // Uses verdictType.description, not the verdict code.
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
        // judicialResultPrompts can be absent entirely, not just an empty list.
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