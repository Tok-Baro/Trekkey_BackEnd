package com.api.trekkey.domain.award.repository;

import com.api.trekkey.domain.award.entity.Award;
import com.api.trekkey.domain.award.entity.AwardStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AwardRepository extends JpaRepository<Award, Long> {

    Optional<Award> findByPublicId(String publicId);

    List<Award> findAllByTeamContestIdOrderByAwardRankNoAsc(Long contestId);

    boolean existsByTeamContestIdAndStatus(Long contestId, AwardStatus status);

    // 리더 기준 내 수상 목록 (확정분)
    List<Award> findAllByTeamLeaderUserIdAndStatusOrderByConfirmedAtDesc(Long leaderUserId, AwardStatus status);
}
