package com.api.trekkey.domain.team.repository;

import com.api.trekkey.domain.team.entity.Team;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByContestIdAndLeaderUserId(Long contestId, Long leaderUserId);

    Optional<Team> findByContestIdAndLeaderUserId(Long contestId, Long leaderUserId);

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
