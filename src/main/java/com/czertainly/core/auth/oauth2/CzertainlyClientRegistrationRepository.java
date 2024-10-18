package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.settings.Oauth2ClientSettings;
import com.czertainly.api.model.core.settings.Oauth2SettingsDto;
import com.czertainly.core.service.SettingService;
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
        Optional<Oauth2ClientSettings> clientSettings = settingService.getOauth2ProviderSettings().stream().filter(setting -> setting.getRegistrationId().equals(registrationId)).map(Oauth2SettingsDto::getClientSettings).findFirst();
        return clientSettings.map(oauth2ClientSettings -> convertJsonToClientRegistration(oauth2ClientSettings, registrationId)).orElse(null);
    }

    private ClientRegistration convertJsonToClientRegistration(Oauth2ClientSettings clientSettings, String registrationId) {
        Map<String, Object> configMetadata = new HashMap<>();
        configMetadata.put("end_session_endpoint", clientSettings.getEndSessionEndpoint());

        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(clientSettings.getClientId())
                .clientSecret(clientSettings.getClientSecret())
                .clientName(clientSettings.getIssuerUri())
                .authorizationGrantType(new AuthorizationGrantType(clientSettings.getAuthorizationGrantType()))
                .redirectUri(clientSettings.getRedirectUri())
                .scope(clientSettings.getScope())
                .authorizationUri(clientSettings.getAuthorizationUri())
                .tokenUri(clientSettings.getTokenUri())
                .jwkSetUri(clientSettings.getJwkSetUri())
                .issuerUri(clientSettings.getIssuerUri())
                .providerConfigurationMetadata(configMetadata)
                .build();
    }




}
