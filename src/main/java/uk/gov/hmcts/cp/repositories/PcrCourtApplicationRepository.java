package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.PcrCourtApplicationEntity;

import java.util.UUID;

@Repository
public interface PcrCourtApplicationRepository extends JpaRepository<PcrCourtApplicationEntity, UUID> {
}
