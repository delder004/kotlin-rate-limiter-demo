package com.example.web

import kotlinx.html.*

private const val DATASTAR_CDN =
    "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-beta.11/bundles/datastar.js"

private const val CHARTJS_CDN =
    "https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js"

private val PAGE_CSS =
    """
    body {
      margin: 0;
      background: #f6f6f3;
      color: #1f2421;
      font-family: "Avenir Next", "Segoe UI", sans-serif;
    }

    #page-root {
      max-width: 880px;
      margin: 0 auto;
      padding: 32px 24px;
      display: grid;
      gap: 20px;
    }

    header h1,
    section h2 {
      margin: 0;
      font-weight: 600;
      letter-spacing: -0.02em;
    }

    header h1 {
      font-size: 1.6rem;
    }

    .subtitle {
      margin: 8px 0 0;
      color: #5f665f;
    }

    .wizard-step,
    #run-panels > section {
      background: #ffffff;
      border: 1px solid #dde2da;
      border-radius: 10px;
      padding: 18px 20px;
    }

    .wizard-step h2 {
      font-size: 1.05rem;
    }

    .limiter-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 14px;
    }

    .limiter-buttons button {
      flex: 1 1 140px;
      padding: 14px 18px;
      border: 1px solid #c6cdc3;
      border-radius: 10px;
      background: #ffffff;
      color: inherit;
      font: inherit;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.15s, border-color 0.15s;
    }

    .limiter-buttons button.active {
      background: #1f2421;
      border-color: #1f2421;
      color: #f6f6f3;
    }

    .slider-row {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-top: 14px;
    }

    .slider-row input[type="range"] {
      flex: 1;
      accent-color: #1f2421;
    }

    .wizard-step > input[type="range"],
    .child-slider > input[type="range"] {
      display: block;
      width: 100%;
      accent-color: #1f2421;
      margin-top: 10px;
    }

    .child-slider-meta {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 8px;
      justify-content: space-between;
      flex-wrap: wrap;
    }

    .slider-value {
      min-width: 64px;
      text-align: right;
      font-variant-numeric: tabular-nums;
      font-size: 1.1rem;
      font-weight: 600;
    }

    .duration-toggle {
      display: inline-flex;
      border: 1px solid #c6cdc3;
      border-radius: 999px;
      padding: 2px;
      background: #ffffff;
    }

    .duration-toggle button {
      border: none;
      background: transparent;
      color: inherit;
      font: inherit;
      font-size: 0.85rem;
      padding: 4px 10px;
      border-radius: 999px;
      cursor: pointer;
    }

    .duration-toggle button.active {
      background: #1f2421;
      color: #f6f6f3;
      font-weight: 600;
    }

    .warmup-row {
      margin-top: 18px;
    }

    .warmup-row label {
      display: block;
      font-size: 0.95rem;
      color: #5f665f;
      margin-bottom: 4px;
    }

    .warmup-row .slider-row {
      margin-top: 4px;
    }

    .composite-editor {
      margin-top: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .composite-row {
      padding: 12px 14px;
      border: 1px solid #dde2da;
      border-radius: 8px;
      background: #f8f9f5;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .composite-row-header {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .composite-row-label {
      font-weight: 600;
      font-size: 0.95rem;
      color: #1f2421;
      min-width: 80px;
    }

    .composite-type-toggle {
      display: inline-flex;
      border: 1px solid #c6cdc3;
      border-radius: 999px;
      padding: 2px;
      background: #ffffff;
    }

    .composite-type-toggle button {
      border: none;
      background: transparent;
      color: inherit;
      font: inherit;
      padding: 5px 14px;
      border-radius: 999px;
      cursor: pointer;
      font-size: 0.9rem;
    }

    .composite-type-toggle button.active {
      background: #1f2421;
      color: #f6f6f3;
      font-weight: 600;
    }

    .composite-remove {
      margin-left: auto;
      border: 1px solid #c6cdc3;
      border-radius: 999px;
      background: #ffffff;
      color: #6e4733;
      font: inherit;
      padding: 5px 14px;
      cursor: pointer;
      font-size: 0.88rem;
    }

    .composite-remove[disabled] {
      opacity: 0.45;
      cursor: default;
    }

    .composite-row-sliders {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .child-slider label {
      display: block;
      font-size: 0.88rem;
      color: #5f665f;
      margin-bottom: 2px;
    }

    .composite-add-row {
      display: flex;
      justify-content: flex-start;
    }

    .composite-add {
      border: 1px dashed #c6cdc3;
      background: transparent;
      color: #1f2421;
      font: inherit;
      padding: 10px 16px;
      border-radius: 999px;
      cursor: pointer;
    }

    .composite-add[disabled] {
      opacity: 0.45;
      cursor: default;
    }

    .overflow-row {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-top: 16px;
    }

    .overflow-label {
      font-size: 0.95rem;
      color: #5f665f;
    }

    .overflow-toggle {
      display: inline-flex;
      border: 1px solid #c6cdc3;
      border-radius: 999px;
      padding: 2px;
      background: #f4f6f1;
    }

    .overflow-toggle button {
      border: none;
      background: transparent;
      color: inherit;
      font: inherit;
      padding: 6px 16px;
      border-radius: 999px;
      cursor: pointer;
    }

    .overflow-toggle button.active {
      background: #1f2421;
      color: #f6f6f3;
      font-weight: 600;
    }

    .traffic-advanced {
      margin-top: 18px;
      border-top: 1px solid #e4e7e1;
      padding-top: 12px;
    }

    .traffic-advanced > summary {
      cursor: pointer;
      font-size: 0.9rem;
      color: #5f665f;
      list-style: none;
      user-select: none;
    }

    .traffic-advanced > summary::-webkit-details-marker {
      display: none;
    }

    .traffic-advanced > summary::before {
      content: "▸ ";
      display: inline-block;
      width: 1em;
    }

    .traffic-advanced[open] > summary::before {
      content: "▾ ";
    }

    .traffic-advanced-body {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-top: 10px;
    }

    .child-slider-label-row {
      display: flex;
      align-items: center;
      gap: 2px;
    }

    .child-slider-label-row label {
      margin-bottom: 0;
    }

    .info-tip {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 15px;
      height: 15px;
      margin-left: 6px;
      border-radius: 50%;
      border: 1px solid #c6cdc3;
      background: #ffffff;
      color: #5f665f;
      font-size: 0.68rem;
      font-weight: 700;
      line-height: 1;
      cursor: help;
    }

    .info-tip:hover,
    .info-tip:focus {
      background: #1f2421;
      color: #f6f6f3;
      outline: none;
    }

    .info-tip::after {
      content: attr(data-tip);
      position: absolute;
      left: 50%;
      bottom: calc(100% + 8px);
      transform: translateX(-50%);
      background: #1f2421;
      color: #f6f6f3;
      padding: 7px 10px;
      border-radius: 6px;
      font-size: 0.78rem;
      font-weight: 400;
      line-height: 1.4;
      width: max-content;
      max-width: 240px;
      white-space: normal;
      text-align: left;
      pointer-events: none;
      opacity: 0;
      visibility: hidden;
      transition: opacity 120ms ease;
      z-index: 10;
      box-shadow: 0 4px 12px rgba(31, 36, 33, 0.18);
    }

    .info-tip:hover::after,
    .info-tip:focus::after {
      opacity: 1;
      visibility: visible;
    }

    #step-start .start-row {
      margin-top: 14px;
      display: flex;
      gap: 10px;
    }

    #lifecycle-controls {
      display: flex;
      gap: 10px;
    }

    #lifecycle-controls button {
      font: inherit;
      cursor: pointer;
      border-radius: 999px;
    }

    #lifecycle-controls .start-button {
      border: 1px solid #1f2421;
      background: #1f2421;
      color: #f6f6f3;
      font-weight: 600;
      padding: 12px 32px;
      font-size: 1.05rem;
    }

    #lifecycle-controls .stop-button {
      border: 1px solid #c6cdc3;
      background: #ffffff;
      color: inherit;
      padding: 10px 18px;
    }

    #lifecycle-controls button[disabled] {
      opacity: 0.45;
      cursor: default;
    }

    #run-panels {
      display: grid;
      gap: 16px;
    }

    .running-bar {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      padding: 3px 10px;
      border-radius: 999px;
      background: #eef2eb;
      font-size: 0.92rem;
    }

    #stats-panel ul {
      list-style: none;
      padding: 0;
      margin: 14px 0 0;
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 10px;
    }

    #stats-panel li {
      padding: 10px 12px;
      border-radius: 6px;
      background: #f8f8f5;
      border: 1px solid #edf0ea;
    }

    .chart-mount {
      position: relative;
      margin-top: 12px;
      height: 260px;
      padding: 8px 10px;
      border: 1px solid #edf0ea;
      border-radius: 6px;
      background: #fbfbf9;
    }

    .chart-mount canvas {
      width: 100% !important;
      height: 100% !important;
      display: block;
    }

    .chart-effect {
      position: absolute;
      width: 0;
      height: 0;
      overflow: hidden;
      visibility: hidden;
    }

    .log-list,
    .status-log-list,
    .form-errors,
    .stream-errors {
      margin-top: 12px;
    }

    .log-list,
    .form-errors,
    .field-error {
      color: #6e4733;
    }

    .status-log-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      color: #1f2421;
      font-variant-numeric: tabular-nums;
    }

    .status-log-row {
      padding: 6px 10px;
      border-radius: 6px;
      background: #f4f6f1;
      border: 1px solid #e5ead8;
      font-size: 0.92rem;
    }

    .form-errors:empty,
    .status-log-list:empty,
    .stream-errors:empty,
    .field-error:empty {
      display: none;
    }

    .stream-errors,
    .field-error {
      color: #b42318;
    }

    [data-show-hidden] {
      display: none !important;
    }
    """.trimIndent()

