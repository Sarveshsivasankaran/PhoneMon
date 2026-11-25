// Makes the README & guide media from the live demo/project UI: page screenshots, special overlays, settings editor, tour video, and hero image.
// Start the demo first (python tools/demo-server.py), then from tools/ui-tests:
//
//   node media.js shots all                           # all 22 docs/screenshots/*.png from the current UI
//   node media.js shots home work system settings-editor
//   node media.js tour                                # docs/tour-<v>.mp4 and .gif (needs ffmpeg)
//   node media.js hero                                # docs/hero-<v>.png (1920 x 810; waits ~1.5 min so the charts fill)
//
// Options: --base http://127.0.0.1:8790/  --out <dir>  --name <v> (the file name suffix, default "new")
const { chromium } = require('@playwright/test');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf('--' + k); return i >= 0 ? args.splice(i, 2)[1] : d; };
const BASE = opt('base', 'http://127.0.0.1:8790/');
const NAME = opt('name', 'new');
const DOCS = path.resolve(__dirname, '..', '..', 'docs');
const OUT = opt('out', DOCS);
const [what, ...rawIds] = args;
const PANEL = new URL('panel/', BASE).href;
const SETTINGS = new URL('settings/#pages', BASE).href;
const ORDER = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'pages', 'demo.js'), 'utf8').match(/const PAGES = (\[[^\]]*\])/)[1].replace(/'/g, '"'));
const ALIAS = { deepwork: 'focuswork' };
const ALL_SHOTS = [
  'home', 'dashboard', 'ai', 'work', 'system', 'procs-sheet', 'status', 'ambient',
  'markets', 'feeds', 'daily', 'world', 'music', 'deck', 'planner', 'automation',
  'nightstand', 'deepwork', 'overview', 'settings-editor', 'session-details', 'smart-stack',
];
const ids = rawIds.length === 1 && rawIds[0] === 'all' ? ALL_SHOTS : rawIds;

const next = (frame, n) => frame.evaluate((count) => { for (let i = 0; i < count; i++) window.onAndroidKey('next'); }, n);
const indexOf = (id) => {
  const canonical = ALIAS[id] || id;
  const i = ORDER.indexOf(canonical);
  if (i < 0) throw new Error(`unknown page "${id}" (pages: ${ORDER.join(', ')})`);
  return i;
};

async function launchBrowser() {
  try { return await chromium.launch(); } catch (_) {}
  for (const channel of ['chrome', 'msedge']) {
    try { return await chromium.launch({ channel }); } catch (_) {}
  }
  throw new Error('Could not launch Chromium, Chrome, or Edge for media generation.');
}

async function captureSettingsEditor(browser, file) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 960 }, deviceScaleFactor: 2, colorScheme: 'dark' });
  const page = await ctx.newPage();
  await page.goto(SETTINGS);
  await page.locator('#pageTabs .ptab').nth(1).waitFor({ timeout: 10000 });
  await page.locator('#pageTabs .ptab').nth(1).click();
  const targetWidget = page.locator('#canvas .wb').filter({ hasText: /Now playing/i }).first();
  if (await targetWidget.count()) {
    await targetWidget.click();
  } else {
    await page.locator('#canvas .wb').first().click();
  }
  await page.waitForTimeout(600);
  await page.screenshot({ path: file });
  await ctx.close();
  console.log(file);
}

async function captureSmartStack(browser, file) {
  const prep = await browser.newContext({ viewport: { width: 780, height: 360 }, deviceScaleFactor: 2 });
  const p0 = await prep.newPage();
  await p0.goto(PANEL);
  await p0.waitForTimeout(2500);
  const storage = await p0.evaluate(() => {
    const cfg = JSON.parse(localStorage.getItem('pb-demo-config'));
    const home = cfg.pages.find((p) => p.id === 'home') || cfg.pages[0];
    const clock = home.widgets.find((w) => w.type === 'clock') || { id: 'clock', type: 'clock', x: 0, y: 0, w: 6, h: 6, options: { status: true, weather: true } };
    cfg.pages = [{
      ...home,
      widgets: [
        { ...clock, x: 0, y: 0, w: 6, h: 6 },
        { id: 'codex-top', type: 'quotas', x: 6, y: 0, w: 6, h: 3, options: { providers: ['codex'], pageSize: 1, rotate: false } },
        {
          id: 'stack-demo',
          type: 'stack',
          x: 6,
          y: 3,
          w: 6,
          h: 3,
          options: {
            widgets: [
              { id: 'c-sessions', type: 'sessions', enabled: true, options: {} },
              { id: 'c-gh', type: 'github', enabled: true, options: {} },
              { id: 'c-weather', type: 'weather', enabled: true, options: {} },
            ],
          },
        },
      ],
    }];
    localStorage.setItem('pb-demo-config', JSON.stringify(cfg));
    return { ...localStorage };
  });
  await prep.close();

  const ctx = await browser.newContext({ viewport: { width: 780, height: 360 }, deviceScaleFactor: 2 });
  await ctx.addInitScript((s) => { for (const [k, v] of Object.entries(s)) localStorage.setItem(k, v); }, storage);
  const page = await ctx.newPage();
  await page.goto(PANEL);
  await page.waitForTimeout(3500);
  await page.screenshot({ path: file });
  await ctx.close();
  console.log(file);
}

