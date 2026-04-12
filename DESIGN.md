# Design

## Overview

This project should be rebuilt as a Datastar-first, server-driven UI for exploring rate-limiter behavior.

The browser is not a client application. It is a reactive document:

- Ktor renders the initial page.
- Datastar binds form state to signals and issues backend actions.
- The server owns simulation state and validation.
- Server-Sent Events (SSE) stream metrics and log updates back into the page.

The design goal is to make the system easier to reason about than the current WebSocket demo:

- no custom client state machine
- no socket-owned control protocol
- no duplicated state on client and server
- explicit validation and explicit overload accounting


## Goals

- Build a server-driven dashboard with minimal custom browser JavaScript.
- Use Datastar as the primary UI interaction model.
- Use SSE for one-way streaming of live simulation updates.
- Keep simulation logic transport-agnostic.
- Model overload honestly, including dropped or backpressured events.
- Keep the implementation simple enough for a small demo repo.


## Non-Goals

- Multi-user persistence
- Durable storage
- Authentication
- Horizontal scaling
- Perfect charting without any client-side JS

This is an in-memory demo application with strong internal structure, not a production control plane.


## Principles

### Server Owns State

The server is the source of truth for:

- simulation lifecycle
- validated config
- latest metrics
- recent logs
- current error state

### Datastar Owns UI Reactivity

The browser should use Datastar for:

- binding controls to signals
- issuing start, update, and stop actions
- updating stats from signal patches
- patching log and status fragments from SSE

### Simulation Is Transport-Agnostic

The simulation engine should know nothing about:

- Datastar
- SSE
- Ktor routing
- HTML structure

It should emit typed domain events and snapshots.

### Overload Must Be Visible

If the simulation drops inbound work, drops outbound events, or slows due to bounded queues, that must be represented in metrics instead of being hidden.


## User Experience

The page has five regions:

- controls
- status
- stats
- chart
- log

The intended flow is:

1. User opens `/`.
2. Server returns fully rendered HTML with initial Datastar signals.
3. User edits settings in place.
4. User clicks Start.
5. Server creates a simulation and returns a `simulationId` plus UI state patches.
6. A dedicated Datastar stream anchor opens an SSE stream for that simulation.
7. Stats and logs update continuously until the user stops the run or disconnects.


## High-Level Architecture

There are three layers:

### 1. Web Layer

Responsibilities:

- page shell rendering
- fragment rendering
- HTTP endpoint handling
- SSE streaming
- translation between Datastar requests and domain calls

Suggested package:

- `com.example.web`

### 2. Simulation Layer

Responsibilities:

- config validation
- simulation lifecycle
- rate-limiter creation
- metrics calculation
- response sampling
- subscriber fan-out

Suggested package:

- `com.example.simulation`

### 3. Shared Domain Types

Responsibilities:

- request/response DTOs
- snapshots
- event types

Suggested package:

- `com.example.model`


## Proposed Package Layout

```text
src/main/kotlin/com/example/
  Application.kt
  web/
    Routes.kt
    Page.kt
    Fragments.kt
    DatastarResponses.kt
  simulation/
    SimulationRegistry.kt
    SimulationHandle.kt
    SimulationEngine.kt
    SimulationConfig.kt
    Validation.kt
    LimiterFactory.kt
    Events.kt
    Metrics.kt
  model/
    Requests.kt
    Responses.kt
    Snapshot.kt
```


## Route Design

### `GET /`

Returns the full page shell.

Includes:

- all form controls
- initial signals
- empty stats
- empty log
- idle status

This route should not start a simulation.

### `POST /simulations`

Creates a new simulation from the current Datastar signals or submitted body.

Responsibilities:

- validate config
- create a new simulation
- return signal patches:
  - `sim.id`
  - `sim.running = true`
  - `sim.status = "running"`
  - `errors.form = null`
- return an element patch that mounts the SSE stream anchor

### `PATCH /simulations/:id`

Updates a running simulation.

Responsibilities:

- validate incoming config
- apply config changes
- return updated signals and any validation errors

This route should be debounced by the UI for slider and numeric input changes.

