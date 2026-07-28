package com.api.trekkey.domain.review.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.publicapi.service.ReviewFileService;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.support.ReviewLinkTokenManager;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "true"
)
@EnabledIfEnvironmentVariable(
        named = "MYSQL_TEST_URL",
        matches = "jdbc:mysql://.+/trekkey_test(?:\\?.*)?"
)
class ReviewFileMySqlIntegrationTest {

    private static final String RAW_TOKEN = "f".repeat(43);
    private static final String STORAGE_KEY =
            "review-file-integration/work.pdf";

    @Autowired
    private ReviewFileService reviewFileService;

    @Autowired
    private ReviewLinkTokenManager reviewLinkTokenManager;

    @Autowired
    private SubmissionFileRepository submissionFileRepository;

    @Autowired
    private ReviewAssignmentRepository reviewAssignmentRepository;

    @Autowired
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Autowired
    private ContestJudgeRepository contestJudgeRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ReviewRoundRepository reviewRoundRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockitoBean
    private FileStoragePort fileStoragePort;

    private SubmissionFile submissionFile;
    private ReviewRound reviewRound;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> System.getenv("MYSQL_TEST_URL")
        );
        registry.add(
                "spring.datasource.username",
                () -> environment("MYSQL_TEST_USERNAME", "root")
        );
        registry.add(
                "spring.datasource.password",
                () -> environment("MYSQL_TEST_PASSWORD", "")
        );
    }

    @BeforeEach
    void setUp() {
        deleteTestData();

        Organization organization =
                BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(
                organization,
                "name",
                "심사 파일 통합테스트대학교"
        );
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        organization = organizationRepository.saveAndFlush(organization);

        User admin = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("심사 파일 관리자")
                .email("mysql-review-file-admin@example.com")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build());

        User participant = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("심사 파일 참가자")
                .email("mysql-review-file-participant@example.com")
                .password("encoded-password")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId("20260004")
                .major("컴퓨터공학부")
                .build());

        Contest contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("심사 파일 통합 테스트 대회")
                .department("산학협력단")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("심사 파일 통합 테스트")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build());

        LocalDateTime now = LocalDateTime.now();
        reviewRound = reviewRoundRepository.saveAndFlush(
                ReviewRound.builder()
                        .contest(contest)
                        .name("1차 심사")
                        .roundNo(1)
                        .status(ReviewRoundStatus.OPEN)
                        .startsAt(now.minusHours(1))
                        .endsAt(now.plusHours(2))
                        .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                        .decisionRule(ReviewRoundDecisionRule.MANUAL)
                        .build()
        );

        Team team = teamRepository.saveAndFlush(Team.builder()
                .contest(contest)
                .leaderUser(participant)
                .name("심사 파일 팀")
                .leaderName(participant.getName())
                .major("컴퓨터공학부")
                .memberCount(1)
                .status(TeamStatus.APPROVED)
                .contactEmail("participant@example.com")
                .phone("010-1234-5678")
                .motivation("학교 문제를 해결합니다.")
                .build());

        Submission submission = submissionRepository.saveAndFlush(
                Submission.builder()
                        .team(team)
                        .title("심사 대상 작품")
                        .status(SubmissionStatus.SUBMITTED)
                        .submittedAt(now.minusDays(1))
                        .build()
        );

        ReviewRoundEntry entry = reviewRoundEntryRepository.saveAndFlush(
                ReviewRoundEntry.builder()
                        .reviewRound(reviewRound)
                        .submission(submission)
                        .status(ReviewRoundEntryStatus.IN_REVIEW)
                        .build()
        );

        ContestJudge judge = ContestJudge.builder()
                .contest(contest)
                .name("김심사")
                .roleLabel("외부 전문가")
                .build();
        judge.issueReviewLink(
                reviewLinkTokenManager.hash(RAW_TOKEN),
                now.minusMinutes(1),
                now.plusHours(1)
        );
        judge = contestJudgeRepository.saveAndFlush(judge);

        reviewAssignmentRepository.saveAndFlush(
                ReviewAssignment.builder()
                        .contestJudge(judge)
                        .reviewRoundEntry(entry)
                        .status(ReviewAssignmentStatus.ASSIGNED)
                        .assignedAt(now.minusMinutes(30))
                        .dueAt(now.plusHours(1))
                        .build()
        );

        submissionFile = submissionFileRepository.saveAndFlush(
                SubmissionFile.builder()
                        .submission(submission)
                        .uploadedBy(participant)
                        .originalName("작품.pdf")
                        .contentType("application/pdf")
                        .sizeBytes(11L)
                        .storageKey(STORAGE_KEY)
                        .sha256("0".repeat(64))
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("MySQL에서 링크 인증 잠금과 파일 다운로드를 같은 쓰기 가능 트랜잭션으로 처리한다")
    void downloadFile_authenticatesWithLockAndReturnsStream()
            throws Exception {
        byte[] contents = "review-file".getBytes(StandardCharsets.UTF_8);
        given(fileStoragePort.open(STORAGE_KEY))
                .willReturn(new ByteArrayInputStream(contents));

        FileDownload result = reviewFileService.downloadFile(
                submissionFile.getId(),
                new ReviewAccessReq(RAW_TOKEN)
        );

        assertThat(result.originalName()).isEqualTo("작품.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.sizeBytes()).isEqualTo(11L);
        assertThat(result.inputStream().readAllBytes())
                .isEqualTo(contents);
    }

    @Test
    @DisplayName("라운드가 확정되면 기존 링크와 배정으로도 제출 파일을 열 수 없다")
    void downloadFile_rejectsFinalizedRoundWithoutOpeningStorage() {
        reviewRound.finalizeAt(LocalDateTime.now());
        reviewRoundRepository.saveAndFlush(reviewRound);

        assertThatThrownBy(() -> reviewFileService.downloadFile(
                submissionFile.getId(),
                new ReviewAccessReq(RAW_TOKEN)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error)
                        .getBaseResponseCode())
                .isEqualTo(
                        ReviewErrorResponseCode.REVIEW_ASSIGNMENT_NOT_FOUND);

        verifyNoInteractions(fileStoragePort);
    }

    private void deleteTestData() {
        submissionFileRepository.deleteAllInBatch();
        reviewAssignmentRepository.deleteAllInBatch();
        reviewRoundEntryRepository.deleteAllInBatch();
        contestJudgeRepository.deleteAllInBatch();
        submissionRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        reviewRoundRepository.deleteAllInBatch();
        contestRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    private static String environment(
            String name,
            String defaultValue
    ) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }
}
