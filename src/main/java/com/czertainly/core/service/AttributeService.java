package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.client.attribute.metadata.ConnectorMetadataResponseDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataCreateRequestDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataUpdateRequestDto;
import com.czertainly.api.model.common.attribute.common.CustomAttribute;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v3.CustomAttributeV3;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttributeService extends ResourceExtensionService {

    /**
     * Function to list the available custom attributes stored in the database
     *
     * @param filter : SecurityFilter to load object permissions
     * @param attributeContentType : Attribute content type to filter custom attributes
     * @return - List of Custom Attributes stored in the database
     */
    List<CustomAttributeDefinitionDto> listCustomAttributes(SecurityFilter filter, AttributeContentType attributeContentType);

    /**
     * Function to list the available global metadata stored in the database
     *
     * @return - List of Global Metadata stored in the database
     */
    List<AttributeDefinitionDto> listGlobalMetadata();

    /**
     * Function to get the detail of the custom attribute by providing the UUID
     *
     * @param uuid UUID of custom attribute
     * @return Attribute definition of the custom attribute
     */
    CustomAttributeDefinitionDetailDto getCustomAttribute(UUID uuid) throws NotFoundException;

    /**
     * Function to get the details of the global metadata
     *
     * @param uuid of the global metadata
     * @return Detail of the global metadata
     */
    GlobalMetadataDefinitionDetailDto getGlobalMetadata(UUID uuid) throws NotFoundException;

    /**
     * Function to create the custom attribute based on the user provided information
     *
     * @param request: {@link CustomAttributeCreateRequestDto} request information
     * @return UUID of the newly created attribute
     */
    CustomAttributeDefinitionDetailDto createCustomAttribute(CustomAttributeCreateRequestDto request) throws AlreadyExistException, AttributeException;

    /**
     * Function to create a global metadata
     *
     * @param request Request containing the details for creating a new global metadata
     * @return Details of the newly created global metadata
     */
    GlobalMetadataDefinitionDetailDto createGlobalMetadata(GlobalMetadataCreateRequestDto request) throws AlreadyExistException, AttributeException;


    /**
     * Function to update the custom attribute
     *
     * @param uuid    UUID of the attribute definition
     * @param request Request containting the attribute information
     * @return
     */
    CustomAttributeDefinitionDetailDto editCustomAttribute(UUID uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException, AttributeException;

    /**
     * Function to update the global metadata
     *
     * @param uuid    UUID of the global metadata
     * @param request Details to Update the global metadata
     * @return Details of the updated global metadata
     */
    GlobalMetadataDefinitionDetailDto editGlobalMetadata(UUID uuid, GlobalMetadataUpdateRequestDto request) throws NotFoundException, AttributeException;

    /**
     * Function to delete custom attribute
     *
     * @param uuid  Attribute UUID
     * @throws NotFoundException
     */
    void deleteCustomAttribute(UUID uuid) throws NotFoundException;

    /**
     * Delete multiple custom attributes
     *
     * @param attributeUuids UUIDs of the attributes to be deleted
     */
    void bulkDeleteCustomAttributes(List<String> attributeUuids);

    /**
     * Function to enable custom attribute. Objects can use the attributes if and only if they are enabled
     *
     * @param uuid  Custom Attribute UUID
     * @param enable flag if to enable or disable attribute
     * @throws NotFoundException when attribute is not found
     */
    void enableCustomAttribute(UUID uuid, boolean enable) throws NotFoundException;

    /**
     * Enable multiple custom attributes. Attributes can be associated with the objects only in their enabled state
     *
     * @param attributeUuids List of UUIDs of the attributes
     * @param enable flag if to enable or disable attributes
     */
    void bulkEnableCustomAttributes(List<String> attributeUuids, boolean enable);

    /**
     * Update the resources to which the attributes can be used
     *
     * @param uuid      UUID of the custom attribute
     * @param resources List of resources to which the attribute has to be associated
     * @throws NotFoundException
     */
    void updateResources(UUID uuid, List<Resource> resources) throws NotFoundException;

    /**
     * Function to get the list of custom attributes that are applicable for the resource
     *
     * @param filter   : SecurityFilter to load object permissions
     * @param resource Name of the resource to get the list of custom attributes
     * @return List of data attributes
     */
    List<CustomAttribute<?>> getResourceAttributes(SecurityFilter filter, Resource resource);

    /**
     * Function to validate if the custom attributes contains the correct information
     *
     * @param attributes List of request attributes
     * @param resource   Resource to which the custom attributes should be validated for
     * @throws ValidationException Thrown if the validation fails
     */
//    void validateCustomAttributes(List<RequestAttribute> attributes, Resource resource) throws ValidationException;

    /**
     * Create the content for the attribute
     *
     * @param objectUuid UUID of the object
     * @param attributes List of custom attributes
     * @param resource   Resource for the attribute and value
     */
//    void createAttributeContent(UUID objectUuid, List<RequestAttribute> attributes, Resource resource) throws NotFoundException, AttributeException;

    /**
     * Update the content for the attribute
     *
     * @param objectUuid UUID of the object
     * @param attributes List of custom attributes
     * @param resource   Resource for the attribute and value
     */
//    void updateAttributeContent(UUID objectUuid, List<RequestAttribute> attributes, Resource resource) throws NotFoundException, AttributeException;

    /**
     * Update the content for a single attribute
     *
     * @param objectUuid UUID of the object
     * @param attributeUuid UUID of custom attributes
     * @param attributeContent Attribute content
     * @param resource   Resource for the attribute and value
     */
//    void updateAttributeContent(UUID objectUuid, UUID attributeUuid, List<BaseAttributeContent> attributeContent, Resource resource) throws NotFoundException, AttributeException;

    /**
     * Function to delete all the attribute content for an individual object. This method to be used when deleting the object
     *
     * @param objectUuid UUID of the object
     * @param resource   Resource type
     */
//    void deleteAttributeContent(UUID objectUuid, Resource resource);

    /**
     * Function to delete all the attribute content for an individual object with the parent resource.
     *
     * @param objectUuid UUID of the object
     * @param resource   Resource type
     * @param parentObjectUuid Parent Object UUID
     * @param parentResource Parent Object resource
     * @param type Type of the attribute to be deleted. Either Custom or Metadata
     */
//    void deleteAttributeContent(UUID objectUuid, Resource resource, UUID parentObjectUuid, Resource parentResource, AttributeType type);

    /**
     * Function to get the list of custom attributes associated with a specific object
     *
     * @param uuid     UUID of the object
     * @param resource Type of the object
     * @return List of the custo attributes with values
     */
//    List<ResponseAttributeDto> getCustomAttributesWithValues(UUID uuid, Resource resource);

    /**
     * Function to get the list of supported resources for which the custom attributes are supported
     *
     * @return
     */
    List<Resource> getResources();

    /**
     * Function to get the list of all the connector metadata that can be promoted as global metadata
     * @param connectorUuid UUID of the connector for filtering
     */
    List<ConnectorMetadataResponseDto> getConnectorMetadata(Optional<String> connectorUuid);

    /**
     * Function to promote the metadata from connector to global metadata
     * @return Details of the global metadata
     */
    GlobalMetadataDefinitionDetailDto promoteConnectorMetadata(UUID uuid, UUID connectorUUid) throws NotFoundException;

    /**
     * Demote multiple global metadata
     *
     * @param attributeUuids UUIDs of the global metadata to be demoted
     */
    void bulkDemoteConnectorMetadata(List<String> attributeUuids);

    /**
     * Function to demote the metadata from global metadata to connector metadata as result of delete operation
     * @param uuid    UUID of the global metadata
     */
    void demoteConnectorMetadata(UUID uuid) throws NotFoundException;

    /**
     * Check and create the reference attributes in the database
     */
//    AttributeDefinition createAttributeDefinition(UUID connectorUuid, BaseAttribute attribute) throws AttributeException;

    /**
     * Function to get the reference attributes. This function will check and return if there is a reference
     * attribute available for the given attribute uuid and the connector UUID
     */
//    DataAttribute getReferenceAttribute(UUID connectorUUid, String attributeName);

    /**
     *
     * @return
     */
//    List<SearchFieldDataByGroupDto> getResourceSearchableFieldInformation(Resource resource);

//    List<UUID> getResourceObjectUuidsByFilters(Resource resource, SecurityFilter securityFilter, List<SearchFilterRequestDto> searchFilters);

}
