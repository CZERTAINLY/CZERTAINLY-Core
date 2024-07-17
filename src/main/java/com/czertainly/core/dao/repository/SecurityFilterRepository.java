package com.czertainly.core.dao.repository;

import com.czertainly.core.model.AggregateResultDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@NoRepositoryBean
public interface SecurityFilterRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    Optional<T> findByUuid(SecuredUUID uuid);

    Optional<T> findByUuid(SecuredUUID uuid, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause);

    List<T> findUsingSecurityFilter(SecurityFilter filter);

    List<T> findUsingSecurityFilter(SecurityFilter filter, boolean enabled);

    List<T> findUsingSecurityFilter(SecurityFilter filter, List<String> fetchAssociations, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause);

    List<T> findUsingSecurityFilter(SecurityFilter filter, List<String> fetchAssociations, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause, Pageable p, BiFunction<Root<T>, CriteriaBuilder, Order> order);

    Map<String, Number> findAggregateUsingSecurityFilter(SecurityFilter filter, List<String> fetchAssociations, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause, String groupByColumn, BiFunction<Root<T>, CriteriaBuilder, Expression> aggregateFunction);

    Map<String, Number> findAggregateTest(Root root, CriteriaBuilder cb, CriteriaQuery<AggregateResultDto> cr, Predicate objectAccessPredicate, Predicate additionalWhereClause, Map<String, From> joinedAssociations, Attribute join, SingularAttribute groupBy, Expression groupByExpression, BiFunction<Root<T>, CriteriaBuilder, Expression> aggregateFunction);

    Long countUsingSecurityFilter(SecurityFilter filter);

    Long countUsingSecurityFilter(SecurityFilter filter, BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause);

    List<T> findUsingSecurityFilterByCustomCriteriaQuery(SecurityFilter filter, Root<T> root, CriteriaQuery<T> criteriaQuery, Predicate customPredicates);
}
