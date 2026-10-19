package com.frunoyman.perf.auth;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

/**
 * The run's current token, shared by all virtual users: {@link #start()} (the simulation's {@code before()}) fetches it, a
 * daemon thread renews it at three quarters of its life — a short-lived token (minutes) outlives no run. The renewal is no Gatling request:
 * it is not in the report and no virtual user waits for a login. A failed renewal is retried every 10 s while the old token works.
 */
@Slf4j
public class TokenStore {

    private static final long RETRY_MS = 10_000;
    private static final long NO_LIFETIME_MS = 600_000;

    private final String name;
    private final TokenProvider provider;
    private final AtomicReference<AccessToken> current = new AtomicReference<>();
    private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "token-renewal");
        thread.setDaemon(true);
        return thread;
    });

    public TokenStore(String name, TokenProvider provider) {
        this.name = name;
        this.provider = provider;
    }

    public void start() {
        AccessToken token = provider.fetch();
        current.set(token);
        log.info("[{}] token acquired: {}", name, token);
        scheduleRenewal(token);
    }

    public void stop() {
        renewals.shutdownNow();
    }

    public String token() {
        AccessToken token = current.get();
        if (token == null) {
            throw new IllegalStateException("[" + name + "] no token — TokenStore.start() belongs in the simulation's before()");
        }
        return token.value();
    }

    private void scheduleRenewal(AccessToken token) {
        long lifetimeMs = token.lifetime() == null ? NO_LIFETIME_MS : token.lifetime().toMillis();
        renewals.schedule(this::renew, Math.max(lifetimeMs * 3 / 4, RETRY_MS), TimeUnit.MILLISECONDS);
    }

    private void renew() {
        try {
            AccessToken token = provider.renew(current.get());
            current.set(token);
            log.info("[{}] token renewed: {}", name, token);
            scheduleRenewal(token);
        } catch (RuntimeException e) {
            log.error("[{}] token renewal failed, retry in {} ms: {}", name, RETRY_MS, e.getMessage());
            renewals.schedule(this::renew, RETRY_MS, TimeUnit.MILLISECONDS);
        }
    }
}
