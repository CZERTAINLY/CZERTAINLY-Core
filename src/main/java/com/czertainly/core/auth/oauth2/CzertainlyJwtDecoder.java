package com.czertainly.core.auth.oauth2;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.settings.SettingsCache;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Duration;
import java.util.List;

@Component
public class CzertainlyJwtDecoder implements JwtDecoder {
    @Override
    public Jwt decode(String token) throws JwtException {
        if (!isAuthenticationNeeded()) {
            return null;
        }
        String issuerUri;
        try {
            issuerUri = SignedJWT.parse(token).getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            throw new ValidationException("Could not extract issuer from JWT.");
        }
        if (issuerUri == null) throw new ValidationException("Issuer URI is not present in JWT.");

        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
        OAuth2ProviderSettingsDto providerSettings = authenticationSettings.getOAuth2Providers().values().stream()
                .filter(p -> p.getIssuerUrl().equals(issuerUri))
                .findFirst().orElse(null);

        if (providerSettings == null) {
            throw new CzertainlyAuthenticationException("No OAuth2 Provider with issuer URI '%s' configured".formatted(issuerUri));
        }

        int skew = providerSettings.getSkew();
        List<String> audiences = providerSettings.getAudiences();

        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> clockSkewValidator = new JwtTimestampValidator(Duration.ofSeconds(skew));

        OAuth2TokenValidator<Jwt> audienceValidator = new DelegatingOAuth2TokenValidator<>();
        // Add audience validation
        if (!audiences.isEmpty()) {
            audienceValidator = new JwtClaimValidator<List<String>>("aud", aud -> aud.stream().anyMatch(audiences::contains));
        }

        OAuth2TokenValidator<Jwt> combinedValidator = JwtValidators.createDefaultWithValidators(List.of(new JwtIssuerValidator(issuerUri), clockSkewValidator, audienceValidator));
        jwtDecoder.setJwtValidator(combinedValidator);
        return jwtDecoder.decode(token);
    }

    private boolean isAuthenticationNeeded() {
        SecurityContext context = SecurityContextHolder.getContext();
        return (context == null || context.getAuthentication() == null || context.getAuthentication() instanceof AnonymousAuthenticationToken);
    }
}
