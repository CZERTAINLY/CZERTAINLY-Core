package com.otilm.core.util;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SortDirection;
import com.otilm.core.dao.repository.SortSpecification;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The cases that hold without a persistence context. Anything that resolves a {@code FilterField} needs the JPA static
 * metamodel populated, and is covered by {@code SecurityFilterRepositoryITest}.
 */
class SortOrderBuilderTest {

    @Test
    void noSortTraversesNoJoin() {
        Assertions.assertFalse(SortOrderBuilder.traversesJoin(null));
    }

    @Test
    void attributeSourcedFieldIsRejected() {
        SortSpecification sort = new SortSpecification(FilterFieldSource.META, "anything", SortDirection.ASC);

        ValidationException e = Assertions
                .assertThrows(ValidationException.class, () -> SortOrderBuilder.traversesJoin(sort));
        Assertions.assertTrue(e.getMessage().contains(FilterFieldSource.META.getLabel()));
    }

    @Test
    void absentRequestSortCarriesNoSpecification() {
        Assertions.assertNull(SortSpecification.from(null));
    }

    @Test
    void requestSortCarriesSourceIdentifierAndDirection() {
        SearchSortRequestDto dto = new SearchSortRequestDto(FilterFieldSource.PROPERTY, "SUBJECTDN",
                SortDirection.DESC);

        Assertions
                .assertEquals(new SortSpecification(FilterFieldSource.PROPERTY, "SUBJECTDN", SortDirection.DESC),
                        SortSpecification.from(dto));
    }
}
