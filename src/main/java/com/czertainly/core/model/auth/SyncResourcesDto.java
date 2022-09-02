package com.czertainly.core.model.auth;

import com.czertainly.api.model.core.auth.ResourceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.List;

public class SyncResourcesDto {

    @Schema(description = "List of new resources added")
    private List<ResourceDto> added;

    @Schema(description = "List of resources unused")
    private List<ResourceDto> unused;

    public List<ResourceDto> getAdded() {
        return added;
    }

    public void setAdded(List<ResourceDto> added) {
        this.added = added;
    }

    public List<ResourceDto> getUnused() {
        return unused;
    }

    public void setUnused(List<ResourceDto> unused) {
        this.unused = unused;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("added", added)
                .append("unused", unused)
                .toString();
    }
}
