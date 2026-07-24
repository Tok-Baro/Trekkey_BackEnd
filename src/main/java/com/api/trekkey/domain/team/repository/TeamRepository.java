package com.api.trekkey.domain.team.repository;

import com.api.trekkey.domain.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByContestIdAndLeaderUserId(Long contestId, Long leaderUserId);
}
