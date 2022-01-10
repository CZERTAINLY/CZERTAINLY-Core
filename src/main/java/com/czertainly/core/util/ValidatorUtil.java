package com.czertainly.core.util;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public class ValidatorUtil {

    public static void validateAuthToRaProfile(String raProfileName) throws ValidationException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().startsWith(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/acme")){
            return;
        }
        boolean hasUserRole = authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_" + raProfileName));
        if (!hasUserRole) {
            throw new ValidationException(ValidationError.create(
                    "Client identified by name {} does not have access to the RA Profile '{}'", authentication.getName(), raProfileName));
        }
    }
}