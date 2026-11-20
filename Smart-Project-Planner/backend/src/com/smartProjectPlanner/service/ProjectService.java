package com.smartProjectPlanner.service;

import com.smartProjectPlanner.exception.ProjectException;
import com.smartProjectPlanner.model.*;

import java.util.List;

/**
 * Service Layer Interface for Smart Project Planner.
 * Enforces business logic rules, tenant boundary checks, and Role-Based Access Control (RBAC).
 */
public interface ProjectService {

    // Auth & Tenant Management
    User login(String username, String password) throws ProjectException;

    User registerCompany(String companyName, String companyCode,
                        String username, String password, String email) throws ProjectException;

    User registerEmployee(String companyCode, String username,
                          String password, String email, Role role) throws ProjectException;

    // Team & RBAC Management
    List<User> getCompanyUsers(int companyId) throws ProjectException;

    boolean updateUserRole(int targetUserId, Role newRole, int companyId, User currentUser) throws ProjectException;

    // Project Operations
    List<Project> getProjects(int companyId) throws ProjectException;

    Project getProjectById(int projectId, int companyId) throws ProjectException;

    Project createProject(Project project, User currentUser) throws ProjectException;

    boolean deleteProject(int projectId, int companyId, User currentUser) throws ProjectException;

    // Task Operations
    List<Task> getTasks(int companyId, Integer projectId) throws ProjectException;

    Task getTaskById(int taskId, int companyId) throws ProjectException;

    Task createTask(Task task, User currentUser) throws ProjectException;

    boolean updateTaskStatus(int taskId, Status status, int companyId, User currentUser) throws ProjectException;

    boolean deleteTask(int taskId, int companyId, User currentUser) throws ProjectException;
}
