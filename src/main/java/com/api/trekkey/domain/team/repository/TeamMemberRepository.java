package com.api.trekkey.domain.team.repository;

import com.api.trekkey.domain.team.entity.TeamMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findAllByTeamId(Long teamId);
}
