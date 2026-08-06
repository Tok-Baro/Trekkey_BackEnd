package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncCredentialStatusEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AncCredentialStatusEventRepository extends JpaRepository<AncCredentialStatusEvent, Long> {

    interface StatusEventRow {

        Long getId();

        String getCredentialPublicId();

        String getCredentialNo();

        String getCredentialStatus();

        String getPreviousStatus();

        String getNextStatus();

        String getReasonCode();

        String getReasonDetail();

        Long getActorUserId();

        String getSupersedingCredentialPublicId();

        byte[] getIssuerSignature();

        LocalDateTime getApprovalDeadline();

        LocalDateTime getEffectiveAt();

        String getTransactionStatus();

        String getLastErrorCode();

        LocalDateTime getCreatedAt();
    }

    Optional<AncCredentialStatusEvent> findByIdempotencyKey(String idempotencyKey);

    boolean existsByCredentialId(Long credentialId);

    @Query(value = """
            select status_event.id as id,
                   credential_record.public_id as credentialPublicId,
                   credential_record.credential_no as credentialNo,
                   credential_record.status as credentialStatus,
                   status_event.previous_status as previousStatus,
                   status_event.next_status as nextStatus,
                   status_event.reason_code as reasonCode,
                   status_event.reason_detail as reasonDetail,
                   status_event.actor_user_id as actorUserId,
                   replacement_record.public_id as supersedingCredentialPublicId,
                   status_event.issuer_signature as issuerSignature,
                   status_event.approval_deadline as approvalDeadline,
                   status_event.effective_at as effectiveAt,
                   chain_tx.status as transactionStatus,
                   chain_tx.last_error_code as lastErrorCode,
                   status_event.created_at as createdAt
            from anc_credential_status_event status_event
            join anc_credential credential_record
              on credential_record.id = status_event.credential_id
            left join anc_credential replacement_record
              on replacement_record.id = status_event.superseding_credential_id
            left join anc_chain_transaction chain_tx
              on chain_tx.credential_status_event_id = status_event.id
            where credential_record.issuer_organization_id = :organizationId
            order by status_event.created_at desc, status_event.id desc
            """, nativeQuery = true)
    List<StatusEventRow> findAllRowsByOrganizationId(
            @Param("organizationId") Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AncCredentialStatusEvent e where e.id = :id")
    Optional<AncCredentialStatusEvent> findByIdForUpdate(@Param("id") Long id);
}
