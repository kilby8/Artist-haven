// The drawing engine runs here, off the main thread, so the pen never waits for painting.
// It owns the document, draws straight into the canvas the page handed over, and saves
// projects to IndexedDB. One extra copy of this worker renders brush previews.

import { WebEditor } from './engine/Artist-haven-web-web.mjs';
import * as db from './db.js';

const Editor = WebEditor.Companion;

let ed = null;          // WebEditor for the open project
let projectId = null;
let projectMeta = null;
let canvas = null;      // OffscreenCanvas shown on the page
let ctx = null;
let saveTimer = 0;
let saving = Promise.resolve();
let frameQueued = false;
let metaDirty = false;

const post = (msg, transfer) => self.postMessage(msg, transfer || []);

function rgbaImage(px, w, h) {
  return new ImageData(new Uint8ClampedArray(px.buffer, px.byteOffset, w * h * 4), w, h);
}

// ---- Drawing -------------------------------------------------------------------------------

function flush() {
  frameQueued = false;
  if (!ed || !ctx) return;
  const d = ed.takeDirty();
  if (d.length === 0) return;
  if (d[2] > d[0] && d[3] > d[1]) {
    const px = ed.render(d[0], d[1], d[2], d[3]);
    if (px.length) ctx.putImageData(rgbaImage(px, d[2] - d[0], d[3] - d[1]), d[0], d[1]);
  }
  if (d[4] === 1) sendState();
}

// ---- Watercolour keeps flowing after the pen lifts -------------------------------------------

let waterTimer = 0;
let waterLast = 0;

function startWater() {
  if (waterTimer || !ed || !ed.isWaterActive) return;
  post({ t: 'water', active: true });
  waterLast = performance.now();
  waterTimer = setTimeout(waterTick, 16);
}

function waterTick() {
  waterTimer = 0;
  if (!ed) return;
  const now = performance.now();
  // While painting, the stroke moves the water in step with the pen.
  if (!ed.isStroking) {
    const historyChanged = ed.tickWater(Math.min(now - waterLast, 100));
    queueFrame();
    if (historyChanged) { sendState(); scheduleSave(); }
  }
  waterLast = now;
  if (ed.isWaterActive) waterTimer = setTimeout(waterTick, 16);
  else post({ t: 'water', active: false });
}

function stopWater() {
  clearTimeout(waterTimer);
  waterTimer = 0;
}

function queueFrame() {
  if (frameQueued) return;
  frameQueued = true;
  // A short timeout, not requestAnimationFrame: rAF can stall in a worker while a pen is busy.
  setTimeout(flush, 0);
}

let lastSelectionStamp = 0;

function sendState() {
  if (!ed) return;
  post({
    t: 'state',
    canUndo: ed.canUndo,
    canRedo: ed.canRedo,
    layers: JSON.parse(ed.layersJson()),
    groups: JSON.parse(ed.groupsJson()),
    paperColor: ed.paperColor,
    paperVisible: ed.paperVisible,
    editingMask: ed.editingMask,
    hasSelection: ed.hasSelection,
    transforming: ed.isTransforming,
    adjusting: ed.isAdjusting,
  });
  sendSelection();
}

// The selection outline, as a coarse coverage grid the page turns into marching ants.
function sendSelection(force) {
  const stamp = ed.selectionStamp;
  if (!force && stamp === lastSelectionStamp) return;
  lastSelectionStamp = stamp;
  if (!ed.hasSelection) { post({ t: 'selection', grid: null }); return; }
  const g = ed.selectionGrid(1024);
  const bytes = new Uint8Array(g.buffer.slice(g.byteOffset, g.byteOffset + g.byteLength));
  post({ t: 'selection', grid: bytes, w: ed.gridWidth, h: ed.gridHeight, step: ed.gridStep }, [bytes.buffer]);
}

function changed() {
  queueFrame();
  scheduleSave();
}

// ---- Saving --------------------------------------------------------------------------------

function scheduleSave(delay = 1500) {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    if (ed && ed.isStroking) return scheduleSave(800);
    saveNow();
  }, delay);
}

