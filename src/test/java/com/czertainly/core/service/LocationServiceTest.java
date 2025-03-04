package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.LocationsResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;

class LocationServiceTest extends BaseSpringBootTest {

    private static final String LOCATION_NAME = "testLocation1";
    private static final String LOCATION_NAME_NOMULTIENTRIES = "testLocation-noMultiEntries";
    private static final String LOCATION_NAME_NOKEYMANAGEMENT = "testLocation-noKeyManagement";

    @Autowired
    private LocationService locationService;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @MockitoBean
    private ClientOperationService clientOperationService;

    private DataAttribute testAttribute;
    private DataAttribute testAttribute2;
    private Location location;
    private Location locationNoMultiEntries;
    private Location locationNoKeyManagement;
    private EntityInstanceReference entityInstanceReference;
    private Certificate certificate;
    private Certificate certificateWithoutLocation;
    private WireMockServer mockServer;
    private AttributeEngine attributeEngine;

    @Autowired
    void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @BeforeEach
    void setUp() throws NotFoundException, AttributeException {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        CertificateContent certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("cc4ab59d436a88dae957");
        certificate = certificateRepository.save(certificate);

        certificateWithoutLocation = new Certificate();
        certificateWithoutLocation.setState(CertificateState.ISSUED);
        certificateWithoutLocation.setValidationStatus(CertificateValidationStatus.VALID);
        certificateWithoutLocation.setCertificateContentId(certificateContent.getId());
        certificateWithoutLocation.setSerialNumber("aa4ab59d436a88dae957");
        certificateWithoutLocation = certificateRepository.save(certificateWithoutLocation);

        Connector connector = new Connector();
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        entityInstanceReference = new EntityInstanceReference();
        entityInstanceReference.setEntityInstanceUuid("ad8d8995-f12e-407e-a8d2-9a2fb91772bb");
        entityInstanceReference.setConnector(connector);
        entityInstanceReference = entityInstanceReferenceRepository.save(entityInstanceReference);

        prepareDataAttributesDefinitions();

        location = createLocation();
        locationNoMultiEntries = createLocationNoMultiEntries();
        locationNoKeyManagement = createLocationNoKeyManagement();

        // Use distinct instances of CertificateLocation
        CertificateLocation certificateLocation1 = new CertificateLocation();
        certificateLocation1.setWithKey(true);
        certificateLocation1.setCertificate(certificate);
        certificateLocation1.setLocation(location);

        CertificateLocation certificateLocation2 = new CertificateLocation();
        certificateLocation2.setWithKey(true);
        certificateLocation2.setCertificate(certificate);
        certificateLocation2.setLocation(locationNoMultiEntries);

        CertificateLocation certificateLocation3 = new CertificateLocation();
        certificateLocation3.setWithKey(true);
        certificateLocation3.setCertificate(certificate);
        certificateLocation3.setLocation(locationNoKeyManagement);

        location.getCertificates().add(certificateLocation1);
        locationNoMultiEntries.getCertificates().add(certificateLocation2);
        locationNoKeyManagement.getCertificates().add(certificateLocation3);

        location = locationRepository.save(location);
        locationNoMultiEntries = locationRepository.save(locationNoMultiEntries);
        locationNoKeyManagement = locationRepository.save(locationNoKeyManagement);
    }


    private void prepareDataAttributesDefinitions() throws AttributeException {
        testAttribute = new DataAttribute();
        testAttribute.setUuid("5e9146a6-da8a-403f-99cb-d5d64d93ce1c");
        testAttribute.setName("attribute");

        DataAttributeProperties properties = new DataAttributeProperties();
        properties.setLabel("Attribute");
        testAttribute.setDescription("description");
        testAttribute.setContentType(AttributeContentType.STRING);
        testAttribute.setType(AttributeType.DATA);
        properties.setRequired(true);
        properties.setReadOnly(false);
        properties.setVisible(true);
        testAttribute.setProperties(properties);

        testAttribute2 = new DataAttribute();
        testAttribute2.setUuid("c9819613-725e-4f01-89fb-cb896a26e555");
        testAttribute2.setName("sample");

        DataAttributeProperties sampleProps = new DataAttributeProperties();
        sampleProps.setLabel("Sample Attribute");
        testAttribute2.setDescription("Desc");
        testAttribute2.setContentType(AttributeContentType.STRING);
        testAttribute2.setType(AttributeType.DATA);
        sampleProps.setRequired(true);
        sampleProps.setReadOnly(false);
        sampleProps.setVisible(true);
        testAttribute2.setProperties(sampleProps);

        attributeEngine.updateDataAttributeDefinitions(entityInstanceReference.getConnectorUuid(), null, List.of(testAttribute, testAttribute2));
    }

