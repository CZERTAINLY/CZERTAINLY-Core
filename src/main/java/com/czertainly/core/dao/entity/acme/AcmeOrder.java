package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.core.acme.Order;
import com.czertainly.api.model.core.acme.OrderStatus;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.service.acme.impl.ExtendedAcmeHelperService;
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
@Table(name = "acme_order")
public class AcmeOrder extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<Order> {

    @Column(name="order_id")
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_uuid", nullable = false, insertable = false, updatable = false)
    private AcmeAccount acmeAccount;

    @Column(name = "account_uuid")
    private UUID acmeAccountUuid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_ref", insertable = false, updatable = false)
    private Certificate certificateReference;

    @Column(name = "certificate_ref")
    private UUID certificateReferenceUuid;

    @Column(name="certificate_id")
    private String certificateId;

    @Column(name="not_before")
    private Date notBefore;

    @Column(name="not_after")
    private Date notAfter;

    @Column(name="expires")
    private Date expires;

    @Column(name="identifiers")
    private String identifiers;

    @Column(name="status")
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @JsonBackReference
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
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
        order.setIdentifiers(SerializationUtil.deserializeIdentifiers(identifiers));
        return order;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("orderId", orderId)
                .append("issuedCertificate", certificateReference)
                .append("acmeAccount", acmeAccount)
                .append("notBefore", notBefore)
                .append("notAfter", notAfter)
                .append("expires", expires)
                .append("certificateId", certificateId)
                .append("identifiers", identifiers).toString();
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
        this.acmeAccountUuid = acmeAccount.getUuid();
    }

    public Certificate getCertificateReference() {
        return certificateReference;
    }

    public void setCertificateReference(Certificate certificateReference) {
        this.certificateReference = certificateReference;
        if(certificateReference != null) this.certificateReferenceUuid = certificateReference.getUuid();
    }

    public UUID getAcmeAccountUuid() {
        return acmeAccountUuid;
    }

    public void setAcmeAccountUuid(UUID acmeAccountUuid) {
        this.acmeAccountUuid = acmeAccountUuid;
    }

    public void setAcmeAccountUuid(String acmeAccountUuid) {
        this.acmeAccountUuid = UUID.fromString(acmeAccountUuid);
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

    public String getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(String certificateId) {
        this.certificateId = certificateId;
    }

    public UUID getCertificateReferenceUuid() {
        return certificateReferenceUuid;
    }

    public void setCertificateReferenceUuid(UUID certificateReferenceUuid) {
        this.certificateReferenceUuid = certificateReferenceUuid;
    }

    public void setCertificateReferenceUuid(String certificateReferenceUuid) {
        this.certificateReferenceUuid = UUID.fromString(certificateReferenceUuid);
    }

    // Customer Getter for Order

    private String getBaseUrl() {
        if(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")){
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + ExtendedAcmeHelperService.ACME_URI_HEADER + "/raProfile/"
                    + acmeAccount.getRaProfile().getName();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                + ExtendedAcmeHelperService.ACME_URI_HEADER + "/"
                + acmeAccount.getAcmeProfile().getName();
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
