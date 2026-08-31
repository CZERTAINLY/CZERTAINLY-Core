package com.otilm.core.integration.api.web;

import com.otilm.api.exception.NotSupportedException;
import com.otilm.api.interfaces.core.web.CryptographicAssetController;
import com.otilm.api.interfaces.core.web.StatisticsController;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDto;
import com.otilm.core.auth.ContextRefreshListener;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.model.auth.ResourceSyncRequestDto;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cryptographic asset inventory endpoints exist so the contract ratified in interfaces#909 is served by a real
 * bean. The read model now serves list and searchable-fields as real reads over the deduplicated cross-CBOM asset
 * projection; detail and statistics stay {@link NotSupportedException} refusals (HTTP 501) until core#2145 builds them.
 * The resource still reaches the auth service with its list and detail actions, so roles can be defined against it
 * independently of which operations are implemented.
 *
 * <p>
 * When detail and statistics are implemented, the refusal assertions below are what should fail first.
 */
class CryptographicAssetControllerITest extends BaseSpringBootTest {

    @Autowired
    private CryptographicAssetController cryptographicAssetController;

    @Autowired
    private StatisticsController statisticsController;

    @Autowired
    private ContextRefreshListener contextRefreshListener;

    @Test
    void listAndSearchableFieldsAreServedWhileDetailAndStatisticsRefuse() {
        PaginationResponseDto<CryptographicAssetDto> page = cryptographicAssetController
                .listCryptographicAssets(new SearchRequestDto());
        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalItems()).isZero();

        assertThat(cryptographicAssetController.getSearchableFieldInformation()).isNotEmpty();

        UUID anyUuid = UUID.randomUUID();
        assertThatThrownBy(() -> cryptographicAssetController.getCryptographicAsset(anyUuid))
                .isInstanceOf(NotSupportedException.class);
        assertThatThrownBy(() -> statisticsController.getCryptographicAssetStatistics())
                .isInstanceOf(NotSupportedException.class);
    }

    @Test
    void theResourceIsRegisteredWithItsReadActions() {
        ResourceSyncRequestDto synced = contextRefreshListener
                .getResources()
                .stream()
                .filter(resource -> resource.getName().getCode().equals(Resource.CRYPTO_ASSET.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resource not synced: " + Resource.CRYPTO_ASSET.getCode()));

        assertThat(synced.getActions()).contains(ResourceAction.LIST.getCode(), ResourceAction.DETAIL.getCode());
        assertThat(synced.getListObjectsEndpoint())
                .describedAs("the one auth-sync behaviour @AuthEndpoint adds: the advertised object listing route")
                .isEqualTo("/v1/cryptoAssets");
    }
}
