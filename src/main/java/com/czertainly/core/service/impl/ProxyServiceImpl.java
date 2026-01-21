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
import com.czertainly.core.provisioning.ProxyProvisioningException;
import com.czertainly.core.provisioning.ProxyProvisioningService;
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
    private final ProxyProvisioningService proxyProvisioningService;

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
        ProxyDto dto = proxy.mapToDto();

        // If the proxy is waiting for installation, fetch the installation instructions
        if (ProxyStatus.WAITING_FOR_INSTALLATION.equals(proxy.getStatus())) {
            try {
                String installInstructions = proxyProvisioningService.getProxyInstallationInstructions(proxy.getCode());
                dto.setInstallationInstructions(installInstructions);
            } catch (ProxyProvisioningException e) {
                logger.warn("Failed to fetch installation instructions for proxy with code {}", proxy.getCode(), e);
            }
        }

        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.DETAIL)
    public Proxy getProxyEntity(SecuredUUID uuid) throws NotFoundException {
        return proxyRepository.findByUuid(uuid)
            .orElseThrow(() -> new NotFoundException(Proxy.class, uuid));
    }

    @Override
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.CREATE)
    public ProxyDto createProxy(ProxyRequestDto request) throws AlreadyExistException, ProxyProvisioningException {
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

        // INITIALIZED and PROVISIONING are currently skipped because the provisioning is synchronous
        proxy.setStatus(ProxyStatus.WAITING_FOR_INSTALLATION);

        proxyRepository.save(proxy);

        // Provision the proxy using the provisioning service
        String installInstructions = proxyProvisioningService.provisionProxy(proxyCode);

        ProxyDto dto = proxy.mapToDto();
        dto.setInstallationInstructions(installInstructions);

        return dto;
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

        proxyProvisioningService.decommissionProxy(proxy.getCode());
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
    @ExternalAuthorization(resource = Resource.PROXY, action = ResourceAction.DETAIL)
    public ProxyDto getInstallationInstructions(SecuredUUID uuid) throws NotFoundException {
        Proxy proxy = getProxyEntity(uuid);

        String installInstructions = proxyProvisioningService.getProxyInstallationInstructions(proxy.getCode());

        ProxyDto dto = proxy.mapToDto();
        dto.setInstallationInstructions(installInstructions);

        return dto;
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
