package com.api.trekkey.domain.credential.repository;

import com.api.trekkey.domain.credential.entity.AncBatchItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AncBatchItemRepository extends JpaRepository<AncBatchItem, Long> {

    Optional<AncBatchItem> findByCredentialId(Long credentialId);

    List<AncBatchItem> findByBatchIdOrderByLeafIndexAsc(Long batchId);
}
