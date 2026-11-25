export function initPanelAssistant(bridge) {
  const el = id => document.getElementById(id);
  const overlay = el('jarvisOverlay');
  const trigger = el('jarvisTrigger');
  const card = overlay.querySelector('.j-card');
  const wake = el('jWake');
  const voicePick = el('jVoicePick');
  let session = {}, mode = '', speechId = 0, partial = '', fault = '', localNote = '';
  let wakeTimer, collapseTimer, followUpTimer, lastTurn = '', pending = '', foreground = !document.hidden;
  let conversing = false, wakePopped = false, prevState = 'STANDBY';
  const savedWake = localStorage.getItem('phoneMonAssistantWake');
  wake.checked = savedWake === 'on' || (savedWake !== 'off' && !!bridge?.hasMicPermission?.());
  for (const wave of document.querySelectorAll('#jarvisOverlay .j-wave')) {
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
  function matchWake(heard) {
    const clean = (heard || '').trim();
    if (!clean) return null;
    const rawName = (session.name || 'JARVIS').trim();
    const esc = rawName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const nameGroup = rawName && rawName.toLowerCase() !== 'jarvis'
      ? `${esc}|jarvis|jar\\s+vis|jervis|jarves|jarvas|jarvice|charvis|garvis`
      : 'jarvis|jar\\s+vis|jervis|jarves|jarvas|jarvice|charvis|garvis';
    const re = new RegExp(`\\b(?:hey\\s+|okay?\\s+|ok\\s+|hi\\s+)?(?:${nameGroup})\\b[\\s,:.!-]*(.*)`, 'i');
    const m = re.exec(clean);
    return m ? (m[1] || '').trim() : null;
  }
  function isStopPhrase(heard) {
    const clean = (heard || '').trim().toLowerCase().replace(/[.!?,]+$/g, '')
      .replace(/^(?:hey|ok|okay|please)\s+/i, '')
      .replace(/^(?:jarvis|jar\s+vis|jervis)\s+/i, '')
      .replace(/\s+(?:jarvis|please|now)$/i, '').trim();
    return /^(?:stop(?:\s+listening)?|quiet|silence|stand\s*down|go\s+to\s+sleep|sleep|never\s*mind|nevermind|that'?s\s+all|i'?m\s+done|nothing(?:\s+else)?|no\s+thanks?|cancel|close|dismiss|bye|goodbye|thank\s*you|thanks)$/.test(clean);
  }
  function stopConversation(shouldClose = false, note = 'Standing by. Say “Jarvis” when you need me.') {
    clearTimeout(followUpTimer);
    clearTimeout(collapseTimer);
    conversing = false; wakePopped = false;
    cancelSpeech();
    call('stopSpeech');
    if (shouldClose) { close(); return; }
    localNote = note;
    render(call('idle'));
    collapseTimer = setTimeout(() => {
      if (!conversing && !mode && !pending && card.querySelector('.j-setup').hidden) close();
    }, 3500);
    scheduleWake();
  }
  function render(next) {
    session = next;
    const name = next.name || 'JARVIS';
    const state = next.state || 'OFFLINE';
    const listening = mode === 'command' || wakePopped;
    const active = listening || ['THINKING','VISION','SPEAKING','EXECUTING'].includes(state);
    const label = listening ? 'detected' : ({THINKING:'processing',VISION:'looking',EXECUTING:'working',SPEAKING:'speaking',OFFLINE:'offline'})[state] || 'Ready';
    overlay.dataset.active = String(active);
    text('jName',name); text('jSpeaker',name); text('jState',label);
    trigger.dataset.state = state;
    trigger.setAttribute('title',name + ' ' + label);
    trigger.setAttribute('aria-label','Talk to ' + name);
    if (next.voice && voicePick && document.activeElement !== voicePick && voicePick.value !== next.voice) {
      voicePick.value = next.voice;
    }
    const heard = listening ? partial : next.user;
    el('jQuestion').hidden = !heard;
    text('jUser',heard || '');
    const progress = {THINKING:'Processing…',VISION:'Analyzing the captured frame…',EXECUTING:'Running the action…'};
    text('jReply', next.error || fault || (listening ? (next.text || 'Listening…') : localNote || progress[state] || next.text || 'Tap Speak to ask a question.'));
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
    const busy = (active && !listening) || !!pending || !!next.error;
    el('jCamera').disabled = el('jSample').disabled = busy || listening;
    el('jVoice').disabled = busy;
    text('jVoice',listening ? 'Finish listening' : 'Speak');
    const finishedTurn = ['THINKING', 'VISION', 'EXECUTING', 'SPEAKING'].includes(prevState) && state === 'STANDBY';
    prevState = state;
    if (finishedTurn && conversing && !overlay.hidden && !mode && !speechId && !pending && !fault && !next.error && card.querySelector('.j-setup').hidden) {
      clearTimeout(followUpTimer);
      followUpTimer = setTimeout(() => {
        if (conversing && !overlay.hidden && !mode && !speechId && !pending && session.state === 'STANDBY') {
          listen('command');
        }
      }, 550);
    }
    const turn = [state,next.user,next.text,pending].join('\n');
    if (turn !== lastTurn) {
      lastTurn = turn; clearTimeout(collapseTimer);
      if (!overlay.hidden && !conversing && !listening && !pending && !fault && !localNote && next.text && state === 'STANDBY') {
        collapseTimer = setTimeout(() => { if (card.querySelector('.j-setup').hidden && !mode && !conversing) close(); }, 15000);
      }
    }
  }
  function open() {
    clearTimeout(collapseTimer);
    localNote = '';
    window.onJarvisOpen?.();
    overlay.hidden = false; trigger.hidden = true;
    trigger.setAttribute('aria-expanded','true'); render(call('status'));
  }
  function close() {
    clearTimeout(collapseTimer); clearTimeout(followUpTimer);
    conversing = false; wakePopped = false; localNote = '';
    cancelSpeech();
    if (session.thumbnail) call('clearThumbnail');
    el('jThumbnail').removeAttribute('src');
    card.querySelector('.j-setup').hidden = true;
    el('jSettings').setAttribute('aria-expanded','false');
    overlay.hidden = true; trigger.hidden = false;
    trigger.setAttribute('aria-expanded','false');
    trigger.focus({preventScroll:true}); scheduleWake();
  }
  function cancelSpeech() {
    clearTimeout(wakeTimer); clearTimeout(followUpTimer);
    const id = speechId;
    speechId = 0; mode = ''; partial = ''; wakePopped = false;
    if (id) bridge?.cancelListening?.(id);
    if (session.state === 'LISTENING') render(call('idle'));
  }
  function listen(nextMode) {
    cancelSpeech(); clearTimeout(collapseTimer);
    if (!foreground || document.hidden) return;
    const starter = nextMode === 'wake' ? (bridge?.jarvisWakeListen || bridge?.jarvisListen) : bridge?.jarvisListen;
    if (!starter || session.error) {
      fault = session.error || 'Open PhoneMon on the paired phone to speak.';
      conversing = false;
      render(session); return;
    }
    fault = ''; localNote = ''; mode = nextMode;
    speechId = starter();
    if (!speechId) { mode = ''; conversing = false; fault = 'Microphone unavailable.'; }
    render(mode === 'command' ? call('listening') : session);
  }
  function scheduleWake() {
    clearTimeout(wakeTimer);
    if (!wake.checked || !foreground || document.hidden) return;
    wakeTimer = setTimeout(() => {
      if (!speechId && !mode && !conversing && !session.pending && !session.error && session.state === 'STANDBY') listen('wake');
      else scheduleWake();
    }, 850);
  }
  function send(value) {
    value = value.trim().slice(0,2000);
    if (!value) { render(call('idle')); return; }
    fault = ''; localNote = ''; render(call('voice',value));
  }
  const previousSpeech = window.onAndroidSpeech;
  window.onAndroidSpeech = (kind, heard, id) => {
    previousSpeech?.(kind,heard,id);
    if (!speechId || id !== speechId) return;
    if (kind === 'partial') {
      if (mode === 'command') { partial = heard; render(session); }
      else if (mode === 'wake') {
        const after = matchWake(heard);
        if (after !== null) {
          wakePopped = true; conversing = true; localNote = '';
          if (overlay.hidden) open();
          partial = after;
          render(session.state === 'STANDBY' ? call('listening') : session);
        } else if (wakePopped) {
          partial = heard; render(session);
        }
      }
      return;
    }
    if (kind !== 'final' && kind !== 'error') return;
    const was = mode, wasWakePopped = wakePopped;
    speechId = 0; mode = ''; partial = ''; wakePopped = false;
    if (kind === 'error') {
      conversing = false;
      if (heard !== 'cancelled') {
        fault = /^permission/.test(heard) ? 'Allow microphone access in Android settings to speak.' : 'Voice input failed. ' + heard;
        if (was === 'wake') {wake.checked = false; localStorage.setItem('phoneMonAssistantWake','off'); open();}
      }
      render(call('idle'));
    } else if (was === 'wake') {
      const after = matchWake(heard);
      if (after !== null) {
        if (localStorage.getItem('phoneMonAssistantWake') === null) {
          wake.checked = true; localStorage.setItem('phoneMonAssistantWake', 'on');
        }
        conversing = true;
        if (overlay.hidden) open();
        if (after) {
          const closeWords = /^(?:close|dismiss|hide|shut\s+down)(?:\s+jarvis)?$/i.test(after.trim());
          if (isStopPhrase(after)) { stopConversation(closeWords); return; }
          send(after);
        } else {
          listen('command'); return;
        }
      } else if (wasWakePopped) {
        conversing = true;
        if (overlay.hidden) open();
        listen('command'); return;
      }
    } else {
      const clean = (heard || '').trim();
      if (clean && localStorage.getItem('phoneMonAssistantWake') === null) {
        wake.checked = true; localStorage.setItem('phoneMonAssistantWake', 'on');
      }
      if (!clean) {
        conversing = false;
        fault = 'No speech heard. Say “Jarvis” or tap Speak.';
        send('');
      } else if (isStopPhrase(clean)) {
        const closeWords = /^(?:(?:jarvis|hey\s+jarvis|ok\s+jarvis)[,\s]*)?(?:close|dismiss|hide)(?:\s+jarvis)?[.!?]*$/i.test(clean);
        stopConversation(closeWords);
        return;
      } else {
        conversing = true;
        send(clean);
      }
    }
    scheduleWake();
  };
  const activate = () => { conversing = true; open(); if (!el('jVoice').disabled) listen('command'); };
  window.openJarvisHUD = activate; window.closeJarvisHUD = close;
  window.toggleJarvisHUD = () => overlay.hidden ? activate() : close();
  trigger.onclick = activate; el('jClose').onclick = close;
  card.addEventListener('pointerdown',e => e.stopPropagation());
  card.addEventListener('touchstart',e => e.stopPropagation(),{passive:true});
  el('jVoice').onclick = () => mode === 'command' ? bridge.stopListening(speechId) : activate();
  el('jCamera').onclick = () => { clearTimeout(followUpTimer); cancelSpeech(); fault = ''; localNote = ''; conversing = true; call('camera'); };
  el('jApprove').onclick = () => render(call('approve',pending));
  el('jDeny').onclick = () => render(call('deny',pending));
  el('jSettings').onclick = () => {
    clearTimeout(collapseTimer); clearTimeout(followUpTimer);
    const setup = card.querySelector('.j-setup');
    setup.hidden = !setup.hidden;
    el('jSettings').setAttribute('aria-expanded',String(!setup.hidden));
  };
  if (voicePick) {
    voicePick.onchange = () => { render(call('setVoice', voicePick.value)); };
  }
  el('jChat').onclick = () => render(call('install','qwen2.5-0.5b'));
  el('jVision').onclick = () => render(call('install','lfm2.5-vl-450m'));
  el('jSample').onclick = () => { clearTimeout(followUpTimer); conversing = false; cancelSpeech(); render(call('previewVoice', voicePick?.value || '')); scheduleWake(); };
  wake.onchange = () => {
    localStorage.setItem('phoneMonAssistantWake',wake.checked ? 'on' : 'off');
    if (wake.checked) scheduleWake(); else if (mode === 'wake') cancelSpeech();
  };
  const previousPause = window.onAppPause;
  window.onAppPause = paused => {
    previousPause?.(paused); foreground = !paused;
    if (paused) { clearTimeout(wakeTimer); clearTimeout(followUpTimer); }
    else scheduleWake();
  };
  document.addEventListener('visibilitychange',() => {
    foreground = !document.hidden;
    if (foreground) scheduleWake(); else { clearTimeout(wakeTimer); clearTimeout(followUpTimer); }
  });
  function poll() {
    if (foreground && !document.hidden && bridge?.jarvis) render(call('status'));
    setTimeout(poll, overlay.hidden && !wake.checked ? 3500 : 550);
  }
  if (call('openOnLaunch').open) activate();
  poll(); scheduleWake();
}
