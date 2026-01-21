package com.czertainly.core.provisioning;

/**
 * Service for managing proxy lifecycle through the external provisioning API.
 */
public interface ProxyProvisioningService {

    /**
     * Provisions a new proxy and returns installation instructions.
     *
     * @param proxyCode unique identifier for the proxy
     * @return installation instructions as a shell command
     * @throws ProxyProvisioningException if provisioning fails
     */
    String provisionProxy(String proxyCode) throws ProxyProvisioningException;

    /**
     * Retrieves installation instructions for an existing proxy.
     *
     * @param proxyCode unique identifier for the proxy
     * @return installation instructions as a shell command
     * @throws ProxyProvisioningException if retrieval fails
     */
    String getProxyInstallationInstructions(String proxyCode) throws ProxyProvisioningException;

    /**
     * Decommissions an existing proxy.
     *
     * @param proxyCode unique identifier for the proxy
     * @throws ProxyProvisioningException if decommissioning fails
     */
    void decommissionProxy(String proxyCode) throws ProxyProvisioningException;
}
