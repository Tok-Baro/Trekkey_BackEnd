package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.entity.AncCredential;
import com.api.trekkey.domain.credential.entity.AncCredentialSubject;
import com.api.trekkey.domain.credential.repository.AncCredentialRepository;
import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.web.dto.CredentialHistoryRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 Credential 이력 조회 (erd-mvp §5·§13).
 * 현재 팀·업무 원장이 아니라 발급 당시 ANC_CREDENTIAL_SUBJECT snapshot을 근거로 조회한다 —
 * 팀이 해체되거나 업무 데이터가 바뀌어도 개인 이력은 보존된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialHistoryServiceImpl implements CredentialHistoryService {

    private final UserRepository userRepository;
    private final AncCredentialSubjectRepository credentialSubjectRepository;
    private final AncCredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<CredentialHistoryRes> getMyCredentials(Long userId) {
        return toHistory(credentialSubjectRepository.findByUserIdOrderByCreatedAtDesc(userId), null);
    }

    @Override
    public List<CredentialHistoryRes> getStudentCredentials(Long adminUserId, String studentId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
        Long organizationId = admin.getOrganization().getId();

        //학번 조회는 관리자 소속 학교 안에서만 — 타 조직 학생은 404로 비노출 (erd-mvp §13 organization scope)
        User student = userRepository.findByOrganizationIdAndStudentId(organizationId, studentId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));

        return toHistory(
                credentialSubjectRepository.findByUserIdOrderByCreatedAtDesc(student.getId()),
                organizationId);
    }

    //======= 헬퍼 메서드 ==========

    private List<CredentialHistoryRes> toHistory(List<AncCredentialSubject> subjects, Long issuerOrganizationId) {
        if (subjects.isEmpty()) {
            return List.of();
        }
        //subject → credential은 batch 조회로 한 번에 적재 (수현 컨벤션: 연관관계 없이 FK 값 참조)
        Map<Long, AncCredential> credentials = credentialRepository
                .findAllById(subjects.stream().map(AncCredentialSubject::getCredentialId).toList())
                .stream()
                .collect(Collectors.toMap(AncCredential::getId, Function.identity()));

        return subjects.stream()
                .map(subject -> {
                    AncCredential credential = credentials.get(subject.getCredentialId());
                    if (credential == null) {
                        return null;
                    }
                    //관리자 학번 조회는 자기 학교가 발급한 Credential만 노출한다
                    if (issuerOrganizationId != null
                            && !credential.getIssuerOrganizationId().equals(issuerOrganizationId)) {
                        return null;
                    }
                    return CredentialHistoryRes.of(credential, subject, contestTitle(credential));
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(CredentialHistoryRes::issuedAt).reversed())
                .toList();
    }

    // 발급 시점 payload에 고정된 대회명 — 업무 테이블이 아니라 불변 원문에서 읽는다
    private String contestTitle(AncCredential credential) {
        try {
            JsonNode payload = objectMapper.readTree(credential.getPayloadJson());
            return payload.path("source").path("snapshot").path("contestTitle").asText(null);
        } catch (Exception exception) {
            return null;
        }
    }
}
