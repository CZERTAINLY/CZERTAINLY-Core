package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.core.acme.*;
import com.czertainly.core.dao.entity.Audited;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.util.AcmeCommonHelper;
import com.czertainly.core.util.AcmeSerializationUtil;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "acme_order")
public class AcmeOrder extends Audited implements Serializable, DtoMapper<Order> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "acme_new_order_seq")
    @SequenceGenerator(name = "acme_new_order_seq", sequenceName = "acme_new_order_id_seq", allocationSize = 1)
    private Long id;

    @Column(name="order_id")
    private String orderId;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    private AcmeAccount acmeAccount;

    @OneToOne
    @JoinColumn(name = "certificate_ref")
    private Certificate certificateReference;

    @Column(name="certificate_id")
    private String certificateId;

    @Column(name="notBefore")
    private Date notBefore;

    @Column(name="notAfter")
    private Date notAfter;

    @Column(name="expires")
    private Date expires;

    @Column(name="identifiers")
    private String identifiers;

    @Column(name="status")
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @JsonBackReference
    @OneToMany(mappedBy = "order")
    private Set<AcmeAuthorization> authorizations = new HashSet<>();

    @Override
    public Order mapToDto(){
        Order order = new Order();
        order.setAuthorizations(authorizations.stream().map(AcmeAuthorization::getUrl).collect(Collectors.toList()));
        order.setFinalize(getFinalizeUrl());
        if(certificateId != null){
            order.setCertificate(getCertificateUrl());
        }
        order.setStatus(status);
        order.setExpires(AcmeCommonHelper.getStringFromDate(expires));
        order.setNotAfter(AcmeCommonHelper.getStringFromDate(notAfter));
        order.setNotBefore(AcmeCommonHelper.getStringFromDate(notBefore));
        order.setIdentifiers(AcmeSerializationUtil.deserializeIdentifiers(identifiers));
        return order;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("orderId", orderId)
                .append("issuedCertificate", certificateReference.getUuid())
                .append("acmeAccount", acmeAccount)
                .append("notBefore", notBefore)
                .append("notAfter", notAfter)
                .append("expires", expires)
                .append("authorizations", authorizations)
                .append("certificateId", certificateId)
                .append("identifiers", identifiers).toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public AcmeAccount getAcmeAccount() {
        return acmeAccount;
    }

    public void setAcmeAccount(AcmeAccount acmeAccount) {
        this.acmeAccount = acmeAccount;
    }

    public Date getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(Date notBefore) {
        this.notBefore = notBefore;
    }

    public Date getNotAfter() {
        return notAfter;
    }

    public void setNotAfter(Date notAfter) {
        this.notAfter = notAfter;
    }

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    public String getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(String identifiers) {
        this.identifiers = identifiers;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Set<AcmeAuthorization> getAuthorizations() {
        return authorizations;
    }

    public void setAuthorizations(Set<AcmeAuthorization> authorizations) {
        this.authorizations = authorizations;
    }

    public Certificate getCertificateReference() {
        return certificateReference;
    }

    public void setCertificateReference(Certificate certificateReference) {
        this.certificateReference = certificateReference;
    }

    public String getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(String certificateId) {
        this.certificateId = certificateId;
    }

    // Customer Getter for Order

    private String getBaseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/acme/test";
    }

    public String getUrl() {
        return getBaseUrl() + "/order/" + orderId;
    }

    // Customer Getter for Certificate URL
    public String getCertificateUrl() {
        return getBaseUrl() + "/cert/" + certificateId;
    }

    // Customer Getter for Finalize URL
    public String getFinalizeUrl() {
        return getBaseUrl() + "/order/" + orderId + "/finalize";
    }
}
