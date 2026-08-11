package com.api.trekkey.domain.graduation.service;

import com.api.trekkey.domain.graduation.web.dto.GraduationEvaluationRes;
import java.time.LocalDate;

public interface GraduationEvaluationService {
    GraduationEvaluationRes evaluate(Long userId, LocalDate policyAsOf);
}
