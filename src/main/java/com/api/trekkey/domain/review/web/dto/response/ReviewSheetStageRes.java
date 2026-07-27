package com.api.trekkey.domain.review.web.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewSheetStageRes(
        Long reviewStageId,
        String stageName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        List<ReviewSheetCriterionRes> criteria,
        List<ReviewSheetAssignmentRes> assignments
) {
}
