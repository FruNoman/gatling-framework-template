package com.frunoyman.perf.core.grpc;

import com.frunoyman.perf.core.stats.RunStats;
import com.typesafe.scalalogging.Logger;
import io.gatling.commons.stats.KO$;
import io.gatling.commons.stats.OK$;
import io.gatling.commons.stats.Status;
import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;
import io.grpc.stub.StreamObserver;
import scala.Option;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Sends one unary call and records it as Gatling records an HTTP request — Gatling's report, assertions and error table see it under
 * its name — and in the run's own counting ({@link RunStats}, the live metrics). The answer comes on a gRPC thread; the session goes
 * on on its own event loop, as after an HTTP response.
 * <p>
 * This is Gatling's internal Scala API ({@code Action}, {@code StatsEngine.logResponse}, {@code !}), not the documented Java DSL:
 * pin the Gatling version and run one smoke after every upgrade — the signatures held from 3.13.5 to 3.16.0.
 */
final class GrpcCallAction<S, Q, R> implements Action {

    private final String name;
    private final Supplier<S> stub;
    private final GrpcActionBuilder.Call<S, Q, R> call;
    private final Function<Session, Q> request;
    private final Function<R, String> check;
    private final StatsEngine stats;
    private final Clock clock;
    private final Action next;
    private final Logger logger = Logger.apply(GrpcCallAction.class);

    GrpcCallAction(String name, Supplier<S> stub, GrpcActionBuilder.Call<S, Q, R> call, Function<Session, Q> request,
                   Function<R, String> check, StatsEngine stats, Clock clock, Action next) {
        this.name = name;
        this.stub = stub;
        this.call = call;
        this.request = request;
        this.check = check;
        this.stats = stats;
        this.clock = clock;
        this.next = next;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void execute(io.gatling.core.session.Session session) {
        Q message;
        try {
            message = request.apply(new Session(session));
        } catch (RuntimeException e) {
            stats.logRequestCrash(session.scenario(), session.groups(), name, "request: " + e.getMessage());
            next.$bang(session.markAsFailed());
            return;
        }
        RunStats.sent(name);
        long start = clock.nowMillis();
        StreamObserver<R> responses = new StreamObserver<>() {

            private R response;

            @Override
            public void onNext(R value) {
                response = value;
            }

            @Override
            public void onError(Throwable error) {
                io.grpc.Status status = io.grpc.Status.fromThrowable(error);
                done(KO$.MODULE$, status.getCode() + (status.getDescription() == null ? "" : ": " + status.getDescription()));
            }

            @Override
            public void onCompleted() {
                String reason;
                try {
                    reason = check.apply(response);
                } catch (RuntimeException e) {
                    reason = "check: " + e.getMessage();
                }
                done(reason == null ? OK$.MODULE$ : KO$.MODULE$, reason);
            }

            private void done(Status status, String reason) {
                long end = clock.nowMillis();
                stats.logResponse(session.scenario(), session.groups(), name, start, end, status, Option.empty(), Option.apply(reason));
                boolean ok = status == OK$.MODULE$;
                RunStats.answered(name, ok, (int) (end - start));
                io.gatling.core.session.Session after = ok ? session : session.markAsFailed();
                session.eventLoop().execute(() -> next.$bang(after));
            }
        };
        try {
            call.invoke(stub.get(), message, responses);
        } catch (RuntimeException e) {
            responses.onError(e);
        }
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /** The setter of Scala's {@code StrictLogging} field — required by the trait, unused. */
    @SuppressWarnings({"checkstyle:MethodName", "unused"})
    public void com$typesafe$scalalogging$StrictLogging$_setter_$logger_$eq(Logger ignored) {
        // the logger is the final field above
    }
}
