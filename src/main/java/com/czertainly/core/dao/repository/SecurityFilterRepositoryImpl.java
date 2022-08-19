package com.czertainly.core.dao.repository;

import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
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
            cr.select(root).where(cb.equal(root.get("uuid"), uuid.toString()));

            if (additionalWhereClause != null) {
                cr.where(additionalWhereClause.apply(root, cb));
            }

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
        Class<T> entity = this.entityInformation.getJavaType();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cr = cb.createQuery(entity);
        Root<T> root = cr.from(entity);
        cr.select(root);

        if (additionalWhereClause != null) {
            cr.where(additionalWhereClause.apply(root, cb));
        }

        if (filter.areOnlySpecificObjectsAllowed()) {
            cr.where(root.get("uuid").in(filter.getAllowedObjects()));
        } else {
            if (!filter.getForbiddenObjects().isEmpty()) {
                cr.where(root.get("uuid").in(filter.getForbiddenObjects()).not());
            }
        }
        return entityManager.createQuery(cr).getResultList();
    }
}
