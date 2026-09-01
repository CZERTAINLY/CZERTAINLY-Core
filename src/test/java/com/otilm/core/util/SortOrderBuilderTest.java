package com.otilm.core.util;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SortDirection;
import com.otilm.core.dao.repository.SortSpecification;
import java.util.List;
import java.util.UUID;
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
    void rankingPutsRowsBackIntoTheOrderOfTheUuidPage() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        List<UUID> ranked = SortOrderBuilder
                .rankBy(List.of(third, first, second), List.of(first, second, third), row -> row);

        Assertions.assertEquals(List.of(third, first, second), ranked);
    }

    @Test
    void rankingSortsARowMissingFromThePageLast() {
        UUID ranked = UUID.randomUUID();
        UUID unranked = UUID.randomUUID();

        Assertions
                .assertEquals(List.of(ranked, unranked),
                        SortOrderBuilder.rankBy(List.of(ranked), List.of(unranked, ranked), row -> row));
    }

    @Test
    void rankingAnEmptyPageYieldsNothing() {
        Assertions.assertTrue(SortOrderBuilder.rankBy(List.of(), List.of(), row -> (UUID) row).isEmpty());
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
