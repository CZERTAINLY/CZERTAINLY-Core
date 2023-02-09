package com.czertainly.core.util;

import com.czertainly.api.model.client.certificate.SearchRequestDto;

import java.util.ArrayList;

public class RequestValidatorHelper {

    private static final Integer DEFAULT_ITEMS_PER_PAGE = 10;

    public static void revalidateSearchRequestDto(final SearchRequestDto dto) {
        if (dto.getFilters() == null) {
            dto.setFilters(new ArrayList<>());
        }
        if (dto.getItemsPerPage() == null) {
            dto.setItemsPerPage(DEFAULT_ITEMS_PER_PAGE);
        }
        if (dto.getPageNumber() == null) {
            dto.setPageNumber(1);
        }
    }


}
