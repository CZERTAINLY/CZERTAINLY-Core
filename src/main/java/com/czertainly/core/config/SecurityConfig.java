package com.czertainly.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;

import com.czertainly.core.auth.AdminDetailsService;
import com.czertainly.core.auth.ClientDetailsService;
import com.czertainly.api.core.modal.AdminRole;


@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private ClientDetailsService clientDetailsService;

    @Autowired
    private AdminDetailsService adminDetailsService;

    @Value("${server.ssl.trust-store-type:JKS}")
    private String trustStoreType;

    @Value("${server.ssl.trust-store:}")
    private Resource trustStore;

    @Value("${server.ssl.trust-store-password:}")
    private String trustStorePassword;

    @Value("${server.ssl.certificate-header-name:}")
    private String clientCertHeader;

    @Bean
    @ConditionalOnProperty("server.ssl.certificate-header-enabled")
    public CertificateHeaderVerificationFilter certificateHeaderVerificationFilter() {
        return new CertificateHeaderVerificationFilter(clientCertHeader, trustStoreType, trustStore, trustStorePassword);
    }

    @Configuration
    @Order(1)
    public class ClientSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
        @Autowired(required = false)
        private CertificateHeaderVerificationFilter certificateHeaderVerificationFilter;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                    .antMatcher("/v?/operations/**")
                    .authorizeRequests()
                    .anyRequest()
                    .authenticated()
                    .and()
                    .csrf().disable()
                    .cors().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .x509()
                    .x509PrincipalExtractor(new SerialNumberX509PrincipalExtractor())
                    .userDetailsService(clientDetailsService);

            if (certificateHeaderVerificationFilter != null) {
                http.addFilterBefore(certificateHeaderVerificationFilter, X509AuthenticationFilter.class);
            }
        }
    }

    @Configuration
    @Order(2)
    public class LocalhostConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                    .antMatcher("/v1/local/**")
                    .authorizeRequests()
                    .anyRequest().hasIpAddress("127.0.0.1")
                    .and()
                    .csrf().disable()
                    .cors().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .x509()
                    .x509PrincipalExtractor(new SerialNumberX509PrincipalExtractor())
                    .userDetailsService(s -> User.withUsername(s).password("").roles(AdminRole.SUPERADMINISTRATOR.toString()).build());
        }
    }

    @Configuration
    @Order(3)
    public class ConnectorConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                    .antMatcher("/v1/connector/**")
                    .authorizeRequests()
                    .anyRequest().permitAll()
                    .and()
                    .csrf().disable()
                    .cors().disable()
                    .logout().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .userDetailsService(s -> User.withUsername(s).password("").roles("CONNECTOR").build());
        }
    }

    @Configuration
    @Order(4)
    public class AdminSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
        @Autowired(required = false)
        private CertificateHeaderVerificationFilter certificateHeaderVerificationFilter;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.authorizeRequests()
                    .anyRequest().authenticated()
                    .and()
                    .csrf().disable()
                    .cors().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .x509()
                    .x509PrincipalExtractor(new SerialNumberX509PrincipalExtractor())
                    .userDetailsService(adminDetailsService);

            if (certificateHeaderVerificationFilter != null) {
                http.addFilterBefore(certificateHeaderVerificationFilter, X509AuthenticationFilter.class);
            }
        }
    }
}