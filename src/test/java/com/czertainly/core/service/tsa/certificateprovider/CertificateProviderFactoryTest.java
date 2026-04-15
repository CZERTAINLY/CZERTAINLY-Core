package com.czertainly.core.service.tsa.certificateprovider;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.core.model.signing.scheme.DelegatedSigning;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateProviderFactoryTest {

    @Mock
    CertificateProvider supportingProvider;

    @Mock
    CertificateProvider nonSupportingProvider;

    // ── getProvider() ─────────────────────────────────────────────────────────

    @Test
    void getProvider_returnsProvider_whenProviderSupportsScheme() throws TspException {
        // given
        var scheme = new DelegatedSigning(UUID.randomUUID(), List.of());
        when(nonSupportingProvider.supports(scheme)).thenReturn(false);
        when(supportingProvider.supports(scheme)).thenReturn(true);
        var factory = new CertificateProviderFactory(List.of(nonSupportingProvider, supportingProvider));

        // when
        CertificateProvider result = factory.getProvider(scheme);

        // then
        assertSame(supportingProvider, result);
    }

    @Test
    void getProvider_throwsTspException_whenNoProviderSupportsScheme() {
        // given
        var scheme = new DelegatedSigning(UUID.randomUUID(), List.of());
        when(nonSupportingProvider.supports(scheme)).thenReturn(false);
        var factory = new CertificateProviderFactory(List.of(nonSupportingProvider));

        // when / then
        var exception = assertThrows(TspException.class, () -> factory.getProvider(scheme));
        assertEquals(TspFailureInfo.SYSTEM_FAILURE, exception.getFailureInfo());
    }
}
