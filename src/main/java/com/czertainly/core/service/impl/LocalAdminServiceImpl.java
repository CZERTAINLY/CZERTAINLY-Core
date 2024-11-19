package com.czertainly.core.service.impl;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.auth.AddUserRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.api.model.core.auth.UserUpdateRequestDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.logging.LoggerWrapper;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.LocalAdminService;
import com.czertainly.core.service.UserManagementService;
import com.czertainly.core.util.CertificateUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.security.cert.CertificateException;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocalAdminServiceImpl implements LocalAdminService {

    private static final LoggerWrapper logger = new LoggerWrapper(UserManagementServiceImpl.class, Module.AUTH, Resource.USER);

    private UserManagementApiClient userManagementApiClient;
    private RoleManagementApiClient roleManagementApiClient;
    private UserManagementService userManagementService;
    private CertificateService certificateService;
    private CzertainlyAuthenticationClient czertainlyAuthenticationClient;

    @Value("${server.ssl.certificate-header-name}")
    private String certificateHeaderName;

    @Autowired
    private void setUserManagementApiClient(UserManagementApiClient userManagementApiClient) {
        this.userManagementApiClient = userManagementApiClient;
    }

    @Autowired
    private void setUserManagementService(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @Autowired
    private void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setCzertainlyAuthenticationClient(CzertainlyAuthenticationClient czertainlyAuthenticationClient) {
        this.czertainlyAuthenticationClient = czertainlyAuthenticationClient;
    }

    @Autowired
    public void setRoleManagementApiClient(RoleManagementApiClient roleManagementApiClient) {
        this.roleManagementApiClient = roleManagementApiClient;
    }

    @Override
    public UserDetailDto createUser(AddUserRequestDto request) throws NotFoundException, CertificateException, NoSuchAlgorithmException, AlreadyExistException, AttributeException {

        String superAdminUuid = getSuperAdminRoleUuid();
        if (request.getCertificateUuid() != null && !request.getCertificateUuid().isEmpty()) {
            UserDetailDto userResponse = userManagementService.createUser(request);
            userManagementService.updateRole(userResponse.getUuid(), superAdminUuid);
            return userResponse;
        }

        X509Certificate x509Cert = CertificateUtil.parseCertificate(request.getCertificateData());
        try {
            x509Cert.checkValidity();
        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
            throw new ValidationException(ValidationError.create("Certificate is not valid."));
        }

        String fingerPrint = getCertificateFingerprint(x509Cert);

        if (certificateService.checkCertificateExistsByFingerprint(fingerPrint)) {
            throw new AlreadyExistException("Certificate already exists.");
        }
        UserDetailDto response = createUser(request, fingerPrint);
        userManagementApiClient.updateRole(response.getUuid(), superAdminUuid);

        AuthenticationInfo authUserInfo = czertainlyAuthenticationClient.authenticate(getHeaders(request.getCertificateData()));
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authUserInfo)));

        Certificate certificate = uploadCertificate(request.getCertificateUuid(), request.getCertificateData());
        certificateService.updateCertificateUser(certificate.getUuid(), response.getUuid());

        logger.logEvent(Operation.CREATE, OperationResult.SUCCESS, response.toLogData(), null);
        return updateUser(request, fingerPrint, certificate.getUuid().toString(), response.getUuid());
    }

    private HttpHeaders getHeaders(String certificateData) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(certificateHeaderName, URLEncoder.encode(certificateData, StandardCharsets.UTF_8));
        return headers;
    }

    private UserDetailDto createUser(AddUserRequestDto request, String fingerprint) {
        UserRequestDto requestDto = new UserRequestDto();
        requestDto.setDescription(request.getDescription());
        requestDto.setEmail(request.getEmail());
        requestDto.setEnabled(request.getEnabled());
        requestDto.setUsername(request.getUsername());
        requestDto.setFirstName(request.getFirstName());
        requestDto.setLastName(request.getLastName());
        requestDto.setCertificateFingerprint(fingerprint);
        return userManagementApiClient.createUser(requestDto);
    }

    private UserDetailDto updateUser(AddUserRequestDto request, String fingerprint, String certificateUuid, String userUuid) {
        UserUpdateRequestDto updateRequestDto = new UserUpdateRequestDto();
        updateRequestDto.setDescription(request.getDescription());
        updateRequestDto.setEmail(request.getEmail());
        updateRequestDto.setFirstName(request.getFirstName());
        updateRequestDto.setLastName(request.getLastName());
        updateRequestDto.setCertificateFingerprint(fingerprint);
        updateRequestDto.setCertificateUuid(certificateUuid);
        return userManagementApiClient.updateUser(userUuid, updateRequestDto);
    }

    private String getCertificateFingerprint(X509Certificate certificate) throws CertificateEncodingException, NoSuchAlgorithmException {
        return CertificateUtil.getThumbprint(certificate.getEncoded());
    }

    private Certificate uploadCertificate(String certificateUuid, String certificateData) throws CertificateException, NotFoundException {
        Certificate certificate;
        if (StringUtils.isNotBlank(certificateUuid)) {
            certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
        } else {
            X509Certificate x509Cert = CertificateUtil.parseCertificate(certificateData);
            try {
                certificate = certificateService.getCertificateEntityByFingerprint(CertificateUtil.getThumbprint(x509Cert));
            } catch (NotFoundException | NoSuchAlgorithmException e) {
                certificate = certificateService.createCertificateEntity(x509Cert);
                certificateService.updateCertificateEntity(certificate);
            }
        }
        return certificate;
    }

    private String getSuperAdminRoleUuid(){
        return roleManagementApiClient.getRoles().getData().stream().filter(e -> e.getSystemRole().equals(true) && e.getName().equals("superadmin")).collect(Collectors.toList()).get(0).getUuid();
    }
}
