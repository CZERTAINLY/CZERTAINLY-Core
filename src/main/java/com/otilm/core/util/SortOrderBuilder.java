package com.otilm.core.util;

import com.otilm.api.exception.ValidationError;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SortDirection;
import com.otilm.core.dao.entity.UniquelyIdentified_;
import com.otilm.core.dao.repository.SortSpecification;
import com.otilm.core.enums.FilterField;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a {@link SortSpecification} into the {@link Order} terms of a secured search, and reports which of those terms
 * the query has to carry in its select list.
 */
public final class SortOrderBuilder {

    private SortOrderBuilder() {
    }

    /**
     * The ordering of one query: the terms to pass to {@code orderBy}, and the subset of their expressions that a
     * {@code SELECT DISTINCT} has to also select. PostgreSQL rejects an ORDER BY expression that is absent from the
     * select list of a DISTINCT query, and every expression reached through a join is such an expression.
     */
    public record Ordering(List<Order> orders, List<Expression<?>> expressionsToSelect) {

        public boolean isEmpty() {
            return orders.isEmpty();
        }
    }

    /**
     * Whether applying the sort means joining away from the root. Answerable from the specification alone, so a caller
     * can pick the shape of the query before it has a root to resolve against.
     */
    public static boolean traversesJoin(SortSpecification sort) {
        return sort != null && !resolveField(sort).getJoinAttributes().isEmpty();
    }

    /**
     * Resolves the ordering of a query. The requested sort wins over the caller's default; a uuid tie-break is appended
     * whenever anything is ordered or paged, because a single ORDER BY term over a non-unique column leaves the row
     * order inside a group of equal values up to the database, and paging then walks an unstable set.
     *
     * @param defaultOrder the ordering the caller applies when the request names none, or {@code null} for no default
     * @param paged whether the query will be windowed, which is what makes an unstable order observable
     */
    public static Ordering resolve(Root<?> root, CriteriaBuilder criteriaBuilder, SortSpecification sort,
            Order defaultOrder, boolean paged) {
        List<Order> orders = new ArrayList<>();
        if (sort != null) {
            orders.add(toOrder(root, criteriaBuilder, sort));
        } else if (defaultOrder != null) {
            orders.add(defaultOrder);
        }

        if (!orders.isEmpty() || paged) {
            tieBreak(root, criteriaBuilder).ifPresent(orders::add);
        }

        List<Expression<?>> expressionsToSelect = orders
                .stream()
                .map(Order::getExpression)
                .filter(expression -> !isRootPath(expression, root))
                .toList();

        return new Ordering(List.copyOf(orders), expressionsToSelect);
    }

    private static Order toOrder(Root<?> root, CriteriaBuilder criteriaBuilder, SortSpecification sort) {
        FilterField field = resolveField(sort);
        Expression<?> expression = resolveExpression(root, field);
        return sort.direction() == SortDirection.DESC
                ? criteriaBuilder.desc(expression)
                : criteriaBuilder.asc(expression);
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

    /**
     * Whether the expression is reachable from the root without crossing a join, and so is already covered by selecting
     * the root itself.
     */
    private static boolean isRootPath(Expression<?> expression, Root<?> root) {
        if (!(expression instanceof Path<?> path)) {
            return false;
        }
        for (Path<?> parent = path.getParentPath(); parent != null; parent = parent.getParentPath()) {
            if (parent == root) {
                return true;
            }
            if (parent instanceof Join<?, ?>) {
                return false;
            }
        }
        return false;
    }
}
