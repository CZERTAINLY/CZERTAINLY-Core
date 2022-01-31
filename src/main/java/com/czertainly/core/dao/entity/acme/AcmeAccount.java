package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.api.model.core.acme.Account;
import com.czertainly.api.model.core.acme.AccountStatus;
import com.czertainly.api.model.core.acme.OrderStatus;
import com.czertainly.core.dao.entity.Audited;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "acme_account")
public class AcmeAccount extends Audited implements Serializable, DtoMapper<Account> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "acme_new_account_seq")
    @SequenceGenerator(name = "acme_new_account_seq", sequenceName = "acme_new_account_id_seq", allocationSize = 1)
    private Long id;

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
    private Boolean isTermsOfServiceAgreed;

    @JsonBackReference
    @OneToMany(mappedBy = "acmeAccount")
    private Set<AcmeOrder> orders = new HashSet<>();

    @OneToOne
    @JoinColumn(name = "ra_profile_id", nullable = false)
    private RaProfile raProfile;

    @OneToOne
    @JoinColumn(name = "acme_profile_id", nullable = false)
    private AcmeProfile acmeProfile;

    @Override
    public Account mapToDto(){
        Account account = new Account();
        account.setContact(MetaDefinitions.deserializeArrayString(contact));
        account.setStatus(status);
        account.setTermsOfServiceAgreed(isTermsOfServiceAgreed);
        return account;
    }

    public AcmeAccountResponseDto mapToDtoForUi(){
        AcmeAccountResponseDto account = new AcmeAccountResponseDto();
        account.setUuid(uuid);
        account.setAccountId(accountId);
        account.setEnabled(isEnabled);
        account.setContact(MetaDefinitions.deserializeArrayString(contact));
        if(acmeProfile != null) {
            account.setAcmeProfileName(acmeProfile.getName());
            account.setAcmeProfileUuid(acmeProfile.getUuid());
        }
        if(raProfile != null) {
            account.setRaProfileName(raProfile.getName());
            account.setRaProfileUuid(raProfile.getUuid());
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
        account.setTermsOfServiceAgreed(isTermsOfServiceAgreed);
        account.setTotalOrders(orders.size());
        return account;
    }

    public AcmeAccountListResponseDto mapToDtoForUiSimple(){
        AcmeAccountListResponseDto account = new AcmeAccountListResponseDto();
        account.setUuid(uuid);
        account.setAccountId(accountId);
        account.setEnabled(isEnabled);
        if(acmeProfile != null) {
            account.setAcmeProfileName(acmeProfile.getName());
        }
        if(raProfile != null) {
            account.setRaProfileName(raProfile.getName());
        }
        account.setStatus(status);
        account.setTotalOrders(orders.size());
        return account;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("id", id)
                .append("accountId", accountId)
                .append("raProfileName", raProfile.getName()).toString();

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
        return isTermsOfServiceAgreed;
    }

    public void setTermsOfServiceAgreed(Boolean termsOfServiceAgreed) {
        isTermsOfServiceAgreed = termsOfServiceAgreed;
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

    public RaProfile getRaProfile() {
        return raProfile;
    }

    public void setRaProfile(RaProfile raProfile) {
        this.raProfile = raProfile;
    }

    public AcmeProfile getAcmeProfile() {
        return acmeProfile;
    }

    public void setAcmeProfile(AcmeProfile acmeProfile) {
        this.acmeProfile = acmeProfile;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }
}
