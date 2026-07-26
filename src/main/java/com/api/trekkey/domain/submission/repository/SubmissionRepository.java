package com.api.trekkey.domain.submission.repository;

import com.api.trekkey.domain.submission.entity.Submission;
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
}
