package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.FileAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.core.service.ConnectorAuthService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.czertainly.api.clients.BaseApiClient.*;

@Service
@Transactional
// TODO AUTH - secure using @ExternalAuthorization. I was unable to find appropriate resource and actions.
public class ConnectorAuthServiceImpl implements ConnectorAuthService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorAuthServiceImpl.class);

    private static final ArrayList<String> SUPPORTED_KEY_STORE_TYPES = new ArrayList<>(List.of("PKCS12", "JKS"));

    @Override
    public Set<AuthType> getAuthenticationTypes() {
        return EnumSet.allOf(AuthType.class);
    }

    @Override
    public List<BaseAttribute> getAuthAttributes(AuthType authenticationType) {
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
    public List<DataAttribute> mergeAndValidateAuthAttributes(AuthType authenticationType, List<ResponseAttributeDto> attributes) {
        if (authenticationType == null || attributes == null) {
            return List.of();
        }

        List<BaseAttribute> definitions = getAuthAttributes(authenticationType);
        List<DataAttribute> merged = AttributeDefinitionUtils.mergeAttributes(definitions, AttributeDefinitionUtils.getClientAttributes(attributes));

        return merged;
    }

    @Override
    public List<BaseAttribute> getBasicAuthAttributes() {
        List<BaseAttribute> attrs = new ArrayList<>();

        DataAttribute username = new DataAttribute();
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

        DataAttribute password = new DataAttribute();
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
    public List<BaseAttribute> getCertificateAttributes() {
        List<BaseAttribute> attrs = new ArrayList<>();

        DataAttribute keyStoreType = new DataAttribute();
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

        List<BaseAttributeContent> base = new ArrayList<>();
        SUPPORTED_KEY_STORE_TYPES.forEach(e -> {
            base.add(new StringAttributeContent(e));
        });

        keyStoreType.setContent(base);
        attrs.add(keyStoreType);

        DataAttribute keyStore = new DataAttribute();
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

        DataAttribute keyStorePassword = new DataAttribute();
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

        DataAttribute trustStoreType = new DataAttribute();
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

        DataAttribute trustStore = new DataAttribute();
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

        DataAttribute trustStorePassword = new DataAttribute();
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
            FileAttributeContent keyStoreBase64 = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_KEYSTORE, attributes, true);
            byte[] keyStoreBytes = Base64.getDecoder().decode(keyStoreBase64.getData().getContent());

            StringAttributeContent keyStoreType = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_KEYSTORE_TYPE, attributes, true);
            StringAttributeContent keyStorePassword = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_KEYSTORE_PASSWORD, attributes, true);

            KeyStore keyStore = KeyStore.getInstance(keyStoreType.getData());
            keyStore.load(new ByteArrayInputStream(keyStoreBytes), keyStorePassword.getData().toCharArray());
            logger.info("Key store attribute successfully validated. Given key store contains: {}", keyStore.aliases());

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ValidationException(ValidationError.create(e.getMessage()));
        }

        try {
            FileAttributeContent trustStoreBase64 = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_TRUSTSTORE, attributes, false);
            StringAttributeContent trustStoreType = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_TRUSTSTORE_TYPE, attributes, false);
            StringAttributeContent trustStorePassword = AttributeDefinitionUtils.getAttributeContent(ATTRIBUTE_TRUSTSTORE_PASSWORD, attributes, false);

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
    public List<BaseAttribute> getApiKeyAuthAttributes() {
        List<BaseAttribute> attrs = new ArrayList<>();

        DataAttribute apiKeyHeader = new DataAttribute();
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
        StringAttributeContent header = new StringAttributeContent();
        header.setReference(headerReference);
        header.setData(headerReference);

        apiKeyHeader.setContent(List.of(header));
        attrs.add(apiKeyHeader);

        DataAttribute apiKey = new DataAttribute();
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
    public List<BaseAttribute> getJWTAuthAttributes() {
        throw new ValidationException(ValidationError.create("Auth type JWT not implemented yet"));
    }

    @Override
    public Boolean validateJWTAuthAttributes(List<RequestAttributeDto> attributes) {
        throw new ValidationException(ValidationError.create("Auth type JWT not implemented yet"));
    }
}
