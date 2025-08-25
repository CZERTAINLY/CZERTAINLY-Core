package com.czertainly.core.util;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.certificate.CertificateResponseDto;
import com.czertainly.api.model.client.certificate.CertificateSearchRequestDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.attribute.v2.content.*;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateRelationType;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateSubjectType;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.CertificateService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.query.sqm.function.SelfRenderingSqmFunction;
import org.hibernate.query.sqm.tree.predicate.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tests for class {@link FilterPredicatesBuilder}
 */
@SpringBootTest
class FilterPredicatesBuilderTest extends BaseSpringBootTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AttributeService attributeService;

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;

    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    @Autowired
    private CertificateRelationRepository certificateRelationRepository;

    private CriteriaBuilder criteriaBuilder;

    private Certificate certificate1;
    private Certificate certificate2;
    private Certificate certificate3;

    private CriteriaQuery<Certificate> criteriaQuery;

    private Root<Certificate> root;

    private final String testValue = "test";
    private final String testDateValue = "2022-01-01T20:28:33.213+00:00";

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:10002");
    }


    @BeforeEach
    public void prepare() throws AlreadyExistException, AttributeException, NotFoundException {

        certificate1 = new Certificate();
        certificate1.setSubjectType(CertificateSubjectType.ROOT_CA);
        certificate2 = new Certificate();
        certificate2.setSubjectType(CertificateSubjectType.END_ENTITY);
        certificate3 = new Certificate();
        certificate3.setSubjectType(CertificateSubjectType.END_ENTITY);
        certificateRepository.saveAll(List.of(certificate1, certificate2, certificate3));

        CustomAttributeDefinitionDetailDto intAttribute = createCustomAttribute("integer", AttributeContentType.INTEGER);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, intAttribute.getName(), List.of(new IntegerAttributeContent("ref", 1)));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, intAttribute.getName(), List.of(new IntegerAttributeContent("ref", 2)));

        CustomAttributeDefinitionDetailDto decimalAttribute = createCustomAttribute("decimal", AttributeContentType.FLOAT);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, decimalAttribute.getName(), List.of(new FloatAttributeContent("ref", 0.5f)));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, decimalAttribute.getName(), List.of(new FloatAttributeContent("ref", 1.5f)));


        CustomAttributeDefinitionDetailDto booleanAttribute = createCustomAttribute("boolean", AttributeContentType.BOOLEAN);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, booleanAttribute.getName(), List.of(new BooleanAttributeContent("ref", true)));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, booleanAttribute.getName(), List.of(new BooleanAttributeContent("ref", false)));

        CustomAttributeDefinitionDetailDto attribute = createCustomAttribute("string", AttributeContentType.STRING);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, attribute.getName(), List.of(new StringAttributeContent("ref", "value1")));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, attribute.getName(), List.of(new StringAttributeContent("ref", "value2")));

        CustomAttributeDefinitionDetailDto dateAttribute = createCustomAttribute("date", AttributeContentType.DATE);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, dateAttribute.getName(), List.of(new DateAttributeContent(LocalDate.parse("2025-05-16"))));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, dateAttribute.getName(), List.of(new DateAttributeContent(LocalDate.parse("2025-05-20"))));

        CustomAttributeDefinitionDetailDto dateTimeAttribute = createCustomAttribute("datetime", AttributeContentType.DATETIME);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, dateTimeAttribute.getName(), List.of(new DateTimeAttributeContent(ZonedDateTime.parse("2018-12-26T20:28:33.213+05:30"))));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, dateTimeAttribute.getName(), List.of(new DateTimeAttributeContent(ZonedDateTime.parse("2018-12-28T20:28:33.213+05:30"))));

        CustomAttributeDefinitionDetailDto timeAttribute = createCustomAttribute("time", AttributeContentType.TIME);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, timeAttribute.getName(), List.of(new TimeAttributeContent(LocalTime.parse("10:15:45"))));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, timeAttribute.getName(), List.of(new TimeAttributeContent(LocalTime.parse("11:15:45"))));

        criteriaBuilder = entityManager.getCriteriaBuilder();
        criteriaQuery = criteriaBuilder.createQuery(Certificate.class);
        root = criteriaQuery.from(Certificate.class);
    }

    @Test
    void testEqualsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.EQUALS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmComparisonPredicate.class, predicateTest);
        Assertions.assertEquals(ComparisonOperator.EQUAL, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(testValue, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    void testNotEqualsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.NOT_EQUALS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmJunctionPredicate.class, predicateTest);

        final SqmJunctionPredicate sqmJunctionPredicate = ((SqmJunctionPredicate) predicateTest);
        for (final SqmPredicate predicate : sqmJunctionPredicate.getPredicates()) {
            Assertions.assertTrue(predicate instanceof SqmComparisonPredicate || predicate instanceof SqmNullnessPredicate);
            if (predicate instanceof SqmComparisonPredicate) {
                Assertions.assertEquals(ComparisonOperator.NOT_EQUAL, ((SqmComparisonPredicate) predicate).getSqmOperator());
                Assertions.assertEquals(testValue, ((SqmComparisonPredicate) predicate).getRightHandExpression().toHqlString());
            } else {
                Assertions.assertFalse(predicate.isNull().isNegated());
            }
        }
    }

    @Test
    void testMatchesPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.MATCHES)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmComparisonPredicate.class, predicateTest);
        SqmComparisonPredicate comparisonPredicateTest = (SqmComparisonPredicate) predicateTest;
        Assertions.assertEquals(ComparisonOperator.EQUAL, comparisonPredicateTest.getSqmOperator());
        Assertions.assertEquals("true", comparisonPredicateTest.getRightHandExpression().toHqlString());
        Assertions.assertInstanceOf(SelfRenderingSqmFunction.class, comparisonPredicateTest.getLeftHandExpression());
        SelfRenderingSqmFunction<?> leftHandExpressionHandExpression = (SelfRenderingSqmFunction<?>) comparisonPredicateTest.getLeftHandExpression();
        Assertions.assertEquals("textregexeq", leftHandExpressionHandExpression.getFunctionName());
        Assertions.assertEquals("'" + testValue + "'", leftHandExpressionHandExpression.getArguments().getLast().toHqlString());
    }

    @Test
    void testContainsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.CONTAINS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, "%" + testValue + "%");
    }

    @Test
    void testNotContainsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.NOT_CONTAINS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmJunctionPredicate.class, predicateTest);

        final SqmJunctionPredicate sqmJunctionPredicate = ((SqmJunctionPredicate) predicateTest);
        for (final SqmPredicate predicate : sqmJunctionPredicate.getPredicates()) {
            Assertions.assertTrue(predicate instanceof SqmLikePredicate || predicate instanceof SqmNullnessPredicate);
            if (predicate instanceof SqmLikePredicate) {
                Assertions.assertTrue(predicate.isNegated());
                Assertions.assertEquals("%" + testValue + "%", ((SqmLikePredicate) predicate).getPattern().toHqlString());
            } else {
                Assertions.assertFalse(predicate.isNull().isNegated());
            }
        }
    }

    @Test
    void testStartWithPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.STARTS_WITH)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, testValue + "%");
    }

    @Test
    void testEndWithPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.ENDS_WITH)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, "%" + testValue);
    }

    @Test
    void testEmptyPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.EMPTY)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmNullnessPredicate.class, predicateTest);
        Assertions.assertFalse(predicateTest.isNull().isNegated());
    }

    @Test
    void testNotEmptyPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.NOT_EMPTY)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertInstanceOf(SqmNullnessPredicate.class, predicateTest);
        Assertions.assertTrue(predicateTest.isNotNull().isNegated());
    }

    @Test
    void testGreaterPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.GREATER)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertEquals(ComparisonOperator.GREATER_THAN, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(getFormattedDate(), ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    void testLesserPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.LESSER)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertEquals(ComparisonOperator.LESS_THAN, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(getFormattedDate(), ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @NotNull
    private String getFormattedDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return OffsetDateTime.parse(testDateValue, formatter).toLocalDateTime().toString();
    }

    @Test
    void testCombinedFiltersPredicate() {
        List<SearchFilterRequestDto> testFilters = new ArrayList<>();
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, FilterField.SUBJECTDN, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME, FilterConditionOperator.EQUALS, "test"));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.META, FilterField.CKI_LENGTH, AttributeContentType.STRING, FilterConditionOperator.EQUALS, 1));
        testFilters.add(new SearchFilterRequestDTODummy(FilterFieldSource.CUSTOM, FilterField.SERIAL_NUMBER, AttributeContentType.INTEGER, FilterConditionOperator.NOT_EQUALS, "123"));

        final SqmJunctionPredicate filterPredicate = (SqmJunctionPredicate) FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, testFilters);
        Assertions.assertEquals(4, (filterPredicate.getPredicates().size()));

        Assertions.assertInstanceOf(SqmExistsPredicate.class, filterPredicate.getPredicates().get(2));
        Assertions.assertInstanceOf(SqmExistsPredicate.class, filterPredicate.getPredicates().get(3));
        Assertions.assertTrue(filterPredicate.getPredicates().get(3).isNegated());
    }

    @Test
    void testEnumPropertyFilter() {
        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SUBJECT_TYPE.name(), FilterConditionOperator.EQUALS, CertificateSubjectType.ROOT_CA.getCode())));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SUBJECT_TYPE.name(), FilterConditionOperator.NOT_EQUALS, CertificateSubjectType.ROOT_CA.getCode())));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testIntegerAttribute() {

        final String ATTR_IDENTIFIER = "integer|INTEGER";
        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EQUALS, 1)));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EQUALS, 1)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER, 1)));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER_OR_EQUAL, 1)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER, 2)));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER_OR_EQUAL, 2)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testDecimalAttribute() {
        final String ATTR_IDENTIFIER = "decimal|FLOAT";
        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EQUALS, 0.5f)));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EQUALS, 0.5f)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER, 0.5f)));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER_OR_EQUAL, 0.5f)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER, 1.5f)));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER_OR_EQUAL, 1.5f)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testBooleanAttribute() {
        final String ATTR_IDENTIFIER = "boolean|BOOLEAN";
        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EQUALS, true)));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EQUALS, true)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testStringAttribute() {

        final String ATTR_IDENTIFIER = "string|STRING";

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EQUALS, "value1")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.CONTAINS, "value")));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_CONTAINS, "1")));
        Assertions.assertEquals(Set.of(certificate3.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EQUALS, "value1")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.STARTS_WITH, "v")));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.ENDS_WITH, "1")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.MATCHES, "^value[12]$")));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.MATCHES, "^\\\\d"))); // starts with a number
        Assertions.assertEquals(Set.of(), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_MATCHES, "^value[12]$")));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_MATCHES, "^\\\\d"))); // starts with a number
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_MATCHES, "[abc"))); // starts with a number
        Assertions.assertThrows(ValidationException.class, () -> certificateService.listCertificates(new SecurityFilter(), searchRequestDto));
    }

    @Test
    void testDateAttribute() {

        final String ATTR_IDENTIFIER = "date|DATE";

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EQUALS, "2025-05-16")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EQUALS, "2025-05-16")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER, "2025-05-16")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER_OR_EQUAL, "2025-05-16")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER, "2025-05-20")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER_OR_EQUAL, "2025-05-20")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testDateTimeAttribute() {

        final String ATTR_IDENTIFIER = "datetime|DATETIME";

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EQUALS, "2018-12-26T20:28:33.213+05:30")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EQUALS, "2018-12-26T20:28:33.213+05:30")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER, "2018-12-26T20:28:33.213+05:30")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER_OR_EQUAL, "2018-12-26T20:28:33.213+05:30")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER, "2018-12-28T20:28:33.213+05:30")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER_OR_EQUAL, "2018-12-28T20:28:33.213+05:30")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testTimeAttribute() {

        final String ATTR_IDENTIFIER = "time|TIME";

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EQUALS, "10:15:45")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EQUALS, "10:15:45")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER, "10:15:45")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.GREATER_OR_EQUAL, "10:15:45")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER, "11:15:45")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.LESSER_OR_EQUAL, "11:15:45")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, ATTR_IDENTIFIER, FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }


    @Test
    void testFiltersOnGroups() throws NotFoundException {
        Group group1 = new Group();
        group1.setName("group 1");
        Group group2 = new Group();
        group2.setName("group 2");
        groupRepository.saveAll(List.of(group1, group2));

        certificateService.updateCertificateGroups(certificate1.getSecuredUuid(), Set.of(group1.getUuid()));
        certificateService.updateCertificateGroups(certificate2.getSecuredUuid(), Set.of(group1.getUuid(), group2.getUuid()));

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.GROUP_NAME.name(), FilterConditionOperator.EQUALS, (Serializable) List.of("group 1"))));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testFiltersOnOwners() throws NotFoundException {
        WireMockServer mockServer = new WireMockServer(10002);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users/[^/]+")).willReturn(WireMock.okJson("{ \"username\": \"owner1\"}")));

        certificateService.updateOwner(certificate1.getSecuredUuid(), String.valueOf(UUID.randomUUID()));

        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users/[^/]+")).willReturn(WireMock.okJson("{ \"username\": \"owner2\"}")));

        certificateService.updateOwner(certificate2.getSecuredUuid(), String.valueOf(UUID.randomUUID()));

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.EQUALS, (Serializable) List.of("owner1"))));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.EQUALS, (Serializable) List.of("owner1", "owner2"))));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of("owner1"))));
        Assertions.assertEquals(Set.of(certificate3.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of("owner1", "owner2"))));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testStringProperty() {
        certificate1.setCommonName("name1");
        certificate2.setCommonName("name2");
        certificateRepository.save(certificate1);
        certificateRepository.save(certificate2);

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.EQUALS, "name1")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.CONTAINS, "name")));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.NOT_CONTAINS, "1")));
        Assertions.assertEquals(Set.of(certificate3.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.NOT_EQUALS, "name1")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.STARTS_WITH, "n")));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.ENDS_WITH, "2")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.MATCHES, "^name[12]$")));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.MATCHES, ".*\\d$"))); // ends with a number
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.NOT_MATCHES, "^name[12]$")));
        Assertions.assertEquals(Set.of(), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.NOT_MATCHES, "^\\\\d"))); // stars with a number
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.MATCHES, "[abc"))); // invalid regex
        Assertions.assertThrows(ValidationException.class, () -> certificateService.listCertificates(new SecurityFilter(), searchRequestDto));

    }

    @Test
    void testEnumProperty() {
        certificate1.setState(CertificateState.FAILED);
        certificate2.setState(CertificateState.REVOKED);
        certificateRepository.save(certificate1);
        certificateRepository.save(certificate2);

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(CertificateState.FAILED.getCode()))));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(CertificateState.FAILED.getCode(), CertificateState.REVOKED.getCode()))));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of(CertificateState.FAILED.getCode()))));
        Assertions.assertEquals(Set.of(certificate3.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of(CertificateState.FAILED.getCode(), CertificateState.REVOKED.getCode()))));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

    }

    @Test
    void testDateTimeProperty() throws ParseException {
        certificate1.setNotAfter(new SimpleDateFormat(("yyyy-MM-dd HH:mm:ss")).parse("2025-05-16 22:10:15"));
        certificate2.setNotAfter(new SimpleDateFormat(("yyyy-MM-dd HH:mm:ss")).parse("2025-05-20 22:10:15"));
        certificateRepository.save(certificate1);
        certificateRepository.save(certificate2);

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.EQUALS, "2025-05-16T22:10:15Z")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.NOT_EQUALS, "2025-05-16T22:10:15Z")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.GREATER, "2025-05-16T22:10:15Z")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.GREATER_OR_EQUAL, "2025-05-16T22:10:15Z")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.LESSER, "2025-05-20T22:10:15Z")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.LESSER_OR_EQUAL, "2025-05-20T22:10:15Z")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
    }

    @Test
    void testDurationFiltersOnProperty() {
        certificate1.setNotAfter(convertToDateViaInstant(LocalDateTime.now().plusDays(10)));
        certificate2.setNotAfter(convertToDateViaInstant(LocalDateTime.now().plusDays(2)));
        certificate3.setNotAfter(convertToDateViaInstant(LocalDateTime.now().minusDays(4)));
        certificateRepository.saveAll(List.of(certificate1, certificate2, certificate3));

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        String duration = "P3DT2H";
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.IN_NEXT, duration)));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        duration = "P1MT2H";
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.IN_NEXT, duration)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        duration = "P5D";
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.IN_PAST, duration)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        certificate1.setNotAfter(convertToDateViaInstant(LocalDateTime.now().minusMinutes(1)));
        certificateRepository.save(certificate1);
        duration = "P1D";
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.IN_NEXT, duration)));
        Assertions.assertEquals(Set.of(), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        certificate1.setNotAfter(convertToDateViaInstant(LocalDateTime.now().plusMinutes(1)));
        certificateRepository.save(certificate1);
        duration = "P1D";
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.IN_PAST, duration)));
        Assertions.assertEquals(Set.of(), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        duration = "invalid";
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.IN_PAST, duration)));
        SecurityFilter securityFilter = new SecurityFilter();
        Assertions.assertThrows(ValidationException.class, () -> certificateService.listCertificates(securityFilter, searchRequestDto));
    }

    @Test
    void testDurationFilterOnAttributes() throws AlreadyExistException, AttributeException, NotFoundException {

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();

        final String DATE_ATTR_IDENTIFIER = "date_duration|DATE";

        CustomAttributeDefinitionDetailDto dateAttribute = createCustomAttribute("date_duration", AttributeContentType.DATE);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, dateAttribute.getName(), List.of(new DateAttributeContent(LocalDate.now().plusDays(3))));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, dateAttribute.getName(), List.of(new DateAttributeContent(LocalDate.now().minusDays(3))));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, DATE_ATTR_IDENTIFIER, FilterConditionOperator.IN_NEXT, "P4D")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, DATE_ATTR_IDENTIFIER, FilterConditionOperator.IN_PAST, "P4D")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        final String DATETIME_ATTR_IDENTIFIER = "datetime_duration|DATETIME";

        CustomAttributeDefinitionDetailDto dateTimeAttribute = createCustomAttribute("datetime_duration", AttributeContentType.DATETIME);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, dateTimeAttribute.getName(), List.of(new DateTimeAttributeContent(ZonedDateTime.now().plusDays(3).plusHours(5))));
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate2.getUuid(), null, dateTimeAttribute.getName(), List.of(new DateTimeAttributeContent(ZonedDateTime.now().minusDays(3).minusHours(5))));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, DATETIME_ATTR_IDENTIFIER, FilterConditionOperator.IN_NEXT, "P3DT6H")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.CUSTOM, DATE_ATTR_IDENTIFIER, FilterConditionOperator.IN_PAST, "P4D")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

    }

    private Date convertToDateViaInstant(LocalDateTime dateToConvert) {
        return java.util.Date
                .from(dateToConvert.atZone(ZoneId.systemDefault())
                        .toInstant());
    }

    @Test
    void testHasPrivateKey() {
        CryptographicKey cryptographicKey = new CryptographicKey();
        cryptographicKey = cryptographicKeyRepository.save(cryptographicKey);
        CryptographicKeyItem cryptographicKeyItem = new CryptographicKeyItem();
        cryptographicKeyItem.setType(KeyType.PRIVATE_KEY);
        cryptographicKeyItem.setKey(cryptographicKey);
        cryptographicKeyItem.setState(KeyState.ACTIVE);
        cryptographicKeyItemRepository.save(cryptographicKeyItem);

        cryptographicKey.getItems().add(cryptographicKeyItem);
        cryptographicKey = cryptographicKeyRepository.save(cryptographicKey);
        certificate1.setKey(cryptographicKey);
        certificateRepository.save(certificate1);

        System.out.println("Key UUID: " + cryptographicKey.getUuid());
        System.out.println("Certificate Key UUID: " + certificate1.getKey().getUuid());

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.PRIVATE_KEY.name(), FilterConditionOperator.EQUALS, true)));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

    }

    @Test
    void testListProperty() {
        certificate1.setKeySize(1);
        certificate2.setKeySize(2);
        certificateRepository.save(certificate1);
        certificateRepository.save(certificate2);

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.KEY_SIZE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(1))));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.KEY_SIZE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(1, 2))));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.KEY_SIZE.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of(1))));
        Assertions.assertEquals(Set.of(certificate3.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.KEY_SIZE.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of(1, 2))));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.KEY_SIZE.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.KEY_SIZE.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

    }

    @Test
    void testIncludeArchived() {
        certificate1.setArchived(true);
        certificateRepository.save(certificate1);
        int allCertificates = certificateRepository.findAll().size();
        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setIncludeArchived(true);
        Assertions.assertEquals(allCertificates, certificateService.listCertificates(new SecurityFilter(), searchRequestDto).getCertificates().size());
        searchRequestDto.setIncludeArchived(false);
        Assertions.assertEquals(allCertificates - 1, certificateService.listCertificates(new SecurityFilter(), searchRequestDto).getCertificates().size());
    }

    @Test
    void testBooleanProperty() {
        certificate1.setTrustedCa(true);
        certificate2.setTrustedCa(false);
        certificateRepository.save(certificate1);
        certificateRepository.save(certificate2);

        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.TRUSTED_CA.name(), FilterConditionOperator.EQUALS, true)));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.TRUSTED_CA.name(), FilterConditionOperator.NOT_EQUALS, true)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.TRUSTED_CA.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.TRUSTED_CA.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

    }

    @Test
    void testCertificateRelationsFilter() {
        CertificateRelation certificateRelation = new CertificateRelation();
        certificateRelation.setSuccessorCertificate(certificate1);
        certificateRelation.setPredecessorCertificate(certificate2);
        certificateRelation.setRelationType(CertificateRelationType.RENEWAL);
        certificateRelationRepository.save(certificateRelation);
        CertificateSearchRequestDto searchRequestDto = new CertificateSearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.PREDECESSOR_RELATION_TYPE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(CertificateRelationType.RENEWAL.getCode()))));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.SUCCESSOR_RELATION_TYPE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(CertificateRelationType.RENEWAL.getCode()))));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

    }


    private Set<UUID> getUuidsFromListCertificatesResponse(CertificateResponseDto certificateResponseDto) {
        return certificateResponseDto.getCertificates().stream().map(c -> UUID.fromString(c.getUuid())).collect(Collectors.toSet());
    }


    private void testLikePredicate(final Predicate predicate, final String value) {
        Assertions.assertInstanceOf(SqmLikePredicate.class, predicate);
        Assertions.assertEquals(value, ((SqmLikePredicate) predicate).getPattern().toHqlString());
    }


    private SearchFilterRequestDTODummy prepareDummyFilterRequest(final FilterConditionOperator condition) {
        SearchFilterRequestDTODummy dummy;
        switch (condition) {
            case EQUALS ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.EQUALS, testValue);
            case NOT_EQUALS ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.NOT_EQUALS, testValue);
            case CONTAINS ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.CONTAINS, testValue);
            case NOT_CONTAINS ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.NOT_CONTAINS, testValue);
            case STARTS_WITH ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.STARTS_WITH, testValue);
            case ENDS_WITH ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.ENDS_WITH, testValue);
            case EMPTY ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.EMPTY, testValue);
            case NOT_EMPTY ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.NOT_EMPTY, testValue);
            case GREATER ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.NOT_AFTER, FilterConditionOperator.GREATER, testDateValue);
            case GREATER_OR_EQUAL ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.NOT_AFTER, FilterConditionOperator.GREATER_OR_EQUAL, testDateValue);
            case LESSER ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.NOT_BEFORE, FilterConditionOperator.LESSER, testDateValue);
            case LESSER_OR_EQUAL ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.NOT_AFTER, FilterConditionOperator.LESSER_OR_EQUAL, testDateValue);
            case MATCHES ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.MATCHES, testValue);
            case NOT_MATCHES ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.NOT_MATCHES, testValue);
            default -> dummy = null;
        }
        return dummy;
    }

    private CustomAttributeDefinitionDetailDto createCustomAttribute(String name, AttributeContentType contentType) throws AlreadyExistException, AttributeException {
        CustomAttributeCreateRequestDto customAttributeRequest = new CustomAttributeCreateRequestDto();
        customAttributeRequest.setName(name);
        customAttributeRequest.setLabel(name);
        customAttributeRequest.setResources(List.of(Resource.CERTIFICATE));
        customAttributeRequest.setContentType(contentType);

        return attributeService.createCustomAttribute(customAttributeRequest);
    }


}

