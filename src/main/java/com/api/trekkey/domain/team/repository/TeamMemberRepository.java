package com.api.trekkey.domain.team.repository;

import com.api.trekkey.domain.team.entity.TeamMember;
import com.api.trekkey.domain.team.entity.TeamMemberRole;
import java.util.Collection;
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

    @Query("""
            select count(teamMember) > 0
            from TeamMember teamMember
            where teamMember.team.contest.id = :contestId
              and teamMember.user.id in :userIds
              and teamMember.team.id <> :teamId
            """)
    boolean existsByContestIdAndUserIdInAndTeamIdNot(
            @Param("contestId") Long contestId,
            @Param("userIds") Collection<Long> userIds,
            @Param("teamId") Long teamId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    List<TeamMember> findAllByTeamIdAndRole(Long teamId, TeamMemberRole role);
}
