# Milestone 5

## Objective

Expose live simulation state to the browser over SSE:

- add the simulation stream route
- attach subscribers to running handles
- emit Datastar-friendly signal and fragment patches
- keep stream delivery resilient under disconnect and overload

This milestone makes the dashboard live, but it does not yet add config updates for running simulations or chart refinement.


## Scope

In scope:

- `GET /simulations/:id/stream`
- subscriber attachment to running handles
- SSE heartbeats
- Datastar signal patches for current metrics and simulation state
- Datastar fragment patches for recent logs and warnings
- explicit outbound-drop accounting
- SSE route tests
- page wiring for the stream anchor

Out of scope:

- `PATCH /simulations/:id`
- live config editing during a run
- chart integration beyond a placeholder or simple mount
- major UI polish


## Deliverables

Likely new files:

- `src/test/kotlin/com/example/web/SseStreamTest.kt`

Likely updates:

- `src/main/kotlin/com/example/web/Routes.kt`
- `src/main/kotlin/com/example/web/Fragments.kt`
- `src/main/kotlin/com/example/web/DatastarResponses.kt`
- `src/main/kotlin/com/example/web/Page.kt`
- `src/main/kotlin/com/example/simulation/SimulationHandle.kt`
- `src/main/kotlin/com/example/simulation/Events.kt`
- `src/main/kotlin/com/example/simulation/Metrics.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`

Optional if needed:

- `src/main/kotlin/com/example/simulation/Subscriber.kt`


## Milestone Outcome

At the end of milestone 5:

- a running simulation can be observed from the browser over SSE
- the page shows live stats and recent log updates
- disconnecting the browser does not destroy simulation ownership
- outbound stream loss is visible in counters rather than silently hidden


## Design Constraints

- simulation ownership stays with `SimulationHandle`
- subscriber attachment must not move ownership into the SSE route
- slow subscribers must not block the engine
- stream framing belongs in the web layer, not the simulation engine


## Streaming Model

### Subscriber Ownership

Each running `SimulationHandle` should support one or more subscribers.

A subscriber should receive:

- current simulation status
- current metrics snapshot
- recent logs or new logs
- warnings or terminal state

The handle should own the subscriber registry.

### Connection Semantics

- opening `GET /simulations/:id/stream` attaches a subscriber
- disconnecting removes the subscriber
- disconnecting does not stop the simulation
- reconnecting to the same simulation ID is allowed

### Delivery Model

Recommended first version:

- patch current metrics on a fixed interval or on every new snapshot
- patch recent logs incrementally
- send heartbeats on a predictable interval


## First-Pass Task List

### 1. Add Subscriber Support to `SimulationHandle`

- Extend `SimulationHandle.kt` to support subscriber attachment.
- Give each subscriber a bounded outbound buffer or channel.
- Support:
  - attach
  - detach
  - publish metric changes
  - publish log updates
  - publish state changes
- Keep subscriber state hidden behind the handle.

Done when:

- the handle can fan out runtime updates without exposing transport concerns to routes


### 2. Decide the Subscriber Payload Shape

- Decide whether subscribers receive:
  - typed domain events
  - prebuilt Datastar patch instructions

Recommended approach:

- subscribers receive typed domain events or snapshots
- the web layer translates them into SSE/Datastar payloads

Reason:

- keeps Datastar and SSE out of the simulation package

Done when:

- stream formatting remains a web concern


### 3. Define Outbound Backpressure Policy

- Make a deliberate choice for slow subscribers.
- Recommended first policy:
  - bounded per-subscriber queue
  - drop newest or oldest consistently
  - increment `droppedOutgoing`
- Ensure the engine never blocks waiting for a subscriber.

Done when:

- outbound overload is explicit and measurable


### 4. Add Datastar Stream Patch Helpers

- Extend `web/DatastarResponses.kt`.
- Add helpers to produce SSE-friendly Datastar events for:
  - sim lifecycle state
  - metrics signal patches
  - log row fragment merge
  - warning fragment merge
  - stream-anchor removal if the simulation ends

Keep patch construction centralized so the SSE route stays thin.

Done when:

- the web layer has reusable patch builders for live updates


### 5. Add Live Fragments

- Extend `web/Fragments.kt`.
- Add reusable fragments for:
  - a log row
  - warning banner or warning row
  - terminal state banner if needed
- Keep the existing stable IDs intact.

Done when:

- the route can stream log-related DOM updates without generating ad hoc HTML inline


### 6. Wire the Stream Anchor in the Page

