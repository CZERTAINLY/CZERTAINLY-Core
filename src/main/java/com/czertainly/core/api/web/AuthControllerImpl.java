package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.AuthController;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.AuthResourceDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserProfileDetailDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.service.AuthService;
import com.czertainly.core.service.ResourceService;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.CertificateException;
import java.util.List;

@RestController
public class AuthControllerImpl implements AuthController {

    private AuthService authService;
    private ResourceService resourceService;

    @Autowired
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(Resource.class, new ResourceCodeConverter());
    }

    @Override
    @AuditLogged(module = Module.AUTH, resource = Resource.USER, operation = Operation.GET_USER_PROFILE)
    public UserProfileDetailDto profile() {
        return authService.getAuthProfile();
    }

    @Override
    @AuditLogged(module = Module.AUTH, resource = Resource.USER, operation = Operation.UPDATE_USER_PROFILE)
    public UserDetailDto updateUserProfile(UpdateUserRequestDto request) throws NotFoundException, CertificateException {
        return authService.updateUserProfile(request);
    }

    @Override
    public List<AuthResourceDto> getAuthResources() {
        return authService.getAuthResources();
    }

    @Override
    public List<NameAndUuidDto> getObjectsForResource(Resource resourceName) throws NotFoundException {
        return resourceService.getObjectsForResource(resourceName);
    }


}

