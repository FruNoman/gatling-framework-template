# gatling-framework-template

Starting point for a load-test project: **native Gatling 3.16 (Java DSL)** on Gradle, Java 21 — one parameterized simulation per
service, five load profiles with a verdict each, typed DTOs, actions apart from scenarios, real data loaded before the run, the
token renewed beside the load, and a mock MCP service so it runs on a fresh clone with nothing installed. The Claude Code setup
that drives the work — the `qa-core` + `qa-java` plugins from
[qa-automation-toolkit](https://github.com/FruNoman/qa-automation-toolkit) — is enabled in `.claude/settings.json`; this repo keeps
only `framework-core` (the framework's rules).

## Run it

```bash
./gradlew gatlingRun --simulation com.frunoyman.perf.simulations.McpSimulation -Dprofile=smoke -Dusers=5 -DsteadyState=40
```

The simulation starts the mock service (`mock/`), takes a token, loads 200 random records for the feeders, logs the plan and runs.
Report: `build/reports/gatling/mcpsimulation-<timestamp>/index.html`; **the exit code is the verdict** (assertions).

## Profiles — one simulation, a question per run

| `-Dprofile` | Answers | Shape | Verdict |
|---|---|---|---|
| `smoke` | does the chain work under concurrency? (every deploy) | closed: `users` looping sessions | 0 failed + p95 per request |
| `load` | does the release hold the expected peak? (the regression gate) | open: ramp to `rate` sessions/s, plateau | failed % + p95 per request |
| `stress` | where does it break? | open: stairs `rate` + `step` × `steps` | none — the level it broke at |
| `spike` | does it survive a ×N burst and recover? | open: base → ×`spikeFactor` → base | failed % |
| `soak` | does it degrade over hours? (leaks, pools, renewals) | open: constant `rate` | failed % + p95 per request |

```bash
G="com.frunoyman.perf.simulations.McpSimulation"
./gradlew gatlingRun --simulation $G -Dprofile=load   -Drate=2 -DrampUp=10 -DsteadyState=60
./gradlew gatlingRun --simulation $G -Dprofile=stress -Drate=1 -Dstep=1 -Dsteps=4 -DsteadyState=40
./gradlew gatlingRun --simulation $G -Dprofile=spike  -Drate=1 -DspikeFactor=4 -Dspike=15 -DsteadyState=20
./gradlew gatlingRun --simulation $G -Dprofile=soak   -Drate=1 -DsteadyState=3600
#  -Denv=local|dev   -Dtools=search,company_details   -DcallsPerSession=5 -DpauseMs=1000
#  thresholds: -DmaxFailedPercent=1 -DmaxToolCallP95Ms=3000 -DmaxInitializeP95Ms=1500 -DmaxProtocolP95Ms=500
#  stop at a breaking point: -DabortFailedPercent=30
```

An open rate is **sessions per second**; one session = initialize, initialized, tools/list, `callsPerSession` tool calls, close —
so requests per second ≈ rate × (4 + `callsPerSession`). The plan in the log gives every level with its wall-clock time.

See it fail: `-Dlocal.mock-server.failure-percent=50` on `stress` — the FailureGuard stops the load
("Load stopped: 97 % of the last 100 sessions failed"); `=5` on `load` — the run ends red (exit 1), the error table says
`tool error: Simulated failure`.

## Layout

| Package | What |
|---|---|
| `config/` | `Environment` (`application.conf` → records), `Profile`, `LoadProfile` (`-D` parameters + thresholds), `Tool` |
| `protocols/` | the HTTP protocol: base URL, JSON / SSE, User-Agent, `Authorization` from the token store — once, for every request |
| `auth/` | `TokenProvider` (client credentials on the JDK `HttpClient`), `TokenStore` (renews at ¾ of the token's life, beside the run) |
| `clients/`, `feeders/` | the data source's client; `CompanyFeeder` — loaded in `before()`, Supplier feeders of request DTOs |
| `dto/` | JSON-RPC messages, one record per tool's arguments, the source's records — Lombok records, no JSON by hand |
| `actions/` | one class per resource, one method per request; the path in the constructor |
| `mcp/` | checks that name the reason (`tool error: …`), the session headers |
| `scenario/` | composition only: an MCP session in `exitBlockOnFail`, the FailureGuard |
| `simulations/` | `McpSimulation` (setUp, before / after, assertions per profile), `Injections` (each profile's injection + the plan) |
| `mock/` | the example service on the JDK `HttpServer` — tokens that expire, sessions, an SSE answer, tool errors, a data endpoint |

## Start a new project

1. Create a repo from this template, open it in Claude Code (accept the `qa-core` and `qa-java` plugins it offers).
2. Ask: *"onboard this project"* / *"set up load tests for <service>"* — `qa-core:engage` asks functional / performance, the
   language, and leads: the load model (`qa-core:design-load-model`), the service's scenario (`qa-java:new-perf-simulation`), the
   first green smoke on the real environment, then the reading of every run (`qa-core:analyze-perf-run`).
3. Every place the template expects a change is marked `ONBOARD:` — the environments in `application.conf`, the tools, the DTOs,
   the data source, the token provider; `mock/` goes once the real service answers.

A company scaffold that runs Gatling from its own app (a scheduled job with Slack / dashboards / AI analysis) keeps that shell and
takes this core inside it — `qa-java:adopt-perf-runner`.

## Configuration and secrets

`src/gatling/resources/application.conf` — a block per environment (`-Denv`, default `local`), secrets only as `${?ENV_NAME}`.
Locally `gatlingRun` passes `.env/local.env` (git-ignored; the names in `.env.example`) as environment variables, as a pod gets
its secrets. `./gradlew checkstyleGatling` — no star or unused imports, lines ≤ 150.
