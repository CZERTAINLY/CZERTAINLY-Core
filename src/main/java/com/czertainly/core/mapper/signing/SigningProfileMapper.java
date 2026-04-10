package com.czertainly.core.mapper.signing;

import com.czertainly.api.model.client.attribute.RequestAttribute;
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
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspActivationDetailDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.model.signing.workflow.ContentSigningWorkflow;
import com.czertainly.core.model.signing.workflow.DelegatedContentSigningWorkflow;
import com.czertainly.core.model.signing.workflow.DelegatedRawSigningWorkflow;
import com.czertainly.core.model.signing.workflow.DelegatedTimestampingWorkflow;
import com.czertainly.core.model.signing.workflow.ManagedContentSigningWorkflow;
import com.czertainly.core.model.signing.workflow.ManagedRawSigningWorkflow;
import com.czertainly.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.czertainly.core.model.signing.workflow.RawSigningWorkflow;
import com.czertainly.core.model.signing.SigningProfileModel;
import com.czertainly.core.model.signing.workflow.TimestampingWorkflow;
import com.czertainly.core.model.signing.scheme.DelegatedSigning;
import com.czertainly.core.model.signing.scheme.ManagedSigning;
import com.czertainly.core.model.signing.scheme.OneTimeKeyManagedSigning;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.scheme.StaticKeyManagedSigning;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SigningProfileMapper {

    private SigningProfileMapper() {
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public mappers — DTO
    // ──────────────────────────────────────────────────────────────────────────

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
        dto.setCustomAttributes(safeList(customAttributes));

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
                staticDto.setSigningOperationAttributes(safeList(signingOperationAttributes));
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

        // Build workflow DTO — delegates to shared helpers
        dto.setWorkflow(switch (profile.getWorkflowType()) {
            case CONTENT_SIGNING -> buildContentSigningWorkflowDto(profile, signatureFormatterConnectorAttributes);
            case RAW_SIGNING -> new RawSigningWorkflowDto();
            case TIMESTAMPING -> buildTimestampingWorkflowDto(profile, signatureFormatterConnectorAttributes);
        });

        // Enabled protocols
        if (profile.getTspProfileUuid() != null) {
            dto.getEnabledProtocols().add(SigningProtocol.TSP);
        }

        return dto;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public mappers — model layer
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Converts a {@link SigningProfile} entity to the typed {@link SigningProfileModel} hierarchy.
     * Must be called within a transaction.
     */
    public static SigningProfileModel<?, ?> toModel(SigningProfile profile,
                                                    List<RequestAttribute> signingOperationAttributes,
                                                    List<RequestAttribute> signatureFormatterConnectorAttributes) {
        SigningSchemeModel schemeModel = buildSchemeModel(profile, signingOperationAttributes);
        List<SigningProtocol> protocols = profile.getTspProfileUuid() != null ? List.of(SigningProtocol.TSP) : List.of();
        int version = profile.getLatestVersion() != null ? profile.getLatestVersion() : 1;
        boolean enabled = profile.getEnabled() != null ? profile.getEnabled() : false;

        return switch (profile.getWorkflowType()) {
            case CONTENT_SIGNING -> new SigningProfileModel<>(
                    profile.getUuid(), profile.getName(), profile.getDescription(),
                    version, enabled, protocols,
                    buildContentSigningWorkflow(profile, signatureFormatterConnectorAttributes),
                    schemeModel);
            case RAW_SIGNING -> new SigningProfileModel<>(
                    profile.getUuid(), profile.getName(), profile.getDescription(),
                    version, enabled, protocols,
                    buildRawSigningWorkflow(profile),
                    schemeModel);
            case TIMESTAMPING -> new SigningProfileModel<>(
                    profile.getUuid(), profile.getName(), profile.getDescription(),
                    version, enabled, protocols,
                    buildTimestampingWorkflow(profile, signatureFormatterConnectorAttributes),
                    schemeModel);
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public mappers — list / simple / TSP
    // ──────────────────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────────────────
    // DTO builders
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a {@link ContentSigningWorkflowDto} from the entity and pre-loaded formatter attributes.
     */
    private static ContentSigningWorkflowDto buildContentSigningWorkflowDto(
            SigningProfile profile, List<ResponseAttribute> signatureFormatterConnectorAttributes) {
        ContentSigningWorkflowDto wf = new ContentSigningWorkflowDto();
        setFormatterRef(profile.getSignatureFormatterConnectorUuid(), wf::setSignatureFormatterConnector);
        wf.setSignatureFormatterConnectorAttributes(safeList(signatureFormatterConnectorAttributes));
        return wf;
    }

    /**
     * Builds a {@link TimestampingWorkflowDto} from the entity and pre-loaded formatter attributes.
     */
    private static TimestampingWorkflowDto buildTimestampingWorkflowDto(
            SigningProfile profile, List<ResponseAttribute> signatureFormatterConnectorAttributes) {
        TimestampingWorkflowDto wf = new TimestampingWorkflowDto();
        setFormatterRef(profile.getSignatureFormatterConnectorUuid(), wf::setSignatureFormatterConnector);
        wf.setSignatureFormatterConnectorAttributes(safeList(signatureFormatterConnectorAttributes));
        wf.setQualifiedTimestamp(profile.getQualifiedTimestamp());
        wf.setDefaultPolicyId(profile.getDefaultPolicyId());
        wf.setAllowedPolicyIds(safeList(profile.getAllowedPolicyIds()));
        if (profile.getAllowedDigestAlgorithms() != null && !profile.getAllowedDigestAlgorithms().isEmpty()) {
            wf.setAllowedDigestAlgorithms(
                    profile.getAllowedDigestAlgorithms().stream()
                            .map(DigestAlgorithm::findByCode)
                            .toList()
            );
        }
        return wf;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Model-layer workflow builders
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a {@link RawSigningWorkflow} variant based on the signing scheme.
     */
    private static RawSigningWorkflow buildRawSigningWorkflow(SigningProfile profile) {
        return profile.getSigningScheme() == SigningScheme.MANAGED
                ? new ManagedRawSigningWorkflow()
                : new DelegatedRawSigningWorkflow();
    }

    /**
     * Builds a {@link ContentSigningWorkflow} variant based on the signing scheme.
     */
    private static ContentSigningWorkflow buildContentSigningWorkflow(
            SigningProfile profile, List<RequestAttribute> signatureFormatterConnectorAttributes) {
        return switch (profile.getSigningScheme()) {
            case MANAGED -> new ManagedContentSigningWorkflow(
                    profile.getSignatureFormatterConnectorUuid(),
                    safeList(signatureFormatterConnectorAttributes));
            case DELEGATED -> new DelegatedContentSigningWorkflow();
        };
    }

    /**
     * Builds a {@link TimestampingWorkflow} variant based on the signing scheme.
     */
    private static TimestampingWorkflow buildTimestampingWorkflow(
            SigningProfile profile, List<RequestAttribute> signatureFormatterConnectorAttributes) {
        List<DigestAlgorithm> algos = profile.getAllowedDigestAlgorithms() != null
                ? profile.getAllowedDigestAlgorithms().stream().map(DigestAlgorithm::findByCode).toList()
                : List.of();
        return switch (profile.getSigningScheme()) {
            case MANAGED -> new ManagedTimestampingWorkflow(
                    profile.getSignatureFormatterConnectorUuid(),
                    safeList(signatureFormatterConnectorAttributes),
                    profile.getQualifiedTimestamp(),
                    null, // timeQualityConfiguration — not persisted on the entity yet
                    profile.getDefaultPolicyId(),
                    safeList(profile.getAllowedPolicyIds()),
                    algos,
                    null); // validateTokenSignature — not persisted on the entity yet
            case DELEGATED -> new DelegatedTimestampingWorkflow(
                    profile.getDefaultPolicyId(),
                    safeList(profile.getAllowedPolicyIds()),
                    algos,
                    null); // validateTokenSignature — not persisted on the entity yet
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Model-layer helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a concrete {@link SigningSchemeModel} record from the entity, resolving DAO associations
     * ({@code Certificate}, {@code RaProfile}, {@code TokenProfile}) that are already loaded on the entity via JPA lazy initialization.
     */
    private static SigningSchemeModel buildSchemeModel(SigningProfile profile,
                                                       List<RequestAttribute> signingOperationAttributes) {
        return switch (profile.getSigningScheme()) {
            case DELEGATED -> new DelegatedSigning(
                    profile.getDelegatedSignerConnectorUuid(),
                    List.of()); // connector attributes are not persisted separately at this stage
            case MANAGED -> {
                if (profile.getManagedSigningType() == null) {
                    throw new IllegalStateException(
                            "MANAGED signing profile " + profile.getUuid() + " has no managedSigningType");
                }
                yield switch (profile.getManagedSigningType()) {
                    case STATIC_KEY -> new StaticKeyManagedSigning(
                            profile.getCertificate(),
                            safeList(signingOperationAttributes));
                    case ONE_TIME_KEY -> new OneTimeKeyManagedSigning(
                            profile.getRaProfile(),
                            profile.getTokenProfile(),
                            profile.getCsrTemplateUuid(),
                            safeList(signingOperationAttributes));
                };
            }
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Shared utilities
    // ──────────────────────────────────────────────────────────────────────────

    private static void setFormatterRef(UUID signatureFormatterConnectorUuid, Consumer<NameAndUuidDto> setter) {
        if (signatureFormatterConnectorUuid != null) {
            NameAndUuidDto ref = new NameAndUuidDto();
            ref.setUuid(signatureFormatterConnectorUuid.toString());
            setter.accept(ref);
        }
    }

    private static <T> List<T> safeList(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }
}
