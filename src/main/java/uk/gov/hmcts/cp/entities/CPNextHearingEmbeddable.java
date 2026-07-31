package uk.gov.hmcts.cp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

// Embedded, nullable, 1:1 with cp_version (design doc §3) — kept per-defendant rather than
// promoted to cp_case_hearing, since which offence's nextHearing wins is still unconfirmed.
@Embeddable
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CPNextHearingEmbeddable {

    @Column(name = "next_hearing_date")
    private LocalDate date;

    @Column(name = "next_hearing_time")
    private String time;

    @Column(name = "next_hearing_court_house_id")
    private UUID courtHouseId;

    @Column(name = "next_hearing_court_house_code")
    private String courtHouseCode;

    @Column(name = "next_hearing_court_house_name")
    private String courtHouseName;

    @Column(name = "next_hearing_id")
    private UUID id;
}
