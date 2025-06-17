package com.czertainly.core.service.impl;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.auth.AddUserRequestDto;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.client.auth.UserIdentificationRequestDto;
import com.czertainly.api.model.client.certificate.UploadCertificateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.*;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.logging.LoggerWrapper;
import com.czertainly.core.model.auth.AuthenticationRequestDto;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.GroupService;
import com.czertainly.core.service.ResourceObjectAssociationService;
import com.czertainly.core.service.UserManagementService;
import com.czertainly.core.util.CertificateUtil;
import com.nimbusds.jwt.SignedJWT;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service(Resource.Codes.USER)
@Transactional
public class UserManagementServiceImpl implements UserManagementService {
    private static final LoggerWrapper logger = new LoggerWrapper(UserManagementServiceImpl.class, Module.AUTH, Resource.USER);

    private UserManagementApiClient userManagementApiClient;

    private CertificateService certificateService;
    private GroupService groupService;
    private ResourceObjectAssociationService objectAssociationService;

    private AttributeEngine attributeEngine;

    @Autowired
    public void setUserManagementApiClient(UserManagementApiClient userManagementApiClient) {
        this.userManagementApiClient = userManagementApiClient;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setGroupService(GroupService groupService) {
        this.groupService = groupService;
    }

    @Autowired
    public void setObjectAssociationService(ResourceObjectAssociationService objectAssociationService) {
        this.objectAssociationService = objectAssociationService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.LIST)
    public List<UserDto> listUsers() {
        return userManagementApiClient.getUsers().getData();
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.DETAIL)
    public UserDetailDto getUser(String userUuid) throws NotFoundException {
        UserDetailDto dto = userManagementApiClient.getUserDetail(userUuid);
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.USER, UUID.fromString(userUuid)));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.CREATE)
    public UserDetailDto createUser(AddUserRequestDto request) throws CertificateException, NotFoundException, AttributeException {
        attributeEngine.validateCustomAttributesContent(Resource.USER, request.getCustomAttributes());
        if (StringUtils.isBlank(request.getUsername())) {
            throw new ValidationException(ValidationError.create("username must not be empty"));
        }
        UserRequestDto requestDto = new UserRequestDto();
        Certificate certificate = null;
        if ((request.getCertificateUuid() != null && !request.getCertificateUuid().isEmpty()) || (request.getCertificateData() != null && !request.getCertificateData().isEmpty())) {
            certificate = addUserCertificate(null, request.getCertificateUuid(), request.getCertificateData());
            requestDto.setCertificateUuid(certificate.getUuid().toString());
            requestDto.setCertificateFingerprint(certificate.getFingerprint());
        }
        requestDto.setEmail(request.getEmail());
        requestDto.setEnabled(request.getEnabled());
        requestDto.setUsername(request.getUsername());
        requestDto.setFirstName(request.getFirstName());
        requestDto.setLastName(request.getLastName());
        requestDto.setDescription(request.getDescription());

        List<NameAndUuidDto> groups = new ArrayList<>();
        for (String groupUuid : request.getGroupUuids()) {
            GroupDto groupDto = groupService.getGroup(SecuredUUID.fromString(groupUuid));
            groups.add(new NameAndUuidDto(groupDto.getUuid(), groupDto.getName()));
        }
        requestDto.setGroups(groups);

        UserDetailDto response = userManagementApiClient.createUser(requestDto);
        if (certificate != null) {
            certificateService.updateCertificateUser(certificate.getUuid(), response.getUuid());
        }

        response.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.USER, UUID.fromString(response.getUuid()), request.getCustomAttributes()));

        logger.logEvent(Operation.CREATE, OperationResult.SUCCESS, response.toLogData(), null);
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.UPDATE)
    public UserDetailDto updateUser(String userUuid, UpdateUserRequestDto request) throws NotFoundException, CertificateException, AttributeException {
        attributeEngine.validateCustomAttributesContent(Resource.USER, request.getCustomAttributes());
        UserDetailDto dto = getUserUpdateRequestPayload(userUuid, request, "", "");
        dto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.USER, UUID.fromString(userUuid), request.getCustomAttributes()));
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

        UUID uuid = UUID.fromString(userUuid);
        certificateService.removeCertificateUser(uuid);
        objectAssociationService.removeOwnerAssociations(uuid);
        attributeEngine.deleteAllObjectAttributeContent(Resource.USER, UUID.fromString(userUuid));
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

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.DETAIL)
    public UserDetailDto identifyUser(UserIdentificationRequestDto request) throws NotFoundException {
        AuthenticationRequestDto authenticationRequest = new AuthenticationRequestDto();
        if (request.getCertificateContent() != null) {
            authenticationRequest.setCertificateContent(CertificateUtil.normalizeCertificateContent(request.getCertificateContent()));
        } else if (request.getAuthenticationToken() != null) {
            Map<String, Object> userClaims;
            SignedJWT signedJWT;
            try {
                signedJWT = SignedJWT.parse(request.getAuthenticationToken());
                userClaims = signedJWT.getJWTClaimsSet().getClaims();
            } catch (ParseException e) {
                throw new ValidationException("Could not extract claims from Authentication Token: " + e.getMessage());
            }
            authenticationRequest.setAuthenticationTokenUserClaims(userClaims);
        } else {
            throw new ValidationException("User cannot be identified without providing certificate or JWT token");
        }

        UserDetailDto dto = userManagementApiClient.identifyUser(authenticationRequest);
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.USER, UUID.fromString(dto.getUuid())));
        return dto;
    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return listUsers().stream().map(u -> new NameAndUuidDto(u.getUuid(), u.getUsername())).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.USER, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getUser(uuid.toString());
    }

    private Certificate addUserCertificate(String userUuid, String certificateUuid, String certificateData) throws CertificateException, NotFoundException {
        Certificate certificate = null;
        boolean uploadCertificate = false;
        if (StringUtils.isNotBlank(certificateUuid)) {
            certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
        } else {
            X509Certificate x509Cert = CertificateUtil.parseCertificate(certificateData);
            try {
                x509Cert.checkValidity();
            } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                throw new ValidationException(ValidationError.create("Certificate is not valid."));
            }
            try {
                certificate = certificateService.getCertificateEntityByFingerprint(CertificateUtil.getThumbprint(x509Cert));
            } catch (NotFoundException e) {
                uploadCertificate = true;
            } catch (NoSuchAlgorithmException e) {
                throw new ValidationException(ValidationError.create("Cannot assign certificate to the user due to error in fingerprint calculation: " + e.getMessage()));
            }
        }

        if (uploadCertificate) {
            try {
                UploadCertificateRequestDto uploadRequest = new UploadCertificateRequestDto();
                uploadRequest.setCertificate(certificateData);
                CertificateDetailDto certificateDetailDto = certificateService.upload(uploadRequest, true);
                certificate = certificateService.getCertificateEntityByFingerprint(certificateDetailDto.getFingerprint());
                logger.getLogger().debug("New Certificate uploaded for the user");
            } catch (Exception e) {
                throw new CertificateException("Cannot upload certificate that should be assigned to the user: " + e.getMessage());
            }
        } else {
            if (!certificate.getState().equals(CertificateState.ISSUED)) {
                throw new ValidationException(ValidationError.create("Cannot assign certificate with state %s to the user".formatted(certificate.getState().getLabel())));
            }
            if (certificate.getUserUuid() != null && !certificate.getUserUuid().toString().equals(userUuid)) {
                throw new ValidationException(ValidationError.create("Cannot assign certificate to the user because it is already assigned to other user"));
            }
        }
        return certificate;
    }

    private UserDetailDto getUserUpdateRequestPayload(String userUuid, UpdateUserRequestDto request, String certificateUuid, String certificateFingerPrint) throws NotFoundException, CertificateException {
        Certificate certificate = null;
        UserUpdateRequestDto requestDto = new UserUpdateRequestDto();

        if ((request.getCertificateUuid() != null && !request.getCertificateUuid().isEmpty()) || (request.getCertificateData() != null && !request.getCertificateData().isEmpty())) {
            certificate = addUserCertificate(userUuid, request.getCertificateUuid(), request.getCertificateData());
            requestDto.setCertificateUuid(certificate.getUuid().toString());
            requestDto.setCertificateFingerprint(certificate.getFingerprint());
        } else {
            if (!certificateUuid.isEmpty()) requestDto.setCertificateUuid(certificateUuid);
            if (!certificateFingerPrint.isEmpty()) requestDto.setCertificateFingerprint(certificateFingerPrint);
        }

        requestDto.setDescription(request.getDescription());
        requestDto.setEmail(request.getEmail());
        requestDto.setFirstName(request.getFirstName());
        requestDto.setLastName(request.getLastName());

        if (request.getGroupUuids() != null) {
            List<NameAndUuidDto> groups = new ArrayList<>();
            for (String groupUuid : request.getGroupUuids()) {
                GroupDto groupDto = groupService.getGroup(SecuredUUID.fromString(groupUuid));
                groups.add(new NameAndUuidDto(groupDto.getUuid(), groupDto.getName()));
            }
            requestDto.setGroups(groups);
        }

        UserDetailDto response = userManagementApiClient.updateUser(userUuid, requestDto);

        try {
            certificateService.removeCertificateUser(UUID.fromString(response.getUuid()));
        } catch (Exception e) {
            logger.getLogger().info("Unable to remove user uuid. It may not exists {}", e.getMessage());
        }
        if (certificate != null) {
            certificateService.updateCertificateUser(certificate.getUuid(), response.getUuid());
        }
        return response;
    }
}
