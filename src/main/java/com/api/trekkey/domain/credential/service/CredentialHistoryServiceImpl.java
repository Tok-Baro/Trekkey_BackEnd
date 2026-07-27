package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.repository.CredentialHistoryRow;
import com.api.trekkey.domain.credential.web.dto.CredentialHistoryRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 Credential 이력 조회 (erd-mvp §5·§13).
 * 현재 팀·업무 원장이 아니라 발급 당시 ANC_CREDENTIAL_SUBJECT snapshot을 근거로 조회한다 —
 * 팀이 해체되거나 업무 데이터가 바뀌어도 개인 이력은 보존된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CredentialHistoryServiceImpl implements CredentialHistoryService {

    private final UserRepository userRepository;
    private final AncCredentialSubjectRepository credentialSubjectRepository;

    @Override
    public List<CredentialHistoryRes> getMyCredentials(Long userId) {
        return toHistory(credentialSubjectRepository.findHistoryRowsByUserId(userId), null);
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
                credentialSubjectRepository.findHistoryRowsByUserId(student.getId()),
                organizationId);
    }

    //======= 헬퍼 메서드 ==========

    private List<CredentialHistoryRes> toHistory(List<CredentialHistoryRow> rows, Long issuerOrganizationId) {
        return rows.stream()
                .filter(row -> {
                    //LEFT JOIN에서 credential이 비면 끊어진 참조 — 데이터 정합 문제이므로 숨기지 않고 경고한다
                    if (row.getCredentialId() == null) {
                        log.warn("ANC_CREDENTIAL_SUBJECT가 존재하지 않는 credential을 참조합니다. credentialId={}",
                                row.getSubjectCredentialId());
                        return false;
                    }
                    return true;
                })
                //관리자 학번 조회는 자기 학교가 발급한 Credential만 노출한다
                .filter(row -> issuerOrganizationId == null
                        || issuerOrganizationId.equals(row.getIssuerOrganizationId()))
                .map(row -> new CredentialHistoryRes(
                        row.getCredentialPublicId(),
                        row.getCredentialNo(),
                        CredentialType.valueOf(row.getCredentialType()),
                        CredentialStatus.valueOf(row.getStatus()),
                        row.getRoleCode(),
                        row.getDisplayName(),
                        row.getContestTitle(),
                        row.getIssuedAt()))
                .toList();
    }
}
