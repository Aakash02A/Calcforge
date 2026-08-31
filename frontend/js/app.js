import { state, setTheme } from './state.js';
import { checkHealth } from './api.js';
import { initCalculatorView } from './views/calculator.js';
import { initWorkspaceView, loadWorkspaces, refreshWorkspaceCanvas } from './views/workspace.js';
import { initVariablesView, refreshVariablesView } from './views/variables.js';
import { initHistoryView, refreshHistoryView } from './views/history.js';
import { initGraphView } from './views/graph.js';
import { initSettingsView } from './views/settings.js';

const VIEW_TITLES = {
  calculator: 'Calculator',
  workspace: 'Workspace canvas',
  variables: 'Variables & Formulas',
  history: 'History',
  graph: 'Graph',
  settings: 'Settings',
};

function switchView(name) {
  document.querySelectorAll('.cf-nav-btn').forEach((b) => b.classList.toggle('active', b.dataset.view === name));
  document.querySelectorAll('.cf-view').forEach((v) => v.classList.toggle('active', v.id === `view-${name}`));
  document.getElementById('view-title').textContent = VIEW_TITLES[name] || name;

  if (name === 'workspace') refreshWorkspaceCanvas();
  if (name === 'variables') refreshVariablesView();
  if (name === 'history') refreshHistoryView();
}

function initNav() {
  document.querySelectorAll('.cf-nav-btn').forEach((btn) => {
    btn.addEventListener('click', () => switchView(btn.dataset.view));
  });
}

function initThemeToggle() {
  document.documentElement.setAttribute('data-theme', state.theme);
  document.getElementById('theme-toggle').addEventListener('click', () => {
    setTheme(state.theme === 'dark' ? 'light' : 'dark');
  });
}

function updateStatusPill() {
  const pill = document.getElementById('status-pill');
  const text = document.getElementById('status-text');
  pill.classList.toggle('offline', !state.online);
  text.textContent = state.online ? 'Online' : 'Offline mode';
}

async function pollHealth() {
  await checkHealth();
  updateStatusPill();
}

async function main() {
  document.documentElement.setAttribute('data-theme', state.theme);
  initThemeToggle();
  initNav();
  initCalculatorView();
  initWorkspaceView();
  initVariablesView();
  initHistoryView();
  initGraphView();
  initSettingsView();

  await pollHealth();
  await loadWorkspaces();
  updateStatusPill();

  setInterval(pollHealth, 15000);
}

document.addEventListener('DOMContentLoaded', main);
