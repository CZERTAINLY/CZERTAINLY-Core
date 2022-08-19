package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.core.acme.Challenge;
import com.czertainly.api.model.core.acme.ChallengeStatus;
import com.czertainly.api.model.core.acme.ChallengeType;
import com.czertainly.core.dao.entity.Audited;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.util.AcmeCommonHelper;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "acme_challenge")
public class AcmeChallenge extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<Challenge> {

    @Column(name="challenge_id")
    private String challengeId;

    @Column(name="type")
    @Enumerated(EnumType.STRING)
    private ChallengeType type;

    @Column(name="token")
    private String token;

    @Column(name="status")
    @Enumerated(EnumType.STRING)
    private ChallengeStatus status;

    @Column(name="validated")
    private Date validated;

    @OneToOne
    @JoinColumn(name = "authorization_uuid", nullable = false)
    private AcmeAuthorization authorization;

    @Column(name = "authorization_uuid")
    private String authorizationUuid;

    @Override
    public Challenge mapToDto(){
        Challenge challenge = new Challenge();
        challenge.setStatus(status);
        challenge.setToken(token);
        challenge.setType(type);
        challenge.setUrl(getUrl());
        challenge.setValidated(AcmeCommonHelper.getStringFromDate(validated));
        return challenge;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("challengeId", challengeId)
                .append("type", type)
                .append("status", status)
                .append("validated", validated)
                .toString();

    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public ChallengeType getType() {
        return type;
    }

    public AcmeAuthorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(AcmeAuthorization authorization) {
        this.authorization = authorization;
    }

    public void setType(ChallengeType type) {
        this.type = type;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public ChallengeStatus getStatus() {
        return status;
    }

    public void setStatus(ChallengeStatus status) {
        this.status = status;
    }

    public Date getValidated() {
        return validated;
    }

    public void setValidated(Date validated) {
        this.validated = validated;
    }

    public String getAuthorizationUuid() {
        return authorizationUuid;
    }

    public void setAuthorizationUuid(String authorizationUuid) {
        this.authorizationUuid = authorizationUuid;
    }

    // Customer Getter for Challenge URL

    private String getBaseUrl() {
        if(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")){
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/acme/raProfile/"
                    + authorization.getOrder().getAcmeAccount().getRaProfile().getName();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/acme/"
                + authorization.getOrder().getAcmeAccount().getAcmeProfile().getName();
    }

    public String getUrl() {
        return getBaseUrl() + "/chall/" + challengeId;
    }
}
