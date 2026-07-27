package com.api.trekkey.domain.submission.repository;

import com.api.trekkey.domain.submission.entity.SubmissionFile;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, Long> {

    List<SubmissionFile> findAllBySubmissionId(Long submissionId);

    List<SubmissionFile> findAllBySubmissionIdIn(Collection<Long> submissionIds);

    void deleteAllBySubmissionId(Long submissionId);

    // 재제출 교체용 — REPEATABLE READ 스냅샷이 아닌 현재읽기(FOR UPDATE)로 직전 커밋 파일까지 확실히 본다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SubmissionFile f where f.submission.id = :submissionId")
    List<SubmissionFile> findAllBySubmissionIdForUpdate(@Param("submissionId") Long submissionId);

    // 벌크 삭제 — 엔티티 스냅샷을 거치지 않고 DB에서 직접 지운다 (동시 재제출 파일 누적 방지)
    @Modifying
    @Query("delete from SubmissionFile f where f.submission.id = :submissionId")
    void deleteAllBySubmissionIdBulk(@Param("submissionId") Long submissionId);
}
