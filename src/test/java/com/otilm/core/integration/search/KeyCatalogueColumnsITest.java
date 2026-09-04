package com.otilm.core.integration.search;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.client.connector.v2.ConnectorVersion;
import com.otilm.api.model.common.enums.cryptography.KeyAlgorithm;
import com.otilm.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.ConnectorStatus;
import com.otilm.api.model.core.cryptography.key.KeyItemDto;
import com.otilm.api.model.core.search.FilterConditionOperator;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.api.model.core.search.SortDirection;
import com.otilm.core.dao.entity.Connector;
import com.otilm.core.dao.entity.CryptographicKey;
import com.otilm.core.dao.entity.CryptographicKeyItem;
import com.otilm.core.dao.entity.TokenInstanceReference;
import com.otilm.core.dao.entity.TokenProfile;
import com.otilm.core.dao.repository.ConnectorRepository;
import com.otilm.core.dao.repository.CryptographicKeyItemRepository;
import com.otilm.core.dao.repository.TokenInstanceReferenceRepository;
import com.otilm.core.dao.repository.TokenProfileRepository;
import com.otilm.core.enums.FilterField;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.CryptographicKeyExternalService;
import com.otilm.core.util.BaseSpringBootTest;
import com.otilm.core.util.WireMockPorts;
import com.otilm.core.util.seeders.CryptographicKeySeeder;
import com.otilm.core.util.seeders.CryptographicKeySeeder.KeyItemSpec;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.otilm.core.util.builders.SearchFilterRequestDtoBuilder.aPropertyFilter;

/**
 * The key columns the inventory renders but the catalogue did not carry.
 *
 * <p>
 * A column the catalogue does not publish can be neither picked, renamed nor sorted, however plainly the page shows it.
 * Publishing them makes them filters as well, which the keys listing had for neither field before.
 */
class KeyCatalogueColumnsITest extends BaseSpringBootTest {

    @Autowired
    private CryptographicKeyExternalService cryptographicKeyService;

    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;

    @Autowired
    private TokenProfileRepository tokenProfileRepository;

    @Autowired
    private CryptographicKeySeeder keySeeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TokenProfile tokenProfile;

    private TokenInstanceReference tokenInstanceReference;

    /** The keys catalogue lists owners, so it reaches the user-management service the stub stands in for. */
    private WireMockServer authService;

    @BeforeEach
    void loadData() {
        authService = new WireMockServer(WireMockPorts.AUTH_SERVICE);
        authService.start();
        WireMock.configureFor("localhost", authService.port());
        authService
                .stubFor(WireMock
                        .get(WireMock.urlPathEqualTo("/auth/users"))
                        .willReturn(WireMock.okJson("{\"data\": []}")));

        Connector connector = new Connector();
        connector.setName("key-catalogue-connector");
        connector.setUrl("http://localhost:0/key-catalogue");
        connector.setVersion(ConnectorVersion.V2);
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.saveAndFlush(connector);

        tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setName("key-catalogue-token");
        tokenInstanceReference.setTokenInstanceUuid("1l");
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReference.setStatus(TokenInstanceStatus.CONNECTED);
        tokenInstanceReferenceRepository.saveAndFlush(tokenInstanceReference);

        tokenProfile = new TokenProfile();
        tokenProfile.setName("key-catalogue-profile");
        tokenProfile.setTokenInstanceReference(tokenInstanceReference);
        tokenProfile.setTokenInstanceName("key-catalogue-token");
        tokenProfile.setEnabled(true);
        tokenProfileRepository.saveAndFlush(tokenProfile);
    }

    @AfterEach
    void stopAuthService() {
        authService.stop();
    }

    @Test
    void theCatalogueOffersTheEnabledColumn() {
        SearchFieldDataDto enabled = field(FilterField.CKI_ENABLED.name()).orElseThrow();

        Assertions.assertEquals(true, enabled.getSortable());
        // The heading the keys inventory already ships: a stored view refreshes its heading from the catalogue, so a
        // different label here would rename the column of a saved view.
        Assertions.assertEquals("Status", enabled.getFieldLabel());
    }

