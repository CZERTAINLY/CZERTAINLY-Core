package com.czertainly.core.service.impl;

import com.czertainly.api.core.modal.*;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.AdminRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.LocalAdminService;
import com.czertainly.core.util.CertificateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.net.InetAddress;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Service
@Transactional
public class LocalAdminServiceImpl implements LocalAdminService {
    private static final Logger logger = LoggerFactory.getLogger(LocalAdminServiceImpl.class);

    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateService certificateService;

    @Override
    @AuditLogged(originator = ObjectType.LOCALHOST, affected = ObjectType.ADMINISTRATOR, operation = OperationType.CREATE)
    public AdminDto addAdmin(AddAdminRequestDto request) throws CertificateException, AlreadyExistException, ValidationException, NotFoundException {
        checkHost();

        String localhostPrefix = "[LOCALHOST]";

        logger.info("{} Going process request from localhost to create administrator. Request: {}", localhostPrefix, request);

        if (StringUtils.isBlank(request.getUsername())) {
            throw new ValidationException("username must not be empty");
        }
        if (StringUtils.isAnyBlank(request.getName(), request.getSurname())) {
            throw new ValidationException("name and surname must not be empty");
        }
        if (StringUtils.isBlank(request.getEmail())) {
            throw new ValidationException("email must not be empty");
        }

        logger.info("{} Request for creating administrator {} validated.", localhostPrefix, request.getUsername());

        if (request.getRole() == null) {
            request.setRole(AdminRole.SUPERADMINISTRATOR);
        }

        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new AlreadyExistException(Admin.class, request.getUsername());
        }

        logger.info("{} Checked that administrator {} doesn't exist.", localhostPrefix, request.getUsername());

        String serialNumber;

        if (StringUtils.isNotBlank(request.getAdminCertificate())) {
            X509Certificate certificate = CertificateUtil.getX509Certificate(request.getAdminCertificate());
            serialNumber = CertificateUtil.getSerialNumberFromX509Certificate(certificate);
        } else {
            Certificate certificate = certificateService.getCertificateEntity(request.getCertificateUuid());
            serialNumber = certificate.getSerialNumber();
        }

        logger.info("{} Certificate serial number for administrator {} determined - {}.", localhostPrefix, request.getUsername(), serialNumber);

        if (adminRepository.findBySerialNumber(serialNumber).isPresent()) {
            throw new AlreadyExistException(Admin.class, serialNumber);
        }

        Admin admin = createAdmin(request);
        adminRepository.save(admin);
        logger.info("{} Administrator {} successfully created.", localhostPrefix, request.getUsername());

        return admin.mapToDto();
    }

    private Admin createAdmin(AddAdminRequestDto requestDTO) throws CertificateException, AlreadyExistException, NotFoundException {
        Admin model = new Admin();

        Certificate certificate;
        if(!requestDTO.getCertificateUuid().isEmpty()) {
            certificate = certificateService.getCertificateEntity(requestDTO.getCertificateUuid());
            model.setCertificate(certificate);
        } else {
            X509Certificate x509Cert = CertificateUtil.parseCertificate(requestDTO.getAdminCertificate());
            if (certificateRepository.findBySerialNumberIgnoreCase(x509Cert.getSerialNumber().toString(16)).isPresent()) {
                throw new AlreadyExistException(Certificate.class, x509Cert.getSerialNumber().toString(16));
            }
            certificate = certificateService.createCertificateEntity(x509Cert);
            certificateRepository.save(certificate);
            model.setCertificate(certificate);
        }
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

    /**
     * This method checks if current request comes from same host.
     * @throws InsufficientAuthenticationException
     */
    private void checkHost() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes)) {
                logger.warn("Could not get current servlet request.");
                return;
            }

            HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
            if ("localhost".equals(request.getRemoteHost()) ||
                    "127.0.0.1".equals(request.getRemoteHost())) {
                logger.info("Request comes from localhost");
            } else if (InetAddress.getLocalHost().getHostName().equals(request.getRemoteHost())) {
                logger.info("Request comes from same host");
            } else {
                throw new InsufficientAuthenticationException("Request not comes from allowed host");
            }
        } catch (InsufficientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Could not check client host.", e);
        }
    }
}
