package com.czertainly.core.dao.repository;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public class SecurityFilterRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> implements SecurityFilterRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager entityManager;

    public SecurityFilterRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
    }

    public Optional<T> findByUuid(SecuredUUID uuid) {
        return findByUuid(uuid, null);
    }

    @Override
    public Optional<T> findByUuid(SecuredUUID uuid, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause) {
        try {
            Class<T> entity = this.entityInformation.getJavaType();
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cr = cb.createQuery(entity);
            Root<T> root = cr.from(entity);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("uuid"), uuid.getValue()));

            if (additionalWhereClause != null) {
                predicates.add(additionalWhereClause.apply(root, cb));
            }
            cr.select(root).where(predicates.toArray(new Predicate[]{}));
            T result = entityManager.createQuery(cr).getSingleResult();
            return Optional.of(result);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<T> findUsingSecurityFilter(SecurityFilter filter) {
        return this.findUsingSecurityFilter(filter, null);
    }

    @Override
    public List<T> findUsingSecurityFilter(SecurityFilter filter, boolean enabled) {
        return findUsingSecurityFilter(filter, (root, cb) -> cb.equal(root.get("enabled"), enabled));
    }

    @Override
    public List<T> findUsingSecurityFilterAndType(SecurityFilter filter, AttributeType type) {
        return findUsingSecurityFilter(filter, (root, cb) -> cb.equal(root.get("type"), type));
    }

    @Override
    public List<T> findUsingSecurityFilter(SecurityFilter filter, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause) {

        CriteriaQuery<T> cr = createCriteriaBuilder(filter, additionalWhereClause, null);
        return entityManager.createQuery(cr).getResultList();
    }

    @Override
    public List<T> findUsingSecurityFilter(SecurityFilter filter, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause, Pageable p, BiFunction<Root<T>, CriteriaBuilder, Order> order) {
        CriteriaQuery<T> cr = createCriteriaBuilder(filter, additionalWhereClause, order);
        if(p != null) {
            return entityManager.createQuery(cr).setFirstResult((int) p.getOffset()).setMaxResults(p.getPageSize()).getResultList();
        } else {
            return entityManager.createQuery(cr).getResultList();
        }
    }

    @Override
    public Long countUsingSecurityFilter(SecurityFilter filter) {
        CriteriaQuery<Long> cr = createCountCriteriaBuilder(filter, null);
        List<Long> crlist = entityManager.createQuery(cr).getResultList();
        return crlist.get(0);
    }


    private CriteriaQuery<T> createCriteriaBuilder(final SecurityFilter filter, final BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause, BiFunction<Root<T>, CriteriaBuilder, Order> order) {
        final Class<T> entity = this.entityInformation.getJavaType();
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<T> cr = cb.createQuery(entity);
        final Root<T> root = cr.from(entity);
        cr.select(root);
        if(order != null){
            cr.orderBy(order.apply(root, cb));
        }
        final List<Predicate> predicates = getPredicates(filter, additionalWhereClause, root, cb);
        return predicates.isEmpty() ? cr : cr.where(predicates.toArray(new Predicate[]{}));
    }

    private CriteriaQuery<Long> createCountCriteriaBuilder(final SecurityFilter filter, final BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause) {
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final Class<T> entity = this.entityInformation.getJavaType();
        final CriteriaQuery<Long> cr = cb.createQuery(Long.class);
        final Root<T> root = cr.from(entity);
        cr.select(cb.count(root));
        final List<Predicate> predicates = getPredicates(filter, additionalWhereClause, root, cb);
        return predicates.isEmpty() ? cr : cr.where(predicates.toArray(new Predicate[]{}));
    }

    private List<Predicate> getPredicates(SecurityFilter filter, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause, Root<T> root, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        if (additionalWhereClause != null) {
            predicates.add(additionalWhereClause.apply(root, cb));
        }

        if (filter.getResourceFilter().areOnlySpecificObjectsAllowed()) {
            predicates.add(root.get("uuid").in(filter.getResourceFilter().getAllowedObjects()));
        } else {
            if (!filter.getResourceFilter().getForbiddenObjects().isEmpty()) {
                predicates.add(root.get("uuid").in(filter.getResourceFilter().getForbiddenObjects()).not());
            }
        }

        if (filter.getParentResourceFilter() != null) {
            if (filter.getParentRefProperty() == null)
                throw new ValidationException("Unknown parent ref property to filter by parent resource " + filter.getParentResourceFilter().getResource());

            if (filter.getParentResourceFilter().areOnlySpecificObjectsAllowed()) {
                predicates.add(root.get(filter.getParentRefProperty()).in(filter.getParentResourceFilter().getAllowedObjects()));
            } else {
                if (!filter.getParentResourceFilter().getForbiddenObjects().isEmpty()) {
                    predicates.add(root.get(filter.getParentRefProperty()).in(filter.getParentResourceFilter().getForbiddenObjects()).not());
                }
            }
        }
        return predicates;
    }
}
