package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.CPCaseMarkerEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CPCaseMarkerRepository extends JpaRepository<CPCaseMarkerEntity, UUID> {

    List<CPCaseMarkerEntity> findByCaseHearingId(UUID caseHearingId);
}
