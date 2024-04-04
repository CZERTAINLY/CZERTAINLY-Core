package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.enums.SearchFieldTypeEnum;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.util.SearchHelper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ResourceServiceImpl implements ResourceService {
    private static final Logger logger = LoggerFactory.getLogger(ResourceServiceImpl.class);

    private LocationService locationService;
    private AcmeProfileService acmeProfileService;
    private AuthorityInstanceService authorityInstanceService;
    private ComplianceProfileService complianceProfileService;
    private ConnectorService connectorService;
    private CredentialService credentialService;
    private DiscoveryService discoveryService;
    private EntityInstanceService entityInstanceService;
    private GroupService groupService;
    private RaProfileService raProfileService;
    private ScepProfileService scepProfileService;
    private CryptographicKeyService keyService;
    private TokenProfileService tokenProfileService;
    private TokenInstanceService tokenInstanceService;
    private UserManagementService userManagementService;
    private RoleManagementService roleManagementService;
    private CertificateService certificateService;
    private AttributeEngine attributeEngine;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setAcmeProfileService(AcmeProfileService acmeProfileService) {
        this.acmeProfileService = acmeProfileService;
    }

    @Autowired
    public void setAuthorityInstanceService(AuthorityInstanceService authorityInstanceService) {
        this.authorityInstanceService = authorityInstanceService;
    }

    @Autowired
    public void setComplianceProfileService(ComplianceProfileService complianceProfileService) {
        this.complianceProfileService = complianceProfileService;
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
    public void setDiscoveryService(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Autowired
    public void setEntityInstanceService(EntityInstanceService entityInstanceService) {
        this.entityInstanceService = entityInstanceService;
    }

    @Autowired
    public void setGroupService(GroupService groupService) {
        this.groupService = groupService;
    }

    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    @Autowired
    public void setRaProfileService(RaProfileService raProfileService) {
        this.raProfileService = raProfileService;
    }

    @Autowired
    public void setScepProfileService(ScepProfileService scepProfileService) {
        this.scepProfileService = scepProfileService;
    }

    @Autowired
    public void setKeyService(CryptographicKeyService keyService) {
        this.keyService = keyService;
    }

    @Autowired
    public void setTokenProfileService(TokenProfileService tokenProfileService) {
        this.tokenProfileService = tokenProfileService;
    }

    @Autowired
    public void setTokenInstanceService(TokenInstanceService tokenInstanceService) {
        this.tokenInstanceService = tokenInstanceService;
    }

    @Autowired
    public void setUserManagementService(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @Autowired
    public void setRoleManagementService(RoleManagementService roleManagementService) {
        this.roleManagementService = roleManagementService;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Override
    public List<NameAndUuidDto> getObjectsForResource(Resource resourceName) throws NotFoundException {
        return switch (resourceName) {
            case ACME_PROFILE -> acmeProfileService.listResourceObjects(SecurityFilter.create());
            case AUTHORITY -> authorityInstanceService.listResourceObjects(SecurityFilter.create());
            case COMPLIANCE_PROFILE -> complianceProfileService.listResourceObjects(SecurityFilter.create());
            case CONNECTOR -> connectorService.listResourceObjects(SecurityFilter.create());
            case CREDENTIAL -> credentialService.listResourceObjects(SecurityFilter.create());
            case ENTITY -> entityInstanceService.listResourceObjects(SecurityFilter.create());
            case GROUP -> groupService.listResourceObjects(SecurityFilter.create());
            case LOCATION -> locationService.listResourceObjects(SecurityFilter.create());
            case RA_PROFILE -> raProfileService.listResourceObjects(SecurityFilter.create());
            case SCEP_PROFILE -> scepProfileService.listResourceObjects(SecurityFilter.create());
            case TOKEN_PROFILE -> tokenProfileService.listResourceObjects(SecurityFilter.create());
            case TOKEN -> tokenInstanceService.listResourceObjects(SecurityFilter.create());
            default ->
                    throw new NotFoundException("Cannot list objects for requested resource: " + resourceName.getCode());
        };
    }

    @Override
    public List<ResponseAttributeDto> updateAttributeContentForObject(Resource objectType, SecuredUUID objectUuid, UUID attributeUuid, List<BaseAttributeContent> attributeContentItems) throws NotFoundException, AttributeException {
        logger.info("Updating the attribute {} for resource {} ith value {}", attributeUuid, objectType, attributeUuid);
        switch (objectType) {
            case ACME_PROFILE:
                acmeProfileService.evaluatePermissionChain(objectUuid);
                break;
            case AUTHORITY:
                authorityInstanceService.evaluatePermissionChain(objectUuid);
                break;
            case COMPLIANCE_PROFILE:
                complianceProfileService.evaluatePermissionChain(objectUuid);
                break;
            case CONNECTOR:
                connectorService.evaluatePermissionChain(objectUuid);
                break;
            case CREDENTIAL:
                credentialService.evaluatePermissionChain(objectUuid);
                break;
            case DISCOVERY:
                discoveryService.evaluatePermissionChain(objectUuid);
                break;
            case ENTITY:
                entityInstanceService.evaluatePermissionChain(objectUuid);
                break;
            case GROUP:
                groupService.evaluatePermissionChain(objectUuid);
                break;
            case LOCATION:
                locationService.evaluatePermissionChain(objectUuid);
                break;
            case RA_PROFILE:
                raProfileService.evaluatePermissionChain(objectUuid);
                break;
            case TOKEN_PROFILE:
                tokenProfileService.evaluatePermissionChain(objectUuid);
                break;
            case TOKEN:
                tokenInstanceService.evaluatePermissionChain(objectUuid);
                break;
            case USER:
                userManagementService.evaluatePermissionChain(objectUuid);
                break;
            case ROLE:
                roleManagementService.evaluatePermissionChain(objectUuid);
                break;
            case CRYPTOGRAPHIC_KEY:
                keyService.evaluatePermissionChain(objectUuid);
                break;
            case CERTIFICATE:
                certificateService.evaluatePermissionChain(objectUuid);
                break;
            default:
                throw new NotFoundException("Cannot update custom attribute for requested resource: " + objectType.getCode());
        }

        attributeEngine.updateObjectCustomAttributeContent(objectType, objectUuid.getValue(), attributeUuid, null, attributeContentItems);
        return attributeEngine.getObjectCustomAttributesContent(objectType, objectUuid.getValue());
    }

    @Override
    public List<SearchFieldDataByGroupDto> listResourceRuleFilterFields(Resource resource, boolean settable) throws NotFoundException {
        List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine.getResourceSearchableFields(resource);

        List<SearchFieldNameEnum> enums = SearchFieldNameEnum.getEnumsForResource(resource);
        List<SearchFieldDataDto> fieldDataDtos = new ArrayList<>();
        for (SearchFieldNameEnum fieldEnum : enums) {
            if (settable && !fieldEnum.isSettable()) continue;
            if (fieldEnum.getFieldTypeEnum() != SearchFieldTypeEnum.LIST) fieldDataDtos.add(SearchHelper.prepareSearch(fieldEnum));
            if (fieldEnum.getFieldProperty().getEnumClass() != null) fieldDataDtos.add(SearchHelper.prepareSearch(fieldEnum, fieldEnum.getFieldProperty().getEnumClass()));
            if (fieldEnum.getFieldResource() != null) fieldDataDtos.add(SearchHelper.prepareSearch(fieldEnum, getObjectsForResource(fieldEnum.getFieldResource())));
        }

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fieldDataDtos, FilterFieldSource.PROPERTY));

        return searchFieldDataByGroupDtos;
    }
}
