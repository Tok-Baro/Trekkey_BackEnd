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

    // 재제출 덮어쓰기용 row lock — 동시 제출 시 sourceVersion 유실·파일 정리 경합 방지 (erd-mvp §5)
    // 최초 제출의 동시 insert는 team_id 유일 제약이 최종 방어선이다
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
