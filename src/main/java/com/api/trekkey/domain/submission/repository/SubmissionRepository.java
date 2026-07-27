package com.api.trekkey.domain.submission.repository;

import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.team.entity.TeamStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByTeamId(Long teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select submission
            from Submission submission
            where submission.team.id = :teamId
            """)
    Optional<Submission> findByTeamIdForUpdate(@Param("teamId") Long teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select submission
            from Submission submission
            join fetch submission.team team
            where team.contest.id = :contestId
              and team.status = :teamStatus
              and submission.status = :submissionStatus
              and submission.submittedAt is not null
            order by submission.id
            """)
    List<Submission> findAllForUpdateByContestIdAndStatusAndTeamStatus(
            @Param("contestId") Long contestId,
            @Param("submissionStatus") SubmissionStatus submissionStatus,
            @Param("teamStatus") TeamStatus teamStatus);
}
