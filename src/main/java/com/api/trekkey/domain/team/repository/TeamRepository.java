package com.api.trekkey.domain.team.repository;

import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByContestIdAndLeaderUserId(Long contestId, Long leaderUserId);

    Optional<Team> findByPublicId(String publicId);

    Optional<Team> findByContestIdAndLeaderUserId(Long contestId, Long leaderUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.publicId = :publicId")
    Optional<Team> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select team
            from Team team
            where team.contest.id = :contestId
              and team.leaderUser.id = :leaderUserId
            """)
    Optional<Team> findByContestIdAndLeaderUserIdForUpdate(
            @Param("contestId") Long contestId,
            @Param("leaderUserId") Long leaderUserId);

    List<Team> findAllByLeaderUserIdOrderByCreatedAtDesc(Long leaderUserId);

    Optional<Team> findByContestPublicIdAndLeaderUserId(String contestPublicId, Long leaderUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select team
            from Team team
            where team.contest.publicId = :contestPublicId
              and team.leaderUser.id = :leaderUserId
            """)
    Optional<Team> findByContestPublicIdAndLeaderUserIdForUpdate(
            @Param("contestPublicId") String contestPublicId,
            @Param("leaderUserId") Long leaderUserId);

    List<Team> findAllByContestIdOrderByCreatedAtDesc(Long contestId);

    List<Team> findAllByContestIdAndStatusOrderByCreatedAtDesc(Long contestId, TeamStatus status);

    /**
     * 리뷰 라운드 편입 전 같은 대회의 팀을 고정된 순서로 잠근다.
     * 제출 덮어쓰기 경로도 TEAM을 먼저 잠그므로 교착 가능성을 낮춘다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select team
            from Team team
            where team.contest.id = :contestId
            order by team.id
            """)
    List<Team> findAllForUpdateByContestIdOrderByIdAsc(
            @Param("contestId") Long contestId);
}
