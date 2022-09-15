package com.czertainly.core.model.auth;

import com.czertainly.api.model.core.auth.ActionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.List;

public class SyncActionsDto {
    @Schema(description = "List of added Actions")
    private List<ActionDto> added;

    @Schema(description = "List of unused Actions")
    private List<ActionDto> unused;

    public List<ActionDto> getAdded() {
        return added;
    }

    public void setAdded(List<ActionDto> added) {
        this.added = added;
    }

    public List<ActionDto> getUnused() {
        return unused;
    }

    public void setUnused(List<ActionDto> unused) {
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
