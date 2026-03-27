package com.czertainly.core.dao.entity.signing;

import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.scheme.DelegatedSigningDto;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.api.model.client.signing.profile.scheme.OneTimeKeyManagedSigningDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningDto;
import com.czertainly.api.model.client.signing.profile.workflow.CodeBinarySigningWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.DocumentSigningWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.RawSigningWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.core.signing.SigningProtocol;
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
import java.util.function.Consumer;

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

    @Column(name = "cryptographic_key_uuid")
    private UUID cryptographicKeyUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cryptographic_key_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private CryptographicKey cryptographicKey;

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

    @Column(name = "ilm_signing_protocol_configuration_uuid")
    private UUID ilmSigningProtocolConfigurationUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ilm_signing_protocol_configuration_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private IlmSigningProtocolConfiguration ilmSigningProtocolConfiguration;

    @Column(name = "tsp_configuration_uuid")
    private UUID tspConfigurationUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tsp_configuration_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private TspConfiguration tspConfiguration;

    @OneToMany(mappedBy = "signingProfile", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<SigningProfileVersion> versions = new ArrayList<>();

    @OneToMany(mappedBy = "signingProfile", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<DigitalSignature> digitalSignatures = new ArrayList<>();

    public void setTokenProfile(TokenProfile tokenProfile) {
        this.tokenProfile = tokenProfile;
        this.tokenProfileUuid = tokenProfile != null ? tokenProfile.getUuid() : null;
    }

    public void setCryptographicKey(CryptographicKey cryptographicKey) {
        this.cryptographicKey = cryptographicKey;
        this.cryptographicKeyUuid = cryptographicKey != null ? cryptographicKey.getUuid() : null;
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

    public void setIlmSigningProtocolConfiguration(IlmSigningProtocolConfiguration ilmSigningProtocolConfiguration) {
        this.ilmSigningProtocolConfiguration = ilmSigningProtocolConfiguration;
        this.ilmSigningProtocolConfigurationUuid = ilmSigningProtocolConfiguration != null ? ilmSigningProtocolConfiguration.getUuid() : null;
    }

    public void setTspConfiguration(TspConfiguration tspConfiguration) {
        this.tspConfiguration = tspConfiguration;
        this.tspConfigurationUuid = tspConfiguration != null ? tspConfiguration.getUuid() : null;
    }

    public SigningProfileDto mapToDto(List<ResponseAttribute> customAttributes) {
        SigningProfileDto dto = new SigningProfileDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(description);
        dto.setVersion(latestVersion != null ? latestVersion : 1);
        dto.setEnabled(enabled != null ? enabled : false);
        dto.setCustomAttributes(customAttributes);

        // Build signing scheme DTO
        if (signingScheme == SigningScheme.DELEGATED) {
            DelegatedSigningDto delegatedDto = new DelegatedSigningDto();
            if (delegatedSignerConnectorUuid != null) {
                NameAndUuidDto ref = new NameAndUuidDto();
                ref.setUuid(delegatedSignerConnectorUuid.toString());
                delegatedDto.setConnector(ref);
            }
            dto.setSigningScheme(delegatedDto);
        } else if (signingScheme == SigningScheme.MANAGED && managedSigningType != null) {
            if (managedSigningType == ManagedSigningType.STATIC_KEY) {
                StaticKeyManagedSigningDto staticDto = new StaticKeyManagedSigningDto();
                if (tokenProfileUuid != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(tokenProfileUuid.toString());
                    staticDto.setTokenProfile(ref);
                }
                if (cryptographicKeyUuid != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(cryptographicKeyUuid.toString());
                    staticDto.setCryptographicKey(ref);
                }
                dto.setSigningScheme(staticDto);
            } else if (managedSigningType == ManagedSigningType.ONE_TIME_KEY) {
                OneTimeKeyManagedSigningDto oneTimeDto = new OneTimeKeyManagedSigningDto();
                if (tokenProfileUuid != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(tokenProfileUuid.toString());
                    oneTimeDto.setTokenProfile(ref);
                }
                if (raProfileUuid != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(raProfileUuid.toString());
                    oneTimeDto.setRaProfile(ref);
                }
                if (csrTemplateUuid != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(csrTemplateUuid.toString());
                    oneTimeDto.setCsrTemplate(ref);
                }
                dto.setSigningScheme(oneTimeDto);
            }
        }

        // Build workflow DTO
        if (workflowType == SigningWorkflowType.CODE_BINARY_SIGNING) {
            CodeBinarySigningWorkflowDto wf = new CodeBinarySigningWorkflowDto();
            setFormatterRef(wf::setSignatureFormatterConnector);
            dto.setWorkflow(wf);
        } else if (workflowType == SigningWorkflowType.DOCUMENT_SIGNING) {
            DocumentSigningWorkflowDto wf = new DocumentSigningWorkflowDto();
            setFormatterRef(wf::setSignatureFormatterConnector);
            dto.setWorkflow(wf);
        } else if (workflowType == SigningWorkflowType.RAW_SIGNING) {
            RawSigningWorkflowDto wf = new RawSigningWorkflowDto();
            // no formatter for raw signing
            dto.setWorkflow(wf);
        } else if (workflowType == SigningWorkflowType.TIMESTAMPING) {
            TimestampingWorkflowDto wf = new TimestampingWorkflowDto();
            setFormatterRef(wf::setSignatureFormatterConnector);
            wf.setQualifiedTimestamp(qualifiedTimestamp);
            wf.setDefaultPolicyId(defaultPolicyId);
            wf.setAllowedPolicyIds(allowedPolicyIds != null ? allowedPolicyIds : new ArrayList<>());
            if (allowedDigestAlgorithms != null && !allowedDigestAlgorithms.isEmpty()) {
                wf.setAllowedDigestAlgorithms(
                        allowedDigestAlgorithms.stream()
                                .map(DigestAlgorithm::findByCode)
                                .toList()
                );
            }
            dto.setWorkflow(wf);
        }

        // Enabled protocols
        if (ilmSigningProtocolConfigurationUuid != null) {
            dto.getEnabledProtocols().add(SigningProtocol.ILM_SIGNING_PROTOCOL);
        }
        if (tspConfigurationUuid != null) {
            dto.getEnabledProtocols().add(SigningProtocol.TSP);
        }

        return dto;
    }

    private void setFormatterRef(Consumer<NameAndUuidDto> setter) {
        if (signatureFormatterConnectorUuid != null) {
            NameAndUuidDto ref = new NameAndUuidDto();
            ref.setUuid(signatureFormatterConnectorUuid.toString());
            setter.accept(ref);
        }
    }

    public SigningProfileListDto mapToListDto() {
        SigningProfileListDto dto = new SigningProfileListDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(description);
        dto.setVersion(latestVersion != null ? latestVersion : 1);
        dto.setSigningWorkflowType(workflowType);
        dto.setEnabled(enabled != null ? enabled : false);
        return dto;
    }
}
