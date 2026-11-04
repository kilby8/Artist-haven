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
}, readJson('ah.settings', {}));
// Version 2 added real blending brushes: move the smudge tool off the old airbrush default.
if ((settings.version | 0) < 2) {
  if (settings.brushes.smudge === 'soft_airbrush') settings.brushes.smudge = 'soft_blend';
  settings.version = 2;
}

function readJson(key, fallback) {
  try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; } catch { return fallback; }
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
const waiters = new Map();

function call(t, msg = {}, transfer = []) { worker.postMessage({ t, ...msg }, transfer); }
function waitFor(type) { return new Promise(resolve => waiters.set(type, resolve)); }

worker.onmessage = (e) => {
  const m = e.data;
  const w = waiters.get(m.t);
  if (w) { waiters.delete(m.t); w(m); }
  switch (m.t) {
    case 'ready': brushes = m.brushes; blendModes = m.blendModes; break;
    case 'state': onDocState(m); break;
    case 'opened': onOpened(m); break;
    case 'snapped': input.snapped = true; break;
    case 'sampled': onSampled(m); break;
    case 'layerThumbs': onLayerThumbs(m.thumbs); break;
    case 'exported': saveBlob(m.blob, exportName(m.type)); break;
    case 'toast': toast(m.message); break;
    case 'error':
      console.error(m.message);
      toast(m.message, 5000);
      if (m.request === 'open' || m.request === 'create') showGallery();
      break;
  }
};

// Brush previews come from a second worker so they never hold up the pen.
const previews = new Map();
let previewWorker = null;
function startPreviews() {
  if (previewWorker) return;
  previewWorker = new Worker('worker.js', { type: 'module' });
  previewWorker.onmessage = (e) => {
    const m = e.data;
    if (m.t === 'ready') {
      previewWorker.postMessage({ t: 'previews', ids: brushes.map(b => b.id), width: 480, height: 72, color: 0xFFF2F2F2 | 0, background: 0xFF2B2B31 | 0 });
    } else if (m.t === 'preview') {
      previews.set(m.id, m.bitmap);
      const c = document.querySelector(`.brush-item[data-id="${m.id}"] canvas`);
      if (c) drawPreview(c, m.bitmap);
    }
  };
  previewWorker.postMessage({ t: 'init' });
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
  updateToolUi();
}

