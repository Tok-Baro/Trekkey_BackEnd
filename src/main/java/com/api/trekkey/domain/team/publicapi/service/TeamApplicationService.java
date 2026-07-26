package com.api.trekkey.domain.team.publicapi.service;

import com.api.trekkey.domain.team.publicapi.web.dto.ParticipantSearchRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationCreateReq;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationRes;
import com.api.trekkey.domain.team.publicapi.web.dto.TeamApplicationUpdateReq;
import java.util.List;

public interface TeamApplicationService {

    void createApplication(Long userId, String contestPublicId, TeamApplicationCreateReq request);

    List<TeamApplicationRes> getMyApplications(Long userId);

    List<ParticipantSearchRes> searchParticipants(Long userId, String keyword);

    void updateApplication(Long userId, String contestPublicId, TeamApplicationUpdateReq request);
}
