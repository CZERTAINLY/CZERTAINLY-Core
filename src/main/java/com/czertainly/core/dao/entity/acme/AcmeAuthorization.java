package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.core.acme.Authorization;
import com.czertainly.api.model.core.acme.AuthorizationStatus;
import com.czertainly.core.dao.entity.Audited;
import com.czertainly.core.util.AcmeCommonHelper;
import com.czertainly.core.util.AcmeSerializationUtil;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "acme_authorization")
public class AcmeAuthorization  extends Audited implements Serializable, DtoMapper<Authorization> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "acme_new_authorization_seq")
    @SequenceGenerator(name = "acme_new_authorization_seq", sequenceName = "acme_new_authorization_id_seq", allocationSize = 1)
    private Long id;

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
    @OneToMany(mappedBy = "authorization")
    private Set<AcmeChallenge> challenges = new HashSet<>();

    @Column(name="wildcard")
    private Boolean wildcard;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false)
    private AcmeOrder order;

    @Override
    public Authorization mapToDto() {

        Authorization authorization = new Authorization();
        authorization.setStatus(status);
        authorization.setExpires(AcmeCommonHelper.getStringFromDate(expires));
        authorization.setWildcard(wildcard);
        authorization.setIdentifier(AcmeSerializationUtil.deserializeIdentifier(identifier));
        authorization.setChallenges(challenges.stream().map(AcmeChallenge::mapToDto).collect(Collectors.toList()));
        return authorization;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("authorizationId", authorizationId)
                .append("status", status)
                .append("expires", expires)
                .append("wildcard", wildcard)
                .append("challenges", challenges)
                .toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AcmeOrder getOrder() {
        return order;
    }

    public void setOrder(AcmeOrder order) {
        this.order = order;
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

    public Boolean getWildcard() {
        return wildcard;
    }

    public void setWildcard(Boolean wildcard) {
        this.wildcard = wildcard;
    }

    public Set<AcmeChallenge> getChallenges() {
        return challenges;
    }

    public void setChallenges(Set<AcmeChallenge> challenges) {
        this.challenges = challenges;
    }

    private String getBaseUrl() {
        if(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")){
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/acme/raProfile/"
                    + order.getAcmeAccount().getRaProfile().getName();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/acme/"
                + order.getAcmeAccount().getAcmeProfile().getName();
    }
    // Customer Getter for Authorization URL
    public String getUrl() {
        return getBaseUrl() + "/authz/" + authorizationId;
    }
}
