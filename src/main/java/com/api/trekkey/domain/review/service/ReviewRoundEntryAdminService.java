package com.api.trekkey.domain.review.service;

import com.api.trekkey.domain.review.web.dto.response.ReviewRoundEntryRes;
import java.util.List;

public interface ReviewRoundEntryAdminService {

    List<ReviewRoundEntryRes> prepareEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewStageId);

    List<ReviewRoundEntryRes> getEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewStageId);
}
