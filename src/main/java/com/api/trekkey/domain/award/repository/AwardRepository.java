package com.api.trekkey.domain.award.repository;

import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AwardRepository extends JpaRepository<Award, Long> {

    Optional<Award> findByPublicId(String publicId);

    // 목록 응답이 팀명·작품명까지 쓰므로 fetch join으로 N+1 방지
    @Query("""
            select a
            from Award a
            join fetch a.team t
            join fetch a.contestStageEntry e
            join fetch e.submission
            where t.contest.id = :contestId
            order by a.awardRankNo asc
            """)
    List<Award> findAllByTeamContestIdOrderByAwardRankNoAsc(@Param("contestId") Long contestId);

    boolean existsByTeamContestIdAndStatus(Long contestId, AwardStatus status);

    // 리더 기준 내 수상 목록 (확정분)
    @Query("""
            select a
            from Award a
            join fetch a.team t
            join fetch a.contestStageEntry e
            join fetch e.submission
            where t.leaderUser.id = :leaderUserId and a.status = :status
            order by a.confirmedAt desc
            """)
    List<Award> findAllByTeamLeaderUserIdAndStatusOrderByConfirmedAtDesc(
            @Param("leaderUserId") Long leaderUserId, @Param("status") AwardStatus status);
}
