const { test, expect } = require('@playwright/test');

test.describe('JARVIS continuous wake, voice selector, and night clock', () => {
  test('wake word pops overlay at bottom-center, loops follow-up listening, and stops only on stop phrase', async ({ page }) => {
    await page.addInitScript(() => {
      let state = 'STANDBY';
      let user = '';
      let text = '';
      let voice = 'natural';
      let nextId = 1;
      const trace = { wakeIds: [], listenIds: [], voices: [], setVoices: [], stopSpeech: 0 };
      window.__jarvisTrace = trace;

      const snapshot = () => JSON.stringify({
        name: 'JARVIS',
        state,
        user,
        text,
        voice,
        pending: '',
        thumbnail: '',
        setup: '',
        installing: false,
        error: '',
      });

      window.Android = {
        hasMicPermission: () => true,
        jarvisWakeListen: () => {
          const id = nextId++;
          trace.wakeIds.push(id);
          return id;
        },
        jarvisListen: () => {
          const id = nextId++;
          trace.listenIds.push(id);
          return id;
        },
        cancelListening: () => {},
        stopListening: () => {},
        jarvis: (_key, op, arg) => {
          if (op === 'openOnLaunch') return JSON.stringify({ open: false });
          if (op === 'listening') { state = 'LISTENING'; return snapshot(); }
          if (op === 'idle') { state = 'STANDBY'; return snapshot(); }
          if (op === 'stopSpeech') { trace.stopSpeech++; state = 'STANDBY'; return snapshot(); }
          if (op === 'setVoice') { voice = arg; trace.setVoices.push(arg); return snapshot(); }
          if (op === 'voice') {
            trace.voices.push(arg);
            user = arg;
            text = 'Executed: ' + arg;
            state = 'SPEAKING';
            setTimeout(() => { state = 'STANDBY'; }, 100);
            return snapshot();
          }
          return snapshot();
        },
      };
    });

    await page.goto('/panel/');

    await page.waitForFunction(() => (window.__jarvisTrace?.wakeIds?.length || 0) >= 1);
    const overlay = page.locator('#jarvisOverlay');
    await expect(overlay).toBeHidden();

    const wakeId = await page.evaluate(() => window.__jarvisTrace.wakeIds.at(-1));
    await page.evaluate((id) => window.onAndroidSpeech('partial', 'hey jarvis', id), wakeId);

    await expect(overlay).toBeVisible();
    await expect(page.locator('#jState')).toHaveText('detected');

    // Bottom-center positioning check on landscape panel viewport
    const box = await page.locator('#jarvisOverlay .j-card').boundingBox();
    const vp = page.viewportSize();
    expect(box).not.toBeNull();
    expect(Math.abs((box.x + box.width / 2) - (vp.width / 2))).toBeLessThan(24);
    expect(box.y + box.height).toBeGreaterThan(vp.height * 0.65);

    // Completing wake phrase automatically hands off to command listening
    await page.evaluate((id) => window.onAndroidSpeech('final', 'hey jarvis', id), wakeId);
    await page.waitForFunction(() => (window.__jarvisTrace?.listenIds?.length || 0) === 1);
    const cmdId1 = await page.evaluate(() => window.__jarvisTrace.listenIds.at(-1));

    await page.evaluate((id) => window.onAndroidSpeech('final', 'check adb status', id), cmdId1);
    await expect(page.locator('#jUser')).toHaveText('check adb status');
    await expect(page.locator('#jReply')).toHaveText('Executed: check adb status');

    // After finishing speaking, JARVIS automatically opens a follow-up listen turn
    await page.waitForFunction(() => (window.__jarvisTrace?.listenIds?.length || 0) === 2, null, { timeout: 5000 });
    const cmdId2 = await page.evaluate(() => window.__jarvisTrace.listenIds.at(-1));

    await page.evaluate((id) => window.onAndroidSpeech('final', '', id), cmdId2);
    await page.waitForFunction(() => window.__jarvisTrace.listenIds.length === 3);
    const cmdId3 = await page.evaluate(() => window.__jarvisTrace.listenIds.at(-1));
    // Only stops conversing when the user tells it to stop
    await page.evaluate((id) => window.onAndroidSpeech('final', 'stop listening', id), cmdId3);
    await expect.poll(() => page.evaluate(() => window.__jarvisTrace.stopSpeech)).toBe(1);
    await expect(page.locator('#jState')).toHaveText('Ready');
    await page.waitForTimeout(900);
    expect(await page.evaluate(() => window.__jarvisTrace.listenIds.length)).toBe(3);
  });

  test('exposes multi-voice selector and purple/white night clock styling', async ({ page }) => {
    await page.addInitScript(() => {
      let voice = 'natural';
      window.__voiceOps = [];
      const snapshot = () => JSON.stringify({
        name: 'JARVIS',
        state: 'STANDBY',
        user: '',
        text: '',
        voice,
        pending: '',
        thumbnail: '',
        setup: '',
        installing: false,
        error: '',
      });
      window.Android = {
        hasMicPermission: () => false,
        jarvisListen: () => 1,
        cancelListening: () => {},
        stopListening: () => {},
        jarvis: (_key, op, arg) => {
          if (op === 'openOnLaunch') return JSON.stringify({ open: false });
          if (op === 'setVoice') { voice = arg; window.__voiceOps.push(arg); }
          return snapshot();
        },
      };
    });

    await page.goto('/panel/');
    await page.click('#jarvisTrigger');
    await page.click('#jSettings');

    const pick = page.locator('#jVoicePick');
    await expect(pick).toBeVisible();
    await expect(pick.locator('option')).toHaveCount(11);
    await pick.selectOption('jarvis');
    await expect.poll(() => page.evaluate(() => window.__voiceOps)).toContain('jarvis');

    // Verify night clock styling has purple + white gradient
    const nightStyle = await page.evaluate(() => {
      const nClock = document.getElementById('nClock');
      const night = document.getElementById('night');
      const csClock = getComputedStyle(nClock);
      const csNight = getComputedStyle(night);
      return {
        clockBg: csClock.backgroundImage,
        nightColor: csNight.color,
      };
    });
    expect(nightStyle.clockBg).toContain('gradient');
    expect(nightStyle.clockBg).toContain('168, 85, 247');
  });
});

