package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.clients.ResultsQueryClient;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.Defendant;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.HearingDetail;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.HearingDetailsResponse.ProsecutionCaseIdentifier;
import uk.gov.hmcts.cp.mappers.PcrVersionMapper;
import uk.gov.hmcts.cp.openapi.model.PcrVersion;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PcrServiceTest {

    private static final String CASE_URN = "ABCD1234567";
    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DEFENDANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");

    @Mock
    private ResultsQueryClient resultsQueryClient;
    @Mock
    private PcrVersionMapper mapper;

    @InjectMocks
    private PcrService pcrService;

    @Test
    void getVersion_should_throw404_whenCaseUrnNotFound_andVersionIsLatest() {
        final HearingDetailsResponse hearingDetails = hearingDetailsWith(List.of());
        when(resultsQueryClient.getHearingDetails(HEARING_ID)).thenReturn(hearingDetails);

        assertThatThrownBy(() -> pcrService.getVersion(CASE_URN, HEARING_ID, DEFENDANT_ID, "latest"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getVersion_should_throw404_whenDefendantIdNotFound_andVersionIsLatest() {
        final ProsecutionCase prosecutionCase = prosecutionCaseWith(List.of());
        final HearingDetailsResponse hearingDetails = hearingDetailsWith(List.of(prosecutionCase));
        when(resultsQueryClient.getHearingDetails(HEARING_ID)).thenReturn(hearingDetails);

        assertThatThrownBy(() -> pcrService.getVersion(CASE_URN, HEARING_ID, DEFENDANT_ID, "latest"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getVersion_should_delegateToMapper_whenDefendantFound_andVersionIsLatest() {
        final Defendant defendant = Defendant.builder().id(DEFENDANT_ID.toString()).build();
        final ProsecutionCase prosecutionCase = prosecutionCaseWith(List.of(defendant));
        final HearingDetailsResponse hearingDetails = hearingDetailsWith(List.of(prosecutionCase));
        final PcrVersion expected = PcrVersion.builder().hearingId(HEARING_ID).build();
        when(resultsQueryClient.getHearingDetails(HEARING_ID)).thenReturn(hearingDetails);
        when(mapper.toPcrVersion(defendant, prosecutionCase, hearingDetails, HEARING_ID)).thenReturn(expected);

        final PcrVersion result = pcrService.getVersion(CASE_URN, HEARING_ID, DEFENDANT_ID, "latest");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getVersion_should_throw501_whenVersionIsSpecificId() {
        assertThatThrownBy(() -> pcrService.getVersion(CASE_URN, HEARING_ID, DEFENDANT_ID, "01hxjk8v3xj0e5jz2h1p4c6q7r"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("501");
    }

    private ProsecutionCase prosecutionCaseWith(final List<Defendant> defendants) {
        return ProsecutionCase.builder()
                .prosecutionCaseIdentifier(ProsecutionCaseIdentifier.builder().caseURN(CASE_URN).build())
                .defendants(defendants)
                .build();
    }

    private HearingDetailsResponse hearingDetailsWith(final List<ProsecutionCase> prosecutionCases) {
        return HearingDetailsResponse.builder()
                .hearing(HearingDetail.builder().prosecutionCases(prosecutionCases).build())
                .build();
    }
}