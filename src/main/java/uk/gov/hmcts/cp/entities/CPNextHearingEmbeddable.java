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

// Embedded, nullable — on cp_version the defendant's earliest next hearing, on cp_offence that offence's own.
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
}
