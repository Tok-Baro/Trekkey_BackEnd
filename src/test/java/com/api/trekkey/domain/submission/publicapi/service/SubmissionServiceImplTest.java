package com.api.trekkey.domain.submission.publicapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.domain.submission.support.StoredFile;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ContestStageRepository contestStageRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionFileRepository submissionFileRepository;

    @Mock
    private FileStoragePort fileStoragePort;

    private SubmissionServiceImpl submissionService;

    private User leader;
    private Contest contest;
    private Team team;

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionServiceImpl(
                userRepository, teamRepository, contestStageRepository,
                submissionRepository, submissionFileRepository, fileStoragePort);

        leader = mock(User.class);
        lenient().when(leader.getId()).thenReturn(10L);
        lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(leader));

        contest = mock(Contest.class);
        lenient().when(contest.getId()).thenReturn(200L);

        team = mock(Team.class);
        lenient().when(team.getId()).thenReturn(1L);
        lenient().when(team.getPublicId()).thenReturn("team-pub-1");
        lenient().when(team.getName()).thenReturn("팀트레키");
        lenient().when(team.getLeaderUser()).thenReturn(leader);
        lenient().when(team.getContest()).thenReturn(contest);
        lenient().when(team.getStatus()).thenReturn(TeamStatus.APPROVED);
        lenient().when(teamRepository.findByPublicId("team-pub-1")).thenReturn(Optional.of(team));
        lenient().when(teamRepository.findByPublicIdForUpdate("team-pub-1")).thenReturn(Optional.of(team));

        lenient().when(fileStoragePort.store(anyString(), anyString(), any()))
                .thenReturn(new StoredFile("submissions/pub/abc.pdf", 1024L, "a".repeat(64)));
        lenient().when(submissionFileRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("최초 제출 시 SHA-256이 확정되어 READY 상태로 저장된다")
    void submit_createsSubmissionWithReadyIntegrity() {
        givenSubmissionStageOpen();
        given(submissionRepository.findByTeamIdForUpdate(1L)).willReturn(Optional.empty());
        given(submissionRepository.save(any(Submission.class))).willAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            ReflectionTestUtils.setField(submission, "id", 100L);
            ReflectionTestUtils.setField(submission, "publicId", "sub-pub-1");
            return submission;
        });

        SubmissionRes result = submissionService.submit(10L, "team-pub-1", "AI 작품", List.of(pdfFile()));

        assertThat(result.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).sha256()).hasSize(64);
        verify(teamRepository).findByPublicIdForUpdate("team-pub-1");
    }

    @Test
    @DisplayName("재제출 시 현재 제목과 파일을 덮어쓰고 이전 파일 객체를 정리한다")
    void submit_overwritesCurrentSubmissionAndCleansOldFiles() {
        givenSubmissionStageOpen();
        Submission existing = submissionFixture(null);
        given(submissionRepository.findByTeamIdForUpdate(1L)).willReturn(Optional.of(existing));
        SubmissionFile oldFile = mock(SubmissionFile.class);
        given(oldFile.getStorageKey()).willReturn("submissions/old/key.pdf");
        given(submissionFileRepository.findAllBySubmissionId(100L)).willReturn(List.of(oldFile));

        SubmissionRes result = submissionService.submit(10L, "team-pub-1", "수정된 작품", List.of(pdfFile()));

        assertThat(result.title()).isEqualTo("수정된 작품");
        verify(submissionFileRepository).deleteAllBySubmissionId(100L);
        verify(fileStoragePort).delete("submissions/old/key.pdf");
    }

    @Test
    @DisplayName("이전 파일 객체는 DB 트랜잭션 커밋 이후에만 삭제한다")
    void submit_deletesPreviousFilesAfterCommit() {
        givenSubmissionStageOpen();
        Submission existing = submissionFixture(null);
        given(submissionRepository.findByTeamIdForUpdate(1L)).willReturn(Optional.of(existing));
        SubmissionFile oldFile = mock(SubmissionFile.class);
        given(oldFile.getStorageKey()).willReturn("submissions/old/key.pdf");
        given(submissionFileRepository.findAllBySubmissionId(100L)).willReturn(List.of(oldFile));

        TransactionSynchronizationManager.initSynchronization();
        try {
            submissionService.submit(10L, "team-pub-1", "수정된 작품", List.of(pdfFile()));
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            verify(fileStoragePort, never()).delete("submissions/old/key.pdf");
            TransactionSynchronizationManager.clearSynchronization();
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            synchronizations.forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

            verify(fileStoragePort).delete("submissions/old/key.pdf");
            verify(fileStoragePort, never()).delete("submissions/pub/abc.pdf");
        } finally {
            clearTransactionSynchronization();
        }
    }

    @Test
    @DisplayName("DB 트랜잭션이 롤백되면 새 파일 객체만 삭제한다")
    void submit_deletesNewFilesAfterRollback() {
        givenSubmissionStageOpen();
        Submission existing = submissionFixture(null);
        given(submissionRepository.findByTeamIdForUpdate(1L)).willReturn(Optional.of(existing));
        SubmissionFile oldFile = mock(SubmissionFile.class);
        given(oldFile.getStorageKey()).willReturn("submissions/old/key.pdf");
        given(submissionFileRepository.findAllBySubmissionId(100L)).willReturn(List.of(oldFile));

        TransactionSynchronizationManager.initSynchronization();
        try {
            submissionService.submit(10L, "team-pub-1", "수정된 작품", List.of(pdfFile()));
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            TransactionSynchronizationManager.clearSynchronization();
            synchronizations.forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(fileStoragePort).delete("submissions/pub/abc.pdf");
            verify(fileStoragePort, never()).delete("submissions/old/key.pdf");
        } finally {
            clearTransactionSynchronization();
        }
    }

    @Test
    @DisplayName("제출이 잠긴 제출물은 덮어쓸 수 없다")
    void submit_throwsWhenFinalized() {
        givenSubmissionStageOpen();
        Submission finalized = submissionFixture(LocalDateTime.now());
        given(submissionRepository.findByTeamIdForUpdate(1L)).willReturn(Optional.of(finalized));

        assertThatThrownBy(() -> submissionService.submit(10L, "team-pub-1", "작품", List.of(pdfFile())))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_FINALIZED);
    }

    @Test
    @DisplayName("제출 단계가 열려 있지 않으면 제출할 수 없다")
    void submit_throwsWhenStageNotOpen() {
        ContestStage closedStage = mock(ContestStage.class);
        given(closedStage.getStageType()).willReturn(StageType.SUBMISSION);
        given(closedStage.getStatus()).willReturn(StageStatus.COMPLETED);
        given(contestStageRepository.findAllByContestIdOrderBySequenceNoAsc(200L))
                .willReturn(List.of(closedStage));

        assertThatThrownBy(() -> submissionService.submit(10L, "team-pub-1", "작품", List.of(pdfFile())))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_NOT_OPEN);
    }

    @Test
    @DisplayName("허용되지 않는 확장자는 거부한다")
    void submit_throwsWhenExtensionInvalid() {
        givenSubmissionStageOpen();
        MultipartFile executable =
                new MockMultipartFile("files", "malware.exe", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> submissionService.submit(10L, "team-pub-1", "작품", List.of(executable)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_FILE_TYPE_INVALID);
    }

    @Test
    @DisplayName("파일 없이 제출하면 거부한다")
    void submit_throwsWhenNoFiles() {
        givenSubmissionStageOpen();

        assertThatThrownBy(() -> submissionService.submit(10L, "team-pub-1", "작품", List.of()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_FILE_REQUIRED);
    }

    @Test
    @DisplayName("팀 대표가 아니면 제출할 수 없다")
    void submit_throwsWhenNotLeader() {
        User other = mock(User.class);
        given(other.getId()).willReturn(99L);
        given(userRepository.findById(99L)).willReturn(Optional.of(other));

        assertThatThrownBy(() -> submissionService.submit(99L, "team-pub-1", "작품", List.of(pdfFile())))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_FORBIDDEN);
    }

    @Test
    @DisplayName("다른 팀 파일은 다운로드할 수 없다")
    void downloadFile_throwsWhenNotOwner() {
        User other = mock(User.class);
        given(other.getId()).willReturn(99L);
        given(userRepository.findById(99L)).willReturn(Optional.of(other));

        Submission submission = submissionFixture(null);
        SubmissionFile file = mock(SubmissionFile.class);
        given(file.getSubmission()).willReturn(submission);
        given(submissionFileRepository.findById(5L)).willReturn(Optional.of(file));

        assertThatThrownBy(() -> submissionService.downloadFile(99L, 5L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(SubmissionErrorResponseCode.SUBMISSION_FORBIDDEN);
    }

    //======= 헬퍼 메서드 ==========

    private void givenSubmissionStageOpen() {
        ContestStage stage = mock(ContestStage.class);
        lenient().when(stage.getStageType()).thenReturn(StageType.SUBMISSION);
        lenient().when(stage.getStatus()).thenReturn(StageStatus.OPEN);
        lenient().when(stage.getEndsAt()).thenReturn(LocalDateTime.now().plusDays(7));
        lenient().when(contestStageRepository.findAllByContestIdOrderBySequenceNoAsc(200L))
                .thenReturn(List.of(stage));
    }

    private Submission submissionFixture(LocalDateTime finalizedAt) {
        Submission submission = Submission.builder()
                .publicId("sub-pub-1")
                .team(team)
                .title("원래 작품")
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now().minusDays(1))
                .build();
        ReflectionTestUtils.setField(submission, "id", 100L);
        if (finalizedAt != null) {
            ReflectionTestUtils.setField(submission, "finalizedAt", finalizedAt);
        }
        return submission;
    }

    private MultipartFile pdfFile() {
        return new MockMultipartFile("files", "제안서.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    private void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
