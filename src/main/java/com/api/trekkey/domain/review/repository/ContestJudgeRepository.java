package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ContestJudge;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContestJudgeRepository extends JpaRepository<ContestJudge, Long> {

    List<ContestJudge> findAllByContestIdOrderByCreatedAtAscIdAsc(Long contestId);

    Optional<ContestJudge> findByIdAndContestId(Long id, Long contestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select judge
            from ContestJudge judge
            where judge.id = :judgeId
              and judge.contest.id = :contestId
            """)
    Optional<ContestJudge> findByIdAndContestIdForUpdate(
            @Param("judgeId") Long judgeId,
            @Param("contestId") Long contestId);

    // MySQL의 읽기 전용 트랜잭션은 locking read를 거부하므로
    // 이 메서드의 호출 서비스는 일반(read-write) 트랜잭션을 사용해야 한다.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select judge
            from ContestJudge judge
            where judge.reviewTokenHash = :reviewTokenHash
            """)
    Optional<ContestJudge> findByReviewTokenHashForShare(
            @Param("reviewTokenHash") String reviewTokenHash);

    boolean existsByContestIdAndUserId(Long contestId, Long userId);

    List<ContestJudge> findAllByContestIdOrderByCreatedAtDesc(Long contestId);

    List<ContestJudge> findAllByContestId(Long contestId);
}
