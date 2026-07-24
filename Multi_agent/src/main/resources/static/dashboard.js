// AI Gateway Dashboard Client Logic

document.addEventListener('DOMContentLoaded', () => {
    initTabs();
    initPlayground();
    initCodeSnippets();
    initFilters();
    initCacheActions();

    // Initial Data Fetch
    refreshAllData();

    // Auto-refresh telemetry every 10 seconds
    setInterval(refreshAllData, 10000);

    document.getElementById('refresh-btn').addEventListener('click', () => {
        refreshAllData();
    });
});

// --- Tab Switching ---
function initTabs() {
    const tabs = document.querySelectorAll('.nav-tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            const targetTab = tab.getAttribute('data-tab');
            document.querySelectorAll('.tab-content').forEach(content => {
                content.classList.remove('active');
            });
            const activeSection = document.getElementById(`tab-${targetTab}`);
            if (activeSection) {
                activeSection.classList.add('active');
            }
        });
    });
}

// --- Data Fetching ---
async function refreshAllData() {
    await Promise.all([
        fetchStats(),
        fetchLogs(),
        fetchProviders()
    ]);
}

async function fetchStats() {
    try {
        const res = await fetch('/v1/dashboard/stats');
        if (!res.ok) throw new Error('Failed to fetch stats');
        const data = await res.json();

        document.getElementById('metric-total-requests').innerText = data.totalRequests || 0;
        document.getElementById('metric-cache-ratio').innerText = `${data.cacheHitRatioPercent || 0}%`;
        document.getElementById('metric-cache-hits').innerText = `${data.totalCacheHits || 0} Cache Hits`;
        document.getElementById('metric-avg-latency').innerText = `${data.avgLatencyMs || 0} ms`;
        document.getElementById('metric-saved-cost').innerText = `$${(data.totalSavedCostUsd || 0).toFixed(4)}`;
        document.getElementById('metric-total-cost').innerText = `Total Spend: $${(data.totalCostUsd || 0).toFixed(4)}`;
        document.getElementById('cache-tab-hit-ratio').innerText = `${data.cacheHitRatioPercent || 0}%`;

        renderProviderChart(data.requestsByProvider || {});
    } catch (err) {
        console.warn('Dashboard stats fallback mode:', err);
    }
}

async function fetchProviders() {
    try {
        const res = await fetch('/v1/dashboard/providers');
        if (!res.ok) throw new Error('Failed to fetch providers');
        const providers = await res.json();
        renderProvidersList(providers);
    } catch (err) {
        console.warn('Providers list error:', err);
    }
}

function renderProvidersList(providers) {
    const container = document.getElementById('providers-container');
    if (!container) return;

    if (!providers || providers.length === 0) {
        container.innerHTML = '<p class="text-muted">No providers registered.</p>';
        return;
    }

    container.innerHTML = providers.map(p => `
        <div class="provider-item">
            <div class="provider-info">
                <div class="provider-avatar">${p.displayName ? p.displayName.charAt(0) : 'P'}</div>
                <div>
                    <div class="provider-name">${p.displayName || p.name}</div>
                    <div class="provider-model">Default model: ${p.defaultModel}</div>
                </div>
            </div>
            <div class="badges-row">
                <span class="badge badge-emerald">● ${p.status}</span>
                <span class="badge badge-purple">CB: ${p.circuitBreaker}</span>
            </div>
        </div>
    `).join('');
}

function renderProviderChart(byProvider) {
    const container = document.getElementById('provider-chart');
    if (!container) return;

    const providers = ['openai', 'anthropic', 'gemini', 'ollama'];
    const total = Object.values(byProvider).reduce((a, b) => a + b, 0) || 1;

    container.innerHTML = providers.map(p => {
        const count = byProvider[p] || 0;
        const percent = Math.round((count / total) * 100);
        return `
            <div style="margin-bottom: 0.75rem;">
                <div style="display:flex; justify-content:space-between; font-size:0.8rem; margin-bottom:0.3rem;">
                    <span>${p.toUpperCase()}</span>
                    <span>${count} reqs (${percent}%)</span>
                </div>
                <div style="background:var(--bg-card); height:8px; border-radius:4px; overflow:hidden;">
                    <div style="width:${Math.max(percent, 2)}%; background:linear-gradient(90deg, var(--accent-purple), var(--accent-cyan)); height:100%;"></div>
                </div>
            </div>
        `;
    }).join('');
}