    @Test
    void theCatalogueOffersTheCreatedColumn() {
        SearchFieldDataDto created = field(FilterField.CKI_CREATED.name()).orElseThrow();

        Assertions.assertEquals(true, created.getSortable());
        Assertions.assertEquals("Creation Date", created.getFieldLabel());
    }

    /**
     * The catalogue is assembled by naming fields one at a time, so a field added to {@code FilterField} and forgotten
     * there is exactly the gap this issue closes - and nothing would report it. Pinned by count, as the secret and
     * signing-record listings pin theirs.
     */
    @Test
    void theCatalogueCarriesEveryKeyField() {
        List<SearchFieldDataDto> published = cryptographicKeyService
                .getSearchableFieldInformation()
                .stream()
                .filter(group -> group.getFilterFieldSource() == FilterFieldSource.PROPERTY)
                .flatMap(group -> group.getSearchFieldData().stream())
                .toList();

        Assertions.assertEquals(FilterField.getEnumsForResource(Resource.CRYPTOGRAPHIC_KEY).size(), published.size());
    }

    @Test
    void theListingCanBeFilteredByEnabled() {
        seedItem("enabled-one", true, OffsetDateTime.now().minusDays(3));
        seedItem("enabled-two", true, OffsetDateTime.now().minusDays(2));
        seedItem("disabled-one", false, OffsetDateTime.now().minusDays(1));

        SearchFilterRequestDto filter = aPropertyFilter(FilterField.CKI_ENABLED, FilterConditionOperator.EQUALS, false);

        Assertions.assertEquals(List.of("disabled-one"), listNames(filter, null));
    }

    @Test
    void theListingCanBeOrderedByCreated() {
        seedItem("oldest", true, OffsetDateTime.now().minusDays(3));
        seedItem("newest", true, OffsetDateTime.now().minusDays(1));
        seedItem("middle", true, OffsetDateTime.now().minusDays(2));

        SearchSortRequestDto ascending = new SearchSortRequestDto(FilterFieldSource.PROPERTY,
                FilterField.CKI_CREATED.name(), SortDirection.ASC);

        Assertions.assertEquals(List.of("oldest", "middle", "newest"), listNames(null, ascending));
    }

    private Optional<SearchFieldDataDto> field(String identifier) {
        List<SearchFieldDataByGroupDto> catalogue = cryptographicKeyService.getSearchableFieldInformation();
        return catalogue
                .stream()
                .flatMap(group -> group.getSearchFieldData().stream())
                .filter(item -> identifier.equals(item.getFieldIdentifier()))
                .findFirst();
    }

    private List<String> listNames(SearchFilterRequestDto filter, SearchSortRequestDto sort) {
        SearchRequestDto request = new SearchRequestDto();
        request.setPageNumber(1);
        request.setItemsPerPage(10);
        if (filter != null) {
            request.setFilters(List.of(filter));
        }
        request.setSort(sort);

        return cryptographicKeyService
                .listCryptographicKeys(SecurityFilter.create(), request)
                .getCryptographicKeys()
                .stream()
                .map(KeyItemDto::getName)
                .toList();
    }

    /**
     * Creation time is written straight to the key's audited column, for two reasons. It is a
     * {@code @CreationTimestamp} and not updatable, so rows saved inside one test would otherwise share the instant
     * auditing stamps on them and an ordering over equal values would be decided by the uuid tie-break rather than by
     * the field under test. And it is the key's own time, not the item's, because that is what the listing renders as
     * {@code creationTime} - the item's {@code created_at} is deliberately left at its default here, so a definition
     * pointing at the item instead would order these rows by an unrelated instant and fail.
     */
    private void seedItem(String name, boolean enabled, OffsetDateTime created) {
        CryptographicKey key = keySeeder
                .seedKey(name, tokenProfile, tokenInstanceReference, KeyItemSpec.signingPrivateKey(KeyAlgorithm.RSA));
        CryptographicKeyItem item = key.getItems().iterator().next();
        item.setName(name);
        item.setEnabled(enabled);
        cryptographicKeyItemRepository.saveAndFlush(item);

        jdbcTemplate.update("UPDATE cryptographic_key SET i_cre = ? WHERE uuid = ?", created, key.getUuid());
    }
}
