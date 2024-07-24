package com.czertainly.core.dao;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.security.authz.SecurityResourceFilter;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Specifications {

    public static <TEntity> Specification<TEntity> countBy(String groupByColumn, Specification<TEntity> spec) {
        return (root, query, builder) -> {
            query.groupBy(root.get(groupByColumn));
            query.multiselect(root.get(groupByColumn), builder.countDistinct(root));
            return spec.toPredicate(root, query, builder);
        };
    }

    public static <TEntity> Specification<TEntity> hasObjectAccess(SecurityFilter filter, Predicate additionalWhereClause) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (additionalWhereClause != null) {
                predicates.add(additionalWhereClause);
            }

            if (filter.getParentResourceFilter() != null && filter.getParentRefProperty() == null) {
                throw new ValidationException(ValidationError.create("Unknown parent ref property to filter by parent resource " + filter.getParentResourceFilter().getResource()));
            }

            List<Predicate> combinedObjectAccessPredicates = new ArrayList<>();
            Predicate resourceFilterPredicate = getPredicateBySecurityResourceFilter(root, filter.getResourceFilter(), "uuid");
            Predicate parentResourceFilterPredicate = getPredicateBySecurityResourceFilter(root, filter.getParentResourceFilter(), filter.getParentRefProperty());

            // no predicates from security filter means user can retrieve all objects and it is not necessary to evaluate groups and owner associations
            if (resourceFilterPredicate == null && parentResourceFilterPredicate == null) {
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }

            combinedObjectAccessPredicates.add(resourceFilterPredicate != null && parentResourceFilterPredicate != null ? criteriaBuilder.and(resourceFilterPredicate, parentResourceFilterPredicate) : (resourceFilterPredicate != null ? resourceFilterPredicate : parentResourceFilterPredicate));
            if (filter.getResourceFilter() != null) {
                // check for group membership predicate
                if (filter.getResourceFilter().getResource().hasGroups()
                        && (filter.getResourceFilter().getResourceAction() == ResourceAction.LIST || filter.getResourceFilter().getResourceAction() == ResourceAction.DETAIL)) {
                    combinedObjectAccessPredicates.add(getPredicateBySecurityResourceFilter(root, filter.getGroupMembersFilter(), "groups.uuid"));
                }
                // check for owner association predicate
                if (filter.getResourceFilter().getResource().hasOwner()) {
                    try {
                        NameAndUuidDto userInformation = AuthHelper.getUserIdentification();
                        String ownerAttributeName = root.getJavaType().equals(CryptographicKeyItem.class) ? "cryptographicKey.owner.ownerUsername" : "owner.ownerUsername";
                        combinedObjectAccessPredicates.add(criteriaBuilder.equal(Sql2PredicateConverter.prepareExpression(root, ownerAttributeName), userInformation.getName()));
                    } catch (ValidationException e) {
                        // cannot apply filter predicate for anonymous user
                    }
                }
            }

            combinedObjectAccessPredicates = combinedObjectAccessPredicates.stream().filter(Objects::nonNull).toList();

            if (!combinedObjectAccessPredicates.isEmpty()) {
                predicates.add(combinedObjectAccessPredicates.size() == 1 ? combinedObjectAccessPredicates.get(0) : criteriaBuilder.or(combinedObjectAccessPredicates.toArray(new Predicate[0])));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <TEntity> Predicate getPredicateBySecurityResourceFilter(Root<TEntity> root, SecurityResourceFilter resourceFilter, String attributeName) {
        Predicate predicate = null;
        if (root.getJavaType().equals(CryptographicKeyItem.class)) {
            attributeName = "cryptographicKey." + attributeName;
        }

        if (resourceFilter != null) {
            From from = root;
            if (attributeName.contains(".")) {
                from = Sql2PredicateConverter.prepareJoin(root, attributeName.substring(0, attributeName.lastIndexOf(".")));
                attributeName = attributeName.substring(attributeName.lastIndexOf(".") + 1);
            }
            if (resourceFilter.areOnlySpecificObjectsAllowed()) {
                predicate = Sql2PredicateConverter.prepareExpression(from, attributeName).in(resourceFilter.getAllowedObjects());
            } else {
                if (!resourceFilter.getForbiddenObjects().isEmpty()) {
                    predicate = Sql2PredicateConverter.prepareExpression(from, attributeName).in(resourceFilter.getForbiddenObjects()).not();
                }
            }
        }
        return predicate;
    }

}
