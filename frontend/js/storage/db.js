// Minimal IndexedDB wrapper used only as an offline fallback: a generic key/value
// "cache" store (last-known-good copies of workspaces/variables/etc, so the app has
// something to show if the backend is unreachable) and an "offline_history" queue for
// calculations performed by the client-side engine (js/engine/localEngine.js) while
// disconnected, which can be pushed into real history once the backend is back.

const DB_NAME = 'calcforge';
const DB_VERSION = 1;

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('cache')) {
        db.createObjectStore('cache', { keyPath: 'key' });
      }
      if (!db.objectStoreNames.contains('offline_history')) {
        db.createObjectStore('offline_history', { keyPath: 'id', autoIncrement: true });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

let dbPromise = null;
function db() {
  if (!dbPromise) dbPromise = openDb().catch((err) => { dbPromise = null; throw err; });
  return dbPromise;
}

function isSupported() {
  return typeof indexedDB !== 'undefined';
}

export async function cacheSet(key, value) {
  if (!isSupported()) return;
  try {
    const database = await db();
    await new Promise((resolve, reject) => {
      const tx = database.transaction('cache', 'readwrite');
      tx.objectStore('cache').put({ key, value, savedAt: Date.now() });
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // Non-fatal: caching is best-effort.
  }
}

export async function cacheGet(key) {
  if (!isSupported()) return null;
  try {
    const database = await db();
    return await new Promise((resolve, reject) => {
      const tx = database.transaction('cache', 'readonly');
      const req = tx.objectStore('cache').get(key);
      req.onsuccess = () => resolve(req.result ? req.result.value : null);
      req.onerror = () => reject(req.error);
    });
  } catch {
    return null;
  }
}

export async function queueOfflineCalculation(entry) {
  if (!isSupported()) return null;
  try {
    const database = await db();
    return await new Promise((resolve, reject) => {
      const tx = database.transaction('offline_history', 'readwrite');
      const req = tx.objectStore('offline_history').add({ ...entry, createdAt: new Date().toISOString() });
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  } catch {
    return null;
  }
}

export async function listOfflineCalculations() {
  if (!isSupported()) return [];
  try {
    const database = await db();
    return await new Promise((resolve, reject) => {
      const tx = database.transaction('offline_history', 'readonly');
      const req = tx.objectStore('offline_history').getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  } catch {
    return [];
  }
}

export async function clearOfflineCalculations() {
  if (!isSupported()) return;
  try {
    const database = await db();
    await new Promise((resolve, reject) => {
      const tx = database.transaction('offline_history', 'readwrite');
      tx.objectStore('offline_history').clear();
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // ignore
  }
}
