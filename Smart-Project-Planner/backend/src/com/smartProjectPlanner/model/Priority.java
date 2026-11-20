package com.smartProjectPlanner.model;

/**
 * Task Priority levels.
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static Priority fromString(String priorityStr) {
        if (priorityStr == null) return MEDIUM;
        try {
            return Priority.valueOf(priorityStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
