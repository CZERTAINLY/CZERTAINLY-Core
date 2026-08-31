package com.otilm.core.integration.api.web;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.core.cbom.asset.AssetRowKeys;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.dao.entity.Cbom;
import com.otilm.core.dao.repository.CbomRepository;
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

    @Autowired
    private CbomRepository cbomRepository;

    private CryptoAssetIdentityFields seededFields;

    @BeforeEach
    void seedAsset() {
        seededFields = new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "ECDSA", "1.2.840.10045.4.3.2",
                "ecdsa", "signature", "P-256", "secp256r1", null, null, null);
        assetWriter.upsertIdentity(AssetRowKeys.forFields(seededFields), seededFields, null);
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
        // The positive control: the seeded row must actually reach the response, or a filter/security regression
        // that empties the page would satisfy every doesNotContain assertion below vacuously.
        String listResponse = mockMvc
                .perform(post(LIST_ENDPOINT).contentType("application/json").content("{}"))
                .andExpectAll(status().isOk(), jsonPath("$.totalItems").value(1),
                        jsonPath("$.items[0].name").value("ecdsa"))
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

        // The name pattern catches field names; a wire leak would realistically be the VALUE. The calculator can
        // recompute this row's exact key from the same fixture input, so assert the strongest possible fact: the
        // literal key appears in neither raw response.
        String exactIdentityKey = AssetRowKeys.forFields(seededFields);
        assertThat(listResponse).doesNotContain(exactIdentityKey);
        assertThat(searchableFieldsResponse).doesNotContain(exactIdentityKey);
    }

    @Test
    void refusesInvalidPagingWithAShapedUnprocessableEntity() throws Exception {
        mockMvc
                .perform(post(LIST_ENDPOINT).contentType("application/json").content("{\"pageNumber\":0}"))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * The source-CBOM value list crosses a resource gate -- CBOM serial numbers belong to the CBOM resource -- so it is
     * scoped by the caller's own {@code cboms:list} access rather than served platform-wide: a caller holding only
     * {@code cryptoAssets:list} still gets the searchable fields, but that one value list comes back empty.
     */
    @Test
    void scopesTheSourceCbomValueListByTheCallersCbomAccess() throws Exception {
        Cbom cbom = new Cbom();
        cbom.setSerialNumber("urn:uuid:scoped");
        cbom.setVersion(1);
        cbom.setSpecVersion("1.7");
        cbomRepository.save(cbom);

        String permitted = mockMvc
                .perform(get(SEARCHABLE_FIELDS_ENDPOINT))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(permitted).contains("urn:uuid:scoped");

        denyObjectAccess(Resource.CBOM, ResourceAction.LIST);
        String scoped = mockMvc
                .perform(get(SEARCHABLE_FIELDS_ENDPOINT))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(scoped).doesNotContain("urn:uuid:scoped");
        assertThat(scoped)
                .describedAs("the fields themselves still serve; only the cross-resource value list is scoped")
                .contains("CBOM_ASSET_SOURCE_CBOM");
    }
}
