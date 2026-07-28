package com.api.trekkey.domain.review.repository;

import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.EntryStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContestStageEntryRepository extends JpaRepository<ContestStageEntry, Long> {

    boolean existsByContestStageId(Long contestStageId);

    List<ContestStageEntry> findAllByContestStageIdOrderByIdAsc(Long contestStageId);

    List<ContestStageEntry> findAllByContestStageIdAndStatus(Long contestStageId, EntryStatus status);

    @Query("""
            select entry
            from ContestStageEntry entry
            join fetch entry.contestStage stage
            where entry.submission.id = :submissionId
            order by stage.sequenceNo asc
            """)
    List<ContestStageEntry> findAllWithStageBySubmissionIdOrderBySequenceNoAsc(
            @Param("submissionId") Long submissionId);
}
