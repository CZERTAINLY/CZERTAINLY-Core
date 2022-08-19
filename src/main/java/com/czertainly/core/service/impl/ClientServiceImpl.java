package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.UploadCertificateRequestDto;
import com.czertainly.api.model.client.client.AddClientRequestDto;
import com.czertainly.api.model.client.client.EditClientRequestDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.client.ClientDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Client;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.ClientRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ClientService;
import com.czertainly.core.util.CertificateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClientServiceImpl implements ClientService {

    private static final Logger logger = LoggerFactory.getLogger(ClientServiceImpl.class);

    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateService certificateService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.LIST)
    public List<ClientDto> listClients(SecurityFilter filter) {
        List<Client> clients = clientRepository.findUsingSecurityFilter(filter);
        return clients.stream().map(Client::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.CREATE)
    public ClientDto addClient(AddClientRequestDto request)
            throws CertificateException, AlreadyExistException, NotFoundException, ValidationException {
        if (StringUtils.isAnyBlank(request.getName())) {
            throw new ValidationException("clientCertificate and name must not be empty");
        }

        String dn;
        String serialNumber;

        if (!StringUtils.isAnyBlank(request.getClientCertificate())) {
            X509Certificate certificate = CertificateUtil.getX509Certificate(request.getClientCertificate());
            dn = CertificateUtil.getDnFromX509Certificate(certificate);
            serialNumber = CertificateUtil.getSerialNumberFromX509Certificate(certificate);
        } else {
            Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(request.getCertificateUuid()));
            dn = certificate.getSubjectDn();
            serialNumber = certificate.getSerialNumber();
        }

        if (clientRepository.existsByName(request.getName())) {
            throw new AlreadyExistException(Client.class, "name");
        }

        if (clientRepository.findBySerialNumber(serialNumber).isPresent()) {
            throw new AlreadyExistException(Client.class, serialNumber);
        }

        Client client = createClient(request);

        clientRepository.save(client);
        logger.info("Client {} registered successfully.", dn);

        return client.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.DETAIL)
    public ClientDto getClient(SecuredUUID uuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        return client.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.UPDATE)
    public ClientDto editClient(SecuredUUID uuid, EditClientRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        if (request.getClientCertificate() != null) {
            X509Certificate certificate = CertificateUtil.getX509Certificate(request.getClientCertificate());
            String serialNumber = CertificateUtil.getSerialNumberFromX509Certificate(certificate);
            // Updating to another certificate?
            if (!client.getCertificate().getSerialNumber().equals(serialNumber) &&
                    clientRepository.findBySerialNumber(serialNumber).isPresent()) {
                throw new AlreadyExistException(Client.class, serialNumber);
            }
        }

        updateClient(client, request);

        clientRepository.save(client);
        logger.info("Client {} updated successfully.", uuid);

        return client.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.DELETE)
    public void deleteClient(SecuredUUID uuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));
        if (client.getRaProfiles() != null && !client.getRaProfiles().isEmpty()) {
            throw new ValidationException(ValidationError.create("Unable to delete Client. It has " + client.getRaProfiles().size() + " authorized RA Profiles"));
        }
        clientRepository.delete(client);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.LIST)
    public List<SimplifiedRaProfileDto> listAuthorizations(SecuredUUID uuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));
        List<SimplifiedRaProfileDto> profiles = new ArrayList<>();
        // TODO AUTH - use RaProfileService
        for (RaProfile profile : client.getRaProfiles()) {
            SimplifiedRaProfileDto dto = new SimplifiedRaProfileDto();
            dto.setUuid(profile.getUuid());
            dto.setEnabled(profile.getEnabled());
            dto.setName(profile.getName());
            profiles.add(dto);
        }
        return profiles;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.AUTH)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.AUTHORIZE)
    public void authorizeClient(SecuredUUID uuid, String raProfileUuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        // TODO AUTH - use RaProfileService
        RaProfile raProfile = raProfileRepository.findByUuid(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        raProfile.getClients().add(client);
        raProfileRepository.save(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.AUTH)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.UNAUTHORIZE)
    public void unauthorizeClient(SecuredUUID uuid, String raProfileUuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        // TODO AUTH - use RaProfileService
        RaProfile raProfile = raProfileRepository.findByUuid(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        raProfile.getClients().remove(client);
        raProfileRepository.save(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.ENABLE)
    public void enableClient(SecuredUUID uuid) throws NotFoundException, CertificateException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        client.setEnabled(true);
        clientRepository.save(client);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.DISABLE)
    public void disableClient(SecuredUUID uuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        client.setEnabled(false);
        clientRepository.save(client);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> bulkDeleteClient(List<SecuredUUID> clientUuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : clientUuids) {
            try {
                Client client = clientRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Client.class, uuid));
                if (client.getRaProfiles() != null && !client.getRaProfiles().isEmpty()) {
                    BulkActionMessageDto forceModal = new BulkActionMessageDto();
                    forceModal.setUuid(client.getUuid());
                    forceModal.setName(client.getName());
                    forceModal.setMessage("Client has " + client.getRaProfiles().size() + " authorized RA Profile(s)");
                    messages.add(forceModal);
                    continue;
                }
                clientRepository.delete(client);
            } catch (Exception e) {
                logger.warn("Unable to delete the client with id {}", uuid);
            }
        }
        return messages;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.DISABLE)
    public void bulkDisableClient(List<SecuredUUID> clientUuids) {
        for (SecuredUUID uuid : clientUuids) {
            try {
                Client client = clientRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Client.class, uuid));

                client.setEnabled(false);
                clientRepository.save(client);
            } catch (NotFoundException e) {
                logger.warn("Unable to disable the client with id {}", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CLIENT, action = ResourceAction.ENABLE)
    public void bulkEnableClient(List<SecuredUUID> clientUuids) {
        for (SecuredUUID uuid : clientUuids) {
            try {
                Client client = clientRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Client.class, uuid));

                client.setEnabled(true);
                clientRepository.save(client);
            } catch (NotFoundException e) {
                logger.warn("Unable to disable the client with id {}", uuid);
            }
        }
    }

    private Client createClient(AddClientRequestDto requestDTO) throws CertificateException, NotFoundException, AlreadyExistException {
        Client client = new Client();
        Certificate certificate;
        if ((requestDTO.getClientCertificate() != null && !requestDTO.getClientCertificate().isEmpty()) || (requestDTO.getCertificateUuid() != null && !requestDTO.getCertificateUuid().isEmpty())) {
            if (!requestDTO.getCertificateUuid().isEmpty()) {
                certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(requestDTO.getCertificateUuid()));

            } else {
                UploadCertificateRequestDto request = new UploadCertificateRequestDto();
                request.setCertificate(requestDTO.getClientCertificate());
                certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateService.upload(request).getUuid()));
            }
            client.setCertificate(certificate);
            client.setSerialNumber(certificate.getSerialNumber());
        }
        client.setName(requestDTO.getName());
        client.setDescription(requestDTO.getDescription());
        client.setEnabled(requestDTO.getEnabled() != null && requestDTO.getEnabled());

        return client;
    }

    private Client updateClient(Client client, EditClientRequestDto requestDTO) throws CertificateException, NotFoundException, AlreadyExistException {
        client.setDescription(requestDTO.getDescription());
        Certificate certificate;
        if ((requestDTO.getClientCertificate() != null && !requestDTO.getClientCertificate().isEmpty()) || (requestDTO.getCertificateUuid() != null && !requestDTO.getCertificateUuid().isEmpty())) {
            if (!requestDTO.getCertificateUuid().isEmpty()) {
                certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(requestDTO.getCertificateUuid()));

            } else {
                UploadCertificateRequestDto request = new UploadCertificateRequestDto();
                request.setCertificate(requestDTO.getClientCertificate());
                certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateService.upload(request).getUuid()));
            }
            client.setCertificate(certificate);
            client.setSerialNumber(certificate.getSerialNumber());
        }
        if (requestDTO.getDescription() != null) {
            client.setDescription(requestDTO.getDescription());
        }
        return client;
    }

}
