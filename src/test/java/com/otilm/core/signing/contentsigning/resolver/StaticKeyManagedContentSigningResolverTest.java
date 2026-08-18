package com.otilm.core.signing.contentsigning.resolver;

import com.otilm.api.clients.ApiClientConnectorInfo;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.otilm.api.model.core.signing.SigningProtocol;
import com.otilm.core.model.signing.SigningCertificateBuilder;
import com.otilm.core.model.signing.SigningProfileModel;
import com.otilm.core.model.signing.SigningRecordPolicyModelBuilder;
import com.otilm.core.model.signing.resolved.ResolvedManagedContentSigningProfile;
import com.otilm.core.model.signing.resolved.ResolvedManagedScheme;
import com.otilm.core.model.signing.resolved.ResolvedStaticKeyManagedSigning;
import com.otilm.core.model.signing.scheme.StaticKeyManagedSigning;
import com.otilm.core.model.signing.workflow.ManagedContentSigningWorkflow;
import com.otilm.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.otilm.core.model.signing.workflow.SigningWorkflow;
import com.otilm.core.service.v2.ConnectorInternalService;
import com.otilm.core.signing.engine.error.SigningEngineException;
import com.otilm.core.signing.engine.error.SigningEngineFailure;
import com.otilm.core.signing.engine.resolver.ManagedSchemeResolver;
import com.otilm.core.util.builders.RequestAttributeV3Builder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StaticKeyManagedContentSigningResolverTest {

    @Mock
    private ManagedSchemeResolver schemeResolver;
    @Mock
    private ConnectorInternalService connectorService;

    @InjectMocks
    private StaticKeyManagedContentSigningResolver resolver;

    private static final UUID PROFILE_UUID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID FORMATTING_CONNECTOR_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final RequestAttribute A_FORMAT_ATTRIBUTE = RequestAttributeV3Builder
            .aCustomAttribute()
            .withUuid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
            .withName("format-attribute")
            .withStringContent("value")
            .build();
    private static final ApiClientConnectorInfo CONNECTOR_INFO = mock(ApiClientConnectorInfo.class);

    @Test
    void supportsOnlyManagedContentSigningWorkflows() {
        // given / when / then
        assertThat(resolver.supports(aManagedContentSigningProfile())).isTrue();
        assertThat(resolver.supports(aManagedTimestampingProfile())).isFalse();
    }

    @Test
    void resolvesTheSchemeAndTheFormattingConnector() throws Exception {
        // given
        ResolvedManagedScheme resolvedScheme = givenSchemeResolutionSucceeds();
        SigningProfileModel<?, ?> model = aManagedContentSigningProfile();
        given(connectorService.getConnectorForApiClient(FORMATTING_CONNECTOR_UUID)).willReturn(CONNECTOR_INFO);

        // when
        ResolvedManagedContentSigningProfile resolved = (ResolvedManagedContentSigningProfile) resolver.resolve(model);

        // then
        assertThat(resolved.signatureFormattingConnector()).isSameAs(CONNECTOR_INFO);
        assertThat(resolved.resolvedScheme()).isSameAs(resolvedScheme);
        assertThat(resolved.signatureFormattingConnectorAttributes()).containsExactly(A_FORMAT_ATTRIBUTE);
        verify(schemeResolver).resolve("cs-profile", model.signingScheme());
    }

    @Test
    void mapsAllProfileFieldsOntoTheResolvedProfile() throws Exception {
        // given
        givenSchemeResolutionSucceeds();
        given(connectorService.getConnectorForApiClient(FORMATTING_CONNECTOR_UUID)).willReturn(CONNECTOR_INFO);

        // when
        ResolvedManagedContentSigningProfile resolved = (ResolvedManagedContentSigningProfile) resolver
                .resolve(aManagedContentSigningProfile());

        // then
        assertThat(resolved.uuid()).isEqualTo(PROFILE_UUID);
        assertThat(resolved.name()).isEqualTo("cs-profile");
        assertThat(resolved.description()).isEqualTo("a description");
        assertThat(resolved.version()).isEqualTo(1);
        assertThat(resolved.enabled()).isTrue();
        assertThat(resolved.enabledProtocols()).containsExactly(SigningProtocol.TSP);
        assertThat(resolved.workflowType()).isEqualTo(SigningWorkflowType.CONTENT_SIGNING);
    }

    @Test
    void reportsAMissingFormattingConnectorAsMisconfiguration() throws Exception {
        // given
        givenSchemeResolutionSucceeds();
        given(connectorService.getConnectorForApiClient(FORMATTING_CONNECTOR_UUID))
                .willThrow(new NotFoundException("connector", FORMATTING_CONNECTOR_UUID));

        // when / then
        assertThatThrownBy(() -> resolver.resolve(aManagedContentSigningProfile()))
                .isInstanceOf(SigningEngineException.class)
                .satisfies(e -> assertThat(((SigningEngineException) e).failure())
                        .isEqualTo(SigningEngineFailure.MISCONFIGURED));
    }

    @Test
    void propagatesSigningEngineException_whenSchemeResolutionFails() throws Exception {
        // given — scheme resolution (certificate, key items, chain) is delegated to ManagedSchemeResolver
        SigningEngineException schemeFailure = new SigningEngineException(SigningEngineFailure.MISCONFIGURED,
                "unsupported scheme", "The system is misconfigured.");
        given(schemeResolver.resolve(any(), any())).willThrow(schemeFailure);

        // when / then
        assertThatThrownBy(() -> resolver.resolve(aManagedContentSigningProfile())).isSameAs(schemeFailure);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private ResolvedManagedScheme givenSchemeResolutionSucceeds() throws Exception {
        ResolvedManagedScheme resolvedScheme = new ResolvedStaticKeyManagedSigning(SigningCertificateBuilder.valid(),
                List.of(), null, List.of());
        given(schemeResolver.resolve(any(), any())).willReturn(resolvedScheme);
        return resolvedScheme;
    }

    private static SigningProfileModel<?, ?> aManagedContentSigningProfile() {
        return aProfile(new ManagedContentSigningWorkflow(FORMATTING_CONNECTOR_UUID, List.of(A_FORMAT_ATTRIBUTE)));
    }

    private static SigningProfileModel<?, ?> aManagedTimestampingProfile() {
        return aProfile(new ManagedTimestampingWorkflow(FORMATTING_CONNECTOR_UUID, List.of(), Boolean.TRUE, null,
                "1.2.3.4.5", List.of(), List.of(), Boolean.FALSE));
    }

    private static SigningProfileModel<?, ?> aProfile(SigningWorkflow workflow) {
        return new SigningProfileModel<>(PROFILE_UUID, "cs-profile", "a description", 1, true,
                List.of(SigningProtocol.TSP), UUID.fromString("88888888-8888-8888-8888-888888888888"), workflow,
                new StaticKeyManagedSigning(UUID.randomUUID(), List.of()),
                SigningRecordPolicyModelBuilder.notRecording().build());
    }
}
