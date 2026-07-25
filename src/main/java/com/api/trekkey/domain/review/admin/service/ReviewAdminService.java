package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.review.admin.web.dto.EntryDecisionReq;
import com.api.trekkey.domain.review.admin.web.dto.EntryRes;
import com.api.trekkey.domain.review.admin.web.dto.JudgeCreateReq;
import com.api.trekkey.domain.review.admin.web.dto.JudgeRes;
import java.util.List;

public interface ReviewAdminService {

    // 심사위원 등록 — 심사 링크 토큰을 발급한다 (응답에만 원문 노출)
    JudgeRes createJudge(Long adminUserId, String contestPublicId, JudgeCreateReq request);

    // 대회별 심사위원 목록 — 배정/완료 집계 포함
    List<JudgeRes> getJudges(Long adminUserId, String contestPublicId);

    // 심사 링크 재발급 (유출 대응)
    JudgeRes rotateToken(Long adminUserId, Long judgeId);

    // 심사위원 삭제 — 배정이 없을 때만
    void deleteJudge(Long adminUserId, Long judgeId);

    // 라운드 시작 — 대상 제출물 확정·잠금 → ENTRY 생성 → 전 심사위원 배정 (erd-mvp §5)
    List<EntryRes> openRound(Long adminUserId, Long stageId);

    // 라운드 심사 현황 — entry별 심사 수·평균 포함
    List<EntryRes> getEntries(Long adminUserId, Long stageId);

    // 라운드 마감·공식 확정 — 집계·순위·통과규칙 적용, FINALIZED 잠금 (erd-mvp §5)
    List<EntryRes> finalizeRound(Long adminUserId, Long stageId);

    // 관리자 수동 판정 — 동점 처리·정정 (MANUAL 통과규칙 라운드)
    EntryRes decideEntry(Long adminUserId, Long entryId, EntryDecisionReq request);
}
