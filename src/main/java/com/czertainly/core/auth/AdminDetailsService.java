package com.czertainly.core.auth;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@Transactional
public class AdminDetailsService implements UserDetailsService {

    static Logger log = LoggerFactory.getLogger(AdminDetailsService.class.getName());

    static List<CertificateStatus> UNALLOWED_CERTIFICATE_STATUS = Arrays.asList(CertificateStatus.REVOKED, CertificateStatus.EXPIRED, CertificateStatus.INVALID);

    @Autowired
    private AdminRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String serialNumber) throws UsernameNotFoundException {
        List<String> authList;
        String username;
        try {
            Admin admin = adminRepository.findBySerialNumber(serialNumber)
                    .orElseThrow(() -> new NotFoundException(Admin.class, serialNumber));
            if (admin.getEnabled() == null || !admin.getEnabled()) {
                String message = String.format("Admin found with SN: %s, name %s, but not enabled.", serialNumber, admin.getName());
                log.info(message);
                throw new UsernameNotFoundException(message);
            }
            if(UNALLOWED_CERTIFICATE_STATUS.contains(admin.getCertificate().getStatus())){
                String message = String.format("Admin found with SN: %s, name %s, but the status is %s.", serialNumber, admin.getName(), admin.getCertificate().getStatus().toString());
                log.info(message);
                throw new UsernameNotFoundException(message);
            }
            authList = Collections.singletonList(admin.getRole().name());
            username = admin.getUsername();
        } catch (NotFoundException e) {
            String message = String.format("Admin not found with SN: %s", serialNumber);
            log.info(message);
            throw new UsernameNotFoundException(message);
        }
        return User.withUsername(username).password("").roles(authList.toArray(new String[]{})).build();
    }

}
