package com.otilm.core.attribute.engine;

import com.otilm.api.model.client.certificate.SearchColumnRequestDto;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.search.AttributeProjectable;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.core.attribute.engine.records.ProjectedAttributeContent;
import com.otilm.core.dao.repository.AttributeContent2ObjectRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Populates the attribute-sourced columns a listing request asked for.
 *
 * <p>
 * Every listing that supports configurable columns implements {@link AttributeProjectable}, so one component fills any
 * of them rather than each resource growing its own branch. Values are loaded for the whole page in one query: a page
 * of twenty-five objects would otherwise issue twenty-five round trips before it could be serialized.
 *
 * <p>
 * A request that names no attribute-sourced column does nothing at all here, which is what keeps a response without
 * {@code columns} identical to what it was before this existed.
 */
@Component
@RequiredArgsConstructor
public class AttributeColumnProjector {

    /** Separates the attribute name from its content type in a field identifier, as the catalogue builds it. */
    private static final String FIELD_IDENTIFIER_SEPARATOR = "|";

    /**
     * Content that is never projected, whatever a request asks for. The catalogue already marks these fields
     * undisplayable, so a well-behaved caller cannot reach them; this is the second lock, because the first one is a
     * flag on a response the caller is free to ignore.
     */
    private static final Set<AttributeContentType> WITHHELD_CONTENT_TYPES = Set
            .of(AttributeContentType.SECRET, AttributeContentType.CODEBLOCK);

    private final AttributeContent2ObjectRepository attributeContent2ObjectRepository;

    /** One requested attribute column, split into the parts the stored content is matched by. */
    private record RequestedColumn(FilterFieldSource source, AttributeType attributeType, String attributeName,
            AttributeContentType contentType, String fieldIdentifier) {
    }

    /**
     * Fills in the values of the attribute-sourced columns the request named, for one page of listing entries.
     *
     * @param resource the resource whose object type the attribute content is stored under
     * @param columns the columns the request asked for; property columns and unparseable identifiers are ignored
     * @param entries the page
     * @param uuidOf the uuid an entry's attribute content is stored against, which is not always the entry's own - a
     * key item carries the attributes of the key it belongs to, and several items may share one. An entry whose uuid
     * cannot be read is left unprojected rather than failing the listing.
     */
    public <T extends AttributeProjectable> void project(Resource resource, List<SearchColumnRequestDto> columns,
            List<T> entries, Function<T, UUID> uuidOf) {
        if (columns == null || columns.isEmpty() || entries == null || entries.isEmpty()) {
            return;
        }

        List<RequestedColumn> requested = parseRequestedColumns(columns);
        if (requested.isEmpty()) {
            return;
        }

        Map<UUID, List<T>> entriesByUuid = entries
                .stream()
                .filter(entry -> uuidOf.apply(entry) != null)
                .collect(Collectors.groupingBy(uuidOf, LinkedHashMap::new, Collectors.toList()));
        if (entriesByUuid.isEmpty()) {
            return;
        }

        List<ProjectedAttributeContent> stored = attributeContent2ObjectRepository
                .getProjectedAttributesContent(resource, List.copyOf(entriesByUuid.keySet()),
                        requested.stream().map(RequestedColumn::attributeType).distinct().toList(),
                        requested.stream().map(RequestedColumn::attributeName).distinct().toList());

        applyToEntries(collectValues(stored, requested), entriesByUuid);
    }

