package com.otilm.core.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.api.exception.AlreadyExistException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.auth.UserDto;
import com.otilm.api.model.core.auth.UserProfileDto;
import com.otilm.api.model.core.listview.ListViewColumnDto;
import com.otilm.api.model.core.listview.ListViewDto;
import com.otilm.api.model.core.listview.ListViewRequestDto;
import com.otilm.api.model.core.listview.ListViewUpdateRequestDto;
import com.otilm.api.model.core.logging.enums.AuthMethod;
import com.otilm.api.model.core.search.FilterConditionOperator;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SortDirection;
import com.otilm.core.dao.entity.ListView;
import com.otilm.core.dao.repository.ListViewRepository;
import com.otilm.core.security.authn.PlatformAuthenticationToken;
import com.otilm.core.security.authn.PlatformUserDetails;
import com.otilm.core.security.authn.client.AuthenticationInfo;
import com.otilm.core.service.ListViewExternalService;
import com.otilm.core.service.ListViewInternalService;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

class ListViewServiceITest extends BaseSpringBootTest {

    @Autowired
    private ListViewExternalService listViewService;

    @Autowired
    private ListViewInternalService listViewInternalService;

    @Autowired
    private ListViewRepository listViewRepository;

    private UUID user;
    private UUID otherUser;

    @BeforeEach
    void setUpUsers() {
        user = UUID.randomUUID();
        otherUser = UUID.randomUUID();
        authenticateAs(user);
    }

