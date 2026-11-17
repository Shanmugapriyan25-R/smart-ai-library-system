package com.smartlibrary.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndRoleAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Public endpoints like landing page and login are accessible without authentication")
    void testPublicEndpointsAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated request to protected dashboard redirects to login")
    void testUnauthenticatedRedirect() throws Exception {
        mockMvc.perform(get("/student/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "student_alex", roles = {"STUDENT"})
    @DisplayName("Student user is strictly forbidden from accessing Admin management endpoints")
    void testStudentCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student_alex", roles = {"STUDENT"})
    @DisplayName("Student user is strictly forbidden from accessing Staff circulation desk")
    void testStudentCannotAccessStaff() throws Exception {
        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/staff/requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff_eleanor", roles = {"STAFF"})
    @DisplayName("Staff user can access staff circulation endpoints but not admin settings")
    void testStaffPermissions() throws Exception {
        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Admin user can access administrative settings and dashboards")
    void testAdminAccess() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student_alex", roles = {"STUDENT"})
    @DisplayName("State-changing POST without CSRF token is rejected with 403 Forbidden")
    void testCsrfProtectionEnforced() throws Exception {
        mockMvc.perform(post("/student/request-issue")
                        .param("bookId", "1"))
                .andExpect(status().isForbidden());
    }
}
