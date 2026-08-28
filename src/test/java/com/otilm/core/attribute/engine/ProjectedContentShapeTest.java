package com.otilm.core.attribute.engine;

import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.content.data.CredentialAttributeContentData;
import com.otilm.api.model.common.attribute.common.content.data.FileAttributeContentData;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The shape one attribute value is projected in. A list cell renders a single line, so two content types must not be
 * serialized whole into a listing: one because it can carry secret material, one because of its size.
 */
class ProjectedContentShapeTest {

    private static <T extends Serializable> BaseAttributeContentV3<T> content(AttributeContentType contentType,
            String reference, T data) {
        BaseAttributeContentV3<T> value = new BaseAttributeContentV3<>();
        value.setContentType(contentType);
        value.setReference(reference);
        value.setData(data);
        return value;
    }

    @Test
    void credentialContentIsProjectedAsItsReferenceAlone() {
        CredentialAttributeContentData credential = new CredentialAttributeContentData();
        credential.setUuid("f0e3c0a4-0000-0000-0000-000000000001");
        credential.setName("vault-token");
        credential.setAttributes(List.of());

        BaseAttributeContentV3<?> projected = AttributeColumnProjector
                .toProjectedValue(content(AttributeContentType.CREDENTIAL, "vault-token", credential),
                        AttributeContentType.CREDENTIAL);

        // The nested attribute list can carry secret-bearing content, and the cell shows the reference.
        Assertions.assertNull(projected.getData());
        Assertions.assertEquals("vault-token", projected.getReference());
        Assertions.assertEquals(AttributeContentType.CREDENTIAL, projected.getContentType());
    }

    @Test
    void fileContentIsProjectedWithoutItsBody() {
        FileAttributeContentData file = new FileAttributeContentData();
        file.setFileName("chain.pem");
        file.setMimeType("application/x-pem-file");
        file.setContent("dGhlIHdob2xlIGZpbGUgYm9keQ==");

        BaseAttributeContentV3<?> projected = AttributeColumnProjector
                .toProjectedValue(content(AttributeContentType.FILE, "chain.pem", file), AttributeContentType.FILE);

        FileAttributeContentData data = (FileAttributeContentData) projected.getData();
        Assertions.assertEquals("chain.pem", data.getFileName());
        Assertions.assertEquals("application/x-pem-file", data.getMimeType());
        // A page of rows would otherwise serialize the body of every file it lists, which no cell shows.
        Assertions.assertNull(data.getContent());
    }

    @Test
    void aFileValueThatCarriesNoFileDataIsLeftAlone() {
        BaseAttributeContentV3<String> value = content(AttributeContentType.FILE, "chain.pem", "not file data");

        Assertions.assertSame(value, AttributeColumnProjector.toProjectedValue(value, AttributeContentType.FILE));
    }

    @ParameterizedTest
    @EnumSource(value = AttributeContentType.class, mode = EnumSource.Mode.EXCLUDE, names = {"CREDENTIAL", "FILE"})
    void everyOtherContentTypeIsProjectedUnchanged(AttributeContentType contentType) {
        BaseAttributeContentV3<String> value = content(contentType, "reference", "data");

        Assertions.assertSame(value, AttributeColumnProjector.toProjectedValue(value, contentType));
    }
}
