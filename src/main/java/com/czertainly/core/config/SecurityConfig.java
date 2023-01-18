package com.czertainly.core.config;

import com.czertainly.core.security.authn.CzertainlyAuthenticationConverter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationFilter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationProvider;
import com.czertainly.core.security.authz.ExternalFilterAuthorizationVoter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig  {

    @Autowired
    CzertainlyAuthenticationProvider czertainlyAuthenticationProvider;

    @Autowired
    ExternalFilterAuthorizationVoter filterAuthorizationVoter;

    @Autowired
    AcmeValidationFilter acmeValidationFilter;

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeRequests()
                .anyRequest().authenticated()
                .accessDecisionManager(accessDecisionManager())
                .and()
                .exceptionHandling()
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                })
                .authenticationEntryPoint((request, response, authException) -> {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                })
                .and()
                .csrf().disable()
                .cors().disable()
                .httpBasic().disable()
                .formLogin().disable()
                .x509().disable()
                .addFilterBefore(acmeValidationFilter, X509AuthenticationFilter.class)
                .addFilterBefore(createCzertainlyAuthenticationFilter(), BasicAuthenticationFilter.class)
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        return http.build();
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
        return new CzertainlyAuthenticationFilter(authenticationManager(), new CzertainlyAuthenticationConverter());
    }
}