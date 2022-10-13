package com.czertainly.core.security.authz;

import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.AnonymousPrincipal;
import com.czertainly.core.security.authz.opa.dto.OpaRequestDetails;
import com.czertainly.core.security.authz.opa.dto.OpaRequestedResource;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ExternalMethodAuthorizationVoter extends AbstractExternalAuthorizationVoter<MethodInvocation> {

    protected final Log logger = LogFactory.getLog(this.getClass());

    private final OpaClient opaClient;

    private final ObjectMapper om;

    public ExternalMethodAuthorizationVoter(@Autowired OpaClient opaClient, @Autowired ObjectMapper om) {
        this.opaClient = opaClient;
        this.om = om;
    }

    @Override
    public boolean supports(Class clazz) {
        return MethodInvocation.class.isAssignableFrom(clazz);
    }

    @Override
    protected int voteInternal(CzertainlyAuthenticationToken auth, MethodInvocation methodInvocation, List<ExternalAuthorizationConfigAttribute> attributes) {
        return this.vote(auth.getPrincipal().getRawData(), methodInvocation, attributes);
    }

    @Override
    protected int voteInternal(AnonymousAuthenticationToken authenticationToken, MethodInvocation methodInvocation, List<ExternalAuthorizationConfigAttribute> attributes) {
        try {
            return this.vote(om.writeValueAsString(new AnonymousPrincipal(authenticationToken.getName())), methodInvocation, attributes);
        } catch (JsonProcessingException e) {
            logger.error("An error occurred during voting. Access will be denied.", e);
            return ACCESS_DENIED;
        }
    }

    private int vote(String principal, MethodInvocation methodInvocation, List<ExternalAuthorizationConfigAttribute> attributes) {
        try {
            Map<String, String> properties = attributes
                    .stream()
                    .filter(this::shouldBeSendToOpa)
                    .collect(Collectors.toMap(ExternalAuthorizationConfigAttribute::getAttributeName, ExternalAuthorizationConfigAttribute::getAttributeValueAsString));

            Optional<ParentUUIDGetter> parentUUIDGetter = getParentUUIDGetter(attributes);
            if(!properties.get("parentName").equals(Resource.NONE.getCode())) {
                int result = voteResource(principal, methodInvocation, properties, parentUUIDGetter, true);
                if(result == ACCESS_DENIED){
                    setDeniedResource(properties.get("parentName"));
                    setDeniedResourceAction(properties.get("parentAction"));
                    return result;
                }
            }

            return voteResource(principal, methodInvocation, properties, parentUUIDGetter, false);
        } catch (Exception e) {
            logger.error(String.format("Unable verify access to the method '%s'. Voting to deny access.", methodInvocation.getMethod().getName()), e);
            return ACCESS_DENIED;
        }
    }

    private int voteResource(String principal, MethodInvocation methodInvocation, Map<String, String> properties, Optional<ParentUUIDGetter> parentUUIDGetter, boolean parentResource) {
        Map<String, String> voteProperties = properties;

        if(parentResource) {
            Map<String, String> parentProperties = properties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            parentProperties.put("name", properties.get("parentName"));
            parentProperties.put("action", properties.get("parentAction"));
            voteProperties = parentProperties;
        }
        voteProperties.remove("parentName");
        voteProperties.remove("parentAction");

        OpaRequestedResource resource = new OpaRequestedResource(voteProperties);

        // UUIDs of objects the operation is executed on
        List<SecuredUUID> objectUUIDs = parentResource ? extractParentUUIDsFromMethodArguments(methodInvocation) : extractUUIDsFromMethodArguments(methodInvocation);

        // Parent UUID Getter not used for now, remove later
        if(!parentResource) {
            if (parentUUIDGetter.isPresent()) {
                if (objectUUIDs.isEmpty()) {
                    logger.error("ParentUUIDGetter specified but no object uuids were found. Access will be denied.");
                    setDeniedResource(voteProperties.get("name"));
                    setDeniedResourceAction(voteProperties.get("action"));
                    return ACCESS_DENIED;
                } else {
                    List<String> parentsUUIDs = parentUUIDGetter.get().getParentsUUID(
                            objectUUIDs.stream()
                                    .map(SecuredUUID::toString)
                                    .collect(Collectors.toList())
                    );
                    resource.setParentObjectUUIDs(parentsUUIDs);
                }
            }
        }

        if (!objectUUIDs.isEmpty()) {
            List<String> uuids = objectUUIDs.stream()
                    .map(SecuredUUID::toString)
                    .collect(Collectors.toList());
            resource.setObjectUUIDs(uuids);
        }

        OpaResourceAccessResult result = this.checkAccess(principal, resource);
        if (result.isAuthorized()) {
            logger.trace(
                    String.format(
                            "Access to the method '%s' has been granted by the following rules [%s].",
                            methodInvocation.getMethod().getName(),
                            String.join(",", result.getAllow())
                    )
            );
            return ACCESS_GRANTED;
        } else {
            logger.trace(String.format("Access to the method '%s' has been denied.", methodInvocation.getMethod().getName()));
            setDeniedResource(voteProperties.get("name"));
            setDeniedResourceAction(voteProperties.get("action"));
            return ACCESS_DENIED;
        }
    }

    @Override
    protected boolean canDecide(Authentication auth, MethodInvocation object) {
        return true;
    }

    protected OpaResourceAccessResult checkAccess(String principal, OpaRequestedResource resource) {
        try {
            return this.opaClient.checkResourceAccess(OpaPolicy.METHOD.policyName, resource, principal, new OpaRequestDetails(null));
        } catch (Exception e) {
            logger.error(
                    String.format(
                            "An error occurred during the authorization request to the OPA policy '%s'.",
                            OpaPolicy.METHOD.policyName),
                    e
            );
            return OpaResourceAccessResult.unauthorized();
        }
    }

    private Optional<ParentUUIDGetter> getParentUUIDGetter(List<ExternalAuthorizationConfigAttribute> attributes) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        @SuppressWarnings("unchecked")
        Optional<Class<ParentUUIDGetter>> parentUUIDGetterClass = attributes.stream()
                .filter(att -> {
                    Object value = att.getAttributeValue();
                    return (value instanceof Class<?>) &&
                            ParentUUIDGetter.class.isAssignableFrom((Class<?>) value) &&
                            !NoOpParentUUIDGetter.class.isAssignableFrom((Class<?>) value);
                })
                .map(att -> (Class<ParentUUIDGetter>) att.getAttributeValue())
                .findFirst();

        Optional<ParentUUIDGetter> parentUUIDGetter = Optional.empty();
        if (parentUUIDGetterClass.isPresent()) {
            parentUUIDGetter = Optional.of(parentUUIDGetterClass.get().getConstructor().newInstance());
        }
        return parentUUIDGetter;
    }

    @SuppressWarnings("unchecked")
    private List<SecuredUUID> extractUUIDsFromMethodArguments(MethodInvocation methodInvocation) {
        List<SecuredUUID> uuids = new ArrayList<>();

        Arrays.stream(methodInvocation.getArguments())
                .filter(arg -> arg instanceof SecuredUUID && !(arg instanceof SecuredParentUUID))
                .forEach(arg -> uuids.add((SecuredUUID) arg));

        Arrays.stream(methodInvocation.getArguments())
                .filter(arg -> arg instanceof List && !((List<?>) arg).isEmpty() && ((List<?>) arg).get(0) instanceof SecuredUUID && !(((List<?>) arg).get(0) instanceof SecuredParentUUID))
                .forEach(arg -> uuids.addAll(((List<SecuredUUID>) arg)));

        return uuids;
    }

    @SuppressWarnings("unchecked")
    private List<SecuredUUID> extractParentUUIDsFromMethodArguments(MethodInvocation methodInvocation) {
        List<SecuredUUID> uuids = new ArrayList<>();

        Arrays.stream(methodInvocation.getArguments())
                .filter(arg -> arg instanceof SecuredParentUUID)
                .forEach(arg -> uuids.add((SecuredParentUUID) arg));

        Arrays.stream(methodInvocation.getArguments())
                .filter(arg -> arg instanceof List && !((List<?>) arg).isEmpty() && ((List<?>) arg).get(0) instanceof SecuredParentUUID)
                .forEach(arg -> uuids.addAll(((List<SecuredParentUUID>) arg)));

        return uuids;
    }

    private boolean shouldBeSendToOpa(ExternalAuthorizationConfigAttribute att) {
        Object value = att.getAttributeValue();
        return value.getClass().isPrimitive() ||
                value instanceof String ||
                value instanceof Integer ||
                value instanceof Boolean ||
                value instanceof Float ||
                value instanceof Double;
    }

    private void setDeniedResource(String resourceName) {
        if(RequestContextHolder.getRequestAttributes() != null) {
            RequestContextHolder.getRequestAttributes().setAttribute("INTERNAL_ATTRIB_DENIED_RESOURCE_NAME",
                    resourceName,
                    121);
        }
    }

    private void setDeniedResourceAction(String resourceActionName) {
        if (RequestContextHolder.getRequestAttributes() != null) {
            RequestContextHolder.getRequestAttributes().setAttribute("INTERNAL_ATTRIB_DENIED_RESOURCE_ACTION_NAME",
                    resourceActionName,
                    121);
        }
    }
}
