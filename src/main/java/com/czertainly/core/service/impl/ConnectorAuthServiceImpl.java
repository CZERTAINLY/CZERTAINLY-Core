package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttributeV2;
import com.czertainly.api.model.common.attribute.v2.DataAttributeV2;
import com.czertainly.api.model.common.attribute.v2.content.*;
import com.czertainly.api.model.common.attribute.v2.content.FileAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v3.DataAttributeV3;
import com.czertainly.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.core.service.ConnectorAuthService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.util.*;

import static com.czertainly.api.clients.BaseApiClient.*;

@Service
@Transactional
public class ConnectorAuthServiceImpl implements ConnectorAuthService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorAuthServiceImpl.class);

    private static final ArrayList<String> SUPPORTED_KEY_STORE_TYPES = new ArrayList<>(List.of("PKCS12", "JKS"));

    @Override
    public Set<AuthType> getAuthenticationTypes() {
        return EnumSet.allOf(AuthType.class);
    }

    @Override
    public List<DataAttributeV3> getAuthAttributes(AuthType authenticationType) {
        switch (authenticationType) {
            case NONE:
                return List.of();
            case BASIC:
                return getBasicAuthAttributes();
            case CERTIFICATE:
                return getCertificateAttributes();
            case API_KEY:
                return getApiKeyAuthAttributes();
            case JWT:
                return getJWTAuthAttributes();
            default:
                throw new IllegalArgumentException("Unknown auth type: " + authenticationType);
        }
    }

    @Override
    public boolean validateAuthAttributes(AuthType authenticationType, List<RequestAttributeDto> attributes) {
        switch (authenticationType) {
            case NONE:
                return true;
            case BASIC:
                return validateBasicAuthAttributes(attributes);
            case CERTIFICATE:
                return validateCertificateAttributes(attributes);
            case API_KEY:
                return validateApiKeyAuthAttributes(attributes);
            case JWT:
                return validateJWTAuthAttributes(attributes);
            default:
                throw new IllegalArgumentException("Unknown auth type: " + authenticationType);
        }
    }

    @Override
    public List<BaseAttribute> mergeAndValidateAuthAttributes(AuthType authenticationType, List<ResponseAttributeDto> attributes) {
        if (authenticationType == null || attributes == null) {
            return List.of();
        }

        List<DataAttributeV3> definitions = getAuthAttributes(authenticationType);
        List<BaseAttribute> merged = AttributeDefinitionUtils.mergeAttributes(definitions, AttributeDefinitionUtils.getClientAttributes(attributes));

        return merged;
    }

    @Override
    public List<DataAttributeV3> getBasicAuthAttributes() {
        List<DataAttributeV3> attrs = new ArrayList<>();

        DataAttributeV3 username = new DataAttributeV3();
        username.setUuid("fe2d6d35-fb3d-4ea0-9f0b-7e39be93beeb");
        username.setName(ATTRIBUTE_USERNAME);
        username.setType(AttributeType.DATA);
        username.setContentType(AttributeContentType.STRING);

        DataAttributeProperties usernameProperties = new DataAttributeProperties();
        usernameProperties.setRequired(true);
        usernameProperties.setReadOnly(false);
        usernameProperties.setVisible(true);
        username.setProperties(usernameProperties);
        attrs.add(username);

        DataAttributeV3 password = new DataAttributeV3();
        password.setUuid("04506d45-c865-4ddc-b6fc-117ee5d5c8e7");
        password.setName(ATTRIBUTE_PASSWORD);
        password.setType(AttributeType.DATA);
        password.setContentType(AttributeContentType.SECRET);

        DataAttributeProperties pwdProperties = new DataAttributeProperties();
        pwdProperties.setRequired(true);
        pwdProperties.setReadOnly(false);
        pwdProperties.setVisible(true);
        password.setProperties(pwdProperties);

        attrs.add(password);

        return attrs;
    }

    @Override
    public Boolean validateBasicAuthAttributes(List<RequestAttributeDto> attributes) {
        AttributeDefinitionUtils.validateAttributes(getBasicAuthAttributes(), attributes);
        return true;
    }

    @Override
    public List<DataAttributeV3> getCertificateAttributes() {
        List<DataAttributeV3> attrs = new ArrayList<>();

        DataAttributeV3 keyStoreType = new DataAttributeV3();
        keyStoreType.setUuid("e334e055-900e-43f1-aedc-54e837028de0");
        keyStoreType.setName(ATTRIBUTE_KEYSTORE_TYPE);
        keyStoreType.setType(AttributeType.DATA);
        keyStoreType.setContentType(AttributeContentType.STRING);

        DataAttributeProperties kstProperties = new DataAttributeProperties();
        kstProperties.setList(true);
        kstProperties.setRequired(true);
        kstProperties.setReadOnly(false);
        kstProperties.setVisible(true);
        keyStoreType.setProperties(kstProperties);

        List<BaseAttributeContentV3> base = new ArrayList<>();
        SUPPORTED_KEY_STORE_TYPES.forEach(e -> {
            base.add(new StringAttributeContentV3(e));
        });

        keyStoreType.setContent(base);
        attrs.add(keyStoreType);

        DataAttributeV3 keyStore = new DataAttributeV3();
        keyStore.setUuid("6df7ace9-c501-4d58-953c-f8d53d4fb378");
        keyStore.setName(ATTRIBUTE_KEYSTORE);
        keyStore.setType(AttributeType.DATA);
        keyStore.setContentType(AttributeContentType.FILE);

        DataAttributeProperties ksProperties = new DataAttributeProperties();
        ksProperties.setRequired(true);
        ksProperties.setReadOnly(false);
        ksProperties.setVisible(true);
        keyStore.setProperties(ksProperties);

        attrs.add(keyStore);

        DataAttributeV3 keyStorePassword = new DataAttributeV3();
        keyStorePassword.setUuid("d975fe42-9d09-4740-a362-fc26f98e55ea");
        keyStorePassword.setName(ATTRIBUTE_KEYSTORE_PASSWORD);
        keyStorePassword.setType(AttributeType.DATA);
        keyStorePassword.setContentType(AttributeContentType.SECRET);

        DataAttributeProperties kspProperties = new DataAttributeProperties();
        kspProperties.setRequired(true);
        kspProperties.setReadOnly(false);
        kspProperties.setVisible(true);
        keyStorePassword.setProperties(kspProperties);

        attrs.add(keyStorePassword);

        DataAttributeV3 trustStoreType = new DataAttributeV3();
        trustStoreType.setUuid("c4454807-805a-44e2-81d1-94b56e993786");
        trustStoreType.setName(ATTRIBUTE_TRUSTSTORE_TYPE);
        trustStoreType.setType(AttributeType.DATA);
        trustStoreType.setContentType(AttributeContentType.STRING);

        DataAttributeProperties tstProperties = new DataAttributeProperties();
        tstProperties.setList(true);
        tstProperties.setRequired(false);
        tstProperties.setReadOnly(false);
        tstProperties.setVisible(true);
        trustStoreType.setProperties(tstProperties);

        trustStoreType.setContent(base);
        attrs.add(trustStoreType);

        DataAttributeV3 trustStore = new DataAttributeV3();
        trustStore.setUuid("6a245220-eaf4-44cb-9079-2228ad9264f5");
        trustStore.setName(ATTRIBUTE_TRUSTSTORE);
        trustStore.setType(AttributeType.DATA);
        trustStore.setContentType(AttributeContentType.FILE);

        DataAttributeProperties tsProperties = new DataAttributeProperties();
        tsProperties.setRequired(false);
        tsProperties.setReadOnly(false);
        tsProperties.setVisible(true);
        trustStore.setProperties(tsProperties);

        attrs.add(trustStore);

        DataAttributeV3 trustStorePassword = new DataAttributeV3();
        trustStorePassword.setUuid("85a874da-1413-4770-9830-4188a37c95ee");
        trustStorePassword.setName(ATTRIBUTE_TRUSTSTORE_PASSWORD);
        trustStorePassword.setType(AttributeType.DATA);
        trustStorePassword.setContentType(AttributeContentType.SECRET);

        DataAttributeProperties tspProperties = new DataAttributeProperties();
        tspProperties.setRequired(false);
        tspProperties.setReadOnly(false);
        tspProperties.setVisible(true);
        trustStorePassword.setProperties(tspProperties);

        attrs.add(trustStorePassword);

        return attrs;
    }

    @Override
    public Boolean validateCertificateAttributes(List<RequestAttributeDto> attributes) {
        AttributeDefinitionUtils.validateAttributes(getCertificateAttributes(), attributes);

        try {
            FileAttributeContentV2 keyStoreBase64 = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_KEYSTORE, attributes, true);
            byte[] keyStoreBytes = Base64.getDecoder().decode(keyStoreBase64.getData().getContent());

            StringAttributeContentV2 keyStoreType = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_KEYSTORE_TYPE, attributes, true);
            StringAttributeContentV2 keyStorePassword = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_KEYSTORE_PASSWORD, attributes, true);

            KeyStore keyStore = KeyStore.getInstance(keyStoreType.getData());
            keyStore.load(new ByteArrayInputStream(keyStoreBytes), keyStorePassword.getData().toCharArray());
            logger.info("Key store attribute successfully validated. Given key store contains: {}", keyStore.aliases());

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ValidationException(ValidationError.create(e.getMessage()));
        }

        try {
            FileAttributeContentV2 trustStoreBase64 = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_TRUSTSTORE, attributes, false);
            StringAttributeContentV2 trustStoreType = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_TRUSTSTORE_TYPE, attributes, false);
            StringAttributeContentV2 trustStorePassword = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_TRUSTSTORE_PASSWORD, attributes, false);

            if (trustStoreBase64 != null && !StringUtils.isAnyBlank(trustStoreBase64.getData().getContent(), trustStoreType.getData(), trustStorePassword.getData())) {
                byte[] trustStoreBytes = Base64.getDecoder().decode(trustStoreBase64.getData().getContent());
                KeyStore trustStore = KeyStore.getInstance(trustStoreType.getData());
                trustStore.load(new ByteArrayInputStream(trustStoreBytes), trustStorePassword.getData().toCharArray());
                logger.info("Trust store attribute successfully validated. Given trust store contains: {}", trustStore.aliases());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ValidationException(ValidationError.create(e.getMessage()));
        }

        return true;
    }

    @Override
    public List<DataAttributeV3> getApiKeyAuthAttributes() {
        List<DataAttributeV3> attrs = new ArrayList<>();

        DataAttributeV3 apiKeyHeader = new DataAttributeV3();
        apiKeyHeader.setUuid("705ccbfb-1d81-402a-ae67-8d38f159b240");
        apiKeyHeader.setName(ATTRIBUTE_API_KEY_HEADER);
        apiKeyHeader.setType(AttributeType.DATA);
        apiKeyHeader.setContentType(AttributeContentType.STRING);

        DataAttributeProperties properties = new DataAttributeProperties();
        properties.setRequired(true);
        properties.setReadOnly(false);
        properties.setVisible(true);
        apiKeyHeader.setProperties(properties);

        String headerReference = "X-API-KEY";
        StringAttributeContentV3 header = new StringAttributeContentV3();
        header.setReference(headerReference);
        header.setData(headerReference);
        header.setContentType(AttributeContentType.STRING);

        apiKeyHeader.setContent(List.of(header));
        attrs.add(apiKeyHeader);

        DataAttributeV3 apiKey = new DataAttributeV3();
        apiKey.setUuid("989dafd6-d18c-41f1-b68d-285c56d6331e");
        apiKey.setName(ATTRIBUTE_API_KEY);
        apiKey.setType(AttributeType.DATA);
        apiKey.setContentType(AttributeContentType.SECRET);

        DataAttributeProperties apiKeyproperties = new DataAttributeProperties();
        apiKeyproperties.setRequired(true);
        apiKeyproperties.setReadOnly(false);
        apiKeyproperties.setVisible(true);
        apiKey.setProperties(apiKeyproperties);

        attrs.add(apiKey);

        return attrs;
    }

    @Override
    public Boolean validateApiKeyAuthAttributes(List<RequestAttributeDto> attributes) {
        AttributeDefinitionUtils.validateAttributes(getApiKeyAuthAttributes(), attributes);
        return true;
    }

    @Override
    public List<DataAttributeV3> getJWTAuthAttributes() {
        throw new ValidationException(ValidationError.create("Auth type JWT not implemented yet"));
    }

    @Override
    public Boolean validateJWTAuthAttributes(List<RequestAttributeDto> attributes) {
        throw new ValidationException(ValidationError.create("Auth type JWT not implemented yet"));
    }
}
