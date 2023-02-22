package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.setting.Section;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Entity
@Table(name = "setting")
public class Setting extends UniquelyIdentifiedAndAudited {

    @Column(name = "section")
    @Enumerated(EnumType.STRING)
    private Section section;

    @Column(name = "attributes", length = Integer.MAX_VALUE)
    private String attributes;

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("section", section)
                .append("attributes", attributes)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Setting that = (Setting) o;
        return new EqualsBuilder().append(uuid, that.uuid).append(section, that.section).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uuid).toHashCode();
    }
}
