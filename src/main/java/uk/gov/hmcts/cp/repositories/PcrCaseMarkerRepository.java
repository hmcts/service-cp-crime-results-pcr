package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.PcrCaseMarkerEntity;

import java.util.UUID;

@Repository
public interface PcrCaseMarkerRepository extends JpaRepository<PcrCaseMarkerEntity, UUID> {
}
