package com.devpilot.common.api;

import java.util.List;

/**
 * One page of results.
 *
 * @param items rows on this page
 * @param total number of matching rows
 * @param page zero-based page index
 * @param size requested page size
 * @param <T> row type
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    /**
     * Builds a page.
     *
     * @param items rows on this page
     * @param total number of matching rows
     * @param page zero-based page index
     * @param size requested page size
     * @param <T> row type
     * @return page response
     */
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        return new PageResponse<>(List.copyOf(items), total, page, size);
    }
}
