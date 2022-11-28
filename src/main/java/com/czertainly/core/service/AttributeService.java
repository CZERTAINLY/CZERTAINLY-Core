package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.UUID;

public interface AttributeService {

    /**
     * Function to create the custom attribute based on the user provided information
     *
     * @param request: {@link CustomAttributeCreateRequestDto} request information
     * @return UUID of the newly created attribute
     */
    CustomAttributeDefinitionDetailDto createAttribute(CustomAttributeCreateRequestDto request) throws ValidationException, AlreadyExistException;

    /**
     * Function to list the available custom attributes stored in the database
     *
     * @param filter : Secutry filter for Access Control
     * @param type:  Type of the attribute, either custom or meta
     * @return - List of Custom Attributes stored in the database
     */
    List<AttributeDefinitionDto> listAttributes(SecurityFilter filter, AttributeType type);

    /**
     * Function to get the detail of the custom attribute by providing the UUID
     *
     * @param uuid UUID of custom attribute
     * @return Attribute definition of the custom attribute
     */
    CustomAttributeDefinitionDetailDto getAttribute(SecuredUUID uuid) throws NotFoundException;

    /**
     * Function to update the custom attribute
     *
     * @param uuid    UUID of the attribute definition
     * @param request Request containting the attribute information
     * @return
     */
    CustomAttributeDefinitionDetailDto editAttribute(SecuredUUID uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException;

    /**
     * Function to delete custom attribute
     *
     * @param uuid  Attribute UUID
     * @param type: Type of the attribute, either custom or meta
     * @throws NotFoundException
     */
    void deleteAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException;

    /**
     * Function to enable custom attribute. Objects can use the attributes if and only if they are enabled
     *
     * @param uuid  - Attribute UUID
     * @param type: Type of the attribute, either custom or meta
     * @throws NotFoundException
     */
    void enableAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException;

    /**
     * Function to disable custom attribute. Once the attribute is disabled, the objects cannot use the custom attributes
     *
     * @param uuid  Custom Attribute UUID
     * @param type: Type of the attribute, either custom or meta
     * @throws NotFoundException
     */
    void disableAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException;

    /**
     * Delete multiple attributes from the database
     *
     * @param attributeUuids UUIDs of the attributes to be deleted
     * @param type:          Type of the attribute, either custom or meta
     */
    void bulkDeleteAttributes(List<SecuredUUID> attributeUuids, AttributeType type);

    /**
     * Enable Multiple attributes. Attributes can be associated with the objects only in their enabled state
     *
     * @param attributeUuids List of UUIDs of the attributes
     * @param type:          Type of the attribute, either custom or meta
     */
    void bulkEnableAttributes(List<SecuredUUID> attributeUuids, AttributeType type);

    /**
     * Disable multiple attributes in the database. Attributes cannot be associated with the objects if they are disabled
     *
     * @param attributeUuids List of Attribute UUIDs to be disabled
     * @param type:          Type of the attribute, either custom or meta
     */
    void bulkDisableAttributes(List<SecuredUUID> attributeUuids, AttributeType type);

    /**
     * Update the resources to which the attributes can be used
     * @param uuid UUID of the custom attribute
     * @param resources List of resources to which the attribute has to be associated
     * @throws NotFoundException
     */
    void updateResources(SecuredUUID uuid, List<Resource> resources) throws NotFoundException;

    /**
     * Function to get the list of custom attributes that are applicable for the resource
     * @param resource Name of the resource to get the list of custom attributes
     * @return List of data attributes
     */
    List<BaseAttribute> getResourceAttributes(Resource resource);

    /**
     * Function to validate if the custom attributes contains the correct information
     * @param attributes List of request attributes
     * @param resource Resource to which the custom attributes should be validated for
     * @throws ValidationException Thrown if the validation fails
     */
    void validateCustomAttributes(List<RequestAttributeDto> attributes, Resource resource) throws ValidationException;

    /**
     * Create the content for the attribute
     * @param objectUuid UUID of the object
     * @param attributes List of custom attributes
     * @param resource Resource for the attribute and value
     */
    void createAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource);

    /**
     * Update the content for the attribute
     * @param objectUuid UUID of the object
     * @param attributes List of custom attributes
     * @param resource Resource for the attribute and value
     */
    void updateAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource);

    /**
     * Function to delete the attribute content for an individual object
     * @param objectUuid UUID of the object
     * @param attributes List of custom attributes
     * @param resource Resource type
     */
    void deleteAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource);

    /**
     * Function to delete all the attribute content for an individual object. This method to be used when deleting the object
     * @param objectUuid UUID of the object
     * @param resource Resource type
     */
    void deleteAttributeContent(UUID objectUuid, Resource resource);

    /**
     * Function to get the list of custom attributes associated with a specific object
     * @param uuid UUID of the object
     * @param resource Type of the object
     * @return List of the custo attributes with values
     */
    List<ResponseAttributeDto> getCustomAttributesWithValues(UUID uuid, Resource resource);

    /**
     * Function to get the list of supported resources for which the custom attributes are supported
     * @return
     */
    List<Resource> getResources();
}
