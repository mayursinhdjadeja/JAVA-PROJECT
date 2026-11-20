package com.smartProjectPlanner.model;

/**
 * Task Status levels for Kanban workflow.
 */
public enum Status {
    TODO,
    IN_PROGRESS,
    DONE;

    public static Status fromString(String statusStr) {
        if (statusStr == null) return TODO;
        String s = statusStr.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        try {
            return Status.valueOf(s);
        } catch (IllegalArgumentException e) {
            return TODO;
        }
    }
}
