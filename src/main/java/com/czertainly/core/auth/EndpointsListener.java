package com.czertainly.core.auth;

import com.czertainly.core.model.auth.SyncRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

@Component
public class EndpointsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointsListener.class);

    public List<SyncRequestDto> endpoints = new ArrayList<>();

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        applicationContext.getBean(RequestMappingHandlerMapping.class)
                .getHandlerMethods()
                .entrySet().stream()
                .filter(e -> !e.getKey().getMethodsCondition().getMethods().isEmpty())
                .filter(e -> e.getValue().getMethod().getAnnotation(AuthEndpoint.class) != null)
                .forEach(e -> {
                    AuthEndpoint annotatedValues = e.getValue().getMethod().getAnnotation(AuthEndpoint.class);
                    LOGGER.debug("{} {} {} {} {}", e.getKey().getMethodsCondition().getMethods(),
                            e.getKey().getPatternValues(),
                            e.getValue().getMethod().getName(),
                            annotatedValues.resourceName(),
                            annotatedValues.actionName(),
                            annotatedValues.isListingEndPoint());
                    SyncRequestDto endpoint = new SyncRequestDto();
                    endpoint.setMethod(e.getKey().getMethodsCondition().getMethods().iterator().next().name());
                    endpoint.setRouteTemplate(e.getKey().getPatternValues().iterator().next());
                    endpoint.setName(e.getValue().getMethod().getName());
                    endpoint.setResourceName(annotatedValues.resourceName());
                    endpoint.setActionName(annotatedValues.actionName());
                    endpoint.setIsListingEndpoint(annotatedValues.isListingEndPoint());
                    endpoints.add(endpoint);
                });
    }

    public List<SyncRequestDto> getEndpoints() {
        return this.endpoints;
    }
}
