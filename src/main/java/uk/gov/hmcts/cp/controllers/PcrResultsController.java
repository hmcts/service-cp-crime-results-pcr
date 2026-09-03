package uk.gov.hmcts.cp.controllers;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.api.PcrApi;
import uk.gov.hmcts.cp.openapi.model.PcrHearingResult;
import uk.gov.hmcts.cp.services.PcrResultsService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PcrResultsController implements PcrApi {

    // Comma-joined and >30 chars for a court application spanning multiple prosecution cases —
    // CP's own applicationReference literally comma-joins every linked caseURN (confirmed via a
    // real appeal hearing referencing two cases), and this repo's own persistence path
    // (CPEntityPersistenceService.findOrCreateCaseHearing) stores that value as-is as case_urn.
    private static final String CASE_URN_REGEX = "^[0-9a-zA-Z,]{1,100}$";

    private final PcrResultsService pcrResultsService;

    @Override
    @NonNull
    public ResponseEntity<List<PcrHearingResult>> getPcrHearingResults(final String caseURN, final UUID hearingId, final UUID defendantId) {
        log.info("Received request to get PCR hearing results for caseURN:{} hearingId:{} defendantId:{}",
                Encode.forJava(caseURN), hearingId, defendantId);
        final List<PcrHearingResult> results = pcrResultsService.getPcrHearingResults(validateCaseUrn(caseURN), hearingId, defendantId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(results);
    }

    private String validateCaseUrn(final String caseUrn) {
        if (caseUrn == null || !caseUrn.matches(CASE_URN_REGEX)) {
            log.warn("CaseUrn {} does not match expected caseRegex:{}", Encode.forJava(caseUrn), CASE_URN_REGEX);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case urn must be between 1 and 30 alphanumerics");
        }
        return caseUrn;
    }
}