private val initialSignals =
    """
    {
      "ui": { "step": 1 },
      "sim": { "id": null, "status": "idle", "running": false },
      "config": {
        "limiterType": "",
        "permits": 0,
        "perSeconds": 1.0,
        "warmupSeconds": 0.0,
        "compositeCount": 2,
        "child0Type": "bursty",
        "child0Permits": 20,
        "child0PerSeconds": 1,
        "child0WarmupSeconds": 0,
        "child1Type": "bursty",
        "child1Permits": 30,
        "child1PerSeconds": 10,
        "child1WarmupSeconds": 0,
        "child2Type": "bursty",
        "child2Permits": 100,
        "child2PerSeconds": 60,
        "child2WarmupSeconds": 0,
        "child3Type": "bursty",
        "child3Permits": 100,
        "child3PerSeconds": 60,
        "child3WarmupSeconds": 0,
        "child4Type": "bursty",
        "child4Permits": 100,
        "child4PerSeconds": 60,
        "child4WarmupSeconds": 0,
        "requestsPerSecond": 0,
        "overflowMode": "queue",
        "apiTarget": "none",
        "serviceTimeMs": 50,
        "jitterMs": 20,
        "failureRate": 0.0,
        "workerConcurrency": 50
      },
      "stats": {
        "queued": 0,
        "inFlight": 0,
        "admitted": 0,
        "completed": 0,
        "denied": 0,
        "droppedIncoming": 0,
        "droppedOutgoing": 0,
        "acceptRate": 0,
        "rejectRate": 0,
        "avgLatencyMs": 0,
        "p50LatencyMs": 0,
        "p95LatencyMs": 0
      },
      "errors": { "form": null, "stream": null }
    }
    """.trimIndent()

