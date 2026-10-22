package com.frunoyman.perf.grpcdemo.scenarios;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.config.Weights;
import com.frunoyman.perf.grpcdemo.actions.CompanySearchActions;
import com.frunoyman.perf.grpcdemo.feeders.CompanyFeeders;
import io.gatling.javaapi.core.ChainBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.percent;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.repeat;

/** What a client of the example gRPC service does: smoke calls every RPC once; load makes {@code callsPerSession} calls by weight. */
public class CompanySearchScenarios {

    /** The RPCs' shares, in percent — a guess until a production source exists; {@code -Dweights=find:50,getCompany:50}. */
    public static final Map<String, Integer> WEIGHTS = weights();

    private final CompanySearchActions companies;

    public CompanySearchScenarios(CompanySearchActions companies) {
        this.companies = companies;
    }

    /** Every RPC once. */
    public ChainBuilder smoke(LoadProfile loadProfile) {
        return exec(
                feed(CompanyFeeders.companyNames()).exec(companies.find()).pause(loadProfile.pause()),
                feed(CompanyFeeders.companyIds()).exec(companies.getCompany()).pause(loadProfile.pause()));
    }

    /** {@code callsPerSession} RPCs, each picked by its weight. */
    public ChainBuilder load(LoadProfile loadProfile, Weights weights) {
        return repeat(loadProfile.callsPerSession()).on(
                randomSwitch().on(
                        percent(weights.of("find")).then(feed(CompanyFeeders.companyNames()).exec(companies.find())),
                        percent(weights.of("getCompany")).then(feed(CompanyFeeders.companyIds()).exec(companies.getCompany())))
                .pause(loadProfile.pause()));
    }

    private static Map<String, Integer> weights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("find", 70);
        weights.put("getCompany", 30);
        return weights;
    }
}
