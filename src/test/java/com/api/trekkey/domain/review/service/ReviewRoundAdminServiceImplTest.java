package com.api.trekkey.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewCriterionRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundEntryRepository;
import com.api.trekkey.domain.review.repository.ReviewRoundRepository;
import com.api.trekkey.domain.review.web.dto.request.ReviewRoundCriterionReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewRoundSaveReq;
import com.api.trekkey.domain.review.web.dto.response.ReviewRoundRes;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewRoundAdminServiceImplTest {

    private static final Long ADMIN_ID = 10L;
    private static final Long ORGANIZATION_ID = 20L;
    private static final Long CONTEST_ID = 30L;
    private static final Long ROUND_ID = 40L;
    private static final String CONTEST_PUBLIC_ID = "contest-public-id";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 27, 12, 0);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime STARTS_AT =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime ENDS_AT =
            LocalDateTime.of(2026, 8, 2, 18, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ReviewRoundRepository reviewRoundRepository;

    @Mock
    private ReviewCriterionRepository reviewCriterionRepository;

    @Mock
    private ReviewRoundEntryRepository reviewRoundEntryRepository;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    @Mock
    private EntityManager entityManager;

    private ReviewRoundAdminServiceImpl service;
    private Organization organization;
    private User admin;
    private Contest contest;

    @BeforeEach
    void setUp() {
        service = new ReviewRoundAdminServiceImpl(
                userRepository,
                contestRepository,
                reviewRoundRepository,
                reviewCriterionRepository,
                reviewRoundEntryRepository,
                adminAuditLogger,
                entityManager,
                Clock.fixed(NOW.atZone(ZONE_ID).toInstant(), ZONE_ID)
        );

        organization = org.mockito.Mockito.mock(Organization.class);
        org.mockito.Mockito.lenient()
                .when(organization.getId())
                .thenReturn(ORGANIZATION_ID);
        admin = User.builder()
                .organization(organization)
                .name("관리자")
                .email("admin@example.com")
                .password("encoded")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        contest = Contest.builder()
                .publicId(CONTEST_PUBLIC_ID)
                .organization(organization)
                .ownerUser(admin)
                .title("AI 공모전")
                .department("교무처")
                .status(ContestStatus.REVIEWING)
                .participationType(ParticipationType.TEAM)
                .awardCount(1)
                .summary("AI 공모전")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build();
        ReflectionTestUtils.setField(contest, "id", CONTEST_ID);

        given(userRepository.findById(ADMIN_ID))
                .willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId(CONTEST_PUBLIC_ID))
                .willReturn(Optional.of(contest));
    }

    @Test
    @DisplayName("심사 라운드와 라운드별 평가 기준을 함께 생성한다")
    void createRound_createsRoundAndCriteria() {
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of());
        given(reviewRoundRepository.saveAndFlush(any(ReviewRound.class)))
                .willAnswer(invocation -> {
                    ReviewRound round = invocation.getArgument(0);
                    ReflectionTestUtils.setField(round, "id", ROUND_ID);
                    return round;
                });

        ReviewRoundRes response = service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                request()
        );

        assertThat(response.id()).isEqualTo(ROUND_ID);
        assertThat(response.status()).isEqualTo(ReviewRoundStatus.PREPARING);
        assertThat(response.roundNo()).isEqualTo(1);
        assertThat(response.criteria()).singleElement().satisfies(criterion -> {
            assertThat(criterion.code()).isEqualTo("creativity");
            assertThat(criterion.maxScore()).isEqualTo(50);
        });

        verify(entityManager).refresh(
                contest,
                LockModeType.PESSIMISTIC_WRITE
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReviewCriterion>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewCriterionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(criterion -> {
            assertThat(criterion.getReviewRound().getId()).isEqualTo(ROUND_ID);
            assertThat(criterion.getCode()).isEqualTo("creativity");
            assertThat(criterion.isActive()).isTrue();
        });
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ROUND_CREATE,
                "REVIEW_ROUND",
                ROUND_ID,
                "contestId=30, roundNo=1"
        );
    }

    @ParameterizedTest(name = "{0} 대상 선정 방식은 아직 생성할 수 없다")
    @EnumSource(
            value = ReviewRoundTargetType.class,
            names = {"PREVIOUS_SELECTED", "MANUAL"}
    )
    @DisplayName("구현되지 않은 대상 선정 방식은 라운드 생성 단계에서 거부한다")
    void createRound_rejectsUnsupportedTargetType(
            ReviewRoundTargetType targetType
    ) {
        ReviewRoundSaveReq unsupported = new ReviewRoundSaveReq(
                1,
                "예선 심사",
                STARTS_AT,
                ENDS_AT,
                targetType,
                ReviewRoundDecisionRule.TOP_N,
                10,
                null,
                List.of(new ReviewRoundCriterionReq(
                        null,
                        "creativity",
                        "창의성",
                        50,
                        1
                ))
        );

        assertThatThrownBy(() -> service.createRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                unsupported
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ENTRY_TARGET_TYPE_UNSUPPORTED);

        verifyNoInteractions(reviewRoundRepository);
    }

    @Test
    @DisplayName("진행 중인 심사 라운드의 설정은 변경할 수 없다")
    void updateRound_rejectsOpenRound() {
        ReviewRound round = round(ReviewRoundStatus.OPEN);
        stubRoundOrganization();
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(round));

        assertThatThrownBy(() -> service.updateRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                request()
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_CONFIGURATION_LOCKED);

        verifyNoInteractions(reviewCriterionRepository);
    }

    @Test
    @DisplayName("심사 대상이 준비된 라운드의 설정은 변경할 수 없다")
    void updateRound_rejectsRoundWithEntries() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReviewCriterion criterion = criterion(round);
        stubRoundOrganization();
        given(reviewRoundRepository
                .findAllForUpdateByContestIdOrderByRoundNoAsc(CONTEST_ID))
                .willReturn(List.of(round));
        given(reviewCriterionRepository
                .findAllForUpdateByReviewRoundIdInOrderBySortOrderAsc(
                        List.of(ROUND_ID)))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForShareByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of(entry(round, 101L)));

        assertThatThrownBy(() -> service.updateRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID,
                request()
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_CONFIGURATION_LOCKED);
    }

    @Test
    @DisplayName("라운드를 열면 모든 ELIGIBLE 심사 대상을 IN_REVIEW로 전환한다")
    void openRound_opensRoundAndStartsEntries() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReviewCriterion criterion = criterion(round);
        ReviewRoundEntry first = entry(round, 101L);
        ReviewRoundEntry second = entry(round, 102L);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID))
                .willReturn(List.of(criterion));
        given(reviewRoundEntryRepository
                .findAllForUpdateByReviewRoundIdOrderByIdAsc(ROUND_ID))
                .willReturn(List.of(first, second));

        ReviewRoundRes response = service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        );

        assertThat(response.status()).isEqualTo(ReviewRoundStatus.OPEN);
        assertThat(first.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.IN_REVIEW);
        assertThat(second.getStatus())
                .isEqualTo(ReviewRoundEntryStatus.IN_REVIEW);
        verify(reviewRoundRepository).flush();
        verify(reviewRoundEntryRepository).flush();
        verify(adminAuditLogger).log(
                ADMIN_ID,
                ORGANIZATION_ID,
                AuditAction.REVIEW_ROUND_OPEN,
                "REVIEW_ROUND",
                ROUND_ID,
                "contestId=30, entryCount=2"
        );
    }

    @Test
    @DisplayName("활성 평가 기준 없이 심사 라운드를 열 수 없다")
    void openRound_rejectsMissingCriteria() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));
        given(reviewCriterionRepository
                .findAllForShareByReviewRoundIdOrderBySortOrderAsc(ROUND_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_CRITERION_REQUIRED);

        verifyNoInteractions(reviewRoundEntryRepository);
    }

    @Test
    @DisplayName("FINALIZED 라운드는 오픈 API로 다시 전환할 수 없다")
    void openRound_rejectsFinalizedRound() {
        ReviewRound round = round(ReviewRoundStatus.FINALIZED);
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_STATUS_TRANSITION_INVALID);

        verifyNoInteractions(
                reviewCriterionRepository,
                reviewRoundEntryRepository);
    }

    @Test
    @DisplayName("종료 시각이 지난 심사 라운드는 열 수 없다")
    void openRound_rejectsExpiredRound() {
        ReviewRound round = round(ReviewRoundStatus.PREPARING);
        ReflectionTestUtils.setField(
                round,
                "startsAt",
                NOW.minusHours(2)
        );
        ReflectionTestUtils.setField(
                round,
                "endsAt",
                NOW.minusMinutes(1)
        );
        stubRoundOrganization();
        given(reviewRoundRepository.findByIdForUpdate(ROUND_ID))
                .willReturn(Optional.of(round));

        assertThatThrownBy(() -> service.openRound(
                ADMIN_ID,
                CONTEST_PUBLIC_ID,
                ROUND_ID
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception ->
                        ((CustomException) exception)
                                .getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode
                        .REVIEW_ROUND_OPEN_WINDOW_EXPIRED);

        verifyNoInteractions(
                reviewCriterionRepository,
                reviewRoundEntryRepository);
    }

    private void stubRoundOrganization() {
        given(reviewRoundRepository.findOrganizationIdById(ROUND_ID))
                .willReturn(Optional.of(ORGANIZATION_ID));
    }

    private ReviewRoundSaveReq request() {
        return new ReviewRoundSaveReq(
                1,
                "예선 심사",
                STARTS_AT,
                ENDS_AT,
                ReviewRoundTargetType.ALL_SUBMISSIONS,
                ReviewRoundDecisionRule.TOP_N,
                10,
                null,
                List.of(new ReviewRoundCriterionReq(
                        null,
                        "creativity",
                        "창의성",
                        50,
                        1
                ))
        );
    }

    private ReviewRound round(ReviewRoundStatus status) {
        ReviewRound round = ReviewRound.builder()
                .contest(contest)
                .roundNo(1)
                .name("예선 심사")
                .status(status)
                .startsAt(STARTS_AT)
                .endsAt(ENDS_AT)
                .targetType(ReviewRoundTargetType.ALL_SUBMISSIONS)
                .decisionRule(ReviewRoundDecisionRule.TOP_N)
                .selectCount(10)
                .build();
        ReflectionTestUtils.setField(round, "id", ROUND_ID);
        return round;
    }

    private ReviewCriterion criterion(ReviewRound round) {
        ReviewCriterion criterion = ReviewCriterion.builder()
                .reviewRound(round)
                .code("creativity")
                .label("창의성")
                .maxScore(50)
                .sortOrder(1)
                .active(true)
                .build();
        ReflectionTestUtils.setField(criterion, "id", 50L);
        return criterion;
    }

    private ReviewRoundEntry entry(ReviewRound round, Long id) {
        ReviewRoundEntry entry = ReviewRoundEntry.builder()
                .reviewRound(round)
                .status(ReviewRoundEntryStatus.ELIGIBLE)
                .build();
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }
}
