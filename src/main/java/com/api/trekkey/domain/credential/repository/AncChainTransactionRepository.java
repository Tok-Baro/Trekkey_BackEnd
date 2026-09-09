package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncChainTransaction;
import com.api.trekkey.domain.credential.entity.ChainOperationType;
import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AncChainTransactionRepository extends JpaRepository<AncChainTransaction, Long> {

    Optional<AncChainTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<AncChainTransaction> findByChainIdAndTxHash(long chainId, byte[] txHash);

    Optional<AncChainTransaction> findByBatchIdAndOperationType(Long batchId, ChainOperationType operationType);

    Optional<AncChainTransaction> findByCredentialStatusEventIdAndOperationType(
            Long credentialStatusEventId,
            ChainOperationType operationType);

    List<AncChainTransaction> findByStatusOrderByCreatedAtAsc(
            ChainTransactionStatus status,
            Pageable pageable);

    @Query("""
            select t from AncChainTransaction t
            where t.status = :status and t.nextAttemptAt <= :now
            order by t.nextAttemptAt asc, t.id asc
            """)
    List<AncChainTransaction> findUnknownDueForReceipt(
            @Param("status") ChainTransactionStatus status,
            @Param("now") java.time.LocalDateTime now,
            Pageable pageable);

    @Query("""
            select t from AncChainTransaction t
            where t.status = :status
              and t.chainId = :chainId and t.contractAddress = :contractAddress
              and t.contractVersion = :contractVersion
              and (t.chainContext = :chainContext or (:allowLegacy = true and t.chainContext is null))
            order by t.createdAt asc, t.id asc
            """)
    List<AncChainTransaction> findSubmittedForReceiptInContext(
            @Param("status") ChainTransactionStatus status,
            @Param("chainContext") String chainContext,
            @Param("allowLegacy") boolean allowLegacy,
            @Param("chainId") long chainId,
            @Param("contractAddress") byte[] contractAddress,
            @Param("contractVersion") String contractVersion,
            Pageable pageable);

    @Query("""
            select t from AncChainTransaction t
            where t.status = :status and t.nextAttemptAt <= :now
              and t.chainId = :chainId and t.contractAddress = :contractAddress
              and t.contractVersion = :contractVersion
              and (t.chainContext = :chainContext or (:allowLegacy = true and t.chainContext is null))
            order by t.nextAttemptAt asc, t.id asc
            """)
    List<AncChainTransaction> findUnknownDueForReceiptInContext(
            @Param("status") ChainTransactionStatus status,
            @Param("now") java.time.LocalDateTime now,
            @Param("chainContext") String chainContext,
            @Param("allowLegacy") boolean allowLegacy,
            @Param("chainId") long chainId,
            @Param("contractAddress") byte[] contractAddress,
            @Param("contractVersion") String contractVersion,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AncChainTransaction t where t.idempotencyKey = :idempotencyKey")
    Optional<AncChainTransaction> findByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AncChainTransaction t where t.id = :id")
    Optional<AncChainTransaction> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from AncChainTransaction t where t.chainId = :chainId and t.txHash = :txHash")
    Optional<AncChainTransaction> findReadbackByChainIdAndTxHash(
            @Param("chainId") long chainId,
            @Param("txHash") byte[] txHash);
}
