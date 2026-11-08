package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Paged response {@code {items, page, size, hasNext}} (page is 0-based). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Page<T>(List<T> items, int page, int size, boolean hasNext) {

    public List<T> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }
}
