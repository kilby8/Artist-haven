// Artist Haven for the web: the page, the pen and the panels. The engine runs in worker.js.
import * as db from './db.js';

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => [...document.querySelectorAll(sel)];
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

// ---- Settings (remembered in this browser) ---------------------------------------------------

const DEFAULT_PALETTE = [
  0xFF1F2A44, 0xFF3D5A80, 0xFF98C1D9, 0xFFE0FBFC, 0xFFEE6C4D,
  0xFFF4A261, 0xFFE9C46A, 0xFF2A9D8F, 0xFF264653, 0xFF8E7DBE,
  0xFFD4A5A5, 0xFF6B4226, 0xFF000000, 0xFF7F7F7F, 0xFFFFFFFF,
].map(c => c | 0);

const settings = Object.assign({
  color: 0xFF1F2A44 | 0,
  recent: [],
  palette: DEFAULT_PALETTE,
  brushes: { brush: 'studio_pen', smudge: 'soft_blend', eraser: 'hard_airbrush' },
  sizes: {},
  opacities: {},
  loads: {},
  symmetry: 0,
  streamline: 0,
  fillTolerance: 0.2,
  fillAll: false,
  fingerPaint: false,
  rightHand: false,
  stylusSeen: false,
  selShape: 'lasso',
  selMode: 0,
  xfUniform: true,
}, readJson('ah.settings', {}));
// Version 2 added real blending brushes: move the smudge tool off the old airbrush default.
if ((settings.version | 0) < 2) {
  if (settings.brushes.smudge === 'soft_airbrush') settings.brushes.smudge = 'soft_blend';
  settings.version = 2;
}

function readJson(key, fallback) {
  try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; } catch { return fallback; }
}
// Brushes made in the Brush Studio, as a JSON array (the engine's brush format).
let customBrushes = readJson('ah.customBrushes', []);
function saveCustomBrushes() {
  try { localStorage.setItem('ah.customBrushes', JSON.stringify(customBrushes)); } catch { toast('Could not save your brushes in this browser'); }
}
let settingsTimer = 0;
function saveSettings() {
  clearTimeout(settingsTimer);
  settingsTimer = setTimeout(() => {
    try { localStorage.setItem('ah.settings', JSON.stringify(settings)); } catch { /* private mode */ }
  }, 300);
}

// ---- Colour helpers --------------------------------------------------------------------------

const hex = (c) => '#' + ((c >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
const fromHex = (h) => {
  const m = /^#?([0-9a-f]{6}|[0-9a-f]{3})$/i.exec(h.trim());
  if (!m) return null;
  let s = m[1];
  if (s.length === 3) s = s.split('').map(ch => ch + ch).join('');
  return (0xFF000000 | parseInt(s, 16)) | 0;
};
function hsvToArgb(h, s, v) {
  const f = (n) => { const k = (n + h / 60) % 6; return v - v * s * Math.max(0, Math.min(k, 4 - k, 1)); };
  const r = Math.round(f(5) * 255), g = Math.round(f(3) * 255), b = Math.round(f(1) * 255);
  return (0xFF000000 | (r << 16) | (g << 8) | b) | 0;
}
function argbToHsv(c) {
  const r = ((c >> 16) & 255) / 255, g = ((c >> 8) & 255) / 255, b = (c & 255) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d) {
    if (max === r) h = ((g - b) / d) % 6; else if (max === g) h = (b - r) / d + 2; else h = (r - g) / d + 4;
    h *= 60; if (h < 0) h += 360;
  }
  return { h, s: max ? d / max : 0, v: max };
}

// ---- Toast, dialogs, menus -------------------------------------------------------------------

let toastTimer = 0;
function toast(text, ms = 2600) {
  const t = $('#toast');
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, ms);
}

function showDialog(id) {
  $('#scrim').hidden = false;
  $(id).hidden = false;
}
function closeDialogs() {
  $('#scrim').hidden = true;
  $$('.dialog').forEach(d => { d.hidden = true; });
}
$$('[data-close]').forEach(b => b.addEventListener('click', closeDialogs));
$('#scrim').addEventListener('click', closeDialogs);

function prompt(title, value) {
  return new Promise(resolve => {
    $('#prompt-title').textContent = title;
    const input = $('#prompt-input');
    input.value = value || '';
    showDialog('#d-prompt');
    setTimeout(() => { input.focus(); input.select(); }, 30);
    const finish = (v) => { cleanup(); closeDialogs(); resolve(v); };
    const ok = () => finish(input.value.trim() || null);
    const key = (e) => { if (e.key === 'Enter') ok(); if (e.key === 'Escape') finish(null); };
    const cancel = () => finish(null);
    $('#prompt-ok').addEventListener('click', ok);
    input.addEventListener('keydown', key);
    $$('#d-prompt [data-close]').forEach(b => b.addEventListener('click', cancel));
    function cleanup() {
      $('#prompt-ok').removeEventListener('click', ok);
      input.removeEventListener('keydown', key);
      $$('#d-prompt [data-close]').forEach(b => b.removeEventListener('click', cancel));
    }
  });
}

function confirmDelete(title, text) {
  return new Promise(resolve => {
    $('#confirm-title').textContent = title;
    $('#confirm-text').textContent = text;
    showDialog('#d-confirm');
    const ok = () => { cleanup(); closeDialogs(); resolve(true); };
    const cancel = () => { cleanup(); resolve(false); };
    $('#confirm-ok').addEventListener('click', ok);
    $$('#d-confirm [data-close]').forEach(b => b.addEventListener('click', cancel));
    $('#scrim').addEventListener('click', cancel);
    function cleanup() {
      $('#confirm-ok').removeEventListener('click', ok);
      $$('#d-confirm [data-close]').forEach(b => b.removeEventListener('click', cancel));
      $('#scrim').removeEventListener('click', cancel);
    }
  });
}

let openMenuEl = null;
function openMenu(menu, x, y) {
  closeMenu();
  menu.hidden = false;
  const r = menu.getBoundingClientRect();
  menu.style.left = clamp(x, 8, innerWidth - r.width - 8) + 'px';
  menu.style.top = clamp(y, 8, innerHeight - r.height - 8) + 'px';
  openMenuEl = menu;
}
function closeMenu() {
  if (openMenuEl) openMenuEl.hidden = true;
  openMenuEl = null;
}
document.addEventListener('pointerdown', (e) => {
  if (openMenuEl && !openMenuEl.contains(e.target)) closeMenu();
}, true);

// ---- Worker ----------------------------------------------------------------------------------

const worker = new Worker('worker.js', { type: 'module' });
const docCanvas = $('#doc');
const offscreen = docCanvas.transferControlToOffscreen();
let brushes = [];
let blendModes = [];
let brushOptions = null;
const waiters = new Map();

function call(t, msg = {}, transfer = []) { worker.postMessage({ t, ...msg }, transfer); }
function waitFor(type) { return new Promise(resolve => waiters.set(type, resolve)); }

worker.onmessage = (e) => {
  const m = e.data;
  const w = waiters.get(m.t);
  if (w) { waiters.delete(m.t); w(m); }
  switch (m.t) {
    case 'ready': brushes = m.brushes; blendModes = m.blendModes; brushOptions = m.brushOptions; break;
    case 'brushes': brushes = m.brushes; if (!$('#p-brushes').hidden) renderBrushes(); break;
    case 'state': onDocState(m); break;
    case 'opened': onOpened(m); break;
    case 'snapped': input.snapped = true; break;
    case 'sampled': onSampled(m); break;
    case 'layerThumbs': onLayerThumbs(m.thumbs); break;
    case 'exported': saveBlob(m.blob, exportName(m.type)); break;
    case 'toast': toast(m.message); break;
    case 'selection': onSelection(m); break;
    case 'transformStarted': onTransformStarted(m); break;
    case 'transformed': onTransformed(); break;
    case 'adjusted': onAdjusted(); break;
    case 'water': onWater(m); break;
    case 'error':
      console.error(m.message);
      toast(m.message, 5000);
      if (m.request === 'open' || m.request === 'create' || m.request === 'openPsd') showGallery();
      if (m.request === 'openPsd') toast("That Photoshop file couldn't be opened: " + m.message, 7000);
      break;
  }
};

// Brush previews come from a second worker so they never hold up the pen. They are painted on
// demand, a category at a time, so a big library doesn't keep the worker busy.
const previews = new Map();
const previewsAsked = new Set();
let previewWorker = null;
let previewReady = null;
let studioPreviewSerial = 0;
const PREVIEW = { width: 480, height: 72, color: 0xFFF2F2F2 | 0, background: 0xFF2B2B31 | 0 };
function startPreviews() {
  if (previewWorker) return previewReady;
  previewWorker = new Worker('worker.js', { type: 'module' });
  previewReady = new Promise((resolve) => {
    previewWorker.onmessage = (e) => {
      const m = e.data;
      if (m.t === 'ready') resolve();
      else if (m.t === 'preview') {
        previews.set(m.id, m.bitmap);
        const c = document.querySelector(`.brush-item[data-id="${m.id}"] canvas`);
        if (c) drawPreview(c, m.bitmap);
      } else if (m.t === 'studioPreview') onStudioPreview(m);
    };
  });
  previewWorker.postMessage({ t: 'init', customBrushes: JSON.stringify(customBrushes) });
  return previewReady;
}
async function requestPreviews(ids) {
  const want = ids.filter(id => !previewsAsked.has(id));
  if (!want.length) return;
  want.forEach(id => previewsAsked.add(id));
  await startPreviews();
  previewWorker.postMessage({ t: 'previews', ids: want, ...PREVIEW });
}
/** Tells both workers about the user's brushes, and repaints their previews. */
function pushCustomBrushes(changedIds = []) {
  const json = JSON.stringify(customBrushes);
  call('setCustomBrushes', { json });
  if (previewWorker) previewWorker.postMessage({ t: 'setCustomBrushes', json });
  for (const id of changedIds) { previews.delete(id); previewsAsked.delete(id); }
}

// ---- Gallery ---------------------------------------------------------------------------------

let current = null; // { id, name, width, height }

async function showGallery() {
  $('#canvas-screen').hidden = true;
  $('#gallery').hidden = false;
  closePanels();
  await renderGallery();
}

const thumbUrls = [];
async function renderGallery() {
  const grid = $('#gallery-grid');
  thumbUrls.splice(0).forEach(u => URL.revokeObjectURL(u));
  let list = [];
  try { list = await db.listProjects(); } catch (err) { toast('Could not read saved drawings: ' + err.message); }
  grid.textContent = '';
  $('#gallery-empty').hidden = list.length > 0;
  for (const p of list) {
    const card = document.createElement('div');
    card.className = 'card';
    const thumb = document.createElement('button');
    thumb.className = 'thumb';
    if (p.thumb) {
      const img = document.createElement('img');
      const url = URL.createObjectURL(p.thumb);
      thumbUrls.push(url);
      img.src = url;
      img.alt = '';
      thumb.append(img);
    }
    thumb.addEventListener('click', () => openProject(p.id));
    thumb.addEventListener('contextmenu', (e) => { e.preventDefault(); cardMenu(p, e.clientX, e.clientY); });
    const meta = document.createElement('div');
    meta.className = 'meta';
    const text = document.createElement('div');
    text.style.minWidth = '0';
    const name = document.createElement('div');
    name.className = 'name';
    name.textContent = p.name;
    const date = document.createElement('div');
    date.className = 'date';
    date.textContent = `${new Date(p.modified).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })} · ${p.width} × ${p.height}`;
    text.append(name, date);
    const more = document.createElement('button');
    more.className = 'more';
    more.title = 'More';
    more.innerHTML = '<svg><use href="#i-more"/></svg>';
    more.addEventListener('click', (e) => { const r = more.getBoundingClientRect(); cardMenu(p, r.left, r.bottom); e.stopPropagation(); });
    meta.append(text, more);
    card.append(thumb, meta);
    grid.append(card);
  }
}

function cardMenu(p, x, y) {
  const menu = $('#card-menu');
  menu.onclick = async (e) => {
    const act = e.target.closest('button')?.dataset.menu;
    if (!act) return;
    closeMenu();
    if (act === 'open') openProject(p.id);
    if (act === 'rename') {
      const name = await prompt('Rename drawing', p.name);
      if (name) { await db.renameProject(p.id, name); renderGallery(); }
    }
    if (act === 'duplicate') { await db.duplicateProject(p.id, newId(), p.name + ' copy'); renderGallery(); }
    if (act === 'export') {
      pendingExportName = p.name;
      await openProject(p.id, { thenExport: true });
    }
    if (act === 'delete') {
      if (await confirmDelete('Delete drawing?', `"${p.name}" will be deleted from this device. This can't be undone.`)) {
        await db.deleteProject(p.id);
        renderGallery();
      }
    }
  };
  openMenu(menu, x, y);
}

const newId = () => 'p' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);

// New canvas dialog
function screenPreset() {
  const dpr = window.devicePixelRatio || 1;
  let w = Math.round(screen.width * dpr), h = Math.round(screen.height * dpr);
  if (w < h) [w, h] = [h, w];
  const k = Math.min(1, 4096 / w);
  return { name: 'Screen size', w: Math.round(w * k), h: Math.round(h * k) };
}
const PRESETS = () => [
  screenPreset(),
  { name: 'Square', w: 2048, h: 2048 },
  { name: 'A4 portrait', w: 2480, h: 3508, note: '300 dpi' },
  { name: 'A4 landscape', w: 3508, h: 2480, note: '300 dpi' },
  { name: '4K', w: 3840, h: 2160 },
  { name: 'Sketch', w: 1600, h: 1200 },
];
function openNewDialog() {
  const box = $('#presets');
  box.textContent = '';
  const presets = PRESETS();
  const pick = (p, btn) => {
    $('#new-w').value = p.w;
    $('#new-h').value = p.h;
    box.querySelectorAll('.preset').forEach(b => b.classList.toggle('active', b === btn));
  };
  presets.forEach((p, i) => {
    const b = document.createElement('button');
    b.className = 'preset';
    const k = 40 / Math.max(p.w, p.h);
    b.innerHTML = `<div class="shape"><i style="width:${Math.round(p.w * k)}px;height:${Math.round(p.h * k)}px"></i></div><div>${p.name}</div><small>${p.w} × ${p.h}${p.note ? ' · ' + p.note : ''}</small>`;
    b.addEventListener('click', () => pick(p, b));
    b.addEventListener('dblclick', () => { pick(p, b); createFromDialog(); });
    box.append(b);
    if (i === 0) pick(p, b);
  });
  showDialog('#d-new');
}
async function createFromDialog() {
  const w = clamp(parseInt($('#new-w').value, 10) || 0, 64, 6000);
  const h = clamp(parseInt($('#new-h').value, 10) || 0, 64, 6000);
  if (w * h > 36e6) { toast('That canvas is too big. Try one under 6000 × 6000.'); return; }
  closeDialogs();
  const list = await db.listProjects().catch(() => []);
  await createProject({ name: `Drawing ${list.length + 1}`, width: w, height: h });
}
$('#new-btn').addEventListener('click', openNewDialog);
$('#new-create').addEventListener('click', createFromDialog);
$('#import-btn').addEventListener('click', () => $('#gallery-file').click());
$('#gallery-file').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  e.target.value = '';
  if (!file) return;
  if (/\.(psd|psb)$/i.test(file.name)) { openPsdFile(file).catch(() => {}); return; }
  try {
    const bmp = await createImageBitmap(file);
    const k = Math.min(1, 4096 / Math.max(bmp.width, bmp.height));
    const w = Math.max(64, Math.round(bmp.width * k)), h = Math.max(64, Math.round(bmp.height * k));
    const photo = imagePixels(bmp, w, h, file.name.replace(/\.[^.]+$/, ''));
    await createProject({ name: photo.name || 'Photo', width: w, height: h, photo }, [photo.rgba]);
  } catch (err) {
    toast("That picture couldn't be opened.");
  }
});

async function requestPersistence() {
  try {
    if (navigator.storage && navigator.storage.persist && !(await navigator.storage.persisted())) {
      await navigator.storage.persist();
    }
  } catch { /* not supported */ }
}

