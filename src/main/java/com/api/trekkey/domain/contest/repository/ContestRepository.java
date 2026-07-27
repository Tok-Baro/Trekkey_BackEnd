package com.api.trekkey.domain.contest.repository;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    java.util.Optional<Contest> findByPublicId(String publicId);

    Optional<Contest> findByPublicIdAndOrganizationId(
            String publicId,
            Long organizationId);

    @Query("""
            select contest
            from Contest contest
            where contest.organization.id = :organizationId
              and contest.status in :statuses
              and (
                    lower(contest.title) like lower(concat('%', :keyword, '%'))
                    or lower(contest.department) like lower(concat('%', :keyword, '%'))
                    or lower(contest.tags) like lower(concat('%', :keyword, '%'))
              )
            order by contest.createdAt desc, contest.id desc
            """)
    List<Contest> searchContests(
            @Param("organizationId") Long organizationId,
            @Param("keyword") String keyword,
            @Param("statuses") Collection<ContestStatus> statuses);

    Optional<Contest> findByPublicIdAndOrganizationIdAndStatusIn(
            String publicId,
            Long organizationId,
            Collection<ContestStatus> statuses);
}
