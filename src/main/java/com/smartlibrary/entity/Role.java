package com.smartlibrary.entity;

public enum Role {
    ROLE_STUDENT,
    ROLE_STAFF,
    ROLE_ADMIN;

    public String getDisplayRole() {
        return name().replace("ROLE_", "");
    }
}
