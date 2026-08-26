package com.otilm.core.dao.entity;

import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.listview.ListViewColumnDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A named set of columns, filters and ordering that one user has saved for one listing.
 *
 * <p>
 * The owning user is a bare UUID with no foreign key: users live in the identity service, not in this database, which
 * is the same arrangement {@code scheduled_job}, {@code notification_recipient} and {@code approval_recipient} already
 * use. Rows are removed when the user is deleted through
 * {@link com.otilm.core.service.UserManagementExternalService#deleteUser(String)}.
 *
 * <p>
 * Columns, filters and ordering are stored as the request shapes the API accepts, and column identifiers are resolved
 * against the resource's live field catalogue on read - a renamed or deleted attribute drops out of the view instead of
 * requiring stored rows to be migrated.
 */
@Setter
@Getter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "list_view", uniqueConstraints = @UniqueConstraint(name = "uk_list_view_user_resource_name",
        columnNames = {"user_uuid", "resource", "name"}))
public class ListView extends UniquelyIdentifiedAndAudited {

    @Column(name = "user_uuid", nullable = false, updatable = false)
    private UUID userUuid;

    @Column(name = "resource", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "default_view", nullable = false)
    private boolean defaultView;

    // S1948: entities are Serializable through UniquelyIdentifiedObject, but nothing Java-serializes them - Jackson
    // owns the persistence shape of these JSONB fields.
    @SuppressWarnings("java:S1948")
    @Column(name = "columns", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ListViewColumnDto> columns;

    @SuppressWarnings("java:S1948")
    @Column(name = "filters", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<SearchFilterRequestDto> filters;

    @SuppressWarnings("java:S1948")
    @Column(name = "sort", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private SearchSortRequestDto sort;
}
