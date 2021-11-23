package com.czertainly.core.config;

import javax.servlet.ServletContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.Contact;
import springfox.documentation.service.Tag;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class OpenApiConfig {
	
	@Autowired
	ServletContext context;
	
    @Bean
    public Docket allApi() {
        return new Docket(DocumentationType.OAS_30)
        		.groupName("v1 APIs")
                .select()
                .apis(RequestHandlerSelectors.basePackage("company.threekey.ra.backend.api"))
                .paths(PathSelectors.any())
                .build()
                .tags(
                        new Tag("Client Operations API", "Client interface to request PKI operations"),
                        new Tag("Administrator Management API", "Admin interface to manage Administrators"),
                        new Tag("RA Profile Management API", "Admin interface to manage RA Profiles"),
                        new Tag("Client Management API", "Admin interface to manage Clients"),
                        new Tag("Auth API", "Auth API")
                )
                .useDefaultResponseMessages(false)
                .apiInfo(new ApiInfoBuilder()
                		.title("3Key RA Profiles REST API")
                		.description("REST APIs for managing Administrators, Clients, RA Profiles, End Entities and Certificates")
                        .version("1")
                        .license("Apache License Version 2.0")
                        .licenseUrl("https://www.apache.org/licenses/LICENSE-2.0")
                        .contact(new Contact("3Key Company", "https://www.3key.company", "getinfo@3key.company"))
                        .build()
                );
    }
    
    @Bean
    public Docket clientApi() {
        return new Docket(DocumentationType.OAS_30)
        		.groupName("v1 Client Operations API")
                .select()
                .apis(RequestHandlerSelectors.basePackage("company.threekey.ra.backend.api"))
                .paths(PathSelectors.ant(context.getContextPath() + "/v1/operations/**"))
                .build()
                .tags(
                        new Tag("Client Operations API", "Client interface to manage Certificates and End Entities")
                )
                .useDefaultResponseMessages(false)
                .apiInfo(new ApiInfoBuilder()
                		.title("3Key RA Profiles REST API")
                        .version("1")
                        .license("Apache License Version 2.0")
                        .licenseUrl("https://www.apache.org/licenses/LICENSE-2.0")
                        .contact(new Contact("3Key Company", "https://www.3key.company", "getinfo@3key.company"))
                        .build()
                );
    }
    
    @Bean
    public Docket adminAdminApi() {
        return new Docket(DocumentationType.OAS_30)
        		.groupName("v1 Administrator Management API")
                .select()
                .apis(RequestHandlerSelectors.basePackage("company.threekey.ra.backend.api"))
                .paths(PathSelectors.ant(context.getContextPath() + "/v1/admins/**"))
                .build()
                .tags(
                        new Tag("Administrator Management API", "Admin interface to manage Administrators")
                )
                .useDefaultResponseMessages(false)
                .apiInfo(new ApiInfoBuilder()
                		.title("3Key RA Profiles REST API")
                        .version("1")
                        .license("Apache License Version 2.0")
                        .licenseUrl("https://www.apache.org/licenses/LICENSE-2.0")
                        .contact(new Contact("3Key Company", "https://www.3key.company", "getinfo@3key.company"))
                        .build()
                );
    }
    
    @Bean
    public Docket adminClientApi() {
        return new Docket(DocumentationType.OAS_30)
        		.groupName("v1 Client Management API")
                .select()
                .apis(RequestHandlerSelectors.basePackage("company.threekey.ra.backend.api"))
                .paths(PathSelectors.ant(context.getContextPath() + "/v1/clients/**"))
                .build()
                .tags(
                        new Tag("Client Management API", "Admin interface to manage Clients")
                )
                .useDefaultResponseMessages(false)
                .apiInfo(new ApiInfoBuilder()
                		.title("3Key RA Profiles REST API")
                        .version("1")
                        .license("Apache License Version 2.0")
                        .licenseUrl("https://www.apache.org/licenses/LICENSE-2.0")
                        .contact(new Contact("3Key Company", "https://www.3key.company", "getinfo@3key.company"))
                        .build()
                );
    }
    
    @Bean
    public Docket adminRaProfilesApi() {
        return new Docket(DocumentationType.OAS_30)
        		.groupName("v1 RA Profiles Management API")
                .select()
                .apis(RequestHandlerSelectors.basePackage("company.threekey.ra.backend.api"))
                .paths(PathSelectors.ant(context.getContextPath() + "/v1/raprofiles/**"))
                .build()
                .tags(
                        new Tag("RA Profile Management API", "Admin interface to manage RA Profiles")
                )
                .useDefaultResponseMessages(false)
                .apiInfo(new ApiInfoBuilder()
                		.title("3Key RA Profiles REST API")
                        .version("1")
                        .license("Apache License Version 2.0")
                        .licenseUrl("https://www.apache.org/licenses/LICENSE-2.0")
                        .contact(new Contact("3Key Company", "https://www.3key.company", "getinfo@3key.company"))
                        .build()
                );
    }

}
