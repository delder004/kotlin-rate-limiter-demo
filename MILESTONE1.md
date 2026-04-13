# Milestone 1

## Status

Complete as of 2026-04-12.

### Completion Summary

Delivered:

- `src/main/kotlin/com/example/Application.kt`
- `src/main/kotlin/com/example/web/Routes.kt`
- `src/main/kotlin/com/example/web/Page.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`
- `build.gradle.kts` update to point `mainClass` at `ApplicationKt`

Confirmed outcomes:

- the app has a new thin Ktor entry point
- `GET /` returns the Datastar-ready page shell
- stable region IDs are present:
  - `page-root`
  - `controls-panel`
  - `status-panel`
  - `stats-panel`
  - `chart-panel`
  - `log-panel`
  - `errors-panel`
- initial `data-signals` cover `sim`, `config`, `stats`, and `errors`
- 10 new `com.example.web.*` tests pass
- the legacy `Server.kt` and `Dashboard.kt` remain untouched
- the full main build succeeds

## Objective

Build the first runnable slice of the new app:

- a clean Ktor entry point
- a static Datastar page shell
- a working `GET /`
- basic tests for page rendering

This milestone does not include simulation logic, validation, start/stop actions, or SSE streaming.


## Scope

In scope:

- app bootstrap
- route wiring
- page shell HTML
- Datastar script include
- placeholder UI regions
- initial placeholder signals or state bootstrap
- page and route tests

Out of scope:

- simulation engine
- form submission
- start/stop/update routes
- SSE
- chart behavior
- presets
- real validation


## Deliverables

- `src/main/kotlin/com/example/Application.kt`
- `src/main/kotlin/com/example/web/Routes.kt`
- `src/main/kotlin/com/example/web/Page.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`

Optional only if needed:

- `build.gradle.kts`


## First-Pass Task List

### 1. Create the New Application Entry Point

- Add `Application.kt` as the main web entry point.
- Start Ktor from this file instead of relying on the current mixed-purpose server file.
- Install only the minimum plugins needed for milestone 1.
- Configure routing from a dedicated `web/Routes.kt` module.

Done when:

- the app starts from the new entry point
- `GET /` is reachable


### 2. Define the Minimal Web Package

- Create `web/Routes.kt`.
- Create `web/Page.kt`.
- Keep route registration and page rendering separate.
- Do not add simulation concerns to the web package yet.

Done when:

- route definitions are isolated from HTML rendering
- the file structure matches the target architecture


### 3. Render the Static Page Shell

- Build a full page shell in `web/Page.kt`.
- Include the Datastar client script.
- Render the main regions:
  - controls
  - status
  - stats
  - chart placeholder
  - log placeholder
- Use stable IDs or selectors for each major region.
- Add placeholder values for all visible stats.
- Add a clear title and short subtitle so tests can anchor on stable content.

Done when:

- the page is visually complete as a shell
- no region depends on live data


### 4. Establish Placeholder State Shape

- Add a minimal initial state model for the page.
- Include placeholder values for:
  - simulation status
  - config fields
  - stats fields
  - error fields
- Represent this in the HTML in the simplest way that fits the Datastar-first design.
- Do not implement real signal mutation yet.

Done when:

- the page can render a coherent idle state
- the intended config and stats shape is visible in the shell


### 5. Add the `GET /` Route

- Register `GET /` in `web/Routes.kt`.
- Return the page shell from `web/Page.kt`.
- Keep the handler thin and free of business logic.

Done when:

- opening `/` returns HTML from the new page renderer


### 6. Add Route and Page Tests

- Create `PageTest.kt`.
- Create `RoutesTest.kt`.
- Test that the shell contains:
  - the page title
  - the controls section
  - the stats section
  - the chart placeholder
  - the log placeholder
- Test that `GET /` returns:
  - status `200`
  - HTML content

Done when:

- tests pass without depending on future milestone behavior


### 7. Confirm the App Is a Stable Base for Milestone 2

- Verify the shell is easy to extend with:
  - typed config inputs
  - Datastar bindings
  - start/stop controls
  - stream anchor insertion
- Keep placeholders simple enough that they can be replaced incrementally.

Done when:

- no milestone-2 work requires restructuring the milestone-1 shell


## Suggested HTML Regions

Use stable IDs or equivalent selectors for:

- `page-root`
- `controls-panel`
- `status-panel`
- `stats-panel`
- `chart-panel`
- `log-panel`
- `errors-panel`

These do not need live behavior yet. They exist to stabilize the page structure and test surface.


## Suggested Placeholder Fields

The initial shell should visibly represent these values, even if they are hardcoded for now:

- `sim.status = "idle"`
- `sim.running = false`
- `config.type`
- `config.permits`
- `config.perSeconds`
- `config.requestsPerSecond`
- `config.overflowMode`
- `stats.queued = 0`
- `stats.inFlight = 0`
- `stats.completed = 0`
- `stats.denied = 0`
- `stats.acceptRate = 0`
- `stats.rejectRate = 0`
- `stats.avgLatencyMs = 0`
- `stats.p50LatencyMs = 0`
- `stats.p95LatencyMs = 0`
- `errors.form = null`
- `errors.stream = null`


## Recommended Implementation Order

1. Add `Application.kt`
2. Add `web/Routes.kt`
3. Add `web/Page.kt`
4. Wire `GET /`
5. Add page tests
6. Add route tests
7. Start the app manually and verify the shell


## What Not To Do Yet

- do not add SSE
- do not add simulation threads or coroutines
- do not implement form actions
- do not add the old WebSocket transport to the new architecture
- do not overbuild the chart


## Acceptance Criteria

Milestone 1 is complete when:

- the app has a dedicated new entry point
- `GET /` returns a stable Datastar-ready page shell
- the page shell contains the full intended layout
- tests cover page rendering and route reachability
- no simulation behavior is required for the app to be useful as a starting point


## Handoff to Milestone 2

Milestone 2 should be able to start directly from this shell by adding:

- typed simulation config
- validation
- structured error rendering
- start and stop routes

If milestone 2 needs to redesign the shell, milestone 1 was too vague or too coupled.
