package com.api.trekkey.domain.review.admin.web.dto.request;

import jakarta.validation.Valid;
import java.util.List;

public record ReviewRoundFinalizeReq(
        List<@Valid ReviewManualDecisionReq> manualDecisions
) {
}
