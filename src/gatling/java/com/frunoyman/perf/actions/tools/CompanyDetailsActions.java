package com.frunoyman.perf.actions.tools;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.frunoyman.perf.config.Tool;
import com.frunoyman.perf.dto.jsonrpc.JsonRpcRequest;
import com.frunoyman.perf.dto.tools.CompanyDetailsRequest;
import com.frunoyman.perf.feeders.CompanyFeeder;
import com.frunoyman.perf.mcp.McpChecks;
import com.frunoyman.perf.mcp.McpHeaders;
import com.frunoyman.perf.utils.Json;
import io.gatling.javaapi.core.ChainBuilder;

/**
 * The tool {@code get_company}: the {@link CompanyDetailsRequest} the feeder put into the session → {@code tools/call}.
 */
public class CompanyDetailsActions {

    private final String mcpPath;

    /** {@code mcpPath} — the service's MCP endpoint ({@code /mcp}). */
    public CompanyDetailsActions(String mcpPath) {
        this.mcpPath = mcpPath;
    }

    public ChainBuilder getCompany() {
        return exec(http(Tool.COMPANY_DETAILS.requestName())
                .post(mcpPath)
                .headers(McpHeaders.SESSION)
                .body(StringBody(session -> {
                    CompanyDetailsRequest companyDetailsRequest = session.get(CompanyFeeder.COMPANY_DETAILS_REQUEST);
                    return Json.toJson(JsonRpcRequest.toolCall(Tool.COMPANY_DETAILS.toolName(), companyDetailsRequest));
                }))
                .check(status().is(200), McpChecks.toolSucceeded()));
    }
}
