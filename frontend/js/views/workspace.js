import { state, setActiveWorkspaceId } from '../state.js';
import { LocalApi } from '../api.js';
import { el, toast, renderTrail, confirmDialog, openFormModal, escapeHtml } from '../utils.js';
import { cacheGet, cacheSet } from '../storage/db.js';

let variableCache = {}; // { [lowercaseName]: numberValue } for the active workspace, used by calculator/graph

export function activeWorkspaceVariables() {
  return { ...variableCache };
}

export function setVariableCache(variables) {
  variableCache = {};
  for (const v of variables) variableCache[v.name.toLowerCase()] = Number(v.value);
}

async function loadVariablesForActiveWorkspace() {
  if (!state.activeWorkspaceId || !state.online) { variableCache = {}; return; }
  try {
    const vars = await LocalApi.listVariables(state.activeWorkspaceId);
    setVariableCache(vars);
  } catch {
    variableCache = {};
  }
}

export async function loadWorkspaces() {
  const selectEl = document.getElementById('active-workspace-select');
  if (!state.online) {
    const cached = await cacheGet('workspaces');
    state.workspaces = cached || [];
  } else {
    try {
      state.workspaces = await LocalApi.listWorkspaces();
      cacheSet('workspaces', state.workspaces);
      if (state.workspaces.length === 0) {
        const created = await LocalApi.createWorkspace({ name: 'My Workspace', description: '' });
        state.workspaces = [created];
      }
    } catch (err) {
      toast('Could not load workspaces: ' + err.message, 'error');
      const cached = await cacheGet('workspaces');
      state.workspaces = cached || [];
    }
  }

  if (!state.activeWorkspaceId || !state.workspaces.some((w) => w.id === state.activeWorkspaceId)) {
    if (state.workspaces[0]) setActiveWorkspaceId(state.workspaces[0].id);
  }

  selectEl.innerHTML = '';
  for (const w of state.workspaces) {
    selectEl.appendChild(el('option', { value: w.id }, w.name));
  }
  selectEl.value = state.activeWorkspaceId || '';

  await loadVariablesForActiveWorkspace();
  renderWorkspaceHeader();
  await refreshWorkspaceCanvas();
  window.dispatchEvent(new CustomEvent('cf:workspace-changed'));
}

function renderWorkspaceHeader() {
  const ws = state.workspaces.find((w) => w.id === state.activeWorkspaceId);
  document.getElementById('workspace-name-heading').textContent = ws ? ws.name : 'No workspace';
  document.getElementById('workspace-desc-text').textContent = ws ? (ws.description || '') : 'Create a workspace to get started.';
}

export async function refreshWorkspaceCanvas() {
  const grid = document.getElementById('canvas-grid');
  const empty = document.getElementById('canvas-empty');
  grid.innerHTML = '';

  if (!state.activeWorkspaceId) { empty.classList.remove('d-none'); return; }
  if (!state.online) {
    grid.innerHTML = '<p class="cf-faint">Workspace canvas requires the backend to be reachable.</p>';
    empty.classList.add('d-none');
    return;
  }

  let cards;
  try {
    cards = await LocalApi.listCards(state.activeWorkspaceId);
  } catch (err) {
    grid.innerHTML = `<p class="cf-faint">Could not load canvas: ${escapeHtml(err.message)}</p>`;
    return;
  }

  if (cards.length === 0) { empty.classList.remove('d-none'); return; }
  empty.classList.add('d-none');

  for (const card of cards) {
    const cardEl = el('div', { class: 'cf-calc-card' }, [
      el('div', { class: 'd-flex justify-content-between align-items-start' }, [
        el('strong', { style: 'font-size:.85rem;' }, card.label || 'Untitled'),
        el('div', { class: 'd-flex gap-1' }, [
          iconButton('trail', () => toggleCardTrail(cardEl, card)),
          iconButton('delete', () => deleteCard(card.id)),
        ]),
      ]),
      el('div', { class: 'cf-card-expr' }, card.expression),
      el('div', { class: 'cf-card-result' }, card.resultDisplay || ''),
      el('div', { class: 'cf-ledger d-none', 'data-role': 'card-trail' }),
    ]);
    grid.appendChild(cardEl);
  }
}

