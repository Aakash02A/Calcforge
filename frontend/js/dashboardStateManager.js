import { state } from './state.js';
import { evaluateOffline, formatNumber } from './engine/localEngine.js';
import { renderTrail } from './utils.js';
import { setVariableCache } from './views/workspace.js';
import { refreshGraphView } from './views/graph.js?v=2.3';

export class DashboardStateManager {
  constructor() {
    this.state = new Map();
    this.bindings = new Map();
    this.cards = new Map();
    this.dependencies = new Map();
    this.listeners = new Set();
    this.pendingVariables = new Set();
    this.rafPending = false;
    this.isSweeping = false;
  }

  registerBinding(variableName, HTMLComponentId, changeCallback) {
    const key = String(variableName).trim();
    const element = typeof HTMLComponentId === 'string' ? document.getElementById(HTMLComponentId) : HTMLComponentId;

    if (!this.bindings.has(key)) {
      this.bindings.set(key, new Set());
    }

    const handler = (event) => {
      let val;
      if (element.type === 'checkbox') {
        val = element.checked ? 1 : 0;
      } else if (element.type === 'number' || element.type === 'range') {
        val = element.value === '' ? 0 : Number(element.value);
      } else if ('value' in element) {
        const num = Number(element.value);
        val = (!isNaN(num) && element.value.trim() !== '') ? num : element.value;
      } else {
        val = element.textContent;
      }

      this.setVariable(key, val, HTMLComponentId, event.type);
      if (typeof changeCallback === 'function') {
        changeCallback(val, key, element, event);
      }
    };

    if (element) {
      element.addEventListener('input', handler);
      element.addEventListener('change', handler);

      if (this.state.has(key)) {
        this.updateElementValue(element, this.state.get(key));
      } else {
        let initialVal;
        if (element.type === 'checkbox') {
          initialVal = element.checked ? 1 : 0;
        } else if (element.type === 'number' || element.type === 'range') {
          initialVal = element.value === '' ? 0 : Number(element.value);
        } else if ('value' in element) {
          const num = Number(element.value);
          initialVal = (!isNaN(num) && element.value.trim() !== '') ? num : element.value;
        } else {
          initialVal = element.textContent;
        }
        this.state.set(key, initialVal);
      }
    }

    const bindingRecord = {
      id: HTMLComponentId,
      element,
      callback: changeCallback,
      handler
    };

    this.bindings.get(key).add(bindingRecord);

    return () => this.unregisterBinding(key, HTMLComponentId);
  }

  unregisterBinding(variableName, HTMLComponentId) {
    const key = String(variableName).trim();
    if (!this.bindings.has(key)) return;

    const set = this.bindings.get(key);
    for (const record of set) {
      if (record.id === HTMLComponentId || record.element === HTMLComponentId) {
        if (record.element) {
          record.element.removeEventListener('input', record.handler);
          record.element.removeEventListener('change', record.handler);
        }
        set.delete(record);
      }
    }

    if (set.size === 0) {
      this.bindings.delete(key);
    }
  }

  registerCard(cardId, dependencies = [], expressionOrFn = null, outputComponentId = null) {
    let deps = [];
    if (Array.isArray(dependencies)) {
      deps = dependencies.map((d) => String(d).trim());
    } else if (typeof dependencies === 'string' && dependencies.trim()) {
      deps = [dependencies.trim()];
    }

    const outputElement = typeof outputComponentId === 'string' ? document.getElementById(outputComponentId) : outputComponentId;

    const cardRecord = {
      id: cardId,
      dependencies: deps,
      evaluator: expressionOrFn,
      outputElement
    };

    this.cards.set(cardId, cardRecord);

    for (const dep of deps) {
      if (!this.dependencies.has(dep)) {
        this.dependencies.set(dep, new Set());
      }
      this.dependencies.get(dep).add(cardId);
    }

    this.evaluateCard(cardId);

    return () => this.unregisterCard(cardId);
  }

