package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticatedOrganizationResolver {

    private final UserRepository userRepository;

    public Long resolve(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getOrganization().getId())
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.INVALID_CREDENTIAL_INPUT));
    }
}