function saveNow() {
  clearTimeout(saveTimer);
  saving = saving.then(doSave).catch(err => post({ t: 'error', message: 'Saving failed: ' + err.message }));
  return saving;
}

async function doSave() {
  if (!ed || !projectId) return;
  const ids = ed.takeUnsavedLayers();
  if (ids.length === 0 && !metaDirty) return;
  metaDirty = false;
  const id = projectId;
  const project = JSON.parse(ed.projectJson());
  const writes = [];
  for (const layerId of ids) {
    if (layerId === 'project') continue;
    const info = project.layers.find(l => l.id === layerId);
    if (!info) continue;
    const data = ed.layerData(layerId);
    writes.push(db.gzip(data).then(blob => [layerId, blob]));
    if (info.mask) writes.push(db.gzip(ed.maskData(layerId)).then(blob => [layerId + db.MASK_SUFFIX, blob]));
  }
  const blobs = await Promise.all(writes);
  const pictureChanged = ids.length > 0;
  const big = pictureChanged ? frameCanvas(720) : null;
  const thumb = big ? await scaledBlob(big, 480, 'image/png') : projectMeta.thumb;
  projectMeta = { ...projectMeta, modified: Date.now(), width: project.width, height: project.height, thumb };
  await db.saveProject(id, projectMeta, project, blobs);
  // Time-lapse: one frame each time the drawing settles after a change.
  if (big && revisionAtLastFrame !== ed.revision) {
    revisionAtLastFrame = ed.revision;
    try { await db.addFrame(id, await big.convertToBlob({ type: 'image/webp', quality: 0.82 })); } catch (err) { console.warn(err); }
  }
  post({ t: 'saved', id });
}

let revisionAtLastFrame = -1;

function frameCanvas(size) {
  const t = ed.thumbnail(size);
  const w = t[0], h = t[1];
  const c = new OffscreenCanvas(w, h);
  c.getContext('2d').putImageData(rgbaImage(t.subarray(2), w, h), 0, 0);
  return c;
}

async function scaledBlob(src, size, type) {
  const k = Math.min(1, size / Math.max(src.width, src.height));
  const c = new OffscreenCanvas(Math.max(1, Math.round(src.width * k)), Math.max(1, Math.round(src.height * k)));
  const x = c.getContext('2d');
  x.imageSmoothingQuality = 'high';
  x.drawImage(src, 0, 0, c.width, c.height);
  return c.convertToBlob({ type });
}

async function thumbnailBlob(size) {
  return frameCanvas(size).convertToBlob({ type: 'image/png' });
}

// ---- Opening -------------------------------------------------------------------------------

function attach(newEd, meta) {
  ed = newEd;
  projectId = meta.id;
  projectMeta = meta;
  canvas.width = ed.width;
  canvas.height = ed.height;
  // desynchronized lets Chromium show strokes without waiting for the page's next frame.
  ctx = canvas.getContext('2d', { alpha: false, desynchronized: true });
  lastSelectionStamp = 0;
  revisionAtLastFrame = -1;
  flush();
  sendState();
  sendSelection(true);
  post({ t: 'opened', id: meta.id, name: meta.name, width: ed.width, height: ed.height });
}

async function createProject(m) {
  await closeProject();
  const now = Date.now();
  const meta = { id: m.id, name: m.name, created: now, modified: now, width: m.width, height: m.height };
  const e = Editor.blank(m.width, m.height, m.paper | 0);
  attach(e, meta);
  if (m.photo) {
    e.addImageLayer(m.photo.name, new Int32Array(m.photo.rgba), m.photo.width, m.photo.height);
  }
  await saveNow();
  queueFrame();
}

async function openProject(id) {
  await closeProject();
  const loaded = await db.loadProject(id);
  if (!loaded) throw new Error('This drawing could not be found.');
  const { meta, project, layers } = loaded;
  const e = Editor.forRestore(project.width, project.height, project.paperColor | 0, project.paperVisible !== false);
  for (const g of project.groups || []) e.restoreGroup(g.id, g.name, g.visible !== false, g.opacity ?? 1);
  for (const l of project.layers) {
    const blob = layers.get(l.id);
    const data = blob ? await db.gunzip(blob) : new Int32Array(1);
    const maskBlob = l.mask ? layers.get(l.id + db.MASK_SUFFIX) : null;
    const mask = maskBlob ? await db.gunzip(maskBlob) : new Int32Array(0);
    e.restoreLayer(l.id, l.name, l.visible !== false, l.opacity ?? 1, l.blendMode || 'NORMAL', !!l.alphaLock, !!l.clipping, data, l.group || '', mask);
  }
  e.finishRestore(project.activeLayerId || '');
  attach(e, meta);
}

