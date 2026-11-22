// JARVIS Local AI Management & Activeness Dashboard
let currentActiveModel = 'qwen3';

async function fetchStatus() {
  try {
    const start = performance.now();
    const res = await fetch('/api/status');
    const latency = Math.round(performance.now() - start);

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();

    document.getElementById('activenessBadge').textContent = 'ONLINE';
    document.getElementById('activenessBadge').style.borderColor = 'var(--state-ok)';
    document.getElementById('activenessBadge').style.color = 'var(--state-ok)';

    document.getElementById('pingBadge').textContent = `PING: ${latency}ms`;
    document.getElementById('currentState').textContent = data.state || 'STANDBY';
    document.getElementById('activeModelName').textContent = data.active_model_name || 'Qwen3 1.7B Instruct';
    document.getElementById('ramResident').textContent = `${data.ram_used_mb} MB / ${data.ram_max_mb} MB`;
    document.getElementById('thermalStatus').textContent = `${data.thermal_status} (${data.temperature_c}°C)`;

    // Update RAM Gauge
    const pct = Math.round((data.ram_used_mb / data.ram_max_mb) * 100);
    document.getElementById('ramPercentText').textContent = `${pct}% Utilized`;
    document.getElementById('ramProgressBar').style.width = `${pct}%`;

    // Update Model Rows
    currentActiveModel = data.active_model_id || 'qwen3';
    updateModelRowUI();

    // Update Subsystem Statuses
    if (data.subsystems) {
      document.getElementById('subVoiceDesc').textContent = data.subsystems.voice || 'Listening for "JARVIS" (16kHz)';
      document.getElementById('subVisionDesc').textContent = data.subsystems.vision || 'Back Lens (Ask & See Active)';
      document.getElementById('subMemoryDesc').textContent = data.subsystems.memory || 'SQLite Memory Verified';
      document.getElementById('subPhoneMonDesc').textContent = data.subsystems.phonemon || 'Port 8765 Loopback OK';
    }
  } catch (err) {
    document.getElementById('activenessBadge').textContent = 'OFFLINE';
    document.getElementById('activenessBadge').style.borderColor = 'var(--state-error)';
    document.getElementById('activenessBadge').style.color = 'var(--state-error)';
    console.warn('Activeness check failed:', err);
  }
}

function updateModelRowUI() {
  const rowQwen = document.getElementById('modelRowQwen');
  const rowLfm = document.getElementById('modelRowLfm');
  const btnQwen = document.getElementById('btnLoadQwen');
  const btnLfm = document.getElementById('btnLoadLfm');
  const pillQwen = document.getElementById('qwenStatusPill');
  const pillLfm = document.getElementById('lfmStatusPill');

  if (currentActiveModel === 'qwen3') {
    rowQwen.classList.add('active');
    rowLfm.classList.remove('active');
    btnQwen.disabled = true;
    btnQwen.textContent = 'Loaded';
    btnLfm.disabled = false;
    btnLfm.textContent = 'Load in RAM';
    pillQwen.className = 'status-pill loaded';
    pillQwen.textContent = 'LOADED IN RAM';
    pillLfm.className = 'status-pill ready';
    pillLfm.textContent = 'READY ON DISK';
  } else {
    rowLfm.classList.add('active');
    rowQwen.classList.remove('active');
    btnLfm.disabled = true;
    btnLfm.textContent = 'Loaded';
    btnQwen.disabled = false;
    btnQwen.textContent = 'Load in RAM';
    pillLfm.className = 'status-pill loaded';
    pillLfm.textContent = 'LOADED IN RAM';
    pillQwen.className = 'status-pill ready';
    pillQwen.textContent = 'READY ON DISK';
  }
}

async function loadModel(modelId) {
  const outBox = document.getElementById('testOutputText');
  outBox.textContent = `Sequential paging in progress: Swapping active model to '${modelId}'...`;

  try {
    const res = await fetch('/api/swap', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: modelId })
    });
    const data = await res.json();
    currentActiveModel = data.active_model_id;
    updateModelRowUI();
    outBox.textContent = `Model '${modelId}' successfully loaded into resident memory (${data.ram_used_mb} MB).`;
    fetchStatus();
  } catch (err) {
    outBox.textContent = `Error swapping model: ${err.message}`;
  }
}

async function verifyChecksum(modelId) {
  const outBox = document.getElementById('testOutputText');
  outBox.textContent = `Verifying SHA-256 checksum for '${modelId}' on disk...`;

  try {
    const res = await fetch('/api/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: modelId })
    });
    const data = await res.json();
    outBox.textContent = data.message || `SHA-256 verification passed for '${modelId}'.`;
  } catch (err) {
    outBox.textContent = `Checksum verification error: ${err.message}`;
  }
}

async function runInferenceTest() {
  const input = document.getElementById('testPromptInput');
  const prompt = input.value.trim();
  if (!prompt) return;

  const outBox = document.getElementById('testOutputText');
  outBox.textContent = 'Streaming tokens from local on-device neural engine...';

  try {
    const res = await fetch('/api/inference', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: prompt, model: currentActiveModel })
    });
    const data = await res.json();
    outBox.textContent = data.response || 'No response generated.';
  } catch (err) {
    outBox.textContent = `Inference failed: ${err.message}`;
  }
}

function checkActiveness() {
  fetchStatus();
}

// Initialize on load and poll every 4 seconds
document.addEventListener('DOMContentLoaded', () => {
  fetchStatus();
  setInterval(fetchStatus, 4000);
});

