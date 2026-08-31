// Central, minimal app state. Settings persist to localStorage (a browser-native,
// zero-dependency store) so they survive reloads; actual calculation data (workspaces,
// history, etc.) lives in the backend/MySQL and, as an offline fallback, IndexedDB - see
// js/storage/db.js.

const LS_PREFIX = 'calcforge:';

function lsGet(key, fallback) {
  const raw = localStorage.getItem(LS_PREFIX + key);
  if (raw === null) return fallback;
  try { return JSON.parse(raw); } catch { return raw; }
}
function lsSet(key, value) {
  localStorage.setItem(LS_PREFIX + key, typeof value === 'string' ? value : JSON.stringify(value));
}

function uuid() {
  if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const state = {
  apiBase: lsGet('apiBase', 'http://localhost:8080'),
  theme: lsGet('theme', 'dark'),
  angleMode: lsGet('angleMode', 'DEGREES'),
  precision: lsGet('precision', 20),
  activeWorkspaceId: lsGet('activeWorkspaceId', null),
  clientId: (() => { const existing = lsGet('clientId', null); if (existing) return existing; const id = uuid(); lsSet('clientId', id); return id; })(),

  accessToken: lsGet('accessToken', null),
  refreshToken: lsGet('refreshToken', null),
  userEmail: lsGet('userEmail', null),

  online: true, // updated by api.js after each health check

  workspaces: [],
};

export function setApiBase(url) { state.apiBase = url; lsSet('apiBase', url); }
export function setTheme(theme) { state.theme = theme; lsSet('theme', theme); document.documentElement.setAttribute('data-theme', theme); }
export function setAngleMode(mode) { state.angleMode = mode; lsSet('angleMode', mode); }
export function setPrecision(p) { state.precision = p; lsSet('precision', p); }
export function setActiveWorkspaceId(id) { state.activeWorkspaceId = id; lsSet('activeWorkspaceId', id); }

export function setAuth({ accessToken, refreshToken, email }) {
  state.accessToken = accessToken; lsSet('accessToken', accessToken || '');
  state.refreshToken = refreshToken; lsSet('refreshToken', refreshToken || '');
  state.userEmail = email; lsSet('userEmail', email || '');
}
export function clearAuth() {
  state.accessToken = null; state.refreshToken = null; state.userEmail = null;
  localStorage.removeItem(LS_PREFIX + 'accessToken');
  localStorage.removeItem(LS_PREFIX + 'refreshToken');
  localStorage.removeItem(LS_PREFIX + 'userEmail');
}

export function activeWorkspace() {
  return state.workspaces.find((w) => w.id === state.activeWorkspaceId) || state.workspaces[0] || null;
}
