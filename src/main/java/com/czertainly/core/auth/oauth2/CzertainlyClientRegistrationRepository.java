package com.czertainly.core.auth.oauth2;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.settings.Oauth2ProviderSettings;
import com.czertainly.api.model.core.settings.Oauth2SettingsDto;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.SecretEncodingVersion;
import com.czertainly.core.util.SecretsUtil;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class CzertainlyClientRegistrationRepository implements ClientRegistrationRepository {

    private SettingService settingService;

    @Autowired
    public void setSettingService(SettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        Oauth2ProviderSettings clientSettings = settingService.getOauth2ProviderSettings(registrationId).getClientSettings();
        return convertJsonToClientRegistration(clientSettings, registrationId);
    }

    private ClientRegistration convertJsonToClientRegistration(Oauth2ProviderSettings clientSettings, String registrationId) {
        if (clientSettings == null) {
            return null;
        }
        Map<String, Object> configMetadata = new HashMap<>();
        configMetadata.put("end_session_endpoint", clientSettings.getLogoutUri());

        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(clientSettings.getClientId())
                .clientSecret(SecretsUtil.decodeAndDecryptSecretString(clientSettings.getClientSecret(), SecretEncodingVersion.V1))
                .authorizationGrantType(new AuthorizationGrantType("authorization_code"))
//                .redirectUri("{baseUrl}/login/oauth2/code/" + registrationId)
                .redirectUri("https://127.0.0.1:8443/api/login/oauth2/code/keycloak")
                .scope(clientSettings.getScope())
                .authorizationUri(clientSettings.getAuthorizationUri())
                .tokenUri(clientSettings.getTokenUri())
                .jwkSetUri(clientSettings.getJwkSetUri())
                .providerConfigurationMetadata(configMetadata)
                .build();
    }

}
