package com.api.trekkey.domain.team.repository;

import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByContestIdAndLeaderUserId(Long contestId, Long leaderUserId);

    Optional<Team> findByPublicId(String publicId);

    Optional<Team> findByContestPublicIdAndLeaderUserId(String contestPublicId, Long leaderUserId);

    List<Team> findAllByContestIdOrderByCreatedAtDesc(Long contestId);

    List<Team> findAllByContestIdAndStatusOrderByCreatedAtDesc(Long contestId, TeamStatus status);
}
