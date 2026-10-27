package com.frunoyman.perf.core.grpc;

import io.grpc.MethodDescriptor;

import java.util.function.Function;

/**
 * The connector's DSL — a feature's actions class extends this, and a gRPC call reads the way Gatling's own gRPC DSL does:
 *
 * <pre>{@code
 * public class CompanySearchActions extends GrpcDsl {
 *
 *     public CompanySearchActions(GrpcChannel channel) {
 *         super(channel);
 *     }
 *
 *     public ChainBuilder getCompany() {
 *         return exec(
 *                 grpc("CompanySearch/GetCompany")
 *                     .unary(CompanySearchGrpc.getGetCompanyMethod())
 *                     .send(session -> GetCompanyRequest.newBuilder().setId(session.getString("companyId")).build())
 *                     .check(response(Company::getId).isEL("#{companyId}"))
 *         );
 *     }
 * }
 * }</pre>
 *
 * No stub supplier, no method reference, no stub type in the feature package: protoc's generated
 * {@code static MethodDescriptor<Q, R> getXxxMethod()} already carries the marshallers, the full method name <b>and</b> the RPC's
 * {@link MethodDescriptor.MethodType}, and the generated stub's own method is nothing but
 * {@code ClientCalls.asyncUnaryCall(getChannel().newCall(getXxxMethod(), getCallOptions()), request, responseObserver)} — which is
 * what these actions do themselves, on the run's {@link GrpcChannel}. {@code Q} and {@code R} are inferred from the descriptor.
 * <p>
 * Unlike Gatling's, the channel is not a protocol in {@code setUp}: it is handed to the actions class, and a stream is one action
 * timed as a whole, not a send and an await. The shape the method reference used to guarantee at compile time is checked at
 * simulation start instead ({@link GrpcShape}).
 */
public abstract class GrpcDsl {

    private final GrpcChannel channel;

    protected GrpcDsl(GrpcChannel channel) {
        this.channel = channel;
    }

    /** A gRPC request named {@code name} in the report and the assertions; the RPC and its shape come next ({@link GrpcRequest}). */
    protected final GrpcRequest grpc(String name) {
        return new GrpcRequest(channel, name);
    }

    /** One value of a unary answer, to compare: {@code response(Company::getId).isEL("#{companyId}")}. */
    protected static <R, T> GrpcResponseCheck<R, T> response(Function<R, T> extract) {
        return new GrpcResponseCheck<>(extract);
    }

    /** A unary check on a stream's last message — a client stream's answer: {@code last(response(…).is(10))}. */
    protected static <R> GrpcStreamCheck<R> last(GrpcCheck<R> check) {
        return GrpcStreamCheck.last(check);
    }

        /** How many messages a stream delivered: {@code messageCount().is(10)}. */
    protected static GrpcMessageCount messageCount() {
        return GrpcMessageCount.INSTANCE;
    }
}
