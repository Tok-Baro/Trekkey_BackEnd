package com.api.trekkey.domain.contest.service;

import com.api.trekkey.domain.contest.web.dto.ContestCreateReq;
import com.api.trekkey.domain.contest.web.dto.ContestDetailRes;
import com.api.trekkey.domain.contest.web.dto.StageRes;
import com.api.trekkey.domain.contest.web.dto.StageStatusUpdateReq;

public interface ContestCommandService {

    // 대회를 생성하고 단계·평가 기준을 함께 저장한다. 담당자와 조직은 인증 사용자 기준으로 설정한다.
    ContestDetailRes createContest(Long userId, ContestCreateReq req);

    // 대회 설정 전체를 수정한다. 단계는 id 매칭 수정 / 미포함 삭제 / 신규 추가로 교체한다.
    ContestDetailRes updateContest(Long userId, String publicId, ContestCreateReq req);

    // 운영 중 단계 상태만 전환한다.
    StageRes updateStageStatus(Long userId, Long stageId, StageStatusUpdateReq req);
}
