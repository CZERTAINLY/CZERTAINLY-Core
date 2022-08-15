package com.czertainly.core.config;

import com.czertainly.core.security.authz.ExternalFilterAuthorizationVoter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationConverter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationFilter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity(debug = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    CzertainlyAuthenticationProvider czertainlyAuthenticationProvider;

    @Autowired
    ExternalFilterAuthorizationVoter filterAuthorizationVoter;

    @Autowired
    AcmeValidationFilter acmeValidationFilter;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .anyRequest().authenticated()
                .accessDecisionManager(accessDecisionManager())
                .and()
                .csrf().disable()
                .cors().disable()
                .httpBasic().disable()
                .formLogin().disable()
                .x509().disable()
                .addFilterBefore(acmeValidationFilter, X509AuthenticationFilter.class)
                .addFilterBefore(this.createCzertainlyAuthenticationFilter(), BasicAuthenticationFilter.class)
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        super.configure(auth);
        auth.authenticationProvider(czertainlyAuthenticationProvider);
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
        // OPA. Doing this, we do not lose much of the security but save one call to OPA for each request.
        RequestMatcher rm = new AntPathRequestMatcher("/v?/local/**");
        filterAuthorizationVoter.setSupportedRequestsMatcher(rm);
        return filterAuthorizationVoter;
    }

    protected CzertainlyAuthenticationFilter createCzertainlyAuthenticationFilter() throws Exception {
        return new CzertainlyAuthenticationFilter(this.authenticationManager(), new CzertainlyAuthenticationConverter());
    }
}