package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.cbom.client.CbomRepositoryClient;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.Cbom;
import com.czertainly.core.dao.entity.Cbom_;
import com.czertainly.core.dao.repository.CbomRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.cbom.BomCreateResponseDto;
import com.czertainly.core.model.cbom.BomResponseDto;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CbomService;
import com.czertainly.core.util.SearchHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service(Resource.Codes.CBOM)
@Transactional
public class CbomServiceImpl implements CbomService {
    private static final Logger logger = LoggerFactory.getLogger(CbomServiceImpl.class);

    private CbomRepository cbomRepository;

    private CbomRepositoryClient cbomRepositoryClient;

    private AttributeEngine attributeEngine;

    @Autowired
    public void setCbomRepository(CbomRepository cbomRepository) {
        this.cbomRepository = cbomRepository;
    }

    @Autowired
    public void setCbomRepositoryClient(CbomRepositoryClient cbomRepositoryClient) {
        this.cbomRepositoryClient = cbomRepositoryClient;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
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
    public CbomDetailDto getCbomDetail(SecuredUUID uuid) throws CbomRepositoryException, NotFoundException{
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

        ObjectMapper mapper = new ObjectMapper();
        CbomDetailDto detailDto = mapper.convertValue(cbom.mapToDto(), CbomDetailDto.class);
        Map<String, Object> map = mapper.convertValue(response.getBom(), new TypeReference<Map<String, Object>>() {});
        detailDto.setContent(map);
        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.LIST)
    public List<CbomDto> getCbomVersions(String serialNumber) throws NotFoundException {

        List<Cbom> versions = cbomRepository.findBySerialNumber(serialNumber);

        List<CbomDto> ret = versions.stream()
        .map(cbom -> {return cbom.mapToDto();})
        .collect(Collectors.toList());
        return ret;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.CREATE)
    public CbomDto createCbom(CbomUploadRequestDto request) throws AlreadyExistException, CbomRepositoryException, ValidationException {
        Map<String, Object> content = request.getContent();
        if (content == null) {
            throw new ValidationException(
                    ValidationError.create("Request must not be empty")
            );
        }

        // serial_number (required)
        Object serialNumber = content.get("serial_number");
        if (serialNumber == null || StringUtils.isBlank(serialNumber.toString())) {
            throw new ValidationException(
                    ValidationError.create("serial_number must not be empty")
            );
        }

        // version (required)
        Object version = content.get("version");
        if (version == null || StringUtils.isBlank(version.toString())) {
            throw new ValidationException(
                    ValidationError.create("version must not be empty")
            );
        }

        if (!(version instanceof Number)) {
            try {
                Integer.parseInt(version.toString());
            } catch (NumberFormatException e) {
                throw new ValidationException("version must be integer");
            }
        }

        // upload JSON to cbom-respository

        BomCreateResponseDto response;
        try {
            response = cbomRepositoryClient.create(request);
        } catch (CbomRepositoryException ex) {
            if (ex.getProblemDetail() != null && ex.getProblemDetail().getStatus() == 409) {
                throw new AlreadyExistException(CbomDetailDto.class, "CBOM with given serial number and version already exists");
            } else {
                throw ex;
            }
        }

        // upload stats to database
        Cbom cbom = new Cbom();
        cbom.setSerialNumber(response.getSerialNumber());
        cbom.setVersion(response.getVersion());
        cbom.setCreatedAt(Instant.now());
        cbom.setSource("CORE");

        cbom.setAlgorithmsCount(
            response.getCryptoStats().getCryptoAssets().getAlgorithms().getTotal()
        );
        cbom.setCertificatesCount(
            response.getCryptoStats().getCryptoAssets().getCertificates().getTotal()
        );
        cbom.setProtocolsCount(
            response.getCryptoStats().getCryptoAssets().getProtocols().getTotal()
        );
        cbom.setCryptoMaterialCount(
            response.getCryptoStats().getCryptoAssets().getRelatedCryptoMaterials().getTotal()
        );
        cbom.setTotalAssetsCount(
            response.getCryptoStats().getCryptoAssets().getTotal()
        );

        cbomRepository.save(cbom);
        return  cbom.mapToDto();
    }

    // ResourceExtenstionService
    @Override
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        return cbomRepository.findResourceObject(objectUuid, Cbom_.serialNumber);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination) {
        return cbomRepository.listResourceObjects(filter, Cbom_.serialNumber);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getEntity(uuid);
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup() {
        final List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine.getResourceSearchableFields(Resource.CBOM, false);

        List<SearchFieldDataDto> fields = List.of(
                SearchHelper.prepareSearch(FilterField.CBOM_SERIAL_NUMBER),
                SearchHelper.prepareSearch(FilterField.CBOM_VERSION),
                SearchHelper.prepareSearch(FilterField.CBOM_CREATED_AT),
                SearchHelper.prepareSearch(FilterField.CBOM_SOURCE),
                SearchHelper.prepareSearch(FilterField.CBOM_ALGORITHMS_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_CERTIFICATES_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_CERTIFICATES_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_PROTOCOLS_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_CRYPTO_MATERIAL_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_TOTAL_ASSETS_COUNT)
        );

        fields = new ArrayList<>(fields);
        fields.sort(new SearchFieldDataComparator());

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));

        logger.debug("Searchable Fields by Groups: {}", searchFieldDataByGroupDtos);
        return searchFieldDataByGroupDtos;
    }

    private Cbom getEntity(SecuredUUID uuid) throws NotFoundException {
        return cbomRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Cbom.class, uuid));
    }

}
