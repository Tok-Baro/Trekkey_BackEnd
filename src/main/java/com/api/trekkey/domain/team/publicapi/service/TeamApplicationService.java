package com.api.trekkey.domain.team.publicapi.service;

import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;

public interface TeamApplicationService {

    void createApplication(Long userId, String contestPublicId, TeamApplicationCreateReq request);
}
