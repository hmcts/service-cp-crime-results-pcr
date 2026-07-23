package uk.gov.hmcts.cp.services;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.JudicialResult;

import java.util.List;

@Component
public class PcrOrchestrator {

    public List<JudicialResult> excludePublishedForNows(final List<JudicialResult> results) {
        // Mirrors RegisterFragmentService.filterJudicialResultsApplicableForRegisters
        // (design doc §3) — a plain field filter, no lookup.
        return results.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getPublishedForNows()))
                .toList();
    }
}