package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncCredentialSubject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AncCredentialSubjectRepository extends JpaRepository<AncCredentialSubject, Long> {

    List<AncCredentialSubject> findByCredentialIdOrderBySubjectOrderAsc(Long credentialId);

    List<AncCredentialSubject> findByUserIdOrderByCreatedAtDesc(Long userId);
}
