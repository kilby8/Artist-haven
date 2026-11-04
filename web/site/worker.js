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

function queueFrame() {
  if (frameQueued) return;
  frameQueued = true;
  // A short timeout, not requestAnimationFrame: rAF can stall in a worker while a pen is busy.
  setTimeout(flush, 0);
}

function sendState() {
  if (!ed) return;
  post({
    t: 'state',
    canUndo: ed.canUndo,
    canRedo: ed.canRedo,
    layers: JSON.parse(ed.layersJson()),
    paperColor: ed.paperColor,
    paperVisible: ed.paperVisible,
  });
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
    if (!project.layers.some(l => l.id === layerId)) continue;
    const data = ed.layerData(layerId);
    writes.push(db.gzip(data).then(blob => [layerId, blob]));
  }
  const blobs = await Promise.all(writes);
  const thumb = await thumbnailBlob(480);
  projectMeta = { ...projectMeta, modified: Date.now(), width: project.width, height: project.height, thumb };
  await db.saveProject(id, projectMeta, project, blobs);
  post({ t: 'saved', id });
}

async function thumbnailBlob(size) {
  const t = ed.thumbnail(size);
  const w = t[0], h = t[1];
  const c = new OffscreenCanvas(w, h);
  c.getContext('2d').putImageData(rgbaImage(t.subarray(2), w, h), 0, 0);
  return c.convertToBlob({ type: 'image/png' });
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
  flush();
  sendState();
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
  for (const l of project.layers) {
    const blob = layers.get(l.id);
    const data = blob ? await db.gunzip(blob) : new Int32Array(1);
    e.restoreLayer(l.id, l.name, l.visible !== false, l.opacity ?? 1, l.blendMode || 'NORMAL', !!l.alphaLock, !!l.clipping, data);
  }
  e.finishRestore(project.activeLayerId || '');
  attach(e, meta);
}

async function closeProject() {
  if (!ed) return;
  if (ed.isStroking) ed.endStroke();
  await saveNow();
  ed = null;
  projectId = null;
  projectMeta = null;
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
    post({
      t: 'ready',
      brushes: JSON.parse(Editor.brushesJson()),
      blendModes: JSON.parse(Editor.blendModesJson()),
    });
  },
  previews(m) { return renderPreviews(m.ids, m.width, m.height, m.color, m.background); },
  create(m) { return createProject(m); },
  open(m) { return openProject(m.id); },
  async close() { await closeProject(); post({ t: 'closed' }); },
  saveNow() { return saveNow(); },

  begin(m) {
    if (!ed) return;
    const p = m.params, q = m.point;
    const ok = ed.beginStroke(p.brushId, p.tool, p.size, p.opacity, p.color | 0, p.symmetry, p.usePressure,
      p.streamline, q[0], q[1], q[2], q[3], q[4], q[5]);
    if (!ok && !ed.isStroking) post({ t: 'toast', message: 'This layer is hidden. Show it to paint on it.' });
    queueFrame();
  },
  move(m) {
    if (!ed || !ed.isStroking) return;
    const a = m.points;
    for (let i = 0; i < a.length; i += 6) ed.strokeTo(a[i], a[i + 1], a[i + 2], a[i + 3], a[i + 4], a[i + 5]);
    queueFrame();
  },
  end() { if (ed && ed.isStroking) { ed.endStroke(); sendState(); changed(); } },
  cancel() { if (ed && ed.isStroking) { ed.cancelStroke(); queueFrame(); } },
  quickShape() { if (ed && ed.isStroking && ed.quickShape()) { post({ t: 'snapped' }); queueFrame(); } },

  fill(m) { ed.fill(m.x, m.y, m.color | 0, m.tolerance, m.allLayers); sendState(); changed(); },
  sample(m) { post({ t: 'sampled', color: ed.sampleColor(m.x, m.y), final: m.final, id: m.id }); },
  undo() { ed.undo(); sendState(); changed(); },
  redo() { ed.redo(); sendState(); changed(); },
  paper(m) { ed.setPaper(m.color | 0, m.visible); sendState(); changed(); },

  layer(m) {
    const a = m.args || [];
    switch (m.op) {
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
    for (const l of JSON.parse(ed.layersJson())) {
      const t = ed.layerThumbnail(l.id, m.size);
      if (t[0] < 1) continue;
      const c = new OffscreenCanvas(t[0], t[1]);
      c.getContext('2d').putImageData(rgbaImage(t.subarray(2), t[0], t[1]), 0, 0);
      out.push({ id: l.id, bitmap: c.transferToImageBitmap() });
    }
    post({ t: 'layerThumbs', thumbs: out }, out.map(o => o.bitmap));
  },
  importImage(m) {
    ed.addImageLayer(m.name, new Int32Array(m.rgba), m.width, m.height);
    sendState();
    changed();
  },
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
