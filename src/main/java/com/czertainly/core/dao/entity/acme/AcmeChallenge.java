package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.core.acme.Challenge;
import com.czertainly.api.model.core.acme.ChallengeStatus;
import com.czertainly.core.dao.entity.Audited;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private String type;

    @Column(name = "url")
    private String url;

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
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SS'Z'")
                .withZone(ZoneId.of("UTC"));
        Challenge challenge = new Challenge();
        challenge.setStatus(status);
        challenge.setToken(token);
        challenge.setType(type);
        challenge.setUrl(url);
        if(validated != null) {
            challenge.setValidated(formatter.format(validated.toInstant()));
        }
        return challenge;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("challengeId", challengeId)
                .append("type", type)
                .append("status", status)
                .append("url", url)
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

    public String getType() {
        return type;
    }

    public AcmeAuthorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(AcmeAuthorization authorization) {
        this.authorization = authorization;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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
}
