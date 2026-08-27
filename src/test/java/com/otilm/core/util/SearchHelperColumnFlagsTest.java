package com.otilm.core.util;

import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.content.data.ProtectionLevel;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.core.model.SearchFieldObject;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The column flags an attribute-sourced catalogue field carries. Property fields report theirs from the JPA metamodel,
 * so they are covered where a persistence context exists rather than here.
 */
class SearchHelperColumnFlagsTest {

    private static SearchFieldObject attributeField(AttributeContentType contentType, ProtectionLevel protectionLevel) {
        SearchFieldObject field = new SearchFieldObject("cost-centre", contentType, AttributeType.CUSTOM);
        field.setLabel("Cost centre");
        field.setProtectionLevel(protectionLevel);
        field.setContentItems(List.of());
        return field;
    }

    private static SearchFieldDataDto prepare(AttributeContentType contentType, ProtectionLevel protectionLevel) {
        return SearchHelper.prepareSearchForJSON(attributeField(contentType, protectionLevel), false);
    }

    @Test
    void ordinaryAttributeIsOfferedAsAColumn() {
        Assertions.assertEquals(true, prepare(AttributeContentType.TEXT, ProtectionLevel.NONE).getDisplayable());
    }

    @Test
    void secretContentIsNeverOfferedAsAColumn() {
        Assertions.assertEquals(false, prepare(AttributeContentType.SECRET, ProtectionLevel.NONE).getDisplayable());
    }

    @Test
    void encryptedContentIsNeverOfferedAsAColumn() {
        // Stored as ciphertext only its own decryption path can read, and a listing does not take that path.
        Assertions.assertEquals(false, prepare(AttributeContentType.TEXT, ProtectionLevel.ENCRYPTED).getDisplayable());
    }

    @Test
    void codeBlockContentIsNeverOfferedAsAColumn() {
        // Multi-line by construction, so a single-line table cell cannot hold one without breaking the row.
        Assertions.assertEquals(false, prepare(AttributeContentType.CODEBLOCK, ProtectionLevel.NONE).getDisplayable());
    }

    @ParameterizedTest
    @EnumSource(AttributeContentType.class)
    void noAttributeFieldIsSortableUntilAttributeSortingLands(AttributeContentType contentType) {
        Assertions.assertEquals(false, prepare(contentType, ProtectionLevel.NONE).getSortable());
    }

    @ParameterizedTest
    @EnumSource(AttributeContentType.class)
    void everyAttributeFieldAnswersBothFlags(AttributeContentType contentType) {
        SearchFieldDataDto field = prepare(contentType, ProtectionLevel.NONE);

        // A flag left null would reach the picker as "unknown", and the frontend reads an absent flag as a no.
        Assertions.assertNotNull(field.getDisplayable());
        Assertions.assertNotNull(field.getSortable());
    }

    @Test
    void noContentTypeBeyondSecretAndCodeBlockIsWithheld() {
        Set<AttributeContentType> undisplayable = Arrays
                .stream(AttributeContentType.values())
                .filter(contentType -> !prepare(contentType, ProtectionLevel.NONE).getDisplayable())
                .collect(Collectors.toSet());

        Assertions.assertEquals(Set.of(AttributeContentType.SECRET, AttributeContentType.CODEBLOCK), undisplayable);
    }
}
