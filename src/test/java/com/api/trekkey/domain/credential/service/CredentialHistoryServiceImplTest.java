package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncCredentialSubject;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.web.dto.CredentialHistoryRes;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Mock
    private AncCredentialRepository credentialRepository;

    private CredentialHistoryServiceImpl credentialHistoryService;

    @BeforeEach
    void setUp() {
        credentialHistoryService = new CredentialHistoryServiceImpl(
                userRepository, credentialSubjectRepository, credentialRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("내 이력은 subject snapshot 기준으로 조회되고 발급일 내림차순으로 정렬된다")
    void getMyCredentials_returnsSnapshotBasedHistorySorted() {
        AncCredentialSubject participationSubject = subjectFixture(1L, "PARTICIPANT");
        AncCredentialSubject awardSubject = subjectFixture(2L, "AWARDEE");
        given(credentialSubjectRepository.findByUserIdOrderByCreatedAtDesc(10L))
                .willReturn(List.of(participationSubject, awardSubject));

        AncCredential participation = credentialFixture(
                1L, "2026-P1-T1", CredentialType.PARTICIPATION, 1L, LocalDateTime.of(2026, 7, 27, 10, 0));
        AncCredential award = credentialFixture(
                2L, "2026-C1-001", CredentialType.AWARD, 1L, LocalDateTime.of(2026, 7, 27, 12, 0));
        given(credentialRepository.findAllById(anyList())).willReturn(List.of(participation, award));

        List<CredentialHistoryRes> result = credentialHistoryService.getMyCredentials(10L);

        assertThat(result).hasSize(2);
        //발급일 내림차순 — 수상이 먼저
        assertThat(result.get(0).credentialNo()).isEqualTo("2026-C1-001");
        assertThat(result.get(0).roleCode()).isEqualTo("AWARDEE");
        assertThat(result.get(0).contestTitle()).isEqualTo("2026 캡스톤 경진대회");
        assertThat(result.get(0).status()).isEqualTo(CredentialStatus.READY);
        assertThat(result.get(1).credentialType()).isEqualTo(CredentialType.PARTICIPATION);
    }

    @Test
    @DisplayName("관리자 학번 조회는 자기 학교가 발급한 Credential만 노출한다")
    void getStudentCredentials_filtersByIssuerOrganization() {
        givenAdminInOrganization(1L);
        User student = mock(User.class);
        lenient().when(student.getId()).thenReturn(10L);
        given(userRepository.findByOrganizationIdAndStudentId(1L, "2171193"))
                .willReturn(Optional.of(student));

        //fixture는 스터빙 밖에서 먼저 생성한다 (mock 중첩 스터빙 방지)
        AncCredentialSubject first = subjectFixture(1L, "PARTICIPANT");
        AncCredentialSubject second = subjectFixture(2L, "AWARDEE");
        //credential 1은 우리 학교(org 1), credential 2는 타 학교(org 9) 발급분
        AncCredential ours = credentialFixture(1L, "2026-P1-T1", CredentialType.PARTICIPATION, 1L, LocalDateTime.now());
        AncCredential theirs = credentialFixture(2L, "2026-C9-001", CredentialType.AWARD, 9L, LocalDateTime.now());
        given(credentialSubjectRepository.findByUserIdOrderByCreatedAtDesc(10L))
                .willReturn(List.of(first, second));
        given(credentialRepository.findAllById(anyList())).willReturn(List.of(ours, theirs));

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

    private AncCredentialSubject subjectFixture(Long credentialId, String roleCode) {
        AncCredentialSubject subject = mock(AncCredentialSubject.class);
        lenient().when(subject.getCredentialId()).thenReturn(credentialId);
        lenient().when(subject.getRoleCode()).thenReturn(roleCode);
        lenient().when(subject.getDisplayNameSnapshot()).thenReturn("이준수");
        return subject;
    }

    private AncCredential credentialFixture(
            Long id, String credentialNo, CredentialType type, Long issuerOrganizationId, LocalDateTime issuedAt) {
        AncCredential credential = mock(AncCredential.class);
        lenient().when(credential.getId()).thenReturn(id);
        lenient().when(credential.getPublicId()).thenReturn("cred-pub-" + id);
        lenient().when(credential.getCredentialNo()).thenReturn(credentialNo);
        lenient().when(credential.getCredentialType()).thenReturn(type);
        lenient().when(credential.getStatus()).thenReturn(CredentialStatus.READY);
        lenient().when(credential.getIssuerOrganizationId()).thenReturn(issuerOrganizationId);
        lenient().when(credential.getIssuedAt()).thenReturn(issuedAt);
        lenient().when(credential.getPayloadJson()).thenReturn(
                "{\"source\":{\"snapshot\":{\"contestTitle\":\"2026 캡스톤 경진대회\"}}}");
        return credential;
    }
}
