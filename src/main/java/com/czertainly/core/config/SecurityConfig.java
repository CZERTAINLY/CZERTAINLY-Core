package com.czertainly.core.config;

import com.czertainly.core.auth.CzertainlyJwtAuthenticationConverter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationConverter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationFilter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationProvider;
import com.czertainly.core.security.authz.ExternalFilterAuthorizationVoter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    CzertainlyAuthenticationProvider czertainlyAuthenticationProvider;

    ExternalFilterAuthorizationVoter filterAuthorizationVoter;

    ProtocolValidationFilter protocolValidationFilter;

    private Environment environment;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private JwtTokenFilter jwtTokenFilter;


    @Bean
    public CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return new CustomAuthenticationSuccessHandler();
    }


//    @Bean
////    @Order(1)
//    protected SecurityFilterChain securityFilterChain(HttpSecurity http, CzertainlyJwtAuthenticationConverter czertainlyJwtAuthenticationConverter) throws Exception {
//
//        http
//                .securityMatcher("/v1/**")
//                .authorizeRequests()
//                .anyRequest().authenticated()
//                .accessDecisionManager(accessDecisionManager())
//                .and()
//                .exceptionHandling(exceptionHandling -> exceptionHandling
//                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpServletResponse.SC_FORBIDDEN))
//                        .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
//                )
//
//                .csrf(AbstractHttpConfigurer::disable)
//                .cors(AbstractHttpConfigurer::disable)
//                .httpBasic(AbstractHttpConfigurer::disable)
//                .formLogin(AbstractHttpConfigurer::disable)
//                .x509(AbstractHttpConfigurer::disable)
//
//                .addFilterBefore(protocolValidationFilter, X509AuthenticationFilter.class)
//                .addFilterBefore(createCzertainlyAuthenticationFilter(), BearerTokenAuthenticationFilter.class)
//                .oauth2ResourceServer(oauth2 -> oauth2
//                        .jwt(jwt -> jwt.jwtAuthenticationConverter(czertainlyJwtAuthenticationConverter))
//                )
//                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
//
//        return http.build();
//    }

    @Bean
//    @Order(2)
    protected SecurityFilterChain configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .requestMatchers("/login", "/oauth2/**").permitAll() // Allow unauthenticated access
                .anyRequest().authenticated() // All other requests require authentication
                .accessDecisionManager(accessDecisionManager())
                .and()
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpServletResponse.SC_FORBIDDEN))
                        .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .x509(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("http://localhost:3000/#/dashboard") // Redirect to home after logout
                        .invalidateHttpSession(true) // Invalidate the session
                        .clearAuthentication(true) // Clear the authentication
                        .logoutSuccessHandler(customLogoutSuccessHandler())
                        .deleteCookies("JSESSIONID") // Optionally delete cookies
                )
                .oauth2Login()
                .successHandler(customAuthenticationSuccessHandler())
                .and().addFilterAfter(jwtTokenFilter, OAuth2LoginAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CustomLogoutSuccessHandler customLogoutSuccessHandler() {
        return new CustomLogoutSuccessHandler();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository);

        // Sets the location that the End-User's User Agent will be redirected to
        // after the logout has been performed at the Provider
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/main.html");
        return oidcLogoutSuccessHandler;
    }


    @Bean
    public CzertainlyJwtAuthenticationConverter czertainlyJwtAuthenticationConverter() {
        return new CzertainlyJwtAuthenticationConverter();  // Your custom converter
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