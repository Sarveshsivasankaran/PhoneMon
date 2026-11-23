const profileSync = document.getElementById('profileSync');

async function loadProfile() {
  const response = await fetch('/api/profile');
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const profile = await response.json();
  document.getElementById('profileName').value = profile.assistantName;
  document.getElementById('assistantName').textContent = profile.assistantName;
  document.getElementById('profilePersonality').value = profile.personality;
  document.getElementById('profileModel').value = profile.model;
}

document.getElementById('saveProfile').addEventListener('click', async () => {
  profileSync.textContent = 'Saving…';
  try {
    const response = await fetch('/api/profile', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        assistantName: document.getElementById('profileName').value,
        personality: document.getElementById('profilePersonality').value,
        model: document.getElementById('profileModel').value
      })
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const profile = await response.json();
    document.getElementById('assistantName').textContent = profile.assistantName;
    profileSync.textContent = 'Saved on this computer';
  } catch (error) {
    profileSync.textContent = `Save failed: ${error.message}`;
  }
});

loadProfile().catch(error => { profileSync.textContent = `Profile unavailable: ${error.message}`; });

async function refreshSession() {
  let session = {state: 'OFFLINE'};
  try {
    const response = await fetch('/api/status', {cache: 'no-store'});
    if (response.ok) session = await response.json();
  } finally {
    const state = session.state;
    document.getElementById('activenessBadge').textContent = state === 'OFFLINE' ? 'PHONE OFFLINE' : 'CONNECTED';
    document.getElementById('assistantState').textContent = state;
    document.getElementById('previewAgent').dataset.state = state;
    document.getElementById('previewEdge').classList.toggle('active', ['LISTENING','THINKING','VISION','SPEAKING','EXECUTING'].includes(state));
    document.getElementById('assistantTranscript').textContent = session.text || (state === 'OFFLINE' ? 'Open the assistant on your paired phone.' : 'Ready when you are.');
    setTimeout(() => refreshSession().catch(console.warn), 1000);
  }
}
refreshSession().catch(console.warn);
