package com.ravalgroups.forms.shared.api;

import com.ravalgroups.forms.shared.pagination.PageResult;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(
                result.content(), result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    public static <T, R> PageResponse<R> from(PageResult<T> result, Function<T, R> mapper) {
        return from(result.map(mapper));
    }
}