async function shots(browser) {
  const overlays = new Set(['overview', 'procs-sheet', 'session-details']);
  const panelIds = [
    ...ids.filter((id) => id !== 'settings-editor' && id !== 'smart-stack' && !overlays.has(id)),
    ...ids.filter((id) => overlays.has(id)),
  ];
  if (panelIds.length) {
    const ctx = await browser.newContext({ viewport: { width: 780, height: 360 }, deviceScaleFactor: 2 });
    const page = await ctx.newPage();
    await page.goto(PANEL);
    // Warm up live real-time sparklines/charts on home/system/dashboard when any chart page is requested.
    const needsCharts = panelIds.some((id) => ['home', 'dashboard', 'system', 'status'].includes(id));
    await page.waitForTimeout(needsCharts ? 95_000 : 3500);

    let currentIdx = 0;
    for (const id of panelIds) {
      const file = path.join(OUT, 'screenshots', id + '.png');
      if (id === 'overview') {
        await page.evaluate(() => window.openOverview());
        await page.waitForTimeout(800);
        await page.screenshot({ path: file });
        await page.evaluate(() => window.closeOverview?.() || document.querySelector('#ovClose')?.click());
        await page.waitForTimeout(300);
      } else if (id === 'procs-sheet') {
        const target = indexOf('procs');
        await next(page, (target - currentIdx + ORDER.length) % ORDER.length);
        currentIdx = target;
        await page.locator('.proc').first().waitFor({ timeout: 8000 });
        await page.waitForTimeout(600);
        await page.locator('.proc').first().click();
        await page.locator('#pSheet:not([hidden])').waitFor({ timeout: 5000 });
        await page.waitForTimeout(500);
        await page.screenshot({ path: file });
        await page.keyboard.press('Escape');
        await page.waitForTimeout(300);
      } else if (id === 'session-details') {
        const target = indexOf('work');
        await next(page, (target - currentIdx + ORDER.length) % ORDER.length);
        currentIdx = target;
        const sessRow = page.locator('.sess', { hasText: /mobile-app/i }).first();
        await sessRow.waitFor({ timeout: 8000 });
        await page.waitForTimeout(600);
        await sessRow.click();
        await page.locator('.session-panel-head').waitFor({ timeout: 5000 });
        await page.waitForTimeout(400);
        await page.screenshot({ path: file });
        await page.keyboard.press('Escape');
        await page.waitForTimeout(300);
      } else {
        const target = indexOf(id);
        const step = (target - currentIdx + ORDER.length) % ORDER.length;
        if (step > 0) {
          await next(page, step);
          currentIdx = target;
        }
        await page.waitForTimeout(2500);
        await page.screenshot({ path: file });
      }
      console.log(file);
    }
    await ctx.close();
  }
  if (ids.includes('settings-editor')) {
    await captureSettingsEditor(browser, path.join(OUT, 'screenshots', 'settings-editor.png'));
  }
  if (ids.includes('smart-stack')) {
    await captureSmartStack(browser, path.join(OUT, 'screenshots', 'smart-stack.png'));
  }
}

