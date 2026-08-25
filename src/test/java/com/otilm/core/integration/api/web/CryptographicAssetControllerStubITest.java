package com.otilm.core.integration.api.web;

import com.otilm.api.exception.NotSupportedException;
import com.otilm.api.interfaces.core.web.CryptographicAssetController;
import com.otilm.api.interfaces.core.web.StatisticsController;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.core.auth.Resource;
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
 * bean, but the read model behind them is not built yet. This pins both halves of that state: every operation refuses
 * with {@link NotSupportedException} (HTTP 501), and the resource still reaches the auth service with its list and
 * detail actions, so roles can be defined against it before the data arrives.
 *
 * <p>
 * When the inventory is implemented, the refusal assertions are what should fail first.
 */
class CryptographicAssetControllerStubITest extends BaseSpringBootTest {

    @Autowired
    private CryptographicAssetController cryptographicAssetController;

    @Autowired
    private StatisticsController statisticsController;

    @Autowired
    private ContextRefreshListener contextRefreshListener;

    @Test
    void everyInventoryOperationRefusesAsNotImplemented() {
        assertThatThrownBy(() -> cryptographicAssetController.listCryptographicAssets(new SearchRequestDto()))
                .isInstanceOf(NotSupportedException.class);
        assertThatThrownBy(() -> cryptographicAssetController.getCryptographicAsset(UUID.randomUUID()))
                .isInstanceOf(NotSupportedException.class);
        assertThatThrownBy(() -> cryptographicAssetController.getSearchableFieldInformation())
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
    }
}
