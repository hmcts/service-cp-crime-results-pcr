package uk.gov.hmcts.cp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cp_case_hearing")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CPCaseHearingEntity {

    @Id
    private UUID id;

    @Column(name = "case_urn")
    private String caseUrn;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "prosecutor_name")
    private String prosecutorName;

    @Column(name = "hearing_id")
    private UUID hearingId;

    @Column(name = "court_house_id")
    private UUID courtHouseId;

    @Column(name = "court_house_code")
    private String courtHouseCode;

    @Column(name = "court_house_name")
    private String courtHouseName;

    @Column(name = "hearing_date")
    private LocalDate hearingDate;

    @Column(name = "hearing_outcome")
    private String hearingOutcome;

    @Column(name = "hearing_type")
    private String hearingType;

    private String jurisdiction;

    @Column(name = "lja_name")
    private String ljaName;

    @Column(name = "court_address_line_1")
    private String courtAddressLine1;

    @Column(name = "court_address_line_2")
    private String courtAddressLine2;

    @Column(name = "court_address_line_3")
    private String courtAddressLine3;

    @Column(name = "court_address_line_4")
    private String courtAddressLine4;

    @Column(name = "court_address_line_5")
    private String courtAddressLine5;

    @Column(name = "court_post_code")
    private String courtPostCode;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
