package com.api.trekkey.domain.review.web.dto.request;

public record ReviewAccessReq(
        String token
) {
    @Override
    public String toString() {
        return "ReviewAccessReq[token=***]";
    }
}
