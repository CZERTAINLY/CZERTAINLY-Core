package com.czertainly.core.service.impl;

import com.czertainly.api.clients.EntityInstanceApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.EntityInstanceResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.entity.EntityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.entity.EntityInstanceRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.entity.EntityInstanceDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.search.SearchGroup;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.EntityInstanceReference;
import com.czertainly.core.dao.entity.Location;
import com.czertainly.core.dao.repository.AttributeContent2ObjectRepository;
import com.czertainly.core.dao.repository.AttributeContentRepository;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.LocationRepository;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.model.SearchFieldObject;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.EntityInstanceService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.RequestValidatorHelper;
import com.czertainly.core.util.SearchHelper;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
@Transactional
public class EntityInstanceServiceImpl implements EntityInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(EntityInstanceServiceImpl.class);
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    private ConnectorService connectorService;
    private CredentialService credentialService;
    private EntityInstanceApiClient entityInstanceApiClient;
    private AttributeService attributeService;
    private AttributeContentRepository attributeContentRepository;
    private AttributeContent2ObjectRepository attributeContent2ObjectRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public void setAttributeContent2ObjectRepository(AttributeContent2ObjectRepository attributeContent2ObjectRepository) {
        this.attributeContent2ObjectRepository = attributeContent2ObjectRepository;
    }

    @Autowired
    public void setAttributeContentRepository(AttributeContentRepository attributeContentRepository) {
        this.attributeContentRepository = attributeContentRepository;
    }

    @Autowired
    public void setEntityInstanceReferenceRepository(EntityInstanceReferenceRepository entityInstanceReferenceRepository) {
        this.entityInstanceReferenceRepository = entityInstanceReferenceRepository;
    }

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setCredentialService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Autowired
    public void setEntityInstanceApiClient(EntityInstanceApiClient entityInstanceApiClient) {
        this.entityInstanceApiClient = entityInstanceApiClient;
    }

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.ENTITY, action = ResourceAction.LIST)
    public EntityInstanceResponseDto listEntityInstances(final SecurityFilter filter, final SearchRequestDto request) {

        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final List<UUID> objectUUIDs = new ArrayList<>();
        if (!request.getFilters().isEmpty()) {
            final List<SearchFieldObject> searchFieldObjects = new ArrayList<>();
            searchFieldObjects.addAll(getSearchFieldObjectForMetadata());
            searchFieldObjects.addAll(getSearchFieldObjectForCustomAttributes());

            final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(searchFieldObjects, request.getFilters(), entityManager.getCriteriaBuilder(), Resource.ENTITY);
            objectUUIDs.addAll(attributeContent2ObjectRepository.findUsingSecurityFilterByCustomCriteriaQuery(filter, criteriaQueryDataObject.getRoot(), criteriaQueryDataObject.getCriteriaQuery(), criteriaQueryDataObject.getPredicate()));
        }

        final BiFunction<Root<EntityInstanceReference>, CriteriaBuilder, Predicate> additionalWhereClause = (root, cb) -> Sql2PredicateConverter.mapSearchFilter2Predicates(request.getFilters(), cb, root, objectUUIDs);
        final List<EntityInstanceDto> listedKeyDTOs = entityInstanceReferenceRepository.findUsingSecurityFilter(filter, additionalWhereClause, p, (root, cb) -> cb.desc(root.get("created")))
                .stream()
                .map(EntityInstanceReference::mapToDto)
                .collect(Collectors.toList());
        final Long maxItems = entityInstanceReferenceRepository.countUsingSecurityFilter(filter, additionalWhereClause);

        final EntityInstanceResponseDto responseDto = new EntityInstanceResponseDto();
        responseDto.setEntities(listedKeyDTOs);
        responseDto.setItemsPerPage(request.getItemsPerPage());
        responseDto.setPageNumber(request.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / request.getItemsPerPage()));
        return responseDto;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.ENTITY, action = ResourceAction.DETAIL)
    public EntityInstanceDto getEntityInstance(SecuredUUID entityUuid) throws ConnectorException {
        EntityInstanceReference entityInstanceReference = getEntityInstanceReferenceEntity(entityUuid);

        if (entityInstanceReference.getConnector() == null) {
            throw new NotFoundException("Connector associated with the Entity is not found. Unable to load details");
        }

        com.czertainly.api.model.connector.entity.EntityInstanceDto entityProviderInstanceDto = entityInstanceApiClient.getEntityInstance(entityInstanceReference.getConnector().mapToDto(),
                entityInstanceReference.getEntityInstanceUuid());

        EntityInstanceDto entityInstanceDto = new EntityInstanceDto();
        entityInstanceDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(entityProviderInstanceDto.getAttributes()));
        entityInstanceDto.setName(entityProviderInstanceDto.getName());
        entityInstanceDto.setUuid(entityInstanceReference.getUuid().toString());
        entityInstanceDto.setConnectorUuid(entityInstanceReference.getConnector().getUuid().toString());
        entityInstanceDto.setKind(entityInstanceReference.getKind());
        entityInstanceDto.setConnectorName(entityInstanceReference.getConnectorName());
        entityInstanceDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(entityUuid.getValue(), Resource.ENTITY));
        return entityInstanceDto;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.ENTITY, action = ResourceAction.CREATE)
    public EntityInstanceDto createEntityInstance(com.czertainly.api.model.client.entity.EntityInstanceRequestDto request) throws AlreadyExistException, ConnectorException {
        if (entityInstanceReferenceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(EntityInstanceReference.class, request.getName());
        }

        if (request.getConnectorUuid() == null) {
            throw new ValidationException(ValidationError.create("Connector UUID is empty"));
        }

        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(request.getConnectorUuid()));

        FunctionGroupCode codeToSearch = FunctionGroupCode.ENTITY_PROVIDER;
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.ENTITY);
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(SecuredUUID.fromUUID(connector.getUuid()), codeToSearch,
                request.getAttributes(), request.getKind());

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        EntityInstanceRequestDto entityInstanceDto = new EntityInstanceRequestDto();
        entityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        entityInstanceDto.setKind(request.getKind());
        entityInstanceDto.setName(request.getName());

        com.czertainly.api.model.connector.entity.EntityInstanceDto response = entityInstanceApiClient.createEntityInstance(connector.mapToDto(), entityInstanceDto);

        EntityInstanceReference entityInstanceRef = new EntityInstanceReference();
        entityInstanceRef.setEntityInstanceUuid((response.getUuid()));
        entityInstanceRef.setName(request.getName());
        //entityInstanceRef.setStatus("connected"); // TODO: status of the Entity
        entityInstanceRef.setConnector(connector);
        entityInstanceRef.setKind(request.getKind());
        entityInstanceRef.setConnectorName(connector.getName());
        entityInstanceReferenceRepository.save(entityInstanceRef);

        attributeService.createAttributeContent(entityInstanceRef.getUuid(), request.getCustomAttributes(), Resource.ENTITY);
        logger.info("Entity {} created with Kind {}", entityInstanceRef.getUuid(), entityInstanceRef.getKind());

        EntityInstanceDto dto = entityInstanceRef.mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(entityInstanceRef.getUuid(), Resource.ENTITY));
        return dto;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.ENTITY, action = ResourceAction.UPDATE)
    public EntityInstanceDto editEntityInstance(SecuredUUID entityUuid, EntityInstanceUpdateRequestDto request) throws ConnectorException {
        EntityInstanceReference entityInstanceRef = getEntityInstanceReferenceEntity(entityUuid);

        EntityInstanceDto ref = getEntityInstance(entityUuid);
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(ref.getConnectorUuid()));

        FunctionGroupCode codeToSearch = FunctionGroupCode.ENTITY_PROVIDER;

        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.ENTITY);
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(), codeToSearch,
                request.getAttributes(), ref.getKind());

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        EntityInstanceRequestDto entityInstanceDto = new EntityInstanceRequestDto();
        entityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        entityInstanceDto.setKind(entityInstanceRef.getKind());
        entityInstanceDto.setName(entityInstanceRef.getName());
        entityInstanceApiClient.updateEntityInstance(connector.mapToDto(),
                entityInstanceRef.getEntityInstanceUuid(), entityInstanceDto);
        entityInstanceReferenceRepository.save(entityInstanceRef);

        attributeService.updateAttributeContent(entityInstanceRef.getUuid(), request.getCustomAttributes(), Resource.ENTITY);
        logger.info("Entity {} updated with Kind {}", entityInstanceRef.getUuid(), entityInstanceRef.getKind());

        EntityInstanceDto dto = entityInstanceRef.mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(entityInstanceRef.getUuid(), Resource.ENTITY));
        return dto;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.ENTITY, action = ResourceAction.DELETE)
    public void deleteEntityInstance(SecuredUUID entityUuid) throws ConnectorException {
        EntityInstanceReference entityInstanceRef = getEntityInstanceReferenceEntity(entityUuid);

        List<ValidationError> errors = new ArrayList<>();
        if (!entityInstanceRef.getLocations().isEmpty()) {
            errors.add(ValidationError.create("Entity instance {} has {} dependent Locations", entityInstanceRef.getName(),
                    entityInstanceRef.getLocations().size()));
            entityInstanceRef.getLocations().forEach(c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete Entity instance", errors);
        }

        entityInstanceApiClient.removeEntityInstance(entityInstanceRef.getConnector().mapToDto(), entityInstanceRef.getEntityInstanceUuid());
        attributeService.deleteAttributeContent(entityInstanceRef.getUuid(), Resource.ENTITY);
        entityInstanceReferenceRepository.delete(entityInstanceRef);

        logger.info("Entity instance {} was deleted", entityInstanceRef.getName());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.ENTITY, action = ResourceAction.ANY)
    public List<BaseAttribute> listLocationAttributes(SecuredUUID entityUuid) throws ConnectorException {
        final EntityInstanceReference entityInstance = getEntityInstanceReferenceEntity(entityUuid);
        final Connector connector = entityInstance.getConnector();
        return entityInstanceApiClient.listLocationAttributes(connector.mapToDto(), entityInstance.getEntityInstanceUuid());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    @ExternalAuthorization(resource = Resource.ENTITY, action = ResourceAction.ANY)
    public void validateLocationAttributes(SecuredUUID entityUuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        EntityInstanceReference entityInstance = getEntityInstanceReferenceEntity(entityUuid);

        Connector connector = entityInstance.getConnector();

        entityInstanceApiClient.validateLocationAttributes(connector.mapToDto(), entityInstance.getEntityInstanceUuid(),
                attributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ENTITY, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return entityInstanceReferenceRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(EntityInstanceReference::mapToAccessControlObjects)
                .collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getEntityInstanceReferenceEntity(uuid);
        // Since there are is no parent to the Entity, exclusive parent permission evaluation need not be done
    }

    private EntityInstanceReference getEntityInstanceReferenceEntity(SecuredUUID uuid) throws NotFoundException {
        return entityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, uuid));
    }

    private List<SearchFieldObject> getSearchFieldObjectForMetadata() {
        return attributeContentRepository.findDistinctAttributeContentNamesByAttrTypeAndObjType(Resource.ENTITY, AttributeType.META);
    }

    private List<SearchFieldObject> getSearchFieldObjectForCustomAttributes() {
        return attributeContentRepository.findDistinctAttributeContentNamesByAttrTypeAndObjType(Resource.ENTITY, AttributeType.CUSTOM);
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup() {

        final List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = new ArrayList<>();

        final List<SearchFieldObject> metadataSearchFieldObject = getSearchFieldObjectForMetadata();
        if (metadataSearchFieldObject.size() > 0) {
            searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(metadataSearchFieldObject), SearchGroup.META.getLabel()));
        }

        final List<SearchFieldObject> customAttrSearchFieldObject = getSearchFieldObjectForCustomAttributes();
        if (customAttrSearchFieldObject.size() > 0) {
            searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(customAttrSearchFieldObject), SearchGroup.CUSTOM.getLabel()));
        }

        List<SearchFieldDataDto> fields = List.of(
                SearchHelper.prepareSearch(SearchFieldNameEnum.ENTITY_NAME),
                SearchHelper.prepareSearch(SearchFieldNameEnum.ENTITY_CONNECTOR_NAME, entityInstanceReferenceRepository.findDistinctConnectorName()),
                SearchHelper.prepareSearch(SearchFieldNameEnum.ENTITY_KIND, entityInstanceReferenceRepository.findDistinctKind())
        );

        fields = fields.stream().collect(Collectors.toList());
        fields.sort(new SearchFieldDataComparator());

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, SearchGroup.PROPERTY.getLabel()));

        logger.debug("Searchable Fields by Groups: {}", searchFieldDataByGroupDtos);
        return searchFieldDataByGroupDtos;
    }
}
