package com.czertainly.core.service.impl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import com.czertainly.api.core.modal.*;
import com.czertainly.core.aop.AuditLogged;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Client;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ClientRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ClientService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.raprofile.RaProfileDto;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class ClientServiceImpl implements ClientService {

    private static final Logger logger = LoggerFactory.getLogger(ClientServiceImpl.class);

    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateService certificateService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.REQUEST)
    public List<ClientDto> listClients() {
        List<Client> clients = clientRepository.findAll();
        return clients.stream().map(Client::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.CREATE)
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
        	Certificate certificate = certificateService.getCertificateEntity(request.getCertificateUuid());
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
    public ClientDto getClient(String uuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        return client.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.CHANGE)
    public ClientDto editClient(String uuid, EditClientRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException {
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
    public void removeClient(String uuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));
        clientRepository.delete(client);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.REQUEST)
    public List<RaProfileDto> listAuthorizations(String uuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        return client.getRaProfiles().stream().map(RaProfile::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.AUTH)
    public void authorizeClient(String uuid, String raProfileUuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        RaProfile raProfile = raProfileRepository.findByUuid(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        raProfile.getClients().add(client);
        raProfileRepository.save(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.AUTH)
    public void unauthorizeClient(String uuid, String raProfileUuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        RaProfile raProfile = raProfileRepository.findByUuid(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        raProfile.getClients().remove(client);
        raProfileRepository.save(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.ENABLE)
    public void enableClient(String uuid) throws NotFoundException, CertificateException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        client.setEnabled(true);
        clientRepository.save(client);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.DISABLE)
    public void disableClient(String uuid) throws NotFoundException {
        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Client.class, uuid));

        client.setEnabled(false);
        clientRepository.save(client);
    }

    @Override
    public void bulkRemoveClient(List<String> clientUuids) {
        for(String uuid: clientUuids){
            try{
                Client client = clientRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Client.class, uuid));
                clientRepository.delete(client);
            }catch(Exception e){
                logger.warn("Unable to delete the client with id {}", uuid);
            }
        }
    }

    @Override
    public void bulkDisableClient(List<String> clientUuids) {
        for(String uuid: clientUuids){
            try{
                Client client = clientRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Client.class, uuid));

                client.setEnabled(false);
                clientRepository.save(client);
            }catch (NotFoundException e){
                logger.warn("Unable to disable the client with id {}", uuid);
            }
        }
    }

    @Override
    public void bulkEnableClient(List<String> clientUuids) {
        for(String uuid: clientUuids){
            try{
                Client client = clientRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Client.class, uuid));

                client.setEnabled(true);
                clientRepository.save(client);
            }catch (NotFoundException e){
                logger.warn("Unable to disable the client with id {}", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.REQUEST)
    public ClientDto getClientBySerialNumber(String serialNumber) throws NotFoundException {
        Client client = clientRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new NotFoundException(Client.class, serialNumber));

        return client.mapToDto();
    }

    private Client createClient(AddClientRequestDto requestDTO) throws CertificateException, NotFoundException, AlreadyExistException {
    	Client client = new Client();
    	Certificate certificate;
        if((requestDTO.getClientCertificate() != null && !requestDTO.getClientCertificate().isEmpty()) || (requestDTO.getCertificateUuid() != null && !requestDTO.getCertificateUuid().isEmpty())) {
            if (!requestDTO.getCertificateUuid().isEmpty()) {
                certificate = certificateService.getCertificateEntity(requestDTO.getCertificateUuid());
                client.setCertificate(certificate);

            } else {
                UploadCertificateRequestDto request = new UploadCertificateRequestDto();
                request.setCertificate(requestDTO.getClientCertificate());
                certificate = certificateService.getCertificateEntity(certificateService.upload(request).getUuid());
                client.setCertificate(certificate);
            }
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
        if(!requestDTO.getCertificateUuid().isEmpty()) {
        	certificate = certificateService.getCertificateEntity(requestDTO.getCertificateUuid());
        	client.setCertificate(certificate);
        	
        }else {
            UploadCertificateRequestDto request = new UploadCertificateRequestDto();
            request.setCertificate(requestDTO.getClientCertificate());
            certificate = certificateService.getCertificateEntity(certificateService.upload(request).getUuid());
            client.setCertificate(certificate);
        }
        client.setSerialNumber(certificate.getSerialNumber());
        return client;
    }

}
