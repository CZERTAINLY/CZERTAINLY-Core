package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.other.ResourceDto;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.other.ResourceEventDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class ResourceServiceTest extends BaseSpringBootTest {

    private static final int AUTH_SERVICE_MOCK_PORT = 10001;
    private static final String CERTIFICATE_UUID = "c1cfe60f-2556-461f-9a64-9dd8e92158cf";
    private static final String ATTRIBUTE_UUID = "f1982dfe-2523-45cf-9bfe-034ff1659369";

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:" + AUTH_SERVICE_MOCK_PORT);
    }

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private AttributeDefinitionRepository attributeDefinitionRepository;

    private WireMockServer mockServer;

    @AfterEach
    void tearDown() {
        mockServer.stop();
        mockServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        mockServer = new WireMockServer(AUTH_SERVICE_MOCK_PORT);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        WireMock.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/auth/users"))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody( // UserWithPaginationDto
                                        """
                                        {
                                            "currentPage": 1,
                                            "pageSize": 2,
                                            "totalPages": 1,
                                            "totalCount": 2,
                                            "hasPrevious": 0,
                                            "hasNext": 0,
                                            "data": [
                                                {
                                                    "uuid": "mock-user-uuid",
                                                    "username": "mockUser",
                                                    "groups": [],
                                                    "enabled": true,
                                                    "systemUser": false
                                                },
                                                {
                                                    "uuid": "mock-user-uuid-2",
                                                    "username": "mockUser2",
                                                    "groups": [],
                                                    "enabled": true,
                                                    "systemUser": false
                                                }
                                            ]
                                        }
                                        """
                                )
                        )
        );

        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        Certificate certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCercertificatetificate");
        certificate.setSerialNumber("123456789");
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate.setUuid(UUID.fromString(CERTIFICATE_UUID));
        certificateRepository.save(certificate);

        CustomAttribute attribute = new CustomAttribute();
        attribute.setUuid(ATTRIBUTE_UUID);
        attribute.setName("testAttribute");
        attribute.setDescription("description");
        attribute.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties attributeProperties = new CustomAttributeProperties();
        attributeProperties.setReadOnly(true);
        attributeProperties.setRequired(true);
        attribute.setProperties(attributeProperties);

        AttributeDefinition attributeDefinition = new AttributeDefinition();
        attributeDefinition.setUuid(UUID.fromString(ATTRIBUTE_UUID));
        attributeDefinition.setName("testAttribute");
        attributeDefinition.setAttributeUuid(UUID.fromString(ATTRIBUTE_UUID));
        attributeDefinition.setContentType(AttributeContentType.STRING);
        attributeDefinition.setLabel("testAttributeLabel");
        attributeDefinition.setType(AttributeType.CUSTOM);
        attributeDefinition.setDefinition(attribute);
        attributeDefinition.setEnabled(true);
        attributeDefinitionRepository.save(attributeDefinition);
    }

    @Test
    void testListResources() {
        // Call the method to test
        List<ResourceDto> resources = resourceService.listResources();

        // Assert the expected results
        Assertions.assertNotNull(resources);
        Assertions.assertFalse(resources.isEmpty(), "Resource list should not be empty");
        Assertions.assertTrue(resources.stream().anyMatch(
                resource -> resource.getResource().getCode().equals(Resource.Codes.CERTIFICATE)),
                "Resource list should contain CERTIFICATE resource");
    }

    @Test
    void testGetObjectsForResource() throws NotFoundException {
        List<Resource> resources = List.of(
                Resource.ACME_PROFILE,
                Resource.AUTHORITY,
                Resource.ATTRIBUTE,
                Resource.COMPLIANCE_PROFILE,
                Resource.CONNECTOR,
                Resource.CREDENTIAL,
                Resource.ENTITY,
                Resource.GROUP,
                Resource.LOCATION,
                Resource.RA_PROFILE,
                Resource.SCEP_PROFILE,
                Resource.TOKEN_PROFILE,
                Resource.TOKEN,
                Resource.USER,
                Resource.CMP_PROFILE
        );

        for (Resource resource : resources) {
            // Call the method to test and check that it does not throw an exception
            Assertions.assertDoesNotThrow(() -> resourceService.getObjectsForResource(resource), "Should not throw exception for resource: " + resource);
        }

        // Throw NotFoundException for unsupported resource
        Resource unsupportedResource = Resource.CERTIFICATE;
        Assertions.assertThrows(NotFoundException.class, () -> resourceService.getObjectsForResource(unsupportedResource), "Should throw NotFoundException for unsupported resource: " + unsupportedResource);
        Assertions.assertThrows(NotFoundException.class, () -> resourceService.getObjectsForResource(Resource.RULE), "Should throw NotFoundException for unsupported resource: " + Resource.RULE);
    }

    @Test
    void testUpdateAttributeContentForObject() {
        // Should throw AttributeException
        Assertions.assertThrows(AttributeException.class, () -> resourceService.updateAttributeContentForObject(
                Resource.CERTIFICATE,
                SecuredUUID.fromString(CERTIFICATE_UUID),
                UUID.fromString(ATTRIBUTE_UUID),
                List.of()
        ));

        // Should throw NotFoundException
        Assertions.assertThrows(NotFoundException.class, () -> resourceService.updateAttributeContentForObject(
                Resource.ATTRIBUTE,
                SecuredUUID.fromString(CERTIFICATE_UUID),
                UUID.fromString(ATTRIBUTE_UUID),
                List.of()
        ));

        Assertions.assertThrows(NotFoundException.class, () -> resourceService.updateAttributeContentForObject(
                Resource.RULE,
                SecuredUUID.fromString(CERTIFICATE_UUID),
                UUID.fromString(ATTRIBUTE_UUID),
                List.of()
        ));
    }

    @Test
    void testListResourceRuleFilterFields() throws NotFoundException {
        // Resource without filter fields and attributes should return empty list
        List<SearchFieldDataByGroupDto> filterFields = resourceService.listResourceRuleFilterFields(Resource.USER, false);
        Assertions.assertNotNull(filterFields);
        Assertions.assertTrue(filterFields.isEmpty(), "Filter fields list should be empty for resource: " + Resource.USER);

        // Resource == CERTIFICATE should return non-empty list
        filterFields = resourceService.listResourceRuleFilterFields(Resource.CERTIFICATE, false);
        Assertions.assertNotNull(filterFields);
        Assertions.assertFalse(filterFields.isEmpty(), "Filter fields list should not be empty for resource: " + Resource.CERTIFICATE);
    }

    @Test
    void testListResourceEvents() {
        // Call the method to test
        List<ResourceEventDto> events = resourceService.listResourceEvents(Resource.CERTIFICATE);

        // Assert the expected results
        Assertions.assertNotNull(events);
        Assertions.assertFalse(events.isEmpty(), "Resource event list should not be empty");
        Assertions.assertTrue(events.stream().anyMatch(
                event -> event.getEvent().getCode().equals(ResourceEvent.CERTIFICATE_DISCOVERED.getCode())),
                "Resource event list should contain CERTIFICATE_DISCOVERED event");
    }

    @Test
    void testListAllResourceEvents() {
        // Call the method to test
        Map<ResourceEvent, List<ResourceEventDto>> events = resourceService.listAllResourceEvents();

        // Assert the expected results
        Assertions.assertNotNull(events);
        Assertions.assertFalse(events.isEmpty(), "Resource event map should not be empty");
        Assertions.assertTrue(events.keySet().stream().anyMatch(
                event -> event.getCode().equals(ResourceEvent.CERTIFICATE_DISCOVERED.getCode())),
                "Resource event map should contain CERTIFICATE_DISCOVERED event");
    }
}
