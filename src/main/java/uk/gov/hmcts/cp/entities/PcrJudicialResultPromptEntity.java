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
@Table(name = "pcr_judicial_result_prompt")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PcrJudicialResultPromptEntity {

    @Id
    private UUID id;

    @Column(name = "judicial_result_id")
    private UUID judicialResultId;

    private String label;

    private String value;

    @Column(name = "prompt_reference")
    private String promptReference;

    private String type;
}
