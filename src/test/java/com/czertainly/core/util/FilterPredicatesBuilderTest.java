package com.czertainly.core.util;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.certificate.CertificateResponseDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BooleanAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.GroupRepository;
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
import org.hibernate.query.sqm.tree.predicate.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    private CriteriaBuilder criteriaBuilder;

    private Certificate certificate1;
    private Certificate certificate2;
    private Certificate certificate3;

    private CriteriaQuery<Certificate> criteriaQuery;

    private Root<Certificate> root;

    private final String TEST_VALUE = "test";
    private final String TEST_DATE_VALUE = "2022-01-01";

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:10001");
    }


    @BeforeEach
    public void prepare() throws AlreadyExistException, AttributeException, NotFoundException {

        certificate1 = new Certificate();
        certificate2 = new Certificate();
        certificate3 = new Certificate();
        certificateRepository.saveAll(List.of(certificate1, certificate2, certificate3));

        CustomAttributeDefinitionDetailDto booleanAttribute = createCustomAttribute("boolean", AttributeContentType.BOOLEAN);
        attributeEngine.updateObjectCustomAttributeContent(Resource.CERTIFICATE, certificate1.getUuid(), null, booleanAttribute.getName(), List.of(new BooleanAttributeContent("ref", true)));


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
        Assertions.assertEquals(TEST_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
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
                Assertions.assertEquals(TEST_VALUE, ((SqmComparisonPredicate) predicate).getRightHandExpression().toHqlString());
            } else {
                Assertions.assertFalse(predicate.isNull().isNegated());
            }
        }
    }

    @Test
    void testContainsPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.CONTAINS)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, "%" + TEST_VALUE + "%");
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
                Assertions.assertEquals("%" + TEST_VALUE + "%", ((SqmLikePredicate) predicate).getPattern().toHqlString());
            } else {
                Assertions.assertFalse(predicate.isNull().isNegated());
            }
        }
    }

    @Test
    void testStartWithPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.STARTS_WITH)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, TEST_VALUE + "%");
    }

    @Test
    void testEndWithPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.ENDS_WITH)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        testLikePredicate(predicateTest, "%" + TEST_VALUE);
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
        Assertions.assertEquals(TEST_DATE_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    void testLesserPredicate() {
        final Predicate filterPredicate = FilterPredicatesBuilder.getFiltersPredicate(criteriaBuilder, criteriaQuery, root, List.of(prepareDummyFilterRequest(FilterConditionOperator.LESSER)));
        Predicate predicateTest = ((SqmJunctionPredicate) filterPredicate).getPredicates().getFirst();
        Assertions.assertEquals(ComparisonOperator.LESS_THAN, ((SqmComparisonPredicate) predicateTest).getSqmOperator());
        Assertions.assertEquals(TEST_DATE_VALUE, ((SqmComparisonPredicate) predicateTest).getRightHandExpression().toHqlString());
    }

    @Test
    void testCombinedFilters() {
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
    @Disabled("In process of being fixed")
    void testBooleanAttribute() {
        SearchFilterRequestDto filterRequestDto = new SearchFilterRequestDto(FilterFieldSource.CUSTOM, "boolean|BOOLEAN", FilterConditionOperator.EQUALS, true);
        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(List.of(filterRequestDto));
        CertificateResponseDto certificates = certificateService.listCertificates(new SecurityFilter(), searchRequestDto);
        Assertions.assertEquals(Set.of(certificate1.getUuid()), new HashSet<>(getUuidsFromListCertificatesResponse(certificates)));
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

        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.GROUP_NAME.name(), FilterConditionOperator.EQUALS, (Serializable) List.of("group 1"))));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()),new HashSet<>(getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto))));
    }

    @Test
    void testFiltersOnOwners() throws NotFoundException {
        WireMockServer mockServer = new WireMockServer(10001);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users/[^/]+")).willReturn(
                WireMock.okJson("{ \"username\": \"owner1\"}")
        ));

        certificateService.updateOwner(certificate1.getSecuredUuid(), String.valueOf(UUID.randomUUID()));

        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users/[^/]+")).willReturn(
                WireMock.okJson("{ \"username\": \"owner2\"}")
        ));

        certificateService.updateOwner(certificate2.getSecuredUuid(), String.valueOf(UUID.randomUUID()));

        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.EQUALS, (Serializable) List.of("owner1"))));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        SearchRequestDto searchRequestDto2 = new SearchRequestDto();
        searchRequestDto2.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.EQUALS, (Serializable) List.of("owner1", "owner2"))));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()),getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto2)));

        SearchRequestDto searchRequestDto3 = new SearchRequestDto();
        searchRequestDto3.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of("owner1"))));
        Assertions.assertEquals(Set.of(certificate3.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto3)));

        SearchRequestDto searchRequestDto6 = new SearchRequestDto();
        searchRequestDto6.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of("owner1", "owner2"))));
        Assertions.assertEquals(Set.of(certificate3.getUuid()),getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto6)));

        SearchRequestDto searchRequestDto4 = new SearchRequestDto();
        searchRequestDto4.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto4)));

        SearchRequestDto searchRequestDto5 = new SearchRequestDto();
        searchRequestDto5.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.OWNER.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto5)));
    }

    @Test
    void testStringProperty() {
        certificate1.setCommonName("name1");
        certificate2.setCommonName("name2");

        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.EQUALS, "name1")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        SearchRequestDto searchRequestDto2 = new SearchRequestDto();
        searchRequestDto2.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.CONTAINS, "name")));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto2)));

        SearchRequestDto searchRequestDto3 = new SearchRequestDto();
        searchRequestDto3.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.NOT_CONTAINS, "1")));
        Assertions.assertEquals(Set.of(certificate3.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto3)));

        SearchRequestDto searchRequestDto4 = new SearchRequestDto();
        searchRequestDto4.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.NOT_EQUALS, "name1")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto4)));

        SearchRequestDto searchRequestDto5 = new SearchRequestDto();
        searchRequestDto5.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto5)));

        SearchRequestDto searchRequestDto6 = new SearchRequestDto();
        searchRequestDto6.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto6)));

        SearchRequestDto searchRequestDto7 = new SearchRequestDto();
        searchRequestDto7.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.STARTS_WITH, "n")));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto7)));

        SearchRequestDto searchRequestDto8 = new SearchRequestDto();
        searchRequestDto8.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.COMMON_NAME.name(), FilterConditionOperator.ENDS_WITH, "2")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto8)));

    }

    @Test
    void testEnumProperty() {
        certificate1.setState(CertificateState.FAILED);
        certificate2.setState(CertificateState.REVOKED);

        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(CertificateState.FAILED.getCode()))));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        SearchRequestDto searchRequestDto2 = new SearchRequestDto();
        searchRequestDto2.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(CertificateState.FAILED.getCode(), CertificateState.REVOKED.getCode()))));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()),getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto2)));

        SearchRequestDto searchRequestDto3 = new SearchRequestDto();
        searchRequestDto3.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of(CertificateState.FAILED.getCode()))));
        Assertions.assertEquals(Set.of(certificate3.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto3)));

        SearchRequestDto searchRequestDto6 = new SearchRequestDto();
        searchRequestDto6.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.NOT_EQUALS, (Serializable) List.of(CertificateState.FAILED.getCode(), CertificateState.REVOKED.getCode()))));
        Assertions.assertEquals(Set.of(certificate3.getUuid()),getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto6)));

        SearchRequestDto searchRequestDto4 = new SearchRequestDto();
        searchRequestDto4.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto4)));

        SearchRequestDto searchRequestDto5 = new SearchRequestDto();
        searchRequestDto5.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.CERTIFICATE_STATE.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto5)));

    }

    @Test
    void testDateProperty() throws ParseException {
        certificate1.setNotAfter(new SimpleDateFormat(("yyyy-MM-dd HH:mm:ss")).parse("2025-05-16 22:10:15"));
        certificate2.setNotAfter(new SimpleDateFormat(("yyyy-MM-dd HH:mm:ss")).parse("2025-05-20 22:10:15"));

        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.EQUALS, "2025-05-16")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto)));

        SearchRequestDto searchRequestDto2 = new SearchRequestDto();
        searchRequestDto2.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.NOT_EQUALS, "2025-05-16")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto2)));

        SearchRequestDto searchRequestDto3 = new SearchRequestDto();
        searchRequestDto3.setFilters(List.of( new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.GREATER, "2025-05-16")));
        Assertions.assertEquals(Set.of(certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto3)));

        SearchRequestDto searchRequestDto4 = new SearchRequestDto();
        searchRequestDto4.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.GREATER_OR_EQUAL, "2025-05-16")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto4)));

        SearchRequestDto searchRequestDto5 = new SearchRequestDto();
        searchRequestDto5.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.LESSER, "2025-05-20")));
        Assertions.assertEquals(Set.of(certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto5)));

        SearchRequestDto searchRequestDto6 = new SearchRequestDto();
        searchRequestDto6.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.LESSER_OR_EQUAL, "2025-05-20")));
        Assertions.assertEquals(Set.of(certificate2.getUuid(), certificate1.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto6)));

        SearchRequestDto searchRequestDto7 = new SearchRequestDto();
        searchRequestDto7.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate3.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto7)));

        SearchRequestDto searchRequestDto8 = new SearchRequestDto();
        searchRequestDto8.setFilters(List.of(new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.NOT_AFTER.name(), FilterConditionOperator.NOT_EMPTY, null)));
        Assertions.assertEquals(Set.of(certificate1.getUuid(), certificate2.getUuid()), getUuidsFromListCertificatesResponse(certificateService.listCertificates(new SecurityFilter(), searchRequestDto8)));
    }

    @Test
    void testHasPrivateKey() {
        CryptographicKey cryptographicKey = new CryptographicKey();

    }



    private Set<UUID> getUuidsFromListCertificatesResponse(CertificateResponseDto certificateResponseDto) {
        return certificateResponseDto.getCertificates().stream().map(c -> UUID.fromString(c.getUuid())).collect(Collectors.toSet());
    }


    private void testLikePredicate(final Predicate predicate, final String value) {
        Assertions.assertInstanceOf(SqmLikePredicate.class, predicate);
        Assertions.assertEquals(value, ((SqmLikePredicate) predicate).getPattern().toHqlString());
    }


    private SearchFilterRequestDTODummy prepareDummyFilterRequest(final FilterConditionOperator condition) {
        SearchFilterRequestDTODummy dummy = null;
        switch (condition) {
            case EQUALS ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.EQUALS, TEST_VALUE);
            case NOT_EQUALS ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.NOT_EQUALS, TEST_VALUE);
            case CONTAINS ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.CONTAINS, TEST_VALUE);
            case NOT_CONTAINS ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.NOT_CONTAINS, TEST_VALUE);
            case STARTS_WITH ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.STARTS_WITH, TEST_VALUE);
            case ENDS_WITH ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.ENDS_WITH, TEST_VALUE);
            case EMPTY ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.EMPTY, TEST_VALUE);
            case NOT_EMPTY ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.COMMON_NAME, FilterConditionOperator.NOT_EMPTY, TEST_VALUE);
            case GREATER ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.NOT_AFTER, FilterConditionOperator.GREATER, TEST_DATE_VALUE);
            case LESSER ->
                    dummy = new SearchFilterRequestDTODummy(FilterField.NOT_BEFORE, FilterConditionOperator.LESSER, TEST_DATE_VALUE);
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

    public FilterConditionOperator getCondition() {
        return conditionTest;
    }

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
