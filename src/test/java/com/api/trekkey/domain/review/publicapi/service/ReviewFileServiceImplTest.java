package com.api.trekkey.domain.review.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.global.exception.CustomException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewFileServiceImplTest {

    private static final String RAW_TOKEN = "a".repeat(43);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 28, 12, 0);

    @Mock
    private ReviewLinkAuthenticator reviewLinkAuthenticator;

    @Mock
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Mock
    private SubmissionFileRepository submissionFileRepository;

    @Mock
    private FileStoragePort fileStoragePort;

    private ReviewFileServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-28T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new ReviewFileServiceImpl(
                reviewLinkAuthenticator,
                reviewAssignmentRepository,
                submissionFileRepository,
                fileStoragePort,
                clock
        );
    }

    @Test
    @DisplayName("심사위원에게 취소되지 않은 배정이 있는 제출 파일만 다운로드한다")
    void downloadFile_returnsAssignedSubmissionFile() {
        ContestJudge judge = org.mockito.Mockito.mock(ContestJudge.class);
        Submission submission = org.mockito.Mockito.mock(Submission.class);
        SubmissionFile file =
                org.mockito.Mockito.mock(SubmissionFile.class);
        InputStream inputStream =
                new ByteArrayInputStream("review-file".getBytes());

        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN, NOW))
                .willReturn(judge);
        given(judge.getId()).willReturn(200L);
        given(submissionFileRepository.findById(10L))
                .willReturn(Optional.of(file));
        given(file.getSubmission()).willReturn(submission);
        given(submission.getId()).willReturn(300L);
        given(reviewAssignmentRepository
                .existsByContestJudgeIdAndReviewRoundEntrySubmissionIdAndStatusNot(
                        200L,
                        300L,
                        ReviewAssignmentStatus.CANCELED
                ))
                .willReturn(true);
        given(file.getOriginalName()).willReturn("작품.pdf");
        given(file.getContentType()).willReturn("application/pdf");
        given(file.getSizeBytes()).willReturn(11L);
        given(file.getStorageKey()).willReturn("submissions/300/work.pdf");
        given(fileStoragePort.open("submissions/300/work.pdf"))
                .willReturn(inputStream);

        FileDownload result = service.downloadFile(
                10L,
                new ReviewAccessReq(RAW_TOKEN)
        );

        assertThat(result.originalName()).isEqualTo("작품.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.sizeBytes()).isEqualTo(11L);
        assertThat(result.inputStream()).isSameAs(inputStream);
        verify(reviewAssignmentRepository)
                .existsByContestJudgeIdAndReviewRoundEntrySubmissionIdAndStatusNot(
                        200L,
                        300L,
                        ReviewAssignmentStatus.CANCELED
                );
        verify(fileStoragePort).open("submissions/300/work.pdf");
    }

    @Test
    @DisplayName("취소된 배정만 있는 제출 파일은 존재 여부를 숨기고 거부한다")
    void downloadFile_rejectsCanceledAssignment() {
        ContestJudge judge = org.mockito.Mockito.mock(ContestJudge.class);
        Submission submission = org.mockito.Mockito.mock(Submission.class);
        SubmissionFile file =
                org.mockito.Mockito.mock(SubmissionFile.class);

        given(reviewLinkAuthenticator.authenticate(RAW_TOKEN, NOW))
                .willReturn(judge);
        given(judge.getId()).willReturn(200L);
        given(submissionFileRepository.findById(10L))
                .willReturn(Optional.of(file));
        given(file.getSubmission()).willReturn(submission);
        given(submission.getId()).willReturn(300L);
        given(reviewAssignmentRepository
                .existsByContestJudgeIdAndReviewRoundEntrySubmissionIdAndStatusNot(
                        200L,
                        300L,
                        ReviewAssignmentStatus.CANCELED
                ))
                .willReturn(false);

        assertThatThrownBy(() -> service.downloadFile(
                10L,
                new ReviewAccessReq(RAW_TOKEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e)
                        .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode.REVIEW_ASSIGNMENT_NOT_FOUND);

        verifyNoInteractions(fileStoragePort);
    }
}