async function createProject(p, transfer = []) {
  requestPersistence();
  showCanvas();
  const id = newId();
  call('create', { id, name: p.name, width: p.width, height: p.height, paper: 0xFFFFFFFF | 0, photo: p.photo }, transfer);
}

async function openProject(id, opts = {}) {
  showCanvas();
  const waited = waitFor('opened');
  call('open', { id });
  await waited;
  if (opts.thenExport) {
    call('export', { type: 'image/png', withPaper: true });
    await waitFor('exported');
    showGallery();
  }
}

// ---- Canvas screen ---------------------------------------------------------------------------

const stage = $('#stage');
const wrap = $('#doc-wrap');
const overlay = $('#overlay');
const octx = overlay.getContext('2d');
const view = { s: 1, r: 0, f: 1, tx: 0, ty: 0 };
let doc = { width: 1, height: 1, canUndo: false, canRedo: false, layers: [], paperColor: -1, paperVisible: true };

function showCanvas() {
  $('#gallery').hidden = true;
  $('#canvas-screen').hidden = false;
  docCanvas.style.visibility = 'hidden';
  layoutSidebar();
  sizeOverlay();
  startPreviews();
}

function onOpened(m) {
  current = { id: m.id, name: m.name, width: m.width, height: m.height };
  doc.width = m.width;
  doc.height = m.height;
  docCanvas.style.width = m.width + 'px';
  docCanvas.style.height = m.height + 'px';
  $('#doc-title').textContent = m.name;
  document.title = m.name + ' · Artist Haven';
  fitView();
  requestAnimationFrame(() => { docCanvas.style.visibility = ''; });
  xf = null;
  adjusting = null;
  $('#p-adjust').hidden = true;
  if (!paintTools.includes(tool) && tool !== 'select') tool = lastPaintTool;
  updateToolUi();
  showReference(null);
  db.getReference(m.id).then(b => { if (b && current && current.id === m.id) showReference(b); }).catch(() => {});
}

function onDocState(m) {
  Object.assign(doc, m);
  $('#b-undo').disabled = !m.canUndo;
  $('#b-redo').disabled = !m.canRedo;
  // Another edit (or undo) finished the transform or adjustment in progress.
  if (xf && !m.transforming) { xf = null; if (tool === 'move') tool = lastPaintTool; updateToolUi(); drawOverlay(); }
  if (adjusting && !m.adjusting) { adjusting = null; $('#p-adjust').hidden = true; }
  syncMaskBanner();
  if (tool === 'select') updateSelectionUi();
  if (!$('#p-layers').hidden) renderLayers();
}

async function goToGallery() {
  closePanels();
  const closed = waitFor('closed');
  call('close');
  await closed;
  current = null;
  document.title = 'Artist Haven';
  showGallery();
}
$('#b-gallery').addEventListener('click', goToGallery);

// ---- View transform --------------------------------------------------------------------------

function applyView() {
  const c = Math.cos(view.r), s = Math.sin(view.r);
  const a = view.s * view.f * c, b = view.s * view.f * s, cc = -view.s * s, d = view.s * c;
  wrap.style.transform = `matrix(${a},${b},${cc},${d},${view.tx},${view.ty})`;
  docCanvas.classList.toggle('pixelated', view.s >= 2);
  drawOverlay();
}
function toDoc(x, y) {
  const px = x - view.tx, py = y - view.ty;
  const c = Math.cos(view.r), s = Math.sin(view.r);
  let dx = (c * px + s * py) / view.s;
  const dy = (-s * px + c * py) / view.s;
  dx *= view.f;
  return [dx, dy];
}
function toScreen(x, y) {
  const c = Math.cos(view.r), s = Math.sin(view.r);
  const fx = x * view.f * view.s, fy = y * view.s;
  return [view.tx + c * fx - s * fy, view.ty + s * fx + c * fy];
}
function fitView() {
  const sb = $('#sidebar').getBoundingClientRect();
  const top = 60 + 16, side = sb.width + 30;
  const aw = innerWidth - side * 2, ah = innerHeight - top - 16;
  view.s = Math.min(aw / doc.width, ah / doc.height, 1.5);
  view.r = 0;
  view.f = 1;
  view.tx = (innerWidth - doc.width * view.s) / 2;
  view.ty = top + (ah - doc.height * view.s) / 2;
  applyView();
}
function zoomAt(x, y, factor) {
  const [dx, dy] = toDoc(x, y);
  view.s = clamp(view.s * factor, 0.05, 40);
  const [sx, sy] = toScreen(dx, dy);
  view.tx += x - sx;
  view.ty += y - sy;
  applyView();
}
function flipView() {
  const cx = innerWidth / 2, cy = innerHeight / 2;
  const [dx, dy] = toDoc(cx, cy);
  view.f = -view.f;
  const [sx, sy] = toScreen(dx, dy);
  view.tx += cx - sx;
  view.ty += cy - sy;
  applyView();
}
function sizeOverlay() {
  const dpr = window.devicePixelRatio || 1;
  overlay.width = Math.round(innerWidth * dpr);
  overlay.height = Math.round(innerHeight * dpr);
  overlay.style.width = innerWidth + 'px';
  overlay.style.height = innerHeight + 'px';
  octx.setTransform(dpr, 0, 0, dpr, 0, 0);
  drawOverlay();
}
addEventListener('resize', () => { sizeOverlay(); });

// ---- Tools -----------------------------------------------------------------------------------

const TOOL_IDS = { brush: 0, eraser: 1, smudge: 2 };
let tool = 'brush';         // brush | smudge | eraser | dropper | fill | select | move | liquify
let lastPaintTool = 'brush';
const paintTools = ['brush', 'smudge', 'eraser'];

const brushById = (id) => brushes.find(b => b.id === id) || brushes[0];
function slotFor(t) { return paintTools.includes(t) ? t : lastPaintTool; }
function brushFor(t) { return brushById(settings.brushes[slotFor(t)]); }
function sizeFor(t) {
  const slot = slotFor(t), b = brushFor(t);
  return settings.sizes[slot + ':' + b.id] ?? b.defaultSize;
}
function opacityFor(t) {
  const slot = slotFor(t), b = brushFor(t);
  return settings.opacities[slot + ':' + b.id] ?? (slot === 'smudge' ? 0.8 : b.defaultOpacity);
}

/** Paint on the brush, 0..1, or -1 for brushes that don't carry paint. */
function loadFor(t) {
  const slot = slotFor(t), b = brushFor(t);
  if (slot !== 'brush' || b.paintLoad == null) return -1;
  return settings.loads[b.id] ?? b.paintLoad;
}

function selectTool(t) {
  if (t !== 'move') finishTransform(true);
  const reopen = t === tool && paintTools.includes(t);
  tool = t;
  if (paintTools.includes(t)) lastPaintTool = t;
  updateToolUi();
  if (reopen) togglePanel('p-brushes');
  else if (!$('#p-brushes').hidden) renderBrushes();
}

function updateToolUi() {
  for (const t of paintTools) $('#b-' + t).classList.toggle('active', tool === t);
  $('#b-dropper').classList.toggle('active', tool === 'dropper');
  $('#b-fill').classList.toggle('active', tool === 'fill');
  $('#b-color span').style.background = hex(settings.color);
  sizeSlider.set(sizeToValue(sizeFor(tool)));
  opacitySlider.set(opacityFor(tool));
  const load = loadFor(tool);
  $('#load-slider').hidden = load < 0;
  if (load >= 0) loadSlider.set(load);
  syncToolBars();
}

function sizeToValue(size) {
  const b = brushFor(tool);
  const min = Math.max(0.5, b.minSize), max = b.maxSize;
  return clamp(Math.log(size / min) / Math.log(max / min), 0, 1);
}
function valueToSize(v) {
  const b = brushFor(tool);
  const min = Math.max(0.5, b.minSize), max = b.maxSize;
  return Math.round(min * Math.pow(max / min, v) * 10) / 10;
}

function vslider(el, onChange, label) {
  const thumb = el.querySelector('.thumb'), bubble = el.querySelector('.bubble');
  let value = 0;
  const pad = 17;
  const place = () => {
    const h = el.clientHeight - pad * 2;
    const y = pad + (1 - value) * h;
    thumb.style.top = y + 'px';
    bubble.style.top = y + 'px';
    bubble.textContent = label(value);
  };
  const fromEvent = (e) => {
    const r = el.getBoundingClientRect();
    return clamp(1 - (e.clientY - r.top - pad) / (r.height - pad * 2), 0, 1);
  };
  el.addEventListener('pointerdown', (e) => {
    el.setPointerCapture(e.pointerId);
    el.classList.add('dragging');
    value = fromEvent(e); place(); onChange(value);
  });
  el.addEventListener('pointermove', (e) => {
    if (!el.hasPointerCapture(e.pointerId)) return;
    value = fromEvent(e); place(); onChange(value);
    drawOverlay();
  });
  const up = () => { el.classList.remove('dragging'); drawOverlay(); };
  el.addEventListener('pointerup', up);
  el.addEventListener('pointercancel', up);
  return { set(v) { value = v; place(); }, get: () => value };
}

const sizeSlider = vslider($('#size-slider'), (v) => {
  const slot = slotFor(tool), b = brushFor(tool);
  settings.sizes[slot + ':' + b.id] = valueToSize(v);
  sliderPreview = true;
  saveSettings();
}, (v) => `Size ${brushes.length ? valueToSize(v) : ''} px`);
const opacitySlider = vslider($('#opacity-slider'), (v) => {
  const slot = slotFor(tool), b = brushFor(tool);
  settings.opacities[slot + ':' + b.id] = Math.round(v * 100) / 100;
  saveSettings();
}, (v) => `Opacity ${Math.round(v * 100)}%`);
const loadSlider = vslider($('#load-slider'), (v) => {
  settings.loads[brushFor(tool).id] = Math.round(v * 100) / 100;
  saveSettings();
}, (v) => `Paint ${v < 0.2 ? 'dry' : v < 0.45 ? 'light' : v < 0.8 ? 'loaded' : 'heavy'} ${Math.round(v * 100)}%`);
let sliderPreview = false;
$('#size-slider').addEventListener('pointerup', () => { sliderPreview = false; drawOverlay(); });

function layoutSidebar() {
  $('#sidebar').classList.toggle('right', !!settings.rightHand);
}

$('#b-brush').addEventListener('click', () => selectTool('brush'));
$('#b-smudge').addEventListener('click', () => selectTool('smudge'));
$('#b-eraser').addEventListener('click', () => selectTool('eraser'));
$('#b-dropper').addEventListener('click', () => { tool = tool === 'dropper' ? lastPaintTool : 'dropper'; updateToolUi(); });
$('#b-fill').addEventListener('click', () => { tool = tool === 'fill' ? lastPaintTool : 'fill'; updateToolUi(); });
$('#b-undo').addEventListener('click', () => undo());
$('#b-redo').addEventListener('click', () => redo());
function undo() {
  if (xf) { finishTransform(false); return; }
  if (adjusting) { endAdjust(false); return; }
  call('undo');
}
function redo() {
  if (xf || adjusting) return;
  call('redo');
}
$('#b-layers').addEventListener('click', () => togglePanel('p-layers'));
$('#b-color').addEventListener('click', () => togglePanel('p-color'));
$('#b-actions').addEventListener('click', () => togglePanel('p-actions'));

function setColor(c, addRecent = false) {
  settings.color = c | 0;
  if (addRecent) pushRecent(c);
  $('#b-color span').style.background = hex(c);
  saveSettings();
  if (!$('#p-color').hidden) syncColorPanel();
}
function pushRecent(c) {
  c |= 0;
  settings.recent = [c, ...settings.recent.filter(x => x !== c)].slice(0, 12);
  saveSettings();
}

// ---- Panels ----------------------------------------------------------------------------------

function closePanels() {
  $$('.panel').forEach(p => { p.hidden = true; });
  $('#b-layers').classList.remove('active');
  $('#b-actions').classList.remove('active');
  closeMenu();
}
function togglePanel(id) {
  const el = $('#' + id);
  const show = el.hidden;
  closePanels();
  if (!show) return;
  el.hidden = false;
  if (id === 'p-brushes') renderBrushes();
  if (id === 'p-color') openColorPanel();
  if (id === 'p-layers') { $('#b-layers').classList.add('active'); renderLayers(); }
  if (id === 'p-actions') { $('#b-actions').classList.add('active'); syncActions(); }
}

// Brush library
let brushCategory = null;
let brushCategorySlot = null;
function renderBrushes() {
  const slot = slotFor(tool);
  const active = settings.brushes[slot];
  // Each tool opens the library on the category of its own brush (smudge opens on Blending).
  if (slot !== brushCategorySlot) { brushCategory = null; brushCategorySlot = slot; }
  $('#brush-tool-name').textContent = { brush: 'for painting', smudge: 'for smudging', eraser: 'for erasing' }[slot];
  const cats = [];
  for (const b of brushes) if (!cats.some(c => c.id === b.category)) cats.push({ id: b.category, name: b.categoryName });
  if (!brushCategory || !cats.some(c => c.id === brushCategory)) brushCategory = brushById(active).category;
  const nav = $('#brush-cats');
  nav.textContent = '';
  for (const c of cats) {
    const btn = document.createElement('button');
    btn.textContent = c.name;
    btn.classList.toggle('active', c.id === brushCategory);
    btn.addEventListener('click', () => { brushCategory = c.id; renderBrushes(); });
    nav.append(btn);
  }
  const list = $('#brush-list');
  list.textContent = '';
  const shown = brushes.filter(x => x.category === brushCategory);
  requestPreviews(shown.map(b => b.id));
  $('#brush-count').textContent = `${brushes.length} brushes`;
  for (const b of shown) {
    const item = document.createElement('div');
    item.className = 'brush-item';
    item.tabIndex = 0;
    item.dataset.id = b.id;
    item.classList.toggle('active', b.id === active);
    const head = document.createElement('div');
    head.className = 'brush-item-head';
    const name = document.createElement('span');
    name.textContent = b.name;
    const edit = document.createElement('button');
    edit.className = 'edit-brush';
    edit.title = 'Adjust this brush in the Brush Studio';
    edit.textContent = 'Adjust';
    edit.addEventListener('click', (e) => { e.stopPropagation(); openStudio(b.id); });
    head.append(name, edit);
    const c = document.createElement('canvas');
    c.width = 480; c.height = 72;
    const bmp = previews.get(b.id);
    if (bmp) drawPreview(c, bmp);
    item.append(head, c);
    item.addEventListener('click', () => {
      settings.brushes[slot] = b.id;
      saveSettings();
      if (!paintTools.includes(tool)) tool = slot;
      updateToolUi();
      renderBrushes();
    });
    list.append(item);
  }
}
function drawPreview(c, bmp) {
  const x = c.getContext('2d');
  x.clearRect(0, 0, c.width, c.height);
  x.drawImage(bmp, 0, 0, c.width, c.height);
}

// ---- Brush Studio ----------------------------------------------------------------------------
// Every setting of a brush, grouped like Procreate's Brush Studio and Photoshop's Brush Settings.
// Built-in brushes are never changed: saving one makes a copy under My brushes.