function onDocState(m) {
  Object.assign(doc, m);
  $('#b-undo').disabled = !m.canUndo;
  $('#b-redo').disabled = !m.canRedo;
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
let tool = 'brush';         // brush | smudge | eraser | dropper | fill
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
$('#b-undo').addEventListener('click', () => call('undo'));
$('#b-redo').addEventListener('click', () => call('redo'));
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
  for (const b of brushes.filter(x => x.category === brushCategory)) {
    const item = document.createElement('button');
    item.className = 'brush-item';
    item.dataset.id = b.id;
    item.classList.toggle('active', b.id === active);
    const name = document.createElement('div');
    name.textContent = b.name;
    const c = document.createElement('canvas');
    c.width = 480; c.height = 72;
    const bmp = previews.get(b.id);
    if (bmp) drawPreview(c, bmp);
    item.append(name, c);
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
  for (const [i, l] of doc.layers.entries()) {
    const row = document.createElement('div');
    row.className = 'layer-row' + (l.active ? ' active' : '') + (l.visible ? '' : ' hidden-layer');
    const th = document.createElement('div');
    th.className = 'lthumb';
    const c = document.createElement('canvas');
    const bmp = layerThumbs.get(l.id);
    if (bmp) { c.width = bmp.width; c.height = bmp.height; c.getContext('2d').drawImage(bmp, 0, 0); }
    else { c.width = 1; c.height = 1; }
    th.append(c);
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
    row.append(th, name, vis);
    row.addEventListener('click', (e) => {
      if (l.active) layerMenu(l, i, e.clientX, e.clientY);
      else call('layer', { op: 'select', args: [l.id] });
    });
    row.addEventListener('contextmenu', (e) => { e.preventDefault(); layerMenu(l, i, e.clientX, e.clientY); });
    list.append(row);
  }
  $('#paper-swatch').style.background = hex(doc.paperColor);
  $('#paper-vis').innerHTML = `<svg><use href="#i-${doc.paperVisible ? 'eye' : 'eye-off'}"/></svg>`;
  clearTimeout(layerThumbTimer);
  layerThumbTimer = setTimeout(() => call('layerThumbs', { size: 128 }), 120);
}
function onLayerThumbs(thumbs) {
  for (const t of thumbs) layerThumbs.set(t.id, t.bitmap);
  for (const row of $$('.layer-row')) {
    const idx = [...row.parentNode.children].indexOf(row);
    const l = doc.layers[idx];
    const bmp = l && layerThumbs.get(l.id);
    if (!bmp) continue;
    const c = row.querySelector('canvas');
    c.width = bmp.width; c.height = bmp.height;
    c.getContext('2d').drawImage(bmp, 0, 0);
  }
}
$('#layer-add').addEventListener('click', () => call('layer', { op: 'add' }));
$('#paper-vis').addEventListener('click', () => call('paper', { color: doc.paperColor, visible: !doc.paperVisible }));
$('#paper-swatch').addEventListener('click', () => { togglePanel('p-actions'); });

function layerMenu(l, index, x, y) {
  const menu = $('#layer-menu');
  const n = doc.layers.length;
  const modes = blendModes.map(m => `<option value="${m.id}"${m.id === l.blendMode ? ' selected' : ''}>${m.name}</option>`).join('');
  menu.innerHTML = `
    <button data-op="rename">Rename</button>
    <label>Opacity <input type="range" min="0" max="1" step="0.01" value="${l.opacity}" data-op="opacity"></label>
    <label>Blend <select data-op="blend">${modes}</select></label>
    <label>Alpha lock <input type="checkbox" data-op="alphaLock"${l.alphaLock ? ' checked' : ''}></label>
    <label>Clip to layer below <input type="checkbox" data-op="clipping"${l.clipping ? ' checked' : ''}></label>
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
  };
  const range = menu.querySelector('[data-op=opacity]');
  range.addEventListener('input', () => call('layer', { op: 'previewOpacity', args: [l.id, parseFloat(range.value)] }));
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
  return base + (type === 'image/jpeg' ? '.jpg' : '.png');
}
async function saveBlob(blob, name) {
  if (window.showSaveFilePicker) {
    try {
      const jpeg = blob.type === 'image/jpeg';
      const handle = await showSaveFilePicker({
        suggestedName: name,
        startIn: 'pictures',
        types: [{ description: jpeg ? 'JPEG picture' : 'PNG picture', accept: { [blob.type]: [jpeg ? '.jpg' : '.png'] } }],
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
  let p = pen ? e.pressure : 1;
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
          if (tap.max === 2) { call('undo'); toast('Undo', 900); }
          if (tap.max === 3) { call('redo'); toast('Redo', 900); }
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
  const showSize = sliderPreview;
  const at = showSize ? [innerWidth / 2, innerHeight / 2] : input.hover;
  if (!at || !brushes.length) return;
  if (!showSize && !(paintTools.includes(tool)) ) return;
  const r = Math.max(1.5, sizeFor(tool) * view.s / 2);
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
  if ((e.ctrlKey || e.metaKey) && k === 'z') { e.preventDefault(); call(e.shiftKey ? 'redo' : 'undo'); return; }
  if ((e.ctrlKey || e.metaKey) && k === 'y') { e.preventDefault(); call('redo'); return; }
  if ((e.ctrlKey || e.metaKey) && k === 's') { e.preventDefault(); call('saveNow'); toast('Saved', 900); return; }
  if (e.ctrlKey || e.metaKey) return;
  if (k === ' ') { input.space = true; stage.classList.add('space'); e.preventDefault(); return; }
  if (k === 'alt') { input.alt = true; e.preventDefault(); return; }
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

// ---- Start -----------------------------------------------------------------------------------

(async function start() {
  const ready = waitFor('ready');
  call('init', { canvas: offscreen }, [offscreen]);
  await ready;
  if (!brushes.some(b => b.id === settings.brushes.brush)) settings.brushes.brush = 'studio_pen';
  $('#boot').hidden = true;
  showGallery();
})();

// For automated checks.
window.__haven = { view, doc: () => doc, settings, call };
