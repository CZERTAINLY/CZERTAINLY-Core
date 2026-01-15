package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.proxy.ProxyRequestDto;
import com.czertainly.api.model.client.proxy.ProxyUpdateRequestDto;
import com.czertainly.api.model.core.proxy.ProxyDto;
import com.czertainly.api.model.core.proxy.ProxyStatus;
import com.czertainly.core.dao.entity.Proxy;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.Optional;

public interface ProxyService extends ResourceExtensionService {

    List<ProxyDto> listProxies(SecurityFilter filter, Optional<ProxyStatus> status) throws NotFoundException;

    ProxyDto getProxy(SecuredUUID uuid) throws NotFoundException;

    Proxy getProxyEntity(SecuredUUID uuid) throws NotFoundException;

    ProxyDto createProxy(ProxyRequestDto request) throws AlreadyExistException;

    ProxyDto editProxy(SecuredUUID uuid, ProxyUpdateRequestDto request) throws NotFoundException;

    void deleteProxy(SecuredUUID uuid) throws NotFoundException;
}
