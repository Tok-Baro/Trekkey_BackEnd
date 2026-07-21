package com.api.trekkey.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.trekkey.domain.audit.entity.AuditAction;
import com.api.trekkey.domain.audit.support.AdminAuditLogger;
import com.api.trekkey.domain.invitation.entity.AdminInvitation;
import com.api.trekkey.domain.invitation.entity.InvitationStatus;
import com.api.trekkey.domain.invitation.exception.AdminInvitationErrorResponseCode;
import com.api.trekkey.domain.invitation.repository.AdminInvitationRepository;
import com.api.trekkey.domain.invitation.web.dto.request.AdminApprovalReq;
import com.api.trekkey.domain.invitation.web.dto.request.AdminInvitationCreateReq;
import com.api.trekkey.domain.invitation.web.dto.response.AdminInvitationRes;
import com.api.trekkey.domain.invitation.web.dto.response.AdminPendingRes;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminInvitationServiceImplTest {

    private static final String FRONT_BASE_URL = "https://trekkey.example.com";
    private static final Long ROOT_USER_ID = 1L;
    private static final Long ORGANIZATION_ID = 10L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminInvitationRepository adminInvitationRepository;

    @Mock
    private AdminAuditLogger adminAuditLogger;

    private AdminInvitationServiceImpl adminInvitationService;

    private Organization organization;
    private User rootUser;

    @BeforeEach
    void setUp() {
        adminInvitationService = new AdminInvitationServiceImpl(
                userRepository,
                adminInvitationRepository,
                adminAuditLogger
        );
        ReflectionTestUtils.setField(adminInvitationService, "frontBaseUrl", FRONT_BASE_URL);

        organization = mock(Organization.class);
        rootUser = User.builder()
                .id(ROOT_USER_ID)
                .organization(organization)
                .name("루트관리자")
                .email("root@test.com")
                .password("encoded-password")
                .role(UserRole.ROOT_ADMIN)
                .memberType(MemberType.STAFF)
                .status(UserStatus.ACTIVE)
                .build();
    }

    //======= createInvitation ==========

    @Test
    @DisplayName("초대 발급 시 토큰 원문은 URL에만 담고 DB에는 SHA-256 해시만 7일 만료로 저장한다")
    void createInvitation_storesHashedTokenAndReturnsInviteUrl() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        given(adminInvitationRepository.findByOrganizationIdOrderByCreatedAtDesc(ORGANIZATION_ID))
                .willReturn(List.of());
        willAnswer(invocation -> {
            AdminInvitation toSave = invocation.getArgument(0);
            ReflectionTestUtils.setField(toSave, "id", 100L);
            return toSave;
        }).given(adminInvitationRepository).save(any(AdminInvitation.class));
        LocalDateTime before = LocalDateTime.now();

        AdminInvitationRes response =
                adminInvitationService.createInvitation(ROOT_USER_ID, new AdminInvitationCreateReq("staff@test.com"));

        LocalDateTime after = LocalDateTime.now();
        String expectedPrefix = FRONT_BASE_URL + "/signup/admin?token=";
        assertThat(response.inviteUrl()).startsWith(expectedPrefix);
        String rawToken = response.inviteUrl().substring(expectedPrefix.length());
        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<AdminInvitation> invitationCaptor = ArgumentCaptor.forClass(AdminInvitation.class);
        verify(adminInvitationRepository).save(invitationCaptor.capture());
        AdminInvitation saved = invitationCaptor.getValue();
        assertThat(saved.getTokenHash())
                .isNotEqualTo(rawToken)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(sha256(rawToken));
        assertThat(saved.getEmail()).isEqualTo("staff@test.com");
        assertThat(saved.getStatus()).isEqualTo(InvitationStatus.ISSUED);
        assertThat(saved.getInvitedBy()).isSameAs(rootUser);
        assertThat(saved.getExpiresAt()).isBetween(before.plusDays(7), after.plusDays(7));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.email()).isEqualTo("staff@test.com");
        verify(adminAuditLogger).log(
                eq(ROOT_USER_ID),
                eq(ORGANIZATION_ID),
                eq(AuditAction.INVITATION_ISSUE),
                eq("INVITATION"),
                eq(100L),
                eq("staff@test.com"));
    }

    @Test
    @DisplayName("같은 이메일의 사용 가능한 초대가 이미 있으면 발급하지 않고 INVITATION_DUPLICATED를 던진다")
    void createInvitation_throwsWhenUsableInvitationAlreadyExists() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        AdminInvitation usable = invitation(
                50L, "staff@test.com", InvitationStatus.ISSUED, LocalDateTime.now().plusDays(3));
        given(adminInvitationRepository.findByOrganizationIdOrderByCreatedAtDesc(ORGANIZATION_ID))
                .willReturn(List.of(usable));

        assertThatThrownBy(() ->
                adminInvitationService.createInvitation(ROOT_USER_ID, new AdminInvitationCreateReq("staff@test.com")))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(AdminInvitationErrorResponseCode.INVITATION_DUPLICATED);

        verify(adminInvitationRepository, never()).save(any());
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("같은 이메일이라도 만료된 ISSUED 초대만 있으면 발급에 성공하고 그 초대는 EXPIRED로 전이된다")
    void createInvitation_succeedsAndExpiresOutdatedInvitationOfSameEmail() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        AdminInvitation outdated = invitation(
                50L, "staff@test.com", InvitationStatus.ISSUED, LocalDateTime.now().minusDays(1));
        given(adminInvitationRepository.findByOrganizationIdOrderByCreatedAtDesc(ORGANIZATION_ID))
                .willReturn(List.of(outdated));
        willAnswer(invocation -> {
            AdminInvitation toSave = invocation.getArgument(0);
            ReflectionTestUtils.setField(toSave, "id", 100L);
            return toSave;
        }).given(adminInvitationRepository).save(any(AdminInvitation.class));

        AdminInvitationRes response =
                adminInvitationService.createInvitation(ROOT_USER_ID, new AdminInvitationCreateReq("staff@test.com"));

        assertThat(outdated.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(response.inviteUrl()).startsWith(FRONT_BASE_URL + "/signup/admin?token=");
        verify(adminInvitationRepository).save(any(AdminInvitation.class));
    }

    //======= getInvitations ==========

    @Test
    @DisplayName("초대 목록 조회 시 ISSUED인데 만료된 초대는 EXPIRED로 전이해서 반환하고 inviteUrl은 포함하지 않는다")
    void getInvitations_lazyExpiresOutdatedInvitations() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        AdminInvitation outdated = invitation(
                50L, "old@test.com", InvitationStatus.ISSUED, LocalDateTime.now().minusHours(1));
        AdminInvitation usable = invitation(
                51L, "new@test.com", InvitationStatus.ISSUED, LocalDateTime.now().plusDays(3));
        given(adminInvitationRepository.findByOrganizationIdOrderByCreatedAtDesc(ORGANIZATION_ID))
                .willReturn(List.of(outdated, usable));

        List<AdminInvitationRes> responses = adminInvitationService.getInvitations(ROOT_USER_ID);

        assertThat(outdated.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(usable.getStatus()).isEqualTo(InvitationStatus.ISSUED);
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(50L);
        assertThat(responses.get(0).status()).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(responses.get(1).id()).isEqualTo(51L);
        assertThat(responses.get(1).status()).isEqualTo(InvitationStatus.ISSUED);
        assertThat(responses).allSatisfy(res -> assertThat(res.inviteUrl()).isNull());
    }

    //======= revokeInvitation ==========

    @Test
    @DisplayName("ISSUED 초대를 철회하면 REVOKED로 전이하고 감사 로그를 남긴다")
    void revokeInvitation_revokesIssuedInvitation() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        AdminInvitation issued = invitation(
                50L, "staff@test.com", InvitationStatus.ISSUED, LocalDateTime.now().plusDays(3));
        given(adminInvitationRepository.findById(50L)).willReturn(Optional.of(issued));

        adminInvitationService.revokeInvitation(ROOT_USER_ID, 50L);

        assertThat(issued.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        verify(adminAuditLogger).log(
                eq(ROOT_USER_ID),
                eq(ORGANIZATION_ID),
                eq(AuditAction.INVITATION_REVOKE),
                eq("INVITATION"),
                eq(50L),
                eq("staff@test.com"));
    }

    @Test
    @DisplayName("다른 조직의 초대는 철회할 수 없고 INVITATION_INVALID를 던진다")
    void revokeInvitation_throwsWhenInvitationBelongsToOtherOrganization() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        Organization otherOrganization = mock(Organization.class);
        given(otherOrganization.getId()).willReturn(99L);
        AdminInvitation otherOrgInvitation = invitationOf(
                otherOrganization, 50L, "staff@test.com", InvitationStatus.ISSUED, LocalDateTime.now().plusDays(3));
        given(adminInvitationRepository.findById(50L)).willReturn(Optional.of(otherOrgInvitation));

        assertThatThrownBy(() -> adminInvitationService.revokeInvitation(ROOT_USER_ID, 50L))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(AdminInvitationErrorResponseCode.INVITATION_INVALID);

        assertThat(otherOrgInvitation.getStatus()).isEqualTo(InvitationStatus.ISSUED);
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("이미 사용된 초대를 철회하면 INVITATION_ALREADY_USED를 던진다")
    void revokeInvitation_throwsWhenInvitationAlreadyUsed() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        AdminInvitation used = invitation(
                50L, "staff@test.com", InvitationStatus.USED, LocalDateTime.now().plusDays(3));
        given(adminInvitationRepository.findById(50L)).willReturn(Optional.of(used));

        assertThatThrownBy(() -> adminInvitationService.revokeInvitation(ROOT_USER_ID, 50L))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(AdminInvitationErrorResponseCode.INVITATION_ALREADY_USED);

        assertThat(used.getStatus()).isEqualTo(InvitationStatus.USED);
        verifyNoInteractions(adminAuditLogger);
    }

    //======= getPendingAdmins ==========

    @Test
    @DisplayName("승인 대기 7일이 지난 관리자는 자동 거절하고 목록에서 제외하며, 7일 이내 대기자만 반환한다")
    void getPendingAdmins_rejectsOverdueAndReturnsRecentOnly() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        User overdue = pendingAdmin(2L, "overdue@test.com", LocalDateTime.now().minusDays(8));
        User recent = pendingAdmin(3L, "recent@test.com", LocalDateTime.now().minusDays(2));
        given(userRepository.findByOrganizationIdAndRoleAndStatus(
                ORGANIZATION_ID, UserRole.ADMIN, UserStatus.PENDING_APPROVAL))
                .willReturn(List.of(overdue, recent));

        List<AdminPendingRes> responses = adminInvitationService.getPendingAdmins(ROOT_USER_ID);

        assertThat(overdue.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(adminAuditLogger).log(
                eq(ROOT_USER_ID),
                eq(ORGANIZATION_ID),
                eq(AuditAction.ADMIN_REJECT),
                eq("USER"),
                eq(2L),
                anyString());

        assertThat(recent.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userId()).isEqualTo(3L);
        assertThat(responses.get(0).email()).isEqualTo("recent@test.com");
    }

    //======= decideApproval ==========

    @Test
    @DisplayName("승인하면 대상 관리자가 ACTIVE로 전이되고 ADMIN_APPROVE 감사 로그를 남긴다")
    void decideApproval_approvesPendingAdmin() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        User target = pendingAdmin(2L, "staff@test.com", LocalDateTime.now().minusDays(1));
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        AdminPendingRes response =
                adminInvitationService.decideApproval(ROOT_USER_ID, 2L, new AdminApprovalReq(true));

        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.userId()).isEqualTo(2L);
        verify(adminAuditLogger).log(
                eq(ROOT_USER_ID),
                eq(ORGANIZATION_ID),
                eq(AuditAction.ADMIN_APPROVE),
                eq("USER"),
                eq(2L),
                anyString());
    }

    @Test
    @DisplayName("거절하면 대상 관리자가 INACTIVE로 전이되고 ADMIN_REJECT 감사 로그를 남긴다")
    void decideApproval_rejectsPendingAdmin() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        User target = pendingAdmin(2L, "staff@test.com", LocalDateTime.now().minusDays(1));
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        adminInvitationService.decideApproval(ROOT_USER_ID, 2L, new AdminApprovalReq(false));

        assertThat(target.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(adminAuditLogger).log(
                eq(ROOT_USER_ID),
                eq(ORGANIZATION_ID),
                eq(AuditAction.ADMIN_REJECT),
                eq("USER"),
                eq(2L),
                anyString());
    }

    @Test
    @DisplayName("PENDING_APPROVAL 상태가 아닌 대상은 APPROVAL_TARGET_INVALID를 던진다")
    void decideApproval_throwsWhenTargetIsNotPending() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        User activeAdmin = admin(2L, "staff@test.com", organization, UserStatus.ACTIVE);
        given(userRepository.findById(2L)).willReturn(Optional.of(activeAdmin));

        assertThatThrownBy(() ->
                adminInvitationService.decideApproval(ROOT_USER_ID, 2L, new AdminApprovalReq(true)))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(AdminInvitationErrorResponseCode.APPROVAL_TARGET_INVALID);

        assertThat(activeAdmin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verifyNoInteractions(adminAuditLogger);
    }

    @Test
    @DisplayName("다른 조직의 대기 관리자는 승인할 수 없고 APPROVAL_TARGET_INVALID를 던진다")
    void decideApproval_throwsWhenTargetBelongsToOtherOrganization() {
        given(organization.getId()).willReturn(ORGANIZATION_ID);
        given(userRepository.findById(ROOT_USER_ID)).willReturn(Optional.of(rootUser));
        Organization otherOrganization = mock(Organization.class);
        given(otherOrganization.getId()).willReturn(99L);
        User otherOrgTarget = admin(2L, "staff@test.com", otherOrganization, UserStatus.PENDING_APPROVAL);
        given(userRepository.findById(2L)).willReturn(Optional.of(otherOrgTarget));

        assertThatThrownBy(() ->
                adminInvitationService.decideApproval(ROOT_USER_ID, 2L, new AdminApprovalReq(true)))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(AdminInvitationErrorResponseCode.APPROVAL_TARGET_INVALID);

        assertThat(otherOrgTarget.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        verifyNoInteractions(adminAuditLogger);
    }

    //======= 헬퍼 메서드 ==========

    private AdminInvitation invitation(Long id, String email, InvitationStatus status, LocalDateTime expiresAt) {
        return invitationOf(organization, id, email, status, expiresAt);
    }

    private AdminInvitation invitationOf(
            Organization invitationOrganization,
            Long id,
            String email,
            InvitationStatus status,
            LocalDateTime expiresAt) {
        AdminInvitation invitation = AdminInvitation.builder()
                .id(id)
                .organization(invitationOrganization)
                .email(email)
                .tokenHash(sha256("raw-token-" + id))
                .status(status)
                .invitedBy(rootUser)
                .expiresAt(expiresAt)
                .build();
        ReflectionTestUtils.setField(invitation, "createdAt", LocalDateTime.now().minusDays(1));
        return invitation;
    }

    private User pendingAdmin(Long id, String email, LocalDateTime createdAt) {
        User user = admin(id, email, organization, UserStatus.PENDING_APPROVAL);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        return user;
    }

    private User admin(Long id, String email, Organization adminOrganization, UserStatus status) {
        return User.builder()
                .id(id)
                .organization(adminOrganization)
                .name("김교직")
                .email(email)
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .memberType(MemberType.STAFF)
                .status(status)
                .department("교무처")
                .position("주임")
                .build();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
