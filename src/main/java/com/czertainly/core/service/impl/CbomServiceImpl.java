package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.cbom.client.CbomRepositoryClient;
import com.czertainly.core.cbom.client.CbomRepositoryException;
import com.czertainly.core.dao.entity.Cbom;
import com.czertainly.core.dao.repository.CbomRepository;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CbomService;
import jakarta.transaction.Transactional;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.cbom.BomResponseDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.czertainly.core.aop.AuditLogged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service(Resource.Codes.GROUP)
@Transactional
public class CbomServiceImpl implements CbomService {
    private static final Logger logger = LoggerFactory.getLogger(CbomServiceImpl.class);

    private CbomRepository cbomRepository;

    private CbomRepositoryClient cbomRepositoryClient;

    @Autowired
    public void setCbomRepository(CbomRepository cbomRepository) {
        this.cbomRepository = cbomRepository;
    }

    @Autowired
    public void setCbomRepositoryClient(CbomRepositoryClient cbomRepositoryClient) {
        this.cbomRepositoryClient = cbomRepositoryClient;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.LIST)
    public List<CbomDto> listCboms(SecurityFilter filter) {
        return cbomRepository.findUsingSecurityFilter(filter)
        .stream()
        .map(Cbom::mapToDto)
        .collect(Collectors.toList());
    }


    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.DETAIL)
    public CbomDto getCbom(SecuredUUID uuid) throws NotFoundException {
        CbomDto dto = getEntity(uuid).mapToDto();
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.DETAIL)
    public CbomDetailDto getCbomDetail(SecuredUUID uuid) throws NotFoundException, CbomRepositoryException {
        Cbom cbom = getEntity(uuid);

        BomResponseDto response;
        try {
            response = cbomRepositoryClient.read(
                cbom.getSerialNumber(),
                cbom.getVersion());
        } catch (CbomRepositoryException ex) {
            if (ex.getProblemDetail() != null && ex.getProblemDetail().getStatus() == 404) {
                throw new NotFoundException(CbomDetailDto.class, "Can't find a cbom");
            } else {
                throw ex;
            }
        }


        CbomDetailDto cbomDetail = new CbomDetailDto();
        return cbomDetail;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.CREATE)
    public CbomDto createCbom(CbomUploadRequestDto request) throws ValidationException, AlreadyExistException {
        Map<String, Object> content = request.getContent();
        if (content == null) {
            throw new ValidationException(
                    ValidationError.create("Request must not be empty")
            );
        }
        Cbom cbom = new Cbom();

        // UUID - generate new one
        cbom.setUuid(UUID.randomUUID());

        // serial_number (required)
        Object serialNumber = content.get("serial_number");
        if (serialNumber == null || StringUtils.isBlank(serialNumber.toString())) {
            throw new ValidationException(
                    ValidationError.create("serial_number must not be empty")
            );
        }
        cbom.setSerialNumber(serialNumber.toString());

        // version (required)
        Object version = content.get("version");
        if (version == null || StringUtils.isBlank(version.toString())) {
            throw new ValidationException(
                    ValidationError.create("version must not be empty")
            );
        }
        cbom.setVersion(version.toString());

        // createdAt - set to current time
        cbom.setCreatedAt(Instant.now());

        // source (optional)
        Object source = content.get("source");
        if (source != null && !StringUtils.isBlank(source.toString())) {
            cbom.setSource(source.toString());
        }

        // algorithms_count (required, default to 0)
        Object algorithmsCount = content.get("algorithms_count");
        cbom.setAlgorithmsCount(algorithmsCount != null ? ((Number) algorithmsCount).intValue() : 0);

        // certificates_count (required, default to 0)
        Object certificatesCount = content.get("certificates_count");
        cbom.setCertificatesCount(certificatesCount != null ? ((Number) certificatesCount).intValue() : 0);

        // protocols_count (required, default to 0)
        Object protocolsCount = content.get("protocols_count");
        cbom.setProtocolsCount(protocolsCount != null ? ((Number) protocolsCount).intValue() : 0);

        // crypto_material_count (required, default to 0)
        Object cryptoMaterialCount = content.get("crypto_material_count");
        cbom.setCryptoMaterialCount(cryptoMaterialCount != null ? ((Number) cryptoMaterialCount).intValue() : 0);

        // total_assets_count (required, default to 0)
        Object totalAssetsCount = content.get("total_assets_count");
        cbom.setTotalAssetsCount(totalAssetsCount != null ? ((Number) totalAssetsCount).intValue() : 0);

        CbomDto dto = cbom.mapToDto();
        return dto;
    }


    private Cbom getEntity(SecuredUUID uuid) throws NotFoundException {
        return cbomRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Cbom.class, uuid));
    }

}
