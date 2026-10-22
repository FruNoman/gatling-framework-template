package com.frunoyman.perf.grpcdemo.actions;

import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.grpcdemo.proto.CompanySearchGrpc;
import com.frunoyman.perf.grpcdemo.proto.CompanySearchGrpc.CompanySearchStub;
import com.frunoyman.perf.grpcdemo.proto.FindRequest;
import com.frunoyman.perf.grpcdemo.proto.GetCompanyRequest;
import io.gatling.javaapi.core.ChainBuilder;

import java.util.concurrent.TimeUnit;

import static com.frunoyman.perf.core.grpc.GrpcActionBuilder.grpc;
import static io.gatling.javaapi.core.CoreDsl.exec;

/**
 * The example service's RPCs — one method per RPC, named after it, the request built from the session, the answer checked. Reads
 * {@code #{companyName}} / {@code #{companyId}} (the feeders of {@code CompanyFeeders}).
 */
public class CompanySearchActions {

    private static final int FIND_LIMIT = 10;

    private final GrpcChannel channel;
    private final int deadlineMs;

    public CompanySearchActions(GrpcChannel channel, int deadlineMs) {
        this.channel = channel;
        this.deadlineMs = deadlineMs;
    }

    /** {@code CompanySearch/Find}. */
    public ChainBuilder find() {
        return exec(
                grpc("CompanySearch/Find", this::stub, CompanySearchStub::find)
                    .request(session -> FindRequest.newBuilder()
                            .setName(session.getString("companyName"))
                            .setLimit(FIND_LIMIT)
                            .build())
                    .check(response -> response.getCompaniesCount() > 0 ? null : "no company found")
        );
    }

    /** {@code CompanySearch/GetCompany}. */
    public ChainBuilder getCompany() {
        return exec(
                grpc("CompanySearch/GetCompany", this::stub, CompanySearchStub::getCompany)
                    .request(session -> GetCompanyRequest.newBuilder()
                            .setId(session.getString("companyId"))
                            .build())
                    .check(company -> company.getId().isEmpty() ? "no company in the answer" : null)
        );
    }

    /** A fresh async stub per call — its deadline counts from this call. */
    private CompanySearchStub stub() {
        return CompanySearchGrpc.newStub(channel.get()).withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
    }
}
