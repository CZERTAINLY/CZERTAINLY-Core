package com.otilm.core.integration.search;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.client.connector.v2.ConnectorVersion;
import com.otilm.api.model.common.enums.cryptography.KeyAlgorithm;
import com.otilm.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.otilm.api.model.core.connector.ConnectorStatus;
import com.otilm.api.model.core.cryptography.key.KeyItemDto;
import com.otilm.api.model.core.search.FilterFieldSource;
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
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.CryptographicKeyExternalService;
import com.otilm.core.util.BaseSpringBootTest;
import com.otilm.core.util.seeders.CryptographicKeySeeder;
import com.otilm.core.util.seeders.CryptographicKeySeeder.KeyItemSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The key listing fetches in two phases - a page of uuids in the requested order, then the items by {@code uuid IN
 * (...)}. The second query used to name its own ordering, which replaced the first's, so these cases are what catches a
 * sort that is accepted and then thrown away.
 */
class KeyListingSortITest extends BaseSpringBootTest {

    private static final List<String> SEEDED_NAMES = List.of("alpha", "bravo", "charlie", "delta");

    @Autowired
    private CryptographicKeyExternalService cryptographicKeyService;

    @Autowired
    private CryptographicKeySeeder keySeeder;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;

    @Autowired
    private TokenProfileRepository tokenProfileRepository;

    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    @BeforeEach
    void loadData() {
        Connector connector = new Connector();
        connector.setName("key-sort-connector");
        connector.setUrl("http://localhost:0/key-sort");
        connector.setVersion(ConnectorVersion.V2);
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.saveAndFlush(connector);

        TokenInstanceReference tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setName("key-sort-token");
        tokenInstanceReference.setTokenInstanceUuid("1l");
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReference.setStatus(TokenInstanceStatus.CONNECTED);
        tokenInstanceReferenceRepository.saveAndFlush(tokenInstanceReference);

        TokenProfile tokenProfile = new TokenProfile();
        tokenProfile.setName("key-sort-profile");
        tokenProfile.setTokenInstanceReference(tokenInstanceReference);
        tokenProfile.setTokenInstanceName("key-sort-token");
        tokenProfile.setEnabled(true);
        tokenProfileRepository.saveAndFlush(tokenProfile);

        // Seeded newest-last, and each key carries one item, so the listing's default createdAt DESC order is the
        // reverse of the alphabetical one the sort asks for. A sort that never reached the query would return the
        // default order and pass an assertion that only checked the set of names.
        seedNamedItem("delta", tokenProfile, tokenInstanceReference);
        seedNamedItem("alpha", tokenProfile, tokenInstanceReference);
        seedNamedItem("charlie", tokenProfile, tokenInstanceReference);
        seedNamedItem("bravo", tokenProfile, tokenInstanceReference);
    }

    private void seedNamedItem(String name, TokenProfile tokenProfile, TokenInstanceReference tokenInstanceReference) {
        CryptographicKey key = keySeeder
                .seedKey(name, tokenProfile, tokenInstanceReference, KeyItemSpec.signingPrivateKey(KeyAlgorithm.RSA));
        CryptographicKeyItem item = key.getItems().iterator().next();
        item.setName(name);
        cryptographicKeyItemRepository.saveAndFlush(item);
    }

    private List<String> listNames(SearchSortRequestDto sort, int pageNumber, int itemsPerPage) {
        SearchRequestDto request = new SearchRequestDto();
        request.setPageNumber(pageNumber);
        request.setItemsPerPage(itemsPerPage);
        request.setSort(sort);

        return cryptographicKeyService
                .listCryptographicKeys(SecurityFilter.create(), request)
                .getCryptographicKeys()
                .stream()
                .map(KeyItemDto::getName)
                .toList();
    }

    private static SearchSortRequestDto sortByName(SortDirection direction) {
        return new SearchSortRequestDto(FilterFieldSource.PROPERTY, "CKI_NAME", direction);
    }

    @Test
    void ordersTheListingByTheRequestedFieldAscending() {
        Assertions.assertEquals(SEEDED_NAMES, listNames(sortByName(SortDirection.ASC), 1, 10));
    }

    @Test
    void ordersTheListingByTheRequestedFieldDescending() {
        Assertions.assertEquals(SEEDED_NAMES.reversed(), listNames(sortByName(SortDirection.DESC), 1, 10));
    }

    /**
     * The ordering has to span the result set rather than each page, so the second page continues where the first ended
     * instead of sorting its own rows.
     */
    @Test
    void pagingWalksTheSortedSet() {
        List<String> paged = new ArrayList<>();
        paged.addAll(listNames(sortByName(SortDirection.ASC), 1, 2));
        paged.addAll(listNames(sortByName(SortDirection.ASC), 2, 2));

        Assertions.assertEquals(SEEDED_NAMES, paged);
    }

    /**
     * The listing's own default is createdAt DESC, and it has to survive untouched for a request that names no sort.
     * The expectation is read back from the seeded rows rather than written out, because four rows saved in one test
     * can share a createdAt, and then the uuid tie-break rather than the seeding order decides.
     */
    @Test
    void aRequestWithoutASortKeepsTheListingDefaultOrder() {
        List<String> byDefaultOrder = seededItems()
                .stream()
                .sorted(Comparator
                        .comparing(CryptographicKeyItem::getCreatedAt)
                        .reversed()
                        .thenComparing(CryptographicKeyItem::getUuid))
                .map(CryptographicKeyItem::getName)
                .toList();

        Assertions.assertEquals(byDefaultOrder, listNames(null, 1, 10));
    }

    private List<CryptographicKeyItem> seededItems() {
        return cryptographicKeyItemRepository
                .findAll()
                .stream()
                .filter(item -> SEEDED_NAMES.contains(item.getName()))
                .toList();
    }
}
