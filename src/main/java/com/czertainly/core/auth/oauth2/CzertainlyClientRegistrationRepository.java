package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.SecretEncodingVersion;
import com.czertainly.core.util.SecretsUtil;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CzertainlyClientRegistrationRepository implements ClientRegistrationRepository {

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
        OAuth2ProviderSettingsDto clientSettings = authenticationSettings.getOAuth2Providers().get(registrationId);
        return convertJsonToClientRegistration(clientSettings, registrationId);
    }

    private ClientRegistration convertJsonToClientRegistration(OAuth2ProviderSettingsDto clientSettings, String registrationId) {
        // Because of validation of OAuth2ProviderSettingsDto, when client ID is null, all other client related properties will be null too, meaning this provider is only for JWT token validation
        if (clientSettings == null || clientSettings.getClientId() == null) {
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
