package com.frunoyman.perf.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.frunoyman.perf.config.MockServerSettings;
import com.frunoyman.perf.utils.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

/**
 * The template's example service — an MCP server over Streamable HTTP on the JDK's own {@link HttpServer}, so {@code ./gradlew
 * gatlingRun} works on a fresh clone with nothing installed. It behaves like a real one where a load test notices:
 * <ul>
 *   <li>{@code POST /oauth/token} — client-credentials grant; tokens expire ({@code token-lifetime-seconds}), an expired one is 401;</li>
 *   <li>{@code POST /mcp} — initialize (answers {@code Mcp-Session-Id}), notifications/initialized (202), tools/list, tools/call —
 *       {@code search_companies} answers as an event stream, {@code get_company} as JSON, a bad argument is HTTP 200 with
 *       {@code isError: true}; an unknown session is 404;</li>
 *   <li>{@code DELETE /mcp} — closes the session;</li>
 *   <li>{@code GET /api/companies?size=N} — the feeders' data source, a random sample each time.</li>
 * </ul>
 * Latency: tens of milliseconds for the protocol, around a hundred for a tool. {@code failure-percent} turns that share of tool calls
 * into tool errors. ONBOARD: delete this package and the {@code mock-server} blocks once the real service is wired.
 */
@Slf4j
public class MockMcpServer {

    private static final String SEARCH = "search_companies";
    private static final String GET_COMPANY = "get_company";
    private static final List<String> WORDS = List.of("Blue", "North", "Quantum", "Green", "Atlas", "Silver", "Nova", "Delta",
            "Harbor", "Summit", "Pixel", "Cedar", "Orbit", "Vector", "Lumen", "Granite", "Echo", "Maple", "Falcon", "Iron");
    private static final List<String> KINDS = List.of("Labs", "Systems", "Capital", "Health", "Robotics", "Foods", "Energy", "Logistics");

    private final MockServerSettings settings;
    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private HttpServer server;

    public MockMcpServer(MockServerSettings settings) {
        this.settings = settings;
    }

    public void start() {
        if (!settings.enabled()) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", settings.port()), 1024);
        } catch (IOException e) {
            throw new IllegalStateException("Mock MCP server: port " + settings.port() + " is taken", e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/oauth/token", this::token);
        server.createContext("/mcp", this::mcp);
        server.createContext("/api/companies", this::companies);
        server.start();
        log.info("Mock MCP server on http://localhost:{} (tool failures {} %, tokens live {} s)", settings.port(),
                settings.failurePercent(), settings.tokenLifetimeSeconds());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(read(exchange.getRequestBody()));
        if (!"client_credentials".equals(form.get("grant_type")) || blank(form.get("client_id")) || blank(form.get("client_secret"))) {
            send(exchange, 401, "application/json", "{\"error\":\"invalid_client\"}");
            return;
        }
        String token = UUID.randomUUID().toString();
        tokens.put(token, Instant.now().plusSeconds(settings.tokenLifetimeSeconds()));
        send(exchange, 200, "application/json", Json.toJson(Map.of("access_token", token, "token_type", "Bearer",
                "expires_in", settings.tokenLifetimeSeconds())));
    }

    private void mcp(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            send(exchange, 401, "application/json", "{\"error\":\"invalid_token\"}");
            return;
        }
        String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        if ("DELETE".equals(exchange.getRequestMethod())) {
            send(exchange, sessions.remove(sessionId) ? 204 : 404, "application/json", "");
            return;
        }
        Map<String, Object> message = parse(read(exchange.getRequestBody()));
        Object method = message.get("method");
        if ("initialize".equals(method)) {
            String newSession = UUID.randomUUID().toString();
            sessions.add(newSession);
            exchange.getResponseHeaders().add("Mcp-Session-Id", newSession);
            pause(10, 40);
            send(exchange, 200, "application/json", result(message, Map.of("protocolVersion", "2025-06-18",
                    "capabilities", Map.of("tools", Map.of()), "serverInfo", Map.of("name", "mock-mcp", "version", "1.0"))));
            return;
        }
        if (sessionId == null || !sessions.contains(sessionId)) {
            send(exchange, 404, "application/json", "{\"error\":\"unknown session\"}");
            return;
        }
        switch (String.valueOf(method)) {
            case "notifications/initialized" -> send(exchange, 202, "application/json", "");
            case "tools/list" -> {
                pause(5, 20);
                send(exchange, 200, "application/json", result(message, Map.of("tools", List.of(
                        tool(SEARCH, "name"), tool(GET_COMPANY, "company_id")))));
            }
            case "tools/call" -> toolCall(exchange, message);
            default -> send(exchange, 200, "application/json", Json.toJson(Map.of("jsonrpc", "2.0", "id", message.get("id"),
                    "error", Map.of("code", -32601, "message", "Method not found: " + method))));
        }
    }