const S = (key, label, min, max, step = 0.01, hint = '') => ({ type: 'range', key, label, min, max, step, hint });
const SEL = (key, label, options) => ({ type: 'select', key, label, options });
const CHK = (key, label, hint = '') => ({ type: 'check', key, label, hint });
const STUDIO_GROUPS = [
  { title: 'Size & opacity', fields: [
    S('defaultSize', 'Starting size (px)', 1, 600, 1), S('maxSize', 'Largest size (px)', 4, 1000, 1),
    S('defaultOpacity', 'Starting opacity', 0, 1), S('flow', 'Flow (paint per dab)', 0.01, 1),
  ] },
  { title: 'Tip shape', fields: [
    SEL('tip', 'Tip', 'tip'), S('hardness', 'Hardness (edge)', 0, 1), S('roundness', 'Roundness', 0.05, 1),
    SEL('tipAngle', 'Tip angle follows', 'tipAngle'), S('angleDegrees', 'Angle (°)', 0, 180, 1),
  ] },
  { title: 'Stroke path', fields: [
    S('spacing', 'Spacing (× size)', 0.01, 3), S('streamline', 'StreamLine (smoothing)', 0, 0.97),
    S('taperStart', 'Taper at start', 0, 10, 0.1), S('taperEnd', 'Taper at end', 0, 14, 0.1), S('taperMinSize', 'Taper tip size', 0, 1),
  ] },
  { title: 'Jitter & scatter', fields: [
    S('scatter', 'Scatter (× size)', 0, 4), S('count', 'Dabs per step', 1, 16, 1),
    S('sizeJitter', 'Size jitter', 0, 1), S('opacityJitter', 'Opacity jitter', 0, 1),
    S('rotationJitter', 'Rotation jitter (°)', 0, 180, 1), S('lateralJitter', 'Wobble across the stroke', 0, 1.5),
    S('roundnessJitter', 'Squash jitter', 0, 1), S('flipJitter', 'Mirror at random', 0, 1),
  ] },
  { title: 'Colour dynamics', fields: [
    S('hueJitter', 'Hue jitter', 0, 1), S('saturationJitter', 'Saturation jitter', 0, 1), S('brightnessJitter', 'Brightness jitter', 0, 1),
    CHK('colourJitterPerStroke', 'Change colour once per stroke, not every dab'),
  ] },
  { title: 'Grain & pattern', fields: [
    SEL('grain', 'Texture', 'grain'), S('grainDepth', 'Depth', 0, 1), S('grainScale', 'Scale', 0.2, 4),
    S('tooth', 'Paper tooth (dry media)', 0, 1), S('dust', 'Dust around the edge', 0, 1),
    CHK('grainMoving', 'Texture moves with the brush'), CHK('grainInvert', 'Invert texture'),
  ] },
  { title: 'Rendering', fields: [
    SEL('renderMode', 'Paint behaves like', 'renderMode'), SEL('blendMode', 'Blend mode', 'blendMode'),
    CHK('buildUp', 'Build up: dabs paint over each other (stamps, sprays)'),
  ] },
  { title: 'Pressure, tilt & speed', fields: [
    S('pressureGamma', 'Pressure curve (soft ← → firm)', 0.3, 3), S('pressureSize', 'Pressure → size', 0, 1),
    S('pressureOpacity', 'Pressure → opacity', 0, 1), S('pressureFlow', 'Pressure → flow', 0, 1),
    S('tiltSize', 'Tilt → size', 0, 4, 0.05), S('tiltOpacity', 'Tilt → lighter', 0, 1),
    S('speedSize', 'Speed → thinner', 0, 1), S('speedOpacity', 'Speed → lighter', 0, 1),
  ] },
  { title: 'Wet paint', when: (b) => b.renderMode === 'WET', fields: [
    S('charge', 'Paint load', 0, 1), S('dilution', 'Dilution', 0, 1), S('pull', 'Pull (drags colour)', 0, 1),
    S('dryOut', 'Dry-out streaks', 0, 1), S('streakColour', 'Bristle colour variation', 0, 1),
    S('impasto', 'Bristle shading', 0, 1), S('relief', 'Thick paint relief', 0, 1),
  ] },
  { title: 'Watercolour', when: (b) => b.renderMode === 'WASH', fields: [
    S('edgeDarken', 'Dark edges', 0, 1), S('granulation', 'Granulation', 0, 1),
    S('pigmentFlow', 'Pigment flow', 0, 1), S('edgeWobble', 'Ragged edge', 0, 1),
  ] },
  { title: 'Mixer brush', when: (b) => b.renderMode === 'MIXER', fields: [
    S('mixerWet', 'Wet (picks up canvas)', 0, 1), S('mixerLoad', 'Load', 0, 1), S('mixerMix', 'Mix', 0, 1),
    CHK('mixerSampleAll', 'Pick up from all layers'), CHK('mixerClean', 'Clean the brush after each stroke'),
  ] },
  { title: 'Smudging', fields: [
    CHK('blendOnly', 'Blender: moves paint without adding colour'), CHK('soften', 'Soften instead of drag'),
    S('smudgeStrength', 'Strength with the Smudge tool', 0, 1),
  ] },
];

let studio = null; // { original, brush, sourceId, isCustom }
let studioPreviewBusy = false;
let studioPreviewAgain = false;

async function openStudio(id) {
  const ready = waitFor('brushJson');
  call('brushJson', { id });
  const m = await ready;
  const brush = JSON.parse(m.json);
  studio = { original: m.json, brush, sourceId: id, isCustom: customBrushes.some(b => b.id === id) };
  closePanels();
  $('#p-studio').hidden = false;
  renderStudio();
}

function renderStudio() {
  const b = studio.brush;
  $('#studio-name').value = b.name;
  $('#studio-save').hidden = !studio.isCustom;
  $('#studio-delete').hidden = !studio.isCustom;
  $('#studio-copy').textContent = studio.isCustom ? 'Save as copy' : 'Save as my brush';
  const body = $('#studio-body');
  const scroll = body.scrollTop;
  body.textContent = '';
  for (const g of STUDIO_GROUPS) {
    if (g.when && !g.when(b)) continue;
    const sec = document.createElement('section');
    const h = document.createElement('h3');
    h.textContent = g.title;
    sec.append(h);
    for (const f of g.fields) sec.append(studioField(f, b));
    body.append(sec);
  }
  body.scrollTop = scroll;
  studioPreview();
}

function studioField(f, b) {
  const row = document.createElement('label');
  row.className = 'studio-field ' + f.type;
  const name = document.createElement('span');
  name.textContent = f.label;
  row.append(name);
  if (f.type === 'range') {
    const input = document.createElement('input');
    input.type = 'range';
    input.min = f.min; input.max = f.max; input.step = f.step;
    input.value = b[f.key];
    const out = document.createElement('output');
    const show = () => { out.textContent = f.step >= 1 ? String(Math.round(b[f.key])) : (+b[f.key]).toFixed(2); };
    show();
    input.addEventListener('input', () => {
      b[f.key] = f.step >= 1 ? Math.round(+input.value) : +input.value;
      show();
      studioPreview();
    });
    row.append(input, out);
  } else if (f.type === 'select') {
    const sel = document.createElement('select');
    for (const o of brushOptions[f.options]) {
      const opt = document.createElement('option');
      opt.value = o.id; opt.textContent = o.name;
      sel.append(opt);
    }
    sel.value = b[f.key];
    sel.addEventListener('change', () => {
      b[f.key] = sel.value;
      if (f.key === 'tip') b.tipImage = null;
      // Some groups only apply to one kind of paint.
      if (f.key === 'renderMode') renderStudio(); else studioPreview();
    });
    row.append(sel);
  } else {
    const box = document.createElement('input');
    box.type = 'checkbox';
    box.checked = !!b[f.key];
    box.addEventListener('change', () => { b[f.key] = box.checked; studioPreview(); });
    row.append(box);
  }
  return row;
}

/** Repaints the preview with the real engine, at most one request in flight. */
async function studioPreview() {
  if (studioPreviewBusy) { studioPreviewAgain = true; return; }
  studioPreviewBusy = true;
  await startPreviews();
  previewWorker.postMessage({ t: 'previewJson', json: JSON.stringify(studio.brush), serial: ++studioPreviewSerial, width: 960, height: 160, color: PREVIEW.color, background: PREVIEW.background });
}
function onStudioPreview(m) {
  const c = $('#studio-preview');
  c.getContext('2d').drawImage(m.bitmap, 0, 0, c.width, c.height);
  studioPreviewBusy = false;
  if (studioPreviewAgain && studio) { studioPreviewAgain = false; studioPreview(); }
}

function closeStudio() {
  $('#p-studio').hidden = true;
  studio = null;
}

function useBrush(id) {
  const slot = slotFor(tool);
  settings.brushes[slot] = id;
  saveSettings();
  if (!paintTools.includes(tool)) tool = slot;
  updateToolUi();
}

function saveStudio(asCopy) {
  const b = { ...studio.brush, name: $('#studio-name').value.trim() || studio.brush.name };
  if (asCopy) {
    b.id = 'mine_' + Date.now().toString(36);
    b.category = 'MINE';
    if (studio.isCustom && b.name === JSON.parse(studio.original).name) b.name += ' copy';
    b.importedFrom = null;
    customBrushes.unshift(b);
  } else {
    const i = customBrushes.findIndex(x => x.id === b.id);
    if (i >= 0) customBrushes[i] = b; else customBrushes.unshift(b);
  }
  saveCustomBrushes();
  pushCustomBrushes([b.id]);
  useBrush(b.id);
  toast(asCopy ? `Saved “${b.name}” in My brushes` : `Saved “${b.name}”`);
  closeStudio();
  brushCategory = 'MINE';
}

