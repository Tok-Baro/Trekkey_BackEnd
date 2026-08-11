package com.api.trekkey.domain.graduation.repository;

import com.api.trekkey.domain.graduation.entity.GraduationEvaluationItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationEvaluationItemRepository extends JpaRepository<GraduationEvaluationItem, Long> {
    List<GraduationEvaluationItem> findAllByEvaluationIdOrderBySequenceNo(Long evaluationId);
}
