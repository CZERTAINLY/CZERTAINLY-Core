package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.core.acme.Authorization;
import com.czertainly.api.model.core.acme.AuthorizationStatus;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.service.acme.AcmeConstants;
import com.czertainly.core.util.AcmeCommonHelper;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.SerializationUtil;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "acme_authorization")
public class AcmeAuthorization  extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<Authorization> {

    @Column(name="authorization_id")
    private String authorizationId;

    @Column(name="identifier")
    private String identifier;

    @Column(name="status")
    @Enumerated(EnumType.STRING)
    private AuthorizationStatus status;

    @Column(name = "expires")
    private Date expires;

    @JsonBackReference
    @OneToMany(mappedBy = "authorization", fetch = FetchType.LAZY)
    private Set<AcmeChallenge> challenges = new HashSet<>();

    @Column(name="wildcard")
    private Boolean wildcard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_uuid", nullable = false, insertable = false, updatable = false)
    private AcmeOrder order;

    @Column(name = "order_uuid", nullable = false)
    private UUID orderUuid;

    @Override
    public Authorization mapToDto() {

        Authorization authorization = new Authorization();
        authorization.setStatus(status);
        authorization.setExpires(AcmeCommonHelper.getStringFromDate(expires));
        authorization.setIdentifier(SerializationUtil.deserializeIdentifier(identifier));
        authorization.setChallenges(challenges.stream().map(AcmeChallenge::mapToDto).collect(Collectors.toList()));
        return authorization;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("authorizationId", authorizationId)
                .append("status", status)
                .append("expires", expires)
                .append("wildcard", wildcard)
                .append("challenges", challenges)
                .toString();
    }

    public AcmeOrder getOrder() {
        return order;
    }

    public void setOrder(AcmeOrder order) {
        this.order = order;
        this.orderUuid = order.getUuid();
    }

    public String getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(String authorizationId) {
        this.authorizationId = authorizationId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public AuthorizationStatus getStatus() {
        return status;
    }

    public void setStatus(AuthorizationStatus status) {
        this.status = status;
    }

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    public Boolean isWildcard() {
        return wildcard;
    }

    public void setWildcard(Boolean isWildcard) {
        this.wildcard = isWildcard;
    }

    public Set<AcmeChallenge> getChallenges() {
        return challenges;
    }

    public void setChallenges(Set<AcmeChallenge> challenges) {
        this.challenges = challenges;
    }

    private String getBaseUrl() {
        if(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")){
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + AcmeConstants.ACME_URI_HEADER + "/raProfile/"
                    + order.getAcmeAccount().getRaProfile().getName();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                + AcmeConstants.ACME_URI_HEADER + "/"
                + order.getAcmeAccount().getAcmeProfile().getName();
    }
    // Customer Getter for Authorization URL
    public String getUrl() {
        return getBaseUrl() + "/authz/" + authorizationId;
    }

    public Boolean getWildcard() {
        return wildcard;
    }

    public UUID getOrderUuid() {
        return orderUuid;
    }

    public void setOrderUuid(UUID orderUuid) {
        this.orderUuid = orderUuid;
    }

    public void setOrderUuid(String orderUuid) {
        this.orderUuid = UUID.fromString(orderUuid);
    }
}
