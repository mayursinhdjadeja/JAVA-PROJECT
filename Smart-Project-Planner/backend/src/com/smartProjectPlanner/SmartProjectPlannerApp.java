package com.smartProjectPlanner;

import com.smartProjectPlanner.config.DatabaseConnector;
import com.smartProjectPlanner.controller.ProjectController;
import com.smartProjectPlanner.dao.ProjectDAO;
import com.smartProjectPlanner.util.BackgroundTaskWorker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Smart Project Planner - Main Application Server.
 * Pure Core Java HTTP Server using com.sun.net.httpserver.HttpServer.
 * No Spring Boot, no external servlet container required.
 */
public class SmartProjectPlannerApp {

    private static final int DEFAULT_PORT = 8080;
    private static BackgroundTaskWorker backgroundWorker;
    private static Thread backgroundWorkerThread;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException ignored) {}
        } else if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Multithreaded request executor pool
            server.setExecutor(Executors.newFixedThreadPool(20));

            // 1. API Route Context
            ProjectController controller = new ProjectController();
            server.createContext("/api/", controller);

            // 2. Static Frontend Route Context
            server.createContext("/", new StaticFileHandler());

            // 3. Start Background SLA Task Monitor Daemon
            ProjectDAO projectDAO = new ProjectDAO();
            backgroundWorker = new BackgroundTaskWorker(projectDAO, 60000);
            backgroundWorkerThread = new Thread(backgroundWorker, "BackgroundTaskWorker-Thread");
            backgroundWorkerThread.setDaemon(true);
            backgroundWorkerThread.start();

            // 4. Test Database Connectivity
            boolean dbConnected = DatabaseConnector.testConnection();

            // 5. Start HTTP Server
            server.start();

            printBanner(port, dbConnected);

            // Graceful Shutdown Hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SmartProjectPlannerApp] Shutting down server gracefully...");
                if (backgroundWorker != null) {
                    backgroundWorker.stop();
                }
                server.stop(1);
                System.out.println("[SmartProjectPlannerApp] Server stopped. Goodbye!");
            }));

        } catch (IOException e) {
            System.err.println("[SmartProjectPlannerApp] FATAL: Could not start HTTP server on port " + port);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printBanner(int port, boolean dbConnected) {
        System.out.println("================================================================================");
        System.out.println("  _____ __  __          _____ _______   _____  _             _   _ ______ _____  ");
        System.out.println(" / ____|  \\/  |   /\\   |  __ \\__   __| |  __ \\| |   /\\      | \\ | |  ____|  __ \\ ");
        System.out.println("| (___ | \\  / |  /  \\  | |__) | | |    | |__) | |  /  \\     |  \\| | |__  | |__) |");
        System.out.println(" \\___ \\| |\\/| | / /\\ \\ |  _  /  | |    |  ___/| | / /\\ \\    | . ` |  __| |  _  / ");
        System.out.println(" ____) | |  | |/ ____ \\| | \\ \\  | |    | |    | |/ ____ \\   | |\\  | |____| | \\ \\ ");
        System.out.println("|_____/|_|  |_/_/    \\_\\_|  \\_\\ |_|    |_|    |_/_/    \\_\\  |_| \\_|______|_|  \\_\\");
        System.out.println("================================================================================");
        System.out.println("  Multi-Tenant SaaS Project & Task Management Platform");
        System.out.println("  Architecture : Pure Core Java (JDK HttpServer) + MySQL JDBC + Vanilla ES6");
        System.out.println("  AI Provider  : Groq API (Cloud LLM Integration)");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("  HTTP Server  : http://localhost:" + port + "/");
        System.out.println("  Landing Page : http://localhost:" + port + "/");
        System.out.println("  Login Page   : http://localhost:" + port + "/login");
        System.out.println("  Register     : http://localhost:" + port + "/register");
        System.out.println("  Dashboard    : http://localhost:" + port + "/dashboard");
        System.out.println("  API Root     : http://localhost:" + port + "/api/health");
        System.out.println("  Database     : " + (dbConnected ? "[CONNECTED] MySQL Ready" : "[PENDING] Check MySQL credentials in DatabaseConnector.java"));
        System.out.println("================================================================================");
    }

    /**
     * Flawless Static File Handler for serving Vanilla HTML5, CSS3, and JavaScript assets.
     */
    public static class StaticFileHandler implements HttpHandler {

        private static final Map<String, String> MIME_TYPES = new HashMap<>();

        static {
            MIME_TYPES.put("html", "text/html; charset=UTF-8");
            MIME_TYPES.put("htm", "text/html; charset=UTF-8");
            MIME_TYPES.put("css", "text/css; charset=UTF-8");
            MIME_TYPES.put("js", "application/javascript; charset=UTF-8");
            MIME_TYPES.put("json", "application/json; charset=UTF-8");
            MIME_TYPES.put("png", "image/png");
            MIME_TYPES.put("jpg", "image/jpeg");
            MIME_TYPES.put("jpeg", "image/jpeg");
            MIME_TYPES.put("gif", "image/gif");
            MIME_TYPES.put("svg", "image/svg+xml");
            MIME_TYPES.put("ico", "image/x-icon");
            MIME_TYPES.put("woff", "font/woff");
            MIME_TYPES.put("woff2", "font/woff2");
            MIME_TYPES.put("ttf", "font/ttf");
        }

        private final Path frontendBaseDir;

        public StaticFileHandler() {
            // Locate frontend directory relative to working directory
            Path candidate = Paths.get("frontend");
            if (!Files.exists(candidate)) {
                // Try parent directory or standard location
                candidate = Paths.get("..", "frontend");
                if (!Files.exists(candidate)) {
                    candidate = Paths.get(System.getProperty("user.dir"), "frontend");
                }
            }
            this.frontendBaseDir = candidate.toAbsolutePath().normalize();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Route shortcuts for clean SPA URLs
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            } else if ("/login".equals(path)) {
                path = "/login.html";
            } else if ("/register".equals(path)) {
                path = "/register.html";
            } else if ("/dashboard".equals(path)) {
                path = "/dashboard.html";
            }

            // Security check against Directory Traversal attacks
            if (path.contains("..")) {
                sendError(exchange, 403, "Access Forbidden");
                return;
            }

            // Clean leading slash
            String relative = path.startsWith("/") ? path.substring(1) : path;
            Path resolvedFile = frontendBaseDir.resolve(relative).normalize();

            // Ensure the resolved file path stays inside frontend directory
            if (!resolvedFile.startsWith(frontendBaseDir) || !Files.exists(resolvedFile) || Files.isDirectory(resolvedFile)) {
                sendError(exchange, 404, "Page Not Found: " + path);
                return;
            }

            String extension = getFileExtension(resolvedFile.getFileName().toString());
            String contentType = MIME_TYPES.getOrDefault(extension.toLowerCase(), "application/octet-stream");

            File file = resolvedFile.toFile();
            long fileLength = file.length();

            exchange.getResponseHeaders().set("Content-Type", contentType);
            // Cache static assets (CSS, JS) for 1 hour, HTML no-cache for instant live updates
            if ("html".equalsIgnoreCase(extension)) {
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            } else {
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            }

            exchange.sendResponseHeaders(200, fileLength);

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
            }
        }

        private String getFileExtension(String filename) {
            int dotIdx = filename.lastIndexOf('.');
            return (dotIdx == -1 || dotIdx == filename.length() - 1) ? "" : filename.substring(dotIdx + 1);
        }

        private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
            String html = "<!DOCTYPE html><html><head><title>" + statusCode + " - " + message + "</title>"
                    + "<style>body{background:#0a0c10;color:#e6edf3;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;flex-direction:column;}"
                    + "h1{font-size:3rem;margin-bottom:0.5rem;color:#00F0FF;}p{color:#8b949e;margin-bottom:1.5rem;}"
                    + "a{color:#00E599;text-decoration:none;border:1px solid #30363d;padding:0.6rem 1.2rem;border-radius:6px;transition:0.2s;}"
                    + "a:hover{background:#161b22;border-color:#8b949e;}</style></head>"
                    + "<body><h1>" + statusCode + "</h1><p>" + message + "</p><a href='/'>Return to Home</a></body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
