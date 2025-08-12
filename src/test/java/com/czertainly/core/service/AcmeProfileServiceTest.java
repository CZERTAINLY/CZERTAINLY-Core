package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.protocol.ProtocolCertificateAssociationsRequestDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.ProtocolCertificateAssociations;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.ProtocolCertificateAssociationsRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class AcmeProfileServiceTest extends BaseSpringBootTest {

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private AcmeProfileService acmeProfileService;

    @Autowired
    private AcmeProfileRepository acmeProfileRepository;

    @Autowired
    private ProtocolCertificateAssociationsRepository protocolCertificateAssociationsRepository;

    private AcmeProfile acmeProfile;
    private CustomAttribute domainAttr;
    private RequestAttributeDto domainAttrRequestAttribute;

    @BeforeEach
    public void setUp() throws AttributeException {
        domainAttr = new CustomAttribute();
        domainAttr.setUuid(UUID.randomUUID().toString());
        domainAttr.setName("domain");
        domainAttr.setType(AttributeType.CUSTOM);
        domainAttr.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties customProps = new CustomAttributeProperties();
        customProps.setLabel("Domain of resource");
        domainAttr.setProperties(customProps);
        attributeEngine.updateCustomAttributeDefinition(domainAttr, List.of(Resource.CERTIFICATE));

        domainAttrRequestAttribute = new RequestAttributeDto();
        domainAttrRequestAttribute.setUuid(domainAttr.getUuid());
        domainAttrRequestAttribute.setName(domainAttr.getName());
        domainAttrRequestAttribute.setContentType(domainAttr.getContentType());
        domainAttrRequestAttribute.setContent(List.of(new StringAttributeContent("test")));


        acmeProfile = new AcmeProfile();
        acmeProfile.setWebsite("sample website");
        acmeProfile.setTermsOfServiceUrl("sample terms");
        acmeProfile.setValidity(30);
        acmeProfile.setRetryInterval(30);
        acmeProfile.setDescription("sample description");
        acmeProfile.setName("sameName");
        acmeProfile.setDnsResolverPort("53");
        acmeProfile.setDnsResolverIp("localhost");
        acmeProfile.setTermsOfServiceChangeUrl("change url");
        acmeProfile.setEnabled(false);
        ProtocolCertificateAssociations protocolCertificateAssociations = new ProtocolCertificateAssociations();
        protocolCertificateAssociations.setOwnerUuid(UUID.randomUUID());
        protocolCertificateAssociations.setGroupUuids(List.of(UUID.randomUUID()));
        protocolCertificateAssociations.setCustomAttributes(List.of(domainAttrRequestAttribute));
        protocolCertificateAssociationsRepository.save(protocolCertificateAssociations);
        acmeProfile.setCertificateAssociations(protocolCertificateAssociations);
        acmeProfile.setCertificateAssociationsUuid(protocolCertificateAssociations.getUuid());
        acmeProfileRepository.save(acmeProfile);
    }

    @Test
    void testListAcmeProfiles() {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        List<AcmeProfileListDto> acmeProfiles = acmeProfileService.listAcmeProfile(SecurityFilter.create());
        Assertions.assertNotNull(acmeProfiles);
        Assertions.assertFalse(acmeProfiles.isEmpty());
        Assertions.assertEquals(1, acmeProfiles.size());
        Assertions.assertEquals(acmeProfile.getUuid().toString(), acmeProfiles.get(0).getUuid());
    }

    @Test
    void testGetAcmeProfileByUuid() throws NotFoundException {
        acmeProfile.setEnabled(true);
        AcmeProfileDto dto = acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(acmeProfile.getUuid().toString(), dto.getUuid());
        Assertions.assertNotNull(dto.getCertificateAssociations());
        Assertions.assertEquals(acmeProfile.getCertificateAssociations().getOwnerUuid(), dto.getCertificateAssociations().getOwnerUuid());
        Assertions.assertEquals(acmeProfile.getCertificateAssociations().getGroupUuids(), dto.getCertificateAssociations().getGroupUuids());
        Assertions.assertEquals(acmeProfile.getCertificateAssociations().getCustomAttributes().size(), dto.getCertificateAssociations().getCustomAttributes().size());
    }

    @Test
    void testGetAcmeProfileByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testAddAcmeProfile() throws ConnectorException, AlreadyExistException, AttributeException, NotFoundException {

        AcmeProfileRequestDto request = new AcmeProfileRequestDto();
        request.setName("Test");
        request.setDescription("sample");
        request.setDnsResolverIp("localhost");
        request.setDnsResolverPort("53");

        AcmeProfileDto dto = acmeProfileService.createAcmeProfile(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertNotNull(dto.getUuid());
        Assertions.assertEquals(request.getDescription(), dto.getDescription());

        request.setName("Test2");
        ProtocolCertificateAssociationsRequestDto certificateAssociations = new ProtocolCertificateAssociationsRequestDto();
        certificateAssociations.setOwnerUuid(UUID.randomUUID());
        certificateAssociations.setGroupUuids(List.of(UUID.randomUUID()));
        certificateAssociations.setCustomAttributes(List.of(domainAttrRequestAttribute));
        request.setCertificateAssociations(certificateAssociations);
        dto = acmeProfileService.createAcmeProfile(request);
        AcmeProfile acmeProfileNew = acmeProfileRepository.findByUuid(UUID.fromString(dto.getUuid())).orElse(null);
        Assertions.assertNotNull(acmeProfileNew);
        Assertions.assertNotNull(acmeProfileNew.getCertificateAssociations());

    }

    @Test
    void testAddAcmeProfile_validationFail() {
        AcmeProfileRequestDto request = new AcmeProfileRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> acmeProfileService.createAcmeProfile(request));
    }

    @Test
    void testAddAcmeProfile_alreadyExist() {
        AcmeProfileRequestDto request = new AcmeProfileRequestDto();
        request.setName("sameName");

        Assertions.assertThrows(AlreadyExistException.class, () -> acmeProfileService.createAcmeProfile(request));
    }

    @Test
    void testEditAcmeProfile() throws ConnectorException, AttributeException, NotFoundException {

        acmeProfile.setEnabled(false);
        acmeProfileRepository.save(acmeProfile);

        AcmeProfileEditRequestDto request = new AcmeProfileEditRequestDto();
        request.setDescription("sample");
        request.setDnsResolverIp("sample");
        request.setDnsResolverPort("32");

        AcmeProfileDto dto = acmeProfileService.editAcmeProfile(acmeProfile.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
        Assertions.assertEquals(request.getDnsResolverIp(), dto.getDnsResolverIp());
        Assertions.assertNull(dto.getCertificateAssociations());

        ProtocolCertificateAssociationsRequestDto protocolCertificateAssociationsDto = new ProtocolCertificateAssociationsRequestDto();
        protocolCertificateAssociationsDto.setOwnerUuid(UUID.randomUUID());
        request.setCertificateAssociations(protocolCertificateAssociationsDto);
        dto = acmeProfileService.editAcmeProfile(acmeProfile.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getCertificateAssociations());
    }

    @Test
    void testEditAcmeProfile_validationFail() {
        AcmeProfileEditRequestDto request = new AcmeProfileEditRequestDto();
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.editAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

    @Test
    void testRemoveAcmeProfile() throws NotFoundException {
        UUID certificateAssociationsUuid = acmeProfile.getCertificateAssociationsUuid();
        acmeProfileService.deleteAcmeProfile(acmeProfile.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()));
        Assertions.assertTrue(protocolCertificateAssociationsRepository.findByUuid(SecuredUUID.fromUUID(certificateAssociationsUuid)).isEmpty());
    }

    @Test
    void testRemoveAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testEnableAcmeProfile() throws NotFoundException {
        acmeProfileService.enableAcmeProfile(acmeProfile.getSecuredUuid());
        Assertions.assertEquals(true, acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testEnableAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.enableAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testDisableAcmeProfile() throws NotFoundException {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        acmeProfileService.disableAcmeProfile(acmeProfile.getSecuredUuid());
        Assertions.assertEquals(false, acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testDisableAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.disableAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testBulkRemove() {
        acmeProfileService.bulkDeleteAcmeProfile(List.of(acmeProfile.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()));
    }

    @Test
    void testBulkEnable() throws NotFoundException {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        acmeProfileService.bulkEnableAcmeProfile(List.of(acmeProfile.getSecuredUuid()));
        Assertions.assertEquals(true, acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testBulkDisable() throws NotFoundException {
        acmeProfileService.bulkDisableAcmeProfile(List.of(acmeProfile.getSecuredUuid()));
        Assertions.assertEquals(false, acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = acmeProfileService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(1, dtos.size());
    }
}
