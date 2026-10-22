package com.frunoyman.perf.grpcdemo.mock;

import com.frunoyman.perf.grpcdemo.config.GrpcDemoSettings;
import com.frunoyman.perf.grpcdemo.proto.Company;
import com.frunoyman.perf.grpcdemo.proto.CompanySearchGrpc;
import com.frunoyman.perf.grpcdemo.proto.FindRequest;
import com.frunoyman.perf.grpcdemo.proto.FindResponse;
import com.frunoyman.perf.grpcdemo.proto.GetCompanyRequest;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * The template's example gRPC service, started in the simulation's {@code before()} so a fresh clone runs with nothing installed:
 * {@code CompanySearch/Find} and {@code /GetCompany} over {@link #COMPANIES}, each answer after {@code latency-ms} (scheduled, never
 * a sleeping thread — the mock must not be the bottleneck), {@code failure-percent} of the calls UNAVAILABLE. ONBOARD: delete with
 * the {@code grpc-demo} config once the real service is wired.
 */
@Slf4j
public class MockCompanySearchServer {

    /** The companies the mock knows — the feeders sample the same list. */
    public static final List<Company> COMPANIES = IntStream.rangeClosed(1, 50)
            .mapToObj(index -> Company.newBuilder().setId(String.format("C-%03d", index)).setName("Company " + index).build())
            .toList();

    private final GrpcDemoSettings.Mock settings;
    private final ScheduledExecutorService answers = Executors.newScheduledThreadPool(2);
    private Server server;

    public MockCompanySearchServer(GrpcDemoSettings.Mock settings) {
        this.settings = settings;
    }

    public void start() {
        if (!settings.enabled()) {
            return;
        }
        try {
            server = ServerBuilder.forPort(settings.port()).addService(new Service()).build().start();
        } catch (IOException e) {
            throw new IllegalStateException("Mock gRPC server on port " + settings.port() + " did not start", e);
        }
        log.info("Mock gRPC server on port {} — latency {} ms, failures {} %", settings.port(), settings.latencyMs(),
                settings.failurePercent());
    }

    public void stop() {
        if (server != null) {
            server.shutdownNow();
        }
        answers.shutdownNow();
    }

    private final class Service extends CompanySearchGrpc.CompanySearchImplBase {

        @Override
        public void find(FindRequest request, StreamObserver<FindResponse> responses) {
            List<Company> found = COMPANIES.stream()
                    .filter(company -> company.getName().contains(request.getName()))
                    .limit(request.getLimit() > 0 ? request.getLimit() : 10)
                    .toList();
            answer(responses, FindResponse.newBuilder().addAllCompanies(found).build());
        }

        @Override
        public void getCompany(GetCompanyRequest request, StreamObserver<Company> responses) {
            COMPANIES.stream().filter(company -> company.getId().equals(request.getId())).findFirst().ifPresentOrElse(
                    company -> answer(responses, company),
                    () -> responses.onError(Status.NOT_FOUND.withDescription("no company " + request.getId()).asRuntimeException()));
        }

        private <T> void answer(StreamObserver<T> responses, T response) {
            answers.schedule(() -> {
                if (ThreadLocalRandom.current().nextInt(100) < settings.failurePercent()) {
                    responses.onError(Status.UNAVAILABLE.withDescription("mock failure").asRuntimeException());
                    return;
                }
                responses.onNext(response);
                responses.onCompleted();
            }, settings.latencyMs(), TimeUnit.MILLISECONDS);
        }
    }
}
