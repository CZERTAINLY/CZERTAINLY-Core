package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.auth.AddUserRequestDto;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.SubjectPermissionsDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.api.model.core.auth.UserUpdateRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.UserManagementService;
import com.czertainly.core.util.CertificateUtil;
import org.apache.catalina.User;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserManagementServiceImpl implements UserManagementService {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementServiceImpl.class);

    @Autowired
    private UserManagementApiClient userManagementApiClient;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private AttributeService attributeService;

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.LIST)
    public List<UserDto> listUsers() {
        return userManagementApiClient.getUsers().getData();
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.DETAIL)
    public UserDetailDto getUser(String userUuid) throws NotFoundException {
        UserDetailDto dto = userManagementApiClient.getUserDetail(userUuid);
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(UUID.fromString(userUuid), Resource.USER));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.CREATE)
    public UserDetailDto createUser(AddUserRequestDto request) throws CertificateException, NotFoundException {
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.USER);
        if (StringUtils.isBlank(request.getUsername())) {
            throw new ValidationException("username must not be empty");
        }
        UserRequestDto requestDto = new UserRequestDto();
        Certificate certificate = null;
        if ((request.getCertificateUuid() != null && !request.getCertificateUuid().isEmpty()) || (request.getCertificateData() != null && !request.getCertificateData().isEmpty())) {
            certificate = addUserCertificate(request.getCertificateUuid(), request.getCertificateData());
            requestDto.setCertificateUuid(certificate.getUuid().toString());
            requestDto.setCertificateFingerprint(certificate.getFingerprint());
        }
        requestDto.setEmail(request.getEmail());
        requestDto.setEnabled(request.getEnabled());
        requestDto.setUsername(request.getUsername());
        requestDto.setFirstName(request.getFirstName());
        requestDto.setLastName(request.getLastName());
        requestDto.setDescription(request.getDescription());

        UserDetailDto response = userManagementApiClient.createUser(requestDto);
        if (certificate != null) {
            certificateService.updateCertificateUser(certificate.getUuid(), response.getUuid());
        }

        attributeService.deleteAttributeContent(UUID.fromString(response.getUuid()), request.getCustomAttributes(), Resource.USER);
        response.setCustomAttributes(attributeService.getCustomAttributesWithValues(UUID.fromString(response.getUuid()), Resource.USER));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.UPDATE)
    public UserDetailDto updateUser(String userUuid, UpdateUserRequestDto request) throws NotFoundException, CertificateException {
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.USER);
        UserDetailDto dto = getUserUpdateRequestPayload(userUuid, request, "", "");
        attributeService.updateAttributeContent(UUID.fromString(userUuid), request.getCustomAttributes(), Resource.USER);
        return dto;
    }

    @Override
    //Internal Use Only -- For Auth Profile Update API
    public UserDetailDto updateUserInternal(String userUuid, UpdateUserRequestDto request, String certificateUuid, String certificateFingerprint) throws NotFoundException, CertificateException {
        return getUserUpdateRequestPayload(userUuid, request, certificateUuid, certificateFingerprint);
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.DELETE)
    public void deleteUser(String userUuid) {
        userManagementApiClient.removeUser(userUuid);
        certificateService.removeCertificateUser(UUID.fromString(userUuid));
        attributeService.deleteAttributeContent(UUID.fromString(userUuid), Resource.USER);
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.UPDATE)
    public UserDetailDto updateRoles(String userUuid, List<String> roleUuids) {
        return userManagementApiClient.updateRoles(userUuid, roleUuids);
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.UPDATE)
    public UserDetailDto updateRole(String userUuid, String roleUuid) {
        return userManagementApiClient.updateRole(userUuid, roleUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.DETAIL)
    public SubjectPermissionsDto getPermissions(String userUuid) {
        return userManagementApiClient.getPermissions(userUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.ENABLE)
    public UserDetailDto enableUser(String userUuid) {
        return userManagementApiClient.enableUser(userUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.ENABLE)
    public UserDetailDto disableUser(String userUuid) {
        return userManagementApiClient.disableUser(userUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.DETAIL)
    public List<RoleDto> getUserRoles(String userUuid) {
        return userManagementApiClient.getUserRoles(userUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.UPDATE)
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
                certificate = certificateService.getCertificateEntityByFingerprint(CertificateUtil.getThumbprint(x509Cert));
            } catch (NotFoundException | NoSuchAlgorithmException e) {
                logger.debug("New Certificate uploaded for the user");
                certificate = certificateService.createCertificateEntity(x509Cert);
                certificateService.updateCertificateEntity(certificate);
            }
        }
        return certificate;
    }

    private UserDetailDto getUserUpdateRequestPayload(String userUuid, UpdateUserRequestDto request, String certificateUuid, String certificateFingerPrint) throws NotFoundException, CertificateException {
        Certificate certificate = null;
        UserUpdateRequestDto requestDto = new UserUpdateRequestDto();

        if ((request.getCertificateUuid() != null && !request.getCertificateUuid().isEmpty()) || (request.getCertificateData() != null && !request.getCertificateData().isEmpty())) {
            certificate = addUserCertificate(request.getCertificateUuid(), request.getCertificateData());
            requestDto.setCertificateUuid(certificate.getUuid().toString());
            requestDto.setCertificateFingerprint(certificate.getFingerprint());
        }else {
            if(!certificateUuid.isEmpty()) requestDto.setCertificateUuid(certificateUuid);
            if(!certificateFingerPrint.isEmpty()) requestDto.setCertificateFingerprint(certificateFingerPrint);
        }

        requestDto.setDescription(request.getDescription());
        requestDto.setEmail(request.getEmail());
        requestDto.setFirstName(request.getFirstName());
        requestDto.setLastName(request.getLastName());
        UserDetailDto response = userManagementApiClient.updateUser(userUuid, requestDto);

        try {
            certificateService.removeCertificateUser(UUID.fromString(response.getUuid()));
        } catch (Exception e) {
            logger.info("Unable to remove user uuid. It may not exists {}", e.getMessage());
        }
        if (certificate != null) {
            certificateService.updateCertificateUser(certificate.getUuid(), response.getUuid());
        }
        return response;
    }
}
