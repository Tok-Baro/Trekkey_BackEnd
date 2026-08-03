package com.api.trekkey.domain.review.entity;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "contest_judge",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contest_judge_user",
                        columnNames = {"contest_id", "user_id"}),
                @UniqueConstraint(
                        name = "uk_contest_judge_review_token_hash",
                        columnNames = "review_token_hash")
        })
public class ContestJudge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String roleLabel;

    @Column(name = "review_token_hash", length = 64)
    private String reviewTokenHash;

    @Column(name = "token_issued_at")
    private LocalDateTime tokenIssuedAt;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "token_revoked_at")
    private LocalDateTime tokenRevokedAt;

    public void issueReviewLink(
            String reviewTokenHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        this.reviewTokenHash = reviewTokenHash;
        this.tokenIssuedAt = issuedAt;
        this.tokenExpiresAt = expiresAt;
        this.tokenRevokedAt = null;
    }

    public boolean revokeReviewLink(LocalDateTime revokedAt) {
        if (reviewTokenHash == null || tokenRevokedAt != null) {
            return false;
        }
        this.tokenRevokedAt = revokedAt;
        return true;
    }

    public ReviewLinkStatus getReviewLinkStatus(LocalDateTime now) {
        if (reviewTokenHash == null || tokenIssuedAt == null || tokenExpiresAt == null) {
            return ReviewLinkStatus.NOT_ISSUED;
        }
        if (tokenRevokedAt != null) {
            return ReviewLinkStatus.REVOKED;
        }
        if (!tokenExpiresAt.isAfter(now)) {
            return ReviewLinkStatus.EXPIRED;
        }
        return ReviewLinkStatus.ACTIVE;
    }
}
