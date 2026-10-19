package com.frunoyman.perf.config;

import java.util.Arrays;

/**
 * The MCP tools a run can call: the switch key ({@code -Dtools=search}), the tool's name on the server, the request name in the report
 * and the assertions — one source for the scenario and the verdict. ONBOARD: the service's tools.
 */
public enum Tool {

    /** Cheap, read-only — the default of every profile. */
    SEARCH("search", "search_companies"),
    /** Stands for a tool that costs something (credits, a quota, a downstream bill): only behind {@code -Dtools}, never a default. */
    COMPANY_DETAILS("company_details", "get_company");

    private final String key;
    private final String toolName;

    Tool(String key, String toolName) {
        this.key = key;
        this.toolName = toolName;
    }

    public String toolName() {
        return toolName;
    }

    public String requestName() {
        return "tools/call " + toolName;
    }

    public static Tool of(String key) {
        return Arrays.stream(values()).filter(tool -> tool.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool '" + key + "' — known: "
                        + Arrays.stream(values()).map(tool -> tool.key).toList()));
    }
}