private val CHART_SCRIPT =
    """
    (function(){
      var chart = null;
      var seriesStart = 0;
      var lastCompleted = 0;
      var wasRunning = false;
      function init(){
        if (chart || typeof Chart === 'undefined') return;
        var canvas = document.getElementById('metrics-chart');
        if (!canvas) return;
        chart = new Chart(canvas.getContext('2d'), {
          type: 'line',
          data: {
            labels: [],
            datasets: [
              {
                label: 'Throughput (req/s)',
                data: [],
                borderColor: '#3ca13c',
                backgroundColor: 'rgba(60,161,60,0.12)',
                fill: true,
                tension: 0.25,
                pointRadius: 0,
                borderWidth: 2
              },
              {
                label: 'Permits/sec',
                data: [],
                borderColor: '#1f2421',
                backgroundColor: 'transparent',
                borderDash: [6, 4],
                tension: 0,
                pointRadius: 0,
                borderWidth: 2
              },
              {
                label: 'Incoming/sec',
                data: [],
                borderColor: '#b42318',
                backgroundColor: 'transparent',
                borderDash: [2, 3],
                tension: 0,
                pointRadius: 0,
                borderWidth: 2
              }
            ]
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            interaction: { mode: 'index', intersect: false },
            scales: {
              x: { title: { display: true, text: 'time (s)' }, ticks: { autoSkip: true, maxTicksLimit: 8 } },
              y: { beginAtZero: true, title: { display: true, text: 'req/s' } }
            },
            plugins: { legend: { position: 'bottom', labels: { boxWidth: 12, font: { size: 11 } } } }
          }
        });
        seriesStart = Date.now();
      }
      function reset(){
        if (!chart) return;
        chart.data.labels = [];
        chart.data.datasets.forEach(function(d){ d.data = []; });
        seriesStart = Date.now();
        lastCompleted = 0;
        chart.update('none');
      }
      window.__chartPush = function(s){
        if (!chart) init();
        if (!chart || !s) return;
        var running = !!s.running;
        if (running && !wasRunning) reset();
        wasRunning = running;
        if (!running) return;
        var completed = Number(s.completed) || 0;
        if (completed < lastCompleted) reset();
        lastCompleted = completed;
        var t = ((Date.now() - seriesStart) / 1000).toFixed(1);
        chart.data.labels.push(t);
        chart.data.datasets[0].data.push(Number(s.acceptRate) || 0);
        chart.data.datasets[1].data.push(Number(s.permitsPerSec)  || 0);
        chart.data.datasets[2].data.push(Number(s.incomingPerSec) || 0);
        if (chart.data.labels.length > 150) {
          chart.data.labels.shift();
          chart.data.datasets.forEach(function(d){ d.data.shift(); });
        }
        chart.update('none');
      };
      window.__chartReset = reset;

      var LOG_LIMIT = 50;
      function trimLog(list) {
        while (list.children.length > LOG_LIMIT) {
          list.removeChild(list.lastElementChild);
        }
      }
      function attachLogTrimmer() {
        var list = document.getElementById('log-list');
        if (!list) { setTimeout(attachLogTrimmer, 100); return; }
        trimLog(list);
        new MutationObserver(function(){ trimLog(list); })
          .observe(list, { childList: true });
      }
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', attachLogTrimmer);
      } else {
        attachLogTrimmer();
      }
    })();
    """.trimIndent()