$('#studio-close').addEventListener('click', closeStudio);
$('#studio-reset').addEventListener('click', () => { studio.brush = JSON.parse(studio.original); renderStudio(); });
$('#studio-save').addEventListener('click', () => saveStudio(false));
$('#studio-copy').addEventListener('click', () => saveStudio(true));
$('#studio-delete').addEventListener('click', async () => {
  const b = studio.brush;
  if (!await confirmDelete('Delete brush?', `“${b.name}” will be removed from My brushes.`)) return;
  customBrushes = customBrushes.filter(x => x.id !== b.id);
  saveCustomBrushes();
  pushCustomBrushes([b.id]);
  for (const k of Object.keys(settings.brushes)) if (settings.brushes[k] === b.id) settings.brushes[k] = { brush: 'studio_pen', smudge: 'soft_blend', eraser: 'hard_airbrush' }[k];
  saveSettings();
  updateToolUi();
  closeStudio();
});
$('#studio-export').addEventListener('click', () => {
  const b = { ...studio.brush, name: $('#studio-name').value.trim() || studio.brush.name };
  const blob = new Blob([JSON.stringify([b], null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = b.name.replace(/[^\w\- ]+/g, '').trim() + '.brush.json';
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
});
$('#brush-import').addEventListener('click', () => $('#brush-import-file').click());
$('#brush-import-file').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  e.target.value = '';
  if (!file) return;
  try {
    let list = JSON.parse(await file.text());
    if (!Array.isArray(list)) list = [list];
    list = list.filter(b => b && typeof b === 'object' && b.name);
    if (!list.length) throw new Error('no brushes');
    for (const [i, b] of list.entries()) { b.id = 'mine_' + Date.now().toString(36) + i; b.category = 'MINE'; customBrushes.unshift(b); }
    saveCustomBrushes();
    pushCustomBrushes();
    brushCategory = 'MINE';
    toast(`Added ${list.length} brush${list.length > 1 ? 'es' : ''} to My brushes`);
  } catch { toast('That file is not an Artist Haven brush'); }
});

// Colour panel
const wheel = $('#color-wheel');
const wctx = wheel.getContext('2d');
let hsv = argbToHsv(settings.color);
let colorAtOpen = settings.color;
const WHEEL = { c: 280, outer: 274, inner: 222 };
const squareHalf = () => WHEEL.inner * Math.SQRT1_2 - 10;

function openColorPanel() {
  colorAtOpen = settings.color;
  hsv = argbToHsv(settings.color);
  syncColorPanel();
}
function syncColorPanel() {
  const cur = argbToHsv(settings.color);
  if (hsvToArgb(hsv.h, hsv.s, hsv.v) !== (settings.color | 0)) hsv = cur;
  drawWheel();
  $('#color-hex').value = hex(settings.color);
  $('#color-old').style.background = hex(colorAtOpen);
  $('#color-new').style.background = hex(settings.color);
  renderSwatches();
}
function drawWheel() {
  const { c, outer, inner } = WHEEL;
  wctx.clearRect(0, 0, 560, 560);
  const g = wctx.createConicGradient(0, c, c);
  for (let i = 0; i <= 12; i++) g.addColorStop(i / 12, `hsl(${i * 30}, 100%, 50%)`);
  wctx.beginPath();
  wctx.arc(c, c, outer, 0, Math.PI * 2);
  wctx.arc(c, c, inner, 0, Math.PI * 2, true);
  wctx.fillStyle = g;
  wctx.fill();
  const h = squareHalf();
  wctx.fillStyle = `hsl(${hsv.h}, 100%, 50%)`;
  wctx.fillRect(c - h, c - h, h * 2, h * 2);
  const gw = wctx.createLinearGradient(c - h, 0, c + h, 0);
  gw.addColorStop(0, '#fff'); gw.addColorStop(1, 'rgba(255,255,255,0)');
  wctx.fillStyle = gw; wctx.fillRect(c - h, c - h, h * 2, h * 2);
  const gb = wctx.createLinearGradient(0, c - h, 0, c + h);
  gb.addColorStop(0, 'rgba(0,0,0,0)'); gb.addColorStop(1, '#000');
  wctx.fillStyle = gb; wctx.fillRect(c - h, c - h, h * 2, h * 2);
  // markers
  const a = hsv.h * Math.PI / 180, rm = (outer + inner) / 2;
  ring(c + Math.cos(a) * rm, c + Math.sin(a) * rm, 18);
  ring(c - h + hsv.s * h * 2, c - h + (1 - hsv.v) * h * 2, 14);
  function ring(x, y, r) {
    wctx.lineWidth = 5; wctx.strokeStyle = '#fff';
    wctx.beginPath(); wctx.arc(x, y, r, 0, Math.PI * 2); wctx.stroke();
    wctx.lineWidth = 1.5; wctx.strokeStyle = 'rgba(0,0,0,.5)';
    wctx.beginPath(); wctx.arc(x, y, r + 3.5, 0, Math.PI * 2); wctx.stroke();
  }
}
let wheelMode = null;
function wheelPick(e, start) {
  const r = wheel.getBoundingClientRect();
  const x = (e.clientX - r.left) * 560 / r.width - WHEEL.c, y = (e.clientY - r.top) * 560 / r.height - WHEEL.c;
  const d = Math.hypot(x, y), h = squareHalf();
  if (start) wheelMode = d > WHEEL.inner - 4 ? 'hue' : (Math.abs(x) <= h + 12 && Math.abs(y) <= h + 12 ? 'sv' : null);
  if (wheelMode === 'hue') hsv.h = (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
  else if (wheelMode === 'sv') { hsv.s = clamp((x + h) / (2 * h), 0, 1); hsv.v = clamp(1 - (y + h) / (2 * h), 0, 1); }
  else return;
  settings.color = hsvToArgb(hsv.h, hsv.s, hsv.v);
  $('#b-color span').style.background = hex(settings.color);
  drawWheel();
  $('#color-hex').value = hex(settings.color);
  $('#color-new').style.background = hex(settings.color);
  saveSettings();
}
wheel.addEventListener('pointerdown', (e) => { wheel.setPointerCapture(e.pointerId); wheelPick(e, true); });
wheel.addEventListener('pointermove', (e) => { if (wheel.hasPointerCapture(e.pointerId)) wheelPick(e, false); });
$('#color-hex').addEventListener('change', (e) => {
  const c = fromHex(e.target.value);
  if (c !== null) { hsv = argbToHsv(c); setColor(c); } else e.target.value = hex(settings.color);
});
$('#palette-add').addEventListener('click', () => {
  const c = settings.color | 0;
  if (!settings.palette.includes(c)) settings.palette = [...settings.palette, c];
  saveSettings();
  renderSwatches();
});
function renderSwatches() {
  const make = (box, colors, removable) => {
    box.textContent = '';
    for (const c of colors) {
      const b = document.createElement('button');
      b.style.background = hex(c);
      b.title = hex(c);
      b.addEventListener('click', () => { hsv = argbToHsv(c); setColor(c); });
      if (removable) {
        const remove = (e) => {
          e.preventDefault();
          settings.palette = settings.palette.filter(x => x !== c);
          saveSettings();
          renderSwatches();
        };
        b.addEventListener('contextmenu', remove);
      }
      box.append(b);
    }
  };
  make($('#recent-swatches'), settings.recent, false);
  make($('#palette-swatches'), settings.palette, true);
}

// Layers panel
let layerThumbTimer = 0;
const layerThumbs = new Map();
function renderLayers() {
  const list = $('#layer-list');
  list.textContent = '';
  const groups = new Map((doc.groups || []).map(g => [g.id, g]));
  for (const [i, l] of doc.layers.entries()) {
    const g = l.group ? groups.get(l.group) : null;
    const lastOfGroup = g && doc.layers[i + 1]?.group !== l.group;
    if (g && collapsedGroups.has(g.id)) {
      if (lastOfGroup) list.append(groupRow(g, doc.layers.filter(x => x.group === g.id).length));
      continue;
    }
    const row = document.createElement('div');
    row.className = 'layer-row' + (l.active ? ' active' : '') + (l.visible ? '' : ' hidden-layer') + (g ? ' in-group' : '');
    row.dataset.id = l.id;
    const th = document.createElement('div');
    th.className = 'lthumb' + (l.active && l.hasMask && !l.editingMask ? ' editing' : '');
    const c = document.createElement('canvas');
    const bmp = layerThumbs.get(l.id);
    if (bmp) { c.width = bmp.width; c.height = bmp.height; c.getContext('2d').drawImage(bmp, 0, 0); }
    else { c.width = 1; c.height = 1; }
    th.append(c);
    let mk = null;
    if (l.hasMask) {
      mk = document.createElement('div');
      mk.className = 'lmask' + (l.editingMask ? ' editing' : '');
      mk.title = l.editingMask ? 'Painting on the mask (tap the picture to paint the layer)' : 'Tap to paint on the mask';
      const mc = document.createElement('canvas');
      const mb = maskThumbs.get(l.id);
      if (mb) { mc.width = mb.width; mc.height = mb.height; mc.getContext('2d').drawImage(mb, 0, 0); } else { mc.width = 1; mc.height = 1; }
      mk.append(mc);
      mk.addEventListener('click', (e) => {
        e.stopPropagation();
        if (!l.active) call('layer', { op: 'select', args: [l.id] });
        call('layer', { op: 'editMask', args: [true] });
      });
      th.addEventListener('click', (e) => {
        if (!l.active || !l.editingMask) return;
        e.stopPropagation();
        call('layer', { op: 'editMask', args: [false] });
      });
    }
    const name = document.createElement('div');
    name.className = 'lname';
    const mode = blendModes.find(m => m.id === l.blendMode)?.name || 'Normal';
    const flags = [l.alphaLock ? 'alpha lock' : '', l.clipping ? 'clipped' : ''].filter(Boolean).join(', ');
    name.innerHTML = `<div></div><small>${mode} · ${Math.round(l.opacity * 100)}%${flags ? ' · ' + flags : ''}</small>`;
    name.firstChild.textContent = l.name;
    const vis = document.createElement('button');
    vis.className = 'icon-btn small';
    vis.title = l.visible ? 'Hide layer' : 'Show layer';
    vis.innerHTML = `<svg><use href="#i-${l.visible ? 'eye' : 'eye-off'}"/></svg>`;
    vis.addEventListener('click', (e) => {
      e.stopPropagation();
      call('layer', { op: 'props', args: [l.id, l.name, !l.visible, l.opacity, l.blendMode, l.alphaLock, l.clipping] });
    });
    row.append(...[th, mk, name, vis].filter(Boolean));
    row.addEventListener('click', (e) => {
      if (l.active) layerMenu(l, i, e.clientX, e.clientY);
      else call('layer', { op: 'select', args: [l.id] });
    });
    row.addEventListener('contextmenu', (e) => { e.preventDefault(); layerMenu(l, i, e.clientX, e.clientY); });
    list.append(row);
    if (lastOfGroup) list.append(groupRow(g, doc.layers.filter(x => x.group === g.id).length));
  }
  const active = doc.layers.find(l => l.active);
  if (active && !layerOpacityDragging) {
    $('#layer-opacity').value = Math.round(active.opacity * 100);
    $('#layer-opacity-val').textContent = Math.round(active.opacity * 100) + '%';
  }
  $('#paper-swatch').style.background = hex(doc.paperColor);
  $('#paper-vis').innerHTML = `<svg><use href="#i-${doc.paperVisible ? 'eye' : 'eye-off'}"/></svg>`;
  clearTimeout(layerThumbTimer);
  layerThumbTimer = setTimeout(() => call('layerThumbs', { size: 128 }), 120);
}
const maskThumbs = new Map();
function onLayerThumbs(thumbs) {
  for (const t of thumbs) {
    layerThumbs.set(t.id, t.bitmap);
    if (t.mask) maskThumbs.set(t.id, t.mask); else maskThumbs.delete(t.id);
  }
  const paint = (c, bmp) => { if (!c || !bmp) return; c.width = bmp.width; c.height = bmp.height; c.getContext('2d').drawImage(bmp, 0, 0); };
  for (const row of $$('.layer-row')) {
    paint(row.querySelector('.lthumb canvas'), layerThumbs.get(row.dataset.id));
    paint(row.querySelector('.lmask canvas'), maskThumbs.get(row.dataset.id));
  }
}
$('#layer-add').addEventListener('click', () => call('layer', { op: 'add' }));

// Layer opacity: previewed live while dragging, one undo step when let go.
let layerOpacityDragging = false;
function showLayerOpacity(l, v) {
  l.opacity = v;
  const idx = doc.layers.indexOf(l);
  const small = $$('.layer-row')[idx]?.querySelector('small');
  if (small) {
    const mode = blendModes.find(m => m.id === l.blendMode)?.name || 'Normal';
    const flags = [l.alphaLock ? 'alpha lock' : '', l.clipping ? 'clipped' : ''].filter(Boolean).join(', ');
    small.textContent = `${mode} · ${Math.round(v * 100)}%${flags ? ' · ' + flags : ''}`;
  }
}
$('#layer-opacity').addEventListener('input', (e) => {
  const l = doc.layers.find(x => x.active);
  if (!l) return;
  layerOpacityDragging = true;
  const v = +e.target.value / 100;
  $('#layer-opacity-val').textContent = Math.round(v * 100) + '%';
  showLayerOpacity(l, v);
  call('layer', { op: 'previewOpacity', args: [l.id, v] });
});
$('#layer-opacity').addEventListener('change', () => {
  const l = doc.layers.find(x => x.active);
  layerOpacityDragging = false;
  if (l) call('layer', { op: 'commitOpacity', args: [l.id] });
});
$('#paper-vis').addEventListener('click', () => call('paper', { color: doc.paperColor, visible: !doc.paperVisible }));
$('#paper-swatch').addEventListener('click', () => { togglePanel('p-actions'); });

function layerMenu(l, index, x, y) {
  const menu = $('#layer-menu');
  const n = doc.layers.length;
  const modes = blendModes.map(m => `<option value="${m.id}"${m.id === l.blendMode ? ' selected' : ''}>${m.name}</option>`).join('');
  menu.innerHTML = `
    <button data-op="rename">Rename</button>
    <label>Opacity <input type="range" min="0" max="1" step="0.01" value="${l.opacity}" data-op="opacity"><output>${Math.round(l.opacity * 100)}%</output></label>
    <label>Blend <select data-op="blend">${modes}</select></label>
    <label>Alpha lock <input type="checkbox" data-op="alphaLock"${l.alphaLock ? ' checked' : ''}></label>
    <label>Clip to layer below <input type="checkbox" data-op="clipping"${l.clipping ? ' checked' : ''}></label>
    <hr>
    ${l.hasMask
      ? `<button data-op="editMask">${l.editingMask ? 'Paint on the layer' : 'Paint on the mask'}</button>
         <button data-op="applyMask">Apply mask</button>
         <button data-op="deleteMask">Delete mask</button>`
      : '<button data-op="addMask" title="Hide parts of the layer without erasing them">Add mask</button>'}
    <hr>
    ${index > 0 ? '<button data-op="groupBelow">Group with layer below</button>' : ''}
    ${l.group ? '<button data-op="leaveGroup">Take out of group</button>' : '<button data-op="group">New group from layer</button>'}
    <hr>
    <button data-op="up"${index >= n - 1 ? ' disabled' : ''}>Move up</button>
    <button data-op="down"${index <= 0 ? ' disabled' : ''}>Move down</button>
    <button data-op="duplicate">Duplicate</button>
    <button data-op="mergeDown"${index <= 0 ? ' disabled' : ''}>Merge down</button>
    <button data-op="clear">Clear</button>
    <button data-op="delete" class="danger"${n <= 1 ? ' disabled' : ''}>Delete</button>`;
  const props = (over) => {
    const p = { ...l, ...over };
    call('layer', { op: 'props', args: [l.id, p.name, p.visible, p.opacity, p.blendMode, p.alphaLock, p.clipping] });
    Object.assign(l, over);
  };
  menu.onclick = async (e) => {
    const op = e.target.closest('button')?.dataset.op;
    if (!op) return;
    closeMenu();
    if (op === 'rename') { const name = await prompt('Rename layer', l.name); if (name) props({ name }); }
    if (op === 'up') call('layer', { op: 'move', args: [index, index + 1] });
    if (op === 'down') call('layer', { op: 'move', args: [index, index - 1] });
    if (op === 'duplicate') call('layer', { op: 'duplicate', args: [l.id] });
    if (op === 'mergeDown') call('layer', { op: 'mergeDown', args: [l.id] });
    if (op === 'clear') call('layer', { op: 'clear', args: [l.id] });
    if (op === 'delete') call('layer', { op: 'delete', args: [l.id] });
    if (op === 'addMask') { call('layer', { op: 'addMask', args: [l.id] }); toast('Paint black on the mask to hide, white to bring back.', 3500); }
    if (op === 'editMask') call('layer', { op: 'editMask', args: [!l.editingMask] });
    if (op === 'applyMask') call('layer', { op: 'applyMask', args: [l.id] });
    if (op === 'deleteMask') call('layer', { op: 'deleteMask', args: [l.id] });
    if (op === 'groupBelow') call('layer', { op: 'group', args: [l.id, true] });
    if (op === 'group') call('layer', { op: 'group', args: [l.id, false] });
    if (op === 'leaveGroup') call('layer', { op: 'leaveGroup', args: [l.id] });
  };
  const range = menu.querySelector('[data-op=opacity]');
  range.addEventListener('input', () => {
    const v = parseFloat(range.value);
    // Keep the menu's copy current, or changing the blend mode next would put the old opacity back.
    showLayerOpacity(l, v);
    range.nextElementSibling.textContent = Math.round(v * 100) + '%';
    call('layer', { op: 'previewOpacity', args: [l.id, v] });
  });
  range.addEventListener('change', () => call('layer', { op: 'commitOpacity', args: [l.id] }));
  menu.querySelector('[data-op=blend]').addEventListener('change', (e) => props({ blendMode: e.target.value }));
  menu.querySelector('[data-op=alphaLock]').addEventListener('change', (e) => props({ alphaLock: e.target.checked }));
  menu.querySelector('[data-op=clipping]').addEventListener('change', (e) => props({ clipping: e.target.checked }));
  openMenu(menu, x - 220, y);
}

// Actions panel
function syncActions() {
  $('#paper-color').value = hex(doc.paperColor);
  $('#symmetry').value = String(settings.symmetry);
  $('#streamline').value = settings.streamline;
  $('#fill-tol').value = settings.fillTolerance;
  $('#fill-all').checked = settings.fillAll;
  $('#finger-paint').checked = settings.fingerPaint;
  $('#right-hand').checked = settings.rightHand;
}
$('#paper-color').addEventListener('input', (e) => call('paper', { color: fromHex(e.target.value), visible: true }));
$('#symmetry').addEventListener('change', (e) => { settings.symmetry = +e.target.value; saveSettings(); drawOverlay(); });
$('#streamline').addEventListener('input', (e) => { settings.streamline = +e.target.value; saveSettings(); });
$('#fill-tol').addEventListener('input', (e) => { settings.fillTolerance = +e.target.value; saveSettings(); });
$('#fill-all').addEventListener('change', (e) => { settings.fillAll = e.target.checked; saveSettings(); });
$('#finger-paint').addEventListener('change', (e) => { settings.fingerPaint = e.target.checked; saveSettings(); });
$('#right-hand').addEventListener('change', (e) => { settings.rightHand = e.target.checked; saveSettings(); layoutSidebar(); });
$('#p-actions').addEventListener('click', async (e) => {
  const act = e.target.closest('[data-act]')?.dataset.act;
  if (!act) return;
  if (act === 'photo') $('#canvas-file').click();
  if (act === 'paste') pasteImage();
  if (act === 'png') call('export', { type: 'image/png', withPaper: true });
  if (act === 'png-clear') call('export', { type: 'image/png', withPaper: false });
  if (act === 'jpg') call('export', { type: 'image/jpeg', withPaper: true });
  if (act === 'psd') { toast('Making the Photoshop file…', 4000); call('exportPsd'); }
  if (act === 'timelapse') openTimelapse();
  if (act === 'pressure') openPressure();
  if (act === 'text') openTextDialog();
  if (act === 'reference') $('#ref-file').click();
  if (act === 'rename') {
    const name = await prompt('Rename drawing', current?.name);
    if (name && current) {
      current.name = name;
      $('#doc-title').textContent = name;
      call('rename', { name });
    }
  }
  if (act === 'fit') fitView();
  if (act === 'flip') flipView();
  if (act !== 'rename') closePanels();
});

// Photos
function imagePixels(bmp, w, h, name) {
  const c = new OffscreenCanvas(w, h);
  const x = c.getContext('2d');
  x.drawImage(bmp, 0, 0, w, h);
  const data = x.getImageData(0, 0, w, h).data;
  return { name, width: w, height: h, rgba: data.buffer };
}
async function insertImage(source, name) {
  const bmp = await createImageBitmap(source);
  const k = Math.min(1, doc.width / bmp.width, doc.height / bmp.height);
  const w = Math.max(1, Math.round(bmp.width * k)), h = Math.max(1, Math.round(bmp.height * k));
  const p = imagePixels(bmp, w, h, name || 'Photo');
  call('importImage', { name: p.name, rgba: p.rgba, width: w, height: h }, [p.rgba]);
}
$('#canvas-file').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  e.target.value = '';
  if (!file) return;
  try { await insertImage(file, file.name.replace(/\.[^.]+$/, '')); } catch { toast("That picture couldn't be opened."); }
});
async function pasteImage() {
  try {
    const items = await navigator.clipboard.read();
    for (const item of items) {
      const type = item.types.find(t => t.startsWith('image/'));
      if (type) { await insertImage(await item.getType(type), 'Pasted'); return; }
    }
    toast('There is no picture on the clipboard.');
  } catch {
    toast('Press Ctrl+V to paste a picture.');
  }
}
document.addEventListener('paste', (e) => {
  if (!current || $('#canvas-screen').hidden) return;
  const file = [...(e.clipboardData?.files || [])].find(f => f.type.startsWith('image/'));
  if (file) { e.preventDefault(); insertImage(file, 'Pasted').catch(() => toast("That picture couldn't be pasted.")); }
});
stage.addEventListener('dragover', (e) => e.preventDefault());
stage.addEventListener('drop', (e) => {
  e.preventDefault();
  const file = [...(e.dataTransfer?.files || [])].find(f => f.type.startsWith('image/'));
  if (file) insertImage(file, file.name.replace(/\.[^.]+$/, '')).catch(() => toast("That picture couldn't be opened."));
});

