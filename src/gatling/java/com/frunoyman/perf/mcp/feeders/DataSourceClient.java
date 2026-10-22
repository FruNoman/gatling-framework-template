package com.frunoyman.perf.mcp.feeders;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.frunoyman.perf.mcp.config.DataSourceSettings;
import com.frunoyman.perf.mcp.dto.Company;
import com.frunoyman.perf.mcp.dto.CompanyPage;
import com.frunoyman.perf.core.utils.Json;

/**
 * Reads the feeders' records from the data source, once, before the load — never from a virtual user. The JDK HTTP client directly.
 * ONBOARD: the real source — a search index ({@code POST /<index>/_search} with {@code function_score} + {@code random_score} for a
 * random sample), a database through its JDBC driver, or the service's own list endpoint.
 */
public class DataSourceClient {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final DataSourceSettings dataSource;

    public DataSourceClient(DataSourceSettings dataSource) {
        this.dataSource = dataSource;
    }

    /** A random sample of {@code size} companies — a different one each run. */
    public List<Company> randomCompanies(int size) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(dataSource.url() + "?random=true&size=" + size)).GET();
        if (dataSource.authenticated()) {
            String credentials = dataSource.username() + ":" + dataSource.password();
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Data source " + dataSource.url() + ": HTTP " + response.statusCode() + " "
                        + Json.abbreviate(response.body()));
            }
            return Json.fromJson(response.body(), CompanyPage.class).items();
        } catch (IOException e) {
            throw new IllegalStateException("Data source " + dataSource.url() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading the data source", e);
        }
    }
}
