package com.frunoyman.perf.mcp.feeders;

import com.frunoyman.perf.mcp.config.DataSourceSettings;
import com.frunoyman.perf.mcp.dto.Company;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Real records for the tool calls — a random sample of the data source, a different one each run: a handful of fixed values measures
 * the service's cache, not the service. {@link #load()} reads once, in the simulation's {@code before()}; fewer than
 * {@link #MIN_RECORDS} fails the run before any load. The feeders put plain values into the session ({@code #{companyName}},
 * {@code #{companyId}}); the action builds its request DTO from them, so the action shows where each argument comes from.
 * <p>
 * Not Gatling's {@code listFeeder}: it needs its records when the scenario is built (the simulation's constructor, before
 * {@code before()}) — "Feeder must not be empty"; a {@code Supplier} is called when Gatling builds the run, after {@code before()}.
 * Data that is ready in the constructor (a static JSON file) → {@code listFeeder(...).random()} / {@code jsonFile(...)}.
 */
@Slf4j
public class CompanyFeeder {

    private static final int MIN_RECORDS = 20;

    private final int size;
    /** Filled by {@link #load()}; read by the feeders' suppliers once the run is built. */
    private final List<Company> companies = new ArrayList<>();

    public CompanyFeeder(int size) {
        this.size = size;
    }

    public void load() {
        new DataSourceClient(DataSourceSettings.load()).randomCompanies(size).stream()
                .filter(company -> company.id() != null && company.name() != null && !company.name().isBlank())
                .distinct()
                .forEach(companies::add);
        if (companies.size() < MIN_RECORDS) {
            throw new IllegalStateException(companies.size() + " companies from the data source, at least " + MIN_RECORDS
                    + " needed — fix the query, not the minimum");
        }
        log.info("{} companies from the data source", companies.size());
    }

    /** {@code #{companyName}} — a search term: a real company's name. */
    public Supplier<Iterator<Map<String, Object>>> companyNames() {
        return () -> random(company -> Map.of("companyName", company.name()));
    }

    /** {@code #{companyId}}. */
    public Supplier<Iterator<Map<String, Object>>> companyIds() {
        return () -> random(company -> Map.of("companyId", company.id()));
    }

    /** A random record each time; the list is read when Gatling builds the run — after {@code before()} filled it. */
    private Iterator<Map<String, Object>> random(Function<Company, Map<String, Object>> record) {
        if (companies.isEmpty()) {
            throw new IllegalStateException("No records — CompanyFeeder.load() belongs in the simulation's before()");
        }
        return Stream.generate(() -> record.apply(companies.get(ThreadLocalRandom.current().nextInt(companies.size())))).iterator();
    }
}
