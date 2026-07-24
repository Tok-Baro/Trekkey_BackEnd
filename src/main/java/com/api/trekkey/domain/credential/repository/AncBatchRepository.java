package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncBatch;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AncBatchRepository extends JpaRepository<AncBatch, Long> {

    Optional<AncBatch> findByPublicId(String publicId);

    Optional<AncBatch> findByBatchIdHash(byte[] batchIdHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AncBatch b where b.publicId = :publicId")
    Optional<AncBatch> findByPublicIdForUpdate(@Param("publicId") String publicId);
}
