package com.czertainly.core.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Configuration that supports running the Spring Boot application in read-only containers
 * /tmp/tomcat directory must ne created outside of this configuration
 * ref: <a href="https://github.com/spring-projects/spring-boot/issues/8578">Support for read-only docker containers</a>
 */
@Component
public class EmbeddedServletContainerConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.setDocumentRoot(new File("/tmp/tomcat"));
        factory.setBaseDirectory(new File("/tmp/tomcat"));
    }

}
