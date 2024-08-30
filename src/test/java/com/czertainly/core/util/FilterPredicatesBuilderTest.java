package com.czertainly.core.util;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.Certificate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.query.sqm.tree.predicate.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for class {@link FilterPredicatesBuilder}
 */
@SpringBootTest
public class FilterPredicatesBuilderTest extends BaseSpringBootTest {

    @Autowired
    private EntityManager entityManager;

    private CriteriaBuilder criteriaBuilder;

    private CriteriaQuery<Certificate> criteriaQuery;

    private Root<Certificate> root;

    private final String TEST_VALUE = "test";
    private final String TEST_DATE_VALUE = "2022-01-01";

    @BeforeEach
    public void prepare() {
        criteriaBuilder = entityManager.getCriteriaBuilder();
        criteriaQuery = criteriaBuilder.createQuery(Certificate.class);
        root = criteriaQuery.from(Certificate.class);
    }

    @Test
    public void testEqualsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.EQUALS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmComparisonPredicate.class, predicateTest);
        Assertions.assertEquals(ComparisonOperator.EQUAL, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(TEST_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    public void testNotEqualsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.NOT_EQUALS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmJunctionPredicate.class, predicateTest);

        final SqmJunctionPredicate sqmJunctionPredicate = ((SqmJunctionPredicate) predicateTest);
        for (final SqmPredicate predicate : sqmJunctionPredicate.getPredicates()) {
            Assertions.assertTrue(predicate instanceof SqmComparisonPredicate || predicate instanceof SqmNullnessPredicate);
            if (predicate instanceof SqmComparisonPredicate) {
                Assertions.assertEquals(ComparisonOperator.NOT_EQUAL, ((SqmComparisonPredicate) predicate).getSqmOperator());
                Assertions.assertEquals(TEST_VALUE, ((SqmComparisonPredicate) predicate).getRightHandExpression().toHqlString());
            } else {
                Assertions.assertFalse(predicate.isNull().isNegated());
            }
        }
    }

    @Test
    public void testContainsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.CONTAINS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, "%" + TEST_VALUE + "%");
    }

    @Test
    public void testNotContainsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.NOT_CONTAINS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmJunctionPredicate.class, predicateTest);

        final SqmJunctionPredicate sqmJunctionPredicate = ((SqmJunctionPredicate) predicateTest);
        for (final SqmPredicate predicate : sqmJunctionPredicate.getPredicates()) {
            Assertions.assertTrue(predicate instanceof SqmLikePredicate || predicate instanceof SqmNullnessPredicate);
            if (predicate instanceof SqmLikePredicate) {
                Assertions.assertTrue(predicate.isNegated());
                Assertions.assertEquals("%" + TEST_VALUE + "%", ((SqmLikePredicate) predicate).getPattern().toHqlString());
            } else {
                Assertions.assertFalse(predicate.isNull().isNegated());
            }
        }
    }

    @Test
    public void testStartWithPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.STARTS_WITH)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, TEST_VALUE + "%");
    }

    @Test
    public void testEndWithPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.ENDS_WITH)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, "%" + TEST_VALUE);
    }

    @Test
    public void testEmptyPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.EMPTY)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmNullnessPredicate.class, predicateTest);
        Assertions.assertFalse(predicateTest.isNull().isNegated());
    }

    @Test
    public void testNotEmptyPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.NOT_EMPTY)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmNullnessPredicate.class, predicateTest);
        Assertions.assertTrue(predicateTest.isNotNull().isNegated());
    }

    @Test
    public void testGreaterPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.GREATER)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertEquals(ComparisonOperator.GREATER_THAN, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(TEST_DATE_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    public void testLesserPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.LESSER)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertEquals(ComparisonOperator.LESS_THAN, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(TEST_DATE_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    public void testCombinedFilters() {
        List<SearchFilterRequestDto> testFilters = new ArrayList<>();
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.SUBJECTDN, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.COMMON_NAME, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.META, SearchableFields.CKI_LENGTH, AttributeContentType.STRING, FilterConditionOperator.EQUALS, 1));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.CUSTOM, SearchableFields.SERIAL_NUMBER, AttributeContentType.INTEGER, FilterConditionOperator.NOT_EQUALS, "123"));

        final SqmJunctionPredicate filterPredicate = (SqmJunctionPredicate) FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, testFilters);
        Assertions.assertEquals(4,(filterPredicate.getPredicates().size()));

        Assertions.assertInstanceOf(SqmExistsPredicate.class, filterPredicate.getPredicates().get(2));
        Assertions.assertInstanceOf(SqmExistsPredicate.class, filterPredicate.getPredicates().get(3));
        Assertions.assertTrue(filterPredicate.getPredicates().get(3).isNegated());
    }


    private void testLikePredicate(final Predicate predicate, final String value) {
        Assertions.assertInstanceOf(SqmLikePredicate.class, predicate);
        Assertions.assertEquals(value, ((SqmLikePredicate) predicate).getPattern().toHqlString());
    }

    private SearchFilterRequestDTODummy prepareDummyFilterRequest(final FilterConditionOperator condition) {
        SearchFilterRequestDTODummy dummy = null;
        switch (condition) {
            case EQUALS ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.COMMON_NAME, FilterConditionOperator.EQUALS, TEST_VALUE);
            case NOT_EQUALS ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.COMMON_NAME, FilterConditionOperator.NOT_EQUALS, TEST_VALUE);
            case CONTAINS ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.COMMON_NAME, FilterConditionOperator.CONTAINS, TEST_VALUE);
            case NOT_CONTAINS ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.COMMON_NAME, FilterConditionOperator.NOT_CONTAINS, TEST_VALUE);
            case STARTS_WITH ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.COMMON_NAME, FilterConditionOperator.STARTS_WITH, TEST_VALUE);
            case ENDS_WITH ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.COMMON_NAME, FilterConditionOperator.ENDS_WITH, TEST_VALUE);
            case EMPTY ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.COMMON_NAME, FilterConditionOperator.EMPTY, TEST_VALUE);
            case NOT_EMPTY ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.COMMON_NAME, FilterConditionOperator.NOT_EMPTY, TEST_VALUE);
            case GREATER ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.NOT_AFTER, FilterConditionOperator.GREATER, TEST_DATE_VALUE);
            case LESSER ->
                    dummy = new SearchFilterRequestDTODummy(SearchableFields.NOT_BEFORE, FilterConditionOperator.LESSER, TEST_DATE_VALUE);
        }
        return dummy;
    }


}

