package com.api.trekkey.domain.submission.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.publicapi.service.SubmissionService;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionSaveReq;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
class SubmissionMySqlIntegrationTest {

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ContestStageRepository contestStageRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private User participant;
    private Contest contest;

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
                "제출 통합테스트대학교"
        );
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE
        );
        organization = organizationRepository.saveAndFlush(organization);

        participant = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("김참가")
                .email("mysql-submission-participant@example.com")
                .password("encoded-password")
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .studentId("20260001")
                .major("컴퓨터공학부")
                .build());

        contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(participant)
                .title("제출 통합 테스트 대회")
                .department("교무처")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("제출 통합 테스트")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build());

        contestStageRepository.saveAndFlush(ContestStage.builder()
                .contest(contest)
                .name("작품 제출")
                .stageType(StageType.SUBMISSION)
                .sequenceNo(1)
                .status(StageStatus.OPEN)
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(1))
                .build());

        teamRepository.saveAndFlush(Team.builder()
                .contest(contest)
                .leaderUser(participant)
                .name("트랙키 팀")
                .leaderName("김참가")
                .major("컴퓨터공학부")
                .memberCount(1)
                .status(TeamStatus.APPROVED)
                .contactEmail("participant@example.com")
                .phone("010-1234-5678")
                .motivation("학교 문제를 해결합니다.")
                .build());
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("MySQL에서 제출물 UUID와 감사 시각을 저장하고 재제출 상태를 유지한다")
    void submissionLifecycle_persistsSingleSubmission() {
        SubmissionRes draft = submissionService.saveDraft(
                participant.getId(),
                contest.getPublicId(),
                new SubmissionSaveReq("  AI 캠퍼스  ")
        );
        SubmissionRes submitted = submissionService.submit(
                participant.getId(),
                contest.getPublicId()
        );
        SubmissionRes reopened = submissionService.reopen(
                participant.getId(),
                contest.getPublicId()
        );
        SubmissionRes updated = submissionService.saveDraft(
                participant.getId(),
                contest.getPublicId(),
                new SubmissionSaveReq("AI 캠퍼스 개선")
        );

        assertThat(draft.publicId()).hasSize(36);
        assertThat(draft.title()).isEqualTo("AI 캠퍼스");
        assertThat(draft.createdAt()).isNotNull();
        assertThat(draft.updatedAt()).isNotNull();
        assertThat(submitted.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submitted.submittedAt()).isNotNull();
        assertThat(reopened.status()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(reopened.submittedAt()).isEqualTo(submitted.submittedAt());
        assertThat(updated.title()).isEqualTo("AI 캠퍼스 개선");
        assertThat(submissionRepository.count()).isEqualTo(1);
    }

    @Test
    @Timeout(15)
    @DisplayName("동시에 최초 저장해도 팀 잠금으로 제출물은 한 건만 생성한다")
    void concurrentSaveDraft_createsOneSubmission() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<SubmissionRes>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return submissionService.saveDraft(
                            participant.getId(),
                            contest.getPublicId(),
                            new SubmissionSaveReq("동시 제출 작품")
                    );
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<SubmissionRes> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS).status())
                        .isEqualTo(SubmissionStatus.DRAFT);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(submissionRepository.count()).isEqualTo(1);
    }

    private void deleteTestData() {
        submissionRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        contestStageRepository.deleteAllInBatch();
        contestRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시성 테스트 대기 시간이 초과되었습니다."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "동시성 테스트 대기가 중단되었습니다.",
                    exception
            );
        }
    }
}