private data class LimiterChoice(val value: String, val label: String)

private val LimiterChoices =
    listOf(
        LimiterChoice("bursty", "Bursty"),
        LimiterChoice("smooth", "Smooth"),
        LimiterChoice("composite", "Composite"),
    )

private data class DurationChoice(val seconds: Int, val label: String)

private val DurationChoices =
    listOf(
        DurationChoice(1, "sec"),
        DurationChoice(60, "min"),
        DurationChoice(3600, "hour"),
        DurationChoice(86400, "day"),
    )

private fun FlowContent.renderInfoTip(text: String) {
    span("info-tip") {
        attributes["tabindex"] = "0"
        attributes["role"] = "img"
        attributes["aria-label"] = text
        attributes["data-tip"] = text
        +"?"
    }
}

private fun FlowContent.renderOverflowRow(idPrefix: String) {
    div("overflow-row") {
        span("overflow-label") { +"On overflow:" }
        div("overflow-toggle") {
            id = "$idPrefix-overflow-toggle"
            button(type = ButtonType.button) {
                id = "$idPrefix-overflow-queue"
                attributes["data-on-click"] =
                    "(\$config.overflowMode = 'queue', " +
                    "\$sim.running && @patch('/simulations/' + \$sim.id))"
                attributes["data-class-active"] =
                    "\$config.overflowMode === 'queue'"
                +"Queue"
            }
            button(type = ButtonType.button) {
                id = "$idPrefix-overflow-deny"
                attributes["data-on-click"] =
                    "(\$config.overflowMode = 'reject', " +
                    "\$sim.running && @patch('/simulations/' + \$sim.id))"
                attributes["data-class-active"] =
                    "\$config.overflowMode === 'reject'"
                +"Deny"
            }
        }
    }
}

