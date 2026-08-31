import { state } from '../state.js';
import { LocalApi } from '../api.js';
import { el, toast, escapeHtml, confirmDialog, openFormModal } from '../utils.js';
import { setVariableCache } from './workspace.js';

async function loadVariables() {
  const tbody = document.getElementById('variables-tbody');
  const empty = document.getElementById('variables-empty');
  tbody.innerHTML = '';
  if (!state.activeWorkspaceId || !state.online) { empty.classList.remove('d-none'); return; }

  let vars;
  try {
    vars = await LocalApi.listVariables(state.activeWorkspaceId);
  } catch (err) {
    toast('Could not load variables: ' + err.message, 'error');
    return;
  }
  setVariableCache(vars);

  if (vars.length === 0) { empty.classList.remove('d-none'); return; }
  empty.classList.add('d-none');

  for (const v of vars) {
    const tr = el('tr', {}, [
      el('td', { class: 'font-mono' }, v.name),
      el('td', { class: 'font-mono' }, String(v.value)),
      el('td', {}, v.unit ? el('span', { class: 'cf-badge-unit' }, v.unit) : ''),
      el('td', { class: 'text-end' }, [
        el('button', { class: 'btn btn-sm btn-cf-ghost py-0 px-2 me-1', onclick: () => editVariable(v) }, 'Edit'),
        el('button', { class: 'btn btn-sm btn-cf-ghost py-0 px-2', onclick: () => deleteVariable(v) }, 'Del'),
      ]),
    ]);
    tbody.appendChild(tr);
  }
}

async function addVariable() {
  if (!state.activeWorkspaceId) { toast('Select a workspace first.', 'warning'); return; }
  await variableForm(null);
}

async function editVariable(v) {
  await variableForm(v);
}

