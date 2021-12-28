package com.czertainly.core.auth;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.core.dao.entity.Client;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.ClientRepository;
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
import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional
public class ClientDetailsService implements UserDetailsService {

    static Logger log = LoggerFactory.getLogger(ClientDetailsService.class.getName());

    static List<CertificateStatus> UNALLOWED_CERTIFICATE_STATUS = Arrays.asList(CertificateStatus.REVOKED, CertificateStatus.EXPIRED, CertificateStatus.INVALID);

    @Autowired
    private ClientRepository clientRepository;

    @Override
    public UserDetails loadUserByUsername(String name) throws UsernameNotFoundException {
        List<String> authList;
        Client client;
        try {
            client = clientRepository.findBySerialNumber(name)
                    .orElseThrow(() -> new NotFoundException(Client.class, name));
            if (client.getEnabled() == null || !client.getEnabled()) {
                String message = String.format("Username found: %s, but not enabled.", name);
                log.info(message);
                throw new UsernameNotFoundException(message);
            }
            if(UNALLOWED_CERTIFICATE_STATUS.contains(client.getCertificate().getStatus())){
                String message = String.format("Client found with SN: %s, name %s, but the status is %s.", client.getSerialNumber(), client.getName(), client.getCertificate().getStatus().toString());
                log.info(message);
                throw new UsernameNotFoundException(message);
            }
            authList = client.getRaProfiles().stream().map(RaProfile::getName).collect(Collectors.toList());
            // Base role for all found and authenticated clients
            authList.add("CLIENT");
        } catch (NotFoundException e) {
            String message = String.format("Username not found: %s", name);
            log.info(message);
            throw new UsernameNotFoundException(message);
        }
        return User.withUsername(client.getName()).password("").roles(authList.toArray(new String[]{})).build();
    }

}
