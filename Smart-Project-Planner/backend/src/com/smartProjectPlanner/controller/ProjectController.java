package com.smartProjectPlanner.controller;

import com.smartProjectPlanner.exception.ProjectException;
import com.smartProjectPlanner.model.*;
import com.smartProjectPlanner.service.ProjectService;
import com.smartProjectPlanner.service.ProjectServiceImplementation;
import com.smartProjectPlanner.util.AIService;
import com.smartProjectPlanner.util.SecurityUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.*;

/**
 * Controller handling all REST API endpoints.
 * Pure Core Java without external JSON libraries (Gson/Jackson).
 * Implements custom manual JSON tokenizer, parser, and serializer.
 */
public class ProjectController implements HttpHandler {

    private final ProjectService projectService;
    private final AIService aiService;

    public ProjectController() {
        this.projectService = new ProjectServiceImplementation();
        this.aiService = new AIService();
    }

    public ProjectController(ProjectService projectService, AIService aiService) {
        this.projectService = projectService;
        this.aiService = aiService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Handle CORS Preflight (OPTIONS)
        addCorsHeaders(exchange);
        String method = exchange.getRequestMethod().toUpperCase();
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        // Normalize path (strip trailing slash if not root)
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        try {
            switch (path) {
                case "/api/login":
                    if ("POST".equals(method)) handleLogin(exchange);
                    else sendMethodNotAllowed(exchange);
                    break;

                case "/api/register/company":
                    if ("POST".equals(method)) handleRegisterCompany(exchange);
                    else sendMethodNotAllowed(exchange);
                    break;

                case "/api/register/employee":
                    if ("POST".equals(method)) handleRegisterEmployee(exchange);
                    else sendMethodNotAllowed(exchange);
                    break;

                case "/api/projects":
                    if ("GET".equals(method)) handleGetProjects(exchange);
                    else if ("POST".equals(method)) handleCreateProject(exchange);
                    else if ("DELETE".equals(method)) handleDeleteProject(exchange);
                    else sendMethodNotAllowed(exchange);
                    break;

                case "/api/tasks":
                    if ("GET".equals(method)) handleGetTasks(exchange);
                    else if ("POST".equals(method)) handleCreateTask(exchange);
                    else if ("DELETE".equals(method)) handleDeleteTask(exchange);
                    else sendMethodNotAllowed(exchange);
                    break;

                case "/api/tasks/status":
                    if ("POST".equals(method)) handleUpdateTaskStatus(exchange);
                    else sendMethodNotAllowed(exchange);
                    break;

                case "/api/users":
                    if ("GET".equals(method)) handleGetUsers(exchange);
                    else if ("POST".equals(method)) handleUpdateUserRole(exchange);
                    else sendMethodNotAllowed(exchange);
                    break;

                case "/api/ai/generate":
                    if ("POST".equals(method)) handleGenerateAIDescription(exchange);
                    else sendMethodNotAllowed(exchange);
                    break;

                case "/api/health":
                    sendJsonResponse(exchange, 200, "{\"status\": \"UP\", \"service\": \"Smart Project Planner API\"}");
                    break;

                default:
                    sendErrorResponse(exchange, 404, "Endpoint not found: " + path);
            }
        } catch (ProjectException pe) {
            sendErrorResponse(exchange, pe.getStatusCode(), pe.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    // =========================================================================
    // Endpoint Handlers
    // =========================================================================

    private void handleLogin(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, Object> body = readJsonBody(exchange);
        String username = getString(body, "username", "");
        String password = getString(body, "password", "");

        User user = projectService.login(username, password);
        String token = SecurityUtil.generateToken(user);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Login successful");
        resp.put("token", token);
        resp.put("user", serializeUser(user));

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    private void handleRegisterCompany(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, Object> body = readJsonBody(exchange);
        String companyName = getString(body, "companyName", "");
        String companyCode = getString(body, "companyCode", "");
        String username = getString(body, "username", "");
        String password = getString(body, "password", "");
        String email = getString(body, "email", "");

        User user = projectService.registerCompany(companyName, companyCode, username, password, email);
        String token = SecurityUtil.generateToken(user);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Company and administrator account created successfully");
        resp.put("token", token);
        resp.put("user", serializeUser(user));

        sendJsonResponse(exchange, 201, toJson(resp));
    }

    private void handleRegisterEmployee(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, Object> body = readJsonBody(exchange);
        String companyCode = getString(body, "companyCode", "");
        String username = getString(body, "username", "");
        String password = getString(body, "password", "");
        String email = getString(body, "email", "");
        Role role = Role.fromString(getString(body, "role", "EMPLOYEE"));

        User user = projectService.registerEmployee(companyCode, username, password, email, role);
        String token = SecurityUtil.generateToken(user);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Account created successfully. Welcome to the workspace!");
        resp.put("token", token);
        resp.put("user", serializeUser(user));

        sendJsonResponse(exchange, 201, toJson(resp));
    }

    private void handleGetProjects(HttpExchange exchange) throws IOException, ProjectException {
        User currentUser = authenticate(exchange, Collections.emptyMap());
        List<Project> projects = projectService.getProjects(currentUser.getCompanyId());

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Project p : projects) {
            serialized.add(serializeProject(p));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("projects", serialized);

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    private void handleCreateProject(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, Object> body = readJsonBody(exchange);
        User currentUser = authenticate(exchange, body);

        Project project = new Project();
        project.setName(getString(body, "name", ""));
        project.setDescription(getString(body, "description", ""));

        String startStr = getString(body, "startDate", null);
        String endStr = getString(body, "endDate", null);

        if (startStr != null) {
            try { project.setStartDate(Date.valueOf(startStr)); } catch (Exception ignored) {}
        }
        if (endStr != null) {
            try { project.setEndDate(Date.valueOf(endStr)); } catch (Exception ignored) {}
        }

        Project created = projectService.createProject(project, currentUser);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Project created successfully");
        resp.put("project", serializeProject(created));

        sendJsonResponse(exchange, 201, toJson(resp));
    }

    private void handleDeleteProject(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        Map<String, Object> body = "DELETE".equals(exchange.getRequestMethod()) ? readJsonBodyQuietly(exchange) : Collections.emptyMap();
        User currentUser = authenticate(exchange, body);

        int projectId = 0;
        if (query.containsKey("id")) {
            projectId = Integer.parseInt(query.get("id"));
        } else if (body.containsKey("projectId")) {
            projectId = getInt(body, "projectId", 0);
        } else if (body.containsKey("id")) {
            projectId = getInt(body, "id", 0);
        }

        if (projectId <= 0) {
            throw new ProjectException("Project ID is required for deletion.", 400);
        }

        boolean deleted = projectService.deleteProject(projectId, currentUser.getCompanyId(), currentUser);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", deleted);
        resp.put("message", deleted ? "Project deleted successfully" : "Project not found");

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    private void handleGetTasks(HttpExchange exchange) throws IOException, ProjectException {
        User currentUser = authenticate(exchange, Collections.emptyMap());
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());

        Integer projectId = null;
        if (query.containsKey("projectId") && !query.get("projectId").isEmpty()) {
            try {
                projectId = Integer.parseInt(query.get("projectId"));
            } catch (NumberFormatException ignored) {}
        }

        List<Task> tasks = projectService.getTasks(currentUser.getCompanyId(), projectId);
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Task t : tasks) {
            serialized.add(serializeTask(t));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("tasks", serialized);

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    private void handleCreateTask(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, Object> body = readJsonBody(exchange);
        User currentUser = authenticate(exchange, body);

        Task task = new Task();
        task.setProjectId(getInt(body, "projectId", 0));
        task.setTitle(getString(body, "title", ""));
        task.setDescription(getString(body, "description", ""));

        int assignedToVal = getInt(body, "assignedTo", 0);
        if (assignedToVal > 0) {
            task.setAssignedTo(assignedToVal);
        }

        task.setPriority(Priority.fromString(getString(body, "priority", "MEDIUM")));
        task.setStatus(Status.fromString(getString(body, "status", "TODO")));

        String dueStr = getString(body, "dueDate", null);
        if (dueStr != null) {
            try { task.setDueDate(Date.valueOf(dueStr)); } catch (Exception ignored) {}
        }

        Task created = projectService.createTask(task, currentUser);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Task created successfully");
        resp.put("task", serializeTask(created));

        sendJsonResponse(exchange, 201, toJson(resp));
    }

    private void handleDeleteTask(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        Map<String, Object> body = "DELETE".equals(exchange.getRequestMethod()) ? readJsonBodyQuietly(exchange) : Collections.emptyMap();
        User currentUser = authenticate(exchange, body);

        int taskId = 0;
        if (query.containsKey("id")) {
            taskId = Integer.parseInt(query.get("id"));
        } else if (body.containsKey("taskId")) {
            taskId = getInt(body, "taskId", 0);
        } else if (body.containsKey("id")) {
            taskId = getInt(body, "id", 0);
        }

        if (taskId <= 0) {
            throw new ProjectException("Task ID is required for deletion.", 400);
        }

        boolean deleted = projectService.deleteTask(taskId, currentUser.getCompanyId(), currentUser);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", deleted);
        resp.put("message", deleted ? "Task deleted successfully" : "Task not found");

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    private void handleUpdateTaskStatus(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, Object> body = readJsonBody(exchange);
        User currentUser = authenticate(exchange, body);

        int taskId = getInt(body, "taskId", 0);
        if (taskId <= 0) {
            taskId = getInt(body, "id", 0);
        }
        if (taskId <= 0) {
            throw new ProjectException("Valid taskId is required.", 400);
        }

        String statusStr = getString(body, "status", null);
        if (statusStr == null) {
            throw new ProjectException("Status is required.", 400);
        }
        Status status = Status.fromString(statusStr);

        boolean updated = projectService.updateTaskStatus(taskId, status, currentUser.getCompanyId(), currentUser);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", updated);
        resp.put("message", "Task status updated to " + status.name());

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    private void handleGetUsers(HttpExchange exchange) throws IOException, ProjectException {
        User currentUser = authenticate(exchange, Collections.emptyMap());
        List<User> users = projectService.getCompanyUsers(currentUser.getCompanyId());

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (User u : users) {
            serialized.add(serializeUser(u));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("users", serialized);

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    private void handleUpdateUserRole(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, Object> body = readJsonBody(exchange);
        User currentUser = authenticate(exchange, body);

        int targetUserId = getInt(body, "userId", 0);
        String roleStr = getString(body, "role", null);
        if (targetUserId <= 0 || roleStr == null) {
            throw new ProjectException("userId and role are required.", 400);
        }

        Role newRole = Role.fromString(roleStr);
        boolean updated = projectService.updateUserRole(targetUserId, newRole, currentUser.getCompanyId(), currentUser);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", updated);
        resp.put("message", "User role updated successfully to " + newRole.name());

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    private void handleGenerateAIDescription(HttpExchange exchange) throws IOException, ProjectException {
        Map<String, Object> body = readJsonBody(exchange);
        // Ensure user is authenticated
        authenticate(exchange, body);

        String title = getString(body, "title", "");
        String projectName = getString(body, "projectName", "Smart Workspace");
        String context = getString(body, "context", "");

        if (title.trim().isEmpty()) {
            throw new ProjectException("Task title is required to generate AI description.", 400);
        }

        String description = aiService.generateTaskDescription(title, projectName, context);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("description", description);

        sendJsonResponse(exchange, 200, toJson(resp));
    }

    // =========================================================================
    // Authentication & Context Resolution
    // =========================================================================

    /**
     * Resolves the current user identity from:
     * 1. Authorization: Bearer <token>
     * 2. X-User-Id, X-Company-Id, X-User-Role headers
     * 3. Request body parameters (fallback)
     */
    private User authenticate(HttpExchange exchange, Map<String, Object> body) throws ProjectException {
        // 1. Try Token
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && !authHeader.trim().isEmpty()) {
            SecurityUtil.AuthContext ctx = SecurityUtil.parseToken(authHeader);
            if (ctx.valid) {
                User u = new User();
                u.setUserId(ctx.userId);
                u.setCompanyId(ctx.companyId);
                u.setRole(ctx.role);
                return u;
            }
        }

        // 2. Try Headers
        String headerUserId = exchange.getRequestHeaders().getFirst("X-User-Id");
        String headerCompanyId = exchange.getRequestHeaders().getFirst("X-Company-Id");
        String headerRole = exchange.getRequestHeaders().getFirst("X-User-Role");

        if (headerUserId != null && headerCompanyId != null) {
            try {
                User u = new User();
                u.setUserId(Integer.parseInt(headerUserId.trim()));
                u.setCompanyId(Integer.parseInt(headerCompanyId.trim()));
                u.setRole(Role.fromString(headerRole));
                return u;
            } catch (Exception ignored) {}
        }

        // 3. Try Body Fallback
        int bodyUserId = getInt(body, "userId", 0);
        int bodyCompanyId = getInt(body, "companyId", 0);
        if (bodyUserId > 0 && bodyCompanyId > 0) {
            User u = new User();
            u.setUserId(bodyUserId);
            u.setCompanyId(bodyCompanyId);
            u.setRole(Role.fromString(getString(body, "role", "EMPLOYEE")));
            return u;
        }

        throw new ProjectException("Unauthorized: Authentication token or tenant headers required.", 401);
    }

    // =========================================================================
    // Serialization Helpers (POJO -> Map)
    // =========================================================================

    private Map<String, Object> serializeUser(User u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", u.getUserId());
        map.put("companyId", u.getCompanyId());
        map.put("companyName", u.getCompanyName());
        map.put("companyCode", u.getCompanyCode());
        map.put("username", u.getUsername());
        map.put("email", u.getEmail());
        map.put("role", u.getRole() != null ? u.getRole().name() : "EMPLOYEE");
        return map;
    }

    private Map<String, Object> serializeProject(Project p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectId", p.getProjectId());
        map.put("companyId", p.getCompanyId());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("startDate", p.getStartDate() != null ? p.getStartDate().toString() : "");
        map.put("endDate", p.getEndDate() != null ? p.getEndDate().toString() : "");
        map.put("createdBy", p.getCreatedBy());
        map.put("creatorName", p.getCreatorName());
        map.put("totalTasks", p.getTotalTasks());
        map.put("completedTasks", p.getCompletedTasks());
        return map;
    }

    private Map<String, Object> serializeTask(Task t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", t.getTaskId());
        map.put("projectId", t.getProjectId());
        map.put("projectName", t.getProjectName());
        map.put("companyId", t.getCompanyId());
        map.put("title", t.getTitle());
        map.put("description", t.getDescription());
        map.put("assignedTo", t.getAssignedTo());
        map.put("assignedToName", t.getAssignedToName() != null ? t.getAssignedToName() : "Unassigned");
        map.put("priority", t.getPriority() != null ? t.getPriority().name() : "MEDIUM");
        map.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : "");
        map.put("status", t.getStatus() != null ? t.getStatus().name() : "TODO");
        return map;
    }

    // =========================================================================
    // HTTP Response & CORS Helpers
    // =========================================================================

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Company-Id, X-User-Id, X-User-Role");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("error", message);
        sendJsonResponse(exchange, statusCode, toJson(err));
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendErrorResponse(exchange, 405, "Method " + exchange.getRequestMethod() + " Not Allowed");
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (idx > 0) {
                params.put(pair.substring(0, idx), "");
            }
        }
        return params;
    }

    private Map<String, Object> readJsonBodyQuietly(HttpExchange exchange) {
        try {
            return readJsonBody(exchange);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException, ProjectException {
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            String jsonStr = buffer.toString(StandardCharsets.UTF_8).trim();
            if (jsonStr.isEmpty()) {
                return new HashMap<>();
            }
            Object parsed = parseJson(jsonStr);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                return map;
            }
            return new HashMap<>();
        } catch (Exception e) {
            throw new ProjectException("Malformed JSON request body: " + e.getMessage(), 400);
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key)).trim();
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // =========================================================================
    // Manual JSON Parser & Serializer (Zero External Dependencies)
    // =========================================================================

    /**
     * Converts a Java Object (Map, List, Primitives, Strings) into standard JSON string.
     */
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + AIService.escapeJsonString((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Enum) {
            return "\"" + ((Enum<?>) obj).name() + "\"";
        }
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(AIService.escapeJsonString(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + AIService.escapeJsonString(obj.toString()) + "\"";
    }

    /**
     * Entry point for manual JSON Parsing.
     */
    public static Object parseJson(String json) {
        if (json == null) return null;
        JsonTokenizer tokenizer = new JsonTokenizer(json);
        return parseValue(tokenizer);
    }

    private static Object parseValue(JsonTokenizer tokenizer) {
        tokenizer.skipWhitespace();
        if (tokenizer.isEof()) return null;

        char c = tokenizer.peek();
        if (c == '{') {
            return parseObject(tokenizer);
        } else if (c == '[') {
            return parseArray(tokenizer);
        } else if (c == '"') {
            return parseString(tokenizer);
        } else if (c == 't' || c == 'f') {
            return parseBoolean(tokenizer);
        } else if (c == 'n') {
            return parseNull(tokenizer);
        } else if (c == '-' || Character.isDigit(c)) {
            return parseNumber(tokenizer);
        } else {
            tokenizer.next(); // Skip unknown char
            return null;
        }
    }

    private static Map<String, Object> parseObject(JsonTokenizer tokenizer) {
        Map<String, Object> map = new LinkedHashMap<>();
        tokenizer.expect('{');
        tokenizer.skipWhitespace();

        if (tokenizer.peek() == '}') {
            tokenizer.next();
            return map;
        }

        while (!tokenizer.isEof()) {
            tokenizer.skipWhitespace();
            String key = parseString(tokenizer);
            tokenizer.skipWhitespace();
            tokenizer.expect(':');
            Object value = parseValue(tokenizer);
            map.put(key, value);

            tokenizer.skipWhitespace();
            char next = tokenizer.next();
            if (next == '}') {
                break;
            } else if (next != ',') {
                break;
            }
        }
        return map;
    }

    private static List<Object> parseArray(JsonTokenizer tokenizer) {
        List<Object> list = new ArrayList<>();
        tokenizer.expect('[');
        tokenizer.skipWhitespace();

        if (tokenizer.peek() == ']') {
            tokenizer.next();
            return list;
        }

        while (!tokenizer.isEof()) {
            tokenizer.skipWhitespace();
            Object item = parseValue(tokenizer);
            list.add(item);

            tokenizer.skipWhitespace();
            char next = tokenizer.next();
            if (next == ']') {
                break;
            } else if (next != ',') {
                break;
            }
        }
        return list;
    }

    private static String parseString(JsonTokenizer tokenizer) {
        tokenizer.expect('"');
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;

        while (!tokenizer.isEof()) {
            char c = tokenizer.next();
            if (escaping) {
                switch (c) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        StringBuilder hex = new StringBuilder();
                        for (int i = 0; i < 4 && !tokenizer.isEof(); i++) {
                            hex.append(tokenizer.next());
                        }
                        try {
                            sb.append((char) Integer.parseInt(hex.toString(), 16));
                        } catch (Exception ignored) {}
                        break;
                    default: sb.append(c);
                }
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Number parseNumber(JsonTokenizer tokenizer) {
        StringBuilder sb = new StringBuilder();
        while (!tokenizer.isEof()) {
            char c = tokenizer.peek();
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || Character.isDigit(c)) {
                sb.append(tokenizer.next());
            } else {
                break;
            }
        }
        String str = sb.toString();
        if (str.contains(".") || str.contains("e") || str.contains("E")) {
            try { return Double.parseDouble(str); } catch (Exception e) { return 0.0; }
        } else {
            try { return Long.parseLong(str); } catch (Exception e) { return 0L; }
        }
    }

    private static Boolean parseBoolean(JsonTokenizer tokenizer) {
        StringBuilder sb = new StringBuilder();
        while (!tokenizer.isEof() && Character.isLetter(tokenizer.peek())) {
            sb.append(tokenizer.next());
        }
        return Boolean.parseBoolean(sb.toString());
    }

    private static Object parseNull(JsonTokenizer tokenizer) {
        StringBuilder sb = new StringBuilder();
        while (!tokenizer.isEof() && Character.isLetter(tokenizer.peek())) {
            sb.append(tokenizer.next());
        }
        return null;
    }

    /**
     * Character stream reader for JSON parsing.
     */
    private static class JsonTokenizer {
        private final String text;
        private int pos = 0;

        public JsonTokenizer(String text) {
            this.text = text != null ? text : "";
        }

        public boolean isEof() {
            return pos >= text.length();
        }

        public char peek() {
            return isEof() ? '\0' : text.charAt(pos);
        }

        public char next() {
            return isEof() ? '\0' : text.charAt(pos++);
        }

        public void expect(char expected) {
            char c = next();
            if (c != expected) {
                // Keep moving forward gracefully
            }
        }

        public void skipWhitespace() {
            while (!isEof() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }
}
