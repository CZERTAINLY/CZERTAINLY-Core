package com.czertainly.core.auth.oauth2;

import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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

    private SettingService settingService;
    @Autowired
    public void setSettingService(SettingService settingService) {
        this.settingService = settingService;
    }


    @GetMapping("/login")
    public String loginPage(Model model, @RequestParam(value = "redirect", required = false) String redirectUrl, HttpServletRequest request, HttpServletResponse response, @RequestParam(value = "error", required = false) String error)
    {

        if (error != null) {
            model.addAttribute("error", "An error occurred: " + error);
            return "error";
        }

        String baseUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath(null)
                .build()
                .toUriString();

        if (redirectUrl != null) {
            request.getSession().setAttribute("redirectUrl", baseUrl + redirectUrl);
        } else {
            model.addAttribute("error", "No redirect URL provided");
            return "error";
        }

        List<String> oauth2Providers = settingService.listNamesOfOAuth2Providers();

        if (oauth2Providers.isEmpty()) return "no-login-options";

        if (oauth2Providers.size() == 1) {
            request.getSession().setMaxInactiveInterval(settingService.getOAuth2ProviderSettings(oauth2Providers.getFirst(), false).getSessionMaxInactiveInterval());
            try {
                response.sendRedirect("oauth2/authorization/" + oauth2Providers.getFirst());
            } catch (IOException e) {
                throw new CzertainlyAuthenticationException("Error when redirecting to OAuth2 Provider with name " + oauth2Providers.getFirst() + " : " + e.getMessage());
            }
        }

        model.addAttribute("providers", oauth2Providers);
        return "login-options";
    }

    @GetMapping("/oauth2/authorization/{provider}/prepare")
    public void loginWithProvider(@PathVariable String provider, HttpServletResponse response, HttpServletRequest request) throws IOException {
        request.getSession().setMaxInactiveInterval(settingService.getOAuth2ProviderSettings(provider, false).getSessionMaxInactiveInterval());
        response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/oauth2/authorization/" + provider);
    }

}