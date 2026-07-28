package uk.gov.hmcts.cp.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Address;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CaseMarker;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CourtApplication;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        return Stream.ofNullable(hearing.getProsecutionCases()).flatMap(List::stream)
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
}