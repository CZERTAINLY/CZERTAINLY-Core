package com.czertainly.core.dao.entity.signing;

import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.service.model.Securable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "signing_profile")
public class SigningProfile extends UniquelyIdentifiedAndAudited implements Securable {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "signing_scheme", nullable = false)
    @Enumerated(EnumType.STRING)
    private SigningScheme signingScheme;

    @Column(name = "managed_signing_type")
    @Enumerated(EnumType.STRING)
    private ManagedSigningType managedSigningType;

    @Column(name = "workflow_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SigningWorkflowType workflowType;

    @Column(name = "latest_version", nullable = false)
    private Integer latestVersion = 1;

    @Column(name = "token_profile_uuid")
    private UUID tokenProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_profile_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private TokenProfile tokenProfile;

    @Column(name = "certificate_uuid")
    private UUID certificateUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private Certificate certificate;

    @Column(name = "ra_profile_uuid")
    private UUID raProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ra_profile_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private RaProfile raProfile;

    @Column(name = "csr_template_uuid")
    private UUID csrTemplateUuid;

    @Column(name = "delegated_signer_connector_uuid")
    private UUID delegatedSignerConnectorUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegated_signer_connector_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private Connector delegatedSignerConnector;

    @Column(name = "signature_formatter_connector_uuid")
    private UUID signatureFormatterConnectorUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signature_formatter_connector_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private Connector signatureFormatterConnector;

    @Column(name = "qualified_timestamp")
    private Boolean qualifiedTimestamp;

    @Column(name = "time_quality_config_uuid")
    private UUID timeQualityConfigurationUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_quality_config_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private TimeQualityConfiguration timeQualityConfiguration;

    @Column(name = "default_policy_id")
    private String defaultPolicyId;

    @Column(name = "allowed_policy_ids")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> allowedPolicyIds = new ArrayList<>();

    @Column(name = "allowed_digest_algorithms")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> allowedDigestAlgorithms = new ArrayList<>();

    @Column(name = "tsp_profile_uuid")
    private UUID tspProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tsp_profile_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private TspProfile tspProfile;

    @OneToMany(mappedBy = "signingProfile", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<SigningProfileVersion> versions = new ArrayList<>();

    @OneToMany(mappedBy = "signingProfile", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<SigningRecord> signingRecords = new ArrayList<>();

    public void setTokenProfile(TokenProfile tokenProfile) {
        this.tokenProfile = tokenProfile;
        this.tokenProfileUuid = tokenProfile != null ? tokenProfile.getUuid() : null;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
        this.certificateUuid = certificate != null ? certificate.getUuid() : null;
    }

    public void setRaProfile(RaProfile raProfile) {
        this.raProfile = raProfile;
        this.raProfileUuid = raProfile != null ? raProfile.getUuid() : null;
    }

    public void setDelegatedSignerConnector(Connector delegatedSignerConnector) {
        this.delegatedSignerConnector = delegatedSignerConnector;
        this.delegatedSignerConnectorUuid = delegatedSignerConnector != null ? delegatedSignerConnector.getUuid() : null;
    }

    public void setSignatureFormatterConnector(Connector signatureFormatterConnector) {
        this.signatureFormatterConnector = signatureFormatterConnector;
        this.signatureFormatterConnectorUuid = signatureFormatterConnector != null ? signatureFormatterConnector.getUuid() : null;
    }

    public void setTimeQualityConfiguration(TimeQualityConfiguration timeQualityConfiguration) {
        this.timeQualityConfiguration = timeQualityConfiguration;
        this.timeQualityConfigurationUuid = timeQualityConfiguration != null ? timeQualityConfiguration.getUuid() : null;
    }

    public void setTspProfile(TspProfile tspProfile) {
        this.tspProfile = tspProfile;
        this.tspProfileUuid = tspProfile != null ? tspProfile.getUuid() : null;
    }
}