class SearchFilterRequestDTODummy extends SearchFilterRequestDto {

    private FilterField fieldTest;
    private FilterConditionOperator conditionTest;
    private Serializable valueTest;
    private FilterFieldSource filterFieldSource;
    private String fieldIdentifier;

    public SearchFilterRequestDTODummy(FilterField fieldTest, FilterConditionOperator conditionTest, Serializable valueTest) {
        this.fieldTest = fieldTest;
        this.conditionTest = conditionTest;
        this.valueTest = valueTest;
        this.fieldIdentifier = fieldTest.name();
        this.filterFieldSource = FilterFieldSource.PROPERTY;
    }

    public SearchFilterRequestDTODummy(FilterFieldSource filterFieldSource, FilterField fieldTest, FilterConditionOperator conditionTest, Serializable valueTest) {
        this.filterFieldSource = filterFieldSource;
        this.fieldTest = fieldTest;
        this.conditionTest = conditionTest;
        this.valueTest = valueTest;
        this.fieldIdentifier = fieldTest.name();
    }

    public SearchFilterRequestDTODummy(FilterFieldSource filterFieldSource, FilterField fieldTest, AttributeContentType attributeContentType, FilterConditionOperator conditionTest, Serializable valueTest) {
        this.filterFieldSource = filterFieldSource;
        this.fieldTest = fieldTest;
        this.conditionTest = conditionTest;
        this.valueTest = valueTest;
        this.fieldIdentifier = fieldTest.name() + "|" + attributeContentType.name();
    }

    public FilterField getField() {
        return fieldTest;
    }

    @Override
    public FilterConditionOperator getCondition() {
        return conditionTest;
    }

    @Override
    public Serializable getValue() {
        return valueTest;
    }

    public void setFieldTest(FilterField fieldTest) {
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