### `DELETE /simulations/:id`

Stops a simulation.

Responsibilities:

- stop the simulation job
- detach subscribers
- return idle state patches

### `GET /simulations/:id/stream`

Provides the live SSE stream for a single simulation.

Responsibilities:

- attach a subscriber
- emit heartbeats
- emit Datastar signal patches for metrics and state
- emit Datastar element patches for logs and banners
- close cleanly if the simulation ends


## Why SSE Instead of WebSockets

This UI only needs one-way streaming from server to browser.

WebSockets add:

- a custom bidirectional protocol
- message parsing on both ends
- connection-owned lifecycle complexity

SSE is a better fit because:

- Datastar works naturally with HTML and SSE
- start/update/stop can be standard HTTP routes
- the browser does not need a custom transport client
- the server can stay closer to plain request/response design

Constraint:

- SSE is not compressed by default in Ktor, so event volume must be controlled.


## Datastar Model

### Core Signals

Suggested signal groups:

```text
sim.id
sim.running
sim.status

config.type
config.permits
config.perSeconds
config.warmupSeconds
config.secondaryPermits
config.secondaryPerSeconds
config.requestsPerSecond
config.overflowMode
config.apiTarget
config.serviceTimeMs
config.jitterMs
config.failureRate
config.workerConcurrency

stats.queued
stats.inFlight
stats.completed
stats.denied
stats.droppedIncoming
stats.droppedOutgoing
stats.acceptRate
stats.rejectRate
stats.avgLatencyMs
stats.p50LatencyMs
stats.p95LatencyMs

ui.starting
ui.saving

errors.form
errors.stream
```

### Control Pattern

Form inputs bind directly to signals.

Examples of intended behavior:

- Start button posts current config
- Stop button deletes the current simulation
- Preset buttons patch `config.*` signals
- While running, control changes issue debounced patch requests

### Stream Anchor Pattern

Use a dedicated hidden element whose only responsibility is opening the SSE stream.

Reason:

- Datastar can cancel existing fetches on the same element when a new one starts
- control requests and stream requests should not compete for the same DOM node

The anchor is mounted only when:

- `sim.id` exists
- `sim.running` is true

When the simulation stops, the server removes or disables the anchor.


## Simulation Model

### Simulation Registry

The registry owns all active simulations in memory.

Responsibilities:

- create a simulation
- look up by ID
- update config
- stop simulation
- clean up inactive handles

The registry decouples simulation lifetime from a single SSE connection.

### Simulation Handle

Each simulation owns:

- validated config
- supervisor job
- latest metrics snapshot
- bounded recent log buffer
- typed event stream for subscribers

The handle is the unit of lifecycle and streaming.

### Simulation Engine

The engine is a coroutine-driven worker that models:

- incoming request generation
- queueing or rejection behavior
- permit acquisition
- work execution
- latency measurement
- metric sampling

It emits typed events but does not know about transport or UI.


## Simulation Configuration

Suggested config fields:

- limiter type
- permits
- primary period
- warmup period
- optional secondary permits
- optional secondary period
- requests per second
- overflow mode
- API target
- service time
- jitter
- failure rate
- worker concurrency

### Validation Rules

Validation must happen before any simulation starts or updates.

Minimum rules:

- permits > 0
- periods > 0
- request rate >= 0
- worker concurrency > 0
- failure rate in `[0, 1]`
- jitter >= 0
- service time >= 0
- allowed limiter types only
- allowed overflow modes only
- allowed API targets only
- composite limiter requires valid secondary values

Validation errors should be returned as structured form errors, not as transport failures.


## Event Model

The engine should emit typed events such as:

- `SimulationStarted`
- `MetricSample`
- `ResponseSample`
- `SimulationWarning`
- `SimulationStopped`
- `SimulationFailed`

This event model is the seam between simulation and web layers.

The web layer decides whether an event becomes:

- a Datastar signal patch
- a Datastar element patch
- a structured HTTP error


## Metrics Model

The dashboard should expose at least:

- queued
- inFlight
- admitted
- completed
- denied
- droppedIncoming
- droppedOutgoing
- avgLatencyMs
- p50LatencyMs
- p95LatencyMs
- acceptRate
- rejectRate

