package com.czertainly.core.mapper.signing;

import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.SimplifiedSigningProfileDto;
import com.czertainly.api.model.client.signing.profile.scheme.DelegatedSigningDto;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.api.model.client.signing.profile.scheme.OneTimeKeyManagedSigningDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningDto;
import com.czertainly.api.model.client.signing.profile.workflow.ContentSigningWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.RawSigningWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspActivationDetailDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SigningProfileMapper {

    private SigningProfileMapper() {
    }

    /**
     * Converts a {@link SigningProfile} entity to a full {@link SigningProfileDto}, populating
     * custom attributes, connector signing-operation attributes, and workflow formatter attributes.
     */
    public static SigningProfileDto toDto(SigningProfile profile, List<ResponseAttribute> customAttributes,
                                          List<ResponseAttribute> signingOperationAttributes,
                                          List<ResponseAttribute> signatureFormatterConnectorAttributes) {
        SigningProfileDto dto = new SigningProfileDto();
        dto.setUuid(profile.getUuid().toString());
        dto.setName(profile.getName());
        dto.setDescription(profile.getDescription());
        dto.setVersion(profile.getLatestVersion() != null ? profile.getLatestVersion() : 1);
        dto.setEnabled(profile.getEnabled() != null ? profile.getEnabled() : false);
        dto.setCustomAttributes(customAttributes);

        // Build signing scheme DTO
        if (profile.getSigningScheme() == SigningScheme.DELEGATED) {
            DelegatedSigningDto delegatedDto = new DelegatedSigningDto();
            if (profile.getDelegatedSignerConnectorUuid() != null) {
                NameAndUuidDto ref = new NameAndUuidDto();
                ref.setUuid(profile.getDelegatedSignerConnectorUuid().toString());
                delegatedDto.setConnector(ref);
            }
            dto.setSigningScheme(delegatedDto);
        } else if (profile.getSigningScheme() == SigningScheme.MANAGED && profile.getManagedSigningType() != null) {
            if (profile.getManagedSigningType() == ManagedSigningType.STATIC_KEY) {
                StaticKeyManagedSigningDto staticDto = new StaticKeyManagedSigningDto();
                if (profile.getCertificateUuid() != null) {
                    staticDto.setCertificate(profile.getCertificate().mapToSimpleDto(null));
                }
                staticDto.setSigningOperationAttributes(signingOperationAttributes != null ? signingOperationAttributes : new ArrayList<>());
                dto.setSigningScheme(staticDto);
            } else if (profile.getManagedSigningType() == ManagedSigningType.ONE_TIME_KEY) {
                OneTimeKeyManagedSigningDto oneTimeDto = new OneTimeKeyManagedSigningDto();
                if (profile.getTokenProfileUuid() != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(profile.getTokenProfileUuid().toString());
                    oneTimeDto.setTokenProfile(ref);
                }
                if (profile.getRaProfileUuid() != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(profile.getRaProfileUuid().toString());
                    oneTimeDto.setRaProfile(ref);
                }
                if (profile.getCsrTemplateUuid() != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(profile.getCsrTemplateUuid().toString());
                    oneTimeDto.setCsrTemplate(ref);
                }
                dto.setSigningScheme(oneTimeDto);
            }
        }

        // Build workflow DTO
        if (profile.getWorkflowType() == SigningWorkflowType.CONTENT_SIGNING) {
            ContentSigningWorkflowDto wf = new ContentSigningWorkflowDto();
            setFormatterRef(profile.getSignatureFormatterConnectorUuid(), wf::setSignatureFormatterConnector);
            wf.setSignatureFormatterConnectorAttributes(signatureFormatterConnectorAttributes != null ? signatureFormatterConnectorAttributes : new ArrayList<>());
            dto.setWorkflow(wf);
        } else if (profile.getWorkflowType() == SigningWorkflowType.RAW_SIGNING) {
            RawSigningWorkflowDto wf = new RawSigningWorkflowDto();
            // no formatter for raw signing
            dto.setWorkflow(wf);
        } else if (profile.getWorkflowType() == SigningWorkflowType.TIMESTAMPING) {
            TimestampingWorkflowDto wf = new TimestampingWorkflowDto();
            setFormatterRef(profile.getSignatureFormatterConnectorUuid(), wf::setSignatureFormatterConnector);
            wf.setSignatureFormatterConnectorAttributes(signatureFormatterConnectorAttributes != null ? signatureFormatterConnectorAttributes : new ArrayList<>());
            wf.setQualifiedTimestamp(profile.getQualifiedTimestamp());
            wf.setDefaultPolicyId(profile.getDefaultPolicyId());
            wf.setAllowedPolicyIds(profile.getAllowedPolicyIds() != null ? profile.getAllowedPolicyIds() : new ArrayList<>());
            if (profile.getAllowedDigestAlgorithms() != null && !profile.getAllowedDigestAlgorithms().isEmpty()) {
                wf.setAllowedDigestAlgorithms(
                        profile.getAllowedDigestAlgorithms().stream()
                                .map(DigestAlgorithm::findByCode)
                                .toList()
                );
            }
            dto.setWorkflow(wf);
        }

        // Enabled protocols
        if (profile.getTspProfileUuid() != null) {
            dto.getEnabledProtocols().add(SigningProtocol.TSP);
        }

        return dto;
    }

    public static SigningProfileListDto toListDto(SigningProfile profile) {
        SigningProfileListDto dto = new SigningProfileListDto();
        dto.setUuid(profile.getUuid().toString());
        dto.setName(profile.getName());
        dto.setDescription(profile.getDescription());
        dto.setVersion(profile.getLatestVersion() != null ? profile.getLatestVersion() : 1);
        dto.setSigningWorkflowType(profile.getWorkflowType());
        dto.setEnabled(profile.getEnabled() != null ? profile.getEnabled() : false);
        return dto;
    }

    public static TspActivationDetailDto toTspActivationDto(SigningProfile profile) {
        TspActivationDetailDto dto = new TspActivationDetailDto();
        if (profile.getTspProfile() != null) {
            dto.setUuid(profile.getTspProfile().getUuid().toString());
            dto.setName(profile.getTspProfile().getName());
            dto.setAvailable(true);
            dto.setSigningUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + "/v1/protocols/tsp/signingProfile/" + profile.getName() + "/sign");
        } else {
            dto.setAvailable(false);
        }
        return dto;
    }

    public static SimplifiedSigningProfileDto toSimpleDto(SigningProfile signingProfile) {
        SimplifiedSigningProfileDto signingProfileDto = new SimplifiedSigningProfileDto();
        signingProfileDto.setUuid(signingProfile.getUuid().toString());
        signingProfileDto.setName(signingProfile.getName());
        signingProfileDto.setEnabled(Boolean.TRUE.equals(signingProfile.getEnabled()));
        return signingProfileDto;
    }

    private static void setFormatterRef(UUID signatureFormatterConnectorUuid, Consumer<NameAndUuidDto> setter) {
        if (signatureFormatterConnectorUuid != null) {
            NameAndUuidDto ref = new NameAndUuidDto();
            ref.setUuid(signatureFormatterConnectorUuid.toString());
            setter.accept(ref);
        }
    }
}