- Update `web/Page.kt`.
- Ensure the page has a stable stream-anchor slot.
- Ensure the running state can mount a dedicated element that opens the SSE stream.
- Do not reuse the same element for button actions and stream fetches.

Recommended rule:

- start/stop actions operate on lifecycle controls
- stream attachment operates through the dedicated stream-anchor region

Done when:

- the UI can subscribe to a running simulation without interfering with action requests


### 7. Add the SSE Route

- Add `GET /simulations/:id/stream` to `web/Routes.kt`.
- Behavior:
  - return `404` for unknown ID
  - attach a subscriber for valid ID
  - send initial state immediately
  - stream subsequent patches
  - emit heartbeats
  - detach on disconnect
- Do not stop the simulation when the route closes.

Done when:

- a browser can open a stable live stream for an existing simulation


### 8. Decide Initial Stream Payloads

The first message set should include:

- current `sim.id`
- current `sim.status`
- current `sim.running`
- current metrics snapshot
- initial recent logs if desired

This ensures a reconnecting browser can recover the current state without waiting for the next update interval.

Done when:

- a freshly attached subscriber sees a coherent current state immediately


### 9. Add SSE Tests

- Create `SseStreamTest.kt`.
- Cover:
  - stream attaches to a running simulation
  - unknown simulation ID returns `404`
  - initial stream payload contains current state
  - metrics patches are emitted
  - log fragment patches are emitted
  - disconnect detaches subscriber without stopping simulation

Done when:

- the live route contract is pinned down before config updates are added


### 10. Extend Route and Page Tests

- Update `RoutesTest.kt` to cover stream route basics if not all live-stream assertions live in `SseStreamTest.kt`.
- Update `PageTest.kt` to assert:
  - stream-anchor slot still exists
  - any required running-state hooks are present

Done when:

- the shell and live route remain aligned


## Suggested Stream Event Mapping

### Signal Patches

Use signal patches for:

- `sim.id`
- `sim.running`
- `sim.status`
- `stats.queued`
- `stats.inFlight`
- `stats.admitted`
- `stats.completed`
- `stats.denied`
- `stats.droppedIncoming`
- `stats.droppedOutgoing`
- `stats.acceptRate`
- `stats.rejectRate`
- `stats.avgLatencyMs`
- `stats.p50LatencyMs`
- `stats.p95LatencyMs`

### Fragment Patches

Use fragment patches for:

- log rows
- warnings
- status banner if that remains easier to patch as HTML than signals
- stream-anchor removal on terminal state if needed


## Heartbeat Policy

Choose a heartbeat interval explicitly.

Reasonable first version:

- every 10s to 20s

The heartbeat should:

- keep intermediaries from closing idle streams
- not interfere with normal patch traffic


## Test Checklist

Minimum green test set for this milestone:

- `SseStreamTest`
  - attach
  - `404` for missing simulation
  - initial state
  - metrics patches
  - log patches
  - disconnect handling
- `RoutesTest`
  - existing start/stop routes still pass
- `PageTest`
  - stream-anchor slot remains stable
- `SimulationEngineTest`
  - still green with subscriber fan-out introduced


## Recommended Implementation Order

1. Add subscriber support to `SimulationHandle`
2. Decide and implement outbound backpressure policy
3. Extend `DatastarResponses.kt`
4. Extend `Fragments.kt`
5. Update `Page.kt` stream-anchor usage
6. Add `GET /simulations/:id/stream`
7. Add `SseStreamTest.kt`
8. Re-run route, page, and engine tests
9. Run the full test suite


## What Not To Do Yet

- do not add `PATCH /simulations/:id`
- do not add live config changes
- do not overbuild the chart
- do not move SSE formatting into the simulation package
- do not tie simulation shutdown to browser disconnect


## Acceptance Criteria

Milestone 5 is complete when:

- `GET /simulations/:id/stream` streams live state for a running handle
- the page can receive live metrics and log updates
- disconnecting the stream does not stop the simulation
- slow subscribers do not block engine progress
- dropped outbound updates are counted explicitly
- milestone 6 can focus on UI refinement and live config updates rather than transport basics


## Handoff to Milestone 6

Milestone 6 should be able to build directly on this by adding:

- `PATCH /simulations/:id`
- debounced live config changes
- richer Datastar actions and presets
- chart wiring or chart polish
- UX polish for errors and terminal states

If milestone 6 needs to redesign the stream route or subscriber ownership, milestone 5 was incomplete.