async function fetchLogs() {
    try {
        const provider = document.getElementById('log-filter-provider').value;
        const cached = document.getElementById('log-filter-cache').value;
        const search = document.getElementById('log-search-input').value;

        let query = [];
        if (provider && provider !== 'all') query.push(`provider=${encodeURIComponent(provider)}`);
        if (cached && cached !== 'all') query.push(`cached=${cached}`);
        if (search) query.push(`search=${encodeURIComponent(search)}`);

        const url = '/v1/dashboard/logs' + (query.length ? '?' + query.join('&') : '');
        const res = await fetch(url);
        if (!res.ok) throw new Error('Failed to fetch logs');
        const logs = await res.json();
        renderLogsTable(logs);
    } catch (err) {
        console.warn('Logs error:', err);
    }
}

function renderLogsTable(logs) {
    const tbody = document.getElementById('logs-tbody');
    if (!tbody) return;

    if (!logs || logs.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="10" class="text-center py-6 text-muted">No telemetry logs recorded yet. Send a request from the AI Playground!</td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = logs.map(l => `
        <tr>
            <td style="font-size:0.75rem; color:var(--text-muted);">${formatTime(l.timestamp)}</td>
            <td class="font-mono" style="font-size:0.75rem;">${l.id}</td>
            <td><span class="badge badge-purple">${l.provider}</span></td>
            <td class="font-mono" style="font-size:0.78rem;">${l.model}</td>
            <td style="max-width:220px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${escapeHtml(l.promptPreview || '')}</td>
            <td>${l.cached ? '<span class="badge badge-cyan">HIT</span>' : '<span class="badge badge-emerald">MISS</span>'}</td>
            <td class="font-mono">${l.latencyMs} ms</td>
            <td class="font-mono">${l.totalTokens}</td>
            <td class="font-mono">$${(l.estimatedCostUsd || 0).toFixed(6)}</td>
            <td><span class="badge ${l.status === 'ERROR' ? 'badge-rose' : (l.status === 'CACHE_HIT' ? 'badge-cyan' : 'badge-emerald')}">${l.status}</span></td>
        </tr>
    `).join('');
}

// --- AI Playground ---
function initPlayground() {
    const providerSelect = document.getElementById('pg-provider');
    const modelInput = document.getElementById('pg-model');
    const submitBtn = document.getElementById('pg-submit');

    const providerDefaultModels = {
        openai: 'gpt-4o-mini',
        anthropic: 'claude-3-5-sonnet',
        gemini: 'gemini-1.5-pro',
        ollama: 'llama3'
    };

    providerSelect.addEventListener('change', () => {
        modelInput.value = providerDefaultModels[providerSelect.value] || 'gpt-4o-mini';
    });

    submitBtn.addEventListener('click', async () => {
        const provider = providerSelect.value;
        const model = modelInput.value;
        const system = document.getElementById('pg-system').value.trim();
        const userPrompt = document.getElementById('pg-prompt').value.trim();

        if (!userPrompt) {
            alert('Please enter a user prompt.');
            return;
        }

        const messages = [];
        if (system) {
            messages.push({ role: 'system', content: system });
        }
        messages.push({ role: 'user', content: userPrompt });

        const payload = {
            provider: provider,
            model: model,
            messages: messages
        };

        const responseBox = document.getElementById('pg-response-box');
        const jsonBox = document.getElementById('pg-json-box');
        const badgesRow = document.getElementById('pg-badges');

        responseBox.innerHTML = '<span class="text-cyan">Routing request through AI Inference Gateway... ⚡</span>';
        jsonBox.innerText = 'Loading...';
        submitBtn.disabled = true;

        const startTime = Date.now();

        try {
            const res = await fetch('/v1/chat/completions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const latency = Date.now() - startTime;
            const data = await res.json();

            if (res.ok) {
                responseBox.innerText = data.content || 'Empty response content';
                jsonBox.innerText = JSON.stringify(data, null, 2);

                const cachedBadge = data.cached ? 
                    '<span class="badge badge-cyan">Cache HIT</span>' : 
                    '<span class="badge badge-emerald">Live API Call</span>';

                badgesRow.innerHTML = `
                    <span class="badge badge-purple">${data.provider || provider}</span>
                    <span class="badge badge-amber">${latency} ms</span>
                    ${cachedBadge}
                    <span class="badge badge-emerald">${data.usage ? data.usage.totalTokens : 0} tokens</span>
                `;
            } else {
                responseBox.innerHTML = `<span class="text-rose">Gateway Error: ${data.message || 'Provider request failed'}</span>`;
                jsonBox.innerText = JSON.stringify(data, null, 2);
            }
        } catch (err) {
            const latency = Date.now() - startTime;
            responseBox.innerHTML = `<span class="text-rose">Error connecting to Gateway backend: ${err.message}</span>`;
            jsonBox.innerText = JSON.stringify({ error: err.message }, null, 2);
        } finally {
            submitBtn.disabled = false;
            // Immediate Telemetry Refresh
            refreshAllData();
        }
    });
}

// --- Filters ---
function initFilters() {
    const pFilter = document.getElementById('log-filter-provider');
    const cFilter = document.getElementById('log-filter-cache');
    const sInput = document.getElementById('log-search-input');

    pFilter.addEventListener('change', fetchLogs);
    cFilter.addEventListener('change', fetchLogs);
    let debounceTimer;
    sInput.addEventListener('input', () => {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(fetchLogs, 300);
    });
}

// --- Cache Actions ---
function initCacheActions() {
    const clearBtn = document.getElementById('clear-cache-btn');
    clearBtn.addEventListener('click', async () => {
        if (confirm('Are you sure you want to purge the Semantic Cache?')) {
            try {
                const res = await fetch('/v1/dashboard/cache/clear', { method: 'POST' });
                const data = await res.json();
                alert(data.message || 'Cache purged!');
                refreshAllData();
            } catch (err) {
                alert('Failed to clear cache: ' + err.message);
            }
        }
    });
}

// --- Code Generator ---
function initCodeSnippets() {
    const tabs = document.querySelectorAll('.code-tab');
    const codeBox = document.getElementById('code-snippet-box');

    const snippets = {
        curl: `curl -X POST http://localhost:8080/v1/chat/completions \\
  -H "Content-Type: application/json" \\
  -d '{
    "provider": "openai",
    "model": "gpt-4o-mini",
    "messages": [
      { "role": "system", "content": "You are a helpful AI software engineer." },
      { "role": "user", "content": "Write a quicksort in Java." }
    ]
  }'`,
        python: `import openai

# Point OpenAI SDK to self-hosted AI Gateway
client = openai.OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="gateway-dummy-key"
)

response = client.chat.completions.create(
    model="gpt-4o-mini",
    extra_body={"provider": "openai"},
    messages=[
        {"role": "system", "content": "You are an AI assistant."},
        {"role": "user", "content": "Hello AI Gateway!"}
    ]
)

print(response.choices[0].message.content)`,
        node: `import OpenAI from 'openai';

const client = new OpenAI({
  baseURL: 'http://localhost:8080/v1',
  apiKey: 'gateway-dummy-key',
});

async function main() {
  const response = await client.chat.completions.create({
    model: 'gpt-4o-mini',
    // Custom body extension for gateway provider selection
    provider: 'openai',
    messages: [{ role: 'user', content: 'Explain Resilience4j circuit breakers' }],
  });

  console.log(response.choices[0].message.content);
}

main();`
    };

    codeBox.innerText = snippets.curl;

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            const lang = tab.getAttribute('data-lang');
            codeBox.innerText = snippets[lang] || '';
        });
    });
}

// --- Utilities ---
function formatTime(isoStr) {
    if (!isoStr) return '';
    try {
        const d = new Date(isoStr);
        return d.toLocaleTimeString();
    } catch (e) {
        return isoStr;
    }
}

function escapeHtml(str) {
    return str
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
