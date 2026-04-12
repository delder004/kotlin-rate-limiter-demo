package com.example

import kotlinx.html.*

fun HTML.dashboardPage() {
    head {
        title("Rate Limiter Dashboard")
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
        script { src = "https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js" }
        style {
            unsafe {
                raw(CSS)
            }
        }
    }
    body {
        div("container") {
            h1 { +"Rate Limiter Dashboard" }
            p("subtitle") { +"Interactive visualization of kotlin-rate-limiter" }

            div("controls") {
                // API target
                div("control-group") {
                    label { htmlFor = "apiTarget"; +"API Target" }
                    select {
                        id = "apiTarget"
                        onChange = "onApiTargetChange(); onConfigChange()"
                        option { value = "none"; +"Simulated (recommended)" }
                        option { value = "catfact"; +"catfact.ninja" }
                        option { value = "jsonplaceholder"; +"JSONPlaceholder" }
                    }
                }

                // Overflow mode
                div("control-group") {
                    label { htmlFor = "overflowMode"; +"On Limit" }
                    select {
                        id = "overflowMode"
                        onChange = "onConfigChange()"
                        option { value = "queue"; +"Queue" }
                        option { value = "reject"; +"Reject (429)" }
                    }
                }

                // Limiter type
                div("control-group") {
                    label { htmlFor = "limiterType"; +"Limiter Type" }
                    select {
                        id = "limiterType"
                        onChange = "onTypeChange(); onConfigChange()"
                        option { value = "bursty"; +"Bursty" }
                        option { value = "smooth"; +"Smooth" }
                        option { value = "composite"; +"Composite" }
                    }
                }

                // Primary config
                div("control-group") {
                    label { htmlFor = "permits"; +"Permits" }
                    input {
                        id = "permits"
                        type = InputType.number
                        value = "10"
                        min = "1"
                        max = "10000"
                        onInput = "onConfigChange()"
                    }
                }
                div("control-group") {
                    label { htmlFor = "perSeconds"; +"Per (seconds)" }
                    input {
                        id = "perSeconds"
                        type = InputType.number
                        value = "1"
                        min = "0.1"
                        max = "60"
                        step = "0.1"
                        onInput = "onConfigChange()"
                    }
                }

                // Smooth-specific
                div("control-group smooth-only hidden") {
                    label { htmlFor = "warmup"; +"Warmup (seconds)" }
                    input {
                        id = "warmup"
                        type = InputType.number
                        value = "2"
                        min = "0"
                        max = "30"
                        step = "0.5"
                        onInput = "onConfigChange()"
                    }
                }

                // Composite-specific
                div("control-group composite-only hidden") {
                    label { htmlFor = "secondaryPermits"; +"Secondary Permits" }
                    input {
                        id = "secondaryPermits"
                        type = InputType.number
                        value = "30"
                        min = "1"
                        max = "1000"
                        onInput = "onConfigChange()"
                    }
                }
                div("control-group composite-only hidden") {
                    label { htmlFor = "secondaryPer"; +"Secondary Per (sec)" }
                    input {
                        id = "secondaryPer"
                        type = InputType.number
                        value = "60"
                        min = "1"
                        max = "3600"
                        onInput = "onConfigChange()"
                    }
                }

                // Simulated-upstream tuning (visible when API Target = Simulated)
                div("control-group sim-only") {
                    label { htmlFor = "serviceTime"; +"Service Time (ms)" }
                    input {
                        id = "serviceTime"
                        type = InputType.number
                        value = "50"
                        min = "0"
                        max = "5000"
                        onInput = "onConfigChange()"
                    }
                }
                div("control-group sim-only") {
                    label { htmlFor = "jitter"; +"Jitter (ms)" }
                    input {
                        id = "jitter"
                        type = InputType.number
                        value = "20"
                        min = "0"
                        max = "2000"
                        onInput = "onConfigChange()"
                    }
                }
                div("control-group sim-only") {
                    label { htmlFor = "failureRate"; +"Failure Rate (%)" }
                    input {
                        id = "failureRate"
                        type = InputType.number
                        value = "0"
                        min = "0"
                        max = "100"
                        step = "1"
                        onInput = "onConfigChange()"
                    }
                }
                div("control-group") {
                    label { htmlFor = "workerConcurrency"; +"Workers" }
                    input {
                        id = "workerConcurrency"
                        type = InputType.number
                        value = "50"
                        min = "1"
                        max = "500"
                        onInput = "onConfigChange()"
                    }
                }
            }

            // Request rate slider
            div("slider-section") {
                div("slider-header") {
                    label { htmlFor = "rateSlider"; +"Incoming Request Rate" }
                    span {
                        id = "rateValue"
                        +"5 req/s"
                    }
                }
                input {
                    id = "rateSlider"
                    type = InputType.range
                    min = "0"
                    max = "100"
                    value = "17"
                    onInput = "onRateChange()"
                }
                div("slider-ticks") {
                    span { attributes["data-label"] = "1"; +"" }
                    span { attributes["data-label"] = "10"; +"" }
                    span { attributes["data-label"] = "100"; +"" }
                    span { attributes["data-label"] = "1k"; +"" }
                    span { attributes["data-label"] = "10k"; +"" }
                }
            }

            // Presets
            div("presets") {
                span("presets-label") { +"Presets" }
                button { classes = setOf("preset-btn"); onClick = "applyPreset('burst-overload')"; +"Burst Overload" }
                button { classes = setOf("preset-btn"); onClick = "applyPreset('queue-backlog')"; +"Queue Backlog" }
                button { classes = setOf("preset-btn"); onClick = "applyPreset('warmup-ramp')"; +"Warm-up Ramp" }
                button { classes = setOf("preset-btn"); onClick = "applyPreset('composite-tiers')"; +"Composite Tiers" }
                button { classes = setOf("preset-btn"); onClick = "applyPreset('small-pool')"; +"Small Pool + Queue" }
            }

            // Action buttons
            div("actions") {
                button {
                    id = "startBtn"
                    onClick = "startSimulation()"
                    +"Start"
                }
                button {
                    id = "stopBtn"
                    classes = setOf("secondary")
                    onClick = "stopSimulation()"
                    +"Stop"
                }
                button {
                    id = "clearBtn"
                    classes = setOf("secondary")
                    onClick = "clearChart()"
                    +"Clear"
                }
            }

            // Stats — two rows of 5
            div("stats") {
                div("stat-card") { div("stat-value") { id = "statQueued"; +"0" }; div("stat-label") { +"Queued" } }
                div("stat-card") { div("stat-value") { id = "statInFlight"; +"0" }; div("stat-label") { +"In-Flight" } }
                div("stat-card") { div("stat-value") { id = "statCompleted"; +"0" }; div("stat-label") { +"Completed" } }
                div("stat-card") { div("stat-value") { id = "statDenied"; +"0" }; div("stat-label") { +"Denied" } }
                div("stat-card") { div("stat-value") { id = "statThroughput"; +"0" }; div("stat-label") { +"Throughput/s" } }
                div("stat-card") { div("stat-value") { id = "statAcceptRate"; +"0" }; div("stat-label") { +"Accept/s" } }
                div("stat-card") { div("stat-value") { id = "statRejectRate"; +"0" }; div("stat-label") { +"Reject/s" } }
                div("stat-card") { div("stat-value") { id = "statLatency"; +"0ms" }; div("stat-label") { +"Avg Latency" } }
                div("stat-card") { div("stat-value") { id = "statP50"; +"0ms" }; div("stat-label") { +"P50 Latency" } }
                div("stat-card") { div("stat-value") { id = "statP95"; +"0ms" }; div("stat-label") { +"P95 Latency" } }
            }

            // Chart
            div("chart-container") {
                canvas { id = "metricsChart" }
            }

            // Response log
            div("log-section") {
                div("log-header") {
                    span { +"Response Log" }
                    span("log-badge") { id = "logCount"; +"0" }
                }
                div("log-container") {
                    id = "responseLog"
                }
            }
        }

        script {
            unsafe { raw(JS) }
        }
    }
}

