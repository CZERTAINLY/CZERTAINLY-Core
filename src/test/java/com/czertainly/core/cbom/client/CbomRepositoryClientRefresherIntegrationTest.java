package com.czertainly.core.cbom.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import com.czertainly.core.events.data.CbomRepositoryUrlChangedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration test for CbomRepositoryClientRefresher that tests the interaction
 * between Spring event system and the CBOM repository client.
 * This test verifies that the refresher correctly handles URL change events
 * and properly manages the client lifecycle.
 */
@SpringBootTest
@ActiveProfiles("test")
class CbomRepositoryClientRefresherIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @SpyBean
    private CbomRepositoryClient cbomRepositoryClient;

    @SpyBean
    private CbomRepositoryClientRefresher cbomRepositoryClientRefresher;

    private static final String OLD_URL = "http://old-url.com";
    private static final String NEW_URL = "http://new-url.com";

    @BeforeEach
    void setUp() {
        // Reset mocks to clear any previous invocations
        reset(cbomRepositoryClient, cbomRepositoryClientRefresher);
    }

    @Test
    void testRefresherReceivesAndHandlesEvent_WithValidNewUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, NEW_URL);

        // Act
        eventPublisher.publishEvent(event);

        // Assert - verify refresher received and handled the event
        verify(cbomRepositoryClientRefresher, timeout(1000).times(1)).onCbomRepositoryUrlChanged(event);
        verify(cbomRepositoryClient, timeout(1000).times(1)).recreateClient(NEW_URL);
        verify(cbomRepositoryClient, never()).resetClient();
    }

    @Test
    void testRefresherReceivesAndHandlesEvent_WithNullUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, null);

        // Act
        eventPublisher.publishEvent(event);

        // Assert - verify refresher received and handled the event
        verify(cbomRepositoryClientRefresher, timeout(1000).times(1)).onCbomRepositoryUrlChanged(event);
        verify(cbomRepositoryClient, timeout(1000).times(1)).resetClient();
        verify(cbomRepositoryClient, never()).recreateClient(anyString());
    }

    @Test
    void testRefresherReceivesAndHandlesEvent_WithEmptyUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, "");

        // Act
        eventPublisher.publishEvent(event);

        // Assert - verify refresher received and handled the event
        verify(cbomRepositoryClientRefresher, timeout(1000).times(1)).onCbomRepositoryUrlChanged(event);
        verify(cbomRepositoryClient, timeout(1000).times(1)).resetClient();
        verify(cbomRepositoryClient, never()).recreateClient(anyString());
    }

    @Test
    void testRefresherReceivesAndHandlesEvent_WithBlankUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, "   ");

        // Act
        eventPublisher.publishEvent(event);

        // Assert - verify refresher received and handled the event
        verify(cbomRepositoryClientRefresher, timeout(1000).times(1)).onCbomRepositoryUrlChanged(event);
        verify(cbomRepositoryClient, timeout(1000).times(1)).resetClient();
        verify(cbomRepositoryClient, never()).recreateClient(anyString());
    }

    @Test
    void testRefresherReceivesAndHandlesEvent_WithSameUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, OLD_URL);

        // Act
        eventPublisher.publishEvent(event);

        // Assert - verify refresher received and handled the event
        verify(cbomRepositoryClientRefresher, timeout(1000).times(1)).onCbomRepositoryUrlChanged(event);
        verify(cbomRepositoryClient, timeout(1000).times(1)).recreateClient(OLD_URL);
        verify(cbomRepositoryClient, never()).resetClient();
    }

    @Test
    void testRefresherHandlesMultipleSequentialEvents() {
        // Arrange
        CbomRepositoryUrlChangedEvent event1 = new CbomRepositoryUrlChangedEvent(OLD_URL, NEW_URL);
        CbomRepositoryUrlChangedEvent event2 = new CbomRepositoryUrlChangedEvent(NEW_URL, "http://another-url.com");
        CbomRepositoryUrlChangedEvent event3 = new CbomRepositoryUrlChangedEvent("http://another-url.com", null);

        // Act
        eventPublisher.publishEvent(event1);
        eventPublisher.publishEvent(event2);
        eventPublisher.publishEvent(event3);

        // Assert - verify refresher handled all events
        verify(cbomRepositoryClientRefresher, timeout(1000).times(3)).onCbomRepositoryUrlChanged(any());
        verify(cbomRepositoryClient, timeout(1000).times(1)).recreateClient(NEW_URL);
        verify(cbomRepositoryClient, timeout(1000).times(1)).recreateClient("http://another-url.com");
        verify(cbomRepositoryClient, timeout(1000).times(1)).resetClient();
    }

    @Test
    void testRefresherHandlesUrlWithSpecialCharacters() {
        // Arrange
        String specialUrl = "http://example.com:8080/cbom?token=abc123&user=test";
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, specialUrl);

        // Act
        eventPublisher.publishEvent(event);

        // Assert - verify refresher handled the event with special URL
        verify(cbomRepositoryClientRefresher, timeout(1000).times(1)).onCbomRepositoryUrlChanged(event);
        verify(cbomRepositoryClient, timeout(1000).times(1)).recreateClient(specialUrl);
        verify(cbomRepositoryClient, never()).resetClient();
    }

    @Test
    void testRefresherIsRegisteredAsEventListener() {
        // Assert - verify refresher is properly wired as Spring component
        assertThat(cbomRepositoryClientRefresher).isNotNull();
        assertThat(cbomRepositoryClient).isNotNull();
    }

    @Test
    void testAsyncEventHandling_RefresherDoesNotBlockPublisher() throws InterruptedException {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, NEW_URL);

        // Act
        eventPublisher.publishEvent(event);

        // Assert - handling by refresher should complete within reasonable time
        verify(cbomRepositoryClientRefresher, timeout(1000).times(1)).onCbomRepositoryUrlChanged(event);
        verify(cbomRepositoryClient, timeout(1000).times(1)).recreateClient(NEW_URL);
    }

    @Test
    void testDirectMethodInvocation_WithValidUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, NEW_URL);

        // Act - directly call the refresher method
        cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event);

        // Assert
        verify(cbomRepositoryClient, times(1)).recreateClient(NEW_URL);
        verify(cbomRepositoryClient, never()).resetClient();
    }

    @Test
    void testDirectMethodInvocation_WithNullUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, null);

        // Act - directly call the refresher method
        cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event);

        // Assert
        verify(cbomRepositoryClient, times(1)).resetClient();
        verify(cbomRepositoryClient, never()).recreateClient(anyString());
    }
}