    private Location createLocation() throws AttributeException, NotFoundException {
        Location newLocation = new Location();
        newLocation.setUuid(UUID.randomUUID());
        newLocation.setName(LOCATION_NAME);
        newLocation.setEntityInstanceReference(entityInstanceReference);
        newLocation.setEnabled(true);
        newLocation.setSupportKeyManagement(true);
        newLocation.setSupportMultipleEntries(true);

        List<RequestAttributeDto> requestAttributes = AttributeDefinitionUtils.createAttributes(testAttribute.getUuid(), testAttribute.getName(), List.of(new StringAttributeContent("newLocation")));
        attributeEngine.updateObjectDataAttributesContent(entityInstanceReference.getConnectorUuid(), null, Resource.LOCATION, newLocation.getUuid(), requestAttributes);
        return newLocation;
    }

    private Location createLocationNoMultiEntries() throws AttributeException, NotFoundException {
        Location newLocation = new Location();
        newLocation.setUuid(UUID.randomUUID());
        newLocation.setName(LOCATION_NAME_NOMULTIENTRIES);
        newLocation.setEntityInstanceReferenceUuid(entityInstanceReference.getUuid());
        newLocation.setEnabled(true);
        newLocation.setSupportKeyManagement(true);
        newLocation.setSupportMultipleEntries(false);

        List<RequestAttributeDto> requestAttributes = AttributeDefinitionUtils.createAttributes(testAttribute.getUuid(), testAttribute.getName(), List.of(new StringAttributeContent("location_multi")));
        attributeEngine.updateObjectDataAttributesContent(entityInstanceReference.getConnectorUuid(), null, Resource.LOCATION, newLocation.getUuid(), requestAttributes);

        return newLocation;
    }