// Export
let pendingExportName = null;
function exportName(type) {
  const base = (pendingExportName || current?.name || 'Drawing').replace(/[\\/:*?"<>|]+/g, ' ').trim() || 'Drawing';
  pendingExportName = null;
  return base + (type === 'image/jpeg' ? '.jpg' : type === 'image/vnd.adobe.photoshop' ? '.psd' : '.png');
}
async function saveBlob(blob, name) {
  if (window.showSaveFilePicker) {
    try {
      const jpeg = blob.type === 'image/jpeg';
      const psd = blob.type === 'image/vnd.adobe.photoshop';
      const handle = await showSaveFilePicker({
        suggestedName: name,
        startIn: 'pictures',
        types: [{ description: psd ? 'Photoshop file' : jpeg ? 'JPEG picture' : 'PNG picture', accept: { [blob.type]: [psd ? '.psd' : jpeg ? '.jpg' : '.png'] } }],
      });
      const w = await handle.createWritable();
      await w.write(blob);
      await w.close();
      toast('Saved ' + handle.name);
      return;
    } catch (err) {
      if (err && err.name === 'AbortError') return;
    }
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 10000);
  toast('Saved ' + name + ' to Downloads');
}

// ---- Input: pen, touch, mouse ----------------------------------------------------------------

const input = {
  mode: null,          // paint | gesture | pan | dropper | fill
  pointerId: null,
  touches: new Map(),  // pointerId -> {x, y}
  gesture: null,
  tap: null,           // multi-finger tap tracking
  hover: null,         // [x, y] for the brush outline
  snapped: false,
  holdTimer: 0,
  holdAt: null,
  space: false,
  alt: false,
  start: null,
};

function penTilt(e) {
  let tilt = 0, az = 0;
  if (typeof e.altitudeAngle === 'number' && e.pointerType === 'pen') {
    tilt = Math.PI / 2 - e.altitudeAngle;
    az = e.azimuthAngle || 0;
  } else if (e.tiltX || e.tiltY) {
    const tx = Math.tan(e.tiltX * Math.PI / 180), ty = Math.tan(e.tiltY * Math.PI / 180);
    tilt = Math.atan(Math.hypot(tx, ty));
    az = Math.atan2(ty, tx);
  }
  // Turn the screen angle into a canvas angle.
  az -= view.r;
  if (view.f < 0) az = Math.PI - az;
  return [clamp(tilt, 0, Math.PI / 2), az];
}

function pointFrom(e) {
  const [x, y] = toDoc(e.clientX, e.clientY);
  const pen = e.pointerType === 'pen';
  let p = pen ? pressureCurve(e.pressure) : 1;
  if (pen && p === 0) p = 0.02;
  const [tilt, az] = pen ? penTilt(e) : [0, 0];
  return [x, y, clamp(p, 0, 1), tilt, az, e.timeStamp];
}

const isEraserTip = (e) => e.pointerType === 'pen' && (e.button === 5 || (e.buttons & 32) !== 0);
const isBarrel = (e) => e.pointerType === 'pen' && (e.buttons & 2) !== 0 && !(e.buttons & 32);

function strokeParams(e) {
  const eraserTip = isEraserTip(e);
  const t = eraserTip ? 'eraser' : (paintTools.includes(tool) ? tool : lastPaintTool);
  return {
    brushId: brushFor(t).id,
    tool: TOOL_IDS[t],
    size: sizeFor(t),
    opacity: opacityFor(t),
    color: settings.color,
    symmetry: settings.symmetry,
    usePressure: e.pointerType === 'pen',
    streamline: settings.streamline,
    paintLoad: eraserTip ? -1 : loadFor(t),
  };
}

function beginPaint(e) {
  input.mode = 'paint';
  input.pointerId = e.pointerId;
  input.snapped = false;
  input.holdAt = [e.clientX, e.clientY];
  input.start = { t: e.timeStamp, x: e.clientX, y: e.clientY, type: e.pointerType };
  if (paintTools.includes(tool) && tool !== 'eraser' && !isEraserTip(e) && tool !== 'smudge' && settings.recent[0] !== (settings.color | 0)) {
    pushRecent(settings.color);
  }
  call('begin', { params: strokeParams(e), point: pointFrom(e) });
  armHold();
}

function armHold() {
  clearTimeout(input.holdTimer);
  input.holdTimer = setTimeout(() => {
    if (input.mode === 'paint' && !input.snapped) call('quickShape');
  }, 520);
}

function movePaint(e) {
  if (input.snapped) return;
  const events = e.getCoalescedEvents ? e.getCoalescedEvents() : [];
  const list = events.length ? events : [e];
  const pts = [];
  for (const ev of list) pts.push(...pointFrom(ev));
  call('move', { points: pts });
  if (Math.hypot(e.clientX - input.holdAt[0], e.clientY - input.holdAt[1]) > 4) {
    input.holdAt = [e.clientX, e.clientY];
    armHold();
  }
}

function endPaint(cancel) {
  clearTimeout(input.holdTimer);
  call(cancel ? 'cancel' : 'end');
  input.mode = null;
  input.pointerId = null;
}

// Eyedropper: the worker samples, the loupe shows the colour under the pen.
let sampleSeq = 0;
function sampleAt(e, final) {
  const [x, y] = toDoc(e.clientX, e.clientY);
  if (x < 0 || y < 0 || x >= doc.width || y >= doc.height) return;
  const loupe = $('#loupe');
  loupe.style.left = e.clientX + 'px';
  loupe.style.top = e.clientY + 'px';
  loupe.hidden = !!final;
  call('sample', { x: Math.floor(x), y: Math.floor(y), final, id: ++sampleSeq });
}
function onSampled(m) {
  $('#loupe').style.background = hex(m.color);
  $('#loupe').style.borderColor = hex(m.color);
  if (m.final) {
    $('#loupe').hidden = true;
    hsv = argbToHsv(m.color);
    setColor(m.color, true);
    if (tool === 'dropper') { tool = lastPaintTool; updateToolUi(); }
  } else {
    setColor(m.color);
  }
}

// Two-finger move, zoom and turn: keep the two canvas points under the fingers.
function startGesture() {
  const pts = [...input.touches.values()].slice(0, 2);
  if (pts.length < 2) return;
  input.gesture = { d1: toDoc(pts[0].x, pts[0].y), d2: toDoc(pts[1].x, pts[1].y), ids: [...input.touches.keys()].slice(0, 2) };
}
function moveGesture() {
  const g = input.gesture;
  if (!g) return;
  const q1 = input.touches.get(g.ids[0]), q2 = input.touches.get(g.ids[1]);
  if (!q1 || !q2) return;
  const dvx = (g.d2[0] - g.d1[0]) * view.f, dvy = g.d2[1] - g.d1[1];
  const qvx = q2.x - q1.x, qvy = q2.y - q1.y;
  const dl = Math.hypot(dvx, dvy);
  if (dl < 1e-3) return;
  view.s = clamp(Math.hypot(qvx, qvy) / dl, 0.05, 40);
  let r = Math.atan2(qvy, qvx) - Math.atan2(dvy, dvx);
  const quarter = Math.PI / 2;
  const snap = Math.round(r / quarter) * quarter;
  if (Math.abs(r - snap) < 0.06) r = snap;
  view.r = r;
  const [sx, sy] = toScreen(g.d1[0], g.d1[1]);
  view.tx += q1.x - sx;
  view.ty += q1.y - sy;
  applyView();
}

let panLast = null;

stage.addEventListener('pointerdown', (e) => {
  if (!current) return;
  // A tap on the canvas closes an open panel and does nothing else.
  if ($$('.panel').some(p => !p.hidden) || openMenuEl) {
    closePanels();
    if (e.pointerType !== 'pen') return;
  }
  try { stage.setPointerCapture(e.pointerId); } catch { /* synthetic events */ }
  const pen = e.pointerType === 'pen';
  if (pen && !settings.stylusSeen) { settings.stylusSeen = true; saveSettings(); }
  input.hover = pen || e.pointerType === 'mouse' ? [e.clientX, e.clientY] : null;

  if (e.pointerType === 'touch') {
    input.touches.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const n = input.touches.size;
    if (!input.tap || n === 1) input.tap = { t: e.timeStamp, max: n, moved: 0 };
    input.tap.max = Math.max(input.tap.max, n);
    if (n >= 2) {
      // A second finger right after the first turns a finger stroke into a gesture.
      if (input.mode === 'paint' && input.start && input.start.type === 'touch') {
        endPaint(e.timeStamp - input.start.t < 300);
      }
      if (input.mode === 'dropper') $('#loupe').hidden = true;
      if (input.mode === 'select') { selDraft = null; }
      if (input.mode === 'liquify') endLiquify();
      if (input.mode === 'xf') endXfDrag();
      input.mode = 'gesture';
      startGesture();
      return;
    }
    const fingerPaints = settings.fingerPaint || !settings.stylusSeen;
    if (!fingerPaints) { input.mode = 'pan'; panLast = [e.clientX, e.clientY]; return; }
  } else if (input.mode === 'gesture') {
    return;
  }

  if (e.pointerType === 'mouse' && (e.button === 1 || e.button === 2 || input.space)) {
    input.mode = 'pan';
    panLast = [e.clientX, e.clientY];
    stage.classList.add('panning');
    return;
  }
  if (input.mode === 'paint') endPaint(false);
  if (adjusting) return;
  if (tool === 'move') {
    if (xf) beginXfDrag(e);
    return;
  }
  if (tool === 'select' && !isBarrel(e)) { beginSelect(e); return; }
  if (tool === 'liquify' && !isBarrel(e) && !input.alt) { beginLiquify(e); return; }

  if (tool === 'dropper' || input.alt || isBarrel(e)) {
    input.mode = 'dropper';
    input.pointerId = e.pointerId;
    sampleAt(e, false);
    return;
  }
  if (tool === 'fill') {
    input.mode = 'fill';
    input.pointerId = e.pointerId;
    input.start = { x: e.clientX, y: e.clientY };
    return;
  }
  beginPaint(e);
});

stage.addEventListener('pointermove', (e) => {
  if (e.pointerType !== 'touch') {
    input.hover = [e.clientX, e.clientY];
    if (input.mode !== 'paint') drawOverlay();
  }
  if (e.pointerType === 'touch' && input.touches.has(e.pointerId)) {
    const t = input.touches.get(e.pointerId);
    if (input.tap) input.tap.moved = Math.max(input.tap.moved, Math.hypot(e.clientX - t.x, e.clientY - t.y) + (input.tap.moved || 0) * 0);
    t.x = e.clientX; t.y = e.clientY;
    if (input.mode === 'gesture') { moveGesture(); return; }
  }
  if (input.mode === 'pan' && panLast) {
    view.tx += e.clientX - panLast[0];
    view.ty += e.clientY - panLast[1];
    panLast = [e.clientX, e.clientY];
    applyView();
    return;
  }
  if (e.pointerId !== input.pointerId) return;
  if (input.mode === 'paint') { movePaint(e); drawOverlay(); }
  else if (input.mode === 'dropper') sampleAt(e, false);
  else if (input.mode === 'select') moveSelect(e);
  else if (input.mode === 'xf') moveXfDrag(e);
  else if (input.mode === 'liquify') { moveLiquify(e); drawOverlay(); }
});

function pointerEnd(e, cancelled) {
  if (e.pointerType === 'touch') {
    input.touches.delete(e.pointerId);
    if (input.mode === 'gesture') {
      if (input.touches.size >= 2) startGesture();
      else if (input.touches.size === 1) { input.gesture = null; }
      if (input.touches.size === 0) {
        const tap = input.tap;
        if (!cancelled && tap && e.timeStamp - tap.t < 350 && tap.moved < 14) {
          if (tap.max === 2) { undo(); toast('Undo', 900); }
          if (tap.max === 3) { redo(); toast('Redo', 900); }
        }
        input.mode = null;
        input.gesture = null;
        input.tap = null;
      }
      return;
    }
    if (input.touches.size === 0) input.tap = null;
  }
  if (input.mode === 'pan') {
    if (e.pointerType !== 'touch' || input.touches.size === 0) { input.mode = null; panLast = null; stage.classList.remove('panning'); }
    return;
  }
  if (e.pointerId !== input.pointerId) return;
  if (input.mode === 'paint') endPaint(false);
  else if (input.mode === 'select') endSelect(e, cancelled);
  else if (input.mode === 'xf') endXfDrag();
  else if (input.mode === 'liquify') endLiquify();
  else if (input.mode === 'dropper') { sampleAt(e, true); input.mode = null; input.pointerId = null; }
  else if (input.mode === 'fill') {
    if (!cancelled && Math.hypot(e.clientX - input.start.x, e.clientY - input.start.y) < 12) {
      const [x, y] = toDoc(e.clientX, e.clientY);
      if (x >= 0 && y >= 0 && x < doc.width && y < doc.height) {
        call('fill', { x: Math.floor(x), y: Math.floor(y), color: settings.color, tolerance: settings.fillTolerance, allLayers: settings.fillAll });
      }
    }
    input.mode = null;
    input.pointerId = null;
  }
  if (e.pointerType === 'touch') input.hover = null;
  drawOverlay();
}
stage.addEventListener('pointerup', (e) => pointerEnd(e, false));
stage.addEventListener('pointercancel', (e) => pointerEnd(e, true));
stage.addEventListener('pointerleave', (e) => { if (e.pointerType !== 'touch' && !input.mode) { input.hover = null; drawOverlay(); } });
stage.addEventListener('contextmenu', (e) => e.preventDefault());
stage.addEventListener('wheel', (e) => {
  e.preventDefault();
  if (!current) return;
  if (e.ctrlKey || Math.abs(e.deltaY) >= 40 || e.deltaMode !== 0) {
    zoomAt(e.clientX, e.clientY, Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.0018)));
  } else {
    view.tx -= e.deltaX;
    view.ty -= e.deltaY;
    applyView();
  }
}, { passive: false });

// Brush outline and symmetry guides.
function drawOverlay() {
  octx.clearRect(0, 0, innerWidth, innerHeight);
  if (!current) return;
  if (settings.symmetry) {
    octx.save();
    octx.strokeStyle = 'rgba(111,168,255,.75)';
    octx.lineWidth = 1.5;
    octx.setLineDash([8, 6]);
    const line = (x0, y0, x1, y1) => {
      const a = toScreen(x0, y0), b = toScreen(x1, y1);
      octx.beginPath(); octx.moveTo(a[0], a[1]); octx.lineTo(b[0], b[1]); octx.stroke();
    };
    if (settings.symmetry === 1 || settings.symmetry === 3) line(doc.width / 2, 0, doc.width / 2, doc.height);
    if (settings.symmetry === 2 || settings.symmetry === 3) line(0, doc.height / 2, doc.width, doc.height / 2);
    octx.restore();
  }
  drawAnts();
  drawSelDraft();
  drawTransform();
  const showSize = sliderPreview;
  const at = showSize ? [innerWidth / 2, innerHeight / 2] : input.hover;
  if (!at || !brushes.length) return;
  if (!showSize && !(paintTools.includes(tool) || tool === 'liquify')) return;
  const r = Math.max(1.5, (tool === 'liquify' ? settings.liquify.size : sizeFor(tool)) * view.s / 2);
  octx.beginPath();
  octx.arc(at[0], at[1], r, 0, Math.PI * 2);
  octx.lineWidth = 2.5; octx.strokeStyle = 'rgba(0,0,0,.35)'; octx.stroke();
  octx.lineWidth = 1; octx.strokeStyle = 'rgba(255,255,255,.9)'; octx.stroke();
}

// ---- Keyboard --------------------------------------------------------------------------------

document.addEventListener('keydown', (e) => {
  if (e.target.matches('input, select, textarea')) return;
  if (!current || $('#canvas-screen').hidden) return;
  const k = e.key.toLowerCase();
  if ((e.ctrlKey || e.metaKey) && k === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo(); return; }
  if ((e.ctrlKey || e.metaKey) && k === 'y') { e.preventDefault(); redo(); return; }
  if ((e.ctrlKey || e.metaKey) && k === 'a') { e.preventDefault(); call('select', { op: 'all' }); return; }
  if ((e.ctrlKey || e.metaKey) && k === 'd') { e.preventDefault(); call('select', { op: 'none' }); return; }
  if ((e.ctrlKey || e.metaKey) && e.shiftKey && k === 'i') { e.preventDefault(); call('select', { op: 'invert' }); return; }
  if ((e.ctrlKey || e.metaKey) && k === 't') { e.preventDefault(); startMoveTool(); return; }
  if ((e.ctrlKey || e.metaKey) && k === 'j') { e.preventDefault(); call('select', { op: 'copy' }); return; }
  if ((e.ctrlKey || e.metaKey) && k === 's') { e.preventDefault(); call('saveNow'); toast('Saved', 900); return; }
  if (e.ctrlKey || e.metaKey) return;
  if (k === ' ') { input.space = true; stage.classList.add('space'); e.preventDefault(); return; }
  if (k === 'alt') { input.alt = true; e.preventDefault(); return; }
  if (xf && (k.startsWith('arrow') || k === 'enter' || k === 'escape')) {
    e.preventDefault();
    if (k === 'enter') finishTransform(true);
    else if (k === 'escape') finishTransform(false);
    else {
      const n = e.shiftKey ? 10 : 1;
      if (k === 'arrowleft') xf.ox -= n; if (k === 'arrowright') xf.ox += n;
      if (k === 'arrowup') xf.oy -= n; if (k === 'arrowdown') xf.oy += n;
      drawOverlay(); xfSend();
    }
    return;
  }
  if (adjusting && (k === 'enter' || k === 'escape')) { endAdjust(k === 'enter'); return; }
  if ((k === 'delete' || k === 'backspace') && doc.hasSelection) { call('select', { op: 'clear' }); return; }
  if (k === 'm') { finishTransform(true); tool = 'select'; updateToolUi(); return; }
  if (k === 'v') { startMoveTool(); return; }
  if (k === 'b') selectTool('brush');
  else if (k === 's') selectTool('smudge');
  else if (k === 'e') selectTool('eraser');
  else if (k === 'i') { tool = 'dropper'; updateToolUi(); }
  else if (k === 'g') { tool = 'fill'; updateToolUi(); }
  else if (k === 'l') togglePanel('p-layers');
  else if (k === 'c') togglePanel('p-color');
  else if (k === '0') fitView();
  else if (k === 'h') flipView();
  else if (k === '[' || k === ']') {
    const v = clamp(sizeSlider.get() + (k === ']' ? 0.04 : -0.04), 0, 1);
    sizeSlider.set(v);
    const slot = slotFor(tool), b = brushFor(tool);
    settings.sizes[slot + ':' + b.id] = valueToSize(v);
    saveSettings();
    drawOverlay();
  } else if (k === 'escape') closePanels();
});
document.addEventListener('keyup', (e) => {
  if (e.key === ' ') { input.space = false; stage.classList.remove('space'); }
  if (e.key === 'Alt') input.alt = false;
});
addEventListener('blur', () => { input.space = false; input.alt = false; stage.classList.remove('space'); });

