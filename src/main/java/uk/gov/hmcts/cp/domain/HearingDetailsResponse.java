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
    // Sibling of hearing in CP's own payload, not nested under it — a version-correlation
    // candidate (design §7), not yet used to correlate anything.
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
        // Hearing-wide defendant-level results, matched by masterDefendantId — a distinct CP
        // concept from Defendant.defendantCaseJudicialResults (which is per-defendant nested, not
        // hearing-wide). Confirmed via the legacy Function App's own
        // DefendantContextBaseService.js:setJudicialResultsAtDefendantCaseLevel, which reads this
        // exact field name and keys off masterDefendantId, not defendantId.
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
        // Boxed, not primitive — not yet confirmed present on every real
        // hearingDetails/internal response (design doc §2/§7); a missing field must not
        // fail deserialization of the whole payload.
        private Boolean welshCourtCentre;
        // lja/address confirmed via the legacy Function App's own HearingVenueMapper.js, which
        // reads hearing.courtCentre.lja.ljaName and hearing.courtCentre.address verbatim.
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
        // Youth/adult vocabulary source (design doc §2/§7) — boxed, not primitive, see
        // CourtCentre.welshCourtCentre for why.
        private Boolean isYouth;
        private PersonDefendant personDefendant;
        private List<Offence> offences;
        // Case-level results attached directly to the defendant, not tied to any specific
        // offence — a distinct CP concept from offences[].judicialResults. Confirmed via the
        // legacy Function App's DefendantMapper.js/DefendantContextBaseService.js, which reads
        // this exact field name.
        private List<JudicialResult> defendantCaseJudicialResults;
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
        // address4/address5: never populated on a real defendant address (no 4th/5th line
        // upstream there), but HearingVenueMapper.js confirms CP's courtCentre.address genuinely
        // carries all 5 lines — kept here rather than on a separate court-only address type since
        // this is the same raw CP address shape either way.
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
        // Confirmed present on CP's own hearing payload — see V1.009 migration comment for why
        // it's retained as source_offence_id, not the primary key.
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
        // allocationDecision/indicatedPlea confirmed via the legacy Function App's own
        // OffenceMapper.js, which reads offence.allocationDecision.motReasonDescription and
        // offence.indicatedPlea.indicatedPleaValue verbatim.
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
        // CP's own verdict code (e.g. "G" for guilty) — kept for correlation/debugging, but
        // description is the field actually surfaced (see CPHearingResultEntityMapper.toVerdict):
        // legacy's OffenceMapper.js sources its "verdictCode" output from verdictType.description,
        // and the api-cp contract's Offence.verdict mirrors that same description value.
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
        // CP's real payload has no bare `date` field — it sends `listedStartDateTime` (full
        // ISO instant) and `courtCentre`, matching the shape already used for the hearing
        // itself. `bookingReference` is the closest CP field identifying this specific future
        // hearing occurrence.
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

    // Court applications are hearing-level, not nested per-defendant. `subject` identifies the
    // defendant — also the PCR source when they're not reached via this hearing's own
    // prosecutionCases[] (the application's own case link is courtApplicationCases[], a separate
    // thing — design doc 2026-09-02). `applicant`/`respondents[]` feed the Applicant/Appellant/
    // Respondent label instead (CPHearingResultEntityMapper.defendantType).
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
        // Feeds CPHearingResultEntityMapper.defendantType — boxed, see CourtCentre.welshCourtCentre.
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
        // Feeds CPHearingResultEntityMapper.applicationOnlyDefendant (design doc 2026-09-02).
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
        // Matches this application to a defendantCase entry when a defendant has several — not
        // used for case URN, which is applicationReference (design doc 2026-09-02).
        private String prosecutionCaseId;
    }
}
