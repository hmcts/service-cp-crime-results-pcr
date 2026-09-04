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
import java.util.UUID;

// Polymorphic parent — exactly one of versionPk/courtApplicationId is set, enforced by the chk_cp_offence_one_parent DB constraint.
@Entity
@Table(name = "cp_offence")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CPOffenceEntity {

    @Id
    private UUID id;

    @Column(name = "version_pk")
    private UUID versionPk;

    @Column(name = "court_application_id")
    private UUID courtApplicationId;

    @Column(name = "source_offence_id")
    private UUID sourceOffenceId;

    private String code;

    private String title;

    private String wording;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "listing_number")
    private Integer listingNumber;

    @Column(name = "conviction_date")
    private LocalDate convictionDate;

    @Column(name = "plea_value")
    private String pleaValue;

    @Column(name = "plea_date")
    private LocalDate pleaDate;

    private String verdict;

    @Column(name = "offence_legislation")
    private String offenceLegislation;

    @Column(name = "allocation_decision")
    private String allocationDecision;

    @Column(name = "indicated_plea_value")
    private String indicatedPleaValue;
}
