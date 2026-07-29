package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CPCaseHearingRepository extends JpaRepository<CPCaseHearingEntity, UUID> {

    Optional<CPCaseHearingEntity> findByCaseUrnAndHearingId(String caseUrn, UUID hearingId);
}
