package com.otilm.core.integration.api.web;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.service.writer.cbom.CryptoAssetWriter;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @ExternalAuthorization} over HTTP for the cryptographic asset inventory's list and searchable-fields
 * endpoints, mirroring {@link ExternalAuthorizationHttpITest}'s style: the real security filter chain, the
 * method-security advisor, and {@code ExceptionHandlingAdvice.handleAccessDeniedException}.
 *
 * <p>
 * The permitted-path assertions also carry the wire-level identity-key criterion: neither endpoint's raw response ever
 * mentions it.
 */
@AutoConfigureMockMvc
class CryptographicAssetAuthorizationHttpITest extends BaseSpringBootTest {

    private static final String LIST_ENDPOINT = "/v1/cryptoAssets";

    private static final String SEARCHABLE_FIELDS_ENDPOINT = "/v1/cryptoAssets/search";

    // The same detection rule IdentityKeyExposureFence uses, applied to the live wire response. A bare "identity"
    // substring is not it: CBOM_ASSET_RULESET_VERSION's own label is "Identity Rule Set Version" -- "identity" as an
    // ordinary adjective, naming the ruleset that computes identity, not the guarded value itself.
    private static final Pattern IDENTITY_KEY = Pattern
            .compile("identity[_\\-\\s]?key|absorbed[_\\-\\s]?key|canonical[_\\-\\s]?key", Pattern.CASE_INSENSITIVE);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CryptoAssetWriter assetWriter;

    @BeforeEach
    void seedAsset() {
        assetWriter
                .upsertIdentity(new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "ECDSA",
                        "1.2.840.10045.4.3.2", "ecdsa", "signature", "P-256", "secp256r1", null, null, null), null);
    }

    @Test
    void deniesBothOperationsWhenTheListActionIsDenied() throws Exception {
        denyResourceAccess(Resource.CRYPTO_ASSET, ResourceAction.LIST);

        mockMvc
                .perform(post(LIST_ENDPOINT).contentType("application/json").content("{}"))
                .andExpectAll(status().isForbidden(), jsonPath("$.code").value("ACCESS_DENIED"));
        // Both operations share the LIST action.
        mockMvc.perform(get(SEARCHABLE_FIELDS_ENDPOINT)).andExpect(status().isForbidden());
    }

    @Test
    void servesBothOperationsWithoutMentioningTheIdentityKeyWhenPermitted() throws Exception {
        String listResponse = mockMvc
                .perform(post(LIST_ENDPOINT).contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(listResponse).doesNotContainPattern(IDENTITY_KEY);

        String searchableFieldsResponse = mockMvc
                .perform(get(SEARCHABLE_FIELDS_ENDPOINT))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(searchableFieldsResponse).doesNotContainPattern(IDENTITY_KEY);
    }
}
