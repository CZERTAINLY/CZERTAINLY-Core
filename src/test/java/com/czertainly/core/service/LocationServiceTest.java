package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.BaseAttributeDefinitionTypes;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class LocationServiceTest {

    private static final String LOCATION_NAME = "testLocation1";

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
    private EntityInstanceReference entityInstanceReference;
    private Certificate certificate;
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

        Connector connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector = connectorRepository.save(connector);

        entityInstanceReference = new EntityInstanceReference();
        entityInstanceReference.setEntityInstanceUuid("ad8d8995-f12e-407e-a8d2-9a2fb91772bb");
        entityInstanceReference.setConnector(connector);
        entityInstanceReference = entityInstanceReferenceRepository.save(entityInstanceReference);

        AttributeDefinition attribute = new AttributeDefinition();
        attribute.setUuid("5e9146a6-da8a-403f-99cb-d5d64d93ce1c");
        attribute.setName("attribute");
        attribute.setLabel("attribute");
        attribute.setDescription("description");
        attribute.setType(BaseAttributeDefinitionTypes.STRING);
        attribute.setRequired(true);
        attribute.setReadOnly(false);
        attribute.setVisible(true);

        location = new Location();
        location.setName(LOCATION_NAME);
        location.setEntityInstanceReference(entityInstanceReference);
        location.setEnabled(true);
        location.setSupportKeyManagement(true);
        location.setSupportMultipleEntries(true);
        List<AttributeDefinition> attributes = new ArrayList<>();
        attributes.add(attribute);
        location.setAttributes(attributes);
        //location = locationRepository.save(location);

        Set<CertificateLocation> cls = new HashSet<>();
        CertificateLocation certificateLocation = new CertificateLocation();
        certificateLocation.setWithKey(true);
        certificateLocation.setCertificate(certificate);
        certificateLocation.setLocation(location);
        //certificateLocationRepository.save(certificateLocation);
        cls.add(certificateLocation);

        location.getCertificates().addAll(cls);
        location = locationRepository.save(location);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListLocations() {
        List<LocationDto> locations = locationService.listLocation();
        Assertions.assertNotNull(locations);
        Assertions.assertFalse(locations.isEmpty());
        Assertions.assertEquals(1, locations.size());
        Assertions.assertEquals(location.getUuid(), locations.get(0).getUuid());
    }

    @Test
    public void testGetLocationByUuid() throws NotFoundException {
        LocationDto dto = locationService.getLocation(location.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(location.getUuid(), dto.getUuid());
    }

    @Test
    public void testGetLocationByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.getLocation("wrong-uuid"));
    }

    @Test
    public void testAddLocation() throws ConnectorException, AlreadyExistException, CertificateException {
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
        request.setEntityInstanceUuid(entityInstanceReference.getUuid());

        LocationDto dto = locationService.addLocation(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddLocation_validationFail() {
        AddLocationRequestDto request = new AddLocationRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> locationService.addLocation(request));
    }

    @Test
    public void testAddLocation_alreadyExist() {
        AddLocationRequestDto request = new AddLocationRequestDto();
        request.setName(LOCATION_NAME); // location with the name that already exists

        Assertions.assertThrows(AlreadyExistException.class, () -> locationService.addLocation(request));
    }

    // TODO
    @Test
    public void testEditLocation() throws ConnectorException, CertificateException {
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
        request.setEntityInstanceUuid(entityInstanceReference.getUuid());

        LocationDto dto = locationService.editLocation(location.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    public void testEditLocation_notFound() {
        EditLocationRequestDto request = new EditLocationRequestDto();

        Assertions.assertThrows(NotFoundException.class, () -> locationService.editLocation("wrong-uuid", request));
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
        Assertions.assertThrows(ValidationException.class, () -> locationService.removeLocation(location.getUuid()));
    }

    @Test
    public void testRemoveLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.removeLocation("wrong-uuid"));
    }

    @Test
    public void testEnableLocation() throws NotFoundException {
        locationService.enableLocation(location.getUuid());
        Assertions.assertEquals(true, location.getEnabled());
    }

    @Test
    public void testEnableLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.enableLocation("wrong-uuid"));
    }

    @Test
    public void testDisableLocation() throws NotFoundException {
        locationService.disableLocation(location.getUuid());
        Assertions.assertEquals(false, location.getEnabled());
    }

    @Test
    public void testDisableLocation_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> locationService.disableLocation("wrong-uuid"));
    }

    // TODO: testing the location push, remove, issue, sync
}
