package com.czertainly.core.dao.entity.acme;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Date;

@Entity(name = "acme_nonce")
@Table(name = "acme_nonce")
public class AcmeNonce {

    @Id
    @Column(name="nonce")
    private String nonce;

    @Column(name = "created")
    private Date created;

    @Column(name="expires")
    private Date expires;

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }
}
