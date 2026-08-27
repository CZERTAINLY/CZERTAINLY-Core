package com.otilm.core.util;

import com.otilm.api.exception.ValidationError;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SortDirection;
import com.otilm.core.dao.entity.CryptographicKeyItem;
import com.otilm.core.dao.entity.UniquelyIdentified_;
import com.otilm.core.dao.repository.SortSpecification;
import com.otilm.core.enums.FilterField;
import com.otilm.core.enums.ResourceToClass;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a {@link SortSpecification} into the {@link Order} terms of a secured search.
 */
public final class SortOrderBuilder {

    private SortOrderBuilder() {
    }

    /**
     * The ordering of a query that groups by the root's uuid: the aggregate that carries the requested sort value of
     * one root, and the terms to pass to {@code orderBy}. A sort reached through a join has as many values per root as
     * the join has matches, so the query cannot order by the value itself - it orders by the least of them when
     * ascending and the greatest when descending, which is the value that decides where the root belongs.
     */
    public record GroupedOrdering(Expression<?> sortKey, List<Order> orders) {
    }

    /**
     * Whether applying the sort means joining away from the root. Answerable from the specification alone, so a caller
     * can pick the shape of the query before it has a root to resolve against.
     */
    public static boolean traversesJoin(SortSpecification sort) {
        return sort != null && !resolveField(sort).getJoinAttributes().isEmpty();
    }

    /**
     * Resolves the ordering of a query that returns one row per root. The requested sort wins over the caller's
     * default; a uuid tie-break is appended whenever anything is ordered or paged, because a single ORDER BY term over
     * a non-unique column leaves the row order inside a group of equal values up to the database, and paging then walks
     * an unstable set.
     *
     * @param defaultOrder the ordering the caller applies when the request names none, or {@code null} for no default
     * @param paged whether the query will be windowed, which is what makes an unstable order observable
     */
    public static List<Order> resolve(Root<?> root, CriteriaBuilder criteriaBuilder, SortSpecification sort,
            Order defaultOrder, boolean paged) {
        List<Order> orders = new ArrayList<>();
        if (sort != null) {
            FilterField field = resolveField(root, sort);
            orders.add(direct(criteriaBuilder, resolveExpression(root, field), sort.direction()));
        } else if (defaultOrder != null) {
            orders.add(defaultOrder);
        }

        if (!orders.isEmpty() || paged) {
            tieBreak(root, criteriaBuilder).ifPresent(orders::add);
        }

        return List.copyOf(orders);
    }

    /**
     * Resolves the ordering of a query grouped by the root's uuid. Only a sort that traverses a join needs it, and such
     * a sort is only offered on a resource keyed by a uuid, so a root without one is a caller error rather than a
     * request the ordering has to degrade for.
     */
    public static GroupedOrdering resolveGrouped(Root<?> root, CriteriaBuilder criteriaBuilder,
            SortSpecification sort) {
        FilterField field = resolveField(root, sort);
        Expression<?> sortKey = aggregate(criteriaBuilder, resolveExpression(root, field), sort.direction());

        Order tieBreak = tieBreak(root, criteriaBuilder)
                .orElseThrow(() -> new ValidationException(
                        ValidationError.create("Field %s cannot be sorted on this resource.".formatted(field.name()))));

        return new GroupedOrdering(sortKey, List.of(direct(criteriaBuilder, sortKey, sort.direction()), tieBreak));
    }

    private static Order direct(CriteriaBuilder criteriaBuilder, Expression<?> expression, SortDirection direction) {
        return direction == SortDirection.DESC ? criteriaBuilder.desc(expression) : criteriaBuilder.asc(expression);
    }

    /**
     * The value of the sort expression that decides where a root belongs among the rows the join multiplied it into:
     * the least of them when ascending, the greatest when descending.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Expression<?> aggregate(CriteriaBuilder criteriaBuilder, Expression<?> expression,
            SortDirection direction) {
        return direction == SortDirection.DESC
                ? criteriaBuilder.greatest((Expression) expression)
                : criteriaBuilder.least((Expression) expression);
    }

    private static Expression<?> resolveExpression(Root<?> root, FilterField field) {
        String path = FilterPredicatesBuilder.buildPathToProperty(field.getJoinAttributes(), field.getFieldAttribute());
        try {
            if (!path.contains(".")) {
                return FilterPredicatesBuilder.prepareExpression(root, path);
            }
            Join<?, ?> join = FilterPredicatesBuilder.prepareJoin(root, path.substring(0, path.lastIndexOf('.')));
            return FilterPredicatesBuilder.prepareExpression(join, path.substring(path.lastIndexOf('.') + 1));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    ValidationError.create("Field %s cannot be sorted on this resource.".formatted(field.name())));
        }
    }

    private static FilterField resolveField(SortSpecification sort) {
        if (sort.fieldSource() != FilterFieldSource.PROPERTY) {
            throw new ValidationException(ValidationError
                    .create("Sorting by %s fields is not supported.".formatted(sort.fieldSource().getLabel())));
        }
        try {
            return FilterField.valueOf(sort.fieldIdentifier());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    ValidationError.create("Unknown sort field identifier %s.".formatted(sort.fieldIdentifier())));
        }
    }

    /**
     * The field the identifier names, rejected unless the queried resource is the one that declares it. The identifier
     * is unique across every resource, but the attribute path behind it need not be: a field declared on a mapped
     * superclass - {@code Audited.created}, say - resolves against any entity that inherits it, so path resolution
     * alone would let a field of another resource through.
     */
    private static FilterField resolveField(Root<?> root, SortSpecification sort) {
        FilterField field = resolveField(sort);
        Resource resource = resourceOf(root);
        if (resource != null && field.getRootResource() != resource) {
            throw new ValidationException(ValidationError
                    .create("Field %s does not belong to resource %s.".formatted(field.name(), resource.getLabel())));
        }
        return field;
    }

    /**
     * The resource the queried entity belongs to, or {@code null} for an entity that is not a resource of its own and
     * so has no field catalogue to check an identifier against. Key items carry the fields of the key they belong to,
     * the same exception the attribute filter path makes.
     */
    private static Resource resourceOf(Root<?> root) {
        return root.getJavaType().equals(CryptographicKeyItem.class)
                ? Resource.CRYPTOGRAPHIC_KEY
                : ResourceToClass.getResourceByClass(root.getJavaType());
    }

    /**
     * The uuid of the queried entity, when it has one. Entities keyed by a generated id instead - the audit log - have
     * no uuid to break ties with, and keep whatever order their own default term produces.
     */
    private static Optional<Order> tieBreak(Root<?> root, CriteriaBuilder criteriaBuilder) {
        try {
            root.getModel().getSingularAttribute(UniquelyIdentified_.UUID);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(criteriaBuilder.asc(root.get(UniquelyIdentified_.UUID)));
    }
}