  unregisterCard(cardId) {
    if (!this.cards.has(cardId)) return;
    const card = this.cards.get(cardId);
    for (const dep of card.dependencies) {
      if (this.dependencies.has(dep)) {
        this.dependencies.get(dep).delete(cardId);
        if (this.dependencies.get(dep).size === 0) {
          this.dependencies.delete(dep);
        }
      }
    }
    this.cards.delete(cardId);
  }

  setVariable(variableName, value, sourceComponentId = null, eventType = 'input') {
    const key = String(variableName).trim();
    const prevValue = this.state.get(key);

    this.state.set(key, value);

    this.syncBindings(key, value, sourceComponentId);
    this.pendingVariables.add(key);

    this.scheduleSweep(eventType);
    this.dispatchStateEvent(key, value, prevValue, eventType);

    return value;
  }

  scheduleSweep(eventType = 'input') {
    if (this.rafPending) return;
    this.rafPending = true;

    requestAnimationFrame(() => {
      this.rafPending = false;
      this.executeRuntimeSweep();
    });
  }

  executeRuntimeSweep() {
    if (this.isSweeping) return;
    this.isSweeping = true;

    try {
      const scope = this.getAllVariables();
      const varsList = Object.entries(scope).map(([name, value]) => ({ name, value }));
      setVariableCache(varsList);

      const queue = Array.from(this.pendingVariables);
      this.pendingVariables.clear();

      const visitedVars = new Set(queue);
      const evaluatedCards = new Set();

      while (queue.length > 0) {
        const currentVar = queue.shift();
        const dependentCards = this.dependencies.get(currentVar);

        if (!dependentCards) continue;

        for (const cardId of dependentCards) {
          if (evaluatedCards.has(cardId)) continue;
          evaluatedCards.add(cardId);

          const result = this.evaluateCard(cardId);

          if (result !== undefined && typeof cardId === 'string' && (this.bindings.has(cardId) || this.dependencies.has(cardId))) {
            this.state.set(cardId, result);
            this.syncBindings(cardId, result, null);
            if (!visitedVars.has(cardId)) {
              visitedVars.add(cardId);
              queue.push(cardId);
            }
          }
        }
      }

      this.refreshWorkspaceCanvasCards(scope);
      this.refreshCalculatorLedger(scope);
      this.refreshOpenCharts();

      for (const listener of this.listeners) {
        try {
          listener(scope);
        } catch {}
      }

      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('cf:variables-updated', { detail: scope }));
      }
    } finally {
      this.isSweeping = false;
    }
  }

  refreshWorkspaceCanvasCards(scope) {
    const cardElements = document.querySelectorAll('#canvas-grid .cf-calc-card');
    if (!cardElements || cardElements.length === 0) return;

    cardElements.forEach((cardEl) => {
      const exprEl = cardEl.querySelector('.cf-card-expr');
      const resEl = cardEl.querySelector('.cf-card-result');
      const trailEl = cardEl.querySelector('[data-role="card-trail"]');

      if (!exprEl || !resEl) return;
      const expr = exprEl.textContent.trim();
      if (!expr) return;

      try {
        const evalRes = evaluateOffline(expr, {
          variables: scope,
          angleMode: state.angleMode,
          precision: state.precision
        });

        resEl.textContent = formatNumber(evalRes.result);
        resEl.classList.remove('cf-result-error');

        if (trailEl && !trailEl.classList.contains('d-none')) {
          renderTrail(trailEl, evalRes.trail);
        }
      } catch (err) {
        resEl.textContent = err.message || 'Error';
        resEl.classList.add('cf-result-error');
      }
    });
  }

  refreshCalculatorLedger(scope) {
    const exprInput = document.getElementById('expr-input');
    const resultDisplay = document.getElementById('result-display');
    const trailLedger = document.getElementById('trail-ledger');

    if (!exprInput || !resultDisplay) return;
    const expr = exprInput.value.trim();
    if (!expr) return;

    try {
      const evalRes = evaluateOffline(expr, {
        variables: scope,
        angleMode: state.angleMode,
        precision: state.precision
      });

      resultDisplay.classList.remove('cf-result-error');
      resultDisplay.textContent = formatNumber(evalRes.result);

      if (trailLedger) {
        renderTrail(trailLedger, evalRes.trail);
      }
    } catch {}
  }

  refreshOpenCharts() {
    const graphCanvas = document.getElementById('graph-canvas');
    if (graphCanvas && typeof refreshGraphView === 'function') {
      refreshGraphView();
    }
  }

  getVariable(variableName) {
    const key = String(variableName).trim();
    return this.state.get(key);
  }

  getAllVariables() {
    const result = {};
    for (const [k, v] of this.state.entries()) {
      result[k] = v;
    }
    return result;
  }

  syncBindings(variableName, value, sourceComponentId = null) {
    const key = String(variableName).trim();
    if (!this.bindings.has(key)) return;

    for (const record of this.bindings.get(key)) {
      if (record.id === sourceComponentId) continue;
      if (record.element) {
        this.updateElementValue(record.element, value);
      }
      if (typeof record.callback === 'function') {
        record.callback(value, key, record.element);
      }
    }
  }

  updateElementValue(element, value) {
    if (!element) return;
    if (element.type === 'checkbox') {
      element.checked = Boolean(Number(value));
    } else if ('value' in element) {
      if (document.activeElement !== element || element.type === 'range') {
        element.value = value !== undefined && value !== null ? value : '';
      }
    } else {
      element.textContent = value !== undefined && value !== null ? String(value) : '';
    }
  }

  evaluateCard(cardId) {
    if (!this.cards.has(cardId)) return;
    const card = this.cards.get(cardId);
    const scope = this.getAllVariables();

    let result;
    if (typeof card.evaluator === 'function') {
      try {
        result = card.evaluator(scope);
      } catch {
        result = NaN;
      }
    } else if (typeof card.evaluator === 'string') {
      try {
        const evalRes = evaluateOffline(card.evaluator, {
          variables: scope,
          angleMode: state.angleMode,
          precision: state.precision
        });
        result = evalRes.result;
      } catch {
        result = NaN;
      }
    }

    if (card.outputElement && result !== undefined) {
      this.updateElementValue(card.outputElement, result);
    }

    const cardElement = typeof card.id === 'string' ? document.getElementById(card.id) : null;
    if (cardElement) {
      const resultContainer = cardElement.querySelector('.cf-card-result') || cardElement.querySelector('[data-role="result"]');
      if (resultContainer && result !== undefined) {
        resultContainer.textContent = formatNumber(result);
      }
    }

    return result;
  }

  evaluateAll() {
    for (const cardId of this.cards.keys()) {
      this.evaluateCard(cardId);
    }
  }

  dispatchStateEvent(variableName, value, prevValue, eventType = 'input') {
    if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
      const event = new CustomEvent('dashboard:state-change', {
        detail: {
          variable: variableName,
          value,
          prevValue,
          eventType,
          state: this.getAllVariables()
        }
      });
      window.dispatchEvent(event);
    }
  }

  subscribe(listener) {
    if (typeof listener === 'function') {
      this.listeners.add(listener);
      return () => this.listeners.delete(listener);
    }
    return () => {};
  }

  reset() {
    this.state.clear();
    for (const [, set] of this.bindings.entries()) {
      for (const record of set) {
        if (record.element) {
          record.element.removeEventListener('input', record.handler);
          record.element.removeEventListener('change', record.handler);
        }
      }
    }
    this.bindings.clear();
    this.cards.clear();
    this.dependencies.clear();
    this.listeners.clear();
    this.pendingVariables.clear();
  }
}

export const dashboardStateManager = new DashboardStateManager();
export default DashboardStateManager;