function iconButton(kind, onClick) {
  const label = kind === 'trail' ? '\u2261' : '\u2715';
  const btn = el('button', {
    class: 'btn btn-sm btn-cf-ghost py-0 px-2',
    style: 'font-size:.75rem; line-height:1.6;',
    title: kind === 'trail' ? 'Show trail' : 'Delete card',
    onclick: onClick,
  }, label);
  return btn;
}

function toggleCardTrail(cardEl, card) {
  const trailEl = cardEl.querySelector('[data-role="card-trail"]');
  trailEl.classList.toggle('d-none');
  if (!trailEl.classList.contains('d-none')) renderTrail(trailEl, card.trail);
}

async function deleteCard(cardId) {
  if (!(await confirmDialog('Delete this calculation card?'))) return;
  try {
    await LocalApi.deleteCard(state.activeWorkspaceId, cardId);
    refreshWorkspaceCanvas();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function newWorkspace() {
  const values = await openFormModal({
    title: 'New workspace',
    bodyHtml: `
      <div class="mb-2"><label class="form-label small">Name</label><input class="form-control form-control-sm" data-field="name" placeholder="e.g. Kitchen remodel"></div>
      <div><label class="form-label small">Description (optional)</label><input class="form-control form-control-sm" data-field="description"></div>`,
    confirmLabel: 'Create',
  });
  if (!values || !values.name) return;
  try {
    const created = await LocalApi.createWorkspace({ name: values.name, description: values.description || '' });
    setActiveWorkspaceId(created.id);
    await loadWorkspaces();
    toast('Workspace created.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function renameWorkspace() {
  const ws = state.workspaces.find((w) => w.id === state.activeWorkspaceId);
  if (!ws) return;
  const values = await openFormModal({
    title: 'Rename workspace',
    bodyHtml: `
      <div class="mb-2"><label class="form-label small">Name</label><input class="form-control form-control-sm" data-field="name" value="${escapeHtml(ws.name)}"></div>
      <div><label class="form-label small">Description</label><input class="form-control form-control-sm" data-field="description" value="${escapeHtml(ws.description || '')}"></div>`,
    confirmLabel: 'Save',
  });
  if (!values || !values.name) return;
  try {
    await LocalApi.updateWorkspace(ws.id, { name: values.name, description: values.description || '' });
    await loadWorkspaces();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function deleteWorkspaceHandler() {
  const ws = state.workspaces.find((w) => w.id === state.activeWorkspaceId);
  if (!ws) return;
  if (!(await confirmDialog(`Delete workspace "${ws.name}" and everything in it? This can't be undone from the UI.`))) return;
  try {
    await LocalApi.deleteWorkspace(ws.id);
    setActiveWorkspaceId(null);
    await loadWorkspaces();
    toast('Workspace deleted.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  }
}

export function initWorkspaceView() {
  document.getElementById('active-workspace-select').addEventListener('change', async (e) => {
    setActiveWorkspaceId(Number(e.target.value));
    await loadVariablesForActiveWorkspace();
    renderWorkspaceHeader();
    await refreshWorkspaceCanvas();
    window.dispatchEvent(new CustomEvent('cf:workspace-changed'));
  });

  document.getElementById('new-workspace-btn').addEventListener('click', newWorkspace);
  document.getElementById('rename-workspace-btn').addEventListener('click', renameWorkspace);
  document.getElementById('delete-workspace-btn').addEventListener('click', deleteWorkspaceHandler);

  document.getElementById('quick-card-add').addEventListener('click', async () => {
    const input = document.getElementById('quick-card-input');
    if (!input.value.trim() || !state.activeWorkspaceId) return;
    try {
      await LocalApi.createCard(state.activeWorkspaceId, {
        expression: input.value.trim(),
        angleMode: state.angleMode,
        precision: state.precision,
      });
      input.value = '';
      refreshWorkspaceCanvas();
    } catch (err) {
      toast(err.message, 'error');
    }
  });
}
