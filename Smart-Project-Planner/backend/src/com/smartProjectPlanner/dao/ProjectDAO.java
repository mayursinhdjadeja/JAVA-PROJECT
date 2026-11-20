package com.smartProjectPlanner.dao;

import com.smartProjectPlanner.config.DatabaseConnector;
import com.smartProjectPlanner.exception.ProjectException;
import com.smartProjectPlanner.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for all JDBC operations.
 * Implements strict Multi-Tenant isolation, transactions, and complex JOIN queries.
 */
public class ProjectDAO {

    // =========================================================================
    // 1. Authentication & Registration Operations (with Transactions)
    // =========================================================================

    /**
     * Registers a new tenant Company and creates the initial ADMIN user atomically within a Transaction.
     */
    public User registerCompanyWithAdmin(String companyName, String companyCode,
                                         String username, String hashedPassword, String email) throws ProjectException {
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement companyStmt = null;
        PreparedStatement userStmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            conn.setAutoCommit(false); // Begin Transaction

            // 1. Verify uniqueness of company code and username
            String checkSql = "SELECT 'code' as conflict FROM companies WHERE company_code = ? " +
                              "UNION ALL " +
                              "SELECT 'username' as conflict FROM users WHERE username = ?";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, companyCode);
            checkStmt.setString(2, username);
            rs = checkStmt.executeQuery();
            if (rs.next()) {
                String conflict = rs.getString("conflict");
                if ("code".equals(conflict)) {
                    throw new ProjectException("Company code '" + companyCode + "' is already taken.", 409);
                } else {
                    throw new ProjectException("Username '" + username + "' is already registered.", 409);
                }
            }
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(checkStmt);

            // 2. Insert into companies table
            String insertCompanySql = "INSERT INTO companies (company_name, company_code) VALUES (?, ?)";
            companyStmt = conn.prepareStatement(insertCompanySql, Statement.RETURN_GENERATED_KEYS);
            companyStmt.setString(1, companyName);
            companyStmt.setString(2, companyCode);
            companyStmt.executeUpdate();

            rs = companyStmt.getGeneratedKeys();
            if (!rs.next()) {
                throw new ProjectException("Failed to generate company ID during registration.", 500);
            }
            int companyId = rs.getInt(1);
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(companyStmt);