// Save straight away when the app is hidden or closed.
document.addEventListener('visibilitychange', () => { if (document.hidden && current) call('saveNow'); });
addEventListener('pagehide', () => { if (current) call('saveNow'); });

// ---- Install and offline ---------------------------------------------------------------------

let installEvent = null;
addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  installEvent = e;
  $('#install-btn').hidden = false;
});
$('#install-btn').addEventListener('click', async () => {
  if (!installEvent) return;
  installEvent.prompt();
  await installEvent.userChoice;
  installEvent = null;
  $('#install-btn').hidden = true;
});
addEventListener('appinstalled', () => { $('#install-btn').hidden = true; });
if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

// ---- Selections ------------------------------------------------------------------------------
// The worker sends a coarse coverage grid; marching squares turn it into outline segments in
// canvas pixels, drawn as animated "marching ants" at any zoom.

let selAnts = null;        // Float32Array of x0, y0, x1, y1 segments in canvas pixels
let selDraft = null;       // { shape, mode, points: [x, y, ...] } while dragging out a selection
let antsPhase = 0;
let antsTimer = 0;

function onSelection(m) {
  selAnts = m.grid ? marchingSquares(m.grid, m.w, m.h, m.step) : null;
  doc.hasSelection = !!m.grid;
  updateSelectionUi();
  animateAnts();
  drawOverlay();
}

function marchingSquares(grid, w, h, step) {
  const at = (x, y) => (x < 0 || y < 0 || x >= w || y >= h) ? 0 : grid[y * w + x];
  const out = [];
  const T = 128;
  const half = step / 2;
  // Interpolated crossing between two grid points along an edge.
  const lerp = (a, b) => (T - a) / ((b - a) || 1);
  for (let y = -1; y < h; y++) {
    for (let x = -1; x < w; x++) {
      const a = at(x, y), b = at(x + 1, y), c = at(x + 1, y + 1), d = at(x, y + 1);
      const k = (a >= T ? 8 : 0) | (b >= T ? 4 : 0) | (c >= T ? 2 : 0) | (d >= T ? 1 : 0);
      if (k === 0 || k === 15) continue;
      const top = [x + lerp(a, b), y], right = [x + 1, y + lerp(b, c)];
      const bottom = [x + lerp(d, c), y + 1], left = [x, y + lerp(a, d)];
      const seg = (p, q) => out.push(p[0] * step + half, p[1] * step + half, q[0] * step + half, q[1] * step + half);
      switch (k) {
        case 1: case 14: seg(left, bottom); break;
        case 2: case 13: seg(bottom, right); break;
        case 3: case 12: seg(left, right); break;
        case 4: case 11: seg(top, right); break;
        case 5: seg(left, top); seg(bottom, right); break;
        case 6: case 9: seg(top, bottom); break;
        case 7: case 8: seg(left, top); break;
        case 10: seg(top, right); seg(left, bottom); break;
      }
    }
  }
  return new Float32Array(out);
}

function animateAnts() {
  clearTimeout(antsTimer);
  if (!selAnts || !selAnts.length || document.hidden) return;
  antsTimer = setTimeout(() => {
    antsPhase = (antsPhase + 1) % 16;
    if (!input.mode || input.mode === 'select') drawOverlay();
    animateAnts();
  }, 90);
}

function drawAnts() {
  if (!selAnts || !selAnts.length || xf) return;
  octx.save();
  octx.beginPath();
  for (let i = 0; i < selAnts.length; i += 4) {
    const p = toScreen(selAnts[i], selAnts[i + 1]), q = toScreen(selAnts[i + 2], selAnts[i + 3]);
    octx.moveTo(p[0], p[1]);
    octx.lineTo(q[0], q[1]);
  }
  octx.lineWidth = 1.5;
  octx.strokeStyle = 'rgba(0,0,0,.85)';
  octx.setLineDash([]);
  octx.stroke();
  octx.strokeStyle = '#fff';
  octx.setLineDash([5, 5]);
  octx.lineDashOffset = -antsPhase;
  octx.stroke();
  octx.restore();
}

function drawSelDraft() {
  if (!selDraft) return;
  const pts = draftPolygon();
  if (pts.length < 4) return;
  octx.save();
  octx.beginPath();
  for (let i = 0; i < pts.length; i += 2) {
    const p = toScreen(pts[i], pts[i + 1]);
    if (i === 0) octx.moveTo(p[0], p[1]); else octx.lineTo(p[0], p[1]);
  }
  octx.closePath();
  octx.fillStyle = selDraft.mode === 2 ? 'rgba(255,107,107,.12)' : 'rgba(111,168,255,.14)';
  octx.fill();
  octx.lineWidth = 1.5;
  octx.strokeStyle = selDraft.mode === 2 ? '#ff8b8b' : '#6fa8ff';
  octx.setLineDash([6, 4]);
  octx.stroke();
  octx.restore();
}

function draftPolygon(d = selDraft) {
  if (d.shape === 'lasso') return d.points;
  const [x0, y0] = d.points, [x1, y1] = d.points.slice(-2);
  if (d.shape === 'rect') {
    // Whole pixels, so moving a boxed area leaves no half-lifted edge behind.
    const l = Math.round(Math.min(x0, x1)), r = Math.round(Math.max(x0, x1));
    const t = Math.round(Math.min(y0, y1)), b = Math.round(Math.max(y0, y1));
    return [l, t, r, t, r, b, l, b];
  }
  const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rx = Math.abs(x1 - x0) / 2, ry = Math.abs(y1 - y0) / 2;
  const out = [];
  const n = 96;
  for (let i = 0; i < n; i++) { const a = i / n * Math.PI * 2; out.push(cx + Math.cos(a) * rx, cy + Math.sin(a) * ry); }
  return out;
}

function selModeFor(e) {
  if (e.shiftKey) return 1;
  if (e.altKey || input.alt) return 2;
  return settings.selMode | 0;
}

function beginSelect(e) {
  input.mode = 'select';
  input.pointerId = e.pointerId;
  const [x, y] = toDoc(e.clientX, e.clientY);
  input.start = { x: e.clientX, y: e.clientY, far: 0 };
  selDraft = { shape: settings.selShape, mode: selModeFor(e), points: [x, y] };
}

function moveSelect(e) {
  const events = e.getCoalescedEvents ? e.getCoalescedEvents() : [];
  const list = events.length ? events : [e];
  for (const ev of list) {
    const [x, y] = toDoc(ev.clientX, ev.clientY);
    input.start.far = Math.max(input.start.far, Math.hypot(ev.clientX - input.start.x, ev.clientY - input.start.y));
    if (selDraft.shape === 'lasso') {
      const n = selDraft.points.length;
      if (Math.hypot(x - selDraft.points[n - 2], y - selDraft.points[n - 1]) * view.s < 2) continue;
      selDraft.points.push(x, y);
    } else {
      selDraft.points = [selDraft.points[0], selDraft.points[1], x, y];
    }
  }
  drawOverlay();
}

function endSelect(e, cancelled) {
  const d = selDraft;
  selDraft = null;
  input.mode = null;
  input.pointerId = null;
  if (cancelled || !d) { drawOverlay(); return; }
  const moved = Math.max(input.start.far, Math.hypot(e.clientX - input.start.x, e.clientY - input.start.y));
  if (d.shape === 'auto' || moved < 6) {
    const [x, y] = toDoc(e.clientX, e.clientY);
    if (d.shape === 'auto' && x >= 0 && y >= 0 && x < doc.width && y < doc.height) {
      call('select', { op: 'similar', x: Math.floor(x), y: Math.floor(y), tolerance: settings.fillTolerance, allLayers: settings.fillAll, mode: d.mode });
    } else if (moved < 6 && doc.hasSelection && d.mode === 0) {
      // A tap with a drawing shape clears the selection, like Procreate.
      call('select', { op: 'none' });
    }
    drawOverlay();
    return;
  }
  const pts = draftPolygon(d);
  if (pts.length >= 6) call('select', { op: 'polygon', points: new Float32Array(pts), mode: d.mode });
  drawOverlay();
}

function updateSelectionUi() {
  for (const b of $$('#sel-shapes button')) b.classList.toggle('active', b.dataset.shape === settings.selShape);
  for (const b of $$('#sel-modes button')) b.classList.toggle('active', +b.dataset.mode === (settings.selMode | 0));
  for (const b of $$('#sel-bar [data-sel]')) b.disabled = b.dataset.sel !== 'all' && !doc.hasSelection;
}
$('#sel-shapes').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  settings.selShape = b.dataset.shape; saveSettings(); updateSelectionUi();
});
$('#sel-modes').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  settings.selMode = +b.dataset.mode; saveSettings(); updateSelectionUi();
});
$('#sel-bar').addEventListener('click', (e) => {
  const op = e.target.closest('[data-sel]')?.dataset.sel;
  if (!op) return;
  selectionAction(op);
});
function selectionAction(op) {
  if (op === 'fill') call('select', { op: 'fill', color: settings.color });
  else call('select', { op });
  if (op === 'copy' || op === 'cut') toast(op === 'copy' ? 'Copied onto a new layer' : 'Cut onto a new layer', 1400);
}

// ---- Move and transform ----------------------------------------------------------------------

let xf = null;          // { rect, cx, cy, w, h, ox, oy, sx, sy, rot, drag }
let xfSerial = 0, xfBusy = false, xfPending = false;

function startMoveTool() {
  if (xf) return;
  if (adjusting) return;
  call('transformBegin');
}

function onTransformStarted(m) {
  if (!m.rect) {
    toast('There is nothing to move on this layer.');
    if (tool === 'move') { tool = lastPaintTool; updateToolUi(); }
    return;
  }
  const [l, t, r, b] = m.rect;
  xf = { rect: m.rect, cx: (l + r) / 2, cy: (t + b) / 2, w: Math.max(1, r - l), h: Math.max(1, b - t), ox: 0, oy: 0, sx: 1, sy: 1, rot: 0, drag: null };
  tool = 'move';
  updateToolUi();
  drawOverlay();
}

function xfMatrix(x = xf) {
  const cs = Math.cos(x.rot), sn = Math.sin(x.rot);
  const a = cs * x.sx, b = sn * x.sx, c = -sn * x.sy, d = cs * x.sy;
  const tx = x.cx + x.ox - (a * x.cx + c * x.cy), ty = x.cy + x.oy - (b * x.cx + d * x.cy);
  return [a, b, c, d, tx, ty];
}
const xfMap = (m, x, y) => [m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]];
const rotate = (x, y, r) => [x * Math.cos(r) - y * Math.sin(r), x * Math.sin(r) + y * Math.cos(r)];

function xfSend() {
  if (!xf) return;
  if (xfBusy) { xfPending = true; return; }
  xfBusy = true;
  xfPending = false;
  call('transformPreview', { m: xfMatrix(), quality: +$('#xf-quality').value, serial: ++xfSerial });
}
function onTransformed() {
  xfBusy = false;
  if (xfPending) xfSend();
}

/** Handles in canvas pixels: 4 corners, 4 edges, and the rotation knob. */
function xfHandles() {
  const m = xfMatrix();
  const { cx, cy, w, h } = xf;
  const hs = [];
  for (const [ux, uy] of [[-1, -1], [0, -1], [1, -1], [1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0]]) {
    hs.push({ ux, uy, p: xfMap(m, cx + ux * w / 2, cy + uy * h / 2) });
  }
  return hs;
}

function xfRotateKnob() {
  // 36 screen pixels outside the top edge, whichever way the box faces.
  const m = xfMatrix();
  const top = toScreen(...xfMap(m, xf.cx, xf.cy - xf.h / 2));
  const mid = toScreen(...xfMap(m, xf.cx, xf.cy));
  let dx = top[0] - mid[0], dy = top[1] - mid[1];
  const len = Math.hypot(dx, dy) || 1;
  dx /= len; dy /= len;
  return [top[0] + dx * 36, top[1] + dy * 36];
}

function drawTransform() {
  if (!xf) return;
  const hs = xfHandles();
  const corners = [0, 2, 4, 6].map(i => toScreen(...hs[i].p));
  octx.save();
  octx.beginPath();
  corners.forEach((p, i) => (i ? octx.lineTo(p[0], p[1]) : octx.moveTo(p[0], p[1])));
  octx.closePath();
  octx.lineWidth = 2.5; octx.strokeStyle = 'rgba(0,0,0,.4)'; octx.stroke();
  octx.lineWidth = 1.2; octx.strokeStyle = '#6fa8ff'; octx.stroke();
  const top = toScreen(...hs[1].p), knob = xfRotateKnob();
  octx.beginPath(); octx.moveTo(top[0], top[1]); octx.lineTo(knob[0], knob[1]); octx.stroke();
  const dot = (p, r, fill) => {
    octx.beginPath(); octx.arc(p[0], p[1], r, 0, Math.PI * 2);
    octx.fillStyle = fill; octx.fill();
    octx.lineWidth = 1.5; octx.strokeStyle = '#1b2a44'; octx.stroke();
  };
  for (const h of hs) dot(toScreen(...h.p), h.ux && h.uy ? 8 : 6, '#fff');
  dot(knob, 8, '#6fa8ff');
  octx.restore();
}

function xfHit(e) {
  const pt = [e.clientX, e.clientY];
  const near = (p, r) => Math.hypot(p[0] - pt[0], p[1] - pt[1]) <= r;
  const slop = e.pointerType === 'touch' ? 26 : 16;
  if (near(xfRotateKnob(), slop)) return { kind: 'rotate' };
  const hs = xfHandles();
  // Corners first, so they win where they overlap the edges on small boxes.
  for (const h of [...hs].sort((a, b) => (b.ux && b.uy) - (a.ux && a.uy))) {
    if (near(toScreen(...h.p), slop)) return { kind: 'scale', ux: h.ux, uy: h.uy };
  }
  return { kind: 'move' };
}

function beginXfDrag(e) {
  input.mode = 'xf';
  input.pointerId = e.pointerId;
  const hit = xfHit(e);
  const p = toDoc(e.clientX, e.clientY);
  xf.drag = { ...hit, start: p, from: { ...xf, drag: null } };
}