async function variableForm(existing) {
  const values = await openFormModal({
    title: existing ? `Edit ${existing.name}` : 'New variable',
    bodyHtml: `
      <div class="mb-2"><label class="form-label small">Name</label><input class="form-control form-control-sm font-mono" data-field="name" value="${existing ? escapeHtml(existing.name) : ''}" ${existing ? 'readonly' : ''}></div>
      <div class="mb-2"><label class="form-label small">Value</label><input class="form-control form-control-sm font-mono" data-field="value" value="${existing ? existing.value : ''}"></div>
      <div class="mb-2"><label class="form-label small">Unit (optional)</label><input class="form-control form-control-sm" data-field="unit" value="${existing ? escapeHtml(existing.unit || '') : ''}"></div>
      <div><label class="form-label small">Description (optional)</label><input class="form-control form-control-sm" data-field="description" value="${existing ? escapeHtml(existing.description || '') : ''}"></div>`,
    confirmLabel: 'Save',
  });
  if (!values || !values.name || values.value === '') return;

  const payload = { name: values.name, value: Number(values.value), unit: values.unit || null, description: values.description || null };
  try {
    if (existing) await LocalApi.updateVariable(state.activeWorkspaceId, existing.id, payload);
    else await LocalApi.createVariable(state.activeWorkspaceId, payload);
    loadVariables();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function deleteVariable(v) {
  if (!(await confirmDialog(`Delete variable "${v.name}"?`))) return;
  try {
    await LocalApi.deleteVariable(state.activeWorkspaceId, v.id);
    loadVariables();
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ---------------------------------------------------------------- formulas

async function loadFormulas() {
  const list = document.getElementById('formulas-list');
  const empty = document.getElementById('formulas-empty');
  list.innerHTML = '';
  if (!state.activeWorkspaceId || !state.online) { empty.classList.remove('d-none'); return; }

  let formulas;
  try {
    formulas = await LocalApi.listFormulas(state.activeWorkspaceId);
  } catch (err) {
    toast('Could not load formulas: ' + err.message, 'error');
    return;
  }

  if (formulas.length === 0) { empty.classList.remove('d-none'); return; }
  empty.classList.add('d-none');

  for (const f of formulas) {
    const card = el('div', { class: 'cf-card py-2 px-3' }, [
      el('div', { class: 'd-flex justify-content-between align-items-start' }, [
        el('div', {}, [
          el('div', { style: 'font-weight:600; font-size:.85rem;' }, f.name),
          el('div', { class: 'font-mono cf-muted', style: 'font-size:.78rem;' }, f.expression),
          f.parameters.length ? el('div', { class: 'cf-faint', style: 'font-size:.7rem;' }, 'needs: ' + f.parameters.join(', ')) : '',
        ]),
        el('div', { class: 'd-flex gap-1' }, [
          el('button', { class: 'btn btn-sm btn-cf-ghost py-0 px-2', onclick: () => evaluateFormula(f) }, 'Run'),
          el('button', { class: 'btn btn-sm btn-cf-ghost py-0 px-2', onclick: () => deleteFormula(f) }, 'Del'),
        ]),
      ]),
    ]);
    list.appendChild(card);
  }
}

async function addFormula() {
  if (!state.activeWorkspaceId) { toast('Select a workspace first.', 'warning'); return; }
  const values = await openFormModal({
    title: 'New formula',
    bodyHtml: `
      <div class="mb-2"><label class="form-label small">Name</label><input class="form-control form-control-sm font-mono" data-field="name" placeholder="e.g. monthly_payment"></div>
      <div class="mb-2"><label class="form-label small">Expression</label><input class="form-control form-control-sm font-mono" data-field="expression" placeholder="e.g. principal * rate / (1 - (1+rate)^-months)"></div>
      <div><label class="form-label small">Description (optional)</label><input class="form-control form-control-sm" data-field="description"></div>`,
    confirmLabel: 'Save',
  });
  if (!values || !values.name || !values.expression) return;
  try {
    await LocalApi.createFormula(state.activeWorkspaceId, { name: values.name, expression: values.expression, description: values.description || null });
    loadFormulas();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function deleteFormula(f) {
  if (!(await confirmDialog(`Delete formula "${f.name}"?`))) return;
  try {
    await LocalApi.deleteFormula(state.activeWorkspaceId, f.id);
    loadFormulas();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function evaluateFormula(f) {
  let args = {};
  if (f.parameters.length > 0) {
    const fields = f.parameters.map((p) => `<div class="mb-2"><label class="form-label small font-mono">${escapeHtml(p)}</label><input class="form-control form-control-sm font-mono" data-field="${escapeHtml(p)}"></div>`).join('');
    const values = await openFormModal({ title: `Evaluate ${f.name}`, bodyHtml: fields, confirmLabel: 'Evaluate' });
    if (!values) return;
    for (const p of f.parameters) args[p] = Number(values[p]);
  }
  try {
    const res = await LocalApi.evaluateFormula(state.activeWorkspaceId, f.id, { arguments: args, angleMode: state.angleMode, precision: state.precision });
    toast(`${f.name} = ${res.resultDisplay}`, 'success', 6000);
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ---------------------------------------------------------------- scenarios

async function loadScenarios() {
  const list = document.getElementById('scenarios-list');
  list.innerHTML = '';
  if (!state.activeWorkspaceId || !state.online) return;
  let scenarios;
  try {
    scenarios = await LocalApi.listScenarios(state.activeWorkspaceId);
  } catch (err) {
    return;
  }
  for (const s of scenarios) {
    const varsText = Object.entries(s.variables).map(([k, v]) => `${k}=${v}`).join(', ');
    list.appendChild(el('div', { class: 'cf-card py-2 px-3 d-flex justify-content-between align-items-center' }, [
      el('div', {}, [
        el('div', { style: 'font-weight:600; font-size:.85rem;' }, s.name),
        el('div', { class: 'cf-faint font-mono', style: 'font-size:.72rem;' }, varsText),
      ]),
      el('button', { class: 'btn btn-sm btn-cf-ghost py-0 px-2', onclick: () => deleteScenario(s) }, 'Del'),
    ]));
  }
}

async function addScenario() {
  if (!state.activeWorkspaceId) { toast('Select a workspace first.', 'warning'); return; }
  const values = await openFormModal({
    title: 'New scenario',
    bodyHtml: `
      <div class="mb-2"><label class="form-label small">Name</label><input class="form-control form-control-sm" data-field="name" placeholder="e.g. If rates rise to 6%"></div>
      <div><label class="form-label small">Variable overrides (name=value, comma separated)</label><input class="form-control form-control-sm font-mono" data-field="vars" placeholder="rate=6"></div>`,
    confirmLabel: 'Save',
  });
  if (!values || !values.name) return;
  const variables = {};
  (values.vars || '').split(',').forEach((pair) => {
    const [k, v] = pair.split('=').map((s) => s && s.trim());
    if (k && v !== undefined && v !== '') variables[k] = Number(v);
  });
  try {
    await LocalApi.createScenario(state.activeWorkspaceId, { name: values.name, variables });
    loadScenarios();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function deleteScenario(s) {
  if (!(await confirmDialog(`Delete scenario "${s.name}"?`))) return;
  try {
    await LocalApi.deleteScenario(state.activeWorkspaceId, s.id);
    loadScenarios();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function runScenarios() {
  const expr = document.getElementById('scenario-run-expr').value.trim();
  const resultsEl = document.getElementById('scenario-results');
  if (!expr) { toast('Enter an expression to compare.', 'warning'); return; }
  if (!state.activeWorkspaceId) return;

  let scenarios;
  try {
    scenarios = await LocalApi.listScenarios(state.activeWorkspaceId);
  } catch (err) {
    toast(err.message, 'error');
    return;
  }

  try {
    const results = await LocalApi.runScenarios(state.activeWorkspaceId, {
      expression: expr,
      scenarioIds: scenarios.map((s) => s.id),
      angleMode: state.angleMode,
      precision: state.precision,
    });
    resultsEl.innerHTML = '';
    for (const r of results) {
      resultsEl.appendChild(el('div', { class: 'cf-card py-2 px-3 mb-2' }, [
        el('div', { class: 'd-flex justify-content-between' }, [
          el('span', { style: 'font-size:.82rem;' }, r.scenarioName),
          el('span', { class: 'font-mono', style: 'font-weight:700; color: var(--cf-accent-strong);' }, r.resultDisplay),
        ]),
      ]));
    }
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ---------------------------------------------------------------- init

export async function refreshVariablesView() {
  await Promise.all([loadVariables(), loadFormulas(), loadScenarios()]);
}

export function initVariablesView() {
  document.getElementById('add-variable-btn').addEventListener('click', addVariable);
  document.getElementById('add-formula-btn').addEventListener('click', addFormula);
  document.getElementById('add-scenario-btn').addEventListener('click', addScenario);
  document.getElementById('scenario-run-btn').addEventListener('click', runScenarios);

  window.addEventListener('cf:workspace-changed', refreshVariablesView);
}
