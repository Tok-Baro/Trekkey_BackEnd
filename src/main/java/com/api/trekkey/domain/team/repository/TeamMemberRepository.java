package com.api.trekkey.domain.team.repository;

import com.api.trekkey.domain.team.entity.TeamMember;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @Query("""
            select teamMember
            from TeamMember teamMember
            join fetch teamMember.team team
            join fetch team.contest
            where teamMember.user.id = :userId
            order by team.createdAt desc
            """)
    List<TeamMember> findAllWithTeamAndContestByUserId(@Param("userId") Long userId);

    boolean existsByTeamContestIdAndUserIdIn(Long contestId, Collection<Long> userIds);
}