private fun FlowContent.renderDurationToggle(
    idPrefix: String,
    permitsPath: String,
    perSecondsPath: String,
) {
    div("duration-toggle") {
        for (choice in DurationChoices) {
            button(type = ButtonType.button) {
                id = "$idPrefix-${choice.seconds}"
                // Compute the new permits count using the CURRENT perSeconds
                // (so the effective rate is preserved) BEFORE swapping
                // perSeconds to the new duration. Clamp to >= 1 so we don't
                // end up with a zero-permit limiter after rounding.
                attributes["data-on-click"] =
                    "(\$$permitsPath = Math.max(1, Math.round(" +
                    "\$$permitsPath * ${choice.seconds} / \$$perSecondsPath)), " +
                    "\$$perSecondsPath = ${choice.seconds}, " +
                    "\$sim.running && @patch('/simulations/' + \$sim.id))"
                attributes["data-class-active"] =
                    "\$$perSecondsPath === ${choice.seconds}"
                +choice.label
            }
        }
    }
}

private fun FlowContent.renderCompositeChildRow(index: Int) {
    val typeKey = "child${index}Type"
    val permitsKey = "child${index}Permits"
    val perSecondsKey = "child${index}PerSeconds"
    val warmupKey = "child${index}WarmupSeconds"
    val patchIfRunning = "\$sim.running && @patch('/simulations/' + \$sim.id)"

    div("composite-row") {
        id = "composite-row-$index"
        attributes["data-show"] = "\$config.compositeCount > $index"

        div("composite-row-header") {
            span("composite-row-label") { +"Limiter ${index + 1}" }
            div("composite-type-toggle") {
                button(type = ButtonType.button) {
                    id = "child$index-bursty"
                    attributes["data-on-click"] =
                        "(\$config.$typeKey = 'bursty', $patchIfRunning)"
                    attributes["data-class-active"] =
                        "\$config.$typeKey === 'bursty'"
                    +"Bursty"
                }
                button(type = ButtonType.button) {
                    id = "child$index-smooth"
                    attributes["data-on-click"] =
                        "(\$config.$typeKey = 'smooth', $patchIfRunning)"
                    attributes["data-class-active"] =
                        "\$config.$typeKey === 'smooth'"
                    +"Smooth"
                }
            }
            button(type = ButtonType.button, classes = "composite-remove") {
                id = "composite-remove-$index"
                attributes["data-on-click"] =
                    "(\$config.compositeCount = Math.max(\$config.compositeCount - 1, 1), " +
                    "$patchIfRunning)"
                attributes["data-attr-disabled"] = "\$config.compositeCount <= 1"
                +"Remove"
            }
        }

        div("composite-row-sliders") {
            div("child-slider") {
                label {
                    htmlFor = "input-$permitsKey"
                    +"Permits"
                }
                input {
                    id = "input-$permitsKey"
                    type = InputType.range
                    attributes["min"] = "1"
                    attributes["max"] = "2000"
                    attributes["step"] = "1"
                    attributes["data-bind"] = "config.$permitsKey"
                    attributes["data-on-change"] = patchIfRunning
                }
                div("child-slider-meta") {
                    renderDurationToggle(
                        idPrefix = "duration-child$index",
                        permitsPath = "config.$permitsKey",
                        perSecondsPath = "config.$perSecondsKey",
                    )
                    span("slider-value") {
                        attributes["data-text"] = "\$config.$permitsKey + ''"
                        +"—"
                    }
                }
            }

            div("child-slider") {
                attributes["data-show"] = "\$config.$typeKey === 'smooth'"
                label {
                    htmlFor = "input-$warmupKey"
                    +"Warmup (seconds)"
                }
                input {
                    id = "input-$warmupKey"
                    type = InputType.range
                    attributes["min"] = "0"
                    attributes["max"] = "30"
                    attributes["step"] = "0.5"
                    attributes["data-bind"] = "config.$warmupKey"
                    attributes["data-on-change"] = patchIfRunning
                }
                div("child-slider-meta") {
                    span("slider-value") {
                        attributes["data-text"] = "\$config.$warmupKey + 's'"
                        +"0s"
                    }
                }
            }
        }
    }
}

