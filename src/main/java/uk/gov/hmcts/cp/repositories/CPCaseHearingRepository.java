package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.CPCaseHearingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CPCaseHearingRepository extends JpaRepository<CPCaseHearingEntity, UUID> {

    Optional<CPCaseHearingEntity> findByCaseUrnAndHearingId(String caseUrn, UUID hearingId);

    @Query("SELECT h FROM CPCaseHearingEntity h WHERE h.hearingId = :hearingId AND h.id IN "
            + "(SELECT r.caseHearingId FROM CPRelatedCaseEntity r WHERE r.caseUrn = :caseUrn) ORDER BY h.createdAt")
    List<CPCaseHearingEntity> findByRelatedCaseUrnAndHearingId(@Param("caseUrn") String caseUrn, @Param("hearingId") UUID hearingId);
}