### Important Rule

If outbound stream updates are dropped because a subscriber is slow or buffers are full, the system must count that explicitly instead of silently losing fidelity.

### Sampling Strategy

Recommended defaults:

- metric sample every 200ms to 500ms
- bounded log buffer
- log sampling under very high request rates


## UI Rendering Strategy

### Full Page Rendering

Use server-side HTML rendering for the main page shell.

### Fragment Rendering

Render reusable fragments for:

- status banner
- stat cards
- validation errors
- log rows
- stream anchor

### Chart Strategy

Do not force Datastar to own the chart initially.

Recommended first version:

- Datastar owns state and DOM updates
- a very small client-side chart component renders the time series

Alternative later:

- server-rendered SVG sparkline or chart fragment

The chart should be treated as an island, not the center of the architecture.


## Error Handling

There are three error classes:

### Validation Errors

Examples:

- invalid permit count
- missing composite config
- invalid target name

These should patch `errors.form`.

### Simulation Errors

Examples:

- upstream call failure
- internal simulation exception

These should patch `sim.status` and `errors.stream`, and may also emit warning log rows.

### Stream Errors

Examples:

- SSE subscriber disconnected
- heartbeat timeout

These should not kill the simulation by default.


## Concurrency and Lifecycle

### Simulation Ownership

The simulation is owned by the registry, not by the SSE connection.

### Subscriber Model

Each SSE connection becomes a subscriber to one simulation.

If a browser refreshes or reconnects:

- the simulation may continue
- the new stream attaches to the same `simulationId`

### Cleanup

The registry should remove stopped simulations after a short retention window or immediately after stop, depending on how much reconnect support is needed.

For a simple demo:

- stop immediately on `DELETE`
- clean up after the final state is streamed


## Testing Strategy

### Unit Tests

- config validation
- limiter factory behavior
- metric calculations
- queue vs reject semantics
- overload accounting

### Simulation Tests

- queue mode under overload
- reject mode under overload
- warmup behavior
- composite limiter behavior
- dropped inbound/outbound accounting

### Route Tests

- `GET /` returns the expected shell
- `POST /simulations` validates and starts
- `PATCH /simulations/:id` validates and updates
- `DELETE /simulations/:id` stops cleanly
- `GET /simulations/:id/stream` produces SSE frames

### UI Contract Tests

At minimum, verify that:

- expected Datastar attributes are present
- stream anchor appears only for active simulations
- form errors are rendered in predictable selectors


## What To Salvage From The Current Demo

Useful pieces:

- rate-limiter concepts
- limiter creation logic
- queue and reject simulation modes
- simulated upstream work model
- existing test ideas for burst, smooth, warmup, and composite behavior

Likely replace entirely:

- WebSocket control protocol
- `ControlMessage`
- inline dashboard JavaScript
- dashboard transport lifecycle
- connection-owned simulation model


## Initial Build Plan

### Milestone 1

- Build `GET /`
- Render a static Datastar page shell
- Define config and signal model

### Milestone 2

- Implement validation
- Implement in-memory registry
- Add start and stop routes

### Milestone 3

- Implement simulation engine
- Add SSE stream
- Patch stats and logs into the page

### Milestone 4

- Add presets
- Add debounced live updates
- Add chart island

### Milestone 5

- Tighten tests
- Clean up repo structure
- Document how to run the app


## Summary

The clean design is:

- Datastar for browser reactivity
- HTTP for control actions
- SSE for live updates
- an in-memory registry for simulation lifecycle
- a transport-agnostic simulation engine

This gives the project a much simpler mental model than the current WebSocket demo while preserving the interesting rate-limiter behavior.


## References

- Datastar: https://data-star.dev/
- Datastar backend requests: https://data-star.dev/guide/backend_requests
- Datastar attributes: https://data-star.dev/reference/attributes
- Datastar actions: https://data-star.dev/reference/actions
- Datastar SSE events: https://data-star.dev/reference/sse_events
- Ktor SSE docs: https://ktor.io/docs/server-server-sent-events.html
