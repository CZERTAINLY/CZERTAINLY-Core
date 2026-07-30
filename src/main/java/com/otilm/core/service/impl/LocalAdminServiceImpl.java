package com.otilm.core.service.impl;

import com.otilm.api.exception.AlreadyExistException;
import com.otilm.api.exception.AttributeException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.model.client.auth.AddUserRequestDto;
import com.otilm.api.model.core.auth.UserDetailDto;
import com.otilm.core.security.authn.client.RoleManagementApiClient;
import com.otilm.core.security.authz.UnauthenticatedEndpoint;
import com.otilm.core.service.LocalAdminExternalService;
import com.otilm.core.service.UserManagementExternalService;
import com.otilm.core.service.UserManagementInternalService;
import com.otilm.core.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Service
@Transactional
public class LocalAdminServiceImpl implements LocalAdminExternalService {

    private RoleManagementApiClient roleManagementApiClient;
    private UserManagementExternalService userManagementService;
    private UserManagementInternalService userManagementInternalService;

    @Autowired
    private void setUserManagementExternalService(UserManagementExternalService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @Autowired
    public void setUserManagementInternalService(UserManagementInternalService userManagementInternalService) {
        this.userManagementInternalService = userManagementInternalService;
    }

    @Autowired
    public void setRoleManagementApiClient(RoleManagementApiClient roleManagementApiClient) {
        this.roleManagementApiClient = roleManagementApiClient;
    }

    @Override
    @UnauthenticatedEndpoint
    public UserDetailDto createUser(AddUserRequestDto request) throws NotFoundException, CertificateException, NoSuchAlgorithmException, AlreadyExistException, AttributeException {
        // Resolved first: without the role there is nothing to grant, and creating the user anyway would leave the
        // first administrator holding no permissions, with its username blocking the retry.
        String superadminRoleUuid = getSuperadminRoleUuid();

        UserDetailDto userDetailDto = userManagementService.createUser(request);
        return userManagementInternalService.updateRoleInternal(userDetailDto.getUuid(), superadminRoleUuid);
    }

    private String getSuperadminRoleUuid() throws NotFoundException {
        return roleManagementApiClient.getRoles().getData().stream()
                .filter(role -> Boolean.TRUE.equals(role.getSystemRole())
                        && AuthHelper.SUPERADMIN_ROLE_NAME.equals(role.getName()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Role", AuthHelper.SUPERADMIN_ROLE_NAME))
                .getUuid();
    }
}
