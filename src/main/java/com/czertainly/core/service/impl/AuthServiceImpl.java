package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.AuthenticationResponseDto;
import com.czertainly.api.model.core.auth.ResourceDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserManagementApiClient userManagementApiClient;

    @Autowired
    private ResourceApiClient resourceApiClient;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACCESS, operation = OperationType.REQUEST)
    public UserDto getAuthProfile() throws NotFoundException {
        return userManagementApiClient.getUserProfile().getData().getUser();
    }

    @Override
    public List<ResourceDetailDto> getAllResources() {
        return resourceApiClient.getAllResources();
    }

}
