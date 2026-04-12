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
                        onChange = "onConfigChange()"
                        option { value = "catfact"; +"catfact.ninja" }
                        option { value = "jsonplaceholder"; +"JSONPlaceholder" }
                        option { value = "none"; +"None (simulated)" }
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
                    // 10^0=1, 10^1=10, 10^2=100, 10^3=1000, 10^4=10000
                    // slider positions: 0, 25, 50, 75, 100
                    span { attributes["data-label"] = "1"; +"" }
                    span { attributes["data-label"] = "10"; +"" }
                    span { attributes["data-label"] = "100"; +"" }
                    span { attributes["data-label"] = "1k"; +"" }
                    span { attributes["data-label"] = "10k"; +"" }
                }
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

            // Stats
            div("stats") {
                div("stat-card") {
                    div("stat-value") { id = "statQueued"; +"0" }
                    div("stat-label") { +"Queued" }
                }
                div("stat-card") {
                    div("stat-value") { id = "statCompleted"; +"0" }
                    div("stat-label") { +"Completed" }
                }
                div("stat-card") {
                    div("stat-value") { id = "statDenied"; +"0" }
                    div("stat-label") { +"Denied" }
                }
                div("stat-card") {
                    div("stat-value") { id = "statThroughput"; +"0" }
                    div("stat-label") { +"Throughput/s" }
                }
                div("stat-card") {
                    div("stat-value") { id = "statLatency"; +"0ms" }
                    div("stat-label") { +"Avg Latency" }
                }
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
    .container { max-width: 960px; margin: 0 auto; padding: 32px 24px; }
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
        padding: 20px; margin-bottom: 20px;
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
    .slider-ticks {
        display: flex; justify-content: space-between; margin-top: 8px; position: relative;
    }
    .slider-ticks span {
        display: flex; flex-direction: column; align-items: center; width: 0; font-size: 11px; color: #484f58;
    }
    .slider-ticks span::before {
        content: ''; display: block; width: 1px; height: 8px; background: #484f58; margin-bottom: 4px;
    }
    .slider-ticks span::after {
        content: attr(data-label); white-space: nowrap;
    }

    .actions { display: flex; gap: 10px; margin-bottom: 20px; }
    button {
        padding: 10px 24px; border: none; border-radius: 6px; font-size: 14px;
        font-weight: 600; cursor: pointer; transition: all 0.15s;
    }
    button:not(.secondary) { background: #238636; color: #fff; }
    button:not(.secondary):hover { background: #2ea043; }
    button.secondary { background: #21262d; color: #c9d1d9; border: 1px solid #30363d; }
    button.secondary:hover { background: #30363d; }

    .stats {
        display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px;
        margin-bottom: 20px;
    }
    .stat-card {
        background: #161b22; border: 1px solid #30363d; border-radius: 8px;
        padding: 16px; text-align: center;
    }
    .stat-value { font-size: 28px; font-weight: 700; font-variant-numeric: tabular-nums; }
    .stat-label { font-size: 12px; color: #8b949e; margin-top: 4px; text-transform: uppercase; letter-spacing: 0.5px; }
    .stat-card:nth-child(1) .stat-value { color: #f0883e; }
    .stat-card:nth-child(2) .stat-value { color: #3fb950; }
    .stat-card:nth-child(3) .stat-value { color: #f85149; }
    .stat-card:nth-child(4) .stat-value { color: #58a6ff; }
    .stat-card:nth-child(5) .stat-value { color: #bc8cff; }

    .chart-container {
        background: #161b22; border: 1px solid #30363d; border-radius: 8px;
        padding: 20px; height: 380px;
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
        max-height: 460px; overflow-y: auto;
    }
    .log-entry {
        padding: 2px 20px; display: flex; gap: 12px; align-items: baseline;
    }
    .log-entry:hover { background: #1c2128; }
    .log-time { color: #484f58; min-width: 60px; }
    .log-status { font-weight: 600; min-width: 32px; }
    .log-status.s2xx { color: #3fb950; }
    .log-status.s4xx { color: #f0883e; }
    .log-status.s5xx { color: #f85149; }
    .log-latency { color: #bc8cff; min-width: 55px; text-align: right; }
    .log-body { color: #8b949e; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    @media (max-width: 640px) {
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
                {
                    label: 'Queued',
                    data: [],
                    borderColor: '#f0883e',
                    backgroundColor: '#f0883e22',
                    fill: true,
                    tension: 0.3,
                    pointRadius: 0,
                    borderWidth: 2,
                },
                {
                    label: 'Throughput/s',
                    data: [],
                    borderColor: '#58a6ff',
                    backgroundColor: '#58a6ff22',
                    fill: false,
                    tension: 0.3,
                    pointRadius: 0,
                    borderWidth: 2,
                    yAxisID: 'y1',
                },
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
                    title: { display: true, text: 'Queued', color: '#8b949e' },
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

function getRate() {
    const slider = parseFloat(document.getElementById('rateSlider').value) || 0;
    return Math.max(1, sliderToRate(slider));
}

function getApiTarget() {
    return document.getElementById('apiTarget').value;
}

function getOverflowMode() {
    return document.getElementById('overflowMode').value;
}

function onTypeChange() {
    const type = document.getElementById('limiterType').value;
    document.querySelectorAll('.smooth-only').forEach(el => el.classList.toggle('hidden', type !== 'smooth'));
    document.querySelectorAll('.composite-only').forEach(el => el.classList.toggle('hidden', type !== 'composite'));
}

let configDebounce = null;
function onConfigChange() {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    clearTimeout(configDebounce);
    configDebounce = setTimeout(() => {
        lastCompleted = 0;
        lastTime = 0;
        ws.send(JSON.stringify({ action: 'updateRate', config: getConfig(), requestsPerSecond: getRate(), apiTarget: getApiTarget(), overflowMode: getOverflowMode() }));
    }, 300);
}

function onRateChange() {
    const rate = getRate();
    document.getElementById('rateValue').textContent = rate.toLocaleString() + ' req/s';
    onConfigChange();
}

function startSimulation() {
    if (ws) ws.close();
    lastCompleted = 0;
    lastTime = 0;

    const loc = window.location;
    ws = new WebSocket((loc.protocol === 'https:' ? 'wss:' : 'ws:') + '//' + loc.host + '/ws');

    ws.onopen = () => {
        ws.send(JSON.stringify({
            action: 'start',
            config: getConfig(),
            requestsPerSecond: getRate(),
            apiTarget: getApiTarget(),
            overflowMode: getOverflowMode(),
        }));
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
        // Skip first point after start/restart to avoid spike from initial burst
        const throughput = (lastTime > 0 && timeDelta > 0)
            ? (completedDelta / (timeDelta / 1000)).toFixed(1)
            : 0;
        lastCompleted = msg.completed;
        lastTime = msg.timeMs;

        // Update stats
        document.getElementById('statQueued').textContent = msg.queued;
        document.getElementById('statCompleted').textContent = msg.completed;
        document.getElementById('statDenied').textContent = msg.denied;
        document.getElementById('statThroughput').textContent = throughput;
        document.getElementById('statLatency').textContent = msg.avgLatencyMs + 'ms';

        // Update chart — keep last 150 points
        chart.data.labels.push(timeSec);
        chart.data.datasets[0].data.push(msg.queued);
        chart.data.datasets[1].data.push(parseFloat(throughput));

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
    document.getElementById('statQueued').textContent = '0';
    document.getElementById('statCompleted').textContent = '0';
    document.getElementById('statDenied').textContent = '0';
    document.getElementById('statThroughput').textContent = '0';
    document.getElementById('statLatency').textContent = '0ms';
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

    // Keep only last 20 entries
    while (log.children.length > 20) {
        log.removeChild(log.firstChild);
    }

    // Auto-scroll to bottom
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
});
""".trimIndent()
