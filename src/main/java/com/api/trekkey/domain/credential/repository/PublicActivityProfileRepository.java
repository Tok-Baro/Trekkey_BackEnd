package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.PublicActivityProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicActivityProfileRepository extends JpaRepository<PublicActivityProfile, Long> {

    Optional<PublicActivityProfile> findByUserId(Long userId);

    Optional<PublicActivityProfile> findByPublicIdAndEnabledTrue(String publicId);
}
