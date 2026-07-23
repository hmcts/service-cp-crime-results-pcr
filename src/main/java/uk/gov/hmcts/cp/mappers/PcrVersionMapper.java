package uk.gov.hmcts.cp.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.CustodialEstablishment;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.openapi.model.CaseMarker;
import uk.gov.hmcts.cp.openapi.model.CourtApplication;
import uk.gov.hmcts.cp.openapi.model.Defendant;
import uk.gov.hmcts.cp.openapi.model.HearingDetails;
import uk.gov.hmcts.cp.openapi.model.JudicialResult;
import uk.gov.hmcts.cp.openapi.model.JudicialResultPrompt;
import uk.gov.hmcts.cp.openapi.model.NextHearing;
import uk.gov.hmcts.cp.openapi.model.Offence;
import uk.gov.hmcts.cp.openapi.model.PcrVersion;
import uk.gov.hmcts.cp.openapi.model.ProsecutionCase;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PcrVersionMapper {

    private final JudicialResultPromptParser promptParser;

    public PcrVersion toPcrVersion(
            final HearingDetailsResponse.Defendant defendant,
            final HearingDetailsResponse.ProsecutionCase prosecutionCase,
            final HearingDetailsResponse hearingDetails,
            final UUID hearingId) {
        final HearingDetail hearing = hearingDetails.getHearing();
        return PcrVersion.builder()
                .id(null) // no event-correlation pipeline in phase 1
                .hearingId(hearingId)
                .defendantId(UUID.fromString(defendant.getId()))
                .prosecutionCase(toProsecutionCase(prosecutionCase))
                .caseMarkers(prosecutionCase.getCaseMarkers().stream()
                        .map(m -> CaseMarker.builder().code(m.getMarkerTypeCode()).build())
                        .toList())
                .defendant(toDefendant(defendant))
                .custodyLocation(toCustodyLocation(defendant))
                .hearing(toHearingDetails(hearing))
                .offences(defendant.getOffences().stream()
                        .map(this::toOffence)
                        .toList())
                .courtApplications(toCourtApplications(hearing, defendant))
                .build();
    }

    private ProsecutionCase toProsecutionCase(final HearingDetailsResponse.ProsecutionCase prosecutionCase) {
        return ProsecutionCase.builder()
                .caseURN(prosecutionCase.getProsecutionCaseIdentifier().getCaseURN())
                .build();
    }

    private Defendant toDefendant(final HearingDetailsResponse.Defendant defendant) {
        return Defendant.builder()
                .id(UUID.fromString(defendant.getId()))
                .masterDefendantId(defendant.getMasterDefendantId() == null ? null
                        : UUID.fromString(defendant.getMasterDefendantId()))
                .build();
    }

    private String toCustodyLocation(final HearingDetailsResponse.Defendant defendant) {
        final CustodialEstablishment establishment = defendant.getPersonDefendant().getCustodialEstablishment();
        return establishment == null ? null : establishment.getName();
    }

    private HearingDetails toHearingDetails(final HearingDetail hearing) {
        return HearingDetails.builder()
                .courtHouseCode(hearing.getCourtCentre() == null ? null : hearing.getCourtCentre().getCode())
                .hearingDate(hearing.getHearingDays().isEmpty() ? null
                        : LocalDate.parse(hearing.getHearingDays().get(0).getSittingDay()))
                .nextHearing(findNextHearing(hearing))
                .build();
        // courtHouseName/hearingOutcome/warrantType/overallConvictionDate:
        // left unset (null) — no confirmed CP source, per PCR-HMPPS-FIELD-MAPPING.md
    }

    private NextHearing findNextHearing(final HearingDetail hearing) {
        // Provisional: first non-null nextHearing found across any offence's
        // judicial results, for any defendant on the hearing — see design doc §4.5/§10.
        return hearing.getProsecutionCases().stream()
                .flatMap(c -> c.getDefendants().stream())
                .flatMap(d -> d.getOffences().stream())
                .flatMap(o -> o.getJudicialResults().stream())
                .map(HearingDetailsResponse.JudicialResult::getNextHearing)
                .filter(Objects::nonNull)
                .findFirst()
                .map(n -> NextHearing.builder().date(n.getDate()).build())
                .orElse(null);
    }

    private Offence toOffence(final HearingDetailsResponse.Offence offence) {
        return Offence.builder()
                .code(offence.getOffenceCode())
                .listingNumber(offence.getListingNumber())
                .startDate(offence.getStartDate())
                .endDate(offence.getEndDate())
                .convictionDate(offence.getConvictionDate())
                .results(offence.getJudicialResults().stream()
                        .map(this::toJudicialResult)
                        .toList())
                .build();
        // title/wording/pleaValue/pleaDate/verdictCode: left unset (null) —
        // no confirmed CP source for a synchronous lookup
    }

    private JudicialResult toJudicialResult(final HearingDetailsResponse.JudicialResult result) {
        return JudicialResult.builder()
                .resultCode(result.getCjsCode())
                .resultText(result.getLabel())
                .financial(result.isFinancialResult() ? JudicialResult.FinancialEnum.Y : JudicialResult.FinancialEnum.N)
                .convicted(result.isConvictedResult() ? JudicialResult.ConvictedEnum.Y : JudicialResult.ConvictedEnum.N)
                .judicialResultPrompts(toJudicialResultPrompts(result))
                .concurrent(promptParser.concurrent(result))
                .consecutiveToDate(promptParser.consecutiveToDate(result))
                .consecutiveToCourtName(promptParser.consecutiveToCourtName(result))
                .fineAmount(promptParser.fineAmount(result))
                .imprisonmentPeriod(promptParser.imprisonmentPeriod(result))
                .totalCustodialPeriod(promptParser.totalCustodialPeriod(result))
                .build();
        // postHearingCustodyStatus/category: need a real ResultDefinition lookup — left null
    }

    private List<JudicialResultPrompt> toJudicialResultPrompts(final HearingDetailsResponse.JudicialResult result) {
        return result.getJudicialResultPrompts().stream()
                .map(p -> JudicialResultPrompt.builder()
                        .promptReference(p.getPromptReference())
                        .value(p.getValue())
                        .build())
                .toList();
    }

    private List<CourtApplication> toCourtApplications(
            final HearingDetail hearing, final HearingDetailsResponse.Defendant defendant) {
        return hearing.getCourtApplications().stream()
                .filter(app -> app.getRespondents().stream()
                        .anyMatch(r -> defendant.getMasterDefendantId() != null
                                && defendant.getMasterDefendantId().equals(r.getMasterDefendantId())))
                .map(this::toCourtApplication)
                .toList();
    }

    private CourtApplication toCourtApplication(final HearingDetailsResponse.CourtApplication application) {
        return CourtApplication.builder()
                .id(UUID.fromString(application.getId()))
                .reference(application.getApplicationReference())
                .type(application.getType())
                .results(application.getJudicialResults().stream()
                        .map(this::toJudicialResult)
                        .toList())
                .offences(application.getCourtApplicationCases().stream()
                        .flatMap(c -> c.getOffences().stream())
                        .map(this::toOffence)
                        .toList())
                .build();
        // decision/decisionDate/response/responseDate: no confirmed CP source found — left unset.
        // courtOrder.courtOrderOffences[] (second offence-link source) not yet folded in — known gap.
    }
}