package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.core.acme.Challenge;
import com.czertainly.api.model.core.acme.ChallengeStatus;
import com.czertainly.api.model.core.acme.ChallengeType;
import com.czertainly.core.dao.entity.Audited;
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
public class AcmeChallenge extends Audited implements Serializable, DtoMapper<Challenge> {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "acme_new_challenge_seq")
    @SequenceGenerator(name = "acme_new_challenge_seq", sequenceName = "acme_new_challenge_id_seq", allocationSize = 1)
    private Long id;

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
    @JoinColumn(name = "authorization_id", nullable = false)
    private AcmeAuthorization authorization;

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
                .append("id", id)
                .append("challengeId", challengeId)
                .append("type", type)
                .append("status", status)
                .append("validated", validated)
                .toString();

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    // Customer Getter for Challenge URL

    private String getBaseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/acme/" +
                authorization.getOrder().getAcmeAccount().getAcmeProfile().getName();
    }

    public String getUrl() {
        return getBaseUrl() + "/chall/" + challengeId;
    }
}
