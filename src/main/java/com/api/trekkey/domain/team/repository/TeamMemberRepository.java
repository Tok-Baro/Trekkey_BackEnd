package com.api.trekkey.domain.team.repository;

import com.api.trekkey.domain.team.entity.TeamMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @Query("""
            select tm
            from TeamMember tm
            join fetch tm.user u
            where tm.team.id = :teamId
            order by u.id asc
            """)
    List<TeamMember> findAllByTeamIdOrderByUserIdAsc(@Param("teamId") Long teamId);
}
