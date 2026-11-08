package com.cinecircle.common;

/**
 * Normalised paging parameters: page is 0-based (negative values become 0),
 * size is clamped to 1..50 (default 20).
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public static PageParams of(Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageParams(p, s);
    }

    public long offset() {
        return (long) page * size;
    }
}
