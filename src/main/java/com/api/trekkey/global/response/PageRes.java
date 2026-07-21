package com.api.trekkey.global.response;

import java.util.List;

public record PageRes<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageRes<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean hasNext = page + 1 < totalPages;
        return new PageRes<>(content, page, size, totalElements, totalPages, hasNext);
    }
}