    @SuppressWarnings("unchecked")
    private void toolCall(HttpExchange exchange, Map<String, Object> message) throws IOException {
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
        String tool = String.valueOf(params.get("name"));
        String required = SEARCH.equals(tool) ? "name" : GET_COMPANY.equals(tool) ? "company_id" : null;
        if (required == null) {
            send(exchange, 200, "application/json", Json.toJson(Map.of("jsonrpc", "2.0", "id", message.get("id"),
                    "error", Map.of("code", -32602, "message", "Unknown tool: " + tool))));
            return;
        }
        pause(SEARCH.equals(tool) ? 40 : 60, SEARCH.equals(tool) ? 140 : 200);
        String answer;
        if (blank((String) arguments.get(required))) {
            answer = toolResult(message, true, "Missing required argument: " + required);
        } else if (ThreadLocalRandom.current().nextInt(100) < settings.failurePercent()) {
            answer = toolResult(message, true, "Simulated failure (mock-server.failure-percent)");
        } else {
            answer = toolResult(message, false, tool + " answered for " + arguments.get(required));
        }
        if (SEARCH.equals(tool)) {
            send(exchange, 200, "text/event-stream", "event: message\ndata: " + answer + "\n\n");
        } else {
            send(exchange, 200, "application/json", answer);
        }
    }

    private void companies(HttpExchange exchange) throws IOException {
        int size = Integer.parseInt(form(exchange.getRequestURI().getRawQuery()).getOrDefault("size", "100"));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Map<String, String>> items = IntStream.range(0, size).mapToObj(index -> {
            int id = random.nextInt(1_000_000);
            return Map.of("id", "C-" + id, "name", WORDS.get(id % WORDS.size()) + " " + WORDS.get(id / 7 % WORDS.size()) + " "
                    + KINDS.get(id % KINDS.size()));
        }).toList();
        send(exchange, 200, "application/json", Json.toJson(Map.of("items", items)));
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        Instant expiry = tokens.get(header.substring("Bearer ".length()));
        return expiry != null && expiry.isAfter(Instant.now());
    }

    private static Map<String, Object> tool(String name, String requiredArgument) {
        return Map.of("name", name, "inputSchema", Map.of("type", "object",
                "properties", Map.of(requiredArgument, Map.of("type", "string")), "required", List.of(requiredArgument)));
    }

    private static String result(Map<String, Object> request, Map<String, Object> result) {
        return Json.toJson(Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
    }

    private static String toolResult(Map<String, Object> request, boolean error, String text) {
        return result(request, Map.of("isError", error, "content", List.of(Map.of("type", "text", "text", text))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String body) {
        return body.isBlank() ? Map.of() : Json.fromJson(body, Map.class);
    }

    private static Map<String, String> form(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(encoded.split("&")).map(pair -> pair.split("=", 2)).filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8), (first, second) -> second));
    }

    private static String read(InputStream body) throws IOException {
        try (body) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void pause(int fromMs, int toMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(fromMs, toMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
