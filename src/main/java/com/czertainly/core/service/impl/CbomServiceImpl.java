package com.czertainly.core.service.impl;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.logging.records.ResourceObjectIdentity;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.cbom.client.CbomRepositoryClient;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.Cbom;
import com.czertainly.core.dao.entity.Cbom_;
import com.czertainly.core.dao.entity.ScheduledJobHistory;
import com.czertainly.core.dao.repository.CbomRepository;
import com.czertainly.core.dao.repository.ScheduledJobHistoryRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.logging.LoggerWrapper;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.cbom.BomCreateResponseDto;
import com.czertainly.core.model.cbom.BomEntryDto;
import com.czertainly.core.model.cbom.BomResponseDto;
import com.czertainly.core.model.cbom.BomSearchRequestDto;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CbomService;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.RequestValidatorHelper;
import com.czertainly.core.util.SearchHelper;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;

@Service(Resource.Codes.CBOM)
@Transactional
public class CbomServiceImpl implements CbomService {

    private static final String CBOM_SYNC_JOB_NAME = "CbomSyncTask";

    private static final LoggerWrapper logger = new LoggerWrapper(CbomServiceImpl.class, Module.CORE, Resource.CBOM);

    private CbomRepository cbomRepository;

    private CbomRepositoryClient cbomRepositoryClient;

    private AttributeEngine attributeEngine;

    private ScheduledJobHistoryRepository scheduledJobHistoryRepository;

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

    @Autowired
    public void setScheduledJobHistoryRepository(ScheduledJobHistoryRepository scheduledJobHistoryRepository) {
        this.scheduledJobHistoryRepository = scheduledJobHistoryRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.LIST)
    public PaginationResponseDto<CbomDto> listCboms(SecurityFilter filter, SearchRequestDto request) {

        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<Cbom>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<CbomDto> cbomDtos = cbomRepository.findUsingSecurityFilter(filter, List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get("createdAt")))
                .stream()
                .map(Cbom::mapToDto).toList();
        final Long maxItems = cbomRepository.countUsingSecurityFilter(filter, additionalWhereClause);

        logger.getLogger().debug("Found {} CBOMs out of {} total", cbomDtos.size(), maxItems);