function moveXfDrag(e) {
  const d = xf.drag;
  if (!d) return;
  const p = toDoc(e.clientX, e.clientY);
  const f = d.from;
  if (d.kind === 'move') {
    xf.ox = f.ox + p[0] - d.start[0];
    xf.oy = f.oy + p[1] - d.start[1];
  } else if (d.kind === 'rotate') {
    const c = [f.cx + f.ox, f.cy + f.oy];
    let r = f.rot + Math.atan2(p[1] - c[1], p[0] - c[0]) - Math.atan2(d.start[1] - c[1], d.start[0] - c[0]);
    const step = Math.PI / 12;
    const snap = Math.round(r / step) * step;
    if (Math.abs(r - snap) < 0.05 && !e.shiftKey) r = snap;
    xf.rot = r;
  } else {
    const { ux, uy } = d;
    const fm = xfMatrix(f);
    // The opposite corner or edge stays put.
    const anchor = xfMap(fm, f.cx - ux * f.w / 2, f.cy - uy * f.h / 2);
    const local = rotate(p[0] - anchor[0], p[1] - anchor[1], -f.rot);
    let sx = f.sx, sy = f.sy;
    if (ux) sx = local[0] / (ux * f.w);
    if (uy) sy = local[1] / (uy * f.h);
    const uniform = ux && uy && (settings.xfUniform !== false) !== e.shiftKey;
    if (uniform) {
      // Keep the proportions: scale by how far along the box's diagonal the pen went.
      const q = [ux * f.sx * f.w, uy * f.sy * f.h];
      const k = (local[0] * q[0] + local[1] * q[1]) / (q[0] * q[0] + q[1] * q[1]);
      sx = f.sx * k; sy = f.sy * k;
    }
    const tiny = 0.01;
    if (Math.abs(sx) < tiny) sx = sx < 0 ? -tiny : tiny;
    if (Math.abs(sy) < tiny) sy = sy < 0 ? -tiny : tiny;
    xf.sx = sx; xf.sy = sy;
    const off = rotate(ux * sx * f.w / 2, uy * sy * f.h / 2, f.rot);
    xf.ox = anchor[0] + off[0] - f.cx;
    xf.oy = anchor[1] + off[1] - f.cy;
  }
  drawOverlay();
  xfSend();
}

function endXfDrag() {
  if (xf) xf.drag = null;
  input.mode = null;
  input.pointerId = null;
}

function xfAction(op) {
  if (!xf) return;
  if (op === 'flipH') xf.sx = -xf.sx;
  if (op === 'flipV') xf.sy = -xf.sy;
  if (op === 'rot') xf.rot += Math.PI / 2;
  if (op === 'reset') Object.assign(xf, { ox: 0, oy: 0, sx: 1, sy: 1, rot: 0 });
  if (op === 'fit') {
    const k = Math.min(doc.width / xf.w, doc.height / xf.h);
    Object.assign(xf, { sx: Math.sign(xf.sx) * k, sy: Math.sign(xf.sy) * k, rot: 0, ox: doc.width / 2 - xf.cx, oy: doc.height / 2 - xf.cy });
  }
  if (op === 'done') return finishTransform(true);
  if (op === 'cancel') return finishTransform(false);
  drawOverlay();
  xfSend();
}

function finishTransform(commit) {
  if (!xf) return;
  if (commit) call('transformCommit', { m: xfMatrix(), quality: +$('#xf-quality').value });
  else call('transformCancel');
  xf = null;
  xfBusy = false;
  xfPending = false;
  if (tool === 'move') tool = lastPaintTool;
  updateToolUi();
  drawOverlay();
}

$('#xf-bar').addEventListener('click', (e) => {
  const op = e.target.closest('[data-xf]')?.dataset.xf;
  if (op) xfAction(op);
});
$('#xf-uniform').addEventListener('click', () => {
  settings.xfUniform = settings.xfUniform === false;
  saveSettings();
  updateToolUi();
});
$('#xf-quality').addEventListener('change', () => xfSend());

// ---- Adjustments -----------------------------------------------------------------------------

const ADJUSTMENTS = [
  { id: 'HUE_SATURATION', name: 'Hue, saturation, lightness', fields: [
    { key: 'a', label: 'Hue', min: -1, max: 1, def: 0, fmt: v => Math.round(v * 180) + '°' },
    { key: 'b', label: 'Saturation', min: -1, max: 1, def: 0, fmt: pct },
    { key: 'c', label: 'Lightness', min: -1, max: 1, def: 0, fmt: pct }] },
  { id: 'BRIGHTNESS_CONTRAST', name: 'Brightness and contrast', fields: [
    { key: 'a', label: 'Brightness', min: -1, max: 1, def: 0, fmt: pct },
    { key: 'b', label: 'Contrast', min: -1, max: 1, def: 0, fmt: pct }] },
  { id: 'LEVELS', name: 'Levels', fields: [
    { key: 'a', label: 'Shadows', min: 0, max: 0.9, def: 0, fmt: v => Math.round(v * 255) },
    { key: 'b', label: 'Highlights', min: 0.1, max: 1, def: 1, fmt: v => Math.round(v * 255) },
    { key: 'c', label: 'Midtones', min: -1, max: 1, def: 0, fmt: pct }] },
  { id: 'COLOR_BALANCE', name: 'Colour balance', fields: [
    { key: 'a', label: 'Cool ↔ warm', min: -1, max: 1, def: 0, fmt: pct },
    { key: 'b', label: 'Green ↔ magenta', min: -1, max: 1, def: 0, fmt: pct },
    { key: 'c', label: 'Blue ↔ yellow', min: -1, max: 1, def: 0, fmt: pct }] },
  { id: 'BLUR', name: 'Gaussian blur', fields: [
    { key: 'a', label: 'Radius', min: 0, max: 1, def: 0.2, fmt: v => (Math.pow(v, 1.6) * 120).toFixed(1) + ' px' }] },
  { id: 'SHARPEN', name: 'Sharpen', fields: [
    { key: 'a', label: 'Amount', min: 0, max: 1, def: 0.4, fmt: pct },
    { key: 'b', label: 'Radius', min: 0, max: 1, def: 0.15, fmt: v => (0.5 + v * 9.5).toFixed(1) + ' px' }] },
  { id: 'NOISE', name: 'Noise', fields: [
    { key: 'a', label: 'Amount', min: 0, max: 1, def: 0.25, fmt: pct }] },
  { id: 'INVERT', name: 'Invert colours', fields: [] },
];
function pct(v) { return Math.round(v * 100) + '%'; }

let adjusting = null;   // { spec, values }
let adjSerial = 0, adjBusy = false, adjPending = false;

function openAdjustMenu() {
  const menu = $('#adjust-menu');
  menu.innerHTML = ADJUSTMENTS.map(a => `<button data-adj="${a.id}">${a.name}</button>`).join('') +
    '<hr><button data-adj="LIQUIFY">Liquify</button>';
  menu.onclick = (e) => {
    const id = e.target.closest('button')?.dataset.adj;
    if (!id) return;
    closeMenu();
    if (id === 'LIQUIFY') { finishTransform(true); selectTool('liquify'); return; }
    startAdjust(id);
  };
  const r = $('#b-adjust').getBoundingClientRect();
  openMenu(menu, r.left, r.bottom + 6);
}
$('#b-adjust').addEventListener('click', () => { closePanels(); openAdjustMenu(); });

async function startAdjust(id) {
  if (adjusting) return;
  finishTransform(true);
  const spec = ADJUSTMENTS.find(a => a.id === id);
  const started = waitFor('adjustStarted');
  call('adjustBegin', { kind: id });
  const r = await started;
  if (!r.ok) { toast('There is nothing on this layer to adjust.'); return; }
  adjusting = { spec, values: Object.fromEntries(spec.fields.map(f => [f.key, f.def])) };
  $('#adjust-title').textContent = spec.name;
  $('#adjust-hint').textContent = doc.editingMask ? 'on the mask' : (doc.hasSelection ? 'on the selection' : 'on ' + (doc.layers.find(l => l.active)?.name || 'this layer'));
  renderAdjust();
  $('#p-adjust').hidden = false;
  adjSend();
}

function renderAdjust() {
  const body = $('#adjust-body');
  body.textContent = '';
  if (!adjusting.spec.fields.length) { body.innerHTML = '<p class="hint">Apply to keep it, or cancel.</p>'; return; }
  for (const f of adjusting.spec.fields) {
    const row = document.createElement('label');
    row.className = 'studio-field';
    row.innerHTML = `<span>${f.label}</span><input type="range" min="${f.min}" max="${f.max}" step="0.005"><output></output>`;
    const inp = row.querySelector('input'), out = row.querySelector('output');
    inp.value = adjusting.values[f.key];
    out.textContent = f.fmt(+inp.value);
    inp.addEventListener('input', () => { adjusting.values[f.key] = +inp.value; out.textContent = f.fmt(+inp.value); adjSend(); });
    inp.addEventListener('dblclick', () => { inp.value = f.def; inp.dispatchEvent(new Event('input')); });
    body.append(row);
  }
}

function adjSend() {
  if (!adjusting) return;
  if (adjBusy) { adjPending = true; return; }
  adjBusy = true;
  adjPending = false;
  const v = adjusting.values;
  call('adjustPreview', { a: v.a ?? 0, b: v.b ?? 0, c: v.c ?? 0, serial: ++adjSerial });
}
function onAdjusted() { adjBusy = false; if (adjPending) adjSend(); }

function endAdjust(apply) {
  if (!adjusting) return;
  if (apply) {
    // Make sure the last slider position is what gets applied.
    const v = adjusting.values;
    call('adjustPreview', { a: v.a ?? 0, b: v.b ?? 0, c: v.c ?? 0, serial: ++adjSerial });
    call('adjustCommit');
  } else {
    call('adjustCancel');
  }
  adjusting = null;
  adjBusy = false;
  adjPending = false;
  $('#p-adjust').hidden = true;
}
$('#adjust-apply').addEventListener('click', () => endAdjust(true));
$('#adjust-cancel').addEventListener('click', () => endAdjust(false));
$('#adjust-reset').addEventListener('click', () => {
  if (!adjusting) return;
  for (const f of adjusting.spec.fields) adjusting.values[f.key] = f.def;
  renderAdjust();
  adjSend();
});

// ---- Liquify ---------------------------------------------------------------------------------

const LIQUIFY_MODES = [['PUSH', 'Push'], ['TWIRL_RIGHT', 'Twirl ↻'], ['TWIRL_LEFT', 'Twirl ↺'], ['PINCH', 'Pinch'], ['EXPAND', 'Expand'], ['RESTORE', 'Reconstruct']];
settings.liquify = Object.assign({ mode: 'PUSH', size: 160, strength: 0.5 }, settings.liquify || {});
$('#lq-modes').innerHTML = LIQUIFY_MODES.map(([id, name]) => `<button data-lq-mode="${id}">${name}</button>`).join('');
$('#lq-modes').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  settings.liquify.mode = b.dataset.lqMode; saveSettings(); syncLiquifyBar();
});
for (const key of ['size', 'strength']) {
  $('#lq-' + key).addEventListener('input', (e) => { settings.liquify[key] = +e.target.value; saveSettings(); syncLiquifyBar(); sliderPreview = key === 'size'; drawOverlay(); });
  $('#lq-' + key).addEventListener('change', () => { sliderPreview = false; drawOverlay(); });
}
$('#lq-bar [data-lq=done]').addEventListener('click', () => { tool = lastPaintTool; updateToolUi(); });
function syncLiquifyBar() {
  for (const b of $$('#lq-modes button')) b.classList.toggle('active', b.dataset.lqMode === settings.liquify.mode);
  $('#lq-size').value = settings.liquify.size;
  $('#lq-strength').value = settings.liquify.strength;
  $('#lq-size').nextElementSibling.textContent = Math.round(settings.liquify.size);
  $('#lq-strength').nextElementSibling.textContent = pct(settings.liquify.strength);
}

function beginLiquify(e) {
  input.mode = 'liquify';
  input.pointerId = e.pointerId;
  const q = pointFrom(e);
  call('liquifyBegin', { mode: settings.liquify.mode, size: settings.liquify.size, strength: settings.liquify.strength, point: q });
}
function moveLiquify(e) {
  const events = e.getCoalescedEvents ? e.getCoalescedEvents() : [];
  const pts = [];
  for (const ev of (events.length ? events : [e])) pts.push(...pointFrom(ev));
  call('liquifyMove', { points: pts });
}
function endLiquify() {
  call('liquifyEnd');
  input.mode = null;
  input.pointerId = null;
}

// ---- Tool bars -------------------------------------------------------------------------------

function syncToolBars() {
  if (xf && tool !== 'move') { const t = tool; finishTransform(true); tool = t; }
  $('#sel-bar').hidden = tool !== 'select';
  $('#xf-bar').hidden = tool !== 'move' || !xf;
  $('#lq-bar').hidden = tool !== 'liquify';
  $('#b-select').classList.toggle('active', tool === 'select');
  $('#b-move').classList.toggle('active', tool === 'move');
  $('#xf-uniform').classList.toggle('on', settings.xfUniform !== false);
  if (tool === 'select') updateSelectionUi();
  if (tool === 'liquify') syncLiquifyBar();
}
$('#b-select').addEventListener('click', () => {
  finishTransform(true);
  tool = tool === 'select' ? lastPaintTool : 'select';
  updateToolUi();
});
$('#b-move').addEventListener('click', () => {
  if (xf) { finishTransform(true); return; }
  startMoveTool();
});

// ---- Masks -----------------------------------------------------------------------------------

function syncMaskBanner() {
  $('#mask-banner').hidden = !doc.editingMask;
}
$('#mask-done').addEventListener('click', () => call('layer', { op: 'editMask', args: [false] }));

// ---- Watercolour Dry button ------------------------------------------------------------------

$('#dry-btn').addEventListener('click', () => { call('dryWater'); $('#dry-btn').hidden = true; });
function onWater(m) { $('#dry-btn').hidden = !m.active || !current; }

// ---- Pen pressure curve ----------------------------------------------------------------------

settings.pressure = Object.assign({ x: 0.5, y: 0.5 }, settings.pressure || {});
const PRESSURE_PRESETS = { soft: { x: 0.28, y: 0.72 }, normal: { x: 0.5, y: 0.5 }, firm: { x: 0.72, y: 0.28 } };

/** Maps raw pen pressure through the curve: a quadratic Bezier from (0,0) to (1,1). */
function pressureCurve(p) {
  const px = settings.pressure.x, py = settings.pressure.y;
  if (px === 0.5 && py === 0.5) return p;
  const A = 1 - 2 * px;
  let t;
  if (Math.abs(A) < 1e-5) t = p / (2 * px);
  else t = (-2 * px + Math.sqrt(4 * px * px + 4 * A * p)) / (2 * A);
  t = clamp(t, 0, 1);
  return clamp(2 * (1 - t) * t * py + t * t, 0, 1);
}

