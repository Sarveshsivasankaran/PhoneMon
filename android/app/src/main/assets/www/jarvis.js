export function initPanelAssistant(bridge) {
  const el = id => document.getElementById(id);
  const overlay = el('jarvisOverlay');
  const trigger = el('jarvisTrigger');
  const card = overlay.querySelector('.j-card');
  const wake = el('jWake');
  let session = {}, mode = '', speechId = 0, partial = '', fault = '';
  let wakeTimer, collapseTimer, lastTurn = '', pending = '', foreground = !document.hidden;
  wake.checked = localStorage.getItem('phoneMonAssistantWake') === 'on';
  for (const wave of document.querySelectorAll('#jarvisTrigger .j-wave, #jarvisOverlay .j-wave')) {
    [4, 9, 14, 7, 17, 11, 5].forEach((height, i) => {
      const bar = document.createElement('i');
      bar.style.setProperty('--bar', height + 'px');
      bar.style.setProperty('--delay', -i * .095 + 's');
      wave.append(bar);
    });
  }
  function text(id, value) {
    if (el(id).textContent !== value) el(id).textContent = value;
  }
  function call(op, arg = '') {
    if (!bridge?.jarvis) return {error:'Open PhoneMon on the paired phone to speak.'};
    try { return JSON.parse(bridge.jarvis(op, arg)); }
    catch (_) { return {error:'The phone assistant is unavailable.'}; }
  }
  function render(next) {
    session = next;
    const name = next.name || 'JARVIS';
    const state = next.state || 'OFFLINE';
    const listening = mode === 'command';
    const active = listening || ['THINKING','VISION','SPEAKING','EXECUTING'].includes(state);
    const label = listening ? 'detected' : ({THINKING:'processing',VISION:'looking',EXECUTING:'working',SPEAKING:'speaking',OFFLINE:'offline'})[state] || 'Ready';
    overlay.dataset.active = String(active);
    text('jName',name); text('jSpeaker',name); text('jState',label);
    text('jPillText',name + ' ' + label);
    trigger.setAttribute('aria-label','Talk to ' + name);
    const heard = listening ? partial : next.user;
    el('jQuestion').hidden = !heard;
    text('jUser',heard || '');
    const progress = {THINKING:'Processing…',VISION:'Analyzing the captured frame…',EXECUTING:'Running the action…'};
    text('jReply',next.error || fault || (listening ? 'Listening…' : progress[state] || next.text || 'Tap Speak to ask a question.'));
    pending = next.pending || '';
    el('jConfirm').hidden = !pending;
    let action = '';
    if (pending) {
      try {
        const p = JSON.parse(pending);
        action = [p.tool.replace(/_/g,' '), ...Object.values(p.parameters || {})].join(' · ');
      } catch (_) { action = pending; }
    }
    text('jAction',action);
    const image = next.thumbnail || '';
    if (image && el('jThumbnail').getAttribute('src') !== image) el('jThumbnail').src = image;
    if (!image) el('jThumbnail').removeAttribute('src');
    el('jCapture').hidden = !image;
    text('jDownload',next.setup || '');
    el('jChat').disabled = el('jVision').disabled = !!next.installing || !!next.error;
    const busy = active && !listening || !!pending || !!next.error;
    el('jCamera').disabled = el('jSample').disabled = busy || listening;
    el('jVoice').disabled = busy;
    text('jVoice',listening ? 'Finish listening' : 'Speak');
    const turn = [state,next.user,next.text,pending].join('\n');
    if (turn !== lastTurn) {
      lastTurn = turn; clearTimeout(collapseTimer);
      if (!overlay.hidden && !listening && !pending && !fault && next.text && state === 'STANDBY') {
        collapseTimer = setTimeout(() => { if (card.querySelector('.j-setup').hidden && !mode) close(); }, 15000);
      }
    }
  }
  function open() {
    clearTimeout(collapseTimer);
    overlay.hidden = false; trigger.hidden = true;
    trigger.setAttribute('aria-expanded','true'); render(call('status'));
  }
  function close() {
    clearTimeout(collapseTimer); cancelSpeech();
    if (session.thumbnail) call('clearThumbnail');
    el('jThumbnail').removeAttribute('src');
    card.querySelector('.j-setup').hidden = true;
    el('jSettings').setAttribute('aria-expanded','false');
    overlay.hidden = true; trigger.hidden = false;
    trigger.setAttribute('aria-expanded','false');
    trigger.focus({preventScroll:true}); scheduleWake();
  }
  function cancelSpeech() {
    clearTimeout(wakeTimer);
    const id = speechId;
    speechId = 0; mode = ''; partial = '';
    if (id) bridge?.cancelListening?.(id);
    if (session.state === 'LISTENING') render(call('idle'));
  }
  function listen(nextMode) {
    cancelSpeech(); clearTimeout(collapseTimer);
    if (!foreground || document.hidden) return;
    if (!bridge?.jarvisListen || session.error) {
      fault = session.error || 'Open PhoneMon on the paired phone to speak.';
      render(session); return;
    }
    fault = ''; mode = nextMode;
    speechId = bridge.jarvisListen();
    if (!speechId) { mode = ''; fault = 'Microphone unavailable.'; }
    render(mode === 'command' ? call('listening') : session);
  }
  function scheduleWake() {
    clearTimeout(wakeTimer);
    if (!wake.checked || !foreground || document.hidden) return;
    wakeTimer = setTimeout(() => {
      if (!speechId && !session.pending && !session.error && session.state === 'STANDBY') listen('wake');
      else scheduleWake();
    }, 850);
  }
  function send(value) {
    value = value.trim().slice(0,2000);
    if (!value) { render(call('idle')); return; }
    fault = ''; render(call('voice',value));
  }
  const previousSpeech = window.onAndroidSpeech;
  window.onAndroidSpeech = (kind, heard, id) => {
    previousSpeech?.(kind,heard,id);
    if (!speechId || id !== speechId) return;
    if (kind === 'partial') {
      if (mode === 'command') {partial = heard; render(session);}
      return;
    }
    if (kind !== 'final' && kind !== 'error') return;
    const was = mode;
    speechId = 0; mode = ''; partial = '';
    if (kind === 'error') {
      if (heard !== 'cancelled') {
        fault = /^permission/.test(heard) ? 'Allow microphone access in Android settings to speak.' : 'Voice input failed. ' + heard;
        if (was === 'wake') {wake.checked = false; localStorage.removeItem('phoneMonAssistantWake'); open();}
      }
      render(call('idle'));
    } else if (was === 'wake') {
      const match = /\bjarvis\b[\s,:.!-]*(.*)/i.exec(heard);
      if (match) { open(); if (match[1]) send(match[1]); else { listen('command'); return; } }
    } else {
      if (!heard.trim()) fault = 'No speech heard. Tap Speak to try again.';
      send(heard);
    }
    scheduleWake();
  };
  const activate = () => { open(); if (!el('jVoice').disabled) listen('command'); };
  window.openJarvisHUD = activate; window.closeJarvisHUD = close;
  window.toggleJarvisHUD = () => overlay.hidden ? activate() : close();
  trigger.onclick = activate; el('jClose').onclick = close;
  card.addEventListener('pointerdown',e => e.stopPropagation());
  card.addEventListener('touchstart',e => e.stopPropagation(),{passive:true});
  el('jVoice').onclick = () => mode === 'command' ? bridge.stopListening(speechId) : listen('command');
  el('jCamera').onclick = () => { cancelSpeech(); fault = ''; call('camera'); };
  el('jApprove').onclick = () => render(call('approve',pending));
  el('jDeny').onclick = () => render(call('deny',pending));
  el('jSettings').onclick = () => {
    clearTimeout(collapseTimer);
    const setup = card.querySelector('.j-setup');
    setup.hidden = !setup.hidden;
    el('jSettings').setAttribute('aria-expanded',String(!setup.hidden));
  };
  el('jChat').onclick = () => render(call('install','qwen2.5-0.5b'));
  el('jVision').onclick = () => render(call('install','lfm2.5-vl-450m'));
  el('jSample').onclick = () => { cancelSpeech(); render(call('previewVoice')); scheduleWake(); };
  wake.onchange = () => {
    localStorage.setItem('phoneMonAssistantWake',wake.checked ? 'on' : 'off');
    if (wake.checked) scheduleWake(); else if (mode === 'wake') cancelSpeech();
  };
  const previousPause = window.onAppPause;
  window.onAppPause = paused => {
    previousPause?.(paused); foreground = !paused;
    if (paused) clearTimeout(wakeTimer); else scheduleWake();
  };
  document.addEventListener('visibilitychange',() => {
    foreground = !document.hidden;
    if (foreground) scheduleWake(); else clearTimeout(wakeTimer);
  });
  function poll() {
    if (foreground && !document.hidden && bridge?.jarvis) render(call('status'));
    setTimeout(poll, overlay.hidden && !wake.checked ? 3500 : 550);
  }
  if (call('openOnLaunch').open) activate();
  poll(); scheduleWake();
}
