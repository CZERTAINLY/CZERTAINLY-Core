package com.czertainly.core.dao.repository;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
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
    public List<T> findUsingSecurityFilter(SecurityFilter filter, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause) {

        CriteriaQuery<T> cr = createCriteriaBuilder(filter, additionalWhereClause);
        return entityManager.createQuery(cr).getResultList();
    }

    @Override
    public List<T> findUsingSecurityFilter(SecurityFilter filter, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause, Pageable p) {
        CriteriaQuery<T> cr = createCriteriaBuilder(filter, additionalWhereClause);
        return entityManager.createQuery(cr).setFirstResult((int) p.getOffset()).setMaxResults(p.getPageSize()).getResultList();
    }

    @Override
    public Long countUsingSecurityFilter(SecurityFilter filter) {
        CriteriaQuery<Long> cr = createCountCriteriaBuilder(filter, null);
        return entityManager.createQuery(cr).getSingleResult();
    }


    private CriteriaQuery<T> createCriteriaBuilder(SecurityFilter filter, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause) {
        Class<T> entity = this.entityInformation.getJavaType();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cr = cb.createQuery(entity);
        Root<T> root = cr.from(entity);
        cr.select(root);
        return cr.where(getPredicates(filter, additionalWhereClause, root, cb).toArray(new Predicate[]{}));
    }

    private CriteriaQuery<Long> createCountCriteriaBuilder(SecurityFilter filter, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        Class<T> entity = this.entityInformation.getJavaType();
        CriteriaQuery<Long> cr = cb.createQuery(Long.class);
        Root<T> root = cr.from(entity);
        cr.select(cb.count(root));
        return cr.where(getPredicates(filter, additionalWhereClause, root, cb).toArray(new Predicate[]{}));
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
