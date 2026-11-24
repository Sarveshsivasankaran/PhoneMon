const { test, expect } = require('@playwright/test');

async function openPanel(page) {
  await page.addInitScript(() => {
    const key = 'panel-test-key';
    window.__phoneboxKey = key;
    const phone = window.testAssistant = {
      calls: [], microphones: 0,
      status: { state: 'STANDBY', name: 'Ada', text: '', user: '', setup: 'Chat model installed' },
    };
    function checkKey(received) {
      if (received !== key) throw new Error('Missing panel bridge key');
    }
    window.Android = {
      jarvisListen(received) { checkKey(received); return ++phone.microphones; },
      stopListening(received, id) { checkKey(received); phone.calls.push(['stop', id]); },
      cancelListening(received, id) { checkKey(received); phone.calls.push(['cancel', id]); },
      jarvis(received, op, arg) {
        checkKey(received);
        if (op === 'openOnLaunch') return '{"open":false}';
        if (op !== 'status') phone.calls.push([op, arg]);
        if (op === 'listening') phone.status.state = 'LISTENING';
        if (op === 'idle') phone.status.state = 'STANDBY';
        if (op === 'voice') Object.assign(phone.status, {user: arg, state: 'THINKING'});
        if (op === 'previewVoice') phone.status.state = 'SPEAKING';
        return JSON.stringify(phone.status);
      },
    };
  });
  await page.goto('/panel/');
  await expect(page.getByRole('button', {name:'Talk to Ada'})).toBeVisible();
}

test('the collapsed assistant starts local speech and captions one recognized turn', async ({page}) => {
  await page.emulateMedia({reducedMotion:'reduce'});
  await openPanel(page);
  const captions = page.getByRole('region', {name:'Assistant captions'});
  await expect(captions).toBeHidden();
  await page.getByRole('button', {name:'Talk to Ada'}).click();
  await expect(captions).toBeVisible();
  await expect(captions.getByRole('textbox')).toHaveCount(0);
  await expect(page.locator('#jState')).toHaveText('detected');
  expect(await page.evaluate(() => window.testAssistant.microphones)).toBe(1);
  expect(await captions.locator('.j-wave i').first().evaluate(el => getComputedStyle(el).animationName)).toBe('none');
  await page.evaluate(() => window.onAndroidSpeech('partial', 'what time', 1));
  await expect(page.locator('#jUser')).toHaveText('what time');
  await page.evaluate(() => window.onAndroidSpeech('final', 'what time is it', 1));
  await expect(page.locator('#jUser')).toHaveText('what time is it');
  await expect(page.locator('#jState')).toHaveText('processing');
  await page.evaluate(() => {
    window.onAndroidSpeech('final', 'stale transcript', 1);
    Object.assign(window.testAssistant.status, {state:'SPEAKING', text:'It is noon.'});
  });
  await expect(page.locator('#jReply')).toHaveText('It is noon.');
  await expect(page.locator('#jSpeaker')).toHaveText('Ada');
  expect(await page.evaluate(() => window.testAssistant.calls.filter(([op]) => op === 'voice')))
    .toEqual([['voice','what time is it']]);
});

test('setup previews the voice and wake listening resumes only after playback ends', async ({page}) => {
  await openPanel(page);
  await page.getByRole('button', {name:'Talk to Ada'}).click();
  await page.getByRole('button', {name:'Setup'}).click();
  await expect(page.getByRole('button', {name:'Hear voice'})).toBeDisabled();
  await page.evaluate(() => window.onAndroidSpeech('final', '', 1));
  await expect(page.locator('#jDownload')).toHaveText('Chat model installed');
  await expect(page.getByRole('button', {name:'Install vision model'})).toBeEnabled();
  await page.getByRole('checkbox', {name:'Listen for “Jarvis”'}).check();
  await page.getByRole('button', {name:'Hear voice'}).click();
  await expect(page.locator('#jState')).toHaveText('speaking');
  await expect(page.getByRole('button', {name:'Hear voice'})).toBeDisabled();
  await expect(page.getByRole('button', {name:'Speak', exact:true})).toBeDisabled();
  await page.getByRole('button', {name:'Collapse captions'}).click();
  await expect(page.locator('#jPillText')).toHaveText('Ada speaking');
  // Cross several wake checks during playback before allowing another recognition session.
  await page.waitForTimeout(2600);
  expect(await page.evaluate(() => window.testAssistant.microphones)).toBe(1);
  expect(await page.evaluate(() => window.testAssistant.calls.filter(([op]) => op === 'previewVoice'))).toHaveLength(1);
  await page.evaluate(() => { window.testAssistant.status.state = 'STANDBY'; });
  await expect.poll(() => page.evaluate(() => window.testAssistant.microphones)).toBe(2);
  await page.evaluate(() => window.onAndroidSpeech('final', 'Jarvis, show the time', 2));
  await expect(page.locator('#jUser')).toHaveText('show the time');
  expect(await page.evaluate(() => window.testAssistant.calls.filter(([op]) => op === 'voice')))
    .toEqual([['voice','show the time']]);
});
