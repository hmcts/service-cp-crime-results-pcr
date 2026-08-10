package uk.gov.hmcts.cp.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Address;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtCentre;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CustodialEstablishment;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class CPHearingResultEntityMapper {

    private final CPJudicialResultPromptParser promptParser;

    public CPCaseHearingEntity toCaseHearingEntity(final ProsecutionCase prosecutionCase, final HearingDetail hearing,
                                                    final UUID hearingId, final OffsetDateTime createdAt) {
        return CPCaseHearingEntity.builder()
                .id(UUID.randomUUID())
                .caseUrn(prosecutionCase.getProsecutionCaseIdentifier().getCaseURN())
                .hearingId(hearingId)
                .courtHouseCode(hearing.getCourtCentre() == null ? null : hearing.getCourtCentre().getCode())
                .courtHouseName(hearing.getCourtCentre() == null ? null : hearing.getCourtCentre().getName())
                .hearingDate(hearing.getHearingDays().isEmpty() ? null
                        : toSittingDay(hearing.getHearingDays().get(0).getSittingDay()))
                .createdAt(createdAt)
                .build();
        // hearingOutcome: left unset (null) — no confirmed CP source, data-store design doc §3
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
        // A real courtApplicationCase can omit "offences" entirely (confirmed against a real
        // hearing fixture) — not always an empty list.
        final Stream<JudicialResult> linkedOffenceResults = application.getCourtApplicationCases().stream()
                .flatMap(c -> Stream.ofNullable(c.getOffences()).flatMap(List::stream))
                .flatMap(o -> o.getJudicialResults().stream());
        return Stream.concat(ownResults, linkedOffenceResults);
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
        return new CPEntitySet(version, courtApplications, offences, judicialResults, prompts);
    }

    private void addLinkedApplicationContent(final CourtApplication application, final UUID courtApplicationId,
                                              final List<CPOffenceEntity> offences, final List<CPJudicialResultEntity> judicialResults,
                                              final List<CPJudicialResultPromptEntity> prompts) {
        application.getCourtApplicationCases().stream()
                .flatMap(c -> Stream.ofNullable(c.getOffences()).flatMap(List::stream))
                .forEach(o -> addLinkedOffence(o, courtApplicationId, offences, judicialResults, prompts));
        application.getJudicialResults().forEach(r -> addResult(r, null, courtApplicationId, judicialResults, prompts));
    }

    private CPVersionEntity toVersionEntity(final Defendant defendant, final HearingDetail hearing, final UUID caseHearingId,
                                             final OffsetDateTime createdAt, final OffsetDateTime expiresAt) {
        final CPVersionEntity.CPVersionEntityBuilder builder = CPVersionEntity.builder()
                .cpVersionPk(UUID.randomUUID())
                .eventId(null) // no event-correlation pipeline yet — data-store design doc §3
                .defendantId(UUID.fromString(defendant.getId()))
                .caseHearingId(caseHearingId)
                .custodyLocation(toCustodyLocation(defendant))
                .custodyType(toCustodyType(defendant))
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
                .build();
        // verdictCode: left unset — no verdict-code-shaped field found anywhere on a real
        // offence payload (confirmed against a real amended hearing), unlike title/wording
        // which are directly present as offenceTitle/wording.
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
                .category(result.getCategory())
                .postHearingCustodyStatus(result.getPostHearingCustodyStatus())
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