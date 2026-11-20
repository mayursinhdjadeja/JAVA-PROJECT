package com.smartProjectPlanner.service;

import com.smartProjectPlanner.dao.ProjectDAO;
import com.smartProjectPlanner.exception.ProjectException;
import com.smartProjectPlanner.model.*;
import com.smartProjectPlanner.util.SecurityUtil;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Service Implementation enforcing multi-tenancy business rules, RBAC validations,
 * and data integrity constraints.
 */
public class ProjectServiceImplementation implements ProjectService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private final ProjectDAO projectDAO;

    public ProjectServiceImplementation() {
        this.projectDAO = new ProjectDAO();
    }

    public ProjectServiceImplementation(ProjectDAO projectDAO) {
        this.projectDAO = projectDAO;
    }

    // =========================================================================
    // Authentication & Registration
    // =========================================================================

    @Override
    public User login(String username, String password) throws ProjectException {
        if (username == null || username.trim().isEmpty()) {
            throw new ProjectException("Username is required.", 400);
        }
        if (password == null || password.trim().isEmpty()) {
            throw new ProjectException("Password is required.", 400);
        }

        User user = projectDAO.getUserByUsername(username.trim());
        if (user == null) {
            throw new ProjectException("Invalid username or password.", 401);
        }

        if (!SecurityUtil.verifyPassword(password, user.getPassword())) {
            throw new ProjectException("Invalid username or password.", 401);
        }

        // Hide password hash before returning
        user.setPassword(null);
        return user;
    }

    @Override
    public User registerCompany(String companyName, String companyCode,
                                String username, String password, String email) throws ProjectException {
        validateNonEmpty("Company Name", companyName);
        validateNonEmpty("Company Code", companyCode);
        validateNonEmpty("Username", username);
        validateNonEmpty("Password", password);
        validateNonEmpty("Email", email);

        if (companyName.trim().length() < 2) {
            throw new ProjectException("Company name must be at least 2 characters.", 400);
        }
        if (companyCode.trim().length() < 3 || companyCode.trim().length() > 30) {
            throw new ProjectException("Company code must be between 3 and 30 characters.", 400);
        }
        if (username.trim().length() < 3) {
            throw new ProjectException("Username must be at least 3 characters.", 400);
        }
        if (password.length() < 6) {
            throw new ProjectException("Password must be at least 6 characters.", 400);
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ProjectException("Please provide a valid email address.", 400);
        }

        String hashedPassword = SecurityUtil.hashPassword(password);
        String normalizedCode = companyCode.trim().toUpperCase().replaceAll("\\s+", "-");

        return projectDAO.registerCompanyWithAdmin(
                companyName.trim(),
                normalizedCode,
                username.trim(),
                hashedPassword,
                email.trim()
        );
    }

    @Override
    public User registerEmployee(String companyCode, String username,
                                 String password, String email, Role role) throws ProjectException {
        validateNonEmpty("Company Code", companyCode);
        validateNonEmpty("Username", username);
        validateNonEmpty("Password", password);
        validateNonEmpty("Email", email);

        if (username.trim().length() < 3) {
            throw new ProjectException("Username must be at least 3 characters.", 400);
        }
        if (password.length() < 6) {
            throw new ProjectException("Password must be at least 6 characters.", 400);
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ProjectException("Please provide a valid email address.", 400);
        }

        String hashedPassword = SecurityUtil.hashPassword(password);
        String normalizedCode = companyCode.trim().toUpperCase();

        return projectDAO.registerEmployee(
                normalizedCode,
                username.trim(),
                hashedPassword,
                email.trim(),
                role != null ? role : Role.EMPLOYEE
        );
    }

    // =========================================================================
    // Team & RBAC Management
    // =========================================================================

    @Override
    public List<User> getCompanyUsers(int companyId) throws ProjectException {
        if (companyId <= 0) {
            throw new ProjectException("Invalid company ID.", 400);
        }
        return projectDAO.getUsersByCompany(companyId);
    }

    @Override
    public boolean updateUserRole(int targetUserId, Role newRole, int companyId, User currentUser) throws ProjectException {
        if (currentUser == null) {
            throw new ProjectException("Unauthorized. Please log in.", 401);
        }
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ProjectException("Access Denied: Only administrators can update team roles.", 403);
        }
        if (newRole == null) {
            throw new ProjectException("Valid role must be specified.", 400);
        }
        if (targetUserId == currentUser.getUserId() && newRole != Role.ADMIN) {
            throw new ProjectException("Administrators cannot demote their own account.", 400);
        }

        return projectDAO.updateUserRole(targetUserId, newRole, companyId);
    }

    // =========================================================================
    // Project Operations
    // =========================================================================

    @Override
    public List<Project> getProjects(int companyId) throws ProjectException {
        if (companyId <= 0) {
            throw new ProjectException("Invalid company ID.", 400);
        }
        return projectDAO.getProjectsByCompany(companyId);
    }

    @Override
    public Project getProjectById(int projectId, int companyId) throws ProjectException {
        Project project = projectDAO.getProjectById(projectId, companyId);
        if (project == null) {
            throw new ProjectException("Project not found or belongs to another tenant.", 404);
        }
        return project;
    }

    @Override
    public Project createProject(Project project, User currentUser) throws ProjectException {
        if (currentUser == null) {
            throw new ProjectException("Unauthorized. Please log in.", 401);
        }

        // RBAC: Only ADMIN and MANAGER can create projects
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            throw new ProjectException("Access Denied: Only Admins and Project Managers can create projects.", 403);
        }

        validateNonEmpty("Project Name", project.getName());
        if (project.getName().trim().length() < 3) {
            throw new ProjectException("Project name must be at least 3 characters.", 400);
        }
        if (project.getStartDate() == null) {
            throw new ProjectException("Project start date is required.", 400);
        }
        if (project.getEndDate() == null) {
            throw new ProjectException("Project end date is required.", 400);
        }
        if (project.getStartDate().after(project.getEndDate())) {
            throw new ProjectException("Start date cannot be after end date.", 400);
        }

        project.setCompanyId(currentUser.getCompanyId());
        project.setCreatedBy(currentUser.getUserId());

        int generatedId = projectDAO.createProject(project);
        project.setProjectId(generatedId);
        project.setCreatorName(currentUser.getUsername());
        return project;
    }

    @Override
    public boolean deleteProject(int projectId, int companyId, User currentUser) throws ProjectException {
        if (currentUser == null) {
            throw new ProjectException("Unauthorized. Please log in.", 401);
        }
        // RBAC: Only ADMIN and MANAGER can delete projects
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            throw new ProjectException("Access Denied: Only Admins and Project Managers can delete projects.", 403);
        }

        // Ensure project exists and belongs to company
        Project project = projectDAO.getProjectById(projectId, companyId);
        if (project == null) {
            throw new ProjectException("Project not found.", 404);
        }

        return projectDAO.deleteProject(projectId, companyId);
    }

    // =========================================================================
    // Task Operations
    // =========================================================================

    @Override
    public List<Task> getTasks(int companyId, Integer projectId) throws ProjectException {
        if (companyId <= 0) {
            throw new ProjectException("Invalid company ID.", 400);
        }
        return projectDAO.getTasksByCompany(companyId, projectId);
    }

    @Override
    public Task getTaskById(int taskId, int companyId) throws ProjectException {
        Task task = projectDAO.getTaskById(taskId, companyId);
        if (task == null) {
            throw new ProjectException("Task not found or belongs to another tenant.", 404);
        }
        return task;
    }

    @Override
    public Task createTask(Task task, User currentUser) throws ProjectException {
        if (currentUser == null) {
            throw new ProjectException("Unauthorized. Please log in.", 401);
        }
        validateNonEmpty("Task Title", task.getTitle());
        if (task.getTitle().trim().length() < 3) {
            throw new ProjectException("Task title must be at least 3 characters.", 400);
        }
        if (task.getProjectId() <= 0) {
            throw new ProjectException("Valid project must be selected.", 400);
        }
        if (task.getDueDate() == null) {
            throw new ProjectException("Task due date is required.", 400);
        }

        // Verify that the project belongs to the current user's company
        Project project = projectDAO.getProjectById(task.getProjectId(), currentUser.getCompanyId());
        if (project == null) {
            throw new ProjectException("Selected project does not belong to your company.", 403);
        }

        if (task.getPriority() == null) {
            task.setPriority(Priority.MEDIUM);
        }
        if (task.getStatus() == null) {
            task.setStatus(Status.TODO);
        }

        int generatedId = projectDAO.createTask(task);
        task.setTaskId(generatedId);
        task.setProjectName(project.getName());
        task.setCompanyId(currentUser.getCompanyId());
        return task;
    }

    @Override
    public boolean updateTaskStatus(int taskId, Status status, int companyId, User currentUser) throws ProjectException {
        if (currentUser == null) {
            throw new ProjectException("Unauthorized. Please log in.", 401);
        }
        if (status == null) {
            throw new ProjectException("Valid status is required.", 400);
        }

        Task task = projectDAO.getTaskById(taskId, companyId);
        if (task == null) {
            throw new ProjectException("Task not found.", 404);
        }

        return projectDAO.updateTaskStatus(taskId, status, companyId);
    }

    @Override
    public boolean deleteTask(int taskId, int companyId, User currentUser) throws ProjectException {
        if (currentUser == null) {
            throw new ProjectException("Unauthorized. Please log in.", 401);
        }
        // RBAC: Only ADMIN and MANAGER can delete tasks
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            throw new ProjectException("Access Denied: Only Admins and Project Managers can delete tasks.", 403);
        }

        Task task = projectDAO.getTaskById(taskId, companyId);
        if (task == null) {
            throw new ProjectException("Task not found.", 404);
        }

        return projectDAO.deleteTask(taskId, companyId);
    }

    // Helper validation
    private void validateNonEmpty(String fieldName, String value) throws ProjectException {
        if (value == null || value.trim().isEmpty()) {
            throw new ProjectException(fieldName + " cannot be empty.", 400);
        }
    }
}
