package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.settings.OAuth2ProviderSettings;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.SecretEncodingVersion;
import com.czertainly.core.util.SecretsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CzertainlyClientRegistrationRepository implements ClientRegistrationRepository {

    private SettingService settingService;

    @Autowired
    public void setSettingService(SettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        OAuth2ProviderSettings clientSettings = settingService.getOAuth2ProviderSettings(registrationId, true);
        return convertJsonToClientRegistration(clientSettings, registrationId);
    }

    private ClientRegistration convertJsonToClientRegistration(OAuth2ProviderSettings clientSettings, String registrationId) {
        if (clientSettings == null) {
            return null;
        }
        Map<String, Object> configMetadata = new HashMap<>();
        configMetadata.put("end_session_endpoint", clientSettings.getLogoutUrl());
        configMetadata.put("post_logout_uri", clientSettings.getPostLogoutUrl());

        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(clientSettings.getClientId())
                .clientSecret(SecretsUtil.decodeAndDecryptSecretString(clientSettings.getClientSecret(), SecretEncodingVersion.V1))
                .authorizationGrantType(new AuthorizationGrantType("authorization_code"))
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(clientSettings.getScope())
                .authorizationUri(clientSettings.getAuthorizationUrl())
                .tokenUri(clientSettings.getTokenUrl())
                .jwkSetUri(clientSettings.getJwkSetUrl())
                .userInfoUri(clientSettings.getUserInfoUrl())
                .providerConfigurationMetadata(configMetadata)
                .build();
    }

}
