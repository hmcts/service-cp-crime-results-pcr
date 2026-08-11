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

    @Column(name = "hearing_id")
    private UUID hearingId;

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

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
