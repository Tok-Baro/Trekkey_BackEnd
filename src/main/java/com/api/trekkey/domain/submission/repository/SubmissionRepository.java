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

    Optional<Submission> findByPublicId(String publicId);

    Optional<Submission> findByTeamId(Long teamId);

    // TEAM row를 먼저 잠근 뒤 기존 제출 행까지 잠가 파일 교체 순서를 유지한다.
    // team_id 유일 제약은 잘못된 쓰기 경로에 대한 최종 방어선이다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.team.id = :teamId")
    Optional<Submission> findByTeamIdForUpdate(@Param("teamId") Long teamId);

    @Query("""
            select s
            from Submission s
            where s.team.contest.id = :contestId
            order by s.submittedAt desc
            """)
    List<Submission> findAllByContestId(@Param("contestId") Long contestId);

    /**
     * 리뷰 라운드 편입 대상을 고정된 순서로 잠가 조회한다.
     *
     * <p>호출 측에서 먼저 같은 대회의 TEAM 행을 id 순서로 잠근 뒤 이 쿼리를
     * 호출해 제출 덮어쓰기와 라운드 오픈 사이의 잠금 순서를 일관되게 유지한다.</p>
     */
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
