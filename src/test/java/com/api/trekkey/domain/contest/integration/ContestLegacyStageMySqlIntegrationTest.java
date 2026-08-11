package com.api.trekkey.domain.contest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.trekkey.domain.audit.repository.AdminAuditLogRepository;
import com.api.trekkey.domain.contest.admin.service.ContestAdminQueryService;
import com.api.trekkey.domain.contest.admin.service.ContestCommandService;
import com.api.trekkey.domain.contest.admin.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.admin.web.dto.StageReq;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.entity.StagePassRule;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageTargetType;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
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
class ContestLegacyStageMySqlIntegrationTest {

    @Autowired
    private ContestCommandService contestCommandService;

    @Autowired
    private ContestAdminQueryService contestAdminQueryService;

    @Autowired
    private ContestStageRepository contestStageRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    private User admin;
    private Contest contest;
    private ContestStage application;
    private ContestStage legacyReview;
    private ContestStage submission;

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
                "이전 단계 통합테스트대학교");
        ReflectionTestUtils.setField(
                organization,
                "status",
                OrganizationStatus.ACTIVE);
        organization = organizationRepository.saveAndFlush(organization);

        admin = userRepository.saveAndFlush(User.builder()
                .organization(organization)
                .name("대회 관리자")
                .email("mysql-legacy-stage-admin@example.com")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build());

        contest = contestRepository.saveAndFlush(Contest.builder()
                .organization(organization)
                .ownerUser(admin)
                .title("이전 단계 보존 테스트")
                .department("교무처")
                .status(ContestStatus.APPLICATION_OPEN)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("이전 심사 단계와 일반 단계를 함께 관리합니다.")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build());

        application = contestStageRepository.saveAndFlush(
                ContestStage.builder()
                        .contest(contest)
                        .name("참가 신청")
                        .stageType(StageType.APPLICATION)
                        .sequenceNo(1)
                        .status(StageStatus.PREPARING)
                        .build());
        legacyReview = contestStageRepository.saveAndFlush(
                ContestStage.builder()
                        .contest(contest)
                        .name("이전 1차 심사")
                        .stageType(StageType.REVIEW)
                        .sequenceNo(2)
                        .status(StageStatus.OPEN)
                        .startsAt(LocalDateTime.now().minusDays(1))
                        .endsAt(LocalDateTime.now().plusDays(1))
                        .targetType(StageTargetType.ALL_SUBMISSIONS)
                        .passRule(StagePassRule.TOP_N)
                        .passCount(1)
                        .build());
        submission = contestStageRepository.saveAndFlush(
                ContestStage.builder()
                        .contest(contest)
                        .name("작품 제출")
                        .stageType(StageType.SUBMISSION)
                        .sequenceNo(3)
                        .status(StageStatus.PREPARING)
                        .endsAt(LocalDateTime.now().plusDays(2))
                        .build());
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("이전 심사 단계의 순서를 보존하면서 일반 단계를 수정하고 추가한다")
    void updateContest_preservesLegacyReviewStageAndUniqueSequences() {
        ContestDetailRes response = contestCommandService.updateContest(
                admin.getId(),
                contest.getPublicId(),
                request(List.of(
                        stage(
                                application.getId(),
                                "참가 신청",
                                StageType.APPLICATION,
                                1,
                                null),
                        stage(
                                submission.getId(),
                                "작품 제출",
                                StageType.SUBMISSION,
                                2,
                                submission.getEndsAt()),
                        stage(
                                null,
                                "시상",
                                StageType.AWARD,
                                3,
                                null)
                ))
        );

        assertThat(response.stages())
                .extracting(StageRes::sequenceNo)
                .containsExactly(1, 3, 4);
        assertThat(contestAdminQueryService.getContest(
                admin.getId(),
                contest.getPublicId()).stages())
                .extracting(StageRes::stageType)
                .containsExactly(
                        StageType.APPLICATION,
                        StageType.SUBMISSION,
                        StageType.AWARD)
                .doesNotContain(StageType.REVIEW);

        List<ContestStage> stored = contestStageRepository
                .findAllByContestIdOrderBySequenceNoAsc(contest.getId());
        assertThat(stored)
                .extracting(
                        ContestStage::getSequenceNo,
                        ContestStage::getStageType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                1,
                                StageType.APPLICATION),
                        org.assertj.core.groups.Tuple.tuple(
                                2,
                                StageType.REVIEW),
                        org.assertj.core.groups.Tuple.tuple(
                                3,
                                StageType.SUBMISSION),
                        org.assertj.core.groups.Tuple.tuple(
                                4,
                                StageType.AWARD));
        assertThat(contestStageRepository.findById(legacyReview.getId()))
                .isPresent()
                .get()
                .extracting(ContestStage::getSequenceNo)
                .isEqualTo(2);
    }

    private ContestCreateReq request(List<StageReq> stages) {
        return new ContestCreateReq(
                contest.getTitle(),
                contest.getDepartment(),
                ContestStatus.APPLICATION_OPEN,
                contest.getParticipationType(),
                contest.getMaxTeamMembers(),
                contest.getAwardCount(),
                contest.getPosterUrl(),
                contest.getSummary(),
                contest.getTarget(),
                contest.getApplicationMethod(),
                contest.getBenefits(),
                contest.getTags(),
                contest.getDetailHtml(),
                stages);
    }

    private StageReq stage(
            Long id,
            String name,
            StageType type,
            int sequenceNo,
            LocalDateTime endsAt
    ) {
        return new StageReq(
                id,
                name,
                type,
                sequenceNo,
                StageStatus.PREPARING,
                null,
                endsAt,
                null,
                null,
                null,
                null,
                null);
    }

    private void deleteTestData() {
        adminAuditLogRepository.deleteAllInBatch();
        contestStageRepository.deleteAllInBatch();
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
