package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.api.model.core.acme.Account;
import com.czertainly.api.model.core.acme.AccountStatus;
import com.czertainly.api.model.core.acme.OrderStatus;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "acme_account")
public class AcmeAccount extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<Account> {

    @Column(name="account_id")
    private String accountId;

    @Column(name="public_key")
    private String publicKey;

    @Column(name="is_default_ra_profile")
    private boolean isDefaultRaProfile;

    @Column(name="is_enabled")
    private boolean isEnabled;

    @Column(name="status")
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @Column(name="contact")
    private String contact;

    @Column(name="terms_of_service_agreed")
    private Boolean termsOfServiceAgreed;

    @JsonBackReference
    @OneToMany(mappedBy = "acmeAccount", fetch = FetchType.LAZY)
    private Set<AcmeOrder> orders = new HashSet<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ra_profile_uuid", nullable = false, insertable = false, updatable = false)
    private RaProfile raProfile;

    @Column(name = "ra_profile_uuid", nullable = false)
    private UUID raProfileUuid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acme_profile_uuid", nullable = false, insertable = false, updatable = false)
    private AcmeProfile acmeProfile;

    @Column(name = "acme_profile_uuid", nullable = false)
    private UUID acmeProfileUuid;

    @Override
    public Account mapToDto(){
        Account account = new Account();
        account.setContact(MetaDefinitions.deserializeArrayString(contact));
        account.setStatus(status);
        account.setTermsOfServiceAgreed(termsOfServiceAgreed);
        return account;
    }

    public AcmeAccountResponseDto mapToDtoForUi(){
        AcmeAccountResponseDto account = new AcmeAccountResponseDto();
        account.setUuid(uuid.toString());
        account.setAccountId(accountId);
        account.setEnabled(isEnabled);
        account.setContact(MetaDefinitions.deserializeArrayString(contact));
        if(acmeProfile != null) {
            account.setAcmeProfileName(acmeProfile.getName());
            account.setAcmeProfileUuid(acmeProfile.getUuid().toString());
        }
        if(raProfile != null) {
            account.setRaProfile(raProfile.mapToDtoSimplified());
        }
        account.setSuccessfulOrders(orders.stream()
                .filter(acmeOrder -> acmeOrder.getStatus()
                        .equals(OrderStatus.READY))
                .collect(Collectors.toList()).size());
        account.setPendingOrders(orders.stream()
                .filter(acmeOrder -> acmeOrder.getStatus()
                        .equals(OrderStatus.PENDING))
                .collect(Collectors.toList()).size());
        account.setFailedOrders(orders.stream()
                .filter(acmeOrder -> acmeOrder.getStatus()
                        .equals(OrderStatus.INVALID))
                .collect(Collectors.toList()).size());
        account.setProcessingOrders(orders.stream()
                .filter(acmeOrder -> acmeOrder.getStatus()
                        .equals(OrderStatus.PROCESSING))
                .collect(Collectors.toList()).size());
        account.setValidOrders(orders.stream()
                .filter(acmeOrder -> acmeOrder.getStatus()
                        .equals(OrderStatus.VALID))
                .collect(Collectors.toList()).size());

        account.setStatus(status);
        account.setTermsOfServiceAgreed(termsOfServiceAgreed);
        account.setTotalOrders(orders.size());
        return account;
    }

    public AcmeAccountListResponseDto mapToDtoForUiSimple(){
        AcmeAccountListResponseDto account = new AcmeAccountListResponseDto();
        account.setUuid(uuid.toString());
        account.setAccountId(accountId);
        account.setEnabled(isEnabled);
        if(acmeProfile != null) {
            account.setAcmeProfileName(acmeProfile.getName());
            account.setAcmeProfileUuid(acmeProfile.getUuid().toString());
        }
        if(raProfile != null) {
            account.setRaProfile(raProfile.mapToDtoSimplified());
        }
        account.setStatus(status);
        account.setTotalOrders(orders.size());
        return account;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("accountId", accountId)
                .append("raProfileName", raProfile.getName()).toString();

    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public Boolean isTermsOfServiceAgreed() {
        return termsOfServiceAgreed;
    }

    public void setTermsOfServiceAgreed(Boolean termsOfServiceAgreed) {
        this.termsOfServiceAgreed = termsOfServiceAgreed;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public boolean isDefaultRaProfile() {
        return isDefaultRaProfile;
    }

    public void setDefaultRaProfile(boolean defaultRaProfile) {
        isDefaultRaProfile = defaultRaProfile;
    }

    public Set<AcmeOrder> getOrders() {
        return orders;
    }

    public void setOrders(Set<AcmeOrder> orders) {
        this.orders = orders;
    }

    public RaProfile getRaProfile() { return raProfile; }

    public void setRaProfile(RaProfile raProfile) {
        this.raProfile = raProfile;
        this.raProfileUuid = raProfile.getUuid();
    }

    public AcmeProfile getAcmeProfile() {
        return acmeProfile;
    }

    public void setAcmeProfile(AcmeProfile acmeProfile) {
        this.acmeProfile = acmeProfile;
        this.acmeProfileUuid = acmeProfile.getUuid();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public Boolean getTermsOfServiceAgreed() {
        return termsOfServiceAgreed;
    }

    public UUID getRaProfileUuid() {
        return raProfileUuid;
    }

    public void setRaProfileUuid(UUID raProfileUuid) {
        this.raProfileUuid = raProfileUuid;
    }

    public void setRaProfileUuid(String raProfileUuid) {
        this.raProfileUuid = UUID.fromString(raProfileUuid);
    }

    public UUID getAcmeProfileUuid() {
        return acmeProfileUuid;
    }

    public void setAcmeProfileUuid(String acmeProfileUuid) {
        this.acmeProfileUuid = UUID.fromString(acmeProfileUuid);
    }
}
