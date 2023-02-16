package com.czertainly.core.dao.entity;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.setting.Section;
import com.czertainly.api.model.core.setting.UtilServiceSettingDto;
import com.czertainly.core.util.SerializationUtil;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Entity
@Table(name = "setting")
public class Setting extends UniquelyIdentifiedAndAudited {

    @Column(name = "name")
    private String name;

    @Column(name = "section")
    @Enumerated(EnumType.STRING)
    private Section section;

    @Column(name = "data", length = Integer.MAX_VALUE)
    private String data;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public Object getData(Section section) {
        return SerializationUtil.deserialize(data, section.getClass());
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setData(Object data) {
        this.data = SerializationUtil.serialize(data);
    }

    public Object toDto(Section section) {
        switch (section){
            case UTIL_SERVICE: return getData(section);
            default: throw new ValidationException(ValidationError.create("Unsupported section"));
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", name)
                .append("uuid", uuid)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Setting that = (Setting) o;
        return new EqualsBuilder().append(uuid, that.uuid).append(name, that.name).append(section, that.section).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uuid).toHashCode();
    }
}
