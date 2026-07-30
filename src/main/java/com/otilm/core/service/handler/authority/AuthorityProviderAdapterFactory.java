package com.otilm.core.service.handler.authority;

import com.otilm.core.dao.entity.AuthorityInstanceReference;
import com.otilm.core.dao.entity.ConnectorInterfaceEntity;
import com.otilm.core.exception.UnsupportedAuthorityVersionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Dispatches authority operations to the adapter matching the authority's connector interface
 * version. v2 → {@link AuthorityProviderV2Adapter}, v3 → {@link AuthorityProviderV3Adapter}.
 *
 * <p>Defensive — legacy v1-authority connectors ({@code FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER})
 * flow through the separate legacy service path and never reach this factory.</p>
 *
 * <p>{@link ConnectorInterfaceEntity#getVersion()} returns the version string as reported by the
 * connector's info endpoint (e.g. {@code "v2"}, {@code "v3"}). A {@code null} interface is treated
 * as v2: framework-v1 connectors that speak the v2 authority wire protocol (e.g. ejbca-ng) declare
 * no interface row, so they route to {@link AuthorityProviderV2Adapter} (see {@link #forAuthority}).
 * Any other non-null value (an unrecognized or bare-decimal version such as {@code "2"}) results in
 * an {@link UnsupportedAuthorityVersionException}.</p>
 */
@Component
public class AuthorityProviderAdapterFactory {

    private final AuthorityProviderV2Adapter v2Adapter;
    private final AuthorityProviderV3Adapter v3Adapter;

    @Autowired
    public AuthorityProviderAdapterFactory(AuthorityProviderV2Adapter v2Adapter,
                                           AuthorityProviderV3Adapter v3Adapter) {
        this.v2Adapter = v2Adapter;
        this.v3Adapter = v3Adapter;
    }

    public AuthorityProviderAdapter forAuthority(AuthorityInstanceReference authority) {
        // A precondition, not a domain condition: callers that can legitimately hold a null reference check it
        // themselves and report it in their own terms. Deliberately not a type any caller catches -- selecting one
        // to land in another class's handler routes this into whatever that handler means, which is how a missing
        // authority once became "the authority refused to cancel" and stranded certificates mid-operation.
        Objects.requireNonNull(authority, "An authority instance is required to select an adapter.");
        ConnectorInterfaceEntity iface = authority.getConnectorInterface();
        if (iface == null) {
            // No interface row → a framework-v1 connector speaking the v2 authority protocol (see class Javadoc).
            return v2Adapter;
        }
        String version = iface.getVersion();
        if (version == null) {
            throw new UnsupportedAuthorityVersionException(
                    "Authority connector interface has no version (authority " + authority.getUuid() + ")");
        }
        return switch (version) {
            case "v2" -> v2Adapter;
            case "v3" -> v3Adapter;
            default -> throw new UnsupportedAuthorityVersionException(
                    "Unsupported authority connector interface version: " + version
                            + " (authority " + authority.getUuid() + ")");
        };
    }
}
