package com.frunoyman.perf.grpcdemo.config;

import com.frunoyman.perf.core.config.Environment;
import com.typesafe.config.Config;

/**
 * The gRPC example's service ({@code grpc-demo} in application.conf): its target, plaintext or TLS, the deadline of every call, and
 * the local mock that answers it in the {@code local} environment.
 */
public record GrpcDemoSettings(String target, boolean plaintext, int deadlineMs, Mock mock) {

    /** The mock gRPC server ({@code mock/}): on locally, with a latency and a share of errors to see a red run. */
    public record Mock(boolean enabled, int port, int latencyMs, int failurePercent) {
    }

    public static GrpcDemoSettings load() {
        Config grpc = Environment.block("grpc-demo");
        Config mock = grpc.getConfig("mock");
        return new GrpcDemoSettings(Environment.required(grpc, "target"), grpc.getBoolean("plaintext"), grpc.getInt("deadline-ms"),
                new Mock(mock.getBoolean("enabled"), mock.getInt("port"), mock.getInt("latency-ms"), mock.getInt("failure-percent")));
    }
}