    private Location createLocationNoKeyManagement() throws AttributeException, NotFoundException {
        Location newLocation = new Location();
        newLocation.setUuid(UUID.randomUUID());
        newLocation.setName(LOCATION_NAME_NOKEYMANAGEMENT);
        newLocation.setEntityInstanceReference(entityInstanceReference);
        newLocation.setEnabled(true);
        newLocation.setSupportKeyManagement(false);
        newLocation.setSupportMultipleEntries(true);

        List<RequestAttributeDto> requestAttributes = AttributeDefinitionUtils.createAttributes(testAttribute.getUuid(), testAttribute.getName(), List.of(new StringAttributeContent("location_no_key")));
        attributeEngine.updateObjectDataAttributesContent(entityInstanceReference.getConnectorUuid(), null, Resource.LOCATION, newLocation.getUuid(), requestAttributes);

        return newLocation;
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void testListLocations() {
        LocationsResponseDto locationsResponseDto = locationService.listLocations(SecurityFilter.create(), new SearchRequestDto());
        List<LocationDto> locations = locationsResponseDto.getLocations();
        Assertions.assertNotNull(locations);
        Assertions.assertFalse(locations.isEmpty());
        Assertions.assertEquals(3, locations.size());
    }

    @Test
    void testGetLocationByUuid() throws NotFoundException {
        LocationDto dto = locationService.getLocation(SecuredParentUUID.fromUUID(location.getEntityInstanceReferenceUuid()), location.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(location.getUuid().toString(), dto.getUuid());
    }

    @Test
    void testGetLocationByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.getLocation(SecuredParentUUID.fromUUID(location.getEntityInstanceReferenceUuid()), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testAddLocation() throws ConnectorException, AlreadyExistException, LocationException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/location/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/location/attributes/validate"))
                .willReturn(WireMock.okJson("true")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations"))
                .willReturn(WireMock.okJson("""
                        {
                          "certificates": [],
                          "multipleEntries": true,
                          "supportKeyManagement": true
                        }""")));

        AddLocationRequestDto request = new AddLocationRequestDto();
        request.setName("testLocation2");
        RequestAttributeDto requestAttributeDto = new RequestAttributeDto();
        requestAttributeDto.setUuid(testAttribute2.getUuid());
        requestAttributeDto.setName(testAttribute2.getName());
        requestAttributeDto.setContent(List.of(new StringAttributeContent("test")));
        request.setAttributes(List.of(requestAttributeDto));

        LocationDto dto = locationService.addLocation(SecuredParentUUID.fromUUID(entityInstanceReference.getUuid()), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    void testAddLocation_DuplicateEntity() {
        AddLocationRequestDto request = new AddLocationRequestDto();
        request.setName(LOCATION_NAME);
        RequestAttributeDto attribute = new RequestAttributeDto();
        attribute.setName("attribute");
        attribute.setContent(List.of(new StringAttributeContent("location")));
        request.setAttributes(List.of(attribute));

        Assertions.assertThrows(AlreadyExistException.class, () -> locationService.addLocation(SecuredParentUUID.fromUUID(entityInstanceReference.getUuid()), request));
    }

    @Test
    void testAddLocation_validationFail() {
        AddLocationRequestDto request = new AddLocationRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> locationService.addLocation(entityInstanceReference.getSecuredParentUuid(), request));
    }

    @Test
    void testAddLocation_alreadyExist() {
        AddLocationRequestDto request = new AddLocationRequestDto();
        request.setName(LOCATION_NAME); // location with the name that already exists

        Assertions.assertThrows(AlreadyExistException.class, () -> locationService.addLocation(entityInstanceReference.getSecuredParentUuid(), request));
    }

    @Test
    void testEditLocation() throws ConnectorException, LocationException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/location/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/location/attributes/validate"))
                .willReturn(WireMock.okJson("true")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations"))
                .willReturn(WireMock.okJson("""
                        {
                          "certificates": [],
                          "multipleEntries": true,
                          "supportKeyManagement": true
                        }""")));

        EditLocationRequestDto request = new EditLocationRequestDto();
        request.setDescription("some description");
        request.setAttributes(List.of());

        LocationDto dto = locationService.editLocation(entityInstanceReference.getSecuredParentUuid(), location.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    void testEditLocation_notFound() {
        EditLocationRequestDto request = new EditLocationRequestDto();

        Assertions.assertThrows(NotFoundException.class, () -> locationService.editLocation(entityInstanceReference.getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

    @Test
    void testRemoveLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.deleteLocation(entityInstanceReference.getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testEnableLocation() throws NotFoundException {
        locationService.enableLocation(location.getEntityInstanceReference().getSecuredParentUuid(), location.getSecuredUuid());
        Assertions.assertEquals(true, location.getEnabled());
    }

    @Test
    void testEnableLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.enableLocation(entityInstanceReference.getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testDisableLocation() throws NotFoundException {
        locationService.disableLocation(location.getEntityInstanceReference().getSecuredParentUuid(), location.getSecuredUuid());
        Assertions.assertFalse(locationService.getLocation(SecuredParentUUID.fromUUID(location.getEntityInstanceReferenceUuid()), location.getSecuredUuid()).isEnabled());
    }

    @Test
    void testDisableLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.disableLocation(entityInstanceReference.getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    // TODO: testing the location push, remove, issue, sync
    @Test
    void testPushCertificateToLocation_MultiNotSupported() {
        PushToLocationRequestDto request = new PushToLocationRequestDto();
        request.setAttributes(List.of());

        Assertions.assertThrows(LocationException.class, () -> locationService.pushCertificateToLocation(
                entityInstanceReference.getSecuredParentUuid(),
                locationNoMultiEntries.getSecuredUuid(),
                certificateWithoutLocation.getUuid().toString(), request)
        );
    }

    @Test
    void testIssueCertificateToLocation_KeyManagementNotSupported() {
        IssueToLocationRequestDto request = new IssueToLocationRequestDto();
        request.setCsrAttributes(List.of());
        request.setIssueAttributes(List.of());
        request.setRaProfileUuid("test");

        Assertions.assertThrows(LocationException.class, () -> locationService.issueCertificateToLocation(SecuredParentUUID.fromUUID(locationNoKeyManagement.getEntityInstanceReferenceUuid()),
                locationNoKeyManagement.getSecuredUuid(),
                request.getRaProfileUuid(), request)
        );
    }

    @Test
    void testIssueCertificateToLocation_MultiNotSupported() {
        IssueToLocationRequestDto request = new IssueToLocationRequestDto();
        request.setCsrAttributes(List.of());
        request.setIssueAttributes(List.of());
        request.setRaProfileUuid("test");

        Assertions.assertThrows(LocationException.class, () -> locationService.issueCertificateToLocation(
                SecuredParentUUID.fromUUID(locationNoMultiEntries.getEntityInstanceReferenceUuid()),
                locationNoMultiEntries.getSecuredUuid(),
                request.getRaProfileUuid(), request)
        );
    }

    @Test
    void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = locationService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(3, dtos.size());
    }

    @Test
    void testIssueToLocation() throws ConnectorException, java.security.cert.CertificateException, NoSuchAlgorithmException, CertificateOperationException, IOException, InvalidKeyException, CertificateRequestException, LocationException {
        RaProfile raProfile = getRaProfile();

        ClientCertificateDataResponseDto responseDto = new ClientCertificateDataResponseDto();

        responseDto.setUuid(certificate.getUuid().toString());
        Mockito.when(clientOperationService.issueCertificate(any(), any(), any(), any())).thenReturn(responseDto);
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/push/attributes")).willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/csr/attributes")).willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/csr")).willReturn(WireMock.okJson("{}")));
        LocationDto locationDto = locationService.issueCertificateToLocation(entityInstanceReference.getSecuredParentUuid(), location.getSecuredUuid(), String.valueOf(raProfile.getUuid()), new IssueToLocationRequestDto());

        Assertions.assertNotNull(locationDto.getCertificates().stream()
                .filter(cl ->  cl.getCertificateUuid().equals(certificate.getUuid().toString()))
                .findFirst().orElse(null));

        Certificate newlyIssuedCertificate = new Certificate();
        certificateRepository.save(newlyIssuedCertificate);
        responseDto.setUuid(newlyIssuedCertificate.getUuid().toString());
        LocationDto locationDto1 = locationService.issueCertificateToLocation(entityInstanceReference.getSecuredParentUuid(), location.getSecuredUuid(), String.valueOf(raProfile.getUuid()), new IssueToLocationRequestDto());
        Assertions.assertNotNull(locationDto1.getCertificates().stream()
                .filter(cl -> cl.getCertificateUuid().equals(newlyIssuedCertificate.getUuid().toString()))
                .findFirst().orElse(null));
    }

    @Test
    void testPushCertificateToLocation() throws NotFoundException, LocationException, AttributeException {
        Certificate certificateToPush = new Certificate();
        CertificateContent certificateContent = new CertificateContent();
        certificateContentRepository.save(certificateContent);
        certificateToPush.setCertificateContent(certificateContent);
        certificateToPush.setState(CertificateState.ISSUED);
        certificateRepository.save(certificateToPush);
        PushToLocationRequestDto pushRequest = new PushToLocationRequestDto();
        pushRequest.setAttributes(new ArrayList<>());
        mockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/push")).willReturn(WireMock.okJson("{\"withKey\": false}")));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/push/attributes")).willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/csr/attributes")).willReturn(WireMock.okJson("[]")));
        LocationDto locationDto = locationService.pushCertificateToLocation(entityInstanceReference.getSecuredParentUuid(), location.getSecuredUuid(), certificateToPush.getUuid().toString(), pushRequest);
        Assertions.assertNotNull(locationDto.getCertificates().stream()
                .filter(cl -> cl.getCertificateUuid().equals(certificateToPush.getUuid().toString()))
                .findFirst().orElse(null));
    }

    @Test
    void renewCertificateInLocation() throws ConnectorException, LocationException, CertificateOperationException, java.security.cert.CertificateException, IOException, NoSuchAlgorithmException, InvalidKeyException, CertificateRequestException {
        CertificateLocation certificateLocation = location.getCertificates().stream().findFirst().get();
        certificateLocation.setPushAttributes(List.of(new DataAttribute()));
        certificateLocation.setCsrAttributes(List.of(new DataAttribute()));
        locationRepository.save(location);

        certificate.setRaProfile(getRaProfile());
        Certificate renewedCertificate = new Certificate();
        certificateRepository.save(renewedCertificate);
        certificateRepository.save(certificate);

        ClientCertificateDataResponseDto responseDto = new ClientCertificateDataResponseDto();
        responseDto.setUuid(renewedCertificate.getUuid().toString());
        Mockito.when(clientOperationService.renewCertificate(any(), any(), any(), any())).thenReturn(responseDto);

        mockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/csr")).willReturn(WireMock.okJson("{}")));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/push/attributes")).willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/csr/attributes")).willReturn(WireMock.okJson("[]")));

        LocationDto locationDto = locationService.renewCertificateInLocation(entityInstanceReference.getSecuredParentUuid(), location.getSecuredUuid(), certificate.getUuid().toString());
        Assertions.assertNotNull(locationDto.getCertificates().stream()
                .filter(cl ->  cl.getCertificateUuid().equals(renewedCertificate.getUuid().toString()))
                .findFirst().orElse(null));
    }

    private RaProfile getRaProfile() {
        RaProfile raProfile = new RaProfile();
        raProfile.setEnabled(true);
        AuthorityInstanceReference authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setStatus("connected");
        Connector connector = new Connector();
        connector.setStatus(ConnectorStatus.CONNECTED);
        connectorRepository.save(connector);
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReferenceRepository.save(authorityInstanceReference);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfileRepository.save(raProfile);
        return raProfile;
    }

}