private val CSS = """
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
        background: #0f1117;
        color: #e1e4e8;
        min-height: 100vh;
    }
    .container { max-width: 1100px; margin: 0 auto; padding: 32px 24px; }
    h1 { font-size: 24px; font-weight: 600; margin-bottom: 4px; }
    .subtitle { color: #8b949e; font-size: 14px; margin-bottom: 28px; }

    .controls {
        display: flex; flex-wrap: wrap; gap: 16px;
        background: #161b22; border: 1px solid #30363d; border-radius: 8px;
        padding: 20px; margin-bottom: 20px;
    }
    .control-group { display: flex; flex-direction: column; gap: 6px; }
    .control-group label { font-size: 12px; font-weight: 500; color: #8b949e; text-transform: uppercase; letter-spacing: 0.5px; }
    .control-group input, .control-group select {
        background: #0d1117; border: 1px solid #30363d; border-radius: 6px;
        color: #e1e4e8; padding: 8px 12px; font-size: 14px; width: 130px;
    }
    .control-group select { width: 146px; }
    .hidden { display: none !important; }

    .slider-section {
        background: #161b22; border: 1px solid #30363d; border-radius: 8px;
        padding: 20px; margin-bottom: 16px;
    }
    .slider-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .slider-header label { font-size: 14px; font-weight: 500; }
    #rateValue {
        background: #1f6feb22; color: #58a6ff; padding: 4px 12px;
        border-radius: 20px; font-size: 14px; font-weight: 600; font-variant-numeric: tabular-nums;
    }
    input[type=range] {
        -webkit-appearance: none; width: 100%; height: 6px;
        background: #30363d; border-radius: 3px; outline: none;
    }
    input[type=range]::-webkit-slider-thumb {
        -webkit-appearance: none; width: 20px; height: 20px;
        background: #58a6ff; border-radius: 50%; cursor: pointer;
    }
    .slider-ticks { display: flex; justify-content: space-between; margin-top: 8px; position: relative; }
    .slider-ticks span {
        display: flex; flex-direction: column; align-items: center; width: 0; font-size: 11px; color: #484f58;
    }
    .slider-ticks span::before {
        content: ''; display: block; width: 1px; height: 8px; background: #484f58; margin-bottom: 4px;
    }
    .slider-ticks span::after { content: attr(data-label); white-space: nowrap; }

    .presets {
        display: flex; gap: 8px; flex-wrap: wrap; align-items: center;
        background: #161b22; border: 1px solid #30363d; border-radius: 8px;
        padding: 14px 20px; margin-bottom: 16px;
    }
    .presets-label {
        font-size: 12px; font-weight: 600; color: #8b949e;
        text-transform: uppercase; letter-spacing: 0.5px; margin-right: 6px;
    }
    .preset-btn {
        background: #21262d; color: #c9d1d9; border: 1px solid #30363d;
        padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer;
    }
    .preset-btn:hover { background: #30363d; border-color: #58a6ff; }

    .actions { display: flex; gap: 10px; margin-bottom: 20px; }
    button {
        padding: 10px 24px; border: none; border-radius: 6px; font-size: 14px;
        font-weight: 600; cursor: pointer; transition: all 0.15s;
    }
    #startBtn { background: #238636; color: #fff; }
    #startBtn:hover { background: #2ea043; }
    button.secondary { background: #21262d; color: #c9d1d9; border: 1px solid #30363d; }
    button.secondary:hover { background: #30363d; }

    .stats {
        display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px;
        margin-bottom: 20px;
    }
    .stat-card {
        background: #161b22; border: 1px solid #30363d; border-radius: 8px;
        padding: 14px; text-align: center;
    }
    .stat-value { font-size: 24px; font-weight: 700; font-variant-numeric: tabular-nums; }
    .stat-label { font-size: 11px; color: #8b949e; margin-top: 4px; text-transform: uppercase; letter-spacing: 0.5px; }
    #statQueued { color: #f0883e; }
    #statInFlight { color: #ffab70; }
    #statCompleted { color: #3fb950; }
    #statDenied { color: #f85149; }
    #statThroughput { color: #58a6ff; }
    #statAcceptRate { color: #3fb950; }
    #statRejectRate { color: #f85149; }
    #statLatency { color: #bc8cff; }
    #statP50 { color: #bc8cff; }
    #statP95 { color: #d2a8ff; }

    .chart-container {
        background: #161b22; border: 1px solid #30363d; border-radius: 8px;
        padding: 20px; height: 400px;
    }
    canvas { width: 100% !important; height: 100% !important; }

    .log-section {
        background: #161b22; border: 1px solid #30363d; border-radius: 8px;
        margin-top: 20px; overflow: hidden;
    }
    .log-header {
        display: flex; align-items: center; gap: 8px;
        padding: 12px 20px; border-bottom: 1px solid #30363d;
        font-size: 13px; font-weight: 600; color: #8b949e; text-transform: uppercase; letter-spacing: 0.5px;
    }
    .log-badge {
        background: #30363d; color: #8b949e; padding: 1px 8px;
        border-radius: 10px; font-size: 11px; font-weight: 600;
    }
    .log-container {
        font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
        font-size: 12px; line-height: 1.6; padding: 12px 0;
        max-height: 380px; overflow-y: auto;
    }
    .log-entry { padding: 2px 20px; display: flex; gap: 12px; align-items: baseline; }
    .log-entry:hover { background: #1c2128; }
    .log-time { color: #484f58; min-width: 60px; }
    .log-status { font-weight: 600; min-width: 32px; }
    .log-status.s2xx { color: #3fb950; }
    .log-status.s4xx { color: #f0883e; }
    .log-status.s5xx { color: #f85149; }
    .log-latency { color: #bc8cff; min-width: 55px; text-align: right; }
    .log-body { color: #8b949e; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    @media (max-width: 900px) {
        .stats { grid-template-columns: repeat(2, 1fr); }
        .controls { flex-direction: column; }
        .control-group input, .control-group select { width: 100%; }
    }
""".trimIndent()

