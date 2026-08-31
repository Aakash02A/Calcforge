import { state } from '../state.js';
import { LocalApi } from '../api.js';
import { evaluateOffline, EngineError } from '../engine/localEngine.js';
import { debounce, renderTrail, toast, escapeHtml } from '../utils.js';
import { queueOfflineCalculation } from '../storage/db.js';
import { activeWorkspaceVariables, refreshWorkspaceCanvas } from './workspace.js';

const exprInput = () => document.getElementById('expr-input');
const resultDisplay = () => document.getElementById('result-display');
const trailLedger = () => document.getElementById('trail-ledger');

let lastGoodResult = null; // { value, display } - for the "ans" keypad button

const KEYS = {
  basic: [
    ['C', 'DEL', '(', ')', '^'],
    ['7', '8', '9', '\u00F7', '%'],
    ['4', '5', '6', '\u00D7', 'sqrt('],
    ['1', '2', '3', '\u2212', '!'],
    ['0', '.', 'ans', '+', '='],
  ],
  scientific: [
    ['sin(', 'cos(', 'tan(', 'ln(', 'log('],
    ['asin(', 'acos(', 'atan(', 'exp(', 'sqrt('],
    ['pi', 'e', '^', '1/(', 'mod('],
    ['abs(', 'floor(', 'ceil(', 'round(', 'cbrt('],
  ],
  engineering: [
    ['pow(', 'root(', 'hypot(', 'gcd(', 'lcm('],
    ['ncr(', 'npr(', 'min(', 'max(', 'avg('],
    ['sum(', 'tau', 'phi', 'mod(', 'fact('],
  ],
};

function insertAtCursor(text) {
  const input = exprInput();
  const start = input.selectionStart ?? input.value.length;
  const end = input.selectionEnd ?? input.value.length;
  input.value = input.value.slice(0, start) + text + input.value.slice(end);
  const newPos = start + text.length;
  input.focus();
  input.setSelectionRange(newPos, newPos);
  scheduleEvaluate();
}

function handleKeyPress(label) {
  switch (label) {
    case 'C': exprInput().value = ''; renderEmpty(); exprInput().focus(); return;
    case 'DEL': {
      const input = exprInput();
      const start = input.selectionStart ?? input.value.length;
      if (start > 0) {
        input.value = input.value.slice(0, start - 1) + input.value.slice(start);
        input.setSelectionRange(start - 1, start - 1);
      }
      input.focus();
      scheduleEvaluate();
      return;
    }
    case '=': commitCalculation(); return;
    case 'ans': insertAtCursor(lastGoodResult ? lastGoodResult.value : '0'); return;
    case '\u00F7': insertAtCursor('/'); return;
    case '\u00D7': insertAtCursor('*'); return;
    case '\u2212': insertAtCursor('-'); return;
    case '1/(': insertAtCursor('1/('); return;
    default: insertAtCursor(label); return;
  }
}

function buildKeypad() {
  for (const [page, rows] of Object.entries(KEYS)) {
    const container = document.getElementById(`keypad-${page}`);
    container.innerHTML = '';
    for (const row of rows) {
      for (const label of row) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'cf-key';
        if (['+', '\u2212', '\u00D7', '\u00F7', '^', '%', '!'].includes(label)) btn.classList.add('cf-key-op');
        if (label === '=') btn.classList.add('cf-key-accent');
        btn.textContent = label;
        btn.addEventListener('click', () => handleKeyPress(label));
        container.appendChild(btn);
      }
    }
  }
}

function renderEmpty() {
  resultDisplay().textContent = '\u00a0';
  resultDisplay().classList.remove('cf-result-error');
  trailLedger().innerHTML = '<p class="cf-ledger-empty">Enter an expression to see the input, assumptions, formula, computation and result laid out step by step.</p>';
}

function renderError(message) {
  resultDisplay().textContent = message;
  resultDisplay().classList.add('cf-result-error');
  trailLedger().innerHTML = `<p class="cf-ledger-empty">${escapeHtml(message)}</p>`;
}

function renderResult(displayValue, trail) {
  resultDisplay().classList.remove('cf-result-error');
  resultDisplay().textContent = displayValue;
  renderTrail(trailLedger(), trail);
}

async function evaluateNow(expression, { save }) {
  if (!expression || !expression.trim()) { renderEmpty(); return; }

  const payload = {
    expression,
    variables: activeWorkspaceVariables(),
    workspaceId: state.activeWorkspaceId || undefined,
    angleMode: state.angleMode,
    precision: state.precision,
    saveToHistory: save,
  };

  if (state.online) {
    try {
      const res = await LocalApi.calculate(payload);
      lastGoodResult = { value: res.resultPlain, display: res.resultDisplay };
      renderResult(res.resultDisplay, res.trail);
      return;
    } catch (err) {
      if (err.status === 0) {
        state.online = false; // fall through to offline engine below
      } else {
        renderError(err.message);
        return;
      }
    }
  }

  // Offline fallback: client-side engine, reduced precision, smaller function set.
  try {
    const { result, trail } = evaluateOffline(expression, {
      variables: activeWorkspaceVariables(),
      angleMode: state.angleMode,
    });
    const display = trail.steps[trail.steps.length - 1].value;
    lastGoodResult = { value: String(result), display };
    renderResult(display, trail);
    if (save) {
      await queueOfflineCalculation({ expression, result: display, trailJson: JSON.stringify(trail) });
    }
  } catch (err) {
    if (err instanceof EngineError) renderError(err.message);
    else renderError('Could not evaluate that expression.');
  }
}

const scheduleEvaluate = debounce(() => evaluateNow(exprInput().value, { save: false }), 350);

function commitCalculation() {
  const expr = exprInput().value;
  if (!expr.trim()) return;
  evaluateNow(expr, { save: true });
}

async function addToCanvas() {
  const expr = exprInput().value.trim();
  if (!expr) { toast('Type an expression first.', 'warning'); return; }
  if (!state.activeWorkspaceId) { toast('Create or select a workspace first (Canvas tab).', 'warning'); return; }
  if (!state.online) { toast('Adding to a workspace canvas requires the backend to be reachable.', 'warning'); return; }
  try {
    await LocalApi.createCard(state.activeWorkspaceId, {
      expression: expr,
      variables: activeWorkspaceVariables(),
      angleMode: state.angleMode,
      precision: state.precision,
    });
    toast('Added to canvas.', 'success');
    refreshWorkspaceCanvas();
  } catch (err) {
    toast(err.message, 'error');
  }
}

export function updateAssumptionChips() {
  document.getElementById('chip-angle').textContent = state.angleMode.slice(0, 3);
  document.getElementById('chip-precision').textContent = `${state.precision} sig figs`;
}

export function setExpression(expr) {
  exprInput().value = expr;
  document.querySelector('[data-view="calculator"]').click();
  evaluateNow(expr, { save: false });
}

export function initCalculatorView() {
  buildKeypad();
  updateAssumptionChips();

  document.querySelectorAll('.cf-keypad-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.cf-keypad-tab').forEach((t) => t.classList.remove('active'));
      document.querySelectorAll('.cf-keypad-page').forEach((p) => p.classList.remove('active'));
      tab.classList.add('active');
      document.getElementById(`keypad-${tab.dataset.page}`).classList.add('active');
    });
  });

  exprInput().addEventListener('input', scheduleEvaluate);
  exprInput().addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); commitCalculation(); }
  });

  document.getElementById('save-to-workspace-btn').addEventListener('click', addToCanvas);
}