fun HTML.renderPageShell() {
    head {
        title { +"Kotlin Rate Limiter Demo" }
        meta { charset = "utf-8" }
        meta {
            name = "viewport"
            content = "width=device-width, initial-scale=1"
        }
        style {
            unsafe { raw(PAGE_CSS) }
        }
        script { src = CHARTJS_CDN }
        script {
            type = "module"
            src = DATASTAR_CDN
        }
    }
    body {
        div {
            id = "page-root"
            attributes["data-signals"] = initialSignals

            header {
                h1 { +"Kotlin Rate Limiter Demo" }
                p("subtitle") {
                    +"A demo app showing the use cases of "
                    a(href = "https://github.com/delder004/kotlin-rate-limiter") {
                        +"github.com/delder004/kotlin-rate-limiter"
                    }
                    +"."
                }
            }

            section("wizard-step") {
                id = "step-limiter"
                h2 { +"Choose your limiter:" }
                div("limiter-buttons") {
                    for (choice in LimiterChoices) {
                        button(type = ButtonType.button) {
                            id = "limiter-${choice.value}"
                            val pickClick =
                                "\$config.limiterType = '${choice.value}', " +
                                    "\$ui.step = Math.max(\$ui.step, 2)"
                            attributes["data-on-click"] =
                                when (choice.value) {
                                    "composite" ->
                                        "($pickClick, " +
                                            "\$ui.step = Math.max(\$ui.step, 3), " +
                                            "\$sim.running && @patch('/simulations/' + \$sim.id))"
                                    else -> "($pickClick)"
                                }
                            attributes["data-class-active"] =
                                "\$config.limiterType === '${choice.value}'"
                            +choice.label
                        }
                    }
                }
                renderFieldErrorSlot("limiterType")

                div("composite-editor") {
                    id = "composite-editor"
                    attributes["data-show"] = "\$config.limiterType === 'composite'"

                    for (index in 0 until 5) {
                        renderCompositeChildRow(index)
                    }

                    div("composite-add-row") {
                        button(type = ButtonType.button, classes = "composite-add") {
                            id = "composite-add"
                            attributes["data-on-click"] =
                                "(\$config.compositeCount = Math.min(\$config.compositeCount + 1, 5), " +
                                "\$sim.running && @patch('/simulations/' + \$sim.id))"
                            attributes["data-attr-disabled"] = "\$config.compositeCount >= 5"
                            +"+ Add limiter"
                        }
                    }

                    renderOverflowRow(idPrefix = "composite")
                }
            }

            section("wizard-step") {
                id = "step-permits"
                attributes["data-show"] =
                    "\$ui.step >= 2 && \$config.limiterType !== 'composite'"
                h2 { +"Permits:" }
                input {
                    id = "input-permits"
                    type = InputType.range
                    attributes["min"] = "0"
                    attributes["max"] = "500"
                    attributes["step"] = "1"
                    attributes["data-bind"] = "config.permits"
                    attributes["data-on-input"] =
                        "\$config.permits > 0 && (\$ui.step = Math.max(\$ui.step, 3))"
                    attributes["data-on-change"] =
                        "\$sim.running && @patch('/simulations/' + \$sim.id)"
                }
                div("child-slider-meta") {
                    renderDurationToggle(
                        idPrefix = "duration-top",
                        permitsPath = "config.permits",
                        perSecondsPath = "config.perSeconds",
                    )
                    span("slider-value") {
                        attributes["data-text"] =
                            "\$config.permits > 0 ? \$config.permits : '—'"
                        +"—"
                    }
                }
                renderFieldErrorSlot("permits")

                div("child-slider warmup-row") {
                    id = "warmup-row"
                    attributes["data-show"] = "\$config.limiterType === 'smooth'"
                    label {
                        htmlFor = "input-warmupSeconds"
                        +"Warmup (seconds)"
                    }
                    input {
                        id = "input-warmupSeconds"
                        type = InputType.range
                        attributes["min"] = "0"
                        attributes["max"] = "30"
                        attributes["step"] = "0.5"
                        attributes["data-bind"] = "config.warmupSeconds"
                        attributes["data-on-change"] =
                            "\$sim.running && @patch('/simulations/' + \$sim.id)"
                    }
                    div("child-slider-meta") {
                        span("slider-value") {
                            attributes["data-text"] = "\$config.warmupSeconds + 's'"
                            +"0s"
                        }
                    }
                    renderFieldErrorSlot("warmupSeconds")
                }

                renderOverflowRow(idPrefix = "permits")
                renderFieldErrorSlot("overflowMode")
            }

            section("wizard-step") {
                id = "step-traffic"
                attributes["data-show"] = "\$ui.step >= 3"
                h2 { +"Requests per sec:" }
                input {
                    id = "input-requestsPerSecond"
                    type = InputType.range
                    attributes["min"] = "0"
                    attributes["max"] = "500"
                    attributes["step"] = "1"
                    attributes["data-bind"] = "config.requestsPerSecond"
                    attributes["data-on-input"] =
                        "\$config.requestsPerSecond > 0 && " +
                        "(\$ui.step = Math.max(\$ui.step, 4))"
                    attributes["data-on-change"] =
                        "\$sim.running && @patch('/simulations/' + \$sim.id)"
                }
                div("child-slider-meta") {
                    span("slider-value") {
                        attributes["data-text"] =
                            "\$config.requestsPerSecond > 0 ? " +
                            "(\$config.requestsPerSecond + ' req/s') : '—'"
                        +"—"
                    }
                }
                renderFieldErrorSlot("requestsPerSecond")

                details("traffic-advanced") {
                    summary { +"Advanced" }
                    div("traffic-advanced-body") {
                        div("child-slider") {
                            div("child-slider-label-row") {
                                label {
                                    htmlFor = "input-serviceTimeMs"
                                    +"Service time"
                                }
                                renderInfoTip(
                                    "Base time each simulated request takes to process, " +
                                        "in milliseconds. Higher values keep workers busy longer, " +
                                        "lowering effective throughput and growing the queue.",
                                )
                            }
                            input {
                                id = "input-serviceTimeMs"
                                type = InputType.range
                                attributes["min"] = "0"
                                attributes["max"] = "500"
                                attributes["step"] = "5"
                                attributes["data-bind"] = "config.serviceTimeMs"
                                attributes["data-on-change"] =
                                    "\$sim.running && @patch('/simulations/' + \$sim.id)"
                            }
                            div("child-slider-meta") {
                                span("slider-value") {
                                    attributes["data-text"] = "\$config.serviceTimeMs + ' ms'"
                                    +"50 ms"
                                }
                            }
                            renderFieldErrorSlot("serviceTimeMs")
                        }

                        div("child-slider") {
                            div("child-slider-label-row") {
                                label {
                                    htmlFor = "input-jitterMs"
                                    +"Jitter"
                                }
                                renderInfoTip(
                                    "Random variance added to each request's service time, " +
                                        "from 0 up to this value. Simulates real-world latency " +
                                        "noise so requests don't finish in lockstep.",
                                )
                            }
                            input {
                                id = "input-jitterMs"
                                type = InputType.range
                                attributes["min"] = "0"
                                attributes["max"] = "200"
                                attributes["step"] = "1"
                                attributes["data-bind"] = "config.jitterMs"
                                attributes["data-on-change"] =
                                    "\$sim.running && @patch('/simulations/' + \$sim.id)"
                            }
                            div("child-slider-meta") {
                                span("slider-value") {
                                    attributes["data-text"] = "'±' + \$config.jitterMs + ' ms'"
                                    +"±20 ms"
                                }
                            }
                            renderFieldErrorSlot("jitterMs")
                        }

                        div("child-slider") {
                            div("child-slider-label-row") {
                                label {
                                    htmlFor = "input-failureRate"
                                    +"Failure rate"
                                }
                                renderInfoTip(
                                    "Chance each processed request is marked as a failure " +
                                        "(500 response). Failed requests still consume a permit " +
                                        "and a worker slot — they just don't count as successful work.",
                                )
                            }
                            input {
                                id = "input-failureRate"
                                type = InputType.range
                                attributes["min"] = "0"
                                attributes["max"] = "1"
                                attributes["step"] = "0.01"
                                attributes["data-bind"] = "config.failureRate"
                                attributes["data-on-change"] =
                                    "\$sim.running && @patch('/simulations/' + \$sim.id)"
                            }
                            div("child-slider-meta") {
                                span("slider-value") {
                                    attributes["data-text"] =
                                        "Math.round(\$config.failureRate * 100) + '%'"
                                    +"0%"
                                }
                            }
                            renderFieldErrorSlot("failureRate")
                        }

                        div("child-slider") {
                            div("child-slider-label-row") {
                                label {
                                    htmlFor = "input-workerConcurrency"
                                    +"Worker concurrency"
                                }
                                renderInfoTip(
                                    "Number of parallel workers pulling from the queue. Acts as " +
                                        "a hard ceiling on in-flight requests, independent of the " +
                                        "limiter — too few workers will bottleneck even a generous limit.",
                                )
                            }
                            input {
                                id = "input-workerConcurrency"
                                type = InputType.range
                                attributes["min"] = "1"
                                attributes["max"] = "200"
                                attributes["step"] = "1"
                                attributes["data-bind"] = "config.workerConcurrency"
                                attributes["data-on-change"] =
                                    "\$sim.running && @patch('/simulations/' + \$sim.id)"
                            }
                            div("child-slider-meta") {
                                span("slider-value") {
                                    attributes["data-text"] = "\$config.workerConcurrency"
                                    +"50"
                                }
                            }
                            renderFieldErrorSlot("workerConcurrency")
                        }
                    }
                }
            }

            section("wizard-step") {
                id = "step-start"
                attributes["data-show"] = "\$ui.step >= 4"
                div("start-row") {
                    renderLifecycleControlsSlot()
                }
            }

            div {
                id = "run-panels"
                attributes["data-show"] = "\$ui.step >= 5"

                section {
                    id = "status-panel"
                    div("running-bar") {
                        renderStatusBadgeSlot()
                        span {
                            attributes["data-text"] = "'id: ' + (\$sim.id || '—')"
                            +"id: —"
                        }
                    }
                }

                section {
                    id = "chart-panel"
                    h2 { +"Chart" }
                    renderChartMount()
                    renderStreamAnchorSlot()
                }

                section {
                    id = "stats-panel"
                    h2 { +"Stats" }
                    ul {
                        li {
                            +"Queued: "
                            span {
                                attributes["data-text"] = "\$stats.queued"
                                +"0"
                            }
                        }
                        li {
                            +"In flight: "
                            span {
                                attributes["data-text"] = "\$stats.inFlight"
                                +"0"
                            }
                        }
                        li {
                            +"Admitted: "
                            span {
                                attributes["data-text"] = "\$stats.admitted"
                                +"0"
                            }
                        }
                        li {
                            +"Completed: "
                            span {
                                attributes["data-text"] = "\$stats.completed"
                                +"0"
                            }
                        }
                        li {
                            +"Denied: "
                            span {
                                attributes["data-text"] = "\$stats.denied"
                                +"0"
                            }
                        }
                        li {
                            +"Dropped incoming: "
                            span {
                                attributes["data-text"] = "\$stats.droppedIncoming"
                                +"0"
                            }
                        }
                        li {
                            +"Dropped outgoing: "
                            span {
                                attributes["data-text"] = "\$stats.droppedOutgoing"
                                +"0"
                            }
                        }
                        li {
                            +"Accept rate: "
                            span {
                                attributes["data-text"] = "\$stats.acceptRate"
                                +"0"
                            }
                        }
                        li {
                            +"Reject rate: "
                            span {
                                attributes["data-text"] = "\$stats.rejectRate"
                                +"0"
                            }
                        }
                        li {
                            +"Avg latency (ms): "
                            span {
                                attributes["data-text"] = "\$stats.avgLatencyMs"
                                +"0"
                            }
                        }
                        li {
                            +"p50 latency (ms): "
                            span {
                                attributes["data-text"] = "\$stats.p50LatencyMs"
                                +"0"
                            }
                        }
                        li {
                            +"p95 latency (ms): "
                            span {
                                attributes["data-text"] = "\$stats.p95LatencyMs"
                                +"0"
                            }
                        }
                    }
                }

                section {
                    id = "status-log-panel"
                    h2 { +"Status Log" }
                    renderStatusLogSlot()
                }

                section {
                    id = "log-panel"
                    h2 { +"Response Log" }
                    renderLogListSlot()
                }

                section("page-errors") {
                    id = "page-errors"
                    h2 { +"Error Log" }
                    renderFormErrorsSlot()
                    div("stream-errors") {
                        id = "errors-stream"
                        attributes["data-text"] = "\$errors.stream || ''"
                    }
                }
            }
        }
        script {
            unsafe { raw(CHART_SCRIPT) }
        }
    }
}
