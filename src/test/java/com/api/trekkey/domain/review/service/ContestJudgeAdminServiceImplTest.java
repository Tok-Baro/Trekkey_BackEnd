package com.api.trekkey.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.entity.ContestStatus;
import com.api.trekkey.domain.contest.entity.ParticipationType;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewLinkStatus;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ContestJudgeRepository;
import com.api.trekkey.domain.review.support.ReviewLinkTokenManager;
import com.api.trekkey.domain.review.web.dto.request.ContestJudgeCreateReq;
import com.api.trekkey.domain.review.web.dto.request.ReviewLinkIssueReq;
import com.api.trekkey.domain.review.web.dto.response.ContestJudgeRes;
import com.api.trekkey.domain.review.web.dto.response.ReviewLinkIssueRes;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContestJudgeAdminServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestJudgeRepository contestJudgeRepository;

    @Mock
    private ReviewLinkTokenManager reviewLinkTokenManager;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private ContestJudgeAdminServiceImpl service;

    private Organization organization;
    private User admin;
    private Contest contest;

    @BeforeEach
    void setUp() {
        service = new ContestJudgeAdminServiceImpl(
                userRepository,
                contestRepository,
                contestJudgeRepository,
                reviewLinkTokenManager,
                adminAuditLogger
        );
        ReflectionTestUtils.setField(service, "frontBaseUrl", "https://trekkey.example.com/");

        organization = org.mockito.Mockito.mock(Organization.class);
        org.mockito.Mockito.lenient().when(organization.getId()).thenReturn(1L);
        admin = user(10L, organization, "관리자", "admin@test.com", UserRole.ADMIN);
        contest = contest(100L, organization);
    }

    @Test
    @DisplayName("외부 심사위원을 등록하면 링크 미발급 상태로 저장한다")
    void createJudge_savesExternalJudgeWithoutLink() {
        stubAdminAndContest();
        given(contestJudgeRepository.saveAndFlush(any(ContestJudge.class)))
                .willAnswer(invocation -> {
                    ContestJudge judge = invocation.getArgument(0);
                    ReflectionTestUtils.setField(judge, "id", 200L);
                    return judge;
                });

        ContestJudgeRes response = service.createJudge(
                10L,
                "contest-public-id",
                new ContestJudgeCreateReq(null, "  김심사  ", "  외부 전문가  ")
        );

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.userId()).isNull();
        assertThat(response.name()).isEqualTo("김심사");
        assertThat(response.roleLabel()).isEqualTo("외부 전문가");
        assertThat(response.reviewLinkStatus()).isEqualTo(ReviewLinkStatus.NOT_ISSUED);

        ArgumentCaptor<ContestJudge> captor = ArgumentCaptor.forClass(ContestJudge.class);
        verify(contestJudgeRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReviewTokenHash()).isNull();
        verify(adminAuditLogger).log(
                10L,
                1L,
                AuditAction.CONTEST_JUDGE_CREATE,
                "CONTEST_JUDGE",
                200L,
                "contestId=100, name=김심사"
        );
    }

    @Test
    @DisplayName("같은 기관의 활성 사용자를 심사위원으로 연결할 수 있다")
    void createJudge_linksActiveUserFromSameOrganization() {
        User linkedUser =
                user(20L, organization, "이교수", "judge@test.com", UserRole.PARTICIPANT);
        stubAdminAndContest();
        given(userRepository.findById(20L)).willReturn(Optional.of(linkedUser));
        given(contestJudgeRepository.existsByContestIdAndUserId(100L, 20L)).willReturn(false);
        given(contestJudgeRepository.saveAndFlush(any(ContestJudge.class)))
                .willAnswer(invocation -> {
                    ContestJudge judge = invocation.getArgument(0);
                    ReflectionTestUtils.setField(judge, "id", 200L);
                    return judge;
                });

        ContestJudgeRes response = service.createJudge(
                10L,
                "contest-public-id",
                new ContestJudgeCreateReq(20L, "이교수", "교내 심사위원")
        );

        assertThat(response.userId()).isEqualTo(20L);
        verify(contestJudgeRepository).existsByContestIdAndUserId(100L, 20L);
    }

    @Test
    @DisplayName("다른 기관 사용자는 심사위원으로 연결할 수 없다")
    void createJudge_rejectsUserFromOtherOrganization() {
        Organization otherOrganization = org.mockito.Mockito.mock(Organization.class);
        given(otherOrganization.getId()).willReturn(2L);
        User linkedUser =
                user(20L, otherOrganization, "타교수", "other@test.com", UserRole.PARTICIPANT);
        stubAdminAndContest();
        given(userRepository.findById(20L)).willReturn(Optional.of(linkedUser));

        assertThatThrownBy(() -> service.createJudge(
                10L,
                "contest-public-id",
                new ContestJudgeCreateReq(20L, "타교수", "외부 심사위원")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.CONTEST_JUDGE_USER_INVALID);

        verify(contestJudgeRepository, never()).saveAndFlush(any(ContestJudge.class));
    }

    @Test
    @DisplayName("같은 대회에 연결된 사용자를 중복 등록할 수 없다")
    void createJudge_rejectsDuplicatedLinkedUser() {
        User linkedUser =
                user(20L, organization, "이교수", "judge@test.com", UserRole.PARTICIPANT);
        stubAdminAndContest();
        given(userRepository.findById(20L)).willReturn(Optional.of(linkedUser));
        given(contestJudgeRepository.existsByContestIdAndUserId(100L, 20L)).willReturn(true);

        assertThatThrownBy(() -> service.createJudge(
                10L,
                "contest-public-id",
                new ContestJudgeCreateReq(20L, "이교수", "교내 심사위원")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.CONTEST_JUDGE_DUPLICATED);
    }

    @Test
    @DisplayName("DB 유니크 제약 충돌도 심사위원 중복 오류로 변환한다")
    void createJudge_mapsDatabaseDuplicateToDomainError() {
        User linkedUser =
                user(20L, organization, "이교수", "judge@test.com", UserRole.PARTICIPANT);
        stubAdminAndContest();
        given(userRepository.findById(20L)).willReturn(Optional.of(linkedUser));
        given(contestJudgeRepository.existsByContestIdAndUserId(100L, 20L)).willReturn(false);
        given(contestJudgeRepository.saveAndFlush(any(ContestJudge.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.createJudge(
                10L,
                "contest-public-id",
                new ContestJudgeCreateReq(20L, "이교수", "교내 심사위원")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.CONTEST_JUDGE_DUPLICATED);
    }

    @Test
    @DisplayName("심사위원 목록에는 링크 원문 없이 현재 링크 상태만 반환한다")
    void getJudges_returnsLinkStatusWithoutRawToken() {
        ContestJudge activeJudge = judge(200L, null, "김심사");
        activeJudge.issueReviewLink(
                "a".repeat(64),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1)
        );
        stubAdminAndContest();
        given(contestJudgeRepository.findAllByContestIdOrderByCreatedAtAscIdAsc(100L))
                .willReturn(List.of(activeJudge));

        List<ContestJudgeRes> response =
                service.getJudges(10L, "contest-public-id");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().reviewLinkStatus()).isEqualTo(ReviewLinkStatus.ACTIVE);
        assertThat(response.getFirst().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("reviewToken", "reviewTokenHash", "reviewUrl");
    }

    @Test
    @DisplayName("심사 링크 발급 시 원문은 URL에만 반환하고 해시만 저장한다")
    void issueReviewLink_returnsRawTokenOnlyInFragmentUrl() {
        ContestJudge judge = judge(200L, null, "김심사");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(3);
        stubAdminAndContest();
        given(contestJudgeRepository.findByIdAndContestIdForUpdate(200L, 100L))
                .willReturn(Optional.of(judge));
        given(reviewLinkTokenManager.generateToken()).willReturn("raw_review-token");
        given(reviewLinkTokenManager.hash("raw_review-token")).willReturn("b".repeat(64));

        ReviewLinkIssueRes response = service.issueReviewLink(
                10L,
                "contest-public-id",
                200L,
                new ReviewLinkIssueReq(expiresAt)
        );

        assertThat(response.reviewUrl())
                .isEqualTo("https://trekkey.example.com/judge/review#token=raw_review-token");
        assertThat(judge.getReviewTokenHash()).isEqualTo("b".repeat(64));
        assertThat(judge.getReviewTokenHash()).doesNotContain("raw_review-token");
        assertThat(judge.getReviewLinkStatus(LocalDateTime.now()))
                .isEqualTo(ReviewLinkStatus.ACTIVE);
        verify(contestJudgeRepository).flush();
        verify(adminAuditLogger).log(
                10L,
                1L,
                AuditAction.REVIEW_LINK_ISSUE,
                "CONTEST_JUDGE",
                200L,
                "expiresAt=" + expiresAt
        );
    }

    @Test
    @DisplayName("심사 링크를 재발급하면 이전 토큰 해시와 철회 상태를 교체한다")
    void issueReviewLink_replacesPreviousToken() {
        ContestJudge judge = judge(200L, null, "김심사");
        judge.issueReviewLink(
                "old-hash",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        judge.revokeReviewLink(LocalDateTime.now().minusHours(1));
        stubAdminAndContest();
        given(contestJudgeRepository.findByIdAndContestIdForUpdate(200L, 100L))
                .willReturn(Optional.of(judge));
        given(reviewLinkTokenManager.generateToken()).willReturn("new-token");
        given(reviewLinkTokenManager.hash("new-token")).willReturn("new-hash");

        service.issueReviewLink(
                10L,
                "contest-public-id",
                200L,
                new ReviewLinkIssueReq(LocalDateTime.now().plusDays(2))
        );

        assertThat(judge.getReviewTokenHash()).isEqualTo("new-hash");
        assertThat(judge.getTokenRevokedAt()).isNull();
    }

    @Test
    @DisplayName("현재보다 이전인 링크 만료 시각은 저장 전에 거부한다")
    void issueReviewLink_rejectsPastExpiration() {
        stubAdminAndContest();

        assertThatThrownBy(() -> service.issueReviewLink(
                10L,
                "contest-public-id",
                200L,
                new ReviewLinkIssueReq(LocalDateTime.now().minusMinutes(1))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.REVIEW_LINK_EXPIRATION_INVALID);

        verify(contestJudgeRepository, never())
                .findByIdAndContestIdForUpdate(anyLong(), anyLong());
        verify(reviewLinkTokenManager, never()).generateToken();
    }

    @Test
    @DisplayName("심사 링크 폐기는 멱등하며 원본 해시는 보존하고 폐기 시각을 기록한다")
    void revokeReviewLink_isIdempotent() {
        ContestJudge judge = judge(200L, null, "김심사");
        judge.issueReviewLink(
                "stored-hash",
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1)
        );
        stubAdminAndContest();
        given(contestJudgeRepository.findByIdAndContestIdForUpdate(200L, 100L))
                .willReturn(Optional.of(judge));

        service.revokeReviewLink(10L, "contest-public-id", 200L);
        service.revokeReviewLink(10L, "contest-public-id", 200L);

        assertThat(judge.getReviewTokenHash()).isEqualTo("stored-hash");
        assertThat(judge.getReviewLinkStatus(LocalDateTime.now()))
                .isEqualTo(ReviewLinkStatus.REVOKED);
        verify(adminAuditLogger, times(1)).log(
                10L,
                1L,
                AuditAction.REVIEW_LINK_REVOKE,
                "CONTEST_JUDGE",
                200L,
                "contestId=100"
        );
    }

    @Test
    @DisplayName("다른 대회의 심사위원 ID로는 링크를 발급할 수 없다")
    void issueReviewLink_rejectsJudgeOutsideContest() {
        stubAdminAndContest();
        given(contestJudgeRepository.findByIdAndContestIdForUpdate(200L, 100L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueReviewLink(
                10L,
                "contest-public-id",
                200L,
                new ReviewLinkIssueReq(LocalDateTime.now().plusDays(1))
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ReviewErrorResponseCode.CONTEST_JUDGE_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 기관 관리자는 대회의 심사위원을 관리할 수 없다")
    void getJudges_rejectsOtherOrganization() {
        Organization otherOrganization = org.mockito.Mockito.mock(Organization.class);
        given(otherOrganization.getId()).willReturn(2L);
        Contest otherContest = contest(100L, otherOrganization);
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("contest-public-id"))
                .willReturn(Optional.of(otherContest));

        assertThatThrownBy(() -> service.getJudges(10L, "contest-public-id"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(ContestErrorResponseCode.CONTEST_FORBIDDEN);
    }

    @Test
    @DisplayName("관리자를 찾을 수 없으면 심사위원 등록을 진행하지 않는다")
    void createJudge_rejectsMissingAdmin() {
        given(userRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createJudge(
                10L,
                "contest-public-id",
                new ContestJudgeCreateReq(null, "김심사", "외부 전문가")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);
    }

    private void stubAdminAndContest() {
        given(userRepository.findById(10L)).willReturn(Optional.of(admin));
        given(contestRepository.findByPublicId("contest-public-id"))
                .willReturn(Optional.of(contest));
    }

    private User user(
            Long id,
            Organization userOrganization,
            String name,
            String email,
            UserRole role
    ) {
        User user = User.builder()
                .organization(userOrganization)
                .name(name)
                .email(email)
                .password("encoded")
                .role(role)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Contest contest(Long id, Organization contestOrganization) {
        Contest contest = Contest.builder()
                .publicId("contest-public-id")
                .organization(contestOrganization)
                .ownerUser(admin)
                .title("AI 공모전")
                .department("교무처")
                .status(ContestStatus.PREPARING)
                .participationType(ParticipationType.BOTH)
                .awardCount(1)
                .summary("요약")
                .target("재학생")
                .applicationMethod("온라인")
                .benefits("상장")
                .detailHtml("<p>본문</p>")
                .build();
        ReflectionTestUtils.setField(contest, "id", id);
        return contest;
    }

    private ContestJudge judge(Long id, User linkedUser, String name) {
        ContestJudge judge = ContestJudge.builder()
                .contest(contest)
                .user(linkedUser)
                .name(name)
                .roleLabel("외부 전문가")
                .build();
        ReflectionTestUtils.setField(judge, "id", id);
        return judge;
    }
}
