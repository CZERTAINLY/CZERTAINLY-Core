package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.proxy.ProxyRequestDto;
import com.czertainly.api.model.client.proxy.ProxyUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.proxy.ProxyDto;
import com.czertainly.api.model.core.proxy.ProxyStatus;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Proxy;
import com.czertainly.core.dao.entity.Proxy_;
import com.czertainly.core.dao.repository.ProxyRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ProxyService;
import com.czertainly.core.util.ProxyCodeHelper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service(Resource.Codes.PROXY)
@Transactional
@RequiredArgsConstructor
public class ProxyServiceImpl implements ProxyService {
    private static final Logger logger = LoggerFactory.getLogger(ProxyServiceImpl.class);

    private final ProxyRepository proxyRepository;
    private final ProxyCodeHelper proxyCodeHelper;

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.LIST)
    public List<ProxyDto> listProxies(SecurityFilter filter, Optional<ProxyStatus> status) {
        List<ProxyDto> proxies = proxyRepository.findUsingSecurityFilter(filter).stream().map(Proxy::mapToDto).toList();
        if (status.isPresent()) {
            proxies = filterByStatus(proxies, status.get());
        }
        return proxies;
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.DETAIL)
    public ProxyDto getProxy(SecuredUUID uuid) throws NotFoundException {
        Proxy proxy = getProxyEntity(uuid);
        return proxy.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.DETAIL)
    public Proxy getProxyEntity(SecuredUUID uuid) throws NotFoundException {
        return proxyRepository.findByUuid(uuid)
            .orElseThrow(() -> new NotFoundException(Proxy.class, uuid));
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.CREATE)
    public ProxyDto createProxy(ProxyRequestDto request) throws AlreadyExistException {
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException(ValidationError.create("name must not be empty"));
        }

        if (proxyRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(Proxy.class, request.getName());
        }

        String proxyCode = proxyCodeHelper.calculateCode(request.getName());

        Proxy proxy = new Proxy();
        proxy.setName(request.getName());
        proxy.setDescription(request.getDescription());
        proxy.setCode(proxyCode);
        proxy.setStatus(ProxyStatus.INITIALIZED);
        proxyRepository.save(proxy);

        return proxy.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.UPDATE)
    public ProxyDto editProxy(SecuredUUID uuid, ProxyUpdateRequestDto request) throws NotFoundException {
        Proxy proxy = proxyRepository.findByUuid(uuid)
            .orElseThrow(() -> new NotFoundException(Proxy.class, uuid));

        if (request.getDescription() != null) {
            proxy.setDescription(request.getDescription());
        }

        proxyRepository.save(proxy);

        return proxy.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.DELETE)
    public void deleteProxy(SecuredUUID uuid) throws NotFoundException {
        Proxy proxy = proxyRepository.findByUuid(uuid)
            .orElseThrow(() -> new NotFoundException(Proxy.class, uuid));
        deleteProxy(proxy);

    }

    private void deleteProxy(Proxy proxy) {
        List<String> errors = new ArrayList<>();
        if (!proxy.getConnectors().isEmpty()) {
            errors.add("Dependent connectors: " + String.join(", ", proxy.getConnectors().stream().map(Connector::getName).collect(Collectors.toSet())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(ValidationError.create(String.join("\n", errors)));
        }

        proxyRepository.delete(proxy);
    }

    @Override
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        return proxyRepository.findResourceObject(objectUuid, Proxy_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return proxyRepository.listResourceObjects(filter, Proxy_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getProxyEntity(uuid);
        // Since there are is no parent to the Proxy, exclusive parent permission evaluation need not be done
    }

    private List<ProxyDto> filterByStatus(List<ProxyDto> proxies, ProxyStatus status) {
        return proxies.stream()
            .filter(proxyDto -> proxyDto.getStatus().equals(status))
            .toList();
    }
}