async function tour(browser) {
  // a rule may switch at once in the recording: no touch grace, no minimum time between switches
  const prep = await browser.newContext({ viewport: { width: 780, height: 360 } });
  const p0 = await prep.newPage();
  await p0.goto(PANEL);
  await p0.waitForTimeout(2000);
  const storage = await p0.evaluate(() => {
    const c = JSON.parse(localStorage.getItem('pb-demo-config'));
    c.display = { ...c.display, touchGraceSec: 0, minDwellSec: 0, switchCountdownSec: 3, idleReturnSec: 0 };
    localStorage.setItem('pb-demo-config', JSON.stringify(c));
    return { ...localStorage };
  });
  await prep.close();

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'phonebox-tour-'));
  const ctx = await browser.newContext({ viewport: { width: 780, height: 360 }, recordVideo: { dir, size: { width: 780, height: 360 } } });
  await ctx.addInitScript((s) => { for (const [k, v] of Object.entries(s)) localStorage.setItem(k, v); }, storage);
  const page = await ctx.newPage();
  const wait = (s) => page.waitForTimeout(s * 1000);
  const demo = (fn, arg) => page.evaluate(fn, arg);
  await page.goto(PANEL);
  await wait(3.5);                                                                  // Home
  for (const s of [2.6, 2.8, 2.8]) { await next(page, 1); await wait(s); }          // Dashboard, AI quotas, Sessions
  await demo(() => window.PhoneBoxDemo.emit('notify', { title: 'Build #143', text: 'main passed in 2m 48s', level: 'ok' }));
  await wait(3.2);
  await demo(() => window.PhoneBoxDemo.ask({ tool: 'Run a command', detail: 'git push origin main --force-with-lease', description: 'Push the rebased branch', project: 'api-gateway' }));
  await wait(3.2);
  await page.getByRole('button', { name: 'Allow' }).click();
  await wait(1.6);
  await next(page, 1); await wait(2.4);                                             // System
  await demo(() => window.PhoneBoxDemo.emit('goto', { page: 'music', rule: 'music', name: 'Spotify is playing', hold: false, priority: 2, countdown: 3 }));
  await wait(6.5);                                                                  // countdown, then Music
  for (const s of [2.6, 2.4, 2.4, 2.4, 2.4]) { await next(page, 1); await wait(s); } // Status, Ambient, World, Daily, Markets
  await demo(() => window.openOverview()); await wait(2.4);
  await page.locator('#ovGrid .ov-tile').nth(indexOf('automation')).click();
  await wait(3);
  for (const s of [3, 3, 2.6, 3]) { await next(page, 1); await wait(s); }           // Planner, Nightstand, Weekend, Deep work
  await next(page, 1); await wait(2.5);                                             // back to Home
  const video = await page.video().path();
  await ctx.close();

  const mp4 = path.join(OUT, `tour-${NAME}.mp4`), gif = path.join(OUT, `tour-${NAME}.gif`);
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-ss', '0.6', '-i', video, '-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-an', mp4]);
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-ss', '0.6', '-i', video, '-vf',
    'fps=8,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle', gif]);
  fs.rmSync(dir, { recursive: true, force: true });
  console.log(mp4); console.log(gif);
}

async function hero(browser) {
  const page = await browser.newPage({ viewport: { width: 1920, height: 810 } });
  const phone = (cls, name) => `<div class="phone ${cls}"><div class="screen"><iframe name="${name}" src="${PANEL}"></iframe></div></div>`;
  await page.setContent(`<!doctype html><html><head><style>
    html,body{margin:0;width:1920px;height:810px;overflow:hidden;background:#07070b}
    body{background:radial-gradient(ellipse 70% 60% at 50% 45%,#1d1530 0%,#0c0a14 55%,#07070b 100%);perspective:1800px}
    .phone{position:absolute;border-radius:46px;background:linear-gradient(145deg,#2a2a33,#121217);padding:26px 30px 26px 34px;box-shadow:0 0 0 2px #3a3a45 inset,0 40px 90px rgba(0,0,0,.7)}
    .phone::before{content:"";position:absolute;left:12px;top:50%;width:12px;height:12px;margin-top:-6px;border-radius:50%;background:#050507;box-shadow:0 0 0 2px #2c2c36}
    .screen{width:1120px;height:517px;border-radius:18px;overflow:hidden;background:#000}
    .screen iframe{width:780px;height:360px;border:0;transform:scale(1.4359);transform-origin:0 0}
    .c{left:50%;top:50%;transform:translate(-50%,-50%);z-index:2}
    .l,.r{top:150px;filter:brightness(.42) saturate(.85);z-index:1}
    .l{left:-560px;transform:rotateY(38deg) scale(.82);transform-origin:right center}
    .r{right:-560px;transform:rotateY(-38deg) scale(.82);transform-origin:left center}
  </style></head><body>${phone('l', 'left')}${phone('r', 'right')}${phone('c', 'center')}</body></html>`);
  await page.waitForTimeout(3500);
  const want = { left: ids[0] || 'automation', center: ids[1] || 'home', right: ids[2] || 'planner' };
  for (const f of page.frames()) if (want[f.name()]) await next(f, indexOf(want[f.name()]));
  await page.waitForTimeout(95_000); // the charts fill up
  const file = path.join(OUT, `hero-${NAME}.png`);
  await page.screenshot({ path: file });
  console.log(file);
}

(async () => {
  const run = { shots, tour, hero }[what];
  if (!run) { console.error('usage: node media.js shots <page|all>… | tour | hero [left center right]'); process.exit(2); }
  const browser = await launchBrowser();
  try { await run(browser); } finally { await browser.close(); }
})().catch((e) => { console.error(e); process.exit(1); });
