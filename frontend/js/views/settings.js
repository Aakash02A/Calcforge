import { state, setAngleMode, setPrecision, setApiBase, setTheme, setAuth, clearAuth } from '../state.js';
import { CloudApi, checkHealth } from '../api.js';
import { toast } from '../utils.js';
import { updateAssumptionChips } from './calculator.js';

function applyThemeButtons() {
  document.getElementById('theme-dark-btn').classList.toggle('btn-cf-accent', state.theme === 'dark');
  document.getElementById('theme-light-btn').classList.toggle('btn-cf-accent', state.theme === 'light');
}

async function refreshCloudFlags() {
  try {
    const flags = await CloudApi.featureFlags();
    const parts = [];
    if (flags.aiAssistEnabled) parts.push('AI assist');
    if (flags.liveCurrencyEnabled) parts.push('live currency');
    if (flags.sharedWorkspacesEnabled) parts.push('shared workspaces');
    document.getElementById('cloud-flags-text').textContent = parts.length
      ? `Enabled on this server: ${parts.join(', ')}.`
      : 'This server has no extra cloud features enabled beyond accounts and sync.';
  } catch {
    document.getElementById('cloud-flags-text').textContent = '';
  }
}

function renderAuthState() {
  const signedIn = !!state.accessToken;
  document.getElementById('cloud-signed-out').classList.toggle('d-none', signedIn);
  document.getElementById('cloud-signed-in').classList.toggle('d-none', !signedIn);
  if (signedIn) document.getElementById('cloud-user-email').textContent = state.userEmail || '';
}

async function login() {
  const email = document.getElementById('auth-email').value.trim();
  const password = document.getElementById('auth-password').value;
  if (!email || !password) { toast('Enter an email and password.', 'warning'); return; }
  try {
    const res = await CloudApi.login({ email, password });
    setAuth({ accessToken: res.accessToken, refreshToken: res.refreshToken, email: res.user.email });
    renderAuthState();
    toast('Signed in.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function register() {
  const email = document.getElementById('auth-email').value.trim();
  const password = document.getElementById('auth-password').value;
  if (!email || !password) { toast('Enter an email and password.', 'warning'); return; }
  try {
    const res = await CloudApi.register({ email, password });
    setAuth({ accessToken: res.accessToken, refreshToken: res.refreshToken, email: res.user.email });
    renderAuthState();
    toast('Account created and signed in.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function logout() {
  try { await CloudApi.logout(); } catch { /* best effort */ }
  clearAuth();
  renderAuthState();
  toast('Signed out.', 'info');
}

async function syncNow() {
  try {
    const result = await CloudApi.syncPull({ clientId: state.clientId, since: null });
    toast(`Synced: ${result.workspaces.length} workspace(s), ${result.variables.length} variable(s), ${result.historyEntries.length} history item(s).`, 'success');
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function exportBackup() {
  try {
    const data = await CloudApi.backupExport();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `calcforge-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (err) {
    toast(err.message, 'error');
  }
}

export function initSettingsView() {
  document.getElementById('setting-angle-mode').value = state.angleMode;
  document.getElementById('setting-precision').value = state.precision;
  document.getElementById('precision-label').textContent = state.precision;
  document.getElementById('setting-api-base').value = state.apiBase;
  applyThemeButtons();
  renderAuthState();
  refreshCloudFlags();

  document.getElementById('setting-angle-mode').addEventListener('change', (e) => {
    setAngleMode(e.target.value);
    updateAssumptionChips();
  });

  document.getElementById('setting-precision').addEventListener('input', (e) => {
    setPrecision(Number(e.target.value));
    document.getElementById('precision-label').textContent = e.target.value;
    updateAssumptionChips();
  });

  document.getElementById('theme-dark-btn').addEventListener('click', () => { setTheme('dark'); applyThemeButtons(); });
  document.getElementById('theme-light-btn').addEventListener('click', () => { setTheme('light'); applyThemeButtons(); });

  document.getElementById('save-api-base-btn').addEventListener('click', async () => {
    setApiBase(document.getElementById('setting-api-base').value.trim().replace(/\/$/, ''));
    const ok = await checkHealth();
    document.getElementById('settings-status-text').textContent = ok
      ? 'Connected.' : 'Could not reach that address - core features will fall back to offline mode.';
    toast(ok ? 'Reconnected.' : 'Still unreachable.', ok ? 'success' : 'warning');
  });

  document.getElementById('auth-login-btn').addEventListener('click', login);
  document.getElementById('auth-register-btn').addEventListener('click', register);
  document.getElementById('auth-logout-btn').addEventListener('click', logout);
  document.getElementById('sync-now-btn').addEventListener('click', syncNow);
  document.getElementById('backup-export-btn').addEventListener('click', exportBackup);
}
