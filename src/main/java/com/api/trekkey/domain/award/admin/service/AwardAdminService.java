package com.api.trekkey.domain.award.admin.service;

import com.api.trekkey.domain.award.admin.web.dto.AwardRes;
import java.util.List;

public interface AwardAdminService {

    // 수상 후보 산출 — 확정된 라운드의 통과작을 순위순으로 awardCount만큼 CANDIDATE 생성 (재산출 시 기존 후보 교체)
    List<AwardRes> calculateAwards(Long adminUserId, Long stageId);

    // 대회별 수상 목록
    List<AwardRes> getAwards(Long adminUserId, String contestPublicId);

    // 수상 확정 — 전체 후보 CONFIRMED, 대회 상태 AWARDED 전환. Credential 발급 원천이 된다 (erd-mvp §6)
    List<AwardRes> confirmAwards(Long adminUserId, String contestPublicId);
}