private val JS = """
let ws = null;
let chart = null;
let lastCompleted = 0;
let lastTime = 0;
let logCount = 0;

function initChart() {
    const ctx = document.getElementById('metricsChart').getContext('2d');
    chart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                { label: 'Queued', data: [], borderColor: '#f0883e', backgroundColor: '#f0883e22',
                  fill: true, tension: 0.3, pointRadius: 0, borderWidth: 2 },
                { label: 'In-Flight', data: [], borderColor: '#ffab70', backgroundColor: '#ffab7022',
                  fill: false, tension: 0.3, pointRadius: 0, borderWidth: 2 },
                { label: 'Throughput/s', data: [], borderColor: '#58a6ff', backgroundColor: '#58a6ff22',
                  fill: false, tension: 0.3, pointRadius: 0, borderWidth: 2, yAxisID: 'y1' },
                { label: 'P95 Latency (ms)', data: [], borderColor: '#d2a8ff', backgroundColor: '#d2a8ff22',
                  fill: false, tension: 0.3, pointRadius: 0, borderWidth: 2, borderDash: [4, 4], yAxisID: 'y2' },
            ],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: { duration: 0 },
            interaction: { intersect: false, mode: 'index' },
            scales: {
                x: {
                    title: { display: true, text: 'Time (s)', color: '#8b949e' },
                    ticks: { color: '#484f58', maxTicksLimit: 20 },
                    grid: { color: '#21262d' },
                },
                y: {
                    title: { display: true, text: 'Queue depth', color: '#8b949e' },
                    ticks: { color: '#484f58' },
                    grid: { color: '#21262d' },
                    beginAtZero: true,
                },
                y1: {
                    position: 'right',
                    title: { display: true, text: 'Throughput/s', color: '#8b949e' },
                    ticks: { color: '#484f58' },
                    grid: { drawOnChartArea: false },
                    beginAtZero: true,
                },
                y2: { display: false, beginAtZero: true },
            },
            plugins: {
                legend: { labels: { color: '#c9d1d9', usePointStyle: true, pointStyle: 'line' } },
            },
        },
    });
}

function getConfig() {
    const type = document.getElementById('limiterType').value;
    return {
        type,
        permits: parseInt(document.getElementById('permits').value) || 10,
        perSeconds: parseFloat(document.getElementById('perSeconds').value) || 1,
        warmupSeconds: parseFloat(document.getElementById('warmup')?.value) || 0,
        secondaryPermits: parseInt(document.getElementById('secondaryPermits')?.value) || 30,
        secondaryPerSeconds: parseFloat(document.getElementById('secondaryPer')?.value) || 60,
    };
}

// Logarithmic mapping: slider 0–100 → rate 1–10000
function sliderToRate(val01) {
    return Math.round(Math.pow(10, val01 / 100 * 4));
}
function rateToSlider(rate) {
    return Math.log10(Math.max(1, rate)) / 4 * 100;
}

function getRate() {
    const slider = parseFloat(document.getElementById('rateSlider').value) || 0;
    return Math.max(1, sliderToRate(slider));
}
function getApiTarget() { return document.getElementById('apiTarget').value; }
function getOverflowMode() { return document.getElementById('overflowMode').value; }

function buildStartMessage(action) {
    return {
        action,
        config: getConfig(),
        requestsPerSecond: getRate(),
        apiTarget: getApiTarget(),
        overflowMode: getOverflowMode(),
        serviceTimeMs: parseInt(document.getElementById('serviceTime').value) || 50,
        jitterMs: parseInt(document.getElementById('jitter').value) || 0,
        failureRate: (parseFloat(document.getElementById('failureRate').value) || 0) / 100,
        workerConcurrency: parseInt(document.getElementById('workerConcurrency').value) || 50,
    };
}

function onTypeChange() {
    const type = document.getElementById('limiterType').value;
    document.querySelectorAll('.smooth-only').forEach(el => el.classList.toggle('hidden', type !== 'smooth'));
    document.querySelectorAll('.composite-only').forEach(el => el.classList.toggle('hidden', type !== 'composite'));
}

function onApiTargetChange() {
    const target = document.getElementById('apiTarget').value;
    document.querySelectorAll('.sim-only').forEach(el => el.classList.toggle('hidden', target !== 'none'));
}

let configDebounce = null;
function onConfigChange() {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    clearTimeout(configDebounce);
    configDebounce = setTimeout(() => {
        lastCompleted = 0;
        lastTime = 0;
        ws.send(JSON.stringify(buildStartMessage('updateRate')));
    }, 300);
}

function onRateChange() {
    const rate = getRate();
    document.getElementById('rateValue').textContent = rate.toLocaleString() + ' req/s';
    onConfigChange();
}

const PRESETS = {
    'burst-overload': {
        _desc: '20 req/s into 5/s bursty, reject mode — watch denied climb',
        limiterType: 'bursty', permits: 5, perSeconds: 1,
        overflowMode: 'reject', rate: 20,
        apiTarget: 'none', serviceTime: 50, jitter: 20, failureRate: 0,
        workerConcurrency: 50,
    },
    'queue-backlog': {
        _desc: '20 req/s into 5/s bursty, queue mode — watch queue build',
        limiterType: 'bursty', permits: 5, perSeconds: 1,
        overflowMode: 'queue', rate: 20,
        apiTarget: 'none', serviceTime: 50, jitter: 20, failureRate: 0,
        workerConcurrency: 50,
    },
    'warmup-ramp': {
        _desc: 'Smooth limiter with 5s warmup — throughput ramps up gradually',
        limiterType: 'smooth', permits: 10, perSeconds: 1, warmup: 5,
        overflowMode: 'queue', rate: 20,
        apiTarget: 'none', serviceTime: 30, jitter: 10, failureRate: 0,
        workerConcurrency: 50,
    },
    'composite-tiers': {
        _desc: '10/s burst + 30/min sustained, reject mode',
        limiterType: 'composite', permits: 10, perSeconds: 1,
        secondaryPermits: 30, secondaryPer: 60,
        overflowMode: 'reject', rate: 30,
        apiTarget: 'none', serviceTime: 50, jitter: 20, failureRate: 0,
        workerConcurrency: 50,
    },
    'small-pool': {
        _desc: '100 req/s → 20/s limiter, only 5 workers — queue + worker contention',
        limiterType: 'bursty', permits: 20, perSeconds: 1,
        overflowMode: 'queue', rate: 100,
        apiTarget: 'none', serviceTime: 100, jitter: 30, failureRate: 0,
        workerConcurrency: 5,
    },
};

function applyPreset(name) {
    const p = PRESETS[name];
    if (!p) return;
    document.getElementById('limiterType').value = p.limiterType;
    document.getElementById('permits').value = p.permits;
    document.getElementById('perSeconds').value = p.perSeconds;
    if (p.warmup !== undefined) document.getElementById('warmup').value = p.warmup;
    if (p.secondaryPermits !== undefined) document.getElementById('secondaryPermits').value = p.secondaryPermits;
    if (p.secondaryPer !== undefined) document.getElementById('secondaryPer').value = p.secondaryPer;
    document.getElementById('overflowMode').value = p.overflowMode;
    document.getElementById('apiTarget').value = p.apiTarget;
    document.getElementById('serviceTime').value = p.serviceTime;
    document.getElementById('jitter').value = p.jitter;
    document.getElementById('failureRate').value = Math.round((p.failureRate || 0) * 100);
    document.getElementById('workerConcurrency').value = p.workerConcurrency;
    document.getElementById('rateSlider').value = rateToSlider(p.rate);
    document.getElementById('rateValue').textContent = p.rate.toLocaleString() + ' req/s';
    onTypeChange();
    onApiTargetChange();
    clearChart();
    // If already running, update; otherwise start.
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(buildStartMessage('updateRate')));
    } else {
        startSimulation();
    }
}

function startSimulation() {
    if (ws) ws.close();
    lastCompleted = 0;
    lastTime = 0;

    const loc = window.location;
    ws = new WebSocket((loc.protocol === 'https:' ? 'wss:' : 'ws:') + '//' + loc.host + '/ws');

    ws.onopen = () => {
        ws.send(JSON.stringify(buildStartMessage('start')));
    };

    ws.onmessage = (event) => {
        const msg = JSON.parse(event.data);

        if (msg.type === 'response') {
            appendResponseLog(msg);
            return;
        }

        const timeSec = (msg.timeMs / 1000).toFixed(1);

        // Throughput: completed delta / time delta
        const timeDelta = msg.timeMs - lastTime;
        const completedDelta = msg.completed - lastCompleted;
        const throughput = (lastTime > 0 && timeDelta > 0)
            ? (completedDelta / (timeDelta / 1000)).toFixed(1)
            : 0;
        lastCompleted = msg.completed;
        lastTime = msg.timeMs;

        // Update stats
        document.getElementById('statQueued').textContent = msg.queued;
        document.getElementById('statInFlight').textContent = msg.inFlight;
        document.getElementById('statCompleted').textContent = msg.completed;
        document.getElementById('statDenied').textContent = msg.denied;
        document.getElementById('statThroughput').textContent = throughput;
        document.getElementById('statAcceptRate').textContent = msg.acceptRate.toFixed(1);
        document.getElementById('statRejectRate').textContent = msg.rejectRate.toFixed(1);
        document.getElementById('statLatency').textContent = msg.avgLatencyMs + 'ms';
        document.getElementById('statP50').textContent = msg.p50LatencyMs + 'ms';
        document.getElementById('statP95').textContent = msg.p95LatencyMs + 'ms';

        // Update chart — keep last 150 points
        chart.data.labels.push(timeSec);
        chart.data.datasets[0].data.push(msg.queued);
        chart.data.datasets[1].data.push(msg.inFlight);
        chart.data.datasets[2].data.push(parseFloat(throughput));
        chart.data.datasets[3].data.push(msg.p95LatencyMs);

        if (chart.data.labels.length > 150) {
            chart.data.labels.shift();
            chart.data.datasets.forEach(ds => ds.data.shift());
        }
        chart.update();
    };

    ws.onclose = () => { ws = null; };
}

function stopSimulation() {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action: 'stop' }));
    }
    if (ws) { ws.close(); ws = null; }
}

function clearChart() {
    if (chart) {
        chart.data.labels = [];
        chart.data.datasets.forEach(ds => { ds.data = []; });
        chart.update();
    }
    ['statQueued', 'statInFlight', 'statCompleted', 'statDenied', 'statThroughput',
     'statAcceptRate', 'statRejectRate'].forEach(id => {
        document.getElementById(id).textContent = '0';
    });
    ['statLatency', 'statP50', 'statP95'].forEach(id => {
        document.getElementById(id).textContent = '0ms';
    });
    document.getElementById('responseLog').innerHTML = '';
    document.getElementById('logCount').textContent = '0';
    lastCompleted = 0;
    lastTime = 0;
    logCount = 0;
}

function appendResponseLog(msg) {
    const log = document.getElementById('responseLog');
    const timeSec = (msg.timeMs / 1000).toFixed(2);
    const statusClass = msg.status < 300 ? 's2xx' : msg.status < 500 ? 's4xx' : 's5xx';

    const entry = document.createElement('div');
    entry.className = 'log-entry';
    entry.innerHTML =
        '<span class="log-time">' + timeSec + 's</span>' +
        '<span class="log-status ' + statusClass + '">' + msg.status + '</span>' +
        '<span class="log-latency">' + msg.latencyMs + 'ms</span>' +
        '<span class="log-body">' + escapeHtml(msg.body) + '</span>';

    log.appendChild(entry);
    logCount++;
    document.getElementById('logCount').textContent = logCount;

    while (log.children.length > 20) {
        log.removeChild(log.firstChild);
    }
    log.scrollTop = log.scrollHeight;
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

document.addEventListener('DOMContentLoaded', () => {
    initChart();
    onTypeChange();
    onApiTargetChange();
});
""".trimIndent()
