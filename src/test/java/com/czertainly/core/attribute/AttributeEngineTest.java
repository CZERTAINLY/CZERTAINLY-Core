package com.czertainly.core.attribute;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.metadata.MetadataResponseDto;
import com.czertainly.api.model.common.attribute.v2.*;
import com.czertainly.api.model.common.attribute.v2.constraint.RegexpAttributeConstraint;
import com.czertainly.api.model.common.attribute.v2.content.*;
import com.czertainly.api.model.common.attribute.v2.content.data.CodeBlockAttributeContentData;
import com.czertainly.api.model.common.attribute.v2.content.data.ProgrammingLanguageEnum;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityResourceFilter;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.util.BaseSpringBootTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class AttributeEngineTest extends BaseSpringBootTest {

    @Autowired
    private AttributeEngine attributeEngine;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private OwnerAssociationRepository ownerAssociationRepository;
    @Autowired
    private AttributeDefinitionRepository attributeDefinitionRepository;
    @Autowired
    private AttributeRelationRepository attributeRelationRepository;

    private Connector connectorAuthority;
    private Connector connectorDiscovery;
    private Certificate certificate;
    private UUID authorityDiscoveryUuid;
    private UUID networkDiscoveryUuid;

    CustomAttribute orderNoCustomAttribute;
    CustomAttribute departmentCustomAttribute;
    CustomAttribute expirationDateCustomAttribute;
    private MetadataAttribute networkDiscoveryMeta;

    @BeforeEach
    void setUp() throws AttributeException, NotFoundException {
        connectorAuthority = new Connector();
        connectorAuthority.setName("EJBCAAuthorityConnector");
        connectorAuthority.setUrl("http://localhost:8080");
        connectorAuthority.setStatus(ConnectorStatus.CONNECTED);
        connectorAuthority = connectorRepository.save(connectorAuthority);

        connectorDiscovery = new Connector();
        connectorDiscovery.setName("NetworkDiscoveryConnector");
        connectorDiscovery.setUrl("http://localhost:8081");
        connectorDiscovery.setStatus(ConnectorStatus.CONNECTED);
        connectorDiscovery = connectorRepository.save(connectorDiscovery);

        AuthorityInstanceReference authorityInstance = new AuthorityInstanceReference();
        authorityInstance.setName("testAuthorityInstance1");
        authorityInstance.setConnector(connectorAuthority);
        authorityInstance.setConnectorUuid(connectorAuthority.getUuid());
        authorityInstance.setKind("sample");
        authorityInstance.setAuthorityInstanceUuid("1l");
        authorityInstance = authorityInstanceReferenceRepository.save(authorityInstance);

        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        RaProfile raProfile = new RaProfile();
        raProfile.setName("Test RA profile");
        raProfile.setAuthorityInstanceReference(authorityInstance);
        raProfile = raProfileRepository.save(raProfile);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCercertificatetificate");
        certificate.setSerialNumber("123456789");
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate.setRaProfile(raProfile);
        certificate = certificateRepository.save(certificate);

        // Ensure OwnerAssociation is created and associated
        OwnerAssociation ownerAssociation = new OwnerAssociation();
        ownerAssociation.setOwnerUuid(UUID.randomUUID()); // Set a proper UUID
        ownerAssociation.setOwnerUsername("ownerName");
        ownerAssociation.setResource(Resource.CERTIFICATE);
        ownerAssociation.setObjectUuid(certificate.getUuid());
        ownerAssociation.setCertificate(certificate);
        ownerAssociation = ownerAssociationRepository.saveAndFlush(ownerAssociation);

        certificate.setOwner(ownerAssociation);
        certificateRepository.save(certificate);

        networkDiscoveryUuid = UUID.randomUUID();
        authorityDiscoveryUuid = UUID.randomUUID();

        loadMetadata();
        loadCustomAttributesData();
    }

    @Test
    void testMetaContents() {
        var mappedMetadata = attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.CERTIFICATE, certificate.getUuid()));
        Assertions.assertEquals(3, mappedMetadata.size());
    }

    @Test
    void testMetadataContentReplacement() throws AttributeException {
        networkDiscoveryMeta.setContent(List.of(new StringAttributeContent("localhost:1443"), new StringAttributeContent("localhost:2443"), new StringAttributeContent("localhost:3443")));
        attributeEngine.updateMetadataAttribute(networkDiscoveryMeta, new ObjectAttributeContentInfo(connectorDiscovery.getUuid(), Resource.CERTIFICATE, certificate.getUuid(), Resource.DISCOVERY, networkDiscoveryUuid));
        var mappedMetadata = attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.CERTIFICATE, certificate.getUuid()));
        Optional<MetadataResponseDto> metadataResponseDto = mappedMetadata.stream().filter(m -> m.getConnectorUuid().equals(connectorDiscovery.getUuid().toString())).findFirst();
        Assertions.assertTrue(metadataResponseDto.isPresent());
        Assertions.assertEquals(4, metadataResponseDto.get().getItems().get(0).getContent().size());

        networkDiscoveryMeta.getProperties().setOverwrite(true);
        networkDiscoveryMeta.setContent(List.of(new StringAttributeContent("TEST", "TEST")));
        attributeEngine.updateMetadataAttribute(networkDiscoveryMeta, new ObjectAttributeContentInfo(connectorDiscovery.getUuid(), Resource.CERTIFICATE, certificate.getUuid(), Resource.DISCOVERY, networkDiscoveryUuid));
        mappedMetadata = attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.CERTIFICATE, certificate.getUuid()));
        metadataResponseDto = mappedMetadata.stream().filter(m -> m.getConnectorUuid().equals(connectorDiscovery.getUuid().toString())).findFirst();
        Assertions.assertTrue(metadataResponseDto.isPresent());
        Assertions.assertEquals(3, mappedMetadata.size());
        Assertions.assertEquals(1, metadataResponseDto.get().getItems().get(0).getContent().size());
        Assertions.assertEquals("TEST", metadataResponseDto.get().getItems().get(0).getContent().get(0).getReference());
    }

    @Test
    void testAttributeContentValidation() {
        RequestAttributeDto departmentAttributeDto = new RequestAttributeDto();
        departmentAttributeDto.setUuid(departmentCustomAttribute.getUuid());
        departmentAttributeDto.setName(departmentCustomAttribute.getName());
        departmentAttributeDto.setContent(List.of(new StringAttributeContent("Sales")));

        RequestAttributeDto expirationDateAttributeDto = new RequestAttributeDto();
        expirationDateAttributeDto.setUuid(expirationDateCustomAttribute.getUuid());
        expirationDateAttributeDto.setName(expirationDateCustomAttribute.getName());
        expirationDateAttributeDto.setContent(List.of(new DateAttributeContent(LocalDate.now())));

        List<RequestAttributeDto> departmentAttributeDtoList = List.of(departmentAttributeDto);
        Assertions.assertThrows(ValidationException.class, () -> attributeEngine.validateCustomAttributesContent(Resource.CONNECTOR, departmentAttributeDtoList), "Custom attribute content should not be updated to resource not assigned");
        List<RequestAttributeDto> departmentExpirationDateList = List.of(departmentAttributeDto, expirationDateAttributeDto);
        Assertions.assertThrows(ValidationException.class, () -> attributeEngine.validateCustomAttributesContent(Resource.CERTIFICATE, departmentExpirationDateList), "Read-only attribute content should not be able to be changed");

        expirationDateAttributeDto.setContent(List.of(new IntegerAttributeContent(100)));
        Assertions.assertThrows(ValidationException.class, () -> attributeEngine.validateCustomAttributesContent(Resource.CERTIFICATE, departmentExpirationDateList), "Mismatch between content types");

        expirationDateAttributeDto.setContent(List.of(new DateAttributeContent(LocalDate.EPOCH)));
        List<RequestAttributeDto> expirationDateAttributeDtoList = List.of(expirationDateAttributeDto);
        Assertions.assertThrows(ValidationException.class, () -> attributeEngine.validateCustomAttributesContent(Resource.CERTIFICATE, expirationDateAttributeDtoList), "Missing content for required custom attribute");

        // the following should not throw any exception, we cannot update read-only attributes
        UUID certificateUuid = certificate.getUuid();
        Assertions.assertThrows(ValidationException.class, () -> attributeEngine.updateObjectCustomAttributesContent(Resource.CERTIFICATE, certificateUuid, departmentExpirationDateList), "Read-only attribute content should not be able to be changed");
    }

    @Test
    void validateCodeBlockAttributeContent() throws AttributeException {
        DataAttribute codeBlockData = new DataAttribute();
        codeBlockData.setContentType(AttributeContentType.CODEBLOCK);
        codeBlockData.setUuid(UUID.randomUUID().toString());
        codeBlockData.setName("testAttribute");
        codeBlockData.setType(AttributeType.DATA);

        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel("Test Label");
        props.setRequired(true);
        props.setReadOnly(false);
        props.setVisible(false);
        props.setList(false);
        codeBlockData.setProperties(props);
        UUID connectorUuid = connectorAuthority.getUuid();
        List<BaseAttribute> codeBlockDataList = List.of(codeBlockData);
        attributeEngine.updateDataAttributeDefinitions(connectorUuid, null, codeBlockDataList);

        RequestAttributeDto requestAttribute = new RequestAttributeDto();
        requestAttribute.setUuid(codeBlockData.getUuid());
        requestAttribute.setName(codeBlockData.getName());
        requestAttribute.setContentType(codeBlockData.getContentType());
        requestAttribute.setContent(List.of(new StringAttributeContent("bad content")));

        List<RequestAttributeDto> requestAttributeList = List.of(requestAttribute);
        Assertions.assertThrows(ValidationException.class, () -> attributeEngine.validateUpdateDataAttributes(connectorUuid, null, codeBlockDataList, requestAttributeList));

        CodeBlockAttributeContent attributeContent = new CodeBlockAttributeContent();
        attributeContent.setData(new CodeBlockAttributeContentData(null, ""));
        requestAttribute.setContent(List.of(attributeContent));
        Assertions.assertThrows(ValidationException.class, () -> attributeEngine.validateUpdateDataAttributes(connectorUuid, null, codeBlockDataList, requestAttributeList));

        attributeContent.setData(new CodeBlockAttributeContentData(ProgrammingLanguageEnum.PYTHON, "abc"));
        requestAttribute.setContent(List.of(attributeContent));
        Assertions.assertDoesNotThrow(() -> attributeEngine.validateUpdateDataAttributes(connectorUuid, null, codeBlockDataList, requestAttributeList));
    }

    @Test
    void testDeleteAllObjectAttributeContent() throws NotFoundException, CertificateException, IOException {
        attributeEngine.deleteAllObjectAttributeContent(Resource.CERTIFICATE, certificate.getUuid());
        CertificateDetailDto certificateDetailDto = certificateService.getCertificate(SecuredUUID.fromUUID(certificate.getUuid()));

        Assertions.assertTrue(certificateDetailDto.getMetadata().isEmpty());
        Assertions.assertTrue(certificateDetailDto.getCustomAttributes().isEmpty());
    }

    @Test
    void testDeleteObjectCustomAttributesContent() throws NotFoundException, CertificateException, IOException, AttributeException {
        RequestAttributeDto departmentAttributeDto = new RequestAttributeDto();
        departmentAttributeDto.setUuid(departmentCustomAttribute.getUuid());
        departmentAttributeDto.setName(departmentCustomAttribute.getName());
        departmentAttributeDto.setContent(List.of(new StringAttributeContent("Sales")));

        RequestAttributeDto orderNoAttributeDto = new RequestAttributeDto();
        orderNoAttributeDto.setUuid(orderNoCustomAttribute.getUuid());
        orderNoAttributeDto.setName(orderNoCustomAttribute.getName());
        orderNoAttributeDto.setContent(List.of(new FloatAttributeContent(555f)));
        attributeEngine.updateObjectCustomAttributesContent(Resource.CERTIFICATE, certificate.getUuid(), List.of(departmentAttributeDto, orderNoAttributeDto));

        SecurityResourceFilter filter = new SecurityResourceFilter(List.of(departmentCustomAttribute.getUuid()), List.of(), true);
        attributeEngine.deleteObjectAllowedCustomAttributeContent(filter, Resource.CERTIFICATE, certificate.getUuid());
        CertificateDetailDto certificateDetailDto = certificateService.getCertificate(SecuredUUID.fromUUID(certificate.getUuid()));
        Assertions.assertEquals(1, certificateDetailDto.getCustomAttributes().size());
        Assertions.assertEquals(orderNoCustomAttribute.getUuid(), certificateDetailDto.getCustomAttributes().getFirst().getUuid());

        filter = new SecurityResourceFilter(List.of(), List.of(expirationDateCustomAttribute.getUuid(), orderNoCustomAttribute.getUuid()), false);
        attributeEngine.deleteObjectAllowedCustomAttributeContent(filter, Resource.CERTIFICATE, certificate.getUuid());
        certificateDetailDto = certificateService.getCertificate(SecuredUUID.fromUUID(certificate.getUuid()));
        Assertions.assertEquals(1, certificateDetailDto.getCustomAttributes().size());
        Assertions.assertEquals(orderNoCustomAttribute.getUuid(), certificateDetailDto.getCustomAttributes().getFirst().getUuid());
    }

    @Test
    void testDeleteDefinitionAttributeContent() throws NotFoundException, CertificateException, IOException {
        attributeEngine.deleteAttributeDefinition(AttributeType.CUSTOM, UUID.fromString(departmentCustomAttribute.getUuid()));
        CertificateDetailDto certificateDetailDto = certificateService.getCertificate(SecuredUUID.fromUUID(certificate.getUuid()));

        Assertions.assertEquals(3, certificateDetailDto.getMetadata().size());
        Assertions.assertTrue(certificateDetailDto.getCustomAttributes().isEmpty());

        attributeEngine.deleteAttributeDefinition(AttributeType.META, connectorDiscovery.getUuid(), UUID.fromString(networkDiscoveryMeta.getUuid()), networkDiscoveryMeta.getName());

        Assertions.assertFalse(certificateDetailDto.getMetadata().isEmpty());
        Assertions.assertTrue(certificateDetailDto.getCustomAttributes().isEmpty());

        certificateDetailDto = certificateService.getCertificate(SecuredUUID.fromUUID(certificate.getUuid()));
        Assertions.assertEquals(2, certificateDetailDto.getMetadata().size());
        Assertions.assertTrue(certificateDetailDto.getCustomAttributes().isEmpty());
    }

    private void loadCustomAttributesData() throws AttributeException, NotFoundException {
        departmentCustomAttribute = new CustomAttribute();
        departmentCustomAttribute.setUuid(UUID.randomUUID().toString());
        departmentCustomAttribute.setName("department");
        departmentCustomAttribute.setType(AttributeType.CUSTOM);
        departmentCustomAttribute.setContentType(AttributeContentType.STRING);

        CustomAttributeProperties customProps1 = new CustomAttributeProperties();
        customProps1.setLabel("Department");
        customProps1.setRequired(true);
        departmentCustomAttribute.setProperties(customProps1);

        orderNoCustomAttribute = new CustomAttribute();
        orderNoCustomAttribute.setUuid(UUID.randomUUID().toString());
        orderNoCustomAttribute.setName("order_no");
        orderNoCustomAttribute.setType(AttributeType.CUSTOM);
        orderNoCustomAttribute.setContentType(AttributeContentType.FLOAT);

        CustomAttributeProperties customProps2 = new CustomAttributeProperties();
        customProps2.setLabel("Order number");
        orderNoCustomAttribute.setProperties(customProps2);

        expirationDateCustomAttribute = new CustomAttribute();
        expirationDateCustomAttribute.setUuid(UUID.randomUUID().toString());
        expirationDateCustomAttribute.setName("expiration_date");
        expirationDateCustomAttribute.setType(AttributeType.CUSTOM);
        expirationDateCustomAttribute.setContentType(AttributeContentType.DATE);
        expirationDateCustomAttribute.setContent(List.of(new DateAttributeContent(LocalDate.EPOCH)));

        CustomAttributeProperties customProps3 = new CustomAttributeProperties();
        customProps3.setLabel("Expiration date");
        customProps3.setReadOnly(true);
        expirationDateCustomAttribute.setProperties(customProps3);

        attributeEngine.updateCustomAttributeDefinition(orderNoCustomAttribute, List.of(Resource.CERTIFICATE));
        attributeEngine.updateCustomAttributeDefinition(departmentCustomAttribute, List.of(Resource.CERTIFICATE, Resource.AUTHORITY));
        attributeEngine.updateCustomAttributeDefinition(expirationDateCustomAttribute, List.of(Resource.CERTIFICATE));

        RequestAttributeDto departmentAttributeDto = new RequestAttributeDto();
        departmentAttributeDto.setUuid(departmentCustomAttribute.getUuid());
        departmentAttributeDto.setName(departmentCustomAttribute.getName());
        departmentAttributeDto.setContent(List.of(new StringAttributeContent("Sales")));
        attributeEngine.updateObjectCustomAttributesContent(Resource.CERTIFICATE, certificate.getUuid(), List.of(departmentAttributeDto));
    }

    @Test
    void testNotUpdatingAttributeDefinitionWhenLoading() {
        AttributeDefinition attributeDefinition = getAttributeDefinition("name", "label", AttributeType.CUSTOM, departmentCustomAttribute);
        LocalDateTime updatedAt = attributeDefinition.getUpdatedAt();

        AttributeRelation attributeRelation = new AttributeRelation();
        attributeRelation.setAttributeDefinition(attributeDefinition);
        attributeRelation.setResource(Resource.ACME_ACCOUNT);
        attributeRelationRepository.save(attributeRelation);

        attributeEngine.getCustomAttributesByResource(attributeRelation.getResource(), SecurityResourceFilter.create());
        attributeDefinition = attributeDefinitionRepository.findByAttributeUuid(attributeDefinition.getAttributeUuid()).get();
        Assertions.assertEquals(updatedAt.truncatedTo(ChronoUnit.MILLIS), attributeDefinition.getUpdatedAt().truncatedTo(ChronoUnit.MILLIS));

        DataAttribute dataAttribute = new DataAttribute();
        dataAttribute.setContentType(AttributeContentType.STRING);
        dataAttribute.setContent(List.of(new BaseAttributeContent<>("ref", "data")));
        AttributeDefinition attributeDataDefinition = getAttributeDefinition("nameData", "labelData", AttributeType.DATA, dataAttribute);
        LocalDateTime updatedAtData = attributeDataDefinition.getUpdatedAt();

        attributeEngine.getDataAttributeDefinition(null, attributeDataDefinition.getName());
        attributeDataDefinition = attributeDefinitionRepository.findByAttributeUuid(attributeDataDefinition.getAttributeUuid()).get();
        Assertions.assertEquals(updatedAtData.truncatedTo(ChronoUnit.MILLIS), attributeDataDefinition.getUpdatedAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @NotNull
    private AttributeDefinition getAttributeDefinition(String name, String label, AttributeType attributeType, BaseAttribute definition) {
        AttributeDefinition attributeDefinition = new AttributeDefinition();
        attributeDefinition.setName(name);
        attributeDefinition.setAttributeUuid(UUID.randomUUID());
        attributeDefinition.setLabel(label);
        attributeDefinition.setType(attributeType);
        attributeDefinition.setContentType(AttributeContentType.STRING);
        attributeDefinition.setDefinition(definition);
        attributeDefinitionRepository.save(attributeDefinition);
        return attributeDefinition;
    }

    private void loadMetadata() throws AttributeException {
        networkDiscoveryMeta = new MetadataAttribute();
        networkDiscoveryMeta.setName("discoverySource");
        networkDiscoveryMeta.setUuid("000043aa-6022-11ed-9b6a-0242ac120002");
        networkDiscoveryMeta.setContentType(AttributeContentType.STRING);
        networkDiscoveryMeta.setType(AttributeType.META);
        networkDiscoveryMeta.setDescription("Source from where the certificate is discovered");
        MetadataAttributeProperties metaProps1 = new MetadataAttributeProperties();
        metaProps1.setLabel("Discovery Source");
        metaProps1.setVisible(true);
        metaProps1.setGlobal(true);
        networkDiscoveryMeta.setProperties(metaProps1);
        networkDiscoveryMeta.setContent(List.of(new StringAttributeContent("localhost:0443")));
        attributeEngine.updateMetadataAttribute(networkDiscoveryMeta, new ObjectAttributeContentInfo(connectorDiscovery.getUuid(), Resource.CERTIFICATE, certificate.getUuid(), Resource.DISCOVERY, networkDiscoveryUuid));

        MetadataAttribute authorityDiscoveryMeta = new MetadataAttribute();
        authorityDiscoveryMeta.setName("username");
        authorityDiscoveryMeta.setUuid("df2fbaa2-60fd-11ed-9b6a-0242ac120002");
        authorityDiscoveryMeta.setContentType(AttributeContentType.STRING);
        authorityDiscoveryMeta.setType(AttributeType.META);
        authorityDiscoveryMeta.setDescription("Username of certificate");
        MetadataAttributeProperties metaProps2 = new MetadataAttributeProperties();
        metaProps2.setLabel("Username");
        metaProps2.setVisible(true);
        authorityDiscoveryMeta.setProperties(metaProps2);
        authorityDiscoveryMeta.setContent(List.of(new StringAttributeContent("tst-username")));
        attributeEngine.updateMetadataAttribute(authorityDiscoveryMeta, new ObjectAttributeContentInfo(connectorAuthority.getUuid(), Resource.CERTIFICATE, certificate.getUuid(), Resource.DISCOVERY, authorityDiscoveryUuid));

        MetadataAttribute authorityIssueMeta = new MetadataAttribute();
        authorityIssueMeta.setUuid("b42ab690-60fd-11ed-9b6a-0242ac120002");
        authorityIssueMeta.setName("ejbcaUsername");
        authorityIssueMeta.setDescription("EJBCA Username");
        authorityIssueMeta.setType(AttributeType.META);
        authorityIssueMeta.setContentType(AttributeContentType.STRING);
        authorityIssueMeta.setContent(List.of(new StringAttributeContent("tst-ejbcaUsername==")));
        MetadataAttributeProperties metaProps3 = new MetadataAttributeProperties();
        metaProps3.setVisible(true);
        metaProps3.setLabel("EJBCA Username");
        authorityIssueMeta.setProperties(metaProps3);
        attributeEngine.updateMetadataAttribute(authorityIssueMeta, new ObjectAttributeContentInfo(connectorAuthority.getUuid(), Resource.CERTIFICATE, certificate.getUuid()));
    }

    @Test
    void getRequestDataAttributesContentReturnsCorrectResponse() throws AttributeException {
        // Arrange
        DataAttribute dataAttribute = new DataAttribute();
        dataAttribute.setUuid(UUID.randomUUID().toString());
        dataAttribute.setName("testAttribute");
        dataAttribute.setType(AttributeType.DATA);
        dataAttribute.setContentType(AttributeContentType.STRING);

        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel("Test Label");
        props.setRequired(true);
        props.setReadOnly(false);
        props.setVisible(false);
        props.setList(false);
        dataAttribute.setProperties(props);
        attributeEngine.updateDataAttributeDefinitions(connectorAuthority.getUuid(), null, List.of(dataAttribute));

        RequestAttributeDto requestAttribute = new RequestAttributeDto();
        requestAttribute.setUuid(dataAttribute.getUuid());
        requestAttribute.setName(dataAttribute.getName());
        requestAttribute.setContentType(dataAttribute.getContentType());
        requestAttribute.setContent(List.of(new StringAttributeContent("testValue")));

        // Act
        List<ResponseAttributeDto> responseAttributes = AttributeEngine.getRequestDataAttributesContent(List.of(dataAttribute), List.of(requestAttribute));

        // Assert
        Assertions.assertEquals(1, responseAttributes.size());
        Assertions.assertEquals(dataAttribute.getUuid(), responseAttributes.getFirst().getUuid());
        Assertions.assertEquals(dataAttribute.getName(), responseAttributes.getFirst().getName());
        Assertions.assertEquals(dataAttribute.getProperties().getLabel(), responseAttributes.getFirst().getLabel());
        Assertions.assertEquals(requestAttribute.getContent(), responseAttributes.getFirst().getContent());
    }

    @Test
    void validateRequestDataAttributesThrowsValidationExceptionForMissingRequiredAttributes() throws AttributeException {
        // Arrange
        DataAttribute requiredAttribute = new DataAttribute();
        requiredAttribute.setUuid(UUID.randomUUID().toString());
        requiredAttribute.setName("requiredAttribute");
        requiredAttribute.setType(AttributeType.DATA);
        requiredAttribute.setContentType(AttributeContentType.STRING);
        RegexpAttributeConstraint constraint = new RegexpAttributeConstraint();
        constraint.setData("^[a-zA-Z]+$");
        requiredAttribute.setConstraints(List.of(constraint));

        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel("Required Label");
        props.setRequired(true);
        props.setReadOnly(false);
        props.setVisible(false);
        props.setList(false);
        requiredAttribute.setProperties(props);
        UUID connectorUuid = connectorAuthority.getUuid();
        List<BaseAttribute> requiredAttributeList = List.of(requiredAttribute);
        attributeEngine.updateDataAttributeDefinitions(connectorUuid, null, requiredAttributeList);

        RequestAttributeDto requestAttribute = new RequestAttributeDto();
        requestAttribute.setUuid(UUID.randomUUID().toString());
        requestAttribute.setName("unrelatedAttribute");
        requestAttribute.setContentType(AttributeContentType.STRING);
        requestAttribute.setContent(List.of(new StringAttributeContent("value")));

        // Act
        List<RequestAttributeDto> requestAttributeList = List.of(requestAttribute);
        Executable executable = () -> AttributeEngine.validateRequestDataAttributes(requiredAttributeList, requestAttributeList, true);

        // Assert
        Assertions.assertThrows(ValidationException.class, executable);

        // Test constraints

        requestAttribute.setUuid(requiredAttribute.getUuid());
        requestAttribute.setName(requiredAttribute.getName());
        Assertions.assertDoesNotThrow(() -> attributeEngine.validateUpdateDataAttributes(connectorUuid, null, requiredAttributeList, requestAttributeList));

        requestAttribute.setContent(List.of(new StringAttributeContent("value123")));
        Assertions.assertThrows(ValidationException.class, () -> attributeEngine.validateUpdateDataAttributes(connectorUuid, null, requiredAttributeList, requestAttributeList));

    }

    @Test
    void validateRequestDataAttributesPassesForValidAttributes() throws AttributeException {
        // Arrange
        DataAttribute validAttribute = new DataAttribute();
        validAttribute.setUuid(UUID.randomUUID().toString());
        validAttribute.setName("validAttribute");
        validAttribute.setType(AttributeType.DATA);
        validAttribute.setContentType(AttributeContentType.STRING);

        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel("Valid Label");
        props.setRequired(true);
        props.setReadOnly(false);
        props.setVisible(false);
        props.setList(false);
        validAttribute.setProperties(props);

        attributeEngine.updateDataAttributeDefinitions(connectorAuthority.getUuid(), null, List.of(validAttribute));

        RequestAttributeDto requestAttribute = new RequestAttributeDto();
        requestAttribute.setUuid(validAttribute.getUuid());
        requestAttribute.setName(validAttribute.getName());
        requestAttribute.setContentType(validAttribute.getContentType());
        requestAttribute.setContent(List.of(new StringAttributeContent("validValue")));

        // Act & Assert
        Assertions.assertDoesNotThrow(() -> AttributeEngine.validateRequestDataAttributes(List.of(validAttribute), List.of(requestAttribute), true));
    }
}