            // 3. Insert the Admin user
            String insertUserSql = "INSERT INTO users (company_id, username, password, email, role) VALUES (?, ?, ?, ?, 'ADMIN')";
            userStmt = conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS);
            userStmt.setInt(1, companyId);
            userStmt.setString(2, username);
            userStmt.setString(3, hashedPassword);
            userStmt.setString(4, email);
            userStmt.executeUpdate();

            rs = userStmt.getGeneratedKeys();
            if (!rs.next()) {
                throw new ProjectException("Failed to generate user ID for administrator.", 500);
            }
            int userId = rs.getInt(1);

            // 4. Commit Transaction
            conn.commit();

            User user = new User(userId, companyId, username, email, Role.ADMIN);
            user.setCompanyName(companyName);
            user.setCompanyCode(companyCode);
            return user;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback on error
                } catch (SQLException rollbackEx) {
                    System.err.println("[ProjectDAO] Rollback failed: " + rollbackEx.getMessage());
                }
            }
            throw new ProjectException("Database error during company registration: " + e.getMessage(), 500, e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(companyStmt);
            DatabaseConnector.closeQuietly(userStmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Registers a new employee under an existing company using the company code.
     */
    public User registerEmployee(String companyCode, String username, String hashedPassword,
                                 String email, Role role) throws ProjectException {
        Connection conn = null;
        PreparedStatement checkCompanyStmt = null;
        PreparedStatement checkUserStmt = null;
        PreparedStatement insertStmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();

            // 1. Verify company code
            String checkCompSql = "SELECT company_id, company_name FROM companies WHERE company_code = ?";
            checkCompanyStmt = conn.prepareStatement(checkCompSql);
            checkCompanyStmt.setString(1, companyCode.trim());
            rs = checkCompanyStmt.executeQuery();
            if (!rs.next()) {
                throw new ProjectException("Invalid company code '" + companyCode + "'. Company not found.", 404);
            }
            int companyId = rs.getInt("company_id");
            String companyName = rs.getString("company_name");
            DatabaseConnector.closeQuietly(rs);

            // 2. Verify username uniqueness
            String checkUserSql = "SELECT user_id FROM users WHERE username = ?";
            checkUserStmt = conn.prepareStatement(checkUserSql);
            checkUserStmt.setString(1, username.trim());
            rs = checkUserStmt.executeQuery();
            if (rs.next()) {
                throw new ProjectException("Username '" + username + "' is already taken.", 409);
            }
            DatabaseConnector.closeQuietly(rs);

            // 3. Insert new employee
            String insertSql = "INSERT INTO users (company_id, username, password, email, role) VALUES (?, ?, ?, ?, ?)";
            insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setInt(1, companyId);
            insertStmt.setString(2, username);
            insertStmt.setString(3, hashedPassword);
            insertStmt.setString(4, email);
            insertStmt.setString(5, (role != null ? role : Role.EMPLOYEE).name());
            insertStmt.executeUpdate();

            rs = insertStmt.getGeneratedKeys();
            if (!rs.next()) {
                throw new ProjectException("Failed to register employee.", 500);
            }
            int userId = rs.getInt(1);

            User user = new User(userId, companyId, username, email, role != null ? role : Role.EMPLOYEE);
            user.setCompanyName(companyName);
            user.setCompanyCode(companyCode);
            return user;

        } catch (SQLException e) {
            throw new ProjectException("Database error during employee registration: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(checkCompanyStmt);
            DatabaseConnector.closeQuietly(checkUserStmt);
            DatabaseConnector.closeQuietly(insertStmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Retrieves user by username with company details for authentication.
     */
    public User getUserByUsername(String username) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "SELECT u.user_id, u.company_id, u.username, u.password, u.email, u.role, " +
                         "       c.company_name, c.company_code " +
                         "FROM users u " +
                         "JOIN companies c ON u.company_id = c.company_id " +
                         "WHERE u.username = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            rs = stmt.executeQuery();

            if (rs.next()) {
                User user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setCompanyId(rs.getInt("company_id"));
                user.setUsername(rs.getString("username"));
                user.setPassword(rs.getString("password"));
                user.setEmail(rs.getString("email"));
                user.setRole(Role.fromString(rs.getString("role")));
                user.setCompanyName(rs.getString("company_name"));
                user.setCompanyCode(rs.getString("company_code"));
                return user;
            }
            return null;
        } catch (SQLException e) {
            throw new ProjectException("Database error finding user: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Retrieves all users belonging to a specific company (tenant isolation).
     */
    public List<User> getUsersByCompany(int companyId) throws ProjectException {
        List<User> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "SELECT u.user_id, u.company_id, u.username, u.email, u.role, " +
                         "       c.company_name, c.company_code " +
                         "FROM users u " +
                         "JOIN companies c ON u.company_id = c.company_id " +
                         "WHERE u.company_id = ? " +
                         "ORDER BY u.role ASC, u.username ASC";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, companyId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                User u = new User();
                u.setUserId(rs.getInt("user_id"));
                u.setCompanyId(rs.getInt("company_id"));
                u.setUsername(rs.getString("username"));
                u.setEmail(rs.getString("email"));
                u.setRole(Role.fromString(rs.getString("role")));
                u.setCompanyName(rs.getString("company_name"));
                u.setCompanyCode(rs.getString("company_code"));
                list.add(u);
            }
            return list;
        } catch (SQLException e) {
            throw new ProjectException("Database error loading users: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Updates a user's role within a company.
     */
    public boolean updateUserRole(int userId, Role newRole, int companyId) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "UPDATE users SET role = ? WHERE user_id = ? AND company_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, newRole.name());
            stmt.setInt(2, userId);
            stmt.setInt(3, companyId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new ProjectException("Database error updating user role: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    // =========================================================================
    // 2. Project Operations (Strict Multi-Tenancy)
    // =========================================================================

    /**
     * Creates a new project.
     */
    public int createProject(Project project) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "INSERT INTO projects (company_id, name, description, start_date, end_date, created_by) " +
                         "VALUES (?, ?, ?, ?, ?, ?)";
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, project.getCompanyId());
            stmt.setString(2, project.getName());
            stmt.setString(3, project.getDescription());
            stmt.setDate(4, project.getStartDate());
            stmt.setDate(5, project.getEndDate());
            stmt.setInt(6, project.getCreatedBy());
            stmt.executeUpdate();

            rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            throw new ProjectException("Failed to retrieve generated project ID.", 500);
        } catch (SQLException e) {
            throw new ProjectException("Database error creating project: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Fetches all projects for a company, enriched with creator username, total tasks, and completed tasks count.
     */
    public List<Project> getProjectsByCompany(int companyId) throws ProjectException {
        List<Project> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "SELECT p.project_id, p.company_id, p.name, p.description, p.start_date, p.end_date, " +
                         "       p.created_by, " +
                         "       u.username AS creator_name, " +
                         "       COUNT(t.task_id) AS total_tasks, " +
                         "       SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END) AS completed_tasks " +
                         "FROM projects p " +
                         "JOIN users u ON p.created_by = u.user_id " +
                         "LEFT JOIN tasks t ON p.project_id = t.project_id " +
                         "WHERE p.company_id = ? " +
                         "GROUP BY p.project_id, p.company_id, p.name, p.description, p.start_date, p.end_date, " +
                         "         p.created_by, u.username " +
                         "ORDER BY p.project_id DESC";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, companyId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                Project p = new Project();
                p.setProjectId(rs.getInt("project_id"));
                p.setCompanyId(rs.getInt("company_id"));
                p.setName(rs.getString("name"));
                p.setDescription(rs.getString("description"));
                p.setStartDate(rs.getDate("start_date"));
                p.setEndDate(rs.getDate("end_date"));
                p.setCreatedBy(rs.getInt("created_by"));
                p.setCreatorName(rs.getString("creator_name"));
                p.setTotalTasks(rs.getInt("total_tasks"));
                p.setCompletedTasks(rs.getInt("completed_tasks"));
                list.add(p);
            }
            return list;
        } catch (SQLException e) {
            throw new ProjectException("Database error loading projects: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Gets project by ID ensuring tenant boundary check.
     */
    public Project getProjectById(int projectId, int companyId) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "SELECT p.project_id, p.company_id, p.name, p.description, p.start_date, p.end_date, " +
                         "       p.created_by, u.username AS creator_name " +
                         "FROM projects p " +
                         "JOIN users u ON p.created_by = u.user_id " +
                         "WHERE p.project_id = ? AND p.company_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, projectId);
            stmt.setInt(2, companyId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                Project p = new Project();
                p.setProjectId(rs.getInt("project_id"));
                p.setCompanyId(rs.getInt("company_id"));
                p.setName(rs.getString("name"));
                p.setDescription(rs.getString("description"));
                p.setStartDate(rs.getDate("start_date"));
                p.setEndDate(rs.getDate("end_date"));
                p.setCreatedBy(rs.getInt("created_by"));
                p.setCreatorName(rs.getString("creator_name"));
                return p;
            }
            return null;
        } catch (SQLException e) {
            throw new ProjectException("Database error finding project: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Deletes project and cascades tasks. Ensures company isolation.
     */
    public boolean deleteProject(int projectId, int companyId) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "DELETE FROM projects WHERE project_id = ? AND company_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, projectId);
            stmt.setInt(2, companyId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new ProjectException("Database error deleting project: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    // =========================================================================
    // 3. Task Operations (Complex JOIN queries & Tenant Isolation)
    // =========================================================================

    /**
     * Creates a new task within a project.
     */
    public int createTask(Task task) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "INSERT INTO tasks (project_id, title, description, assigned_to, priority, due_date, status) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?)";
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, task.getProjectId());
            stmt.setString(2, task.getTitle());
            stmt.setString(3, task.getDescription());
            if (task.getAssignedTo() != null && task.getAssignedTo() > 0) {
                stmt.setInt(4, task.getAssignedTo());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setString(5, task.getPriority() != null ? task.getPriority().name() : Priority.MEDIUM.name());
            stmt.setDate(6, task.getDueDate());
            stmt.setString(7, task.getStatus() != null ? task.getStatus().name() : Status.TODO.name());
            stmt.executeUpdate();

            rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            throw new ProjectException("Failed to retrieve generated task ID.", 500);
        } catch (SQLException e) {
            throw new ProjectException("Database error creating task: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Complex JOIN query to fetch tasks with Project Name and Assigned User Name.
     * Guarantees strict multi-tenant isolation via projects.company_id.
     */
    public List<Task> getTasksByCompany(int companyId, Integer projectId) throws ProjectException {
        List<Task> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT t.task_id, t.project_id, t.title, t.description, t.assigned_to, ")
               .append("       t.priority, t.due_date, t.status, ")
               .append("       p.name AS project_name, p.company_id, ")
               .append("       u.username AS assigned_to_name ")
               .append("FROM tasks t ")
               .append("JOIN projects p ON t.project_id = p.project_id ")
               .append("LEFT JOIN users u ON t.assigned_to = u.user_id ")
               .append("WHERE p.company_id = ? ");

            if (projectId != null && projectId > 0) {
                sql.append("AND t.project_id = ? ");
            }
            sql.append("ORDER BY CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END, ")
               .append("t.due_date ASC, t.task_id DESC");

            stmt = conn.prepareStatement(sql.toString());
            stmt.setInt(1, companyId);
            if (projectId != null && projectId > 0) {
                stmt.setInt(2, projectId);
            }

            rs = stmt.executeQuery();
            while (rs.next()) {
                Task t = new Task();
                t.setTaskId(rs.getInt("task_id"));
                t.setProjectId(rs.getInt("project_id"));
                t.setProjectName(rs.getString("project_name"));
                t.setCompanyId(rs.getInt("company_id"));
                t.setTitle(rs.getString("title"));
                t.setDescription(rs.getString("description"));

                int assignedToVal = rs.getInt("assigned_to");
                if (!rs.wasNull()) {
                    t.setAssignedTo(assignedToVal);
                } else {
                    t.setAssignedTo(null);
                }
                t.setAssignedToName(rs.getString("assigned_to_name"));
                t.setPriority(Priority.fromString(rs.getString("priority")));
                t.setDueDate(rs.getDate("due_date"));
                t.setStatus(Status.fromString(rs.getString("status")));
                list.add(t);
            }
            return list;
        } catch (SQLException e) {
            throw new ProjectException("Database error loading tasks: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Gets a single task ensuring company boundary check.
     */
    public Task getTaskById(int taskId, int companyId) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "SELECT t.task_id, t.project_id, t.title, t.description, t.assigned_to, " +
                         "       t.priority, t.due_date, t.status, " +
                         "       p.name AS project_name, p.company_id, u.username AS assigned_to_name " +
                         "FROM tasks t " +
                         "JOIN projects p ON t.project_id = p.project_id " +
                         "LEFT JOIN users u ON t.assigned_to = u.user_id " +
                         "WHERE t.task_id = ? AND p.company_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, taskId);
            stmt.setInt(2, companyId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                Task t = new Task();
                t.setTaskId(rs.getInt("task_id"));
                t.setProjectId(rs.getInt("project_id"));
                t.setProjectName(rs.getString("project_name"));
                t.setCompanyId(rs.getInt("company_id"));
                t.setTitle(rs.getString("title"));
                t.setDescription(rs.getString("description"));

                int assignedToVal = rs.getInt("assigned_to");
                if (!rs.wasNull()) {
                    t.setAssignedTo(assignedToVal);
                }
                t.setAssignedToName(rs.getString("assigned_to_name"));
                t.setPriority(Priority.fromString(rs.getString("priority")));
                t.setDueDate(rs.getDate("due_date"));
                t.setStatus(Status.fromString(rs.getString("status")));
                return t;
            }
            return null;
        } catch (SQLException e) {
            throw new ProjectException("Database error finding task: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Updates task status (e.g. drag-and-drop in Kanban board) with company tenant protection.
     */
    public boolean updateTaskStatus(int taskId, Status status, int companyId) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "UPDATE tasks t " +
                         "JOIN projects p ON t.project_id = p.project_id " +
                         "SET t.status = ? " +
                         "WHERE t.task_id = ? AND p.company_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, status.name());
            stmt.setInt(2, taskId);
            stmt.setInt(3, companyId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new ProjectException("Database error updating task status: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Deletes a task ensuring tenant isolation.
     */
    public boolean deleteTask(int taskId, int companyId) throws ProjectException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "DELETE t FROM tasks t " +
                         "JOIN projects p ON t.project_id = p.project_id " +
                         "WHERE t.task_id = ? AND p.company_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, taskId);
            stmt.setInt(2, companyId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new ProjectException("Database error deleting task: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }

    /**
     * Queries overdue tasks across all tenants for BackgroundTaskWorker SLA monitoring.
     */
    public List<Task> getOverdueTasks() throws ProjectException {
        List<Task> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnector.getConnection();
            String sql = "SELECT t.task_id, t.project_id, t.title, t.priority, t.due_date, t.status, " +
                         "       p.name AS project_name, u.username AS assigned_to_name " +
                         "FROM tasks t " +
                         "JOIN projects p ON t.project_id = p.project_id " +
                         "LEFT JOIN users u ON t.assigned_to = u.user_id " +
                         "WHERE t.due_date < CURRENT_DATE() AND t.status != 'DONE' " +
                         "ORDER BY t.due_date ASC";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                Task t = new Task();
                t.setTaskId(rs.getInt("task_id"));
                t.setProjectId(rs.getInt("project_id"));
                t.setTitle(rs.getString("title"));
                t.setPriority(Priority.fromString(rs.getString("priority")));
                t.setDueDate(rs.getDate("due_date"));
                t.setStatus(Status.fromString(rs.getString("status")));
                t.setProjectName(rs.getString("project_name"));
                t.setAssignedToName(rs.getString("assigned_to_name"));
                list.add(t);
            }
            return list;
        } catch (SQLException e) {
            throw new ProjectException("Database error checking overdue tasks: " + e.getMessage(), 500, e);
        } finally {
            DatabaseConnector.closeQuietly(rs);
            DatabaseConnector.closeQuietly(stmt);
            DatabaseConnector.closeQuietly(conn);
        }
    }
}
