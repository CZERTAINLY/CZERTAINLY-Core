package com.otilm.core.service.impl;

import com.otilm.api.exception.AlreadyExistException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.ValidationError;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.listview.ListViewColumnDto;
import com.otilm.api.model.core.listview.ListViewDto;
import com.otilm.api.model.core.listview.ListViewRequestDto;
import com.otilm.api.model.core.listview.ListViewUpdateRequestDto;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.dao.entity.ListView;
import com.otilm.core.dao.repository.ListViewRepository;
import com.otilm.core.enums.FilterField;
import com.otilm.core.security.authz.AnyPrincipalEndpoint;
import com.otilm.core.service.ListViewExternalService;
import com.otilm.core.service.ListViewInternalService;
import com.otilm.core.service.writer.ListViewWriter;
import com.otilm.core.util.AuthHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Saved list views. There is no OPA check on these operations: a view is addressed only through the user it belongs to,
 * so being authenticated as that user is the whole of the authorization. A view of another user answers 404 rather than
 * 403 - it is not addressable, not merely forbidden.
 */
@Service
public class ListViewServiceImpl implements ListViewExternalService, ListViewInternalService {

    private ListViewRepository listViewRepository;
    private ListViewWriter listViewWriter;
    private AttributeEngine attributeEngine;

    @Autowired
    public void setListViewRepository(ListViewRepository listViewRepository) {
        this.listViewRepository = listViewRepository;
    }

    @Autowired
    public void setListViewWriter(ListViewWriter listViewWriter) {
        this.listViewWriter = listViewWriter;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    @AnyPrincipalEndpoint
    public List<ListViewDto> listViews(Resource resource) {
        UUID userUuid = loggedUserUuid();
        List<ListView> views = resource == null
                ? listViewRepository.findByUserUuidOrderByNameAsc(userUuid)
                : listViewRepository.findByUserUuidAndResourceOrderByNameAsc(userUuid, resource);

        Map<Resource, Set<CatalogueField>> catalogues = new HashMap<>();
        return views.stream().map(view -> toDto(view, catalogues)).toList();
    }

    @Override
    @AnyPrincipalEndpoint
    public ListViewDto createView(ListViewRequestDto request) throws AlreadyExistException {
        UUID userUuid = loggedUserUuid();
        Resource resource = request.getResource();
        validateColumns(resource, request.getColumns());

        if (listViewRepository.existsByUserUuidAndResourceAndName(userUuid, resource, request.getName())) {
            throw new AlreadyExistException(ListView.class, request.getName());
        }

        ListView view = new ListView();
        view.setUserUuid(userUuid);
        view.setResource(resource);
        applyRequest(view, request);

        return toDto(listViewWriter.save(view), new HashMap<>());
    }

    @Override
    @AnyPrincipalEndpoint
    public ListViewDto editView(String uuid, ListViewUpdateRequestDto request)
            throws NotFoundException, AlreadyExistException {
        UUID userUuid = loggedUserUuid();
        ListView view = ownView(uuid, userUuid);
        validateColumns(view.getResource(), request.getColumns());

        if (listViewRepository
                .existsByUserUuidAndResourceAndNameAndUuidNot(userUuid, view.getResource(), request.getName(),
                        view.getUuid())) {
            throw new AlreadyExistException(ListView.class, request.getName());
        }

        applyRequest(view, request);

        return toDto(listViewWriter.save(view), new HashMap<>());
    }

    @Override
    @AnyPrincipalEndpoint
    public void deleteView(String uuid) throws NotFoundException {
        listViewWriter.delete(ownView(uuid, loggedUserUuid()).getUuid());
    }

    @Override
    public int deleteViewsOfUser(UUID userUuid) {
        return listViewWriter.deleteAllForUser(userUuid);
    }

    private static void applyRequest(ListView view, ListViewUpdateRequestDto request) {
        view.setName(request.getName());
        view.setColumns(request.getColumns());
        view.setDefaultView(request.isDefaultView());
        view.setFilters(request.getFilters() == null || request.getFilters().isEmpty() ? null : request.getFilters());
        view.setSort(request.getSort());
    }

    private ListView ownView(String uuid, UUID userUuid) throws NotFoundException {
        UUID viewUuid;
        try {
            viewUuid = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(ListView.class, uuid);
        }
        return listViewRepository
                .findByUuidAndUserUuid(viewUuid, userUuid)
                .orElseThrow(() -> new NotFoundException(ListView.class, uuid));
    }

    private static UUID loggedUserUuid() {
        return UUID.fromString(AuthHelper.getUserIdentification().getUuid());
    }

    private ListViewDto toDto(ListView view, Map<Resource, Set<CatalogueField>> catalogues) {
        Set<CatalogueField> catalogue = catalogues.computeIfAbsent(view.getResource(), this::catalogueOf);

        ListViewDto dto = new ListViewDto();
        dto.setUuid(view.getUuid().toString());
        dto.setName(view.getName());
        dto.setResource(view.getResource());
        dto.setDefaultView(view.isDefaultView());
        dto
                .setColumns(view
                        .getColumns()
                        .stream()
                        .filter(column -> catalogue.contains(CatalogueField.of(column)))
                        .toList());
        dto.setFilters(view.getFilters());
        dto.setSort(view.getSort());
        return dto;
    }

    /**
     * Rejects a column the resource's catalogue does not offer, so a view is stored only in a shape the listing can
     * actually apply. A field that disappears afterwards is a different case, and is skipped on read instead.
     */
    private void validateColumns(Resource resource, List<ListViewColumnDto> columns) {
        Set<CatalogueField> catalogue = catalogueOf(resource);
        if (catalogue.isEmpty()) {
            throw new ValidationException(ValidationError
                    .create("Resource %s has no field catalogue and cannot carry views."
                            .formatted(resource.getCode())));
        }

        Set<CatalogueField> seen = new LinkedHashSet<>();
        List<String> duplicated = columns
                .stream()
                .filter(column -> !seen.add(CatalogueField.of(column)))
                .map(ListViewColumnDto::getFieldIdentifier)
                .toList();
        if (!duplicated.isEmpty()) {
            throw new ValidationException(ValidationError
                    .create("A column can appear only once in a view: %s".formatted(String.join(", ", duplicated))));
        }

        List<String> unknown = columns
                .stream()
                .filter(column -> !catalogue.contains(CatalogueField.of(column)))
                .map(ListViewColumnDto::getFieldIdentifier)
                .toList();
        if (!unknown.isEmpty()) {
            throw new ValidationException(ValidationError
                    .create("Resource %s has no field %s.".formatted(resource.getCode(), String.join(", ", unknown))));
        }
    }

    /**
     * Every field the resource's listing can show, addressed the way a stored column addresses it. Properties come from
     * the filter-field enum, everything else from the attribute definitions currently registered for the resource.
     */
    private Set<CatalogueField> catalogueOf(Resource resource) {
        Set<CatalogueField> catalogue = new HashSet<>();
        FilterField
                .getEnumsForResource(resource)
                .forEach(field -> catalogue.add(new CatalogueField(FilterFieldSource.PROPERTY, field.name())));
        attributeEngine
                .getResourceSearchableFields(resource, false)
                .forEach(group -> group
                        .getSearchFieldData()
                        .forEach(field -> catalogue
                                .add(new CatalogueField(group.getFilterFieldSource(), field.getFieldIdentifier()))));
        return catalogue;
    }

    private record CatalogueField(FilterFieldSource fieldSource, String fieldIdentifier) {

        static CatalogueField of(ListViewColumnDto column) {
            return new CatalogueField(column.getFieldSource(), column.getFieldIdentifier());
        }
    }
}
