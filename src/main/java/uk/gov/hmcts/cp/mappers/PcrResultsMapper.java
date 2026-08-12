package uk.gov.hmcts.cp.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;
import uk.gov.hmcts.cp.entities.CPNextHearingEmbeddable;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;
import uk.gov.hmcts.cp.entities.CPVersionEntity;
import uk.gov.hmcts.cp.openapi.model.Address;
import uk.gov.hmcts.cp.openapi.model.CaseMarker;
import uk.gov.hmcts.cp.openapi.model.Court;
import uk.gov.hmcts.cp.openapi.model.CourtApplication;
import uk.gov.hmcts.cp.openapi.model.CourtDetails;
import uk.gov.hmcts.cp.openapi.model.CustodyLocation;
import uk.gov.hmcts.cp.openapi.model.Defendant;
import uk.gov.hmcts.cp.openapi.model.HearingDetails;
import uk.gov.hmcts.cp.openapi.model.JudicialResult;
import uk.gov.hmcts.cp.openapi.model.JudicialResultPrompt;
import uk.gov.hmcts.cp.openapi.model.NextHearing;
import uk.gov.hmcts.cp.openapi.model.Offence;
import uk.gov.hmcts.cp.openapi.model.PcrHearingResult;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
public class PcrResultsMapper {

    // Matches legacy's own LevelTypeEnum literally: {DEFENDANT:'D', CASE:'C', OFFENCE:'O', APPLICATION:'A'}.
    private static final String LEVEL_DEFENDANT = "D";
    private static final String LEVEL_CASE = "C";

    public PcrHearingResult toPcrHearingResult(final CPCaseHearingEntity caseHearing, final CPVersionEntity version,
                                                final List<CPCaseMarkerEntity> caseMarkers,
                                                final List<CPCourtApplicationEntity> courtApplications,
                                                final List<CPOffenceEntity> offences,
                                                final List<CPJudicialResultEntity> judicialResults,
                                                final List<CPJudicialResultPromptEntity> prompts) {
        return PcrHearingResult.builder()
                .caseURN(caseHearing.getCaseUrn())
                .caseMarkers(caseMarkers.stream().map(this::toCaseMarker).toList())
                .defendant(toDefendant(version))
                .custodyLocation(toCustodyLocation(version))
                .hearing(toHearingDetails(caseHearing, version))
                .nextHearing(toNextHearing(version.getNextHearing()))
                .offences(directOffences(offences, version.getCpVersionPk()).stream()
                        .map(o -> toOffence(o, judicialResults, prompts))
                        .toList())
                .courtApplications(courtApplications.stream()
                        .map(a -> toCourtApplication(a, offences, judicialResults, prompts))
                        .toList())
                .defendantResults(judicialResults.stream()
                        .filter(r -> LEVEL_DEFENDANT.equals(r.getLevel()))
                        .map(r -> toJudicialResult(r, prompts))
                        .toList())
                .caseResults(judicialResults.stream()
                        .filter(r -> LEVEL_CASE.equals(r.getLevel()))
                        .map(r -> toJudicialResult(r, prompts))
                        .toList())
                .build();
    }

    private CaseMarker toCaseMarker(final CPCaseMarkerEntity marker) {
        return CaseMarker.builder().code(marker.getCode()).description(marker.getDescription()).build();
    }

    private Defendant toDefendant(final CPVersionEntity version) {
        return Defendant.builder()
                .id(version.getDefendantId())
                .masterDefendantId(version.getMasterDefendantId())
                .title(version.getTitle())
                .firstName(version.getFirstName())
                .middleName(version.getMiddleName())
                .lastName(version.getLastName())
                .dateOfBirth(version.getDateOfBirth())
                .address(toAddress(version))
                .gender(version.getGender())
                .nationality(version.getNationality())
                .postHearingCustodyStatus(version.getPostHearingCustodyStatus())
                .build();
    }

    private Address toAddress(final CPVersionEntity version) {
        return Address.builder()
                .address1(version.getAddressLine1())
                .address2(version.getAddressLine2())
                .address3(version.getAddressLine3())
                .address4(version.getAddressLine4())
                .address5(version.getAddressLine5())
                .postCode(version.getPostCode())
                .build();
    }

    private CustodyLocation toCustodyLocation(final CPVersionEntity version) {
        return version.getCustodyLocation() == null && version.getCustodyType() == null
                ? null
                : CustodyLocation.builder().name(version.getCustodyLocation()).custodyType(version.getCustodyType()).build();
    }

    private HearingDetails toHearingDetails(final CPCaseHearingEntity caseHearing, final CPVersionEntity version) {
        // courtHouseId: no confirmed source on CPCaseHearingEntity today — left unset
        return HearingDetails.builder()
                .id(caseHearing.getHearingId())
                .courtDetails(toCourtDetails(caseHearing))
                .hearingDate(caseHearing.getHearingDate())
                .hearingOutcome(caseHearing.getHearingOutcome())
                .hearingType(caseHearing.getHearingType())
                .jurisdiction(caseHearing.getJurisdiction())
                .defendantAppearanceDetails(version.getDefendantAppearanceDetails())
                .build();
    }

    private Court toCourt(final UUID courtHouseId, final String courtHouseCode, final String courtHouseName) {
        return courtHouseId == null && courtHouseCode == null && courtHouseName == null
                ? null
                : Court.builder().courtHouseId(courtHouseId).courtHouseCode(courtHouseCode).courtHouseName(courtHouseName).build();
    }

