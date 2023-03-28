package com.czertainly.core.util.converter;

import com.czertainly.api.model.core.settings.Section;

import java.beans.PropertyEditorSupport;

public class SectionCodeConverter extends PropertyEditorSupport {
    public void setAsText(final String text) throws IllegalArgumentException {
        setValue(Section.findByCode(text));
    }
}