package com.api.trekkey.domain.review.admin.service;

import com.api.trekkey.domain.review.admin.web.dto.response.ReviewRoundEntryRes;
import java.util.List;

public interface ReviewRoundEntryAdminService {

    List<ReviewRoundEntryRes> prepareEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId);

    List<ReviewRoundEntryRes> getEntries(
            Long adminUserId,
            String contestPublicId,
            Long reviewRoundId);
}