// A Photoshop file becomes a new project.
async function openPsd(m) {
  await closeProject();
  const e = Editor.fromPsd(new Int8Array(m.bytes), 4096);
  const now = Date.now();
  const meta = { id: m.id, name: m.name, created: now, modified: now, width: e.width, height: e.height };
  attach(e, meta);
  e.markEverythingUnsaved();
  await saveNow();
  if (Editor.psdWarnings) post({ t: 'toast', message: 'Opened, but ' + Editor.psdWarnings.split('\n').join('; ') + '.' });
}

async function closeProject() {
  if (!ed) return;
  if (ed.isStroking) ed.endStroke();
  stopWater();
  if (ed.isWaterActive) ed.dryWater();
  await saveNow();
  ed = null;
  projectId = null;
  projectMeta = null;
}

function beginTransform() {
  const r = ed.beginTransform();
  queueFrame();
  post({ t: 'transformStarted', rect: r.length ? [...r] : null });
  if (r.length) sendState();
}

// ---- Export --------------------------------------------------------------------------------

async function exportImage(type, withPaper) {
  const px = ed.flatten(withPaper || type === 'image/jpeg');
  const c = new OffscreenCanvas(ed.width, ed.height);
  c.getContext('2d').putImageData(rgbaImage(px, ed.width, ed.height), 0, 0);
  return c.convertToBlob({ type, quality: 0.95 });
}

// ---- Brush previews ------------------------------------------------------------------------

async function renderPreviews(list, w, h, color, bg) {
  for (const id of list) {
    const p = Editor.brushPreview(id, w, h, color | 0, bg | 0);
    const c = new OffscreenCanvas(p[0], p[1]);
    c.getContext('2d').putImageData(rgbaImage(p.subarray(2), p[0], p[1]), 0, 0);
    const bitmap = c.transferToImageBitmap();
    post({ t: 'preview', id, bitmap }, [bitmap]);
    await new Promise(r => setTimeout(r, 0));
  }
}

// ---- Messages ------------------------------------------------------------------------------

