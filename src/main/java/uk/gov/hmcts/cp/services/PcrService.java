package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.clients.ResultsClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.mappers.PcrVersionMapper;
import uk.gov.hmcts.cp.openapi.model.PcrVersion;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PcrService {

    private static final String LATEST = "latest";

    private final ResultsClient resultsClient;
    private final PcrVersionMapper mapper;

    public PcrVersion getVersion(final String caseURN, final UUID hearingId, final UUID defendantId, final String version) {
        if (!LATEST.equals(version)) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Version lookup by a specific id is not yet supported");
        }
        return getLatestVersion(caseURN, hearingId, defendantId);
    }

    private PcrVersion getLatestVersion(final String caseURN, final UUID hearingId, final UUID defendantId) {
        final HearingDetailsResponse hearingDetails = resultsClient.getHearingDetails(hearingId);
        final HearingDetailsResponse.ProsecutionCase prosecutionCase = findCase(hearingDetails, caseURN);
        final HearingDetailsResponse.Defendant defendant = findDefendant(prosecutionCase, defendantId);
        return mapper.toPcrVersion(defendant, prosecutionCase, hearingDetails, hearingId);
    }

    private HearingDetailsResponse.ProsecutionCase findCase(final HearingDetailsResponse hearingDetails, final String caseURN) {
        return hearingDetails.getHearing().getProsecutionCases().stream()
                .filter(c -> caseURN.equals(c.getProsecutionCaseIdentifier().getCaseURN()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No PCR version found for the supplied case URN, hearing and defendant"));
    }

    private HearingDetailsResponse.Defendant findDefendant(final HearingDetailsResponse.ProsecutionCase prosecutionCase, final UUID defendantId) {
        return prosecutionCase.getDefendants().stream()
                .filter(d -> defendantId.equals(UUID.fromString(d.getId())))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No PCR version found for the supplied case URN, hearing and defendant"));
    }
}