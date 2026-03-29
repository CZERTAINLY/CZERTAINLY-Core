package com.czertainly.core.mapper.signing;

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
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolActivationDetailDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspActivationDetailDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SigningProfileMapper {

    private SigningProfileMapper() {
    }

    public static SigningProfileDto toDto(SigningProfile profile, List<ResponseAttribute> customAttributes) {
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
                if (profile.getTokenProfileUuid() != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(profile.getTokenProfileUuid().toString());
                    staticDto.setTokenProfile(ref);
                }
                if (profile.getCryptographicKeyUuid() != null) {
                    NameAndUuidDto ref = new NameAndUuidDto();
                    ref.setUuid(profile.getCryptographicKeyUuid().toString());
                    staticDto.setCryptographicKey(ref);
                }
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
        if (profile.getWorkflowType() == SigningWorkflowType.CODE_BINARY_SIGNING) {
            CodeBinarySigningWorkflowDto wf = new CodeBinarySigningWorkflowDto();
            setFormatterRef(profile.getSignatureFormatterConnectorUuid(), wf::setSignatureFormatterConnector);
            dto.setWorkflow(wf);
        } else if (profile.getWorkflowType() == SigningWorkflowType.DOCUMENT_SIGNING) {
            DocumentSigningWorkflowDto wf = new DocumentSigningWorkflowDto();
            setFormatterRef(profile.getSignatureFormatterConnectorUuid(), wf::setSignatureFormatterConnector);
            dto.setWorkflow(wf);
        } else if (profile.getWorkflowType() == SigningWorkflowType.RAW_SIGNING) {
            RawSigningWorkflowDto wf = new RawSigningWorkflowDto();
            // no formatter for raw signing
            dto.setWorkflow(wf);
        } else if (profile.getWorkflowType() == SigningWorkflowType.TIMESTAMPING) {
            TimestampingWorkflowDto wf = new TimestampingWorkflowDto();
            setFormatterRef(profile.getSignatureFormatterConnectorUuid(), wf::setSignatureFormatterConnector);
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
        if (profile.getIlmSigningProtocolConfigurationUuid() != null) {
            dto.getEnabledProtocols().add(SigningProtocol.ILM_SIGNING_PROTOCOL);
        }
        if (profile.getTspConfigurationUuid() != null) {
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

    public static IlmSigningProtocolActivationDetailDto toIlmSigningProtocolActivationDto(SigningProfile profile) {
        IlmSigningProtocolActivationDetailDto dto = new IlmSigningProtocolActivationDetailDto();
        dto.setAvailable(profile.getIlmSigningProtocolConfiguration() != null);
        if (profile.getIlmSigningProtocolConfiguration() != null) {
            dto.setSigningUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + "/v1/protocols/ilm/signingProfile/" + profile.getName() + "/sign");
        }
        return dto;
    }

    public static TspActivationDetailDto toTspActivationDto(SigningProfile profile) {
        TspActivationDetailDto dto = new TspActivationDetailDto();
        dto.setAvailable(profile.getTspConfiguration() != null);
        if (profile.getTspConfiguration() != null) {
            dto.setSigningUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + "/v1/protocols/tsp/signingProfile/" + profile.getName() + "/sign");
        }
        return dto;
    }

    private static void setFormatterRef(UUID signatureFormatterConnectorUuid, Consumer<NameAndUuidDto> setter) {
        if (signatureFormatterConnectorUuid != null) {
            NameAndUuidDto ref = new NameAndUuidDto();
            ref.setUuid(signatureFormatterConnectorUuid.toString());
            setter.accept(ref);
        }
    }
}
