package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.CPJudicialResultEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CPJudicialResultRepository extends JpaRepository<CPJudicialResultEntity, UUID> {

    List<CPJudicialResultEntity> findByOffenceId(UUID offenceId);

    List<CPJudicialResultEntity> findByCourtApplicationId(UUID courtApplicationId);
}
