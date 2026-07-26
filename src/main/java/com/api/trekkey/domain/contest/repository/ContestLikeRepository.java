package com.api.trekkey.domain.contest.repository;

import com.api.trekkey.domain.contest.entity.ContestLike;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestLikeRepository extends JpaRepository<ContestLike, Long> {

    List<ContestLike> findAllByContestIdIn(Collection<Long> contestIds);

    long countByContestId(Long contestId);

    Optional<ContestLike> findByContestIdAndUserId(Long contestId, Long userId);

    
}