    private CourtDetails toCourtDetails(final CPCaseHearingEntity caseHearing) {
        final Court court = toCourt(null, caseHearing.getCourtHouseCode(), caseHearing.getCourtHouseName());
        final Address courtAddress = toCourtAddress(caseHearing);
        return court == null && courtAddress == null && caseHearing.getLjaName() == null
                ? null
                : CourtDetails.builder().court(court).courtAddress(courtAddress).ljaName(caseHearing.getLjaName()).build();
    }

    private Address toCourtAddress(final CPCaseHearingEntity caseHearing) {
        return caseHearing.getCourtAddressLine1() == null && caseHearing.getCourtAddressLine2() == null
                && caseHearing.getCourtAddressLine3() == null && caseHearing.getCourtAddressLine4() == null
                && caseHearing.getCourtAddressLine5() == null && caseHearing.getCourtPostCode() == null
                ? null
                : Address.builder()
                        .address1(caseHearing.getCourtAddressLine1())
                        .address2(caseHearing.getCourtAddressLine2())
                        .address3(caseHearing.getCourtAddressLine3())
                        .address4(caseHearing.getCourtAddressLine4())
                        .address5(caseHearing.getCourtAddressLine5())
                        .postCode(caseHearing.getCourtPostCode())
                        .build();
    }

    private NextHearing toNextHearing(final CPNextHearingEmbeddable nextHearing) {
        return nextHearing == null || nextHearing.getDate() == null
                ? null
                : NextHearing.builder()
                        .hearingId(nextHearing.getId())
                        .court(toCourt(nextHearing.getCourtHouseId(), nextHearing.getCourtHouseCode(), nextHearing.getCourtHouseName()))
                        .dateTime(toNextHearingDateTime(nextHearing))
                        .build();
    }

    // dateTime: uses the recorded time when the write path has populated it; midnight UTC is
    // only a fallback for when the source data genuinely carries a date with no time.
    private Instant toNextHearingDateTime(final CPNextHearingEmbeddable nextHearing) {
        return nextHearing.getTime() == null
                ? nextHearing.getDate().atStartOfDay(ZoneOffset.UTC).toInstant()
                : nextHearing.getDate().atTime(LocalTime.parse(nextHearing.getTime())).atZone(ZoneOffset.UTC).toInstant();
    }

    private List<CPOffenceEntity> directOffences(final List<CPOffenceEntity> offences, final UUID versionPk) {
        return offences.stream().filter(o -> versionPk.equals(o.getVersionPk())).toList();
    }

    private Offence toOffence(final CPOffenceEntity offence,
                               final List<CPJudicialResultEntity> allResults, final List<CPJudicialResultPromptEntity> allPrompts) {
        return Offence.builder()
                .code(offence.getCode())
                .title(offence.getTitle())
                .wording(offence.getWording())
                .startDate(offence.getStartDate())
                .endDate(offence.getEndDate())
                .listingNumber(offence.getListingNumber())
                .convictionDate(offence.getConvictionDate())
                .pleaValue(offence.getPleaValue())
                .pleaDate(offence.getPleaDate())
                .verdict(offence.getVerdict())
                .offenceLegislation(offence.getOffenceLegislation())
                .allocationDecision(offence.getAllocationDecision())
                .indicatedPleaValue(offence.getIndicatedPleaValue())
                .judicialResults(allResults.stream()
                        .filter(r -> offence.getId().equals(r.getOffenceId()))
                        .map(r -> toJudicialResult(r, allPrompts))
                        .toList())
                .build();
    }

    private JudicialResult toJudicialResult(final CPJudicialResultEntity result, final List<CPJudicialResultPromptEntity> allPrompts) {
        return JudicialResult.builder()
                .resultCode(result.getResultCode())
                .resultText(result.getResultText())
                .financial(result.getFinancial())
                .category(result.getCategory())
                .convicted(result.getConvicted())
                .prompts(allPrompts.stream()
                        .filter(p -> result.getId().equals(p.getJudicialResultId()))
                        .map(this::toJudicialResultPrompt)
                        .toList())
                .concurrent(result.getConcurrent())
                .consecutiveToDate(result.getConsecutiveToDate())
                .consecutiveToCourtName(result.getConsecutiveToCourtName())
                .fineAmount(result.getFineAmount() == null ? null : result.getFineAmount().doubleValue())
                .imprisonmentPeriod(result.getImprisonmentPeriod())
                .totalCustodialPeriod(result.getTotalCustodialPeriod())
                .build();
    }

    private JudicialResultPrompt toJudicialResultPrompt(final CPJudicialResultPromptEntity prompt) {
        return JudicialResultPrompt.builder()
                .label(prompt.getLabel())
                .value(prompt.getValue())
                .reference(prompt.getPromptReference())
                .type(prompt.getType())
                .build();
    }

    private CourtApplication toCourtApplication(final CPCourtApplicationEntity application, final List<CPOffenceEntity> allOffences,
                                                 final List<CPJudicialResultEntity> allResults, final List<CPJudicialResultPromptEntity> allPrompts) {
        return CourtApplication.builder()
                .reference(application.getReference())
                .type(application.getType())
                .decision(application.getDecision())
                .decisionDate(application.getDecisionDate())
                .response(application.getResponse())
                .responseDate(application.getResponseDate())
                .judicialResults(allResults.stream()
                        .filter(r -> application.getId().equals(r.getCourtApplicationId()))
                        .map(r -> toJudicialResult(r, allPrompts))
                        .toList())
                .offences(allOffences.stream()
                        .filter(o -> application.getId().equals(o.getCourtApplicationId()))
                        .map(o -> toOffence(o, allResults, allPrompts))
                        .toList())
                .build();
    }
}
