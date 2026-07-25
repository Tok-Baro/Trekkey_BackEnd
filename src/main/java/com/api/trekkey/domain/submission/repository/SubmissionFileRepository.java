package com.api.trekkey.domain.submission.repository;

import com.api.trekkey.domain.submission.entity.SubmissionFile;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, Long> {

    List<SubmissionFile> findAllBySubmissionId(Long submissionId);

    List<SubmissionFile> findAllBySubmissionIdIn(Collection<Long> submissionIds);

    void deleteAllBySubmissionId(Long submissionId);
}
