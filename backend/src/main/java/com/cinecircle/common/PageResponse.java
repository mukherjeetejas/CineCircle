package com.cinecircle.common;

import java.util.List;
import java.util.function.Function;

/** Paged response: { items, page, size, hasNext } (page is 0-based). */
public record PageResponse<T>(List<T> items, int page, int size, boolean hasNext) {

    public <R> PageResponse<R> map(Function<List<T>, List<R>> mapper) {
        return new PageResponse<>(mapper.apply(items), page, size, hasNext);
    }

    /**
     * Builds a page from a result list that was fetched with {@code limit = size + 1}:
     * the extra element (if present) only signals {@code hasNext}.
     */
    public static <T> PageResponse<T> fromOverfetch(List<T> fetched, PageParams params) {
        boolean hasNext = fetched.size() > params.size();
        List<T> items = hasNext ? List.copyOf(fetched.subList(0, params.size())) : fetched;
        return new PageResponse<>(items, params.page(), params.size(), hasNext);
    }
}
