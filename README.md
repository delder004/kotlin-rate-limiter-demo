# kotlin-rate-limiter-demo

A server-driven web dashboard for exploring rate-limiter behavior under load. Built with Kotlin, Ktor, and [Datastar](https://data-star.dev/) — the browser is a reactive document, not a client app.

Backed by [`io.github.delder004:kotlin-rate-limiter`](https://central.sonatype.com/artifact/io.github.delder004/kotlin-rate-limiter).

## What it does

Run a simulated traffic workload against a configurable rate limiter and watch the live results:

- Tune request rate, burst size, and limiter parameters from the browser.
- Start, update, and stop simulations from server-rendered controls.
- Stream live metrics and logs over SSE — no custom client state machine.
- See overload honestly: dropped inbound work and backpressured events show up in metrics.

## Stack

- **Kotlin** 2.1, JVM toolchain 21
- **Ktor** 3.1 (Netty, HTML DSL, SSE)
- **Datastar** for UI reactivity
- **Gradle** with the wrapper

## Run it

```sh
./gradlew run
```

Then open <http://localhost:8080>.

## Build & test

```sh
./gradlew build       # compile + test + ktlint
./gradlew test        # tests only
./gradlew ktlintCheck # lint only
```

## Project layout

```
src/main/kotlin/com/example/
  Application.kt      # Ktor entrypoint
  simulation/         # transport-agnostic simulation engine
  web/                # routes, page rendering, SSE streams
docs/
  DESIGN.md           # architecture and design rationale
  MILESTONE*.md       # incremental build history
```

## License

[MIT](LICENSE)