        final PaginationResponseDto<CbomDto> responseDto = new PaginationResponseDto<>();
        responseDto.setItems(cbomDtos);
        responseDto.setItemsPerPage(request.getItemsPerPage());
        responseDto.setPageNumber(request.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / request.getItemsPerPage()));
        return responseDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.DETAIL)
    public CbomDetailDto getCbomDetail(SecuredUUID uuid) throws CbomRepositoryException, NotFoundException {
        Cbom cbom = getEntity(uuid);

        BomResponseDto response = read(
            cbom.getSerialNumber(),
            cbom.getVersion()
        );

        CbomDto cbomDto = cbom.mapToDto();
        CbomDetailDto detailDto = new CbomDetailDto();
        detailDto.setContent(response);
        // cbom dto
        detailDto.setUuid(cbomDto.getUuid());
        detailDto.setCreatedAt(cbomDto.getCreatedAt());
        detailDto.setSerialNumber(cbomDto.getSerialNumber());
        detailDto.setVersion(cbomDto.getVersion());
        detailDto.setSpecVersion(cbomDto.getSpecVersion());
        detailDto.setTimestamp(cbomDto.getTimestamp());
        detailDto.setSource(cbomDto.getSource());
        detailDto.setAlgorithms(cbomDto.getAlgorithms());
        detailDto.setCertificates(cbomDto.getCertificates());
        detailDto.setProtocols(cbomDto.getProtocols());
        detailDto.setCryptoMaterial(cbomDto.getCryptoMaterial());
        detailDto.setTotalAssets(cbomDto.getTotalAssets());

        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CBOM, action = ResourceAction.LIST)
    public List<CbomDto> getCbomVersions(SecuredUUID uuid) throws NotFoundException {
        List<Cbom> cboms = cbomRepository.findVersionsByUuid(uuid.getValue());

        return cboms
                .stream()
                .map(Cbom::mapToDto)
                .toList();
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
        String serialNumber = Optional.ofNullable(content.get("serialNumber")).map(Object::toString).orElse(null);
        if (StringUtils.isBlank(serialNumber)) {
            throw new ValidationException(
                    ValidationError.create("serialNumber must not be empty")
            );
        }

        // version (required)
        String version = Optional.ofNullable(content.get("version")).map(Object::toString).orElse(null);
        if (StringUtils.isBlank(version)) {
            throw new ValidationException(
                    ValidationError.create("version must not be empty")
            );
        }

        try {
            Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new ValidationException("version must be integer");
        }

        // specVersion (required)
        String specVersion = Optional.ofNullable(content.get("specVersion")).map(Object::toString).orElse(null);
        if (StringUtils.isBlank(specVersion)) {
            throw new ValidationException(
                    ValidationError.create("specVersion must not be empty")
            );
        }

        // metadata (required)
        Object metadataObj = content.get("metadata");
        if (metadataObj == null) {
            throw new ValidationException("metadata must be present");
        }
        if (!(metadataObj instanceof Map)) {
            throw new ValidationException("metadata must be JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) metadataObj;

        // metadata.timestamp (required)
        OffsetDateTime timestamp = null;
        try {
            String timestampStr = (String) metadata.get("timestamp");
            timestamp = OffsetDateTime.parse(timestampStr);
        } catch (ClassCastException e) {
            throw new ValidationException("timestamp must be String");
        } catch (NullPointerException npe) {
            throw new ValidationException("metadata.timestamp must be present");
        } catch (DateTimeParseException e) {
            throw new ValidationException("metadata.timestamp must be valid ISO-8601 timestamp");
        }

        // upload JSON to cbom-repository
        BomCreateResponseDto response;
        try {
            response = cbomRepositoryClient.create(request);
            logger.logEventDebug(Operation.CREATE, OperationResult.SUCCESS, response, List.of(new ResourceObjectIdentity(response.getSerialNumber(), null)), "CBOM document created in repository with serialNumber %s and version %s".formatted(response.getSerialNumber(), response.getVersion()));
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
        cbom.setSpecVersion(specVersion);
        cbom.setTimestamp(timestamp);
        String source = Optional.ofNullable(metadata.get("component"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(m -> m.get("name"))
                .map(String::valueOf)
                .orElse("");
        cbom.setSource(source);

        setCryptoStats(cbom, response);

        cbomRepository.save(cbom);
        LoggingHelper.putLogResourceInfo(Resource.CBOM, false, cbom.getUuid().toString(), cbom.getSerialNumber());
        logger.logEvent(Operation.CREATE, OperationResult.SUCCESS, null, List.of(new ResourceObjectIdentity(cbom.getSerialNumber(), cbom.getUuid())), "CBOM record created with serialNumber %s and version %s".formatted(cbom.getSerialNumber(), cbom.getVersion()));
        return cbom.mapToDto();
    }

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
                SearchHelper.prepareSearch(FilterField.CBOM_TIMESTAMP),
                SearchHelper.prepareSearch(FilterField.CBOM_SOURCE),
                SearchHelper.prepareSearch(FilterField.CBOM_ALGORITHMS_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_CERTIFICATES_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_PROTOCOLS_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_CRYPTO_MATERIAL_COUNT),
                SearchHelper.prepareSearch(FilterField.CBOM_TOTAL_ASSETS_COUNT)
        );

        fields = new ArrayList<>(fields);
        fields.sort(new SearchFieldDataComparator());

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));

        logger.getLogger().debug("Searchable Fields by Groups: {}", searchFieldDataByGroupDtos);
        return searchFieldDataByGroupDtos;
    }

    private Cbom getEntity(SecuredUUID uuid) throws NotFoundException {
        return cbomRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Cbom.class, uuid));
    }

    private void setCryptoStats(Cbom cbom, BomCreateResponseDto response) {
        // Defensive handling of potentially null nested response objects
        int algorithmsCount = 0;
        int certificatesCount = 0;
        int protocolsCount = 0;
        int cryptoMaterialCount = 0;
        int totalAssetsCount = 0;
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
        } else {
            logger.getLogger().debug("CBOM document retrieved from repository for serialNumber {} and version {} does not contain crypto stats: {}", cbom.getSerialNumber(), cbom.getVersion(), response);
        }

        cbom.setAlgorithmsCount(algorithmsCount);
        cbom.setCertificatesCount(certificatesCount);
        cbom.setProtocolsCount(protocolsCount);
        cbom.setCryptoMaterialCount(cryptoMaterialCount);
        cbom.setTotalAssetsCount(totalAssetsCount);
    }

    public void sync() throws CbomRepositoryException {
        Optional<ScheduledJobHistory> lastSync;

        try {
            lastSync = scheduledJobHistoryRepository.findLastStartedOrSucceededByJobName(CBOM_SYNC_JOB_NAME);
        } catch (Exception e) {
            logger.getLogger().error("CBOM sync failed: unable to read last sync status: {}", e.getMessage());
            return;
        }

        Long timestamp = Long.valueOf(0);
        if (lastSync.isEmpty()) {
            logger.getLogger().debug("CBOM sync: there is no last scheduled run: using timestamp: 0");
        }

        // prevents sync tasks run in a parallel
        try {
            timestamp = (lastSync.get().getJobEndTime().getTime() / 1000);
        } catch (NullPointerException e) {
            logger.getLogger().debug("CBOM sync: there is sync job in progress run. Skipping sync.");
            return;
        }

        BomSearchRequestDto query = new BomSearchRequestDto();
        query.setAfter(timestamp);
        List<BomEntryDto> cboms = cbomRepositoryClient.search(query);
        logger.getLogger().debug("CBOM sync: {} CBOM entries retrieved from repository for after: {}", cboms.size(), query.getAfter());

        int skipped = 0;
        int duplicates = 0;
        int stored = 0;
        for (BomEntryDto entry: cboms) {
            int version;
            try {
                version = Integer.parseInt(entry.getVersion());
            } catch (NumberFormatException e) {
                logger.getLogger().warn("CBOM Sync: CBOM document serialNumber {} and version {}: has invalid version. Skipping the sync", entry.getSerialNumber(), entry.getVersion());
                skipped ++;
                continue;
            }

            try {
                createCbomEntry(entry, version);
            } catch(AlreadyExistException e) {
                logger.getLogger().debug("CBOM Sync: CBOM serialNumber {} and version {}: already exists. Skipping the sync", entry.getSerialNumber(), version);
                duplicates ++;
                continue;
            }
            stored ++;
        }
        logger.getLogger().info("CBOM Sync: finished, read {} entries, skipped due an error {}, skipped duplicates {}, stored {} new entries",
            cboms.size(),
            skipped,
            duplicates,
            stored
        );
    }

    private BomResponseDto read(String serialNumber, int version) throws CbomRepositoryException, NotFoundException {
        BomResponseDto response;
        try {
            response = cbomRepositoryClient.read(
                    serialNumber,
                    version);
            logger.getLogger().debug("CBOM document retrieved from repository for serialNumber {} and version {}: {}", serialNumber, version, response);
        } catch (CbomRepositoryException ex) {
            if (ex.getProblemDetail() != null && ex.getProblemDetail().getStatus() == 404) {
                throw new NotFoundException(CbomDetailDto.class, "Cbom");
            } else {
                throw ex;
            }
        }
        return response;
    }

    private void createCbomEntry(BomEntryDto entry, int version) throws AlreadyExistException {
        Cbom cbom = new Cbom();
        cbom.setSerialNumber(entry.getSerialNumber());
        cbom.setVersion(version);
        // FIXME: this is not returned by cbom-repository
        // cbom.setSpecVersion(entry.getSpecVersion());
        cbom.setTimestamp(entry.getTimestamp());
        // FIXME: this is not returned by cbom-repository
        // cbom.setSource(entry.getSource());
        cbom.setAlgorithmsCount(entry.getCryptoStats().getCryptoAssets().getAlgorithms().getTotal());
        cbom.setCertificatesCount(entry.getCryptoStats().getCryptoAssets().getCertificates().getTotal());
        cbom.setProtocolsCount(entry.getCryptoStats().getCryptoAssets().getProtocols().getTotal());
        cbom.setCryptoMaterialCount(entry.getCryptoStats().getCryptoAssets().getRelatedCryptoMaterials().getTotal());
        cbom.setTotalAssetsCount(entry.getCryptoStats().getCryptoAssets().getTotal());

        try {
            cbomRepository.save(cbom);
        } catch (ConstraintViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("cbom_serial_version_unique")) {
                throw new AlreadyExistException(Cbom.class, 
                    "serialNumber: " + cbom.getSerialNumber() + ", version: " + cbom.getVersion());
            }
            throw e;
        }
    }
}
