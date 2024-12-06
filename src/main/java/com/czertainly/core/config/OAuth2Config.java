package com.czertainly.core.config;

import com.czertainly.core.auth.oauth2.CzertainlyOAuth2TokenRequestHeadersConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequestEntityConverter;

@Configuration
public class OAuth2Config {
    @Bean
    public OAuth2AuthorizedClientProvider oAuth2AuthorizedClientProvider() {
        DefaultRefreshTokenTokenResponseClient refreshTokenTokenResponseClient = new DefaultRefreshTokenTokenResponseClient();
        OAuth2RefreshTokenGrantRequestEntityConverter refreshTokenGrantRequestEntityConverter = new OAuth2RefreshTokenGrantRequestEntityConverter();
        refreshTokenGrantRequestEntityConverter.setHeadersConverter(CzertainlyOAuth2TokenRequestHeadersConverter.withCharsetUtf8());
        refreshTokenTokenResponseClient.setRequestEntityConverter(refreshTokenGrantRequestEntityConverter);

        return OAuth2AuthorizedClientProviderBuilder.builder()
                .refreshToken(configurer -> configurer.accessTokenResponseClient(refreshTokenTokenResponseClient))
                .build();
    }

}
