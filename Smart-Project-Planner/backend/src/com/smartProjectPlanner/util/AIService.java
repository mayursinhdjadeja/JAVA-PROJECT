package com.smartProjectPlanner.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AI Service integrating Groq Cloud API using standard java.net.http.HttpClient.
 * Generates smart, professional task descriptions, acceptance criteria, and subtasks.
 */
public class AIService {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    private final HttpClient httpClient;
    private final String apiKey;

    public AIService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String key = "";
        if (key == null || key.trim().isEmpty()) {
            key = System.getenv("GROQ_API_KEY");
        }
        if (key == null || key.trim().isEmpty()) {
            key = System.getProperty("groq.api.key", "");
        }
        this.apiKey = key != null ? key.trim() : "";
    }

    /**
     * Generates an enriched task description based on task title, project name, and extra context.
     * @param title Task Title (e.g. "Implement OAuth2 login")
     * @param projectName Project Name (e.g. "Cloud Portal")
     * @param extraContext Optional user instructions
     * @return Generated Markdown description
     */
    public String generateTaskDescription(String title, String projectName, String extraContext) {
        if (title == null || title.trim().isEmpty()) {
            return "Please provide a valid task title to generate description.";
        }

        // If no API key is provided, use our intelligent offline template generator
        if (apiKey.isEmpty()) {
            System.out.println("[AIService] GROQ_API_KEY not configured. Generating offline AI template.");
            return generateOfflineDescription(title, projectName, extraContext);
        }

        try {
            String prompt = "Project: " + (projectName != null ? projectName : "General") + "\n" +
                    "Task Title: " + title + "\n" +
                    (extraContext != null && !extraContext.trim().isEmpty() ? "Additional Context: " + extraContext + "\n" : "") +
                    "Generate a concise, professional task description formatted in Markdown with:\n" +
                    "1. Overview & Objective\n" +
                    "2. Technical Requirements / Key Considerations\n" +
                    "3. Acceptance Criteria (checklist with - [ ])\n" +
                    "4. Suggested Subtasks";

            String jsonPayload = buildGroqJsonPayload(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_API_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String extracted = extractContentFromGroqResponse(response.body());
                if (extracted != null && !extracted.trim().isEmpty()) {
                    return extracted;
                }
            } else {
                System.err.println("[AIService] Groq API returned status " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[AIService] Error calling Groq API: " + e.getMessage());
        }

        // Fallback to intelligent template if remote call fails
        return generateOfflineDescription(title, projectName, extraContext);
    }

    /**
     * Builds manual JSON request payload without external libraries.
     */
    private String buildGroqJsonPayload(String prompt) {
        String escapedPrompt = escapeJsonString(prompt);
        String systemPrompt = escapeJsonString("You are a Principal Engineering Lead and Product Manager. " +
                "Write clear, actionable, production-grade technical task descriptions. Use clean Markdown formatting with bullet points and checklists.");

        return "{"
                + "\"model\": \"" + DEFAULT_MODEL + "\","
                + "\"messages\": ["
                + "  {\"role\": \"system\", \"content\": \"" + systemPrompt + "\"},"
                + "  {\"role\": \"user\", \"content\": \"" + escapedPrompt + "\"}"
                + "],"
                + "\"temperature\": 0.4,"
                + "\"max_tokens\": 800"
                + "}";
    }

    /**
     * Manual JSON content extraction from Groq / OpenAI response format:
     * choices[0].message.content
     */
    private String extractContentFromGroqResponse(String json) {
        if (json == null) return null;
        int messageIdx = json.indexOf("\"message\"");
        if (messageIdx == -1) return null;

        int contentKeyIdx = json.indexOf("\"content\":", messageIdx);
        if (contentKeyIdx == -1) return null;

        int startQuote = json.indexOf("\"", contentKeyIdx + 10);
        if (startQuote == -1) return null;

        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else if (c == 'b') sb.append('\b');
                else if (c == 'f') sb.append('\f');
                else if (c == 'u' && i + 4 < json.length()) {
                    try {
                        String hex = json.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    } catch (NumberFormatException ignored) {
                        sb.append(c);
                    }
                } else {
                    sb.append(c);
                }
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                break; // End of string
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Escapes raw string into a safe JSON string value.
     */
    public static String escapeJsonString(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String hex = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(hex.substring(hex.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * High-quality offline template generator when GROQ_API_KEY is not configured or network is isolated.
     */
    private String generateOfflineDescription(String title, String projectName, String extraContext) {
        String pName = (projectName != null && !projectName.trim().isEmpty()) ? projectName : "Active Workspace";
        return "### Objective\n" +
                "Implement and deliver **" + title + "** for project *" + pName + "* to ensure reliability, security, and velocity.\n\n" +
                (extraContext != null && !extraContext.trim().isEmpty() ? "### Context & Notes\n" + extraContext + "\n\n" : "") +
                "### Technical Scope & Implementation Steps\n" +
                "- [ ] Review architectural requirements and database dependencies.\n" +
                "- [ ] Implement the core logic adhering to strict data validation standards.\n" +
                "- [ ] Ensure proper error handling, logging, and security boundary checks.\n" +
                "- [ ] Write unit and integration test coverage.\n\n" +
                "### Acceptance Criteria\n" +
                "- [ ] Functionality verified and passing all automated test suites.\n" +
                "- [ ] Multi-tenant isolation verified with zero cross-tenant data leakage.\n" +
                "- [ ] Code review completed and approved by team lead.\n" +
                "- [ ] Deployment documentation updated.";
    }
}
