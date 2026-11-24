export function initPanelAssistant(bridge) {
  const byId = id => document.getElementById(id);
  const overlay = byId('jarvisOverlay');
  const trigger = byId('jarvisTrigger');
  const card = overlay.querySelector('.j-card');
  const wake = byId('jWake');
  let session = {};
  let speechId = 0;
  let listeningFor = '';
  let nextWake;
  let fault = '';
  let pending = '';
  wake.checked = localStorage.getItem('phoneMonAssistantWake') === 'on';
  for (let i = 0; i < 24; i++) {
    const bar = document.createElement('i');
    bar.style.setProperty('--delay', (-i * .075) + 's');
    overlay.querySelector('.j-wave').append(bar);
  }
  function call(op, arg = '') {
    if (!bridge?.jarvis) return {error:'Open PhoneMon on your phone to use the local assistant.'};
    try { return JSON.parse(bridge.jarvis(op, arg)); }
    catch (_) { return {error:'The phone assistant is unavailable.'}; }
  }
  function render(next) {
    session = next;
    const state = next.state || 'OFFLINE';
    const name = next.name || 'JARVIS';
    const active = ['THINKING','VISION','SPEAKING','EXECUTING','LISTENING'].includes(state);
    overlay.dataset.active = String(active);
    byId('jName').textContent = name;
    byId('jSpeaker').textContent = name.toUpperCase();
    byId('jState').textContent = next.pending ? 'CONFIRM' : state;
    byId('jQuestion').hidden = !next.user;
    byId('jUser').textContent = next.user || '';
    byId('jReply').textContent = next.error || fault || next.text || (state === 'LISTENING' ? 'Listening…' : state === 'THINKING' ? 'Thinking…' : 'How can I help?');
    pending = next.pending || '';
    byId('jConfirm').hidden = !pending;
    byId('jAction').textContent = pending;
    byId('jDownload').textContent = next.setup || '';
    byId('jChat').disabled = byId('jVision').disabled = !!next.installing || !!next.error;
    const busy = ['THINKING','VISION','EXECUTING'].includes(state) || !!pending || !!next.error;
    byId('jSend').disabled = byId('jCamera').disabled = byId('jVoice').disabled = busy;
  }
  function open() {
    overlay.hidden = false;
    trigger.hidden = true;
    trigger.setAttribute('aria-expanded','true');
    render(call('status'));
  }
  function close() {
    overlay.hidden = true;
    trigger.hidden = false;
    trigger.setAttribute('aria-expanded','false');
    if (listeningFor === 'command') stopListening();
    trigger.focus();
  }
  function stopListening() {
    clearTimeout(nextWake);
    if (speechId) bridge?.cancelListening?.(speechId);
    speechId = 0;
    listeningFor = '';
    if (session.state === 'LISTENING') render(call('idle'));
  }
  function listen(mode) {
    if (document.hidden || !bridge?.listen || !bridge.canListen?.()) {
      fault = 'On-device speech recognition is unavailable. Type your question.';
      render(call('status')); return;
    }
    stopListening();
    listeningFor = mode;
    speechId = bridge.listen('en-US', true);
    if (mode === 'command') render(call('listening'));
  }
  function scheduleWake() {
    clearTimeout(nextWake);
    if (wake.checked && !document.hidden) nextWake = setTimeout(() => {
      if (!speechId && !session.pending && !['SPEAKING','THINKING','VISION','EXECUTING'].includes(session.state)) listen('wake');
      else scheduleWake();
    }, 1200);
  }
  function send(text, voice) {
    const value = text.trim().slice(0,2000);
    if (!value) return;
    stopListening(); fault = '';
    byId('jPrompt').value = '';
    render(call(voice ? 'voice' : 'text', value));
    scheduleWake();
  }
  const previousSpeech = window.onAndroidSpeech;
  window.onAndroidSpeech = (kind, text, id) => {
    previousSpeech?.(kind, text, id);
    if (!speechId || id !== speechId) return;
    if (kind !== 'final' && kind !== 'error') return;
    const mode = listeningFor;
    speechId = 0; listeningFor = '';
    if (kind === 'final' && mode === 'wake') {
      const match = /\bjarvis\b[\s,:.!-]*(.*)/i.exec(text);
      if (match) {
        open();
        if (match[1]) send(match[1], true);
        else { listen('command'); return; }
      }
    } else if (kind === 'final' && text) send(text, true);
    else if (kind === 'error' && /permission|offline|unavailable/i.test(text)) {
      fault = 'On-device speech recognition is unavailable. Type your question.';
      wake.checked = false; localStorage.removeItem('phoneMonAssistantWake');
    }
    if (mode === 'command' && kind === 'error') render(call('idle'));
    scheduleWake();
  };
  window.openJarvisHUD = open; window.closeJarvisHUD = close;
  window.toggleJarvisHUD = () => overlay.hidden ? open() : close();
  trigger.onclick = open; byId('jClose').onclick = close;
  card.addEventListener('pointerdown', e => e.stopPropagation());
  card.addEventListener('touchstart', e => e.stopPropagation(), {passive:true});
  card.querySelector('form').onsubmit = e => {e.preventDefault();send(byId('jPrompt').value, false);};
  byId('jVoice').onclick = () => { fault = ''; listen('command'); };
  byId('jCamera').onclick = () => { stopListening(); fault = ''; call('camera'); };
  byId('jApprove').onclick = () => render(call('approve',pending));
  byId('jDeny').onclick = () => render(call('deny',pending));
  byId('jSettings').onclick = () => {
    const setup = card.querySelector('.j-setup');
    setup.hidden = !setup.hidden;
    byId('jSettings').setAttribute('aria-expanded',String(!setup.hidden));
  };
  byId('jChat').onclick = () => render(call('install','qwen2.5-0.5b'));
  byId('jVision').onclick = () => render(call('install','lfm2.5-vl-450m'));
  wake.onchange = () => {
    localStorage.setItem('phoneMonAssistantWake',wake.checked ? 'on' : 'off');
    if (wake.checked) listen('wake'); else stopListening();
  };
  document.addEventListener('visibilitychange', () => document.hidden ? stopListening() : scheduleWake());
  function poll() {
    if (!document.hidden && bridge?.jarvis) render(call('status'));
    setTimeout(poll, overlay.hidden ? 3500 : 550);
  }
  if (call('openOnLaunch').open) open();
  poll(); scheduleWake();
}
