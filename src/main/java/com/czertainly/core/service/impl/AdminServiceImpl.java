package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.admin.AddAdminRequestDto;
import com.czertainly.api.model.client.admin.EditAdminRequestDto;
import com.czertainly.api.model.core.admin.AdminDto;
import com.czertainly.api.model.core.admin.AdminRole;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.AdminRepository;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AdminService;
import com.czertainly.core.service.CertificateService;
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
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminServiceImpl implements AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminServiceImpl.class);

    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private CertificateService certificateService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.LIST)
    public List<AdminDto> listAdmins(SecurityFilter filter) {
        List<Admin> admins = adminRepository.findUsingSecurityFilter(filter);

        return admins.stream().map(Admin::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.CREATE)
    public AdminDto addAdmin(AddAdminRequestDto request)
            throws CertificateException, AlreadyExistException, ValidationException, NotFoundException {

        if (StringUtils.isBlank(request.getUsername())) {
            throw new ValidationException("username must not be empty");
        }
        if (StringUtils.isAnyBlank(request.getName(), request.getSurname())) {
            throw new ValidationException("name and surname must not be empty");
        }
        if (StringUtils.isBlank(request.getEmail())) {
            throw new ValidationException("email must not be empty");
        }

        if (request.getRole() == null) {
            request.setRole(AdminRole.ADMINISTRATOR);
        }

        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new AlreadyExistException(Admin.class, request.getUsername());
        }

        String serialNumber;
        String subjectDn;
        if (!StringUtils.isAnyBlank(request.getAdminCertificate())) {
            X509Certificate certificate = CertificateUtil.getX509Certificate(request.getAdminCertificate());
            serialNumber = CertificateUtil.getSerialNumberFromX509Certificate(certificate);
            subjectDn = certificate.getSubjectDN().toString();
        } else {
            Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(request.getCertificateUuid()));
            serialNumber = certificate.getSerialNumber();
            subjectDn = certificate.getSubjectDn();
        }

        if (adminRepository.findBySerialNumber(serialNumber).isPresent()) {
            throw new AlreadyExistException(Admin.class, serialNumber);
        }

        Admin admin = createAdmin(request);

        adminRepository.save(admin);
        logger.info("Admin {} registered successfully.", subjectDn);

        return admin.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.DETAIL)
    public AdminDto getAdminByUuid(SecuredUUID uuid) throws NotFoundException {
        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));
        return admin.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.UPDATE)
    public AdminDto editAdmin(SecuredUUID uuid, EditAdminRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException {
        if (StringUtils.isAnyBlank(request.getName(), request.getSurname())) {
            throw new ValidationException("name and surname must not be empty");
        }
        if (StringUtils.isBlank(request.getEmail())) {
            throw new ValidationException("email must not be empty");
        }

        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));

        if (request.getAdminCertificate() != null) {
            X509Certificate certificate = CertificateUtil.getX509Certificate(request.getAdminCertificate());
            String serialNumber = CertificateUtil.getSerialNumberFromX509Certificate(certificate);
            // Updating to another certificate?
            if (!admin.getSerialNumber().equals(serialNumber) &&
                    adminRepository.findBySerialNumber(serialNumber).isPresent()) {
                throw new AlreadyExistException(Admin.class, serialNumber);
            }
        }

        updateAdmin(admin, request);

        adminRepository.save(admin);
        logger.info("Admin {} updated successfully.", uuid);

        return admin.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.DELETE)
    public void deleteAdmin(SecuredUUID uuid) throws NotFoundException {
        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));
        adminRepository.delete(admin);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.ENABLE)
    public void enableAdmin(SecuredUUID uuid) throws NotFoundException, CertificateException {
        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));

        admin.setEnabled(true);
        adminRepository.save(admin);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.ENABLE)
    public void disableAdmin(SecuredUUID uuid) throws NotFoundException {
        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));

        admin.setEnabled(false);
        adminRepository.save(admin);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.DETAIL)
    public void bulkDeleteAdmin(List<SecuredUUID> adminUuids) {
        for (SecuredUUID uuid : adminUuids) {
            try {
                deleteAdmin(uuid);
            } catch (NotFoundException e) {
                logger.warn("Unable to delete the admin with id {}", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.ENABLE)
    public void bulkDisableAdmin(List<SecuredUUID> adminUuids) {
        for (SecuredUUID uuid : adminUuids) {
            try {
                disableAdmin(uuid);
            } catch (NotFoundException e) {
                logger.warn("Unable to disable admin with id {}", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.ADMIN, action = ResourceAction.ENABLE)
    public void bulkEnableAdmin(List<SecuredUUID> adminUuids) {
        for (SecuredUUID uuid : adminUuids) {
            try {
                enableAdmin(uuid);
            } catch (NotFoundException | CertificateException e) {
                logger.warn("Unable to enable admin with id {}", uuid);
            }
        }
    }

    private Admin createAdmin(AddAdminRequestDto requestDTO) throws CertificateException, AlreadyExistException, NotFoundException {
        Admin model = new Admin();

        Certificate certificate = null;
        if (StringUtils.isNotBlank(requestDTO.getCertificateUuid())) {
            certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(requestDTO.getCertificateUuid()));
        } else {
            X509Certificate x509Cert = CertificateUtil.parseCertificate(requestDTO.getAdminCertificate());
            try {
                certificate = certificateService.getCertificateEntityBySerial(x509Cert.getSerialNumber().toString(16));
            } catch (NotFoundException e) {
                logger.debug("New Certificate uploaded for admin");
            }
            if(certificate != null) {
                certificate = certificateService.createCertificateEntity(x509Cert);
                certificateService.updateCertificateEntity(certificate);
            }
        }
        model.setCertificate(certificate);
        model.setCertificateUuid(certificate.getUuid());
        model.setUsername(requestDTO.getUsername());
        model.setName(requestDTO.getName());
        model.setDescription(requestDTO.getDescription());
        model.setEnabled(requestDTO.getEnabled() != null && requestDTO.getEnabled());
        model.setRole(requestDTO.getRole());
        model.setEmail(requestDTO.getEmail());
        model.setSurname(requestDTO.getSurname());
        model.setSerialNumber(certificate.getSerialNumber());
        return model;
    }

    private Admin updateAdmin(Admin admin, EditAdminRequestDto dto) throws CertificateException, NotFoundException, AlreadyExistException {

        Certificate certificate;
        if ((dto.getAdminCertificate() != null && !dto.getAdminCertificate().isEmpty()) || (dto.getCertificateUuid() != null && !dto.getCertificateUuid().isEmpty())) {
            if (!dto.getCertificateUuid().isEmpty()) {
                certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(dto.getCertificateUuid()));

            } else {
                X509Certificate x509Cert = CertificateUtil.parseCertificate(dto.getAdminCertificate());
                try {
                    certificateService.getCertificateEntityBySerial(x509Cert.getSerialNumber().toString(16));
                    throw new AlreadyExistException(Certificate.class, x509Cert.getSerialNumber().toString(16));
                } catch (NotFoundException e) {
                    logger.debug("New Certificate uploaded for admin");
                }

                certificate = certificateService.createCertificateEntity(x509Cert);
                certificateService.updateCertificateEntity(certificate);
            }
            admin.setCertificate(certificate);
            admin.setSerialNumber(certificate.getSerialNumber());
        }

        admin.setName(dto.getName());
        admin.setDescription(dto.getDescription());
        if (dto.getRole() != null) {
            admin.setRole(dto.getRole());
        }
        admin.setEmail(dto.getEmail());
        admin.setSurname(dto.getSurname());

        return admin;
    }
}
