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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.publicId = :publicId")
    Optional<Team> findByPublicIdForUpdate(@Param("publicId") String publicId);

    List<Team> findAllByLeaderUserIdOrderByCreatedAtDesc(Long leaderUserId);

    List<Team> findAllByContestIdOrderByCreatedAtDesc(Long contestId);

    List<Team> findAllByContestIdAndStatusOrderByCreatedAtDesc(Long contestId, TeamStatus status);
}
