package com.api.trekkey.domain.credential.service;

import com.api.trekkey.domain.credential.entity.CredentialStatus;
import com.api.trekkey.domain.credential.entity.CredentialType;
import com.api.trekkey.domain.credential.entity.PublicActivityProfile;
import com.api.trekkey.domain.credential.exception.CredentialErrorResponseCode;
import com.api.trekkey.domain.credential.repository.AncCredentialSubjectRepository;
import com.api.trekkey.domain.credential.repository.CredentialHistoryRow;
import com.api.trekkey.domain.credential.repository.PublicActivityProfileRepository;
import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileRes;
import com.api.trekkey.domain.credential.web.dto.PublicActivityProfileSettingsRes;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicActivityProfileServiceImpl implements PublicActivityProfileService {

    private final UserRepository userRepository;
    private final PublicActivityProfileRepository profileRepository;
    private final AncCredentialSubjectRepository credentialSubjectRepository;

    @Override
    public PublicActivityProfileSettingsRes getSettings(Long userId) {
        requireUser(userId);
        int count = publicRows(userId).size();
        return profileRepository.findByUserId(userId)
                .map(profile -> settings(profile, count))
                .orElseGet(() -> PublicActivityProfileSettingsRes.disabled(count));
    }

    @Override
    @Transactional
    public PublicActivityProfileSettingsRes updateSettings(Long userId, boolean enabled) {
        requireUser(userId);
        PublicActivityProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> profileRepository.save(PublicActivityProfile.enabled(userId, newPublicId())));
        profile.setEnabled(enabled);
        return settings(profile, publicRows(userId).size());
    }

    @Override
    @Transactional
    public PublicActivityProfileSettingsRes rotateLink(Long userId) {
        requireUser(userId);
        PublicActivityProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.PUBLIC_ACTIVITY_PROFILE_NOT_FOUND));
        profile.rotatePublicId(newPublicId());
        profile.setEnabled(true);
        return settings(profile, publicRows(userId).size());
    }

    @Override
    public PublicActivityProfileRes getPublicProfile(String publicId) {
        PublicActivityProfile profile = profileRepository.findByPublicIdAndEnabledTrue(publicId)
                .orElseThrow(() -> new CustomException(CredentialErrorResponseCode.PUBLIC_ACTIVITY_PROFILE_NOT_FOUND));
        User user = requireUser(profile.getUserId());
        List<CredentialHistoryRow> rows = publicRows(user.getId());

        List<PublicActivityProfileRes.Activity> activities = rows.stream()
                .map(row -> new PublicActivityProfileRes.Activity(
                        row.getCredentialPublicId(),
                        row.getCredentialNo(),
                        CredentialType.valueOf(row.getCredentialType()),
                        CredentialStatus.valueOf(row.getStatus()),
                        row.getRoleCode(),
                        row.getContestTitle(),
                        row.getIssuedAt()))
                .toList();

        return new PublicActivityProfileRes(
                profile.getPublicId(),
                user.getName(),
                user.getMajor(),
                user.getOrganization().getName(),
                activities.size(),
                activities);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
    }

    private List<CredentialHistoryRow> publicRows(Long userId) {
        return credentialSubjectRepository.findPublicOnChainHistoryRowsByUserId(userId);
    }

    private PublicActivityProfileSettingsRes settings(PublicActivityProfile profile, int count) {
        return new PublicActivityProfileSettingsRes(profile.isEnabled(), profile.getPublicId(), count);
    }

    private String newPublicId() {
        return UUID.randomUUID().toString();
    }
}
