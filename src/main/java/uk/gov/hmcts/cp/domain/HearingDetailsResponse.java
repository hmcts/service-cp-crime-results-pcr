package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class HearingDetailsResponse {

    private HearingDetail hearing;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class HearingDetail {
        private CourtCentre courtCentre;
        private List<HearingDay> hearingDays;
        private List<ProsecutionCase> prosecutionCases;
        private List<CourtApplication> courtApplications;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CourtCentre {
        private String id;
        private String code;
        private String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class HearingDay {
        private String sittingDay;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ProsecutionCase {
        private String id;
        private ProsecutionCaseIdentifier prosecutionCaseIdentifier;
        private List<CaseMarker> caseMarkers;
        private List<Defendant> defendants;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ProsecutionCaseIdentifier {
        private String caseURN;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CaseMarker {
        private String markerTypeCode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Defendant {
        private String id;
        private String masterDefendantId;
        private PersonDefendant personDefendant;
        private List<Offence> offences;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class PersonDefendant {
        // Deliberately no name/DOB/address here — consumers resolve defendant identity
        // via defendantId/masterDefendantId against their own systems (e.g. NOMIS).
        private CustodialEstablishment custodialEstablishment;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CustodialEstablishment {
        private String id;
        private String name;
        private String custody;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Offence {
        private String offenceCode;
        private Integer listingNumber;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate convictionDate;
        private List<JudicialResult> judicialResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class JudicialResult {
        private String cjsCode;
        private String label;
        private boolean isFinancialResult;
        private boolean isConvictedResult;
        private NextHearing nextHearing;
        private List<JudicialResultPrompt> judicialResultPrompts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class NextHearing {
        private LocalDate date;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class JudicialResultPrompt {
        private String promptReference;
        private String value;
    }

    // Court applications are hearing-level, not nested per-defendant (confirmed —
    // cpp-context-results's shared hearing.json has hearing.courtApplications[]
    // as a sibling of prosecutionCases[], linked to defendants via respondents[]).
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CourtApplication {
        private String id;
        private String applicationReference;
        private String type;
        private List<Respondent> respondents;
        private List<CourtApplicationCase> courtApplicationCases;
        private List<JudicialResult> judicialResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Respondent {
        private String masterDefendantId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CourtApplicationCase {
        private List<Offence> offences;
    }
}