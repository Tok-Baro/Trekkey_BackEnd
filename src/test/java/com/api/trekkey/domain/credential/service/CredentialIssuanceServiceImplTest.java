package com.api.trekkey.domain.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncCredentialSource;
import com.api.trekkey.domain.credential.entity.AncCredentialSubject;
import com.api.trekkey.domain.credential.entity.CredentialSourceType;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialSubjectType;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.DisclosureClass;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.crypto.FileManifest;
import com.api.trekkey.domain.credential.crypto.Hashing;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialSourceRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.service.dto.CredentialIssueCommand;
import com.api.trekkey.domain.credential.service.dto.IssuedCredential;
import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CredentialIssuanceServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AncCredentialRepository credentialRepository;

    @Mock
    private AncCredentialSourceRepository sourceRepository;

    @Mock
    private AncCredentialSubjectRepository subjectRepository;

    private CredentialIssuanceServiceImpl service;
    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new CredentialIssuanceServiceImpl(
                organizationRepository,
                credentialRepository,
                sourceRepository,
                subjectRepository);
        organization = org();
        given(organizationRepository.findByIdForUpdate(1L)).willReturn(Optional.of(organization));
    }

    @Test
    void createsImmutableCredentialSourceAndSubjectSnapshots() throws Exception {
        given(sourceRepository.findBySourceFingerprint(any(byte[].class))).willReturn(Optional.empty());
        given(credentialRepository.findByIssuerOrganizationIdAndCredentialNo(1L, "AWARD-2026-001"))
                .willReturn(Optional.empty());
        given(credentialRepository.save(any(AncCredential.class))).willAnswer(invocation -> {
            AncCredential credential = invocation.getArgument(0);
            ReflectionTestUtils.setField(credential, "id", 10L);
            return credential;
        });

        IssuedCredential result = service.issue(command());

        ArgumentCaptor<AncCredential> credentialCaptor = ArgumentCaptor.forClass(AncCredential.class);
        ArgumentCaptor<AncCredentialSource> sourceCaptor = ArgumentCaptor.forClass(AncCredentialSource.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AncCredentialSubject>> subjectsCaptor = ArgumentCaptor.forClass(List.class);
        verify(credentialRepository).save(credentialCaptor.capture());
        verify(sourceRepository).save(sourceCaptor.capture());
        verify(subjectRepository).saveAll(subjectsCaptor.capture());

        AncCredential credential = credentialCaptor.getValue();
        assertThat(result.status()).isEqualTo(CredentialStatus.READY);
        assertThat(result.alreadyExisted()).isFalse();
        assertThat(credential.getPayloadJson())
                .contains("\"credentialType\":\"AWARD\"")
                .contains("\"issuer\"")
                .contains("\"subjects\"")
                .doesNotContain("\"userId\"");
        assertThat(credential.getContentHash()).hasSize(32);
        assertThat(credential.getFileManifestHash()).hasSize(32);
        assertThat(sourceCaptor.getValue().getSourceFingerprint()).hasSize(32);
        assertThat(subjectsCaptor.getValue())
                .extracting(AncCredentialSubject::getSubjectOrder)
                .containsExactly(0, 1);
    }

    @Test
    void returnsExistingCredentialForTheSameSourceFingerprint() throws Exception {
        AncCredential existing = credential(77L, "existing-credential");
        AncCredentialSource source = org.mockito.Mockito.mock(AncCredentialSource.class);
        given(source.getCredentialId()).willReturn(77L);
        given(sourceRepository.findBySourceFingerprint(any(byte[].class))).willReturn(Optional.of(source));
        given(credentialRepository.findById(77L)).willReturn(Optional.of(existing));

        IssuedCredential result = service.issue(command());

        assertThat(result.publicId()).isEqualTo("existing-credential");
        assertThat(result.alreadyExisted()).isTrue();
    }

    @Test
    void rejectsDifferentIssuanceMetadataForTheSameSourceFingerprint() throws Exception {
        AncCredential existing = credential(77L, "existing-credential");
        AncCredentialSource source = org.mockito.Mockito.mock(AncCredentialSource.class);
        given(source.getCredentialId()).willReturn(77L);
        given(sourceRepository.findBySourceFingerprint(any(byte[].class))).willReturn(Optional.of(source));
        given(credentialRepository.findById(77L)).willReturn(Optional.of(existing));

        CredentialIssueCommand conflicting = new CredentialIssueCommand(
                1L,
                "AWARD-2026-CHANGED",
                command().credentialType(),
                command().schemaProfileId(),
                command().source(),
                command().subjects(),
                command().files(),
                command().issuedAt(),
                command().expiresAt());

        assertThatThrownBy(() -> service.issue(conflicting))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(CredentialErrorResponseCode.CREDENTIAL_SOURCE_CONFLICT));
    }

    private CredentialIssueCommand command() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return new CredentialIssueCommand(
                1L,
                "AWARD-2026-001",
                CredentialType.AWARD,
                CredentialSchemaProfiles.AWARD_V1,
                new CredentialIssueCommand.Source(
                        CredentialSourceType.AWARD,
                        null,
                        null,
                        30L,
                        "award-public-30",
                        Instant.parse("2026-07-24T01:00:00Z"),
                        mapper.readTree("{\"awardName\":\"대상\",\"contestPublicId\":\"contest-1\"}")),
                List.of(
                        new CredentialIssueCommand.Subject(
                                null,
                                20L,
                                "team-public-20",
                                CredentialSubjectType.TEAM,
                                "트레키 팀",
                                null,
                                "AWARDEE",
                                DisclosureClass.PUBLIC,
                                0),
                        new CredentialIssueCommand.Subject(
                                40L,
                                null,
                                "user-public-40",
                                CredentialSubjectType.USER,
                                "김학생",
                                "컴퓨터공학",
                                "REPRESENTATIVE",
                                DisclosureClass.PUBLIC,
                                1)),
                List.of(),
                Instant.parse("2026-07-24T01:05:00Z"),
                null);
    }

    private Organization org() {
        Organization value = org.springframework.beans.BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(value, "id", 1L);
        ReflectionTestUtils.setField(value, "publicId", "organization-public-1");
        ReflectionTestUtils.setField(value, "name", "트레키대학교");
        return value;
    }

    private AncCredential credential(Long id, String publicId) {
        FileManifest.Result manifest = FileManifest.build(List.of());
        AncCredential value = AncCredential.ready(
                1L,
                publicId,
                bytes(1),
                "AWARD-2026-001",
                CredentialType.AWARD,
                CredentialSchemaProfiles.AWARD_V1,
                Hashing.schemaVersion(CredentialSchemaProfiles.AWARD_V1).bytes(),
                "{}",
                new byte[] {1},
                manifest.canonicalBytes(),
                Hashing.sha256(new byte[] {1}).bytes(),
                manifest.hash().bytes(),
                java.time.LocalDateTime.of(2026, 7, 24, 1, 5),
                null);
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }

    private byte[] bytes(int first) {
        byte[] value = new byte[32];
        value[0] = (byte) first;
        return value;
    }
}
