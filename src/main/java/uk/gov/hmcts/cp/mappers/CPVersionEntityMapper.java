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