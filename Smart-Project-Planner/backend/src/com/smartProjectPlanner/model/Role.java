package com.smartProjectPlanner.model;

/**
 * Role-Based Access Control (RBAC) Roles.
 */
public enum Role {
    ADMIN,
    MANAGER,
    HR,
    EMPLOYEE;

    public static Role fromString(String roleStr) {
        if (roleStr == null) return EMPLOYEE;
        try {
            return Role.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EMPLOYEE;
        }
    }
}