    /**
     * A view has to follow the user across sessions, so nothing about it may live in the session: signing in again as
     * the same user has to find it, and signing in as anyone else must not.
     */
    private void authenticateAs(UUID userUuid) {
        UserProfileDto profile = new UserProfileDto();
        UserDto userDto = new UserDto();
        userDto.setUuid(userUuid.toString());
        userDto.setUsername("user-" + userUuid);
        profile.setUser(userDto);

        String rawData;
        try {
            rawData = new ObjectMapper().writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        AuthenticationInfo info = new AuthenticationInfo(AuthMethod.USER_PROXY, userDto.getUuid(),
                userDto.getUsername(), List.of(), rawData);
        SecurityContextHolder
                .getContext()
                .setAuthentication(new PlatformAuthenticationToken(new PlatformUserDetails(info)));
    }

    private static ListViewColumnDto column(String fieldIdentifier) {
        return new ListViewColumnDto(FilterFieldSource.PROPERTY, fieldIdentifier, null);
    }

    private static ListViewColumnDto column(String fieldIdentifier, String label) {
        return new ListViewColumnDto(FilterFieldSource.PROPERTY, fieldIdentifier, label);
    }

    private static ListViewRequestDto request(String name, ListViewColumnDto... columns) {
        ListViewRequestDto request = new ListViewRequestDto();
        request.setResource(Resource.CERTIFICATE);
        request.setName(name);
        request.setColumns(List.of(columns));
        return request;
    }

    private static ListViewUpdateRequestDto update(String name, ListViewColumnDto... columns) {
        ListViewUpdateRequestDto request = new ListViewUpdateRequestDto();
        request.setName(name);
        request.setColumns(List.of(columns));
        return request;
    }

    private static List<String> identifiersOf(ListViewDto view) {
        return view.getColumns().stream().map(ListViewColumnDto::getFieldIdentifier).toList();
    }

    @Test
    void aViewSurvivesTheSessionAndIsInvisibleToOtherUsers() throws AlreadyExistException {
        ListViewDto created = listViewService.createView(request("Expiry watch", column("COMMON_NAME")));

        authenticateAs(user);
        Assertions
                .assertEquals(List.of(created.getUuid()),
                        listViewService.listViews(Resource.CERTIFICATE).stream().map(ListViewDto::getUuid).toList());

        authenticateAs(otherUser);
        Assertions.assertTrue(listViewService.listViews(Resource.CERTIFICATE).isEmpty());
        Assertions.assertTrue(listViewService.listViews(null).isEmpty());
    }

    @Test
    void aViewOfAnotherUserIsNotAddressable() throws AlreadyExistException {
        ListViewDto created = listViewService.createView(request("Expiry watch", column("COMMON_NAME")));

        authenticateAs(otherUser);
        Assertions.assertThrows(NotFoundException.class, () -> listViewService.deleteView(created.getUuid()));
        Assertions
                .assertThrows(NotFoundException.class,
                        () -> listViewService.editView(created.getUuid(), update("Renamed", column("COMMON_NAME"))));
    }

    @Test
    void viewsAreListedCreatedRenamedEditedAndDeleted() throws AlreadyExistException, NotFoundException {
        ListViewDto created = listViewService
                .createView(request("Expiry watch", column("COMMON_NAME"), column("NOT_AFTER")));

        ListViewDto renamed = listViewService.editView(created.getUuid(), update("Expiring soon", column("NOT_AFTER")));
        Assertions.assertEquals("Expiring soon", renamed.getName());
        Assertions.assertEquals(List.of("NOT_AFTER"), identifiersOf(renamed));
        Assertions.assertEquals(created.getUuid(), renamed.getUuid());

        listViewService.deleteView(created.getUuid());
        Assertions.assertTrue(listViewService.listViews(Resource.CERTIFICATE).isEmpty());
    }

    @Test
    void aSecondViewOfTheSameNameIsRejected() throws AlreadyExistException {
        listViewService.createView(request("Expiry watch", column("COMMON_NAME")));

        ListViewRequestDto duplicate = request("Expiry watch", column("NOT_AFTER"));
        Assertions.assertThrows(AlreadyExistException.class, () -> listViewService.createView(duplicate));
    }

    @Test
    void theSameNameIsFreeForAnotherUserAndForAnotherResource() throws AlreadyExistException {
        listViewService.createView(request("Expiry watch", column("COMMON_NAME")));

        ListViewRequestDto otherResource = request("Expiry watch", column("CKI_NAME"));
        otherResource.setResource(Resource.CRYPTOGRAPHIC_KEY);
        Assertions.assertNotNull(listViewService.createView(otherResource));

        authenticateAs(otherUser);
        Assertions.assertNotNull(listViewService.createView(request("Expiry watch", column("COMMON_NAME"))));
    }

    @Test
    void renamingOntoTheNameOfAnotherViewIsRejected() throws AlreadyExistException {
        listViewService.createView(request("Expiry watch", column("COMMON_NAME")));
        ListViewDto second = listViewService.createView(request("Everything", column("COMMON_NAME")));

        ListViewUpdateRequestDto clash = update("Expiry watch", column("COMMON_NAME"));
        Assertions.assertThrows(AlreadyExistException.class, () -> listViewService.editView(second.getUuid(), clash));
    }

    @Test
    void aViewKeepsItsOwnNameWhenSavedUnchanged() throws AlreadyExistException, NotFoundException {
        ListViewDto created = listViewService.createView(request("Expiry watch", column("COMMON_NAME")));

        ListViewDto saved = listViewService
                .editView(created.getUuid(), update("Expiry watch", column("COMMON_NAME"), column("NOT_AFTER")));

        Assertions.assertEquals(List.of("COMMON_NAME", "NOT_AFTER"), identifiersOf(saved));
    }

    @Test
    void columnOrderRoundTripsExactlyAsSent() throws AlreadyExistException {
        List<String> order = List.of("NOT_AFTER", "COMMON_NAME", "CERTIFICATE_STATE");

        ListViewDto created = listViewService
                .createView(
                        request("Ordered", column("NOT_AFTER"), column("COMMON_NAME"), column("CERTIFICATE_STATE")));

        Assertions.assertEquals(order, identifiersOf(created));
        Assertions.assertEquals(order, identifiersOf(listViewService.listViews(Resource.CERTIFICATE).getFirst()));
    }

    @Test
    void aLabelOverrideRoundTripsAndCanBeClearedBackToTheCatalogueLabel()
            throws AlreadyExistException, NotFoundException {
        ListViewDto created = listViewService
                .createView(request("Labelled", column("COMMON_NAME", "Subject"), column("NOT_AFTER")));

        Assertions.assertEquals("Subject", created.getColumns().getFirst().getLabel());
        // absent rather than filled in from the catalogue, so a caller can tell an override from the default
        Assertions.assertNull(created.getColumns().get(1).getLabel());

        ListViewDto cleared = listViewService
                .editView(created.getUuid(), update("Labelled", column("COMMON_NAME"), column("NOT_AFTER")));
        Assertions.assertNull(cleared.getColumns().getFirst().getLabel());
    }

    @Test
    void filtersAndOrderingAreStoredAndReturnedWithTheColumns() throws AlreadyExistException {
        SearchFilterRequestDto filter = new SearchFilterRequestDto(FilterFieldSource.PROPERTY, "COMMON_NAME",
                FilterConditionOperator.CONTAINS, "test");
        SearchSortRequestDto sort = new SearchSortRequestDto(FilterFieldSource.PROPERTY, "NOT_AFTER",
                SortDirection.DESC);

        ListViewRequestDto request = request("Sliced", column("COMMON_NAME"));
        request.setFilters(List.of(filter));
        request.setSort(sort);

        listViewService.createView(request);

        ListViewDto stored = listViewService.listViews(Resource.CERTIFICATE).getFirst();
        Assertions.assertEquals(List.of("COMMON_NAME"), identifiersOf(stored));
        Assertions.assertEquals(1, stored.getFilters().size());
        Assertions.assertEquals("COMMON_NAME", stored.getFilters().getFirst().getFieldIdentifier());
        Assertions.assertEquals(FilterConditionOperator.CONTAINS, stored.getFilters().getFirst().getCondition());
        Assertions.assertEquals(sort, stored.getSort());
    }

    @Test
    void aViewWithoutFiltersOrOrderingReportsNeither() throws AlreadyExistException {
        ListViewDto created = listViewService.createView(request("Plain", column("COMMON_NAME")));

        Assertions.assertNull(created.getFilters());
        Assertions.assertNull(created.getSort());
    }

    @Test
    void markingAViewDefaultClearsThePreviousDefault() throws AlreadyExistException, NotFoundException {
        ListViewRequestDto first = request("First", column("COMMON_NAME"));
        first.setDefaultView(true);
        ListViewDto firstView = listViewService.createView(first);

        ListViewRequestDto second = request("Second", column("NOT_AFTER"));
        second.setDefaultView(true);
        ListViewDto secondView = listViewService.createView(second);

        Assertions.assertEquals(List.of(secondView.getUuid()), defaultViewUuids());

        ListViewUpdateRequestDto promoteFirst = update("First", column("COMMON_NAME"));
        promoteFirst.setDefaultView(true);
        listViewService.editView(firstView.getUuid(), promoteFirst);

        Assertions.assertEquals(List.of(firstView.getUuid()), defaultViewUuids());
    }

    @Test
    void theDefaultOfOneResourceDoesNotClearTheDefaultOfAnother() throws AlreadyExistException {
        ListViewRequestDto certificates = request("Certificates", column("COMMON_NAME"));
        certificates.setDefaultView(true);
        ListViewDto certificateView = listViewService.createView(certificates);

        ListViewRequestDto keys = request("Keys", column("CKI_NAME"));
        keys.setResource(Resource.CRYPTOGRAPHIC_KEY);
        keys.setDefaultView(true);
        ListViewDto keyView = listViewService.createView(keys);

        Assertions
                .assertEquals(List.of(certificateView.getUuid(), keyView.getUuid()).stream().sorted().toList(),
                        defaultViewUuids().stream().sorted().toList());
    }

    private List<String> defaultViewUuids() {
        return listViewRepository
                .findByUserUuidOrderByNameAsc(user)
                .stream()
                .filter(ListView::isDefaultView)
                .map(view -> view.getUuid().toString())
                .toList();
    }

    /**
     * A field can leave the catalogue after a view has stored it - a custom attribute is deleted, a property is
     * retired. Reading the view then has to drop that column rather than fail, so the rest of the view keeps working.
     */
    @Test
    void aColumnWhoseFieldNoLongerExistsIsSkippedOnRead() {
        ListView stored = new ListView();
        stored.setUserUuid(user);
        stored.setResource(Resource.CERTIFICATE);
        stored.setName("Stale");
        stored
                .setColumns(List
                        .of(column("COMMON_NAME"), column("RETIRED_FIELD"),
                                new ListViewColumnDto(FilterFieldSource.CUSTOM, "deleted|STRING", null)));
        listViewRepository.save(stored);

        ListViewDto read = listViewService.listViews(Resource.CERTIFICATE).getFirst();

        Assertions.assertEquals(List.of("COMMON_NAME"), identifiersOf(read));
    }

    @Test
    void aUuidThatIsNotAUuidIsNotFoundRatherThanAnInternalError() {
        Assertions.assertThrows(NotFoundException.class, () -> listViewService.deleteView("not-a-uuid"));
    }

    @Test
    void anEmptyFilterListIsStoredAsNoFilterAtAll() throws AlreadyExistException {
        ListViewRequestDto request = request("Unfiltered", column("COMMON_NAME"));
        request.setFilters(List.of());

        Assertions.assertNull(listViewService.createView(request).getFilters());
    }

    @Test
    void viewsOfEveryResourceAreListedWhenNoResourceIsNamed() throws AlreadyExistException {
        listViewService.createView(request("Certificates", column("COMMON_NAME")));
        ListViewRequestDto keys = request("Keys", column("CKI_NAME"));
        keys.setResource(Resource.CRYPTOGRAPHIC_KEY);
        listViewService.createView(keys);

        Assertions
                .assertEquals(List.of("Certificates", "Keys"),
                        listViewService.listViews(null).stream().map(ListViewDto::getName).sorted().toList());
    }

    @Test
    void aColumnTheResourceDoesNotOfferIsRejectedOnWrite() {
        ListViewRequestDto request = request("Impossible", column("CKI_NAME"));

        ValidationException e = Assertions
                .assertThrows(ValidationException.class, () -> listViewService.createView(request));
        Assertions.assertTrue(e.getMessage().contains("CKI_NAME"));
    }

    @Test
    void theSameColumnTwiceIsRejected() {
        ListViewRequestDto request = request("Doubled", column("COMMON_NAME"), column("COMMON_NAME", "Again"));

        Assertions.assertThrows(ValidationException.class, () -> listViewService.createView(request));
    }

    @Test
    void aResourceWithNoFieldCatalogueCannotCarryViews() {
        ListViewRequestDto request = request("Nowhere", column("COMMON_NAME"));
        request.setResource(Resource.SETTINGS);

        Assertions.assertThrows(ValidationException.class, () -> listViewService.createView(request));
    }

    @Test
    void viewsOfADeletedUserAreRemovedAndOtherUsersAreLeftAlone() throws AlreadyExistException {
        listViewService.createView(request("Mine", column("COMMON_NAME")));

        authenticateAs(otherUser);
        listViewService.createView(request("Theirs", column("COMMON_NAME")));

        Assertions.assertEquals(1, listViewInternalService.deleteViewsOfUser(user));

        Assertions.assertTrue(listViewRepository.findByUserUuidOrderByNameAsc(user).isEmpty());
        Assertions.assertEquals(1, listViewRepository.findByUserUuidOrderByNameAsc(otherUser).size());
    }
}
