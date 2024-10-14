package com.czertainly.core.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class LoginController {

    @GetMapping("/login")
    public void loginPage(@RequestParam(value = "redirect", required = false) String redirectUrl, HttpServletResponse response,
                          HttpServletRequest request) throws IOException {


        String originalUrl = request.getHeader("referer");
        if (originalUrl != null) {
            if (redirectUrl != null) {
                originalUrl += redirectUrl.replaceFirst("/", "");
            }
            request.getSession().setAttribute("redirectUrl", originalUrl);
        }



//        response.sendRedirect("oauth2/authorization/keycloak?redirectUrl=" + URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8));
        response.sendRedirect("oauth2/authorization/keycloak");

    }

}