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

const overlay = document.getElementById('jarvisOverlay');
const trigger = document.getElementById('jarvisTrigger');
const wave = overlay.querySelector('.j-wave');
for (let i = 0; i < 24; i++) {
  const bar = document.createElement('i');
  bar.style.setProperty('--delay', (-i * .075) + 's');
  wave.append(bar);
}
function showAssistant(open) { overlay.hidden = !open; trigger.hidden = open; }
trigger.onclick = () => showAssistant(true);
document.getElementById('jClose').onclick = () => showAssistant(false);
let previousState = '';
async function refreshSession() {
  let session = {state: 'OFFLINE'};
  try {
    const response = await fetch('/api/status', {cache: 'no-store'});
    if (response.ok) session = await response.json();
  } finally {
    const state = session.state;
    const active = ['LISTENING','THINKING','VISION','EXECUTING','SPEAKING'].includes(state);
    if (active && previousState !== state) showAssistant(true);
    previousState = state;
    overlay.dataset.active = String(active);
    document.getElementById('activenessBadge').textContent = state === 'OFFLINE' ? 'PHONE OFFLINE' : 'CONNECTED';
    document.getElementById('assistantState').textContent = state;
    document.getElementById('jQuestion').hidden = !session.user;
    document.getElementById('jUser').textContent = session.user || '';
    document.getElementById('jSpeaker').textContent = document.getElementById('assistantName').textContent.toUpperCase();
    document.getElementById('assistantTranscript').textContent = session.text || (state === 'OFFLINE' ? 'Open PhoneMon on your paired phone.' : 'Ready when you are.');
    setTimeout(() => refreshSession().catch(console.warn), 700);
  }
}
refreshSession().catch(console.warn);
