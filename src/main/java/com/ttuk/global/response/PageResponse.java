package com.ttuk.global.response;

import java.util.List;

public record PageResponse<T>(

        List<T> content,
        int page,
        int size,
        int totalPages,
        long totalElements

) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        long fullPages = totalElements / size;
        boolean hasRemainder = totalElements % size != 0;
        int totalPages = (int) (hasRemainder ? fullPages + 1 : fullPages);
        return new PageResponse<>(content, page, size, totalPages, totalElements);
    }

}
