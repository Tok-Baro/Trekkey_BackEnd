package com.api.trekkey.domain.credential.entity;

import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "anc_outbox_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_anc_outbox_event_idempotency", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "idx_anc_outbox_event_claim", columnList = "status,available_at,id"),
                @Index(name = "idx_anc_outbox_event_aggregate", columnList = "aggregate_type,aggregate_id")
        })
public class AncOutboxEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 20, updatable = false)
    private OutboxAggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Lob
    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    private AncOutboxEvent(
            OutboxAggregateType aggregateType,
            Long aggregateId,
            String eventType,
            String idempotencyKey,
            String payloadJson,
            LocalDateTime availableAt) {
        if (aggregateType == null || aggregateId == null || aggregateId <= 0 || isBlank(eventType)
                || isBlank(idempotencyKey) || isBlank(payloadJson) || availableAt == null) {
            throw new IllegalArgumentException("outbox required fields are missing");
        }
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.idempotencyKey = idempotencyKey;
        this.payloadJson = payloadJson;
        this.status = OutboxStatus.PENDING;
        this.availableAt = availableAt;
        this.attemptCount = 0;
    }

    public static AncOutboxEvent pending(
            OutboxAggregateType aggregateType,
            Long aggregateId,
            String eventType,
            String idempotencyKey,
            String payloadJson,
            LocalDateTime availableAt) {
        return new AncOutboxEvent(aggregateType, aggregateId, eventType, idempotencyKey, payloadJson, availableAt);
    }

    public void claim(String workerId, LocalDateTime claimedAt) {
        if (status != OutboxStatus.PENDING || isBlank(workerId) || claimedAt == null || availableAt.isAfter(claimedAt)) {
            throw new IllegalStateException("only an available pending outbox event can be claimed");
        }
        this.status = OutboxStatus.PROCESSING;
        this.lockedBy = workerId;
        this.lockedAt = claimedAt;
        this.attemptCount++;
    }

    public void reclaim(String workerId, LocalDateTime reclaimedAt, LocalDateTime leaseExpiredAt) {
        if (status != OutboxStatus.PROCESSING || isBlank(workerId) || reclaimedAt == null || leaseExpiredAt == null
                || lockedAt == null || lockedAt.isAfter(leaseExpiredAt)) {
            throw new IllegalStateException("only an expired processing outbox event can be reclaimed");
        }
        this.lockedBy = workerId;
        this.lockedAt = reclaimedAt;
        this.attemptCount++;
    }

    public void markProcessed(LocalDateTime processedAt) {
        if (status != OutboxStatus.PROCESSING || processedAt == null || lockedAt == null
                || processedAt.isBefore(lockedAt)) {
            throw new IllegalStateException("only a processing outbox event can be completed");
        }
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = processedAt;
        this.lockedAt = null;
        this.lockedBy = null;
        this.lastErrorCode = null;
    }

    public void reschedule(String errorCode, LocalDateTime nextAvailableAt) {
        if (status != OutboxStatus.PROCESSING || isBlank(errorCode) || nextAvailableAt == null
                || lockedAt == null || nextAvailableAt.isBefore(lockedAt)) {
            throw new IllegalStateException("only a processing outbox event can be rescheduled");
        }
        this.status = OutboxStatus.PENDING;
        this.availableAt = nextAvailableAt;
        this.lockedAt = null;
        this.lockedBy = null;
        this.lastErrorCode = errorCode;
    }

    public void markDead(String errorCode) {
        if (status != OutboxStatus.PROCESSING || isBlank(errorCode)) {
            throw new IllegalStateException("only a processing outbox event can become dead");
        }
        this.status = OutboxStatus.DEAD;
        this.lockedAt = null;
        this.lockedBy = null;
        this.lastErrorCode = errorCode;
    }

    public void markDeliveryFailed(String errorCode) {
        if (status != OutboxStatus.PROCESSED || isBlank(errorCode)) {
            throw new IllegalStateException("only a processed outbox event can record a delivery failure");
        }
        this.status = OutboxStatus.DEAD;
        this.lastErrorCode = errorCode;
    }

    public void resetForApprovalRenewal(LocalDateTime availableAt) {
        if (status != OutboxStatus.DEAD || availableAt == null) {
            throw new IllegalStateException("only a dead outbox event can be reset for approval renewal");
        }
        this.status = OutboxStatus.PENDING;
        this.availableAt = availableAt;
        this.lockedAt = null;
        this.lockedBy = null;
        this.processedAt = null;
        this.lastErrorCode = null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
