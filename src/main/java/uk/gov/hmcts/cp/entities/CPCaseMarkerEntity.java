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

import java.util.UUID;

@Entity
@Table(name = "cp_case_marker")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CPCaseMarkerEntity {

    @Id
    private UUID id;

    @Column(name = "case_hearing_id")
    private UUID caseHearingId;

    private String code;

    private String description;
}
