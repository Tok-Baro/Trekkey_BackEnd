package com.api.trekkey.domain.auth.service;

import com.api.trekkey.domain.organization.entity.Organization;
import com.api.trekkey.domain.organization.entity.OrganizationStatus;
import com.api.trekkey.domain.organization.exception.OrganizationErrorResponseCode;
import com.api.trekkey.domain.organization.repository.OrganizationRepository;
import com.api.trekkey.domain.user.entity.MemberType;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.entity.UserRole;
import com.api.trekkey.domain.user.entity.UserStatus;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.domain.user.web.dto.UserSignUpReq;
import com.api.trekkey.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;

    @Override
    public void signUp(UserSignUpReq userSignUpReq) {

        //사용자가 입력한 이메일이 이미 존재하는지 확인
        if (userRepository.existsByEmail(userSignUpReq.getEmail())) {
            throw new CustomException(UserErrorResponseCode.USER_EXISTS_EMAIL);
        }

        //사용자가 선택한 학교가 실제로 활성상태인지 확인
        Organization organization = organizationRepository.findByIdAndStatus(userSignUpReq.getOrganizationId(), OrganizationStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(OrganizationErrorResponseCode.ORGANIZATION_NOT_FOUND));

        User user = User.builder()
                .organization(organization)
                .email(userSignUpReq.getEmail())
                .name(userSignUpReq.getName())
                .password(passwordEncoder.encode(userSignUpReq.getPassword()))
                .studentId(userSignUpReq.getStudentId())
                .major(userSignUpReq.getMajor())
                .role(UserRole.PARTICIPANT)
                .memberType(MemberType.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
    }
}
