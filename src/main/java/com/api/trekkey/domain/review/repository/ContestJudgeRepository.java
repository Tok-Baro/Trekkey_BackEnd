package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ContestJudge;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestJudgeRepository extends JpaRepository<ContestJudge, Long> {

    Optional<ContestJudge> findByReviewTokenHash(String reviewTokenHash);

    List<ContestJudge> findAllByContestIdOrderByCreatedAtDesc(Long contestId);

    List<ContestJudge> findAllByContestId(Long contestId);
}
