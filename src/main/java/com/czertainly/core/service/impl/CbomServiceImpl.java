package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomListResponseDto;
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
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.RequestValidatorHelper;
import com.czertainly.core.util.SearchHelper;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public CbomListResponseDto listCboms(SecurityFilter filter, SearchRequestDto request) {

        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<Cbom>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<CbomDto> listedKeyDTOs = cbomRepository.findUsingSecurityFilter(filter, List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get("createdAt")))
                .stream()
                .map(Cbom::mapToDto).toList();
        final Long maxItems = cbomRepository.countUsingSecurityFilter(filter, additionalWhereClause);

        final CbomListResponseDto responseDto = new CbomListResponseDto();
        responseDto.setItems(listedKeyDTOs);
        responseDto.setItemsPerPage(request.getItemsPerPage());
        responseDto.setPageNumber(request.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / request.getItemsPerPage()));
        return responseDto;
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
                throw new NotFoundException(CbomDetailDto.class, "Cbom");
            } else {
                throw ex;
            }
        }

        CbomDetailDto detailDto = response.mapToCbomDetailDto();
        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.LIST_VERSIONS)
    public List<CbomDto> getCbomVersions(String serialNumber) throws NotFoundException {

        List<Cbom> versions = cbomRepository.findBySerialNumber(serialNumber);

        List<CbomDto> ret = versions.stream()
        .map(cbom -> cbom.mapToDto())
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

        // serialNumber (required)
        Object serialNumber = content.get("serialNumber");
        if (serialNumber == null || StringUtils.isBlank(serialNumber.toString())) {
            throw new ValidationException(
                    ValidationError.create("serialNumber must not be empty")
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

        // upload JSON to cbom-repository

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

        // Defensive handling of potentially null nested response objects
        Integer algorithmsCount = 0;
        Integer certificatesCount = 0;
        Integer protocolsCount = 0;
        Integer cryptoMaterialCount = 0;
        Integer totalAssetsCount = 0;

        if (response != null && response.getCryptoStats() != null && response.getCryptoStats().getCryptoAssets() != null) {
            if (response.getCryptoStats().getCryptoAssets().getAlgorithms() != null
                    && response.getCryptoStats().getCryptoAssets().getAlgorithms().getTotal() != null) {
                algorithmsCount = response.getCryptoStats().getCryptoAssets().getAlgorithms().getTotal();
            }
            if (response.getCryptoStats().getCryptoAssets().getCertificates() != null
                    && response.getCryptoStats().getCryptoAssets().getCertificates().getTotal() != null) {
                certificatesCount = response.getCryptoStats().getCryptoAssets().getCertificates().getTotal();
            }
            if (response.getCryptoStats().getCryptoAssets().getProtocols() != null
                    && response.getCryptoStats().getCryptoAssets().getProtocols().getTotal() != null) {
                protocolsCount = response.getCryptoStats().getCryptoAssets().getProtocols().getTotal();
            }
            if (response.getCryptoStats().getCryptoAssets().getRelatedCryptoMaterials() != null
                    && response.getCryptoStats().getCryptoAssets().getRelatedCryptoMaterials().getTotal() != null) {
                cryptoMaterialCount = response.getCryptoStats().getCryptoAssets().getRelatedCryptoMaterials().getTotal();
            }
            if (response.getCryptoStats().getCryptoAssets().getTotal() != null) {
                totalAssetsCount = response.getCryptoStats().getCryptoAssets().getTotal();
            }
        }

        cbom.setAlgorithmsCount(algorithmsCount);
        cbom.setCertificatesCount(certificatesCount);
        cbom.setProtocolsCount(protocolsCount);
        cbom.setCryptoMaterialCount(cryptoMaterialCount);
        cbom.setTotalAssetsCount(totalAssetsCount);
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
