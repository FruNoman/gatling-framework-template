package com.frunoyman.perf.feeders;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.frunoyman.perf.clients.DataSourceClient;
import com.frunoyman.perf.config.Environment;
import com.frunoyman.perf.config.Tool;
import com.frunoyman.perf.dto.data.Company;
import com.frunoyman.perf.dto.tools.CompanyDetailsRequest;
import com.frunoyman.perf.dto.tools.SearchRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Real records for the tool calls — a random sample of the data source, a different one each run: a handful of fixed values measures
 * the service's cache, not the service. {@link #load()} reads once, in the simulation's {@code before()}; fewer than
 * {@link #MIN_RECORDS} fails the run before any load. Each feeder puts one random request DTO into the session per call.
 * <p>
 * Not Gatling's {@code listFeeder}: it needs its records when the scenario is built (the simulation's constructor, before
 * {@code before()}) — "Feeder must not be empty"; a {@code Supplier} is called when Gatling builds the run, after {@code before()}.
 * Data that is ready in the constructor (a static JSON file) → {@code listFeeder(...).random()} / {@code jsonFile(...)}.
 */
@Slf4j
public class CompanyFeeder {

    public static final String SEARCH_REQUEST = "searchRequest";
    public static final String COMPANY_DETAILS_REQUEST = "companyDetailsRequest";

    private static final int MIN_RECORDS = 20;

    private final List<Tool> tools;
    private final int size;
    /** Filled by {@link #load()}; read by the feeders' suppliers once the run is built. */
    private final List<Map<String, Object>> searchRequests = new ArrayList<>();
    private final List<Map<String, Object>> companyDetailsRequests = new ArrayList<>();

    public CompanyFeeder(List<Tool> tools, int size) {
        this.tools = tools;
        this.size = size;
    }

    public void load() {
        List<Company> companies = new DataSourceClient(Environment.dataSource()).randomCompanies(size).stream()
                .filter(company -> company.id() != null && company.name() != null && !company.name().isBlank())
                .distinct()
                .toList();
        if (tools.contains(Tool.SEARCH)) {
            fill(searchRequests, "search requests", SEARCH_REQUEST, companies,
                    company -> SearchRequest.builder().name(company.name()).limit(10).build());
        }
        if (tools.contains(Tool.COMPANY_DETAILS)) {
            fill(companyDetailsRequests, "company details requests", COMPANY_DETAILS_REQUEST, companies,
                    company -> CompanyDetailsRequest.builder().companyId(company.id()).sections(List.of("profile")).build());
        }
    }

    public Supplier<Iterator<Map<String, Object>>> searchRequests() {
        return () -> randomOf(searchRequests);
    }

    public Supplier<Iterator<Map<String, Object>>> companyDetailsRequests() {
        return () -> randomOf(companyDetailsRequests);
    }

    /** A random record each time; the list is read when Gatling builds the run — after {@code before()} filled it. */
    private static Iterator<Map<String, Object>> randomOf(List<Map<String, Object>> records) {
        if (records.isEmpty()) {
            throw new IllegalStateException("No records — CompanyFeeder.load() belongs in the simulation's before()");
        }
        return Stream.generate(() -> records.get(ThreadLocalRandom.current().nextInt(records.size()))).iterator();
    }

    private static void fill(List<Map<String, Object>> records, String name, String key, List<Company> companies,
                             Function<Company, Object> request) {
        if (companies.size() < MIN_RECORDS) {
            throw new IllegalStateException(companies.size() + " " + name + " from the data source, at least " + MIN_RECORDS
                    + " needed — fix the query, not the minimum");
        }
        companies.forEach(company -> records.add(Map.of(key, request.apply(company))));
        log.info("{} {} from the data source", records.size(), name);
    }
}
