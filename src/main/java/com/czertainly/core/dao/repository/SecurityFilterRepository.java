package com.czertainly.core.dao.repository;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

@NoRepositoryBean
public interface SecurityFilterRepository<T, ID> extends JpaRepository<T, ID> {

    Optional<T> findByUuid(SecuredUUID uuid);

    Optional<T> findByUuid(SecuredUUID uuid, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause);

    List<T> findUsingSecurityFilter(SecurityFilter filter);

    List<T> findUsingSecurityFilter(SecurityFilter filter, boolean enabled);

    List<T> findUsingSecurityFilter(SecurityFilter filter, List<String> fetchAssociations, TriFunction<Root<T>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause);

    List<T> findUsingSecurityFilter(SecurityFilter filter, List<String> fetchAssociations, TriFunction<Root<T>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause, Pageable p, BiFunction<Root<T>, CriteriaBuilder, Order> order);

    List<UUID> findUuidsUsingSecurityFilter(SecurityFilter filter, TriFunction<Root<T>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause, Pageable p, BiFunction<Root<T>, CriteriaBuilder, Order> order);

    Map<String, Long> countGroupedUsingSecurityFilter(SecurityFilter filter, Attribute join, SingularAttribute groupBy, BiFunction<Root<T>, CriteriaBuilder, Expression> groupByExpression, TriFunction<Root<T>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause);

    Long countUsingSecurityFilter(SecurityFilter filter);

    Long countUsingSecurityFilter(SecurityFilter filter, TriFunction<Root<T>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause);

    int deleteUsingSecurityFilter(SecurityFilter filter, TriFunction<Root<T>, CriteriaBuilder, CriteriaDelete<T>, Predicate> additionalWhereClause);

    List<NameAndUuidDto> listResourceObjects(SecurityFilter securityFilter, SingularAttribute<T, String> nameAttribute);

    List<NameAndUuidDto> listResourceObjects(SecurityFilter securityFilter, SingularAttribute<T, String> nameAttribute, TriFunction<Root<T>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause);

    NameAndUuidDto findResourceObject(UUID uuid, SingularAttribute<T, String> nameAttribute) throws NotFoundException;
}
