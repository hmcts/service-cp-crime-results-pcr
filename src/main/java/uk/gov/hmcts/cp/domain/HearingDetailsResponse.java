package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class HearingDetailsResponse {

    private HearingDetail hearing;
    // Version-correlation candidate, not yet used.
    private Instant sharedTime;

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
        private HearingType type;
        private String jurisdictionType;
        private List<DefendantAttendance> defendantAttendance;
        // Hearing-wide defendant-level results, matched by masterDefendantId — distinct from
        // Defendant.defendantCaseJudicialResults, which is per-defendant nested.
        private List<DefendantJudicialResult> defendantJudicialResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class DefendantJudicialResult {
        private String masterDefendantId;
        private JudicialResult judicialResult;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class DefendantAttendance {
        private String defendantId;
        private List<AttendanceDay> attendanceDays;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class AttendanceDay {
        private String day;
        private String attendanceType;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class HearingType {
        private String id;
        private String description;
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
        // Boxed, not primitive — not confirmed present on every response; a missing field must not fail deserialization.
        private Boolean welshCourtCentre;
        private LocalJusticeArea lja;
        private Address address;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class LocalJusticeArea {
        private String ljaName;
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
        private String prosecutionAuthorityName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Prosecutor {
        // Boxed, not primitive — see welshCourtCentre.
        private Boolean isCps;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CaseMarker {
        private String markerTypeCode;
        private String markerTypeDescription;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Defendant {
        private String id;
        private String masterDefendantId;
        // Youth/adult vocabulary source — boxed, not primitive, see welshCourtCentre.
        private Boolean isYouth;
        private PersonDefendant personDefendant;
        private List<Offence> offences;
        // Case-level results attached directly to the defendant, not tied to any offence — distinct from offences[].judicialResults.
        private List<JudicialResult> defendantCaseJudicialResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class PersonDefendant {
        private CustodialEstablishment custodialEstablishment;
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
        private String gender;
        private String nationalityDescription;
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
        // address4/address5: never populated on a defendant address, but courtCentre.address genuinely uses all 5 lines — same shape either way.
        private String address4;
        private String address5;
        private String postcode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Offence {
        // Retained as source_offence_id, not the primary key — see V1.009 migration.
        private String id;
        private String offenceCode;
        private String offenceTitle;
        private String wording;
        private Integer listingNumber;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate convictionDate;
        private PleaDetails plea;
        private List<JudicialResult> judicialResults;
        private String offenceLegislation;
        private Verdict verdict;
        private AllocationDecision allocationDecision;
        private IndicatedPlea indicatedPlea;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class AllocationDecision {
        private String motReasonDescription;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class IndicatedPlea {
        private String indicatedPleaValue;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Verdict {
        private VerdictType verdictType;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class VerdictType {
        // verdictCode kept for correlation/debugging — description is what's actually surfaced (see toVerdict).
        private String verdictCode;
        private String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class PleaDetails {
        private String pleaValue;
        private LocalDate pleaDate;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class JudicialResult {
        private String cjsCode;
        private String label;
        private String resultText;
        private String category;
        private String postHearingCustodyStatus;
        private boolean isFinancialResult;
        private boolean isConvictedResult;
        // publishedForNows: the PCR eligibility flag — boxed, not primitive, see welshCourtCentre.
        private Boolean publishedForNows;
        // orderedDate: sourced for resolveActiveAt.
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
        private String bookingReference;
        private Instant listedStartDateTime;
        private CourtCentre courtCentre;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class JudicialResultPrompt {
        private String promptReference;
        private String value;
        private String label;
        private String type;
    }

    // Hearing-level, not nested per-defendant. `subject` identifies the defendant; `applicant`/
    // `respondents[]` feed the Applicant/Appellant/Respondent label instead (see defendantType).
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CourtApplication {
        private String id;
        private String applicationReference;
        private ApplicationType type;
        private ApplicationParty subject;
        private ApplicationParty applicant;
        private List<ApplicationParty> respondents;
        private List<CourtApplicationCase> courtApplicationCases;
        private List<JudicialResult> judicialResults;
        private CourtOrder courtOrder;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CourtOrder {
        private List<CourtOrderOffence> courtOrderOffences;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CourtOrderOffence {
        private Offence offence;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ApplicationType {
        private String code;
        private String type;
        // Feeds defendantType — boxed, see welshCourtCentre.
        private Boolean appealFlag;
        private Boolean applicantAppellantFlag;
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
        // Feeds applicationOnlyDefendant.
        private Boolean isYouth;
        private PersonDefendant personDefendant;
        private List<DefendantCase> defendantCase;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class DefendantCase {
        private String caseId;
        private String caseReference;
        private String defendantId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CourtApplicationCase {
        private List<Offence> offences;
        // Matches this application to a defendantCase entry when a defendant has several — not used for case URN.
        private String prosecutionCaseId;
        private ProsecutionCaseIdentifier prosecutionCaseIdentifier;
    }
}
