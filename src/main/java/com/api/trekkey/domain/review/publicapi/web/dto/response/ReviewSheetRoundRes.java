package com.api.trekkey.domain.review.publicapi.web.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewSheetRoundRes(
        Long reviewRoundId,
        int roundNo,
        String roundName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        List<ReviewSheetCriterionRes> criteria,
        List<ReviewSheetAssignmentRes> assignments
) {
}
