package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.auth.AddUserRequestDto;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.core.auth.*;
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
    public UserDetailDto createUser(AddUserRequestDto request) throws CertificateException, NotFoundException {

        if (StringUtils.isBlank(request.getUsername())) {
            throw new ValidationException("username must not be empty");
        }
        Certificate certificate = addUserCertificate(request.getCertificateUuid(), request.getCertificateData());
        UserRequestDto requestDto = new UserRequestDto();
        requestDto.setEmail(request.getEmail());
        requestDto.setEnabled(request.getEnabled());
        requestDto.setUsername(request.getUsername());
        requestDto.setFirstName(request.getFirstName());
        requestDto.setLastName(request.getLastName());
        requestDto.setCertificateUuid(certificate.getUuid().toString());
        requestDto.setCertificateFingerprint(certificate.getFingerprint());

        UserDetailDto response = userManagementApiClient.createUser(requestDto);
        certificateService.updateCertificateUser(certificate.getUuid(), response.getUuid());

        return response;
    }

    @Override
    public UserDetailDto updateUser(String userUuid, UpdateUserRequestDto request) throws NotFoundException, CertificateException {
        Certificate certificate = addUserCertificate(request.getCertificateUuid(), request.getCertificateData());
        UserUpdateRequestDto requestDto = new UserUpdateRequestDto();
        requestDto.setEmail(request.getEmail());
        requestDto.setFirstName(request.getFirstName());
        requestDto.setLastName(request.getLastName());
        requestDto.setCertificateUuid(certificate.getUuid().toString());
        requestDto.setCertificateFingerprint(certificate.getFingerprint());
        UserDetailDto response = userManagementApiClient.updateUser(userUuid, requestDto);
        certificateService.updateCertificateUser(certificate.getUuid(), response.getUuid());
        return response;
    }

    @Override
    public void deleteUser(String userUuid) {
        userManagementApiClient.removeUser(userUuid);
        certificateService.removeCertificateUser(userUuid);
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
    public SubjectPermissionsDto getPermissions(String userUuid) {
        return userManagementApiClient.getPermissions(userUuid);
    }

    @Override
    public UserDetailDto enableUser(String userUuid) {
        return userManagementApiClient.enableUser(userUuid);
    }

    @Override
    public UserDetailDto disableUser(String userUuid) {
        return userManagementApiClient.disableUser(userUuid);
    }

    @Override
    public List<RoleDto> getUserRoles(String userUuid) {
        return userManagementApiClient.getUserRoles(userUuid);
    }

    @Override
    public UserDetailDto removeRole(String userUuid, String roleUuid) {
        return userManagementApiClient.removeRole(userUuid, roleUuid);
    }

    private Certificate addUserCertificate(String certificateUuid, String certificateData) throws CertificateException, NotFoundException {

        Certificate certificate;
        if (StringUtils.isNotBlank(certificateUuid)) {
            certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
        } else {
            X509Certificate x509Cert = CertificateUtil.parseCertificate(certificateData);
            try {
                certificate = certificateService.getCertificateEntityBySerial(x509Cert.getSerialNumber().toString(16));
            } catch (NotFoundException e) {
                logger.debug("New Certificate uploaded for the user");
                certificate = certificateService.createCertificateEntity(x509Cert);
                certificateService.updateCertificateEntity(certificate);
            }
        }
        return certificate;
    }
}
