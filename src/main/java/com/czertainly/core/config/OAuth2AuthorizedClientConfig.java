package com.czertainly.core.config;

import com.czertainly.core.auth.oauth2.CzertainlyJdbcOAuth2AuthorizedClientService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class OAuth2AuthorizedClientConfig {
    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(JdbcTemplate jdbcTemplate, ClientRegistrationRepository clientRegistrationRepository) {
        return new CzertainlyJdbcOAuth2AuthorizedClientService(jdbcTemplate, clientRegistrationRepository);
    }
}
