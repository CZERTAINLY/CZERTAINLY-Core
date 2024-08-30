package com.czertainly.core.util.converter;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.model.SearchFieldObject;
import com.czertainly.core.util.BaseSpringBootTest;
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
 * Tests for class {@link Sql2PredicateConverter}
 */
@SpringBootTest
public class Sql2PredicateConverterTest extends BaseSpringBootTest {

    @Autowired
    private EntityManager entityManager;

    private CriteriaBuilder criteriaBuilder;

    private CriteriaQuery<Certificate> criteriaQuery;

    private Root<Certificate> root;

    private Root<CryptographicKeyItem> rootCryptoKeyItem;

    private final String TEST_VALUE = "test";
    private final String TEST_DATE_VALUE = "2022-01-01";

    private final String TEST_VERIFICATION_TEXT = "{\"status\":\"%STATUS%\"";

    @BeforeEach
    public void prepare() {
        criteriaBuilder = entityManager.getCriteriaBuilder();
        criteriaQuery = criteriaBuilder.createQuery(Certificate.class);
        root = criteriaQuery.from(Certificate.class);
    }

    @Test
    public void testEqualsPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.EQUALS), criteriaBuilder, root);
        Assertions.assertInstanceOf(SqmComparisonPredicate.class, predicateTest);
        Assertions.assertEquals(ComparisonOperator.EQUAL, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(TEST_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    public void testNotEqualsPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.NOT_EQUALS), criteriaBuilder, root);
        Assertions.assertInstanceOf(SqmJunctionPredicate.class, predicateTest);

        final SqmJunctionPredicate sqmJunctionPredicate = ((SqmJunctionPredicate) predicateTest);
        for (final SqmPredicate predicate : sqmJunctionPredicate.getPredicates()) {
            Assertions.assertTrue(predicate instanceof SqmComparisonPredicate || predicate instanceof SqmNullnessPredicate);
            if (predicate instanceof SqmComparisonPredicate) {
                Assertions.assertEquals(ComparisonOperator.NOT_EQUAL, ((SqmComparisonPredicate) predicate).getSqmOperator());
                Assertions.assertEquals(TEST_VALUE, ((SqmComparisonPredicate) predicate).getRightHandExpression().toHqlString());
            } else if (predicate instanceof SqmNullnessPredicate) {
                Assertions.assertFalse(predicate.isNull().isNegated());
            }
        }
    }

    @Test
    public void testContainsPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.CONTAINS), criteriaBuilder, root);
        testLikePredicate(predicateTest, "%" + TEST_VALUE + "%");
    }

    @Test
    public void testNotContainsPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.NOT_CONTAINS), criteriaBuilder, root);
        Assertions.assertInstanceOf(SqmJunctionPredicate.class, predicateTest);

        final SqmJunctionPredicate sqmJunctionPredicate = ((SqmJunctionPredicate) predicateTest);
        for (final SqmPredicate predicate : sqmJunctionPredicate.getPredicates()) {
            Assertions.assertTrue(predicate instanceof SqmLikePredicate || predicate instanceof SqmNullnessPredicate);
            if (predicate instanceof SqmLikePredicate) {
                Assertions.assertTrue(predicate.isNegated());
                Assertions.assertEquals("%" + TEST_VALUE + "%", ((SqmLikePredicate) predicate).getPattern().toHqlString());
            } else if (predicate instanceof SqmNullnessPredicate) {
                Assertions.assertFalse(predicate.isNull().isNegated());
            }
        }
    }

    @Test
    public void testStartWithPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.STARTS_WITH), criteriaBuilder, root);
        testLikePredicate(predicateTest, TEST_VALUE + "%");
    }

    @Test
    public void testEndWithPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.ENDS_WITH), criteriaBuilder, root);
        testLikePredicate(predicateTest, "%" + TEST_VALUE);
    }

    @Test
    public void testEmptyPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.EMPTY), criteriaBuilder, root);
        Assertions.assertInstanceOf(SqmNullnessPredicate.class, predicateTest);
        Assertions.assertFalse(predicateTest.isNull().isNegated());
    }

    @Test
    public void testNotEmptyPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.NOT_EMPTY), criteriaBuilder, root);
        Assertions.assertInstanceOf(SqmNullnessPredicate.class, predicateTest);
        Assertions.assertTrue(predicateTest.isNotNull().isNegated());
    }

    @Test
    public void testGreaterPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.GREATER), criteriaBuilder, root);
        Assertions.assertEquals(ComparisonOperator.GREATER_THAN, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(TEST_DATE_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    public void testLesserPredicate() {
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(prepareDummyFilterRequest(FilterConditionOperator.LESSER), criteriaBuilder, root);
        Assertions.assertEquals(ComparisonOperator.LESS_THAN, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(TEST_DATE_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    public void testOCSPValidation() {
        testVerifications(SearchableFields.OCSP_VALIDATION, FilterConditionOperator.EQUALS, CertificateValidationStatus.VALID);
    }

    @Test
    public void testSignatureValidation() {
        testVerifications(SearchableFields.SIGNATURE_VALIDATION, FilterConditionOperator.NOT_EQUALS, CertificateValidationStatus.FAILED);
    }

    @Test
    public void testCRLValidation() {
        testVerifications(SearchableFields.CRL_VALIDATION, FilterConditionOperator.EQUALS, CertificateValidationStatus.EXPIRED);
    }

    @Test
    public void testReplaceSearchCondition() {
        rootCryptoKeyItem = criteriaQuery.from(CryptographicKeyItem.class);
        final SearchFilterRequestDTODummy searchFilterRequestDtoDummy = prepareDummyFilterRequest(FilterConditionOperator.EQUALS);
        searchFilterRequestDtoDummy.setFieldTest(SearchableFields.CKI_USAGE);
        searchFilterRequestDtoDummy.setValueTest("sign");

        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(searchFilterRequestDtoDummy, criteriaBuilder, rootCryptoKeyItem);
        Assertions.assertInstanceOf(SqmLikePredicate.class, predicateTest);

        final String predicateHqlString = ((SqmLikePredicate) predicateTest).getPattern().toHqlString();
        Assertions.assertTrue(predicateHqlString.startsWith("%"));
        Assertions.assertTrue(predicateHqlString.endsWith("%"));
    }


    @Test
    public void testFilterMetaOnly() {
        final List<SearchFieldObject> testSearchableFieldsList = new ArrayList<>();
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.COMMON_NAME.name(), AttributeContentType.STRING, AttributeType.META));

        List<SearchFilterRequestDto> testFilters = new ArrayList<>();
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.COMMON_NAME, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.META, SearchableFields.COMMON_NAME, AttributeContentType.STRING, FilterConditionOperator.EQUALS, "test"));

        final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject
                    = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(testSearchableFieldsList, testFilters,  criteriaBuilder, Resource.CERTIFICATE);
        Assertions.assertEquals(1, criteriaQueryDataObject.getPredicate().getExpressions().size());

    }


    @Test
    public void testFilterCustomAttrOnly() {

        final List<SearchFieldObject> testSearchableFieldsList = new ArrayList<>();
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.COMMON_NAME.name(), AttributeContentType.STRING, AttributeType.META));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.NOT_AFTER.name(), AttributeContentType.DATE, AttributeType.META));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.SERIAL_NUMBER.name(), AttributeContentType.STRING, AttributeType.CUSTOM));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.CKI_LENGTH.name(), AttributeContentType.INTEGER, AttributeType.CUSTOM));

        List<SearchFilterRequestDto> testFilters = new ArrayList<>();
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.SUBJECTDN, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.COMMON_NAME, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.CUSTOM, SearchableFields.SERIAL_NUMBER, AttributeContentType.STRING, FilterConditionOperator.EQUALS, "test"));

        final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject
                = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(testSearchableFieldsList, testFilters,  criteriaBuilder, Resource.CERTIFICATE);
        Assertions.assertEquals(1, criteriaQueryDataObject.getPredicate().getExpressions().size());


        List<SearchFilterRequestDto> testFilters2 = new ArrayList<>();
        testFilters2.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.COMMON_NAME, FilterConditionOperator.EQUALS, "test"));
        testFilters2.add(new SearchFilterRequestDTODummy(FilterFieldSource.CUSTOM, SearchableFields.CKI_LENGTH, AttributeContentType.INTEGER, FilterConditionOperator.EQUALS, 1));
        testFilters2.add(new SearchFilterRequestDTODummy(FilterFieldSource.CUSTOM, SearchableFields.SERIAL_NUMBER, AttributeContentType.STRING, FilterConditionOperator.EQUALS, "test"));
        final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject2
                = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(testSearchableFieldsList, testFilters2,  criteriaBuilder, Resource.CERTIFICATE);
        Assertions.assertEquals(2, criteriaQueryDataObject2.getPredicate().getExpressions().size());

    }

    @Test
    public void testFilterNoMetaOrCustomAttr() {

        final List<SearchFieldObject> testSearchableFieldsList = new ArrayList<>();
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.COMMON_NAME.name(), AttributeContentType.STRING, AttributeType.META));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.NOT_AFTER.name(), AttributeContentType.DATE, AttributeType.META));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.SERIAL_NUMBER.name(), AttributeContentType.STRING, AttributeType.CUSTOM));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.CKI_LENGTH.name(), AttributeContentType.INTEGER, AttributeType.META));

        List<SearchFilterRequestDto> testFilters = new ArrayList<>();
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.SUBJECTDN, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.COMMON_NAME, FilterConditionOperator.EQUALS, "test"));

        final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject
                = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(testSearchableFieldsList, testFilters,  criteriaBuilder, Resource.CERTIFICATE);
        Assertions.assertEquals(0, criteriaQueryDataObject.getPredicate().getExpressions().size());
    }

    @Test
    public void testFilterNoMetaOrCustomAttrWithCorrectAttrContentType() {

        final List<SearchFieldObject> testSearchableFieldsList = new ArrayList<>();
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.COMMON_NAME.name(), AttributeContentType.STRING, AttributeType.META));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.NOT_AFTER.name(), AttributeContentType.DATE, AttributeType.META));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.SERIAL_NUMBER.name(), AttributeContentType.STRING, AttributeType.CUSTOM));
        testSearchableFieldsList.add(new SearchFieldObject(SearchableFields.CKI_LENGTH.name(), AttributeContentType.INTEGER, AttributeType.META));

        List<SearchFilterRequestDto> testFilters = new ArrayList<>();
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.SUBJECTDN, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, SearchableFields.COMMON_NAME, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.META, SearchableFields.CKI_LENGTH, AttributeContentType.STRING, FilterConditionOperator.EQUALS, 1));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.CUSTOM, SearchableFields.SERIAL_NUMBER, AttributeContentType.INTEGER, FilterConditionOperator.EQUALS, "test"));

        final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject
                = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(testSearchableFieldsList, testFilters,  criteriaBuilder, Resource.CERTIFICATE);
        Assertions.assertEquals(0, criteriaQueryDataObject.getPredicate().getExpressions().size());
    }


    private void testLikePredicate(final Predicate predicate, final String value) {
        Assertions.assertInstanceOf(SqmLikePredicate.class, predicate);
        Assertions.assertEquals(value, ((SqmLikePredicate) predicate).getPattern().toHqlString());
    }

    private void testVerifications(final SearchableFields fieldTest, final FilterConditionOperator condition, final CertificateValidationStatus certificateValidationCheckStatus) {
        final SearchFilterRequestDTODummy searchFilterRequestDTODummy
                = new SearchFilterRequestDTODummy(fieldTest, condition, certificateValidationCheckStatus.getCode());
        final Predicate predicateTest = Sql2PredicateConverter.mapSearchFilter2Predicate(searchFilterRequestDTODummy, criteriaBuilder, root);
        Assertions.assertInstanceOf(SqmLikePredicate.class, predicateTest);

        final String hqlString = ((SqmLikePredicate) predicateTest).getPattern().toHqlString();
        Assertions.assertTrue(hqlString.contains(TEST_VERIFICATION_TEXT.replace("%STATUS%", certificateValidationCheckStatus.getCode())));
        Assertions.assertTrue(hqlString.startsWith("%"));
        Assertions.assertTrue(hqlString.endsWith("%"));
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
