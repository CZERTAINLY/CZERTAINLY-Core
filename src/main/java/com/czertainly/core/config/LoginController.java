package com.czertainly.core.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Controller
public class LoginController {

    @GetMapping("/login")
    public void loginPage(HttpServletResponse response) throws IOException {
        response.sendRedirect("oauth2/authorization/keycloak");
    }

}