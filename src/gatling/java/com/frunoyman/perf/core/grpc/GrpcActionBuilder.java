package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.stub.StreamObserver;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A unary gRPC call as a Gatling action — Gatling's own gRPC support is Gatling Enterprise; this is the free way, on the same
 * Gatling and in the same scenarios as the HTTP requests:
 *
 * <pre>{@code
 * exec(
 *         grpc("CompanySearch/Find", this::stub, CompanySearchStub::find)
 *             .request(session -> FindRequest.newBuilder().setName(session.getString("companyName")).build())
 *             .check(response -> response.getCompaniesCount() > 0 ? null : "no company found")
 * )
 * }</pre>
 *
 * The stub is the generated <b>async</b> one ({@code newStub}, never {@code newBlockingStub}: a blocking call holds one of Gatling's
 * few engine threads and the run applies a fraction of the planned load with a healthy p95). {@code stub} is called per request,
 * so a deadline set in it ({@code withDeadlineAfter}) is fresh each time. {@link #check}: null = OK, else the KO's reason.
 */
public final class GrpcActionBuilder<S, Q, R> implements ActionBuilder {

    /** An async stub's unary method: {@code CompanySearchStub::find}. */
    @FunctionalInterface
    public interface Call<S, Q, R> {
        void invoke(S stub, Q request, StreamObserver<R> responses);
    }

    private final String name;
    private final Supplier<S> stub;
    private final Call<S, Q, R> call;
    private Function<Session, Q> request;
    private Function<R, String> check = response -> null;

    private GrpcActionBuilder(String name, Supplier<S> stub, Call<S, Q, R> call) {
        this.name = name;
        this.stub = stub;
        this.call = call;
    }

    /** {@code name} — the request's name in the report and the assertions, the RPC's own ("CompanySearch/Find"). */
    public static <S, Q, R> GrpcActionBuilder<S, Q, R> grpc(String name, Supplier<S> stub, Call<S, Q, R> call) {
        return new GrpcActionBuilder<>(name, stub, call);
    }

    /** The request message, built from the session. */
    public GrpcActionBuilder<S, Q, R> request(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The answer's check: null when it is right, else the reason — the KO's message in the report. */
    public GrpcActionBuilder<S, Q, R> check(Function<R, String> reasonOrNull) {
        this.check = reasonOrNull;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no request(...)");
        }
        return (context, next) -> new GrpcCallAction<>(name, stub, call, request, check, context.coreComponents().statsEngine(),
                context.coreComponents().clock(), next);
    }
}
