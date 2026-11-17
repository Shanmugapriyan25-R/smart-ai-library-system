package com.smartlibrary.controller;

import com.smartlibrary.dto.UserDto;
import com.smartlibrary.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String role = authentication.getAuthorities().iterator().next().getAuthority();
            if ("ROLE_ADMIN".equals(role)) return "redirect:/admin/dashboard";
            if ("ROLE_STAFF".equals(role)) return "redirect:/staff/dashboard";
            if ("ROLE_STUDENT".equals(role)) return "redirect:/student/dashboard";
        }
        return "auth/landing";
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "portal", required = false, defaultValue = "student") String portal,
                        @RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {
        model.addAttribute("portal", portal);
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "You have been logged out securely.");
        }
        return "auth/login";
    }

    @GetMapping("/student/login")
    public String studentLogin() {
        return "redirect:/login?portal=student";
    }

    @GetMapping("/staff/login")
    public String staffLogin() {
        return "redirect:/login?portal=staff";
    }

    @GetMapping("/admin/login")
    public String adminLogin() {
        return "redirect:/login?portal=admin";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("userDto")) {
            model.addAttribute("userDto", new UserDto());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String processRegistration(@Valid @ModelAttribute("userDto") UserDto userDto,
                                      BindingResult bindingResult,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.registerStudent(userDto);
            redirectAttributes.addFlashAttribute("successMessage", "Registration successful! You can now log in to the Student Portal.");
            return "redirect:/login?portal=student";
        } catch (Exception e) {
            bindingResult.rejectValue("username", "error.userDto", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("errorMessage", "Access Denied: You do not possess the required role permissions to view this resource.");
        return "common/access-denied";
    }
}
