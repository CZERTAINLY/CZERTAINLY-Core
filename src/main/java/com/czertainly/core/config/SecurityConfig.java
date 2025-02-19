package com.czertainly.core.config;

import com.czertainly.core.auth.oauth2.*;
import com.czertainly.core.security.authn.CzertainlyAuthenticationFilter;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.czertainly.core.security.authz.ExternalFilterAuthorizationManager;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {


    ExternalFilterAuthorizationManager filterAuthorizationManager;


    ProtocolValidationFilter protocolValidationFilter;

    private Environment environment;

    private CzertainlyClientRegistrationRepository clientRegistrationRepository;

    private OAuth2LoginFilter oauth2LoginFilter;

    private JwtDecoder jwtDecoder;

    private CzertainlyAuthenticationClient authenticationClient;



    @Autowired
    public void setClientRegistrationRepository(CzertainlyClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Autowired
    public void setOauth2LoginFilter(OAuth2LoginFilter oauth2LoginFilter) {
        this.oauth2LoginFilter = oauth2LoginFilter;
    }

    @Autowired
    public void setJwtDecoder(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }


    @Bean
    public CzertainlyAuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return new CzertainlyAuthenticationSuccessHandler();
    }

    @Bean
    public CzertainlyOAuth2FailureHandler failureHandler() {
        return new CzertainlyOAuth2FailureHandler();
    }

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/oauth2/**").permitAll()
                        .anyRequest().access(requestAuthorization())
                )
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
                        .jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(czertainlyJwtAuthenticationConverter())
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
                                .failureHandler(failureHandler())
                )
                .oauth2Client(oauth2client -> oauth2client.clientRegistrationRepository(clientRegistrationRepository))
                .addFilterAfter(oauth2LoginFilter, OAuth2LoginAuthenticationFilter.class)
        ;

        return http.build();
    }


    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        return new CzertainlyLogoutSuccessHandler(clientRegistrationRepository);
    }

    @Bean
    public CzertainlyJwtAuthenticationConverter czertainlyJwtAuthenticationConverter() {
        return new CzertainlyJwtAuthenticationConverter();
    }

    AuthorizationManager<RequestAuthorizationContext> requestAuthorization() {
        return AuthorizationManagers.anyOf(externalFilterAuthorizationManager(), new WebExpressionAuthorizationManager("isAuthenticated()"));
    }

    protected ExternalFilterAuthorizationManager externalFilterAuthorizationManager() {
        // filterAuthorizationVoter is configured so that only requests to local controller are authorized against OPA.
        // For other request we only require users to be authenticated (this is checked by WebExpressionVoter) as
        // Controllers mostly only delegate to a Service and any call to a Service method should be always authorized by
        // OPA. Doing this, we do not lose much of the security but save one call to OPA for each request. Anonymous
        // request will always be authorized against OPA if not explicitly excluded.
        RequestMatcher toAuthorize = new AntPathRequestMatcher("/v?/local/**");
        filterAuthorizationManager.setToAuthorizeRequestsMatcher(toAuthorize);

        // Exclude the error endpoint from authorization when accessed by anonymous user
        RequestMatcher doNotAuthorize = new AntPathRequestMatcher("/error");
        filterAuthorizationManager.setDoNotAuthorizeAnonymousRequestsMatcher(doNotAuthorize);
        return filterAuthorizationManager;
    }

    protected CzertainlyAuthenticationFilter createCzertainlyAuthenticationFilter() {
        return new CzertainlyAuthenticationFilter(authenticationClient, environment.getProperty("management.endpoints.web.base-path"), environment.getProperty("server.ssl.certificate-header-name"));
    }

    // SETTERs

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Autowired
    public void setProtocolValidationFilter(ProtocolValidationFilter protocolValidationFilter) {
        this.protocolValidationFilter = protocolValidationFilter;
    }

    @Autowired
    public void setAuthenticationClient(CzertainlyAuthenticationClient authenticationClient) {
        this.authenticationClient = authenticationClient;
    }

    @Autowired
    public void setFilterAuthorizationManager(ExternalFilterAuthorizationManager filterAuthorizationManager) {
        this.filterAuthorizationManager = filterAuthorizationManager;
    }
}
