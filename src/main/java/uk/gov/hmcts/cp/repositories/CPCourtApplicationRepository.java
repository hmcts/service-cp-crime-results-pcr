package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.CPCourtApplicationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CPCourtApplicationRepository extends JpaRepository<CPCourtApplicationEntity, UUID> {

    List<CPCourtApplicationEntity> findByVersionPk(UUID versionPk);
}
