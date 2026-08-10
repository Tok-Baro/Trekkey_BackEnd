package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.api.trekkey.domain.credential.entity.PublicActivityProfile;
import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.repository.CredentialHistoryRow;
import com.api.trekkey.domain.credential.repository.PublicActivityProfileRepository;
import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileRes;
import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileSettingsRes;
import com.api.trekkey.domain.organization.entity.Organization;
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

@ExtendWith(MockitoExtension.class)
class PublicActivityProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PublicActivityProfileRepository profileRepository;

    @Mock
    private AncCredentialSubjectRepository credentialSubjectRepository;

    @Mock
    private User user;

    @Mock
    private Organization organization;

    private PublicActivityProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PublicActivityProfileServiceImpl(
                userRepository,
                profileRepository,
                credentialSubjectRepository);
    }

    @Test
    @DisplayName("공개 프로필을 만들기 전에는 외부 조회가 비활성화되어 있다")
    void settingsAreDisabledBeforeOptIn() {
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(10L)).willReturn(Optional.empty());
        given(credentialSubjectRepository.findPublicOnChainHistoryRowsByUserId(10L))
                .willReturn(List.of(row("ANCHORED")));

        PublicActivityProfileSettingsRes result = service.getSettings(10L);

        assertThat(result.enabled()).isFalse();
        assertThat(result.publicId()).isNull();
        assertThat(result.publicCredentialCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자가 공개를 켜면 추측하기 어려운 공유 ID가 생성된다")
    void enablingCreatesShareId() {
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(10L)).willReturn(Optional.empty());
        given(profileRepository.save(any(PublicActivityProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(credentialSubjectRepository.findPublicOnChainHistoryRowsByUserId(10L))
                .willReturn(List.of());

        PublicActivityProfileSettingsRes result = service.updateSettings(10L, true);

        assertThat(result.enabled()).isTrue();
        assertThat(result.publicId()).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("공개 프로필은 학번과 이메일 없이 온체인 Credential 요약만 반환한다")
    void publicProfileReturnsSafeActivitySummary() {
        PublicActivityProfile profile = PublicActivityProfile.enabled(
                10L,
                "67a72cf5-247a-4b7e-b05e-b0e42d2f880c");
        given(profileRepository.findByPublicIdAndEnabledTrue(profile.getPublicId()))
                .willReturn(Optional.of(profile));
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(user.getId()).willReturn(10L);
        given(user.getName()).willReturn("홍길동");
        given(user.getMajor()).willReturn("컴퓨터공학부");
        given(user.getOrganization()).willReturn(organization);
        given(organization.getName()).willReturn("한성대학교");
        given(credentialSubjectRepository.findPublicOnChainHistoryRowsByUserId(10L))
                .willReturn(List.of(row("ANCHORED")));

        PublicActivityProfileRes result = service.getPublicProfile(profile.getPublicId());

        assertThat(result.displayName()).isEqualTo("홍길동");
        assertThat(result.organizationName()).isEqualTo("한성대학교");
        assertThat(result.credentialCount()).isEqualTo(1);
        assertThat(result.activities().getFirst().credentialPublicId()).isEqualTo("credential-public-id");
    }

    @Test
    @DisplayName("비활성화됐거나 변경된 공유 ID는 공개 조회할 수 없다")
    void disabledLinkIsNotFound() {
        given(profileRepository.findByPublicIdAndEnabledTrue("old-link"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicProfile("old-link"))
                .isInstanceOf(CustomException.class);
    }

    private CredentialHistoryRow row(String status) {
        return new CredentialHistoryRow() {
            @Override public Long getCredentialId() { return 1L; }
            @Override public Long getSubjectCredentialId() { return 1L; }
            @Override public Long getIssuerOrganizationId() { return 1L; }
            @Override public String getCredentialPublicId() { return "credential-public-id"; }
            @Override public String getCredentialNo() { return "2026-AWARD-001"; }
            @Override public String getCredentialType() { return "AWARD"; }
            @Override public String getStatus() { return status; }
            @Override public String getRoleCode() { return "AWARDEE"; }
            @Override public String getDisplayName() { return "홍길동"; }
            @Override public String getContestTitle() { return "Trekkey 공모전"; }
            @Override public LocalDateTime getIssuedAt() { return LocalDateTime.of(2026, 8, 10, 12, 0); }
        };
    }
}
