package com.czertainly.core.config;

import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.dao.entity.Setting;
import com.czertainly.core.dao.repository.SettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CzertainlyClientRegistrationRepository implements ClientRegistrationRepository {

    private SettingRepository settingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public void setSettingRepository(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        List<Setting> setting = settingRepository.findBySectionAndName(SettingsSection.OAUTH2_PROVIDER, registrationId);
        if (!setting.isEmpty()) {
            return convertJsonToClientRegistration(setting.getFirst().getValue(), registrationId);
        }

        return null;

    }

    private ClientRegistration convertJsonToClientRegistration(String json, String registrationId) {
        try {
            JsonNode clientNode = objectMapper.readTree(json);
            Map<String, Object> keycloakConfig = new HashMap<>();
            keycloakConfig.put("end_session_endpoint", "http://localhost:8080/realms/CZERTAINLY-realm/protocol/openid-connect/logout");


            return ClientRegistration.withRegistrationId(registrationId)
                    .clientId(clientNode.get("client-id").asText())
                    .clientSecret(clientNode.get("client-secret").asText())
                    .clientName(clientNode.get("issuer-uri").asText())
                    .authorizationGrantType(new AuthorizationGrantType(clientNode.get("authorization-grant-type").asText()))
                    .redirectUri(clientNode.get("redirect-uri").asText())
                    .scope(clientNode.get("scope").asText().split(","))
//                    .clientAuthenticationMethod(new ClientAuthenticationMethod(clientNode.get("clientAuthenticationMethod").asText()))
                    .authorizationUri(clientNode.get("authorization-uri").asText())
                    .tokenUri(clientNode.get("token-uri").asText())
                    .jwkSetUri(clientNode.get("jwk-set-uri").asText())
                    .issuerUri(clientNode.get("issuer-uri").asText())
                    .providerConfigurationMetadata(keycloakConfig)
                    .build();
        } catch (JsonProcessingException e) {
            e.printStackTrace(); // Log the exception appropriately
        }
        return null;
    }




}
