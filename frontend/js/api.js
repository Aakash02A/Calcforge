import { state, setAuth, clearAuth } from './state.js';

export class ApiError extends Error {
  constructor(message, { status, errorCode, fieldErrors } = {}) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
    this.fieldErrors = fieldErrors || [];
  }
}

async function request(method, path, body, { auth = false, retry = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && state.accessToken) headers['Authorization'] = `Bearer ${state.accessToken}`;

  let response;
  try {
    response = await fetch(state.apiBase + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (networkError) {
    state.online = false;
    throw new ApiError('Could not reach the CalcForge server at ' + state.apiBase, { status: 0 });
  }

  if (response.status === 401 && auth && retry && state.refreshToken) {
    // Access token probably expired - try a silent refresh once, then retry the call.
    const refreshed = await tryRefresh();
    if (refreshed) return request(method, path, body, { auth, retry: false });
    clearAuth();
  }

  if (response.status === 204) {
    state.online = true;
    return null;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const message = (data && data.message) || `Request failed (HTTP ${response.status})`;
    throw new ApiError(message, {
      status: response.status,
      errorCode: data && data.errorCode,
      fieldErrors: data && data.fieldErrors,
    });
  }

  state.online = true;
  return data;
}

async function tryRefresh() {
  try {
    const data = await request('POST', '/api/v1/cloud/auth/refresh', { refreshToken: state.refreshToken }, { auth: false });
    setAuth({ accessToken: data.accessToken, refreshToken: data.refreshToken, email: data.user.email });
    return true;
  } catch {
    return false;
  }
}

export async function checkHealth() {
  try {
    const res = await fetch(state.apiBase + '/actuator/health', { method: 'GET' });
    state.online = res.ok;
    return res.ok;
  } catch {
    state.online = false;
    return false;
  }
}

// ---------------------------------------------------------------- local (unauthenticated)

export const LocalApi = {
  calculate: (payload) => request('POST', '/api/v1/local/calculate', payload),
  compile: (expression) => request('POST', '/api/v1/local/calculate/compile', { expression }),
  validate: (expression) => request('POST', '/api/v1/local/calculate/validate', { expression }),

  listWorkspaces: () => request('GET', '/api/v1/local/workspaces'),
  createWorkspace: (payload) => request('POST', '/api/v1/local/workspaces', payload),
  getWorkspace: (id) => request('GET', `/api/v1/local/workspaces/${id}`),
  updateWorkspace: (id, payload) => request('PUT', `/api/v1/local/workspaces/${id}`, payload),
  deleteWorkspace: (id) => request('DELETE', `/api/v1/local/workspaces/${id}`),

  listCards: (workspaceId) => request('GET', `/api/v1/local/workspaces/${workspaceId}/calculations`),
  createCard: (workspaceId, payload) => request('POST', `/api/v1/local/workspaces/${workspaceId}/calculations`, payload),
  updateCard: (workspaceId, cardId, payload) => request('PUT', `/api/v1/local/workspaces/${workspaceId}/calculations/${cardId}`, payload),
  deleteCard: (workspaceId, cardId) => request('DELETE', `/api/v1/local/workspaces/${workspaceId}/calculations/${cardId}`),
  reorderCards: (workspaceId, cardIds) => request('POST', `/api/v1/local/workspaces/${workspaceId}/calculations/reorder`, { cardIds }),

  listVariables: (workspaceId) => request('GET', `/api/v1/local/workspaces/${workspaceId}/variables`),
  createVariable: (workspaceId, payload) => request('POST', `/api/v1/local/workspaces/${workspaceId}/variables`, payload),
  updateVariable: (workspaceId, id, payload) => request('PUT', `/api/v1/local/workspaces/${workspaceId}/variables/${id}`, payload),
  deleteVariable: (workspaceId, id) => request('DELETE', `/api/v1/local/workspaces/${workspaceId}/variables/${id}`),

  listFormulas: (workspaceId) => request('GET', `/api/v1/local/workspaces/${workspaceId}/formulas`),
  createFormula: (workspaceId, payload) => request('POST', `/api/v1/local/workspaces/${workspaceId}/formulas`, payload),
  updateFormula: (workspaceId, id, payload) => request('PUT', `/api/v1/local/workspaces/${workspaceId}/formulas/${id}`, payload),
  deleteFormula: (workspaceId, id) => request('DELETE', `/api/v1/local/workspaces/${workspaceId}/formulas/${id}`),
  evaluateFormula: (workspaceId, id, payload) => request('POST', `/api/v1/local/workspaces/${workspaceId}/formulas/${id}/evaluate`, payload),

  listScenarios: (workspaceId) => request('GET', `/api/v1/local/workspaces/${workspaceId}/scenarios`),
  createScenario: (workspaceId, payload) => request('POST', `/api/v1/local/workspaces/${workspaceId}/scenarios`, payload),
  deleteScenario: (workspaceId, id) => request('DELETE', `/api/v1/local/workspaces/${workspaceId}/scenarios/${id}`),
  runScenarios: (workspaceId, payload) => request('POST', `/api/v1/local/workspaces/${workspaceId}/scenarios/run`, payload),

  searchHistory: (params) => {
    const qs = new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));
    return request('GET', `/api/v1/local/history?${qs.toString()}`);
  },
  updateHistory: (id, payload) => request('PATCH', `/api/v1/local/history/${id}`, payload),
  deleteHistory: (id) => request('DELETE', `/api/v1/local/history/${id}`),

  unitCategories: () => request('GET', '/api/v1/local/units/categories'),
  convertUnit: (payload) => request('POST', '/api/v1/local/units/convert', payload),

  loan: (payload) => request('POST', '/api/v1/local/finance/loan', payload),
  compoundInterest: (payload) => request('POST', '/api/v1/local/finance/compound-interest', payload),
  npv: (payload) => request('POST', '/api/v1/local/finance/npv', payload),
  tipSplit: (payload) => request('POST', '/api/v1/local/finance/tip-split', payload),

  graph: (payload) => request('POST', '/api/v1/local/graph', payload),
};

// ---------------------------------------------------------------- cloud (optional)

export const CloudApi = {
  featureFlags: () => request('GET', '/api/v1/cloud/feature-flags'),

  register: (payload) => request('POST', '/api/v1/cloud/auth/register', payload),
  login: (payload) => request('POST', '/api/v1/cloud/auth/login', payload),
  logout: () => request('POST', '/api/v1/cloud/auth/logout', { refreshToken: state.refreshToken }),

  syncPush: (payload) => request('POST', '/api/v1/cloud/sync/push', payload, { auth: true }),
  syncPull: (payload) => request('POST', '/api/v1/cloud/sync/pull', payload, { auth: true }),

  backupExport: () => request('GET', '/api/v1/cloud/backup/export', undefined, { auth: true }),
  backupRestore: (payload) => request('POST', '/api/v1/cloud/backup/restore', payload, { auth: true }),

  askAi: (payload) => request('POST', '/api/v1/cloud/ai/ask', payload, { auth: true }),
  currencyRates: () => request('GET', '/api/v1/cloud/currency/rates', undefined, { auth: true }),

  shareWorkspace: (id, shared) => request('POST', `/api/v1/cloud/workspaces/${id}/share`, { shared }, { auth: true }),
  viewSharedWorkspace: (id) => request('GET', `/api/v1/cloud/shared/workspaces/${id}`),
};