const handlers = {
  init(m) {
    if (m.canvas) canvas = m.canvas;
    if (m.customBrushes) Editor.setCustomBrushes(m.customBrushes);
    post({
      t: 'ready',
      brushes: JSON.parse(Editor.brushesJson()),
      blendModes: JSON.parse(Editor.blendModesJson()),
      brushOptions: JSON.parse(Editor.brushOptionsJson()),
    });
  },
  previews(m) { return renderPreviews(m.ids, m.width, m.height, m.color, m.background); },
  openPsd(m) { return openPsd(m); },
  async exportPsd(m) {
    const bytes = ed.exportPsd();
    const blob = new Blob([bytes], { type: 'image/vnd.adobe.photoshop' });
    post({ t: 'exported', blob, type: 'image/vnd.adobe.photoshop', request: m.request });
  },
  async frames() {
    const list = projectId ? await db.listFrames(projectId) : [];
    post({ t: 'frames', frames: list });
  },

  // ---- Brush Studio ----
  setCustomBrushes(m) {
    Editor.setCustomBrushes(m.json);
    post({ t: 'brushes', brushes: JSON.parse(Editor.brushesJson()) });
  },
  brushJson(m) { post({ t: 'brushJson', id: m.id, json: Editor.brushJson(m.id) }); },
  previewJson(m) {
    const p = Editor.brushPreviewJson(m.json, m.width, m.height, m.color | 0, m.background | 0);
    const c = new OffscreenCanvas(p[0], p[1]);
    c.getContext('2d').putImageData(rgbaImage(p.subarray(2), p[0], p[1]), 0, 0);
    const bitmap = c.transferToImageBitmap();
    post({ t: 'studioPreview', serial: m.serial, bitmap }, [bitmap]);
  },
  create(m) { return createProject(m); },
  open(m) { return openProject(m.id); },
  async close() { await closeProject(); post({ t: 'closed' }); },
  saveNow() { return saveNow(); },

  begin(m) {
    if (!ed) return;
    const p = m.params, q = m.point;
    const ok = ed.beginStroke(p.brushId, p.tool, p.size, p.opacity, p.color | 0, p.symmetry, p.usePressure,
      p.streamline, p.paintLoad ?? -1, q[0], q[1], q[2], q[3], q[4], q[5]);
    if (!ok && !ed.isStroking) post({ t: 'toast', message: 'This layer is hidden. Show it to paint on it.' });
    queueFrame();
    startWater();
  },
  move(m) {
    if (!ed || !ed.isStroking) return;
    const a = m.points;
    for (let i = 0; i < a.length; i += 6) ed.strokeTo(a[i], a[i + 1], a[i + 2], a[i + 3], a[i + 4], a[i + 5]);
    queueFrame();
  },
  end() { if (ed && ed.isStroking) { ed.endStroke(); sendState(); changed(); startWater(); } },
  dryWater() { if (ed) { ed.dryWater(); sendState(); changed(); post({ t: 'water', active: false }); } },
  cancel() { if (ed && ed.isStroking) { ed.cancelStroke(); queueFrame(); } },
  quickShape() { if (ed && ed.isStroking && ed.quickShape()) { post({ t: 'snapped' }); queueFrame(); } },

  fillShape(m) { if (ed) { ed.fillPolygon(new Float32Array(m.points), m.color | 0); sendState(); changed(); } },
  fill(m) { ed.fill(m.x, m.y, m.color | 0, m.tolerance, m.allLayers); sendState(); changed(); },
  sample(m) { post({ t: 'sampled', color: ed.sampleColor(m.x, m.y), final: m.final, id: m.id }); },
  undo() { ed.undo(); sendState(); changed(); startWater(); },
  redo() { ed.redo(); sendState(); changed(); startWater(); },
  paper(m) { ed.setPaper(m.color | 0, m.visible); sendState(); changed(); },

  layer(m) {
    const a = m.args || [];
    switch (m.op) {
      case 'addMask': ed.addMask(a[0]); break;
      case 'deleteMask': ed.deleteMask(a[0]); break;
      case 'applyMask': ed.applyMask(a[0]); break;
      case 'editMask': ed.setEditingMask(!!a[0]); break;
      case 'group': ed.groupLayer(a[0], !!a[1]); break;
      case 'ungroup': ed.ungroup(a[0]); break;
      case 'leaveGroup': ed.removeFromGroup(a[0]); break;
      case 'groupProps': ed.setGroupProps(a[0], a[1], a[2], a[3]); break;
      case 'previewGroupOpacity': ed.previewGroupOpacity(a[0], a[1]); queueFrame(); return;
      case 'moveGroup': ed.moveGroup(a[0], !!a[1]); break;
      case 'deleteGroup': ed.deleteGroup(a[0]); break;
      case 'flattenGroup': ed.flattenGroup(a[0]); break;
      case 'add': ed.addLayer(); break;
      case 'duplicate': ed.duplicateLayer(a[0]); break;
      case 'delete': ed.deleteLayer(a[0]); break;
      case 'select': ed.selectLayer(a[0]); break;
      case 'move': ed.moveLayer(a[0], a[1]); break;
      case 'mergeDown': ed.mergeDown(a[0]); break;
      case 'clear': ed.clearLayer(a[0]); break;
      case 'props': ed.setLayerProps(a[0], a[1], a[2], a[3], a[4], a[5], a[6]); break;
      case 'previewOpacity': ed.previewLayerOpacity(a[0], a[1]); queueFrame(); return;
      case 'commitOpacity': ed.commitLayerOpacity(a[0]); break;
    }
    sendState();
    changed();
  },
  async layerThumbs(m) {
    const out = [];
    const bitmap = (t) => {
      const c = new OffscreenCanvas(t[0], t[1]);
      c.getContext('2d').putImageData(rgbaImage(t.subarray(2), t[0], t[1]), 0, 0);
      return c.transferToImageBitmap();
    };
    for (const l of JSON.parse(ed.layersJson())) {
      const t = ed.layerThumbnail(l.id, m.size);
      if (t[0] < 1) continue;
      const entry = { id: l.id, bitmap: bitmap(t) };
      if (l.hasMask) {
        const mt = ed.maskThumbnail(l.id, m.size);
        if (mt[0] > 0) entry.mask = bitmap(mt);
      }
      out.push(entry);
    }
    const transfer = [];
    for (const o of out) { transfer.push(o.bitmap); if (o.mask) transfer.push(o.mask); }
    post({ t: 'layerThumbs', thumbs: out }, transfer);
  },
  importImage(m) {
    if (m.x != null) ed.addImageLayerAt(m.name, new Int32Array(m.rgba), m.width, m.height, Math.round(m.x), Math.round(m.y));
    else ed.addImageLayer(m.name, new Int32Array(m.rgba), m.width, m.height);
    sendState();
    changed();
    if (m.thenTransform) beginTransform();
  },

  // ---- Selections ----
  select(m) {
    const mode = m.mode | 0;
    switch (m.op) {
      case 'polygon': ed.selectPolygon(new Float32Array(m.points), mode); break;
      case 'similar': ed.selectSimilar(m.x, m.y, m.tolerance, !!m.allLayers, mode); break;
      case 'all': ed.selectAll(); break;
      case 'none': ed.deselect(); break;
      case 'invert': ed.invertSelection(); break;
      case 'clear': ed.clearSelected(); break;
      case 'fill': ed.fillSelection(m.color | 0); break;
      case 'copy': ed.selectionToNewLayer(false); break;
      case 'cut': ed.selectionToNewLayer(true); break;
    }
    sendState();
    changed();
  },

  // ---- Move and transform ----
  transformBegin() { beginTransform(); },
  transformPreview(m) {
    const a = m.m;
    ed.previewTransform(a[0], a[1], a[2], a[3], a[4], a[5], m.quality | 0);
    flush();
    post({ t: 'transformed', serial: m.serial });
  },
  transformCommit(m) {
    const a = m.m;
    ed.commitTransform(a[0], a[1], a[2], a[3], a[4], a[5], m.quality | 0);
    sendState();
    changed();
  },
  transformCancel() { ed.cancelTransform(); sendState(); queueFrame(); },

  // ---- Adjustments ----
  adjustBegin(m) {
    const ok = ed.beginAdjust(m.kind);
    queueFrame();
    post({ t: 'adjustStarted', ok });
    if (ok) sendState();
  },
  adjustPreview(m) {
    ed.previewAdjust(m.a, m.b, m.c);
    flush();
    post({ t: 'adjusted', serial: m.serial });
  },
  adjustCommit() { ed.commitAdjust(); sendState(); changed(); },
  adjustCancel() { ed.cancelAdjust(); sendState(); queueFrame(); },

  // ---- Liquify ----
  liquifyBegin(m) {
    const q = m.point;
    ed.beginLiquify(m.mode, m.size, m.strength, q[0], q[1], q[2]);
    queueFrame();
  },
  liquifyMove(m) {
    const a = m.points;
    for (let i = 0; i < a.length; i += 6) ed.liquifyTo(a[i], a[i + 1], a[i + 2]);
    queueFrame();
  },
  liquifyEnd() { ed.endLiquify(); sendState(); changed(); },
  async export(m) {
    const blob = await exportImage(m.type, m.withPaper);
    post({ t: 'exported', blob, type: m.type, request: m.request });
  },
  rename(m) {
    if (projectMeta) {
      projectMeta.name = m.name;
      metaDirty = true;
      scheduleSave(100);
    }
  },
};

// Messages are handled strictly in order, even the ones that wait on storage.
let queue = Promise.resolve();
self.onmessage = (e) => {
  const m = e.data;
  const h = handlers[m.t];
  if (!h) return;
  queue = queue.then(() => h(m)).catch(err => {
    console.error(err);
    post({ t: 'error', message: err && err.message ? err.message : String(err), request: m.t });
  });
};
