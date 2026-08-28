package com.otilm.core.attribute.engine;

import com.otilm.api.model.client.certificate.SearchColumnRequestDto;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.content.data.FileAttributeContentData;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.search.AttributeProjectable;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.core.attribute.engine.AttributeEngine.CustomAttributeContentFilter;
import com.otilm.core.attribute.engine.records.ProjectedAttributeContent;
import com.otilm.core.dao.repository.AttributeContent2ObjectRepository;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
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
 * A request that names no attribute-sourced column leaves every listing entry unchanged.
 */
@Component
@RequiredArgsConstructor
public class AttributeColumnProjector {

    private static final String FIELD_IDENTIFIER_SEPARATOR = "|";

    /**
     * Content that is never projected, whatever a request asks for. The catalogue already marks these fields
     * undisplayable, so a well-behaved caller cannot reach them; this is the second lock, because the first one is a
     * flag on a response the caller is free to ignore.
     */
    private static final Set<AttributeContentType> WITHHELD_CONTENT_TYPES = Set
            .of(AttributeContentType.SECRET, AttributeContentType.CODEBLOCK);

    private final AttributeContent2ObjectRepository attributeContent2ObjectRepository;
    private final AttributeEngine attributeEngine;

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
        project(resource, columns, entries, uuidOf, uuidOf);
    }

    /**
     * As above, for a listing whose metadata is stored against a different object than its other attributes.
     *
     * @param metadataUuidOf the uuid metadata content is stored against. A key's own attributes hang off the key while
     * its metadata hangs off each key item, so one resolver cannot serve both. Passing the same resolver twice - which
     * the overload above does - keeps the whole page in one query.
     */
    public <T extends AttributeProjectable> void project(Resource resource, List<SearchColumnRequestDto> columns,
            List<T> entries, Function<T, UUID> uuidOf, Function<T, UUID> metadataUuidOf) {
        if (columns == null || columns.isEmpty() || entries == null || entries.isEmpty()) {
            return;
        }

        List<RequestedColumn> requested = parseRequestedColumns(columns);
        if (requested.isEmpty()) {
            return;
        }

        // Grouping by the resolver rather than branching on whether the two differ: when they are the same reference
        // the two lists land in one entry, so the common case still issues a single query.
        Map<Function<T, UUID>, List<RequestedColumn>> byResolver = new LinkedHashMap<>();
        byResolver.computeIfAbsent(uuidOf, resolver -> new ArrayList<>()).addAll(columnsOtherThanMetadata(requested));
        byResolver.computeIfAbsent(metadataUuidOf, resolver -> new ArrayList<>()).addAll(metadataColumns(requested));

        CustomAttributeContentFilter contentFilter = attributeEngine.loadCustomAttributeContentFilter();

        // Keyed by identity: a listing DTO may implement equals, and two equal entries are still two rows that each
        // need their own values.
        Map<T, Map<FilterFieldSource, Map<String, List<BaseAttributeContentV3<?>>>>> collected = new IdentityHashMap<>();

        byResolver.forEach((resolver, resolverColumns) -> {
            if (!resolverColumns.isEmpty()) {
                collectPass(resource, resolverColumns, entries, resolver, contentFilter, collected);
            }
        });

        // Set once per entry, after every pass, so a second pass adds to the first rather than replacing it.
        collected.forEach((entry, values) -> {
            if (!values.isEmpty()) {
                entry.setAttributeValues(values);
            }
        });
    }

    private static List<RequestedColumn> metadataColumns(List<RequestedColumn> requested) {
        return requested.stream().filter(column -> column.attributeType() == AttributeType.META).toList();
    }

    private static List<RequestedColumn> columnsOtherThanMetadata(List<RequestedColumn> requested) {
        return requested.stream().filter(column -> column.attributeType() != AttributeType.META).toList();
    }

    /** Loads and collects the columns that share one uuid resolver, adding what it finds to {@code collected}. */
    private <T extends AttributeProjectable> void collectPass(Resource resource, List<RequestedColumn> requested,
            List<T> entries, Function<T, UUID> uuidOf, CustomAttributeContentFilter contentFilter,
            Map<T, Map<FilterFieldSource, Map<String, List<BaseAttributeContentV3<?>>>>> collected) {
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
                        requested.stream().map(RequestedColumn::attributeName).distinct().toList(),
                        AttributeType.CUSTOM, contentFilter.allowedDefinitionUuids(),
                        contentFilter.forbiddenDefinitionUuids());

        collectValues(stored, requested).forEach((objectUuid, values) -> {
            List<T> matching = entriesByUuid.get(objectUuid);
            if (matching == null || values.isEmpty()) {
                return;
            }
            matching
                    .forEach(entry -> collected
                            .computeIfAbsent(entry, key -> new EnumMap<>(FilterFieldSource.class))
                            .putAll(values));
        });
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
                    .add(toProjectedValue(
                            AttributeVersionHelper
                                    .convertAttributeContentToV3(content.contentItem(), content.contentType()),
                            content.contentType()));
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
     * One value reduced to what a list cell renders. Two content types are never projected whole.
     *
     * <p>
     * CREDENTIAL data is a nested attribute list that can carry secret-bearing content, and the cell shows the
     * reference, so only the reference is projected. FILE data carries the base64 body, which no cell shows - a file
     * renders as its name and media type - so a page of rows would otherwise serialize every file it lists.
     */
    static BaseAttributeContentV3<?> toProjectedValue(BaseAttributeContentV3<?> value,
            AttributeContentType contentType) {
        if (contentType == AttributeContentType.CREDENTIAL) {
            return referenceOnly(value, contentType);
        }
        if (contentType == AttributeContentType.FILE && value.getData() instanceof FileAttributeContentData file) {
            FileAttributeContentData withoutBody = new FileAttributeContentData();
            withoutBody.setFileName(file.getFileName());
            withoutBody.setMimeType(file.getMimeType());

            BaseAttributeContentV3<Serializable> reduced = referenceOnly(value, contentType);
            reduced.setData(withoutBody);
            return reduced;
        }
        return value;
    }

    private static BaseAttributeContentV3<Serializable> referenceOnly(BaseAttributeContentV3<?> value,
            AttributeContentType contentType) {
        BaseAttributeContentV3<Serializable> reduced = new BaseAttributeContentV3<>();
        reduced.setContentType(contentType);
        reduced.setReference(value.getReference());
        return reduced;
    }
}
