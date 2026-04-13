# Milestone 6 Fixes

## Purpose

This document captures the issues found while running the app locally in a browser and the concrete fixes needed to make the Milestone 6 dashboard actually usable.

The browser pass confirmed that:

- the page shell renders correctly
- Datastar is loaded and reacts to preset clicks
- `POST /simulations` is fired on Start

But the app is not functionally complete in-browser because the start flow fails and the controls are not truly editable.


## Current Findings

### 1. Start Fails Due to Request Contract Mismatch

Observed behavior:

- Clicking `Start` sends `POST /simulations`
- The page remains in `idle`
- The errors panel fills with validation failures

Confirmed cause:

- Datastar sends a nested JSON payload shaped like:
  - `sim`
  - `config`
  - `stats`
  - `errors`
- The server only parses:
  - `application/x-www-form-urlencoded`
  - query parameters

Relevant code:

- [Routes.kt](/Users/delos/repos/test-rate-limiter/src/main/kotlin/com/example/web/Routes.kt:150)
- [Page.kt](/Users/delos/repos/test-rate-limiter/src/main/kotlin/com/example/web/Page.kt:117)

Effect:

- `receiveRawConfig()` returns a `RawSimulationConfig` full of nulls for Datastar JSON requests
- `Validator` correctly rejects the request
- the UI never enters the running state


### 2. Controls Are Display-Only, Not Editable Inputs

Observed behavior:

- Preset buttons update signal-backed values
- The main config controls render as spans, not form inputs

Relevant code:

- [Page.kt](/Users/delos/repos/test-rate-limiter/src/main/kotlin/com/example/web/Page.kt:44)
- [Page.kt](/Users/delos/repos/test-rate-limiter/src/main/kotlin/com/example/web/Page.kt:79)

Effect:

- users cannot directly edit limiter type, permits, durations, or failure settings from the UI
- only presets can change config in the live browser
- the “controls panel” is not yet a real control surface


### 3. Live Update Wiring Exists, But the UI Cannot Exercise It Fully

Observed behavior:

- the controls panel has debounced update wiring
- the page has preset buttons and stream anchor support

Effect:

- the wiring for live updates is present
- but because the controls are not actual editable inputs and Start is broken, the intended live update flow is blocked


## Fix Goals

The fixes should make the current milestone actually usable without redesigning the architecture.

Success means:

- Start works from the browser
- Stop works from the browser
- A simulation can be started, observed, and stopped
- The main controls are truly editable
- PATCH updates work from control edits while running
- Existing lifecycle, engine, and SSE ownership boundaries stay intact


## Fix Scope

In scope:

- request parsing for Datastar JSON submissions
- true editable control elements in the page
- keeping Datastar signal bindings correct for those controls
- route tests for Datastar-style JSON payloads
- browser-usable start and update flows

Out of scope:

- new architecture
- persistence
- major visual redesign
- chart redesign


## Fix Plan

### Fix 1: Support Datastar JSON Request Parsing

Update `receiveRawConfig()` so it can parse the actual request payload emitted by Datastar.

Required behavior:

- support `application/json`
- extract values from the nested `config` object
- preserve existing support for form and query parsing if still useful

Recommended implementation:

- define a small request DTO representing the Datastar payload shape
- parse the request body as JSON when `Content-Type` is JSON
- map `payload.config.*` into `RawSimulationConfig`

Avoid:

- parsing raw JSON strings manually
- duplicating validation logic

Done when:

- `POST /simulations` with the current Datastar payload successfully reaches `Validator` with populated fields
- `PATCH /simulations/:id` works through the same parsing path


### Fix 2: Convert Display-Only Fields into Real Inputs

Replace the current `span`-based controls with true form controls:

- `select` for enum-like fields
- `input type=number` for numeric fields
- possibly `input type=text` only if needed

Fields that should become editable:

- `limiterType`
- `permits`
- `perSeconds`
- `warmupSeconds`
- `secondaryPermits`
- `secondaryPerSeconds`
- `requestsPerSecond`
- `overflowMode`
- `apiTarget`
- `serviceTimeMs`
- `jitterMs`
- `failureRate`
- `workerConcurrency`

Each control should:

- bind to the appropriate `config.*` signal
- preserve the existing field-error slot
- keep stable field IDs

Done when:

- the page functions as an actual config form
- users can edit values without relying on presets only


### Fix 3: Verify Datastar Binding Strategy for Inputs

Once real inputs are added, confirm the correct Datastar binding attributes for:

- two-way value binding
- select binding
- numeric input updates

The binding approach should ensure:

- idle state edits update local signals only
- running state edits trigger the existing debounced `PATCH`

Done when:

- editing a control visibly changes the bound state
- running simulations receive update requests from those edits


### Fix 4: Keep Presets Aligned with Real Inputs

After converting controls to real inputs:

- verify preset clicks still update the displayed controls
- verify preset clicks still trigger updates while running
- ensure no duplicate update requests are fired unintentionally

Done when:

- presets remain a convenience layer on top of the real form, not a separate state path


### Fix 5: Extend Route Tests for Datastar JSON Payloads

Add route coverage for the actual browser contract.

Required tests:

- `POST /simulations` with Datastar JSON payload succeeds
- `PATCH /simulations/:id` with Datastar JSON payload succeeds
- invalid Datastar JSON payload returns form errors
- existing route tests still pass

Done when:

- the server contract is tested against the actual browser payload shape


### Fix 6: Extend Page Tests for Real Controls

Update page tests to assert the controls are genuine form elements rather than static spans.

Suggested assertions:

- `select` exists for limiter type
- `select` exists for overflow mode
- `select` exists for API target
- numeric inputs exist for numeric config fields
- field-error slots still exist for every editable field

Done when:

- page tests reflect a real, editable control surface


### Fix 7: Re-Test the Browser Flows End-to-End

After the above fixes, run the local browser pass again and verify:

1. Open page
2. Click Start
3. Observe status changes to running
4. Observe Stop becomes enabled
5. Observe live stats and/or log updates
6. Change a config field while running
7. Confirm the simulation stays attached and updates apply
8. Click Stop
9. Observe return to idle

Done when:

- the app is actually operable from the browser, not just from tests


## Suggested File Changes

### Likely updates

- `src/main/kotlin/com/example/web/Routes.kt`
- `src/main/kotlin/com/example/web/Page.kt`
- `src/test/kotlin/com/example/web/RoutesTest.kt`
- `src/test/kotlin/com/example/web/PageTest.kt`
- possibly `src/main/kotlin/com/example/model/Requests.kt` if a JSON DTO is added

### Likely unchanged

- simulation engine
- subscriber model
- SSE route logic
- registry ownership


## Recommended Implementation Order

1. Add Datastar JSON request parsing in `Routes.kt`
2. Add route tests for JSON payloads
3. Convert controls from spans to real inputs in `Page.kt`
4. Update page tests for real controls
5. Verify presets still work
6. Re-run full test suite
7. Re-run browser flow against the live app


## Acceptance Criteria

These fixes are complete when:

- Start succeeds from the browser
- Stop succeeds from the browser
- browser edits can change config through real inputs
- PATCH updates work from running-state control changes
- the server accepts the actual Datastar JSON payload shape
- no lifecycle or SSE ownership boundaries were weakened to achieve the fix


## Non-Goals

Do not use this fix pass to:

- redesign the app
- remove the Datastar-first model
- collapse lifecycle, engine, and transport layers together
- rewrite chart behavior unless it blocks basic operability

