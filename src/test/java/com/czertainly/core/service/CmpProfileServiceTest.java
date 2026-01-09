package com.czertainly.core.service;


import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeV3;
import com.czertainly.api.model.client.cmp.CmpProfileEditRequestDto;
import com.czertainly.api.model.client.cmp.CmpProfileRequestDto;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.common.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v3.CustomAttributeV3;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cmp.CmpProfileDetailDto;
import com.czertainly.api.model.core.cmp.CmpProfileDto;
import com.czertainly.api.model.core.cmp.CmpProfileVariant;
import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.api.model.core.protocol.ProtocolCertificateAssociationsRequestDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.ProtocolCertificateAssociations;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.dao.repository.ProtocolCertificateAssociationsRepository;
import com.czertainly.core.dao.repository.cmp.CmpProfileRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class CmpProfileServiceTest extends BaseSpringBootTest {

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private CmpProfileService cmpProfileService;

    @Autowired
    private CmpProfileRepository cmpProfileRepository;

    @Autowired
    private ProtocolCertificateAssociationsRepository protocolCertificateAssociationsRepository;

    private CmpProfile cmpProfile;
    private RequestAttributeV3 domainAttrRequestAttribute;

    @BeforeEach
    public void setUp() throws AttributeException {
        CustomAttributeV3 domainAttr = new CustomAttributeV3();
        domainAttr.setUuid(UUID.randomUUID().toString());
        domainAttr.setName("domain");
        domainAttr.setType(AttributeType.CUSTOM);
        domainAttr.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties customProps = new CustomAttributeProperties();
        customProps.setLabel("Domain of resource");
        domainAttr.setProperties(customProps);
        attributeEngine.updateCustomAttributeDefinition(domainAttr, List.of(Resource.CERTIFICATE));

        domainAttrRequestAttribute = new RequestAttributeV3();
        domainAttrRequestAttribute.setUuid(UUID.fromString(domainAttr.getUuid()));
        domainAttrRequestAttribute.setName(domainAttr.getName());
        domainAttrRequestAttribute.setContentType(domainAttr.getContentType());
        domainAttrRequestAttribute.setContent(List.of(new StringAttributeContentV3("test")));

        cmpProfile = new CmpProfile();
        cmpProfile.setDescription("sample description");
        cmpProfile.setName("sameName");
        cmpProfile.setEnabled(true);
        ProtocolCertificateAssociations protocolCertificateAssociations = new ProtocolCertificateAssociations();
        protocolCertificateAssociations.setOwnerUuid(UUID.randomUUID());
        protocolCertificateAssociations.setGroupUuids(List.of(UUID.randomUUID()));
        protocolCertificateAssociations.setCustomAttributes(List.of(domainAttrRequestAttribute));
        protocolCertificateAssociationsRepository.save(protocolCertificateAssociations);
        cmpProfile.setCertificateAssociations(protocolCertificateAssociations);
        cmpProfile.setCertificateAssociationsUuid(protocolCertificateAssociations.getUuid());
        cmpProfileRepository.save(cmpProfile);
    }

    @Test
    void testGetCmpProfileByUuid() throws NotFoundException {
        cmpProfile.setEnabled(true);
        CmpProfileDetailDto dto = cmpProfileService.getCmpProfile(cmpProfile.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(cmpProfile.getUuid().toString(), dto.getUuid());
        Assertions.assertNotNull(dto.getCertificateAssociations());
        Assertions.assertEquals(cmpProfile.getCertificateAssociations().getOwnerUuid(), dto.getCertificateAssociations().getOwnerUuid());
        Assertions.assertEquals(cmpProfile.getCertificateAssociations().getGroupUuids(), dto.getCertificateAssociations().getGroupUuids());
        Assertions.assertEquals(cmpProfile.getCertificateAssociations().getCustomAttributes().size(), dto.getCertificateAssociations().getCustomAttributes().size());
    }

    @Test
    void testAddCmpProfile() throws ConnectorException, AlreadyExistException, AttributeException, NotFoundException {
        CmpProfileRequestDto request = new CmpProfileRequestDto();
        request.setName("Test");
        request.setDescription("sample");
        request.setVariant(CmpProfileVariant.V2);
        request.setRequestProtectionMethod(ProtectionMethod.SHARED_SECRET);
        request.setResponseProtectionMethod(ProtectionMethod.SHARED_SECRET);
        request.setSharedSecret("secret");

        CmpProfileDto dto = cmpProfileService.createCmpProfile(request);
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
        dto = cmpProfileService.createCmpProfile(request);
        CmpProfile cmpProfileNew = cmpProfileRepository.findByUuid(UUID.fromString(dto.getUuid())).orElse(null);
        Assertions.assertNotNull(cmpProfileNew);
        Assertions.assertNotNull(cmpProfileNew.getCertificateAssociations());

    }

    @Test
    void testEditCmpProfile() throws ConnectorException, AttributeException, NotFoundException {

        cmpProfile.setEnabled(false);
        cmpProfileRepository.save(cmpProfile);

        CmpProfileEditRequestDto request = new CmpProfileEditRequestDto();
        request.setDescription("sample");
        request.setVariant(CmpProfileVariant.V2);
        request.setRequestProtectionMethod(ProtectionMethod.SHARED_SECRET);
        request.setResponseProtectionMethod(ProtectionMethod.SHARED_SECRET);
        request.setSharedSecret("secret");


        CmpProfileDetailDto dto = cmpProfileService.editCmpProfile(cmpProfile.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
        Assertions.assertNull(dto.getCertificateAssociations());

        ProtocolCertificateAssociationsRequestDto protocolCertificateAssociationsDto = new ProtocolCertificateAssociationsRequestDto();
        protocolCertificateAssociationsDto.setOwnerUuid(UUID.randomUUID());
        request.setCertificateAssociations(protocolCertificateAssociationsDto);
        dto = cmpProfileService.editCmpProfile(cmpProfile.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getCertificateAssociations());
    }

    @Test
    void testRemoveCmpProfile() throws NotFoundException {
        UUID certificateAssociationsUuid = cmpProfile.getCertificateAssociationsUuid();
        cmpProfileService.deleteCmpProfile(cmpProfile.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> cmpProfileService.getCmpProfile(cmpProfile.getSecuredUuid()));
        Assertions.assertTrue(protocolCertificateAssociationsRepository.findByUuid(SecuredUUID.fromUUID(certificateAssociationsUuid)).isEmpty());
    }
}