class SearchFilterRequestDTODummy extends SearchFilterRequestDto {

    private SearchableFields fieldTest;
    private FilterConditionOperator conditionTest;
    private Serializable valueTest;
    private FilterFieldSource filterFieldSource;
    private String fieldIdentifier;

    public SearchFilterRequestDTODummy(SearchableFields fieldTest, FilterConditionOperator conditionTest, Serializable valueTest) {
        this.fieldTest = fieldTest;
        this.conditionTest = conditionTest;
        this.valueTest = valueTest;
        this.fieldIdentifier = fieldTest.name();
        this.filterFieldSource = FilterFieldSource.PROPERTY;
    }

    public SearchFilterRequestDTODummy(FilterFieldSource filterFieldSource, SearchableFields fieldTest, FilterConditionOperator conditionTest, Serializable valueTest) {
        this.filterFieldSource = filterFieldSource;
        this.fieldTest = fieldTest;
        this.conditionTest = conditionTest;
        this.valueTest = valueTest;
        this.fieldIdentifier = fieldTest.name();
    }

    public SearchFilterRequestDTODummy(FilterFieldSource filterFieldSource, SearchableFields fieldTest, AttributeContentType attributeContentType, FilterConditionOperator conditionTest, Serializable valueTest) {
        this.filterFieldSource = filterFieldSource;
        this.fieldTest = fieldTest;
        this.conditionTest = conditionTest;
        this.valueTest = valueTest;
        this.fieldIdentifier = fieldTest.name() + "|" + attributeContentType.name();
    }

    public SearchableFields getField() {
        return fieldTest;
    }

    public FilterConditionOperator getCondition() {
        return conditionTest;
    }

    public Serializable getValue() {
        return valueTest;
    }

    public void setFieldTest(SearchableFields fieldTest) {
        this.fieldTest = fieldTest;
        this.fieldIdentifier = fieldTest.name();
    }

    public void setConditionTest(FilterConditionOperator conditionTest) {
        this.conditionTest = conditionTest;
    }

    public void setValueTest(Serializable valueTest) {
        this.valueTest = valueTest;
    }

    @Override
    public String getFieldIdentifier() {
        return fieldIdentifier;
    }

    public void setSearchGroup(FilterFieldSource filterFieldSource) {
        this.filterFieldSource = filterFieldSource;
    }

    @Override
    public FilterFieldSource getFieldSource() {
        return filterFieldSource;
    }
}
