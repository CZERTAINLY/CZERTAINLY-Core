package com.czertainly.core.service;

import com.czertainly.api.model.client.metadata.MetadataResponseDto;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.core.auth.Resource;

import java.util.List;
import java.util.UUID;

public interface MetadataService {
    /**
     * Method to create / register metadata based on the responses from the connector
     * @param connectorUuid - UUID of the connector
     * @param metadataDefinitions - List of attributes from the connector
     */
    void createMetadataDefinitions(UUID connectorUuid, List<MetadataAttribute> metadataDefinitions);

    /**
     * Method to create the metadata for the objects without any source objects
     * @param connectorUuid - UUID Of the connector
     * @param objectUuid - UUID of the Object
     * @param sourceObjectUuid - UUID of the source object
     * @param metadata - List of metadata for the attributes
     * @param  resource - Resource for the metadata
     * @param sourceObjectResource - Resource of the source object
     */
    void createMetadata(UUID connectorUuid, UUID objectUuid, UUID sourceObjectUuid, String sourceObjectName, List<MetadataAttribute> metadata, Resource resource, Resource sourceObjectResource);

    /**
     * Method to get the metadata for the specified object
     * @param uuid UUID of the Object
     * @param resource Resource for which the metadata has to be retrieved
     * @return List of Info attributes as metadata
     */
    List<MetadataAttribute> getMetadata(UUID connectorUuid, UUID uuid, Resource resource);

    /**
     * Method to get the full metadata for the specified object
     * @param uuid UUID of the Object
     * @param resource Resource for which the metadata has to be retrieved
     * @return List of Info attributes as metadata
     */
    List<MetadataAttribute> getMetadataWithSource(UUID connectorUuid, UUID uuid, Resource resource, UUID sourceObjectUuid, Resource sourceObjectResource);

    /**
     * Method to get the metadata for the specified object
     * @param uuid UUID of the Object
     * @param resource Resource for which the metadata has to be retrieved
     * @param sourceObjectUuid UUID of the source object
     * @param sourceObjectResource Source Object Resource
     * @return List of Info attributes as metadata
     */
    List<MetadataResponseDto> getFullMetadata(UUID uuid, Resource resource, UUID sourceObjectUuid, Resource sourceObjectResource);

    /**
     * Method to get the metadata for the specified object
     * @param uuid UUID of the Object
     * @param resource Resource for which the metadata has to be retrieved
     * @param sourceObjectResource List of source object resources
     * @return List of Info attributes as metadata
     */
    List<MetadataResponseDto> getFullMetadataWithNullResource(UUID uuid, Resource resource, List<Resource> sourceObjectResource);

    /**
     * Method to get the metadata for the specified object
     * @param uuid UUID of the Object
     * @param resource Resource for which the metadata has to be retrieved
     * @return List of Info attributes as metadata
     */
    List<MetadataResponseDto> getFullMetadata(UUID uuid, Resource resource);
}