let padLast = null;
function openPressure() {
  showDialog('#d-pressure');
  drawPressureCurve();
  const pad = $('#pressure-pad');
  pad.getContext('2d').clearRect(0, 0, pad.width, pad.height);
}
function drawPressureCurve(live) {
  const c = $('#pressure-curve'), x = c.getContext('2d');
  const W = c.width, H = c.height, pad = 30;
  const X = v => pad + v * (W - pad * 2), Y = v => H - pad - v * (H - pad * 2);
  x.clearRect(0, 0, W, H);
  x.strokeStyle = 'rgba(255,255,255,.08)'; x.lineWidth = 2;
  for (let i = 0; i <= 4; i++) {
    x.beginPath(); x.moveTo(X(i / 4), Y(0)); x.lineTo(X(i / 4), Y(1)); x.stroke();
    x.beginPath(); x.moveTo(X(0), Y(i / 4)); x.lineTo(X(1), Y(i / 4)); x.stroke();
  }
  x.strokeStyle = 'rgba(255,255,255,.25)'; x.setLineDash([8, 8]);
  x.beginPath(); x.moveTo(X(0), Y(0)); x.lineTo(X(1), Y(1)); x.stroke();
  x.setLineDash([]);
  x.strokeStyle = '#6fa8ff'; x.lineWidth = 5;
  x.beginPath();
  for (let i = 0; i <= 64; i++) { const p = i / 64; const v = pressureCurve(p); i ? x.lineTo(X(p), Y(v)) : x.moveTo(X(p), Y(v)); }
  x.stroke();
  x.fillStyle = '#fff';
  x.beginPath(); x.arc(X(settings.pressure.x), Y(settings.pressure.y), 14, 0, Math.PI * 2); x.fill();
  if (live != null) {
    x.fillStyle = '#ffb04a';
    x.beginPath(); x.arc(X(live), Y(pressureCurve(live)), 10, 0, Math.PI * 2); x.fill();
  }
  x.fillStyle = '#9a9aa3'; x.font = '22px Segoe UI, sans-serif';
  x.fillText('light', X(0), H - 4); x.fillText('hard', X(1) - 44, H - 4);
  for (const b of $$('#pressure-presets button')) {
    const pr = PRESSURE_PRESETS[b.dataset.p];
    b.classList.toggle('active', Math.abs(pr.x - settings.pressure.x) < 0.01 && Math.abs(pr.y - settings.pressure.y) < 0.01);
  }
}
{
  const c = $('#pressure-curve');
  const pick = (e) => {
    const r = c.getBoundingClientRect();
    const pad = 30 * r.width / c.width;
    settings.pressure.x = clamp((e.clientX - r.left - pad) / (r.width - pad * 2), 0.04, 0.96);
    settings.pressure.y = clamp(1 - (e.clientY - r.top - pad) / (r.height - pad * 2), 0.04, 0.96);
    saveSettings();
    drawPressureCurve();
  };
  c.addEventListener('pointerdown', (e) => { c.setPointerCapture(e.pointerId); pick(e); });
  c.addEventListener('pointermove', (e) => { if (c.hasPointerCapture(e.pointerId)) pick(e); });
  $('#pressure-presets').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    Object.assign(settings.pressure, PRESSURE_PRESETS[b.dataset.p]);
    saveSettings();
    drawPressureCurve();
  });
  const pad = $('#pressure-pad'), px = pad.getContext('2d');
  pad.addEventListener('pointerdown', (e) => { pad.setPointerCapture(e.pointerId); padLast = null; padDraw(e); });
  pad.addEventListener('pointermove', (e) => { if (pad.hasPointerCapture(e.pointerId)) padDraw(e); else if (e.pointerType === 'pen') drawPressureCurve(e.pressure); });
  pad.addEventListener('pointerup', () => { padLast = null; });
  function padDraw(e) {
    const r = pad.getBoundingClientRect();
    const raw = e.pointerType === 'pen' ? e.pressure : 0.5;
    const p = pressureCurve(raw);
    const x = (e.clientX - r.left) * pad.width / r.width, y = (e.clientY - r.top) * pad.height / r.height;
    px.strokeStyle = '#1f2a44'; px.lineCap = 'round';
    px.lineWidth = 1 + p * 22;
    px.beginPath();
    if (padLast) px.moveTo(padLast[0], padLast[1]); else px.moveTo(x, y);
    px.lineTo(x, y); px.stroke();
    padLast = [x, y];
    drawPressureCurve(raw);
  }
}

// ---- Text ------------------------------------------------------------------------------------

function openTextDialog() {
  showDialog('#d-text');
  const t = settings.text || {};
  if (t.font) $('#text-font').value = t.font;
  if (t.size) $('#text-size').value = t.size;
  $('#text-bold').checked = !!t.bold;
  $('#text-italic').checked = !!t.italic;
  if (t.align) $('#text-align').value = t.align;
  setTimeout(() => $('#text-input').focus(), 30);
}
$('#text-ok').addEventListener('click', () => {
  const text = $('#text-input').value.replace(/\s+$/, '');
  if (!text) { closeDialogs(); return; }
  const opts = {
    font: $('#text-font').value, size: clamp(+$('#text-size').value || 120, 6, 1000),
    bold: $('#text-bold').checked, italic: $('#text-italic').checked, align: $('#text-align').value,
  };
  settings.text = opts;
  saveSettings();
  closeDialogs();
  $('#text-input').value = '';
  const fontCss = `${opts.italic ? 'italic ' : ''}${opts.bold ? '700 ' : '400 '}${opts.size}px ${opts.font}`;
  const lines = text.split('\n');
  const m = new OffscreenCanvas(8, 8).getContext('2d');
  m.font = fontCss;
  const widths = lines.map(l => m.measureText(l).width);
  const lineH = Math.ceil(opts.size * 1.25);
  const padding = Math.ceil(opts.size * 0.3);
  const w = Math.min(doc.width * 2, Math.ceil(Math.max(...widths, 1)) + padding * 2);
  const h = Math.min(doc.height * 2, lineH * lines.length + padding * 2);
  const c = new OffscreenCanvas(w, h);
  const x = c.getContext('2d');
  x.font = fontCss;
  x.fillStyle = hex(settings.color);
  x.textBaseline = 'middle';
  x.textAlign = opts.align;
  const ax = opts.align === 'left' ? padding : opts.align === 'right' ? w - padding : w / 2;
  lines.forEach((l, i) => x.fillText(l, ax, padding + lineH * (i + 0.5)));
  const data = x.getImageData(0, 0, w, h).data;
  const name = lines[0].slice(0, 40) || 'Text';
  call('importImage', { name, rgba: data.buffer, width: w, height: h, x: (doc.width - w) / 2, y: (doc.height - h) / 2, thenTransform: true }, [data.buffer]);
});

// ---- Reference picture -----------------------------------------------------------------------

let refBitmap = null;
async function showReference(blob) {
  try { refBitmap = blob ? await createImageBitmap(blob) : null; } catch { refBitmap = null; }
  $('#ref-win').hidden = !refBitmap;
  drawReference();
}
function drawReference() {
  if (!refBitmap) return;
  const c = $('#ref-canvas');
  const r = c.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  c.width = Math.max(1, Math.round(r.width * dpr));
  c.height = Math.max(1, Math.round(r.height * dpr));
  const x = c.getContext('2d');
  x.clearRect(0, 0, c.width, c.height);
  const k = Math.min(c.width / refBitmap.width, c.height / refBitmap.height);
  const w = refBitmap.width * k, h = refBitmap.height * k;
  x.imageSmoothingQuality = 'high';
  x.drawImage(refBitmap, (c.width - w) / 2, (c.height - h) / 2, w, h);
}
new ResizeObserver(() => drawReference()).observe($('#ref-win'));
$('#ref-file').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  e.target.value = '';
  if (!file || !current) return;
  await db.setReference(current.id, file).catch(() => {});
  showReference(file);
});
$('#ref-close').addEventListener('click', () => {
  if (current) db.setReference(current.id, null).catch(() => {});
  showReference(null);
});
{
  // Drag the window by its title, resize it from the corner.
  const win = $('#ref-win');
  let drag = null;
  const start = (mode) => (e) => {
    if (e.target.closest('button')) return;
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    const r = win.getBoundingClientRect();
    drag = { mode, x: e.clientX, y: e.clientY, r };
  };
  const move = (e) => {
    if (!drag) return;
    const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
    if (drag.mode === 'move') {
      win.style.left = clamp(drag.r.left + dx, 0, innerWidth - 60) + 'px';
      win.style.top = clamp(drag.r.top + dy, 0, innerHeight - 40) + 'px';
    } else {
      win.style.width = clamp(drag.r.width + dx, 140, innerWidth) + 'px';
      win.style.height = clamp(drag.r.height + dy, 120, innerHeight) + 'px';
    }
  };
  const end = () => { drag = null; };
  for (const [el, mode] of [[$('#ref-head'), 'move'], [$('#ref-grip'), 'size']]) {
    el.addEventListener('pointerdown', start(mode));
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerup', end);
    el.addEventListener('pointercancel', end);
  }
  // Tap the picture to pick a colour from it.
  const c = $('#ref-canvas');
  const pickRef = (e, final) => {
    const r = c.getBoundingClientRect();
    const x = Math.floor((e.clientX - r.left) * c.width / r.width), y = Math.floor((e.clientY - r.top) * c.height / r.height);
    const px = c.getContext('2d').getImageData(clamp(x, 0, c.width - 1), clamp(y, 0, c.height - 1), 1, 1).data;
    if (px[3] < 10) return;
    const col = (0xFF000000 | (px[0] << 16) | (px[1] << 8) | px[2]) | 0;
    const loupe = $('#loupe');
    loupe.style.left = e.clientX + 'px'; loupe.style.top = e.clientY + 'px';
    loupe.style.background = hex(col); loupe.style.borderColor = hex(col);
    loupe.hidden = !!final;
    setColor(col, !!final);
  };
  c.addEventListener('pointerdown', (e) => { c.setPointerCapture(e.pointerId); pickRef(e, false); });
  c.addEventListener('pointermove', (e) => { if (c.hasPointerCapture(e.pointerId)) pickRef(e, false); });
  c.addEventListener('pointerup', (e) => { pickRef(e, true); if (tool === 'dropper') { tool = lastPaintTool; updateToolUi(); } });
}

// ---- Time-lapse ------------------------------------------------------------------------------

let tl = null;  // { frames: ImageBitmap[], i, timer, playing }
async function openTimelapse() {
  const got = waitFor('frames');
  call('frames');
  const { frames } = await got;
  if (!frames.length) { toast('No time-lapse yet: it records from now on, a frame each time you pause.'); return; }
  const bitmaps = [];
  for (const b of frames) { try { bitmaps.push(await createImageBitmap(b)); } catch { /* skip */ } }
  if (!bitmaps.length) return;
  const c = $('#tl-canvas');
  c.width = bitmaps[bitmaps.length - 1].width;
  c.height = bitmaps[bitmaps.length - 1].height;
  tl = { frames: bitmaps, i: 0, timer: 0, playing: false };
  $('#tl-pos').max = bitmaps.length - 1;
  showDialog('#d-timelapse');
  tlShow(bitmaps.length - 1);
  tlPlay(true);
}
function tlFps() { return clamp(tl.frames.length / 15, 6, 30); }
function tlShow(i) {
  if (!tl) return;
  tl.i = i;
  const c = $('#tl-canvas'), x = c.getContext('2d');
  const f = tl.frames[i];
  x.fillStyle = '#fff';
  x.fillRect(0, 0, c.width, c.height);
  x.drawImage(f, 0, 0, c.width, c.height);
  $('#tl-pos').value = i;
  $('#tl-info').textContent = `${i + 1} / ${tl.frames.length}`;
}
function tlPlay(on) {
  if (!tl) return;
  clearTimeout(tl.timer);
  tl.playing = on;
  $('#tl-play').textContent = on ? 'Pause' : 'Play';
  if (!on) return;
  if (tl.i >= tl.frames.length - 1) tlShow(0);
  const tick = () => {
    if (!tl || !tl.playing) return;
    if (tl.i >= tl.frames.length - 1) { tlPlay(false); return; }
    tlShow(tl.i + 1);
    tl.timer = setTimeout(tick, 1000 / tlFps());
  };
  tl.timer = setTimeout(tick, 1000 / tlFps());
}
$('#tl-play').addEventListener('click', () => tlPlay(!tl?.playing));
$('#tl-pos').addEventListener('input', (e) => { tlPlay(false); tlShow(+e.target.value); });
$('#tl-export').addEventListener('click', async () => {
  if (!tl || !window.MediaRecorder) { toast("This browser can't make videos."); return; }
  tlPlay(false);
  const c = $('#tl-canvas');
  const stream = c.captureStream(30);
  const type = ['video/mp4;codecs=avc1', 'video/webm;codecs=vp9', 'video/webm'].find(t => MediaRecorder.isTypeSupported(t)) || 'video/webm';
  const rec = new MediaRecorder(stream, { mimeType: type, videoBitsPerSecond: 8e6 });
  const chunks = [];
  rec.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
  const stopped = new Promise(r => { rec.onstop = r; });
  toast('Making the video…', 60000);
  rec.start();
  const fps = tlFps();
  for (let i = 0; i < tl.frames.length; i++) {
    tlShow(i);
    await new Promise(r => setTimeout(r, 1000 / fps));
  }
  await new Promise(r => setTimeout(r, 1500));
  rec.stop();
  await stopped;
  const blob = new Blob(chunks, { type: type.split(';')[0] });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = (current?.name || 'Drawing') + ' time-lapse.' + (type.includes('mp4') ? 'mp4' : 'webm');
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 20000);
  toast('Saved ' + a.download + ' to Downloads');
});
document.addEventListener('click', (e) => {
  // Stop the replay when its dialog closes.
  if (tl && $('#d-timelapse').hidden) { tlPlay(false); tl = null; }
}, true);

// ---- Layer groups in the layer list ----------------------------------------------------------

const collapsedGroups = new Set();

function groupMenu(g, x, y) {
  const menu = $('#layer-menu');
  menu.innerHTML = `
    <button data-op="rename">Rename group</button>
    <label>Opacity <input type="range" min="0" max="1" step="0.01" value="${g.opacity}" data-op="opacity"><output>${Math.round(g.opacity * 100)}%</output></label>
    <hr>
    <button data-op="up">Move group up</button>
    <button data-op="down">Move group down</button>
    <button data-op="flatten">Flatten group into one layer</button>
    <button data-op="ungroup">Ungroup</button>
    <button data-op="delete" class="danger">Delete group and its layers</button>`;
  menu.onclick = async (e) => {
    const op = e.target.closest('button')?.dataset.op;
    if (!op) return;
    closeMenu();
    if (op === 'rename') { const name = await prompt('Rename group', g.name); if (name) call('layer', { op: 'groupProps', args: [g.id, name, g.visible, g.opacity] }); }
    if (op === 'up' || op === 'down') call('layer', { op: 'moveGroup', args: [g.id, op === 'up'] });
    if (op === 'flatten') call('layer', { op: 'flattenGroup', args: [g.id] });
    if (op === 'ungroup') call('layer', { op: 'ungroup', args: [g.id] });
    if (op === 'delete') {
      if (await confirmDelete('Delete group?', `"${g.name}" and every layer in it will be deleted. You can undo this.`)) call('layer', { op: 'deleteGroup', args: [g.id] });
    }
  };
  const range = menu.querySelector('[data-op=opacity]');
  range.addEventListener('input', () => {
    range.nextElementSibling.textContent = Math.round(range.value * 100) + '%';
    call('layer', { op: 'previewGroupOpacity', args: [g.id, +range.value] });
  });
  range.addEventListener('change', () => call('layer', { op: 'groupProps', args: [g.id, g.name, g.visible, +range.value] }));
  openMenu(menu, x - 220, y);
}

function groupRow(g, members) {
  const row = document.createElement('div');
  row.className = 'group-row' + (collapsedGroups.has(g.id) ? ' collapsed' : '') + (g.visible ? '' : ' hidden-layer');
  row.innerHTML = `<button class="icon-btn small chev" title="Show or hide the layers in it"><svg><use href="#i-chevron"/></svg></button>
    <svg><use href="#i-folder"/></svg><div class="gname"><div></div><small>${members} layer${members === 1 ? '' : 's'} · ${Math.round(g.opacity * 100)}%</small></div>
    <button class="icon-btn small vis" title="${g.visible ? 'Hide group' : 'Show group'}"><svg><use href="#i-${g.visible ? 'eye' : 'eye-off'}"/></svg></button>`;
  row.querySelector('.gname div').textContent = g.name;
  row.querySelector('.chev').addEventListener('click', (e) => {
    e.stopPropagation();
    if (collapsedGroups.has(g.id)) collapsedGroups.delete(g.id); else collapsedGroups.add(g.id);
    renderLayers();
  });
  row.querySelector('.vis').addEventListener('click', (e) => {
    e.stopPropagation();
    call('layer', { op: 'groupProps', args: [g.id, g.name, !g.visible, g.opacity] });
  });
  row.addEventListener('click', (e) => groupMenu(g, e.clientX, e.clientY));
  row.addEventListener('contextmenu', (e) => { e.preventDefault(); groupMenu(g, e.clientX, e.clientY); });
  return row;
}

// ---- Photoshop files -------------------------------------------------------------------------

async function openPsdFile(file) {
  requestPersistence();
  showCanvas();
  const id = newId();
  const bytes = await file.arrayBuffer();
  const opened = waitFor('opened');
  call('openPsd', { id, name: file.name.replace(/\.(psd|psb)$/i, ''), bytes }, [bytes]);
  await opened;
}

// ---- Start -----------------------------------------------------------------------------------

(async function start() {
  const ready = waitFor('ready');
  call('init', { canvas: offscreen, customBrushes: JSON.stringify(customBrushes) }, [offscreen]);
  await ready;
  if (!brushes.some(b => b.id === settings.brushes.brush)) settings.brushes.brush = 'studio_pen';
  $('#boot').hidden = true;
  showGallery();
})();

// For automated checks.
window.__haven = { view, doc: () => doc, settings, call };
