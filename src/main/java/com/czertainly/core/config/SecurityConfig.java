package com.czertainly.core.config;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.auth.oauth2.*;
import com.czertainly.core.security.authn.CzertainlyAuthenticationConverter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.security.authn.CzertainlyAuthenticationFilter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationProvider;
import com.czertainly.core.security.authz.ExternalFilterAuthorizationVoter;
import com.czertainly.core.settings.SettingsCache;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.text.ParseException;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    CzertainlyAuthenticationProvider czertainlyAuthenticationProvider;

    ExternalFilterAuthorizationVoter filterAuthorizationVoter;

    ProtocolValidationFilter protocolValidationFilter;

    private Environment environment;

    private CzertainlyClientRegistrationRepository clientRegistrationRepository;

    private OAuth2LoginFilter oauth2LoginFilter;

    @Autowired
    public void setClientRegistrationRepository(CzertainlyClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Autowired
    public void setOauth2LoginFilter(OAuth2LoginFilter oauth2LoginFilter) {
        this.oauth2LoginFilter = oauth2LoginFilter;
    }


    @Bean
    public CzertainlyAuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return new CzertainlyAuthenticationSuccessHandler();
    }

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http, CzertainlyJwtAuthenticationConverter czertainlyJwtAuthenticationConverter) throws Exception {

        http
                .authorizeRequests()
                .requestMatchers("/login", "/oauth2/**").permitAll() // Allow unauthenticated access
                .anyRequest().authenticated()
                .accessDecisionManager(accessDecisionManager())
                .and()
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpServletResponse.SC_FORBIDDEN))
                        .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .x509(AbstractHttpConfigurer::disable)
                .addFilterBefore(protocolValidationFilter, X509AuthenticationFilter.class)
                .addFilterBefore(createCzertainlyAuthenticationFilter(), BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder())
                                .jwtAuthenticationConverter(czertainlyJwtAuthenticationConverter)
                        )
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                        .invalidateHttpSession(true) // Invalidate the session
                        .clearAuthentication(true) // Clear the authentication
                        .deleteCookies(CookieConfig.COOKIE_NAME) // Delete the session cookie
                )
                .oauth2Login(oauth2
                        ->
                        oauth2.successHandler(customAuthenticationSuccessHandler())
                                .failureHandler(new CzertainlyOAuth2FailureHandler())
                )
                .oauth2Client(oauth2client -> oauth2client.clientRegistrationRepository(clientRegistrationRepository))
                .addFilterAfter(oauth2LoginFilter, OAuth2LoginAuthenticationFilter.class)
        ;

        return http.build();
    }

    private boolean isAuthenticationNeeded() {
        SecurityContext context = SecurityContextHolder.getContext();
        return (context == null || context.getAuthentication() == null || context.getAuthentication() instanceof AnonymousAuthenticationToken);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            if (!isAuthenticationNeeded()) {
                return null;
            }
            String issuerUri;
            try {
                issuerUri = SignedJWT.parse(token).getJWTClaimsSet().getIssuer();
            } catch (ParseException e) {
                throw new ValidationException("Could not extract issuer from JWT.");
            }
            if (issuerUri == null) throw new ValidationException("Issuer URI is not present in JWT.");

            AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
            OAuth2ProviderSettingsDto providerSettings = authenticationSettings.getOAuth2Providers().values().stream()
                    .filter(p -> p.getIssuerUrl().equals(issuerUri))
                    .findFirst().orElse(null);

            if (providerSettings == null) {
                throw new CzertainlyAuthenticationException("No OAuth2 Provider with issuer URI '%s' configured".formatted(issuerUri));
            }

            int skew = providerSettings.getSkew();
            List<String> audiences = providerSettings.getAudiences();

            NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);

            OAuth2TokenValidator<Jwt> clockSkewValidator = new JwtTimestampValidator(Duration.ofSeconds(skew));

            OAuth2TokenValidator<Jwt> audienceValidator = new DelegatingOAuth2TokenValidator<>();
            // Add audience validation
            if (!audiences.isEmpty()) {
                audienceValidator = new JwtClaimValidator<List<String>>("aud", aud -> aud.stream().anyMatch(audiences::contains));
            }

            OAuth2TokenValidator<Jwt> combinedValidator = JwtValidators.createDefaultWithValidators(List.of(new JwtIssuerValidator(issuerUri), clockSkewValidator, audienceValidator));
            jwtDecoder.setJwtValidator(combinedValidator);
            return jwtDecoder.decode(token);
        };
    }


    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        return new CzertainlyLogoutSuccessHandler(clientRegistrationRepository);
    }

    @Bean
    public CzertainlyJwtAuthenticationConverter czertainlyJwtAuthenticationConverter() {
        return new CzertainlyJwtAuthenticationConverter();
    }


    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(czertainlyAuthenticationProvider);
    }

    protected AccessDecisionManager accessDecisionManager() {
        // This configuration controls access to api endpoints. Important here is the order in which the individual
        // voters are registered. The first one must always be ExternalAuthorizationFilter.
        // ExternalFilterAuthorizationVoter approves access for requests:
        //   * authenticated as anonymous
        //   * to urls that has been explicitly set to be authenticated
        // If request does not conform to the rules above, voter abstains voting.
        // WebExpressionVoter checks that the request is authenticated, as configured by HTTP Security.
        List<AccessDecisionVoter<?>> voters = List.of(
                this.externalFilterAuthorizationVoter(),
                new WebExpressionVoter()
        );
        return new AffirmativeBased(voters);
    }

    protected ExternalFilterAuthorizationVoter externalFilterAuthorizationVoter() {
        // filterAuthorizationVoter is configured so that only requests to local controller are authorized against OPA.
        // For other request we only require users to be authenticated (this is checked by WebExpressionVoter) as
        // Controllers mostly only delegate to a Service and any call to a Service method should be always authorized by
        // OPA. Doing this, we do not lose much of the security but save one call to OPA for each request. Anonymous
        // request will always be authorized against OPA if not explicitly excluded.
        RequestMatcher toAuthorize = new AntPathRequestMatcher("/v?/local/**");
        filterAuthorizationVoter.setToAuthorizeRequestsMatcher(toAuthorize);

        // Exclude the error endpoint from authorization when accessed by anonymous user
        RequestMatcher doNotAuthorize = new AntPathRequestMatcher("/error");
        filterAuthorizationVoter.setDoNotAuthorizeAnonymousRequestsMatcher(doNotAuthorize);
        return filterAuthorizationVoter;
    }

    protected CzertainlyAuthenticationFilter createCzertainlyAuthenticationFilter() {
        return new CzertainlyAuthenticationFilter(authenticationManager(), new CzertainlyAuthenticationConverter(), environment.getProperty("management.endpoints.web.base-path"));
    }

    // SETTERs

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Autowired
    public void setCzertainlyAuthenticationProvider(CzertainlyAuthenticationProvider czertainlyAuthenticationProvider) {
        this.czertainlyAuthenticationProvider = czertainlyAuthenticationProvider;
    }

    @Autowired
    public void setFilterAuthorizationVoter(ExternalFilterAuthorizationVoter filterAuthorizationVoter) {
        this.filterAuthorizationVoter = filterAuthorizationVoter;
    }

    @Autowired
    public void setProtocolValidationFilter(ProtocolValidationFilter protocolValidationFilter) {
        this.protocolValidationFilter = protocolValidationFilter;
    }
}
