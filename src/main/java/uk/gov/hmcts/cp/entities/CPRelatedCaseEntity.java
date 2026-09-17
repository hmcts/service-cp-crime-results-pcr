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
@Table(name = "cp_case_hearing_related_case")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CPRelatedCaseEntity {

    @Id
    private UUID id;

    @Column(name = "case_hearing_id")
    private UUID caseHearingId;

    @Column(name = "case_urn")
    private String caseUrn;
}
