package com.frunoyman.perf.grpcdemo.feeders;

import com.frunoyman.perf.grpcdemo.mock.MockCompanySearchServer;
import io.gatling.javaapi.core.FeederBuilder;

import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.listFeeder;

/**
 * The values the gRPC actions read from the session: {@code #{companyName}} for Find, {@code #{companyId}} for GetCompany. Static
 * here (the mock's own companies), so a list feeder; a real service samples a live source in {@code before()} and feeds a
 * {@code Supplier} (new-perf-simulation §4).
 */
public final class CompanyFeeders {

    private static final List<Map<String, Object>> NAMES = MockCompanySearchServer.COMPANIES.stream()
            .map(company -> Map.<String, Object>of("companyName", company.getName())).toList();
    private static final List<Map<String, Object>> IDS = MockCompanySearchServer.COMPANIES.stream()
            .map(company -> Map.<String, Object>of("companyId", company.getId())).toList();

    private CompanyFeeders() {
    }

    public static FeederBuilder<Object> companyNames() {
        return listFeeder(NAMES).random();
    }

    public static FeederBuilder<Object> companyIds() {
        return listFeeder(IDS).random();
    }
}
