package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.AddUserRequestDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.api.model.core.auth.UserUpdateRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.LocalAdminService;
import com.czertainly.core.service.UserManagementService;
import com.czertainly.core.util.CertificateUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Service
@Transactional
public class LocalAdminServiceImpl implements LocalAdminService {

    private UserManagementApiClient userManagementApiClient;
    private UserManagementService userManagementService;
    private CertificateService certificateService;

    private static final String AUTH_SUPER_ADMIN_ROLE_UUID = "d34f960b-75c9-4184-ba97-665d30a9ee8a";

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


    @Override
    public UserDetailDto createUser(AddUserRequestDto request) throws NotFoundException, CertificateException, NoSuchAlgorithmException {
        if(request.getCertificateUuid() != null && !request.getCertificateUuid().isEmpty()) {
            UserDetailDto userResponse = userManagementService.createUser(request);
            userManagementService.updateRole(userResponse.getUuid(), AUTH_SUPER_ADMIN_ROLE_UUID);
            return userResponse;
        }

        X509Certificate x509Cert = CertificateUtil.parseCertificate(request.getCertificateData());
        String fingerPrint = getCertificateFingerprint(x509Cert);

        UserDetailDto response = createUser(request, fingerPrint);

        //TODO Add Auth header to the request - self assignment
        //TODO logger

        Certificate certificate = uploadCertificate(request.getCertificateUuid(), request.getCertificateData());
        certificateService.updateCertificateUser(certificate.getUuid(), response.getUuid());
        userManagementService.updateRole(response.getUuid(), AUTH_SUPER_ADMIN_ROLE_UUID);

        return updateUser(request, fingerPrint, certificate.getUuid().toString(), response.getUuid());
    }

    private UserDetailDto createUser(AddUserRequestDto request, String fingerprint) {
        UserRequestDto requestDto = new UserRequestDto();
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
                certificate = certificateService.getCertificateEntityBySerial(x509Cert.getSerialNumber().toString(16));
            } catch (NotFoundException e) {
//                logger.debug("New Certificate uploaded for the user");
                certificate = certificateService.createCertificateEntity(x509Cert);
                certificateService.updateCertificateEntity(certificate);
            }
        }
        return certificate;
    }


}
