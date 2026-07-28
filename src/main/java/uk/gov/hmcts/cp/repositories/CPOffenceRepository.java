package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.CPOffenceEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CPOffenceRepository extends JpaRepository<CPOffenceEntity, UUID> {

    List<CPOffenceEntity> findByVersionPk(UUID versionPk);

    List<CPOffenceEntity> findByCourtApplicationId(UUID courtApplicationId);
}
