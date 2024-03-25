package com.czertainly.core.evaluator;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.rules.RuleActionType;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.ResourceService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

public class RuleEvaluatorTest extends BaseSpringBootTest {

    @Autowired
    private RuleEvaluator<Certificate> certificateRuleEvaluator;
    @Autowired
    private RuleEvaluator<CryptographicKeyItem> cryptographicKeyRuleEvaluator;

    @Autowired
    private RuleEvaluator<DiscoveryHistory> discoveryHistoryRuleEvaluator;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private AttributeService attributeService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private AttributeEngine attributeEngine;

    private Certificate certificate;

    private RuleCondition condition;

    private RuleTrigger trigger;
    private RuleAction action;
    @Autowired
    private ActionEvaluator<Certificate> certificateActionEvaluator;



    @BeforeEach
    public void setUp() {
        certificate = new Certificate();
        certificateRepository.save(certificate);
        condition = new RuleCondition();

        trigger = new RuleTrigger();
        trigger.setResource(Resource.CERTIFICATE);

        action = new RuleAction();
        trigger.setActions(List.of(action));
    }

    @Test
    public void testCertificateEvaluatorOnProperties() throws RuleException {

        certificate.setCommonName("Common Name");
        condition.setFieldSource(FilterFieldSource.PROPERTY);
        condition.setFieldIdentifier("commonName");
        condition.setOperator(FilterConditionOperator.NOT_EMPTY);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
        condition.setValue("Common Name");
        condition.setOperator(FilterConditionOperator.EQUALS);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
        condition.setOperator(FilterConditionOperator.NOT_EQUALS);
        certificate.setCommonName("Common NameE");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
        condition.setOperator(FilterConditionOperator.CONTAINS);
        condition.setValue("Name");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
        condition.setOperator(FilterConditionOperator.NOT_CONTAINS);
        condition.setValue("abc");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
        condition.setOperator(FilterConditionOperator.STARTS_WITH);
        condition.setValue("Comm");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
        condition.setOperator(FilterConditionOperator.ENDS_WITH);
        condition.setValue("eE");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
        certificate.setCommonName(null);
        condition.setOperator(FilterConditionOperator.EMPTY);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));

        Group group = new Group();
        group.setName("group");

        certificate.setGroup(group);

        condition.setOperator(FilterConditionOperator.EQUALS);
        condition.setFieldIdentifier("group.name");
        condition.setValue("group");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));


        certificate.setTrustedCa(true);
        condition.setFieldIdentifier("trustedCa");
        condition.setOperator(FilterConditionOperator.EQUALS);
        condition.setValue(true);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));


    }

    @Test
    public void testExceptions() throws RuleException, ParseException {
        Rule rule = new Rule();
        rule.setResource(Resource.CRYPTOGRAPHIC_KEY);
        Assertions.assertFalse(certificateRuleEvaluator.evaluateRules(List.of(rule), certificate));

        condition.setFieldIdentifier("invalid");
        condition.setFieldSource(FilterFieldSource.PROPERTY);
        Assertions.assertThrows(RuleException.class, () -> certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));

        condition.setFieldIdentifier("commonName");
        condition.setFieldSource(FilterFieldSource.PROPERTY);
        condition.setOperator(FilterConditionOperator.GREATER);
        Assertions.assertThrows(RuleException.class, () -> certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));

        condition.setValue(123);
        condition.setOperator(FilterConditionOperator.CONTAINS);
        Assertions.assertThrows(RuleException.class, () -> certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));

        condition.setFieldIdentifier("expiryInDays");
        condition.setOperator(FilterConditionOperator.GREATER);
        condition.setValue(1);
        certificate.setNotAfter(new SimpleDateFormat(("dd.MM.yyyy")).parse("01.01.5000"));
        Assertions.assertThrows(RuleException.class, () -> certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
    }


    @Test
    public void testEvaluatorDate() throws RuleException, ParseException {
        certificate.setNotBefore(new SimpleDateFormat(("yyyy-MM-dd HH:mm:ss")).parse("2019-12-01 22:10:15"));
        condition.setFieldSource(FilterFieldSource.PROPERTY);
        condition.setFieldIdentifier("notBefore");
        condition.setValue("2010-12-12");
        condition.setOperator(FilterConditionOperator.GREATER);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));

        DiscoveryHistory discovery = new DiscoveryHistory();
        discovery.setStartTime(new SimpleDateFormat(("yyyy-MM-dd HH:mm:ss")).parse("2019-12-01 22:10:15"));
        condition.setFieldIdentifier("startTime");
        condition.setValue("2019-12-01T22:10:00.274+00:00");
        Assertions.assertTrue(discoveryHistoryRuleEvaluator.evaluateCondition(condition, discovery, Resource.DISCOVERY));

    }


    @Test
    public void testsCryptographicKeyRuleEvaluator() throws RuleException {
        CryptographicKeyItem cryptographicKey = new CryptographicKeyItem();
        cryptographicKey.setName("Key");
        RuleCondition condition = new RuleCondition();
        condition.setFieldSource(FilterFieldSource.PROPERTY);
        condition.setFieldIdentifier("name");
        condition.setOperator(FilterConditionOperator.NOT_EMPTY);
        Assertions.assertTrue(cryptographicKeyRuleEvaluator.evaluateCondition(condition, cryptographicKey, Resource.CRYPTOGRAPHIC_KEY));
        cryptographicKey.setLength(256);
        condition.setFieldIdentifier("length");
        condition.setOperator(FilterConditionOperator.GREATER);
        condition.setValue(255);
        Assertions.assertTrue(cryptographicKeyRuleEvaluator.evaluateCondition(condition, cryptographicKey, Resource.CRYPTOGRAPHIC_KEY));
        condition.setValue(255.4);
        Assertions.assertTrue(cryptographicKeyRuleEvaluator.evaluateCondition(condition, cryptographicKey, Resource.CRYPTOGRAPHIC_KEY));


    }

    @Test
    public void testCertificateRuleEvaluatorCustomAttributes() throws AlreadyExistException, NotFoundException, RuleException, AttributeException {
        Certificate certificate = new Certificate();
        certificateRepository.save(certificate);

        CustomAttributeCreateRequestDto customAttributeRequest = new CustomAttributeCreateRequestDto();
        customAttributeRequest.setName("custom");
        customAttributeRequest.setLabel("custom");
        customAttributeRequest.setResources(List.of(Resource.CERTIFICATE));
        customAttributeRequest.setContentType(AttributeContentType.STRING);

        CustomAttributeDefinitionDetailDto customAttribute = attributeService.createCustomAttribute(customAttributeRequest);
        resourceService.updateAttributeContentForObject(Resource.CERTIFICATE, SecuredUUID.fromUUID(certificate.getUuid()), UUID.fromString(customAttribute.getUuid()),
                List.of(new StringAttributeContent("ref", "data1"), new StringAttributeContent("ref", "data")));

        RuleCondition condition = new RuleCondition();
        condition.setFieldSource(FilterFieldSource.CUSTOM);
        condition.setFieldIdentifier("custom");
        condition.setOperator(FilterConditionOperator.EQUALS);
        condition.setValue("data");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));
    }

    @Test
    public void testCertificateRuleEvaluatorMeta() throws RuleException, AttributeException {
        Certificate certificate = new Certificate();
        certificateRepository.save(certificate);
        Connector connector = new Connector();
        connectorRepository.save(connector);
        UUID connectorUuid = connector.getUuid();
        MetadataAttribute metadataAttribute = new MetadataAttribute();
        metadataAttribute.setContentType(AttributeContentType.STRING);
        metadataAttribute.setName("meta");
        metadataAttribute.setUuid(UUID.randomUUID().toString());
        metadataAttribute.setContent(List.of(new StringAttributeContent("ref", "data")));
        metadataAttribute.setType(AttributeType.META);

        MetadataAttributeProperties props = new MetadataAttributeProperties();
        props.setLabel("Test meta");
        metadataAttribute.setProperties(props);

        attributeEngine.updateMetadataAttributes(List.of(metadataAttribute), new ObjectAttributeContentInfo(connectorUuid, Resource.CERTIFICATE, certificate.getUuid()));

        RuleCondition condition = new RuleCondition();
        condition.setFieldSource(FilterFieldSource.META);
        condition.setFieldIdentifier("meta|STRING");
        condition.setOperator(FilterConditionOperator.EQUALS);
        condition.setValue("data");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate, Resource.CERTIFICATE));

    }

    @Test
    public void testSetProperty() throws ActionException, NotFoundException, AttributeException {
        action.setActionType(RuleActionType.SET_FIELD);
        action.setFieldSource(FilterFieldSource.PROPERTY);
        action.setFieldIdentifier("state");
        action.setActionData(CertificateState.ISSUED);
        certificateRuleEvaluator.performRuleActions(trigger, certificate);
        Assertions.assertEquals(CertificateState.ISSUED, certificate.getState());
    }

    @Test
    public void testSetCustomAttribute() throws AlreadyExistException, AttributeException, ActionException, NotFoundException {
        CustomAttributeCreateRequestDto createRequestDto = new CustomAttributeCreateRequestDto();
        createRequestDto.setName("custom");
        createRequestDto.setContentType(AttributeContentType.STRING);
        createRequestDto.setLabel("custom");
        createRequestDto.setResources(List.of(Resource.CERTIFICATE));
        attributeService.createCustomAttribute(createRequestDto);
        List<BaseAttributeContent> baseAttributeContents = List.of(new StringAttributeContent("ref", "data1"), new StringAttributeContent("ref", "data"));
        String content = AttributeDefinitionUtils.serializeAttributeContent(baseAttributeContents);
        action.setActionType(RuleActionType.SET_FIELD);
        action.setFieldSource(FilterFieldSource.CUSTOM);
        action.setActionData(content);
        action.setFieldIdentifier("custom");
        certificateRuleEvaluator.performRuleActions(trigger, certificate);
        List<ResponseAttributeDto> responseAttributeDtos = attributeEngine.getObjectCustomAttributesContent(Resource.CERTIFICATE, certificate.getUuid());
        Assertions.assertEquals(baseAttributeContents, responseAttributeDtos.get(0).getContent());

    }

}
