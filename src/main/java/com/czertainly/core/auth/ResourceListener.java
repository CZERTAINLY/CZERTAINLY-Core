package com.czertainly.core.auth;

import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceSyncRequestDto;
import com.czertainly.core.security.authz.ExternalAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ResourceListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceListener.class);

    public List<ResourceSyncRequestDto> resources = new ArrayList<>();

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        Map<Resource, String> listingEndpoints = new HashMap<>();
        Map<Resource, Set<String>> resourceToAction = new HashMap<>();
        applicationContext.getBean(RequestMappingHandlerMapping.class)
                .getHandlerMethods()
                .entrySet().stream()
                .filter(e -> !e.getKey().getMethodsCondition().getMethods().isEmpty())
                .filter(e -> e.getValue().getMethod().getAnnotation(AuthEndpoint.class) != null)
                .forEach(e -> {
                    AuthEndpoint annotatedValues = e.getValue().getMethod().getAnnotation(AuthEndpoint.class);
                    if(listingEndpoints.get(annotatedValues.resourceName().getCode()) != null) {
                        throw new RuntimeException("Duplicate listing end point on " + annotatedValues.resourceName().getCode());
                    }
                    listingEndpoints.put(annotatedValues.resourceName(), e.getKey().getPatternValues().iterator().next());
                });

        for(String beanName: applicationContext.getBeanDefinitionNames()){
            Method[] methods = AopUtils.getTargetClass(applicationContext.getBean(beanName)).getDeclaredMethods();

            for(Method m: methods) {
                if (m.isAnnotationPresent(ExternalAuthorization.class)) {
                    ExternalAuthorization annotatedValue = m.getAnnotation(ExternalAuthorization.class);
                    resourceToAction.computeIfAbsent(annotatedValue.resource(),
                            k -> new HashSet()).add(annotatedValue.action().getCode());
                }
            }
        }

        for(Map.Entry<Resource, Set<String>> entry: resourceToAction.entrySet()) {
            ResourceSyncRequestDto requestDto = new ResourceSyncRequestDto();
            requestDto.setActions(new ArrayList<>(entry.getValue()));
            requestDto.setResourceName(entry.getKey());
            requestDto.setListObjectsEndpoint(listingEndpoints.get(entry.getKey()));
            resources.add(requestDto);
        }
    }

    public List<ResourceSyncRequestDto> getResources() {
        return this.resources;
    }
}
