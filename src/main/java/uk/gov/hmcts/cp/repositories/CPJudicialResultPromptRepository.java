package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.CPJudicialResultPromptEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CPJudicialResultPromptRepository extends JpaRepository<CPJudicialResultPromptEntity, UUID> {

    List<CPJudicialResultPromptEntity> findByJudicialResultId(UUID judicialResultId);
}
