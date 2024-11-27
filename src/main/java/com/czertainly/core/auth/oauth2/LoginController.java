package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.settings.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.OAuth2ProviderSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.settings.SettingsCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.List;

@Controller
public class LoginController {

    private static final String ERROR_ATTRIBUTE_NAME = "error";

    @GetMapping("/login")
    public String loginPage(Model model, @RequestParam(value = "redirect", required = false) String redirectUrl, HttpServletRequest request, HttpServletResponse response, @RequestParam(value = "error", required = false) String error) {

        if (error != null) {
            model.addAttribute(ERROR_ATTRIBUTE_NAME, "An error occurred: " + error);
            return ERROR_ATTRIBUTE_NAME;
        }

        String baseUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath(null)
                .build()
                .toUriString();

        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            request.getSession().setAttribute("redirectUrl", baseUrl + redirectUrl);
        } else {
            model.addAttribute(ERROR_ATTRIBUTE_NAME, "No redirect URL provided");
            return ERROR_ATTRIBUTE_NAME;
        }

        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
        if (authenticationSettings.getOAuth2Providers().isEmpty()) return "no-login-options";

        List<OAuth2ProviderSettingsDto> oauth2Providers = authenticationSettings.getOAuth2Providers().values().stream().toList();
        if (oauth2Providers.size() == 1) {
            request.getSession().setMaxInactiveInterval(oauth2Providers.getFirst().getSessionMaxInactiveInterval());
            try {
                response.sendRedirect("oauth2/authorization/" + oauth2Providers.getFirst().getName());
            } catch (IOException e) {
                throw new CzertainlyAuthenticationException("Error when redirecting to OAuth2 Provider with name " + oauth2Providers.getFirst() + " : " + e.getMessage());
            }
        }

        model.addAttribute("providers", oauth2Providers.stream().map(OAuth2ProviderSettingsDto::getName).toList());
        return "login-options";
    }

    @GetMapping("/oauth2/authorization/{provider}/prepare")
    public void loginWithProvider(@PathVariable String provider, HttpServletResponse response, HttpServletRequest request) throws IOException {
        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
        OAuth2ProviderSettingsDto providerSettings = authenticationSettings.getOAuth2Providers().get(provider);

        if (providerSettings == null) {
            throw new CzertainlyAuthenticationException("Unknown OAuth2 Provider with name '%s'".formatted(provider));
        }

        request.getSession().setMaxInactiveInterval(providerSettings.getSessionMaxInactiveInterval());
        response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/oauth2/authorization/" + provider);
    }

}