    /**
     * The uuid a listing entry carries, for the DTOs that hold theirs as a string. An unparseable value yields
     * {@code null}, which leaves that one entry unprojected instead of failing the page it is on.
     */
    public static UUID parseUuid(String uuid) {
        try {
            return uuid == null ? null : UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The attribute-sourced columns of a request, as the parts a stored content row is matched by. A property column
     * has no attribute behind it, and an identifier that does not carry a content type names no field the catalogue
     * ever published - both are skipped rather than rejected, so one stale entry in a saved view does not fail the
     * listing that carries it.
     */
    private List<RequestedColumn> parseRequestedColumns(List<SearchColumnRequestDto> columns) {
        List<RequestedColumn> requested = new ArrayList<>();
        for (SearchColumnRequestDto column : columns) {
            FilterFieldSource source = column.getFieldSource();
            if (source == null || source.getAttributeType() == null || column.getFieldIdentifier() == null) {
                continue;
            }
            int separator = column.getFieldIdentifier().lastIndexOf(FIELD_IDENTIFIER_SEPARATOR);
            if (separator <= 0) {
                continue;
            }
            AttributeContentType contentType = contentTypeOf(column.getFieldIdentifier().substring(separator + 1));
            if (contentType == null || WITHHELD_CONTENT_TYPES.contains(contentType)) {
                continue;
            }
            requested
                    .add(new RequestedColumn(source, source.getAttributeType(),
                            column.getFieldIdentifier().substring(0, separator), contentType,
                            column.getFieldIdentifier()));
        }
        return requested;
    }

    private static AttributeContentType contentTypeOf(String name) {
        try {
            return AttributeContentType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Groups the loaded content by object, then by source, then by field identifier. The query returns the rows of one
     * object together and in {@code item_order}, so appending in iteration order is what preserves the sequence a
     * multi-valued attribute was stored in.
     */
    private Map<UUID, Map<FilterFieldSource, Map<String, List<BaseAttributeContentV3<?>>>>> collectValues(
            List<ProjectedAttributeContent> stored, List<RequestedColumn> requested) {
        Map<UUID, Map<FilterFieldSource, Map<String, List<BaseAttributeContentV3<?>>>>> byObject = new LinkedHashMap<>();

        for (ProjectedAttributeContent content : stored) {
            // Encrypted content is ciphertext that only its own decryption path can read, and a listing does not take
            // that path. The catalogue withholds such fields; a row that reaches here anyway is dropped.
            if (content.encryptedContent() != null || WITHHELD_CONTENT_TYPES.contains(content.contentType())) {
                continue;
            }
            RequestedColumn column = matchRequested(requested, content);
            if (column == null) {
                continue;
            }
            byObject
                    .computeIfAbsent(content.objectUuid(), uuid -> new EnumMap<>(FilterFieldSource.class))
                    .computeIfAbsent(column.source(), source -> new LinkedHashMap<>())
                    .computeIfAbsent(column.fieldIdentifier(), identifier -> new ArrayList<>())
                    .add(AttributeVersionHelper
                            .convertAttributeContentToV3(content.contentItem(), content.contentType()));
        }

        return byObject;
    }

    /**
     * The requested column a stored row belongs to, or {@code null} when the row is not one that was asked for. The
     * query narrows by attribute name, which is not unique on its own: the same name may be registered under two
     * content types, and under more than one attribute type.
     */
    private static RequestedColumn matchRequested(List<RequestedColumn> requested, ProjectedAttributeContent content) {
        return requested
                .stream()
                .filter(column -> column.attributeType() == content.attributeType()
                        && column.attributeName().equals(content.attributeName())
                        && column.contentType() == content.contentType())
                .findFirst()
                .orElse(null);
    }

    /**
     * Sets the collected values on the entries they belong to. An entry with no value for any requested column is left
     * untouched rather than given an empty map, so an object that simply has none looks the same as it always did
     * instead of gaining an empty object in the response.
     *
     * <p>
     * Entries sharing a uuid share one map instance. The listing serializes it and discards it, so there is nothing to
     * copy it for.
     */
    private static <T extends AttributeProjectable> void applyToEntries(
            Map<UUID, Map<FilterFieldSource, Map<String, List<BaseAttributeContentV3<?>>>>> byObject,
            Map<UUID, List<T>> entriesByUuid) {
        byObject.forEach((objectUuid, values) -> {
            List<T> entries = entriesByUuid.get(objectUuid);
            if (entries == null || values.isEmpty()) {
                return;
            }
            entries.forEach(entry -> entry.setAttributeValues(values));
        });
    }
}
