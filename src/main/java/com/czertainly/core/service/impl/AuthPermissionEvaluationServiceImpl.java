package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.PermissionEvaluator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthPermissionEvaluationServiceImpl implements PermissionEvaluator {

    @Override
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.DETAIL)
    public void tokenProfile(SecuredUUID uuid) throws NotFoundException { }

    @Override
    @ExternalAuthorization(resource = Resource.TOKEN, action = ResourceAction.DETAIL)
    public void tokenInstance(SecuredUUID uuid) throws NotFoundException { }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DETAIL)
    public void certificate(SecuredUUID uuid) throws NotFoundException { }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public void authorityInstance(SecuredUUID uuid) throws NotFoundException { }

    @Override
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.LIST)
    public void tokenProfiles(List<SecuredUUID> uuids) { }
}
