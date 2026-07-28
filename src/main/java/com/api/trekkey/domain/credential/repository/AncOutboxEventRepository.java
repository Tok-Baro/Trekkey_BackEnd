package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncOutboxEvent;
import com.api.trekkey.domain.credential.entity.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AncOutboxEventRepository extends JpaRepository<AncOutboxEvent, Long> {

    Optional<AncOutboxEvent> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AncOutboxEvent e where e.id = :id")
    Optional<AncOutboxEvent> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from AncOutboxEvent e
            where (e.status = :pendingStatus and e.availableAt <= :now)
               or (e.status = :processingStatus and e.lockedAt <= :leaseExpiredAt)
            order by e.availableAt asc, e.id asc
            """)
    List<AncOutboxEvent> findClaimCandidatesForUpdate(
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("now") LocalDateTime now,
            @Param("leaseExpiredAt") LocalDateTime leaseExpiredAt,
            Pageable pageable);
}
