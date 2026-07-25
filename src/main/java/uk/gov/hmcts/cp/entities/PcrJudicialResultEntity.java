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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Polymorphic parent (design doc §3) — exactly one of offenceId/courtApplicationId is set,
// enforced by the chk_pcr_judicial_result_one_parent DB constraint, not by this entity.
@Entity
@Table(name = "pcr_judicial_result")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PcrJudicialResultEntity {

    @Id
    private UUID id;

    @Column(name = "offence_id")
    private UUID offenceId;

    @Column(name = "court_application_id")
    private UUID courtApplicationId;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "result_text")
    private String resultText;

    @Column(name = "post_hearing_custody_status")
    private String postHearingCustodyStatus;

    private Boolean financial;

    private String category;

    private Boolean convicted;

    private Boolean concurrent;

    @Column(name = "consecutive_to_date")
    private LocalDate consecutiveToDate;

    @Column(name = "consecutive_to_court_name")
    private String consecutiveToCourtName;

    @Column(name = "fine_amount")
    private BigDecimal fineAmount;

    @Column(name = "imprisonment_period")
    private String imprisonmentPeriod;

    @Column(name = "total_custodial_period")
    private String totalCustodialPeriod;
}
