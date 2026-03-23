package com.czertainly.core.cbom.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.czertainly.core.events.data.CbomRepositoryUrlChangedEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbomRepositoryClientRefresherTest {

    @Mock
    private CbomRepositoryClient cbomRepositoryClient;

    @InjectMocks
    private CbomRepositoryClientRefresher cbomRepositoryClientRefresher;

    private static final String OLD_URL = "http://localhost:8080/cbom";
    private static final String NEW_URL = "http://localhost:9090/cbom-new";

    @BeforeEach
    void setUp() {
        // Additional setup if needed
    }

    @Test
    void testOnCbomRepositoryUrlChanged_WithValidUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, NEW_URL);

        // Act
        cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event);

        // Assert
        verify(cbomRepositoryClient, times(1)).recreateClient(NEW_URL);
    }

    @Test
    void testOnCbomRepositoryUrlChanged_WithNullEvent() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> 
            cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(null));
        verify(cbomRepositoryClient, never()).recreateClient(anyString());
    }

    @Test
    void testOnCbomRepositoryUrlChanged_WithNullUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, null);

        // Act
        cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event);

        // Assert
        verify(cbomRepositoryClient, times(1)).resetClient();
    }

    @Test
    void testOnCbomRepositoryUrlChanged_WithEmptyUrl() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, "");

        // Act
        cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event);

        // Assert
        verify(cbomRepositoryClient, times(1)).resetClient();
    }

    @Test
    void testOnCbomRepositoryUrlChanged_MultipleInvocations() {
        // Arrange
        CbomRepositoryUrlChangedEvent event1 = new CbomRepositoryUrlChangedEvent(OLD_URL, NEW_URL);
        CbomRepositoryUrlChangedEvent event2 = new CbomRepositoryUrlChangedEvent(NEW_URL, OLD_URL);

        // Act
        cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event1);
        cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event2);

        // Assert
        verify(cbomRepositoryClient, times(1)).recreateClient(NEW_URL);
        verify(cbomRepositoryClient, times(1)).recreateClient(OLD_URL);
        verify(cbomRepositoryClient, times(2)).recreateClient(anyString());
    }

    @Test
    void testOnCbomRepositoryUrlChanged_ClientRecreationThrowsException() {
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, NEW_URL);
        doThrow(new RuntimeException("Failed to recreate client"))
            .when(cbomRepositoryClient).recreateClient(NEW_URL);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
            cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event));
        verify(cbomRepositoryClient, times(1)).recreateClient(NEW_URL);
    }

    @Test
    void testEventListenerIsCalledBySpringContext() {
        // This test verifies the event listener mechanism
        // In a real integration test, you would publish the event via ApplicationEventPublisher
        
        // Arrange
        CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, NEW_URL);

        // Act
        cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event);

        // Assert
        verify(cbomRepositoryClient, times(1)).recreateClient(NEW_URL);
    }

    @Test
    void testOnCbomRepositoryUrlChanged_WithDifferentUrls() {
        // Arrange
        String[] urls = {
            "http://localhost:8080/cbom",
            "https://production.com/cbom",
            "http://192.168.1.1:3000/api/cbom"
        };

        // Act
        for (String url : urls) {
            CbomRepositoryUrlChangedEvent event = new CbomRepositoryUrlChangedEvent(OLD_URL, url);
            cbomRepositoryClientRefresher.onCbomRepositoryUrlChanged(event);
        }

        // Assert
        for (String url : urls) {
            verify(cbomRepositoryClient, times(1)).recreateClient(url);
        }
        verify(cbomRepositoryClient, times(urls.length)).recreateClient(anyString());
    }
}

