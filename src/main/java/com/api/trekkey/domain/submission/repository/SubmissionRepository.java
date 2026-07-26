package com.api.trekkey.domain.submission.repository;

import com.api.trekkey.domain.submission.entity.Submission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByPublicId(String publicId);

    Optional<Submission> findByTeamId(Long teamId);

    @Query("""
            select s
            from Submission s
            where s.team.contest.id = :contestId
            order by s.submittedAt desc
            """)
    List<Submission> findAllByContestId(@Param("contestId") Long contestId);
}
