// Projects live in IndexedDB, in this browser profile:
//   projects: id -> { id, name, created, modified, width, height, thumb (PNG blob) }
//   docs:     id -> project JSON (size, paper, layer list)
//   layers:   "projectId/layerId" -> gzip blob of the layer's tiles
// Layers are saved only when they change, and all three stores are written in one transaction.

const DB_NAME = 'artist-haven';
let dbPromise = null;

function open() {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => {
        const d = req.result;
        d.createObjectStore('projects', { keyPath: 'id' });
        d.createObjectStore('docs');
        d.createObjectStore('layers');
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }
  return dbPromise;
}

function done(tx) {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error('Storage was interrupted'));
  });
}

function request(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

const layerRange = (id) => IDBKeyRange.bound(id + '/', id + '/￿');

export async function listProjects() {
  const d = await open();
  const tx = d.transaction('projects');
  const all = await request(tx.objectStore('projects').getAll());
  return all.sort((a, b) => b.modified - a.modified);
}

export async function saveProject(id, meta, project, layerBlobs) {
  const d = await open();
  const tx = d.transaction(['projects', 'docs', 'layers'], 'readwrite');
  tx.objectStore('projects').put(meta);
  tx.objectStore('docs').put(project, id);
  const layers = tx.objectStore('layers');
  for (const [layerId, blob] of layerBlobs) layers.put(blob, id + '/' + layerId);
  // Drop layers that were deleted from the project.
  const keep = new Set(project.layers.map(l => id + '/' + l.id));
  const keys = await request(layers.getAllKeys(layerRange(id)));
  for (const k of keys) if (!keep.has(k)) layers.delete(k);
  await done(tx);
}

export async function loadProject(id) {
  const d = await open();
  const tx = d.transaction(['projects', 'docs', 'layers']);
  const meta = await request(tx.objectStore('projects').get(id));
  const project = await request(tx.objectStore('docs').get(id));
  if (!meta || !project) return null;
  const store = tx.objectStore('layers');
  const keys = await request(store.getAllKeys(layerRange(id)));
  const values = await request(store.getAll(layerRange(id)));
  const layers = new Map();
  keys.forEach((k, i) => layers.set(k.slice(id.length + 1), values[i]));
  return { meta, project, layers };
}

export async function renameProject(id, name) {
  const d = await open();
  const tx = d.transaction('projects', 'readwrite');
  const store = tx.objectStore('projects');
  const meta = await request(store.get(id));
  if (meta) store.put({ ...meta, name });
  await done(tx);
}

export async function deleteProject(id) {
  const d = await open();
  const tx = d.transaction(['projects', 'docs', 'layers'], 'readwrite');
  tx.objectStore('projects').delete(id);
  tx.objectStore('docs').delete(id);
  tx.objectStore('layers').delete(layerRange(id));
  await done(tx);
}

export async function duplicateProject(id, newId, name) {
  const loaded = await loadProject(id);
  if (!loaded) return;
  const now = Date.now();
  const meta = { ...loaded.meta, id: newId, name, created: now, modified: now };
  await saveProject(newId, meta, loaded.project, [...loaded.layers.entries()]);
}

export async function gzip(int32) {
  const stream = new Blob([int32]).stream().pipeThrough(new CompressionStream('gzip'));
  return new Response(stream).blob();
}

export async function gunzip(blob) {
  const stream = blob.stream().pipeThrough(new DecompressionStream('gzip'));
  return new Int32Array(await new Response(stream).arrayBuffer());
}
