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
import uk.gov.hmcts.cp.openapi.model.PcrVersion;
import uk.gov.hmcts.cp.services.PcrService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PcrController implements PcrApi {

    private static final String CASE_URN_REGEX = "^[0-9a-zA-Z]{1,30}$";

    private final PcrService pcrService;

    @Override
    @NonNull
    public ResponseEntity<PcrVersion> getLatestPcrVersion(final String caseURN, final UUID hearingId, final UUID defendantId) {
        log.info("Received request to get latest PCR version for caseURN:{} hearingId:{} defendantId:{}",
                Encode.forJava(caseURN), hearingId, defendantId);
        final PcrVersion pcrVersion = pcrService.getLatestVersion(validateCaseUrn(caseURN), hearingId, defendantId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(pcrVersion);
    }

    private String validateCaseUrn(final String caseUrn) {
        if (caseUrn == null || !caseUrn.matches(CASE_URN_REGEX)) {
            log.warn("CaseUrn {} does not match expected caseRegex:{}", Encode.forJava(caseUrn), CASE_URN_REGEX);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case urn must be between 1 and 30 alphanumerics");
        }
        return caseUrn;
    }
}