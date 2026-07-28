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
            join fetch t.contest c
            join fetch a.reviewRoundEntry e
            join fetch e.submission
            where c.id = :contestId
            order by a.awardRankNo asc
            """)
    List<Award> findAllByTeamContestIdOrderByAwardRankNoAsc(@Param("contestId") Long contestId);

    boolean existsByTeamContestIdAndStatus(Long contestId, AwardStatus status);

    // 대표자 또는 TEAM_MEMBER 기준 내 수상 목록 (확정분)
    @Query("""
            select a
            from Award a
            join fetch a.team t
            join fetch t.contest
            join fetch a.reviewRoundEntry e
            join fetch e.submission
            where a.status = :status
              and (
                t.leaderUser.id = :userId
                or exists (
                  select tm.id
                  from TeamMember tm
                  where tm.team = t and tm.user.id = :userId
                )
              )
            order by a.confirmedAt desc
            """)
    List<Award> findAllVisibleToUserByStatusOrderByConfirmedAtDesc(
            @Param("userId") Long userId, @Param("status") AwardStatus status);

    Optional<Award> findFirstByTeamIdAndStatusOrderByAwardRankNoAsc(
            Long teamId, AwardStatus status);
}
