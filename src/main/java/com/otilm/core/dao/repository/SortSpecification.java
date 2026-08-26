package com.otilm.core.dao.repository;

import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SortDirection;

/**
 * Ordering a caller asked for, addressed the way a filter addresses a field: a source plus an identifier that is unique
 * only within that source. Carried instead of a ready-made {@code Order} lambda so the repository can decide how the
 * ordering interacts with the rest of the query it builds - the {@code SELECT DISTINCT} it emits, and the tie-break it
 * appends to keep paging stable.
 */
public record SortSpecification(FilterFieldSource fieldSource, String fieldIdentifier, SortDirection direction) {

    public static SortSpecification from(SearchSortRequestDto dto) {
        return dto == null
                ? null
                : new SortSpecification(dto.getFieldSource(), dto.getFieldIdentifier(), dto.getDirection());
    }
}
