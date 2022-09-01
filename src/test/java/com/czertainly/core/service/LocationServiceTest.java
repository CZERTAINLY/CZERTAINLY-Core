package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.LocationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.AttributeType;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.CertificateLocation;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.EntityInstanceReference;
import com.czertainly.core.dao.entity.Location;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.LocationRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocationServiceTest extends BaseSpringBootTest {

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

    private Location location;
    private Location locationNoMultiEntries;
    private Location locationNoKeyManagement;
    private EntityInstanceReference entityInstanceReference;
    private Certificate certificate;
    private Certificate certificateWithoutLocation;
    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        CertificateContent certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("cc4ab59d436a88dae957");
        certificate = certificateRepository.save(certificate);

        certificateWithoutLocation = new Certificate();
        certificateWithoutLocation.setCertificateContentId(certificateContent.getId());
        certificateWithoutLocation.setSerialNumber("aa4ab59d436a88dae957");
        certificateWithoutLocation = certificateRepository.save(certificateWithoutLocation);

        Connector connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        entityInstanceReference = new EntityInstanceReference();
        entityInstanceReference.setEntityInstanceUuid("ad8d8995-f12e-407e-a8d2-9a2fb91772bb");
        entityInstanceReference.setConnector(connector);
        entityInstanceReference = entityInstanceReferenceRepository.save(entityInstanceReference);

        location = createLocation();
        locationNoMultiEntries = createLocationNoMultiEntries();
        locationNoKeyManagement = createLocationNoKeyManagement();

        Set<CertificateLocation> cls = new HashSet<>();
        CertificateLocation certificateLocation = new CertificateLocation();
        certificateLocation.setWithKey(true);
        certificateLocation.setCertificate(certificate);
        certificateLocation.setLocation(location);
        //certificateLocationRepository.save(certificateLocation);
        cls.add(certificateLocation);

        location.getCertificates().addAll(cls);
        locationNoMultiEntries.getCertificates().addAll(cls);
        locationNoKeyManagement.getCertificates().addAll(cls);
        location = locationRepository.save(location);
        locationNoMultiEntries = locationRepository.save(locationNoMultiEntries);
        locationNoKeyManagement = locationRepository.save(locationNoKeyManagement);
    }

    private Location createLocation() {
        AttributeDefinition attribute = new AttributeDefinition();
        attribute.setUuid("5e9146a6-da8a-403f-99cb-d5d64d93ce1c");
        attribute.setName("attribute");
        attribute.setLabel("attribute");
        attribute.setDescription("description");
        attribute.setType(AttributeType.STRING);
        attribute.setRequired(true);
        attribute.setReadOnly(false);
        attribute.setVisible(true);

        Location location = new Location();
        location.setName(LOCATION_NAME);
        location.setEntityInstanceReference(entityInstanceReference);
        location.setEnabled(true);
        location.setSupportKeyManagement(true);
        location.setSupportMultipleEntries(true);
        List<AttributeDefinition> attributes = new ArrayList<>();
        attributes.add(attribute);
        location.setAttributes(attributes);

        return location;
    }

    private Location createLocationNoMultiEntries() {
        AttributeDefinition attribute = new AttributeDefinition();
        attribute.setUuid("a9392cc3-6f7f-46a2-8915-b9873f1267df");
        attribute.setName("attribute");
        attribute.setLabel("attribute");
        attribute.setDescription("description");
        attribute.setType(AttributeType.STRING);
        attribute.setRequired(true);
        attribute.setReadOnly(false);
        attribute.setVisible(true);

        Location location = new Location();
        location.setName(LOCATION_NAME_NOMULTIENTRIES);
        location.setEntityInstanceReferenceUuid(entityInstanceReference.getUuid());
        location.setEnabled(true);
        location.setSupportKeyManagement(true);
        location.setSupportMultipleEntries(false);
        List<AttributeDefinition> attributes = new ArrayList<>();
        attributes.add(attribute);
        location.setAttributes(attributes);

        return location;
    }

    private Location createLocationNoKeyManagement() {
        AttributeDefinition attribute = new AttributeDefinition();
        attribute.setUuid("eec75a92-a8c3-4903-935e-60c248f92af6");
        attribute.setName("attribute");
        attribute.setLabel("attribute");
        attribute.setDescription("description");
        attribute.setType(AttributeType.STRING);
        attribute.setRequired(true);
        attribute.setReadOnly(false);
        attribute.setVisible(true);

        Location location = new Location();
        location.setName(LOCATION_NAME_NOKEYMANAGEMENT);
        location.setEntityInstanceReference(entityInstanceReference);
        location.setEnabled(true);
        location.setSupportKeyManagement(false);
        location.setSupportMultipleEntries(true);
        List<AttributeDefinition> attributes = new ArrayList<>();
        attributes.add(attribute);
        location.setAttributes(attributes);

        return location;
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListLocations() {
        List<LocationDto> locations = locationService.listLocation(SecurityFilter.create());
        Assertions.assertNotNull(locations);
        Assertions.assertFalse(locations.isEmpty());
        Assertions.assertEquals(3, locations.size());
        Assertions.assertEquals(location.getUuid(), locations.get(0).getUuid());
    }

    @Test
    public void testGetLocationByUuid() throws NotFoundException {
        LocationDto dto = locationService.getLocation(location.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(location.getUuid(), dto.getUuid());
    }

    @Test
    public void testGetLocationByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.getLocation(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testAddLocation() throws NotFoundException, AlreadyExistException, LocationException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/location/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/location/attributes/validate"))
                .willReturn(WireMock.okJson("true")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations"))
                .willReturn(WireMock.okJson("{\n" +
                        "  \"certificates\": [],\n" +
                        "  \"multipleEntries\": true,\n" +
                        "  \"supportKeyManagement\": true\n" +
                        "}")));

        AddLocationRequestDto request = new AddLocationRequestDto();
        request.setName("testLocation2");
        request.setAttributes(List.of());

        LocationDto dto = locationService.addLocation(SecuredUUID.fromUUID(entityInstanceReference.getUuid()),request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddLocation_validationFail() {
        AddLocationRequestDto request = new AddLocationRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> locationService.addLocation(entityInstanceReference.getSecuredUuid(), request));
    }

    @Test
    public void testAddLocation_alreadyExist() {
        AddLocationRequestDto request = new AddLocationRequestDto();
        request.setName(LOCATION_NAME); // location with the name that already exists

        Assertions.assertThrows(AlreadyExistException.class, () -> locationService.addLocation(entityInstanceReference.getSecuredUuid(), request));
    }

    // TODO
    @Test
    public void testEditLocation() throws NotFoundException, LocationException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/location/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/location/attributes/validate"))
                .willReturn(WireMock.okJson("true")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations"))
                .willReturn(WireMock.okJson("{\n" +
                        "  \"certificates\": [],\n" +
                        "  \"multipleEntries\": true,\n" +
                        "  \"supportKeyManagement\": true\n" +
                        "}")));

        EditLocationRequestDto request = new EditLocationRequestDto();
        request.setDescription("some description");
        request.setAttributes(List.of());

        LocationDto dto = locationService.editLocation(entityInstanceReference.getSecuredUuid(), location.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    public void testEditLocation_notFound() {
        EditLocationRequestDto request = new EditLocationRequestDto();

        Assertions.assertThrows(NotFoundException.class, () -> locationService.editLocation(entityInstanceReference.getSecuredUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

//    @Test
//    public void testRemoveLocation_withCertificates() throws ConnectorException {
//        mockServer.stubFor(WireMock
//                .post(WireMock.urlPathMatching("/v1/entityProvider/entities/[^/]+/locations/remove"))
//                .willReturn(WireMock.okJson("{\n" +
//                        "  \"certificateMetadata\": {}\n" +
//                        "}")));
//
//        RemoveCertificateRequestDto removeCertificateRequestDto = new RemoveCertificateRequestDto();
//        removeCertificateRequestDto.setLocationAttributes(location.getRequestAttributes());
//
//        locationService.removeCertificateFromLocation(location.getUuid(), certificate.getUuid());
//
//        locationService.removeLocation(location.getUuid());
//        Assertions.assertThrows(NotFoundException.class, () -> locationService.getLocation(location.getUuid()));
//    }

    @Test
    public void testRemoveLocation_withCertificates() {
        Assertions.assertThrows(ValidationException.class, () -> locationService.deleteLocation(location.getSecuredUuid()));
    }

    @Test
    public void testRemoveLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.deleteLocation(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testEnableLocation() throws NotFoundException {
        locationService.enableLocation(location.getSecuredUuid());
        Assertions.assertEquals(true, location.getEnabled());
    }

    @Test
    public void testEnableLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.enableLocation(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testDisableLocation() throws NotFoundException {
        locationService.disableLocation(location.getSecuredUuid());
        Assertions.assertEquals(false, location.getEnabled());
    }

    @Test
    public void testDisableLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.disableLocation(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    // TODO: testing the location push, remove, issue, sync

    @Test
    public void testPushCertificateToLocation_MultiNotSupported() {
        PushToLocationRequestDto request = new PushToLocationRequestDto();
        request.setAttributes(List.of());

        Assertions.assertThrows(LocationException.class, () -> locationService.pushCertificateToLocation(
                locationNoMultiEntries.getSecuredUuid(),
                certificateWithoutLocation.getUuid().toString(),
                request)
        );
    }

    @Test
    public void testIssueCertificateToLocation_KeyManagementNotSupported() {
        IssueToLocationRequestDto request = new IssueToLocationRequestDto();
        request.setCsrAttributes(List.of());
        request.setIssueAttributes(List.of());
        request.setRaProfileUuid("test");

        Assertions.assertThrows(LocationException.class, () -> locationService.issueCertificateToLocation(
                locationNoKeyManagement.getSecuredUuid(),
                request.getRaProfileUuid(),
                request)
        );
    }

    @Test
    public void testIssueCertificateToLocation_MultiNotSupported() {
        IssueToLocationRequestDto request = new IssueToLocationRequestDto();
        request.setCsrAttributes(List.of());
        request.setIssueAttributes(List.of());
        request.setRaProfileUuid("test");

        Assertions.assertThrows(LocationException.class, () -> locationService.issueCertificateToLocation(
                locationNoMultiEntries.getSecuredUuid(),
                request.getRaProfileUuid(),
                request)
        );
    }
}
