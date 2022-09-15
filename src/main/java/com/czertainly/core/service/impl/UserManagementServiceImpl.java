package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.AddUserRequestDto;
import com.czertainly.api.model.core.auth.MergedPermissionsDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.api.model.core.auth.UserUpdateRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.UserManagementService;
import com.czertainly.core.util.CertificateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

@Service
@Transactional
public class UserManagementServiceImpl implements UserManagementService {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementServiceImpl.class);

    @Autowired
    private UserManagementApiClient userManagementApiClient;

    @Autowired
    private CertificateService certificateService;

    @Override
    public List<UserDto> listUsers() {
        return userManagementApiClient.getUsers().getData();
    }

    @Override
    public UserDetailDto getUser(String userUuid) throws NotFoundException {
        return userManagementApiClient.getUserDetail(userUuid);
    }

    @Override
    public UserDto createUser(AddUserRequestDto request) throws CertificateException, NotFoundException {
        if (StringUtils.isBlank(request.getUsername())) {
            throw new ValidationException("username must not be empty");
        }
        if (StringUtils.isBlank(request.getEmail())) {
            throw new ValidationException("email must not be empty");
        }

        Certificate certificate;
        if (StringUtils.isNotBlank(request.getCertificateUuid())) {
            certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(request.getCertificateUuid()));
        } else {
            X509Certificate x509Cert = CertificateUtil.parseCertificate(request.getCertificateData());
            try {
                certificate = certificateService.getCertificateEntityBySerial(x509Cert.getSerialNumber().toString(16));
            } catch (NotFoundException e) {
                logger.debug("New Certificate uploaded for admin");
                certificate = certificateService.createCertificateEntity(x509Cert);
                certificateService.updateCertificateEntity(certificate);
            }
        }

        UserRequestDto requestDto = new UserRequestDto();
        requestDto.setEmail(request.getEmail());
        requestDto.setEnabled(true);
        requestDto.setUsername(request.getUsername());
        requestDto.setFirstName(request.getFirstName());
        requestDto.setLastName(request.getLastName());
        requestDto.setCertificateUuid(certificate.getUuid().toString());
        requestDto.setCertificateFingerprint(certificate.getFingerprint());

        return userManagementApiClient.createUser(requestDto);
    }

    @Override
    public UserDto updateUser(String userUuid, UserUpdateRequestDto request) {
        return userManagementApiClient.updateUser(userUuid, request);
    }

    @Override
    public void deleteUser(String userUuid) {
        userManagementApiClient.removeUser(userUuid);
    }

    @Override
    public UserDetailDto updateRoles(String userUuid, List<String> roleUuids) {
        return userManagementApiClient.updateRoles(userUuid, roleUuids);
    }

    @Override
    public UserDetailDto updateRole(String userUuid, String roleUuid) {
        return userManagementApiClient.updateRole(userUuid, roleUuid);
    }

    @Override
    public MergedPermissionsDto getPermissions(String userUuid) {
        return userManagementApiClient.getPermissions(userUuid);
    }
}
