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
        // Boxed, not primitive — not yet confirmed present on every real
        // hearingDetails/internal response (design doc §2/§7); a missing field must not
        // fail deserialization of the whole payload.
        private Boolean welshCourtCentre;
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
        private Prosecutor prosecutor;
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
    public static class Prosecutor {
        // Boxed, not primitive — see CourtCentre.welshCourtCentre for why.
        private Boolean isCps;
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
        // Youth/adult vocabulary source (design doc §2/§7) — boxed, not primitive, see
        // CourtCentre.welshCourtCentre for why.
        private Boolean isYouth;
        private PersonDefendant personDefendant;
        private List<Offence> offences;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class PersonDefendant {
        private CustodialEstablishment custodialEstablishment;
        // personDetails: confirmed present on CP's own hearing payload (ADR-004, updated
        // 28 Jul 2026) — no longer "deliberately absent".
        private PersonDetails personDetails;
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
    public static class PersonDetails {
        private String title;
        private String firstName;
        private String middleName;
        private String lastName;
        private LocalDate dateOfBirth;
        private Address address;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Address {
        private String address1;
        private String address2;
        private String address3;
        private String postcode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Offence {
        // Confirmed present on CP's own hearing payload — see V1.009 migration comment for why
        // it's retained as source_offence_id, not the primary key.
        private String id;
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
        // publishedForNows: the PCR eligibility flag (orchestrator design doc §3) — boxed,
        // not primitive, see CourtCentre.welshCourtCentre for why.
        private Boolean publishedForNows;
        // orderedDate: sourced for the persistence-wiring design's resolveActiveAt (design
        // doc §4.2) — needs a real fixture check, same caveat as publishedForNows was under.
        private LocalDate orderedDate;
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
    // as a sibling of prosecutionCases[]). `subject` is the only party role used for
    // defendant-linkage/vocabulary merge purposes — confirmed against
    // cpp-context-azure-legalaidagency's DefendantContextBaseService.js, which reads only
    // `subject.masterDefendant.masterDefendantId` for this. The real payload also carries
    // `respondents[]`/`applicant`, but those serve a separate NOW/document-mapping subsystem and
    // a CPS-eligibility check respectively (not defendant linkage) — neither is in this service's
    // scope, so neither is modelled here.
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CourtApplication {
        private String id;
        private String applicationReference;
        // Real CP payload sends a whole object here (code + human-readable description +
        // several other classification flags), not a plain string — confirmed against a real
        // hearing fixture; deserialization of the entire payload fails otherwise.
        private ApplicationType type;
        private ApplicationParty subject;
        private List<CourtApplicationCase> courtApplicationCases;
        private List<JudicialResult> judicialResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ApplicationType {
        private String code;
        private String type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ApplicationParty {
        private MasterDefendant masterDefendant;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class MasterDefendant {
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