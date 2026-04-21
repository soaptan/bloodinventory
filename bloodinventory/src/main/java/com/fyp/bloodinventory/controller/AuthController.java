package com.fyp.bloodinventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String loginPage(HttpServletRequest request) {
        // Ensure the session exists before Thymeleaf reaches the form tag.
        request.getSession();
        return "login";
    }
}
