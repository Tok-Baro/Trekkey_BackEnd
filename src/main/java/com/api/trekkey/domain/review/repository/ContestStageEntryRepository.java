package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.EntryStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestStageEntryRepository extends JpaRepository<ContestStageEntry, Long> {

    boolean existsByContestStageId(Long contestStageId);

    List<ContestStageEntry> findAllByContestStageIdOrderByIdAsc(Long contestStageId);

    List<ContestStageEntry> findAllByContestStageIdAndStatus(Long contestStageId, EntryStatus status);
}
