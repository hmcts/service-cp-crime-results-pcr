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

@Entity
@Table(name = "pcr_court_application")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PcrCourtApplicationEntity {

    @Id
    private UUID id;

    @Column(name = "version_pk")
    private UUID versionPk;

    private String reference;

    private String type;

    private String decision;

    @Column(name = "decision_date")
    private LocalDate decisionDate;

    private String response;

    @Column(name = "response_date")
    private LocalDate responseDate;
}
