# Milestone 2

## Objective

Build the typed config and validation layer for the new app:

- define a real simulation config model
- validate all user-controlled fields on the server
- add structured error plumbing for Datastar responses
- prepare the page shell for milestone 3 lifecycle routes

This milestone does not include the simulation engine, SSE, or a real running simulation.


## Scope

In scope:

- typed simulation config
- typed enums or equivalent for constrained values
- validation rules
- structured validation results
- Datastar response helpers for signal and form error patches
- error rendering hooks in the existing shell
- tests for valid and invalid config cases

Out of scope:

- start and stop lifecycle
- simulation registry
- simulation engine
- SSE stream
- chart behavior
- request processing that mutates running simulation state


## Deliverables

- `src/main/kotlin/com/example/simulation/SimulationConfig.kt`
- `src/main/kotlin/com/example/simulation/Validation.kt`
- `src/main/kotlin/com/example/web/DatastarResponses.kt`
- `src/main/kotlin/com/example/web/Fragments.kt`
- `src/test/kotlin/com/example/simulation/ValidationTest.kt`

Likely updates:

- `src/main/kotlin/com/example/web/Page.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`

Optional if needed:

- `src/main/kotlin/com/example/model/Requests.kt`
- `src/main/kotlin/com/example/model/Responses.kt`


## Milestone Outcome

At the end of milestone 2:

- the server has a typed representation of the dashboard config
- invalid input is rejected before any simulation work is created
- form errors can be rendered predictably in the page
- milestone 3 can add start/stop lifecycle without redesigning parsing or validation


## First-Pass Task List

### 1. Define the Typed Config Model

- Create `simulation/SimulationConfig.kt`.
- Define the full config shape needed by the dashboard:
  - limiter type
  - permits
  - primary period
  - warmup period
  - secondary permits
  - secondary period
  - requests per second
  - overflow mode
  - API target
  - service time
  - jitter
  - failure rate
  - worker concurrency
- Use enums or similarly constrained types for:
  - limiter type
  - overflow mode
  - API target

Done when:

- runtime behavior no longer depends on ad hoc string values
- the config model is complete enough for future start/update routes


### 2. Define the Validation Result Shape

- Create a structured validation result model.
- Support:
  - valid config
  - field-level errors
  - optional global errors
- Keep the error shape easy to map into Datastar signals and fragments.

Suggested error categories:

- invalid number
- missing required field
- out-of-range value
- invalid enum value
- invalid composite config

Done when:

- validation failures can be returned without exceptions
- errors have stable field keys suitable for UI rendering


### 3. Implement Validation Rules

- Create `simulation/Validation.kt`.
- Validate at least:
  - `permits > 0`
  - primary period > 0
  - warmup >= 0
  - request rate >= 0
  - service time >= 0
  - jitter >= 0
  - failure rate within `[0, 1]`
  - worker concurrency > 0
  - valid limiter type
  - valid overflow mode
  - valid API target
- For composite configs:
  - require secondary permits
  - require secondary period
  - require both to be positive
- Define whether zero request rate is allowed for an idle-but-valid config.

Recommended rule:

- allow `requestsPerSecond = 0` so the shell can represent a paused or not-yet-started configuration cleanly

Done when:

- validation fully covers all user-editable fields
- composite mode is not allowed to drift into half-configured states


### 4. Decide the Request Parsing Boundary

- Decide how raw form or Datastar values become typed config.
- Keep raw parsing separate from semantic validation.
- If needed, add DTOs in `model/`.

The desired boundary is:

- raw request values in
- typed config or structured parse errors out

Done when:

- future routes do not need to parse strings inline
- type conversion and validation are testable in isolation


### 5. Add Datastar Response Helpers

- Create `web/DatastarResponses.kt`.
- Add helpers for:
  - patching signals
  - patching the errors panel or field fragments
  - clearing form errors
- Keep the helper API narrow and transport-specific.

This file should understand Datastar response formatting.
The simulation package should not.

Done when:

- milestone 3 routes can return Datastar-friendly responses without hand-building payloads each time


### 6. Add Error Rendering Hooks to the Page

- Create `web/Fragments.kt` if needed for reusable error fragments.
- Update `web/Page.kt` so `errors-panel` is structured for future patches.
- Add stable selectors for:
  - global form error area
  - field error area or field-specific message targets
- Keep rendering minimal; this milestone only needs a predictable shape.

Done when:

- the shell can display field and global validation errors without layout redesign


### 7. Add Tests for the Validation Layer

- Create `ValidationTest.kt`.
- Cover valid cases:
  - bursty config
  - smooth config
  - composite config
- Cover invalid cases:
  - zero permits
  - negative or zero durations where disallowed
  - invalid enum values
  - missing composite secondary settings
  - out-of-range failure rate
  - invalid worker concurrency

Done when:

- the validation layer can evolve safely without route-level regressions


### 8. Extend Page-Level Tests for Error Plumbing

- Update `PageTest.kt`.
- Assert that the shell includes:
  - `errors-panel`
  - predictable error container selectors
  - any placeholder state needed for form errors
- Do not add tests that assume milestone 3 route behavior yet.

Done when:

- the page contract for error rendering is locked in before lifecycle work begins


## Suggested Deliverable Breakdown

### Config Types

Keep these explicit:

- `LimiterType`
- `OverflowMode`
- `ApiTarget`
- `SimulationConfig`

### Validation Output

A practical shape would include:

- parsed config when valid
- map of field errors
- optional list of global errors

### Error Targets in the UI

At minimum:

- `errors-panel`
- `errors-form`
- per-field hooks for invalid controls


## Test Checklist

Minimum green test set for this milestone:

- `PageTest`
  - shell still renders
  - error containers exist
- `RoutesTest`
  - `GET /` still returns the shell
- `ValidationTest`
  - valid bursty config
  - valid smooth config
  - valid composite config
  - invalid permits
  - invalid durations
  - invalid enum values
  - invalid composite settings
  - invalid failure rate
  - invalid worker count


## Recommended Implementation Order

1. Create `SimulationConfig.kt`
2. Define enums and constrained types
3. Create `Validation.kt`
4. Add validation result shape
5. Add `DatastarResponses.kt`
6. Add `Fragments.kt`
7. Update `Page.kt` error hooks
8. Add `ValidationTest.kt`
9. Update page tests
10. Run the full test suite


## What Not To Do Yet

- do not add the simulation registry
- do not add `POST /simulations` or `DELETE /simulations/:id`
- do not add SSE
- do not wire the chart to live data
- do not reintroduce WebSocket protocol types into the new architecture


## Acceptance Criteria

Milestone 2 is complete when:

- the dashboard config has a typed server-side model
- all editable fields have explicit validation rules
- invalid config produces structured errors instead of exceptions
- the page shell has stable error rendering hooks
- Datastar response helpers exist for future form and signal patches
- milestone 3 can implement lifecycle routes without redesigning parsing or validation


## Handoff to Milestone 3

Milestone 3 should be able to build directly on this by adding:

- `SimulationRegistry`
- `SimulationHandle`
- `POST /simulations`
- `DELETE /simulations/:id`
- idle/running UI transitions

If milestone 3 needs to invent new config parsing or error structures, milestone 2 was incomplete.

