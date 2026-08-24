package com.api.trekkey.domain.graduation.service;

import com.api.trekkey.domain.graduation.web.dto.GraduationProfileReq;
import com.api.trekkey.domain.graduation.web.dto.GraduationProfileRes;

public interface GraduationProfileService {
    GraduationProfileRes get(Long userId);
    GraduationProfileRes save(Long userId, GraduationProfileReq request);
}
