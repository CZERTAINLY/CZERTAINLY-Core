package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.AcmeProfileService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AcmeSerializationUtil;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_SUPERADMINISTRATOR", "ROLE_ADMINISTARTOR"})
public class AcmeProfileServiceImpl implements AcmeProfileService {


    private static final Logger logger = LoggerFactory.getLogger(AcmeProfileServiceImpl.class);

    @Autowired
    private AcmeProfileRepository acmeProfileRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private ClientOperationService clientOperationServiceV2;

    @Override
    public List<AcmeProfileListDto> listAcmeProfile() {
        logger.debug("Getting all the ACME Profiles available in the database");
        return acmeProfileRepository.findAll().stream().map(AcmeProfile::mapToDtoSimple).collect(Collectors.toList());
    }

    @Override
    public AcmeProfileDto getAcmeProfile(String uuid) throws NotFoundException {
        logger.info("Requesting the details for the ACME Profile with uuid " + uuid);
        return getAcmeProfileEntity(uuid).mapToDto();
    }

    @Override
    public AcmeProfileDto createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        if(request.getName() == null || request.getName().isEmpty()){
            throw new ValidationException("Name cannot be empty");
        }
        logger.info("Creating a new ACME Profile");

        if (acmeProfileRepository.existsByName(request.getName())) {
            throw new AlreadyExistException("ACME Profile with same name already exists");
        }

        AcmeProfile acmeProfile = new AcmeProfile();
        acmeProfile.setEnabled(true);
        acmeProfile.setName(request.getName());
        acmeProfile.setDescription(request.getDescription());
        acmeProfile.setDnsResolverIp(request.getDnsResolverIp());
        acmeProfile.setDnsResolverPort(request.getDnsResolverPort());
        acmeProfile.setRetryInterval(request.getRetryInterval());
        acmeProfile.setValidity(request.getValidity());
        acmeProfile.setWebsite(request.getWebsiteUrl());
        acmeProfile.setTermsOfServiceUrl(request.getTermsOfServiceUrl());
        acmeProfile.setInsistContact(request.getInsistContact());
        acmeProfile.setInsistTermsOfService(request.getInsistTermsOfService());
        RaProfile raProfile = getRaProfileEntity(request.getRaProfileUuid());
        if(request.getRaProfileUuid() != null && !request.getRaProfileUuid().isEmpty()){
            acmeProfile.setRaProfile(raProfile);
        }
        acmeProfile.setIssueCertificateAttributes(AttributeDefinitionUtils.serialize(clientOperationServiceV2.mergeAndValidateIssueAttributes(raProfile, request.getIssueCertificateAttributes())));
        acmeProfile.setRevokeCertificateAttributes(AttributeDefinitionUtils.serialize(clientOperationServiceV2.mergeAndValidateRevokeAttributes(raProfile, request.getRevokeCertificateAttributes())));
        acmeProfileRepository.save(acmeProfile);
        return acmeProfile.mapToDto();
    }

    @Override
    public AcmeProfileDto updateAcmeProfile(AcmeProfileDto request) throws NotFoundException {
        return null;
    }

    @Override
    public void deleteAcmeProfile(String uuid) throws NotFoundException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        acmeProfileRepository.delete(acmeProfile);

    }

    @Override
    public void enableAcmeProfile(String uuid) throws NotFoundException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        if (acmeProfile.getEnabled()) {
            throw new RuntimeException("ACME Profile is already enabled");
        }
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
    }

    @Override
    public void disableAcmeProfile(String uuid) throws NotFoundException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        if (!acmeProfile.getEnabled()) {
            throw new RuntimeException("ACME Profile is already disabled");
        }
        acmeProfile.setEnabled(false);
        acmeProfileRepository.save(acmeProfile);
    }

    @Override
    public void bulkEnableAcmeProfile(List<String> uuids) {
        for (String uuid : uuids) {
            try {
                AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
                if (acmeProfile.getEnabled()) {
                    logger.warn("ACME Profile is already enabled");
                }
                acmeProfile.setEnabled(true);
                acmeProfileRepository.save(acmeProfile);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    public void bulkDisableAcmeProfile(List<String> uuids) {
        for (String uuid : uuids) {
            try {
                AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
                if (acmeProfile.getEnabled()) {
                    logger.warn("ACME Profile is already disabled");
                }
                acmeProfile.setEnabled(false);
                acmeProfileRepository.save(acmeProfile);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    public void bulkDeleteAcmeProfile(List<String> uuids) {
        for (String uuid : uuids) {
            try {
                AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
                acmeProfileRepository.delete(acmeProfile);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    public void updateRaProfile(String uuid, String raProfileUuid) throws NotFoundException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        acmeProfile.setRaProfile(getRaProfileEntity(raProfileUuid));
        acmeProfileRepository.save(acmeProfile);
    }

    private RaProfile getRaProfileEntity(String uuid) throws NotFoundException {
        return raProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));
    }

    private AcmeProfile getAcmeProfileEntity(String uuid) throws NotFoundException {
        return acmeProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(AcmeProfile.class, uuid));
    }
}
