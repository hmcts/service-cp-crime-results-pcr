package uk.gov.hmcts.cp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// Embedded on cp_judicial_result, nullable — the next hearing CP recorded against that result.
@Embeddable
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CPNextHearingEmbeddable {

    @Column(name = "next_hearing_listed_start")
    private Instant listedStart;

    @Column(name = "next_hearing_court_house_id")
    private UUID courtHouseId;

    @Column(name = "next_hearing_court_house_code")
    private String courtHouseCode;

    @Column(name = "next_hearing_court_house_name")
    private String courtHouseName;
}
