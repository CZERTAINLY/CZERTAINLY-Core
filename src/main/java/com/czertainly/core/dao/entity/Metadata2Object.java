package com.czertainly.core.dao.entity;

import com.czertainly.core.model.auth.Resource;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "metadata_2_object")
public class Metadata2Object extends UniquelyIdentifiedAndAudited {

    @ManyToOne
    @JoinColumn(name = "metadata_content_uuid", nullable = false, insertable = false, updatable = false)
    private MetadataContent metadataContent;

    @Column(name = "metadata_content_uuid", nullable = false)
    private UUID metadataContentUuid;

    @Column(name = "object_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource objectType;

    @Column(name = "object_uuid", nullable = false)
    private UUID objectUuid;

    @Column(name = "source_object_type")
    @Enumerated(EnumType.STRING)
    private Resource sourceObjectType;

    @Column(name = "source_object_uuid")
    private UUID sourceObjectUuid;

    public MetadataContent getMetadataContent() {
        return metadataContent;
    }

    public void setMetadataContent(MetadataContent metadataContent) {
        this.metadataContent = metadataContent;
        this.metadataContentUuid = metadataContent.getUuid();
    }

    public UUID getMetadataContentUuid() {
        return metadataContentUuid;
    }

    public void setMetadataContentUuid(UUID metadataContentUuid) {
        this.metadataContentUuid = metadataContentUuid;
    }

    public Resource getObjectType() {
        return objectType;
    }

    public void setObjectType(Resource objectType) {
        this.objectType = objectType;
    }

    public UUID getObjectUuid() {
        return objectUuid;
    }

    public void setObjectUuid(UUID objectUuid) {
        this.objectUuid = objectUuid;
    }

    public Resource getSourceObjectType() {
        return sourceObjectType;
    }

    public void setSourceObjectType(Resource sourceObjectType) {
        this.sourceObjectType = sourceObjectType;
    }

    public UUID getSourceObjectUuid() {
        return sourceObjectUuid;
    }

    public void setSourceObjectUuid(UUID sourceObjectUuid) {
        this.sourceObjectUuid = sourceObjectUuid;
    }
}
