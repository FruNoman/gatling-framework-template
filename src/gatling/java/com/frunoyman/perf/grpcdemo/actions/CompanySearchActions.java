package com.frunoyman.perf.grpcdemo.actions;

import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.core.grpc.GrpcDsl;
import com.frunoyman.perf.grpcdemo.proto.Company;
import com.frunoyman.perf.grpcdemo.proto.CompanySearchGrpc;
import com.frunoyman.perf.grpcdemo.proto.FindRequest;
import com.frunoyman.perf.grpcdemo.proto.FindResponse;
import com.frunoyman.perf.grpcdemo.proto.GetCompanyRequest;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;

/**
 * The example service's RPCs — one method per RPC, named after it, the request built from the session, the answer checked. Reads
 * {@code #{companyName}} / {@code #{companyId}} (the feeders of {@code CompanyFeeders}).
 * <p>
 * A call reads as Gatling's own gRPC DSL — {@code grpc(name).unary(descriptor).send(...).check(...)} ({@link GrpcDsl}): no stub,
 * no method reference — only {@code CompanySearchGrpc.getXxxMethod()}, which already knows the marshallers, the method's full
 * name and its shape. The deadline comes from the channel's config, per call.
 */
public class CompanySearchActions extends GrpcDsl {

    private static final int FIND_LIMIT = 10;

    public CompanySearchActions(GrpcChannel channel) {
        super(channel);
    }

    /** {@code CompanySearch/Find}. */
    public ChainBuilder find() {
        return exec(
                grpc("CompanySearch/Find")
                    .unary(CompanySearchGrpc.getFindMethod())
                    .send(session -> FindRequest.newBuilder()
                            .setName(session.getString("companyName"))
                            .setLimit(FIND_LIMIT)
                            .build())
                    .check(response(FindResponse::getCompaniesCount).gt(0))
        );
    }

    /** {@code CompanySearch/GetCompany}. */
    public ChainBuilder getCompany() {
        return exec(
                grpc("CompanySearch/GetCompany")
                    .unary(CompanySearchGrpc.getGetCompanyMethod())
                    .send(session -> GetCompanyRequest.newBuilder()
                            .setId(session.getString("companyId"))
                            .build())
                    .check(response(Company::getId).isEL("#{companyId}"))
        );
    }
}
