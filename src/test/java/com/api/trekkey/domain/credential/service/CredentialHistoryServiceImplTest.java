package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.repository.CredentialHistoryRow;
import com.api.trekkey.domain.credential.web.dto.CredentialHistoryRes;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.User;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialHistoryServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AncCredentialSubjectRepository credentialSubjectRepository;

    private CredentialHistoryServiceImpl credentialHistoryService;

    @BeforeEach
    void setUp() {
        credentialHistoryService = new CredentialHistoryServiceImpl(
                userRepository, credentialSubjectRepository);
    }

    @Test
    @DisplayName("내 이력은 subject snapshot 기준 projection으로 조회된다")
    void getMyCredentials_returnsSnapshotBasedHistory() {
        CredentialHistoryRow award = rowFixture(2L, "2026-C1-001", "AWARD", "AWARDEE", 1L);
        CredentialHistoryRow participation = rowFixture(1L, "2026-P1-T1", "PARTICIPATION", "PARTICIPANT", 1L);
        given(credentialSubjectRepository.findHistoryRowsByUserId(10L))
                .willReturn(List.of(award, participation));

        List<CredentialHistoryRes> result = credentialHistoryService.getMyCredentials(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).credentialNo()).isEqualTo("2026-C1-001");
        assertThat(result.get(0).credentialType()).isEqualTo(CredentialType.AWARD);
        assertThat(result.get(0).status()).isEqualTo(CredentialStatus.READY);
        assertThat(result.get(0).roleCode()).isEqualTo("AWARDEE");
        assertThat(result.get(0).contestTitle()).isEqualTo("2026 캡스톤 경진대회");
    }

    @Test
    @DisplayName("끊어진 credential 참조는 결과에서 제외한다 (경고 로그 대상)")
    void getMyCredentials_skipsDanglingReference() {
        CredentialHistoryRow dangling = mock(CredentialHistoryRow.class);
        lenient().when(dangling.getCredentialId()).thenReturn(null);
        lenient().when(dangling.getSubjectCredentialId()).thenReturn(99L);
        CredentialHistoryRow valid = rowFixture(1L, "2026-P1-T1", "PARTICIPATION", "PARTICIPANT", 1L);
        given(credentialSubjectRepository.findHistoryRowsByUserId(10L))
                .willReturn(List.of(dangling, valid));

        List<CredentialHistoryRes> result = credentialHistoryService.getMyCredentials(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).credentialNo()).isEqualTo("2026-P1-T1");
    }

    @Test
    @DisplayName("관리자 학번 조회는 자기 학교가 발급한 Credential만 노출한다")
    void getStudentCredentials_filtersByIssuerOrganization() {
        givenAdminInOrganization(1L);
        User student = mock(User.class);
        lenient().when(student.getId()).thenReturn(10L);
        given(userRepository.findByOrganizationIdAndStudentId(1L, "2171193"))
                .willReturn(Optional.of(student));

        //credential 1은 우리 학교(org 1), credential 2는 타 학교(org 9) 발급분
        CredentialHistoryRow ours = rowFixture(1L, "2026-P1-T1", "PARTICIPATION", "PARTICIPANT", 1L);
        CredentialHistoryRow theirs = rowFixture(2L, "2026-C9-001", "AWARD", "AWARDEE", 9L);
        given(credentialSubjectRepository.findHistoryRowsByUserId(10L))
                .willReturn(List.of(ours, theirs));

        List<CredentialHistoryRes> result = credentialHistoryService.getStudentCredentials(100L, "2171193");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).credentialNo()).isEqualTo("2026-P1-T1");
    }

    @Test
    @DisplayName("자기 학교에 없는 학번은 404로 비노출한다")
    void getStudentCredentials_throwsWhenStudentNotInOrganization() {
        givenAdminInOrganization(1L);
        given(userRepository.findByOrganizationIdAndStudentId(1L, "9999999"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> credentialHistoryService.getStudentCredentials(100L, "9999999"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getBaseResponseCode())
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND);
    }

    //======= 헬퍼 메서드 ==========

    private void givenAdminInOrganization(Long organizationId) {
        Organization organization = mock(Organization.class);
        lenient().when(organization.getId()).thenReturn(organizationId);
        User admin = mock(User.class);
        lenient().when(admin.getOrganization()).thenReturn(organization);
        lenient().when(userRepository.findById(100L)).thenReturn(Optional.of(admin));
    }

    private CredentialHistoryRow rowFixture(
            Long credentialId, String credentialNo, String type, String roleCode, Long issuerOrganizationId) {
        CredentialHistoryRow row = mock(CredentialHistoryRow.class);
        lenient().when(row.getCredentialId()).thenReturn(credentialId);
        lenient().when(row.getSubjectCredentialId()).thenReturn(credentialId);
        lenient().when(row.getIssuerOrganizationId()).thenReturn(issuerOrganizationId);
        lenient().when(row.getCredentialPublicId()).thenReturn("cred-pub-" + credentialId);
        lenient().when(row.getCredentialNo()).thenReturn(credentialNo);
        lenient().when(row.getCredentialType()).thenReturn(type);
        lenient().when(row.getStatus()).thenReturn("READY");
        lenient().when(row.getRoleCode()).thenReturn(roleCode);
        lenient().when(row.getDisplayName()).thenReturn("이준수");
        lenient().when(row.getContestTitle()).thenReturn("2026 캡스톤 경진대회");
        lenient().when(row.getIssuedAt()).thenReturn(LocalDateTime.of(2026, 7, 27, 12, 0));
        return row;
    }
}
