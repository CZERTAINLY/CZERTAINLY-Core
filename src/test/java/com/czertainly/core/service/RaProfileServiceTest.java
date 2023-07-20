package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileResponseDto;
import com.czertainly.api.model.client.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.cert.CertificateException;
import java.util.List;
import java.util.Optional;

public class RaProfileServiceTest extends ApprovalProfileData {

    private static final String RA_PROFILE_NAME = "testRaProfile1";

    @Autowired
    private com.czertainly.core.service.RaProfileService raProfileService;

    @Autowired
    private ApprovalProfileService approvalProfileService;

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private ApprovalProfileRelationRepository approvalProfileRelationRepository;

    @Autowired
    private ApprovalProfileRepository approvalProfileRepository;

    private RaProfile raProfile;
    private Certificate certificate;
    private CertificateContent certificateContent;
    private AuthorityInstanceReference authorityInstanceReference;
    private Connector connector;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());


        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("123456789");
        certificate = certificateRepository.save(certificate);

        connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        raProfile = new RaProfile();
        raProfile.setName(RA_PROFILE_NAME);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile.setEnabled(true);
        raProfile = raProfileRepository.save(raProfile);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListRaProfiles() {
        List<RaProfileDto> raProfiles = raProfileService.listRaProfiles(SecurityFilter.create(), Optional.of(true));
        Assertions.assertNotNull(raProfiles);
        Assertions.assertFalse(raProfiles.isEmpty());
        Assertions.assertEquals(1, raProfiles.size());
        Assertions.assertEquals(raProfile.getUuid().toString(), raProfiles.get(0).getUuid());
    }

    @Test
    public void testGetRaProfileByUuid() throws NotFoundException {
        RaProfileDto dto = raProfileService.getRaProfile(raProfile.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(raProfile.getUuid().toString(), dto.getUuid());
    }

    @Test
    public void testGetRaProfileByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.getRaProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testAddRaProfile() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/raProfile/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/raProfile/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        AddRaProfileRequestDto request = new AddRaProfileRequestDto();
        request.setName("testRaProfile2");
        request.setAttributes(List.of());

        RaProfileDto dto = raProfileService.addRaProfile(authorityInstanceReference.getSecuredParentUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddRaProfile_validationFail() {
        AddRaProfileRequestDto request = new AddRaProfileRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> raProfileService.addRaProfile(authorityInstanceReference.getSecuredParentUuid(), request));
    }

    @Test
    public void testAddRaProfile_alreadyExist() {
        AddRaProfileRequestDto request = new AddRaProfileRequestDto();
        request.setName(RA_PROFILE_NAME); // raProfile with same username exist

        Assertions.assertThrows(AlreadyExistException.class, () -> raProfileService.addRaProfile(authorityInstanceReference.getSecuredParentUuid(), request));
    }

    @Test
    public void testEditRaProfile() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/raProfile/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/raProfile/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        EditRaProfileRequestDto request = new EditRaProfileRequestDto();
        request.setDescription("some description");
        request.setAttributes(List.of());

        RaProfileDto dto = raProfileService.editRaProfile(authorityInstanceReference.getSecuredParentUuid(), raProfile.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    public void testEditRaProfile_notFound() {
        EditRaProfileRequestDto request = new EditRaProfileRequestDto();

        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.editRaProfile(authorityInstanceReference.getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

    @Test
    public void testRemoveRaProfile() throws NotFoundException {
        raProfileService.deleteRaProfile(raProfile.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.getRaProfile(raProfile.getSecuredUuid()));
    }

    @Test
    public void testRemoveRaProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.deleteRaProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testEnableRaProfile() throws NotFoundException, CertificateException {
        raProfileService.enableRaProfile(raProfile.getSecuredParentUuid(), raProfile.getSecuredUuid());
        Assertions.assertEquals(true, raProfile.getEnabled());
    }

    @Test
    public void testEnableRaProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.enableRaProfile(raProfile.getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testDisableRaProfile() throws NotFoundException {
        raProfileService.disableRaProfile(raProfile.getSecuredParentUuid(), raProfile.getSecuredUuid());
        Assertions.assertEquals(false, raProfile.getEnabled());
    }

    @Test
    public void testDisableRaProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.disableRaProfile(raProfile.getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }


    @Test
    public void testBulkRemove() {
        raProfileService.bulkDeleteRaProfile(List.of(raProfile.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.getRaProfile(raProfile.getSecuredUuid()));
    }

    @Test
    public void testBulkEnable() {
        raProfileService.bulkEnableRaProfile(List.of(raProfile.getSecuredUuid()));
        Assertions.assertTrue(raProfile.getEnabled());
    }

    @Test
    public void testBulkDisable() {
        raProfileService.bulkDisableRaProfile(List.of(raProfile.getSecuredUuid()));
        Assertions.assertFalse(raProfile.getEnabled());
    }

    @Test
    public void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = raProfileService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(1, dtos.size());
    }

    @Test
    public void testAssociationRaProfileWithApprovalProfile() throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        final ApprovalProfileRelation approvalProfileRelation = raProfileService.associateApprovalProfileWithRaProfile(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecurityFilter.create(), raProfile.getSecuredUuid(), approvalProfile.getSecuredUuid());

        Assertions.assertNotNull(approvalProfileRelation);
        Assertions.assertEquals(raProfile.getUuid(), approvalProfileRelation.getResourceUuid());
        Assertions.assertEquals(approvalProfile.getUuid(), approvalProfileRelation.getApprovalProfileUuid());
        Assertions.assertEquals(Resource.RA_PROFILE, approvalProfileRelation.getResource());
    }

    @Test
    public void testDisassociationRaProfileWithApprovalProfile() throws NotFoundException, AlreadyExistException {
        final SecurityFilter securityFilter = SecurityFilter.create();
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        final ApprovalProfileRelation approvalProfileRelation = raProfileService.associateApprovalProfileWithRaProfile(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), securityFilter, raProfile.getSecuredUuid(), approvalProfile.getSecuredUuid());
        Assertions.assertNotNull(approvalProfileRelation);

        raProfileService.disassociateApprovalProfileWithRaProfile(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), securityFilter, raProfile.getSecuredUuid(), approvalProfile.getSecuredUuid());

        final Optional<ApprovalProfileRelation> approvalProfileRelationOptional = approvalProfileRelationRepository.findByUuid(approvalProfileRelation.getSecuredUuid());
        Assertions.assertFalse(approvalProfileRelationOptional.isPresent());
    }

    @Test
    public void testListOfApprovalProfilesByRAProfile() throws NotFoundException, AlreadyExistException {
        final SecurityFilter securityFilter = SecurityFilter.create();
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        final ApprovalProfileRelation approvalProfileRelation = raProfileService.associateApprovalProfileWithRaProfile(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), securityFilter, raProfile.getSecuredUuid(), approvalProfile.getSecuredUuid());
        Assertions.assertNotNull(approvalProfileRelation);

        final ApprovalProfileResponseDto approvalProfileResponseDto = raProfileService.listApprovalProfilesByRaProfile(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), securityFilter, raProfile.getSecuredUuid(), new PaginationRequestDto());

        Assertions.assertEquals(1, approvalProfileResponseDto.getApprovalProfiles().size());

        final ApprovalProfileDto approvalProfileDto = approvalProfileResponseDto.getApprovalProfiles().get(0);
        Assertions.assertEquals(approvalProfile.getUuid().toString(), approvalProfileDto.getUuid());
    }

}
