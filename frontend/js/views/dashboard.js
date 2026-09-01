import { state } from '../state.js';
import { dashboardStateManager } from '../dashboardStateManager.js';
import { LocalApi } from '../api.js';
import { el, toast, escapeHtml, confirmDialog, openFormModal } from '../utils.js';
import { activeWorkspaceVariables } from './workspace.js';

const LS_DASHBOARD_KEY = 'calcforge:dashboard_layout';

let widgets = [];
let nextWidgetId = 1;

function loadDashboardState() {
  const wsId = state.activeWorkspaceId || 'default';
  const raw = localStorage.getItem(`${LS_DASHBOARD_KEY}:${wsId}`);
  if (raw) {
    try {
      widgets = JSON.parse(raw);
      const maxId = widgets.reduce((max, w) => Math.max(max, parseInt(String(w.id).replace(/\D/g, '') || '0', 10)), 0);
      nextWidgetId = maxId + 1;
    } catch {
      widgets = [];
      nextWidgetId = 1;
    }
  } else {
    widgets = [];
    nextWidgetId = 1;
  }
}

function saveDashboardState() {
  const wsId = state.activeWorkspaceId || 'default';
  localStorage.setItem(`${LS_DASHBOARD_KEY}:${wsId}`, JSON.stringify(widgets));
}

function getAvailableVariables() {
  const cached = activeWorkspaceVariables();
  const stateVars = dashboardStateManager.getAllVariables();
  const set = new Set([...Object.keys(cached), ...Object.keys(stateVars)]);
  return Array.from(set).sort();
}

export function createWidget(type, customConfig = {}) {
  const id = `widget-${Date.now()}-${nextWidgetId++}`;
  const defaults = {
    range: {
      label: 'Range Slider',
      variable: 'x',
      min: 0,
      max: 100,
      step: 1,
      value: 50,
    },
    dial: {
      label: 'Rotary Dial',
      variable: 'angle',
      min: 0,
      max: 360,
      step: 1,
      value: 90,
    },
    spinner: {
      label: 'Numeric Spinner',
      variable: 'count',
      min: 0,
      max: 1000,
      step: 5,
      value: 10,
    },
    toggle: {
      label: 'Toggle Switch',
      variable: 'active',
      min: 0,
      max: 1,
      step: 1,
      value: 1,
    }
  };

  const widget = {
    id,
    type,
    ...(defaults[type] || defaults.range),
    ...customConfig
  };

  const currentVal = dashboardStateManager.getVariable(widget.variable);
  if (currentVal !== undefined) {
    widget.value = currentVal;
  } else {
    dashboardStateManager.setVariable(widget.variable, widget.value, null, 'change');
  }

  widgets.push(widget);
  saveDashboardState();
  renderCanvas();
  return widget;
}

export function removeWidget(widgetId) {
  const idx = widgets.findIndex((w) => w.id === widgetId);
  if (idx !== -1) {
    const w = widgets[idx];
    dashboardStateManager.unregisterBinding(w.variable, w.id);
    widgets.splice(idx, 1);
    saveDashboardState();
    renderCanvas();
  }
}

export async function openWidgetConfig(widgetId) {
  const widget = widgets.find((w) => w.id === widgetId);
  if (!widget) return;

  const vars = getAvailableVariables();
  const varOptions = vars.map((v) => `<option value="${escapeHtml(v)}" ${widget.variable.toLowerCase() === v.toLowerCase() ? 'selected' : ''}>${escapeHtml(v)}</option>`).join('');

  const modalHtml = `
    <div class="mb-3">
      <label class="form-label small">Widget Label</label>
      <input type="text" class="form-control form-control-sm" data-field="label" value="${escapeHtml(widget.label)}">
    </div>
    <div class="mb-3">
      <label class="form-label small">Linked System Variable</label>
      <div class="input-group input-group-sm mb-1">
        <select class="form-select font-mono" data-field="variableSelect" id="config-var-select">
          <option value="__custom__">Custom Variable Name...</option>
          ${varOptions}
        </select>
      </div>
      <input type="text" class="form-control form-control-sm font-mono mt-1 ${vars.includes(widget.variable) ? 'd-none' : ''}" data-field="variableCustom" id="config-var-custom" placeholder="e.g. mass, length, rate" value="${escapeHtml(widget.variable)}">
    </div>
    <div class="row g-2 mb-3">
      <div class="col-4">
        <label class="form-label small">Minimum</label>
        <input type="number" step="any" class="form-control form-control-sm font-mono" data-field="min" value="${widget.min}">
      </div>
      <div class="col-4">
        <label class="form-label small">Maximum</label>
        <input type="number" step="any" class="form-control form-control-sm font-mono" data-field="max" value="${widget.max}">
      </div>
      <div class="col-4">
        <label class="form-label small">Calculation Step</label>
        <input type="number" step="any" class="form-control form-control-sm font-mono" data-field="step" value="${widget.step}">
      </div>
    </div>
    <div class="mb-2">
      <label class="form-label small">Current Default Value</label>
      <input type="number" step="any" class="form-control form-control-sm font-mono" data-field="value" value="${widget.value}">
    </div>
  `;

  setTimeout(() => {
    const sel = document.getElementById('config-var-select');
    const cust = document.getElementById('config-var-custom');
    if (sel && cust) {
      sel.addEventListener('change', () => {
        if (sel.value === '__custom__') {
          cust.classList.remove('d-none');
          cust.focus();
        } else {
          cust.classList.add('d-none');
          cust.value = sel.value;
        }
      });
      if (!vars.includes(widget.variable)) {
        sel.value = '__custom__';
        cust.classList.remove('d-none');
      }
    }
  }, 50);

  const values = await openFormModal({
    title: `Configure ${widget.label}`,
    bodyHtml: modalHtml,
    confirmLabel: 'Apply Configuration'
  });

  if (!values) return;

  const oldVariable = widget.variable;
  const newVariable = (values.variableSelect === '__custom__' || !values.variableSelect ? values.variableCustom : values.variableSelect) || widget.variable;

  dashboardStateManager.unregisterBinding(oldVariable, widget.id);

  widget.label = values.label.trim() || widget.label;
  widget.variable = newVariable.trim() || widget.variable;
  widget.min = Number(values.min);
  widget.max = Number(values.max);
  widget.step = Number(values.step) || 1;
  widget.value = Number(values.value);

  if (widget.min >= widget.max) {
    widget.max = widget.min + 100;
  }

  dashboardStateManager.setVariable(widget.variable, widget.value, null, 'change');
  saveDashboardState();
  renderCanvas();
}

function renderSliderWidget(w) {
  const val = Number(dashboardStateManager.getVariable(w.variable) ?? w.value);
  const inputId = `input-${w.id}`;
  const displayId = `val-${w.id}`;

  const slider = el('input', {
    type: 'range',
    class: 'form-range cf-dashboard-range-slider',
    id: inputId,
    min: String(w.min),
    max: String(w.max),
    step: String(w.step),
    value: String(val)
  });

  const valueDisplay = el('span', { class: 'font-mono cf-widget-val-badge', id: displayId }, String(val));

  slider.addEventListener('input', () => {
    const num = Number(slider.value);
    valueDisplay.textContent = String(num);
    w.value = num;
    dashboardStateManager.setVariable(w.variable, num, w.id, 'input');
  });

  slider.addEventListener('change', () => {
    const num = Number(slider.value);
    valueDisplay.textContent = String(num);
    w.value = num;
    dashboardStateManager.setVariable(w.variable, num, w.id, 'change');
  });

  dashboardStateManager.registerBinding(w.variable, inputId, (newVal) => {
    slider.value = String(newVal);
    valueDisplay.textContent = String(newVal);
    w.value = Number(newVal);
  });

  return el('div', { class: 'cf-widget-body' }, [
    el('div', { class: 'd-flex justify-content-between align-items-baseline mb-2' }, [
      el('span', { class: 'cf-muted small' }, `${w.variable} =`),
      valueDisplay
    ]),
    slider,
    el('div', { class: 'd-flex justify-content-between cf-faint small mt-1 font-mono' }, [
      el('span', {}, String(w.min)),
      el('span', {}, `step: ${w.step}`),
      el('span', {}, String(w.max))
    ])
  ]);
}

function renderDialWidget(w) {
  const val = Number(dashboardStateManager.getVariable(w.variable) ?? w.value);
  const inputId = `dial-${w.id}`;
  const displayId = `val-${w.id}`;
  const knobId = `knob-${w.id}`;
  const arcId = `arc-${w.id}`;

  const min = w.min;
  const max = w.max;
  const range = max - min || 1;

  const valueToAngle = (v) => {
    const clamped = Math.max(min, Math.min(max, v));
    const ratio = (clamped - min) / range;
    return -135 + ratio * 270;
  };

  const angleToValue = (angle) => {
    const clampedAngle = Math.max(-135, Math.min(135, angle));
    const ratio = (clampedAngle + 135) / 270;
    let computed = min + ratio * range;
    if (w.step > 0) {
      computed = Math.round((computed - min) / w.step) * w.step + min;
    }
    return Number(computed.toFixed(4));
  };

  const currentAngle = valueToAngle(val);
  const radius = 38;
  const circumference = 2 * Math.PI * radius;
  const arcLength = circumference * (270 / 360);
  const strokeOffset = arcLength - ((val - min) / range) * arcLength;

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 100 100');
  svg.setAttribute('class', 'cf-dial-svg');

  const bgCircle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  bgCircle.setAttribute('cx', '50');
  bgCircle.setAttribute('cy', '50');
  bgCircle.setAttribute('r', String(radius));
  bgCircle.setAttribute('class', 'cf-dial-bg-track');
  bgCircle.setAttribute('stroke-dasharray', `${arcLength} ${circumference}`);
  bgCircle.setAttribute('transform', 'rotate(135 50 50)');

  const activeArc = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  activeArc.setAttribute('cx', '50');
  activeArc.setAttribute('cy', '50');
  activeArc.setAttribute('r', String(radius));
  activeArc.setAttribute('class', 'cf-dial-active-arc');
  activeArc.setAttribute('id', arcId);
  activeArc.setAttribute('stroke-dasharray', `${arcLength} ${circumference}`);
  activeArc.setAttribute('stroke-dashoffset', String(strokeOffset));
  activeArc.setAttribute('transform', 'rotate(135 50 50)');

  svg.appendChild(bgCircle);
  svg.appendChild(activeArc);

  const knob = el('div', {
    class: 'cf-dial-knob',
    id: knobId,
    style: `transform: rotate(${currentAngle}deg);`
  }, [
    el('div', { class: 'cf-dial-pointer' })
  ]);

  const valueDisplay = el('div', { class: 'font-mono cf-dial-val-text', id: displayId }, String(val));

  const dialContainer = el('div', { class: 'cf-dial-container', id: inputId }, [
    svg,
    knob,
    valueDisplay
  ]);

  let isDragging = false;

  const updateDial = (clientX, clientY, eventType = 'input') => {
    const rect = dialContainer.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const dx = clientX - centerX;
    const dy = clientY - centerY;

    let deg = Math.atan2(dy, dx) * (180 / Math.PI) + 90;
    if (deg > 180) deg -= 360;

    let targetAngle = deg;
    if (targetAngle < -135 && targetAngle > -180) targetAngle = -135;
    if (targetAngle > 135 || targetAngle <= -180) targetAngle = 135;

    const newVal = angleToValue(targetAngle);
    w.value = newVal;
    valueDisplay.textContent = String(newVal);
    knob.style.transform = `rotate(${valueToAngle(newVal)}deg)`;
    const newOffset = arcLength - ((newVal - min) / range) * arcLength;
    activeArc.setAttribute('stroke-dashoffset', String(newOffset));

    dashboardStateManager.setVariable(w.variable, newVal, w.id, eventType);
  };

  dialContainer.addEventListener('mousedown', (e) => {
    e.preventDefault();
    isDragging = true;
    updateDial(e.clientX, e.clientY, 'input');

    const onMouseMove = (ev) => {
      if (isDragging) updateDial(ev.clientX, ev.clientY, 'input');
    };
    const onMouseUp = (ev) => {
      if (isDragging) {
        isDragging = false;
        updateDial(ev.clientX, ev.clientY, 'change');
      }
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
  });

  dialContainer.addEventListener('touchstart', (e) => {
    e.preventDefault();
    isDragging = true;
    if (e.touches[0]) updateDial(e.touches[0].clientX, e.touches[0].clientY, 'input');

    const onTouchMove = (ev) => {
      if (isDragging && ev.touches[0]) updateDial(ev.touches[0].clientX, ev.touches[0].clientY, 'input');
    };
    const onTouchEnd = (ev) => {
      if (isDragging) {
        isDragging = false;
        const touch = ev.changedTouches ? ev.changedTouches[0] : null;
        if (touch) updateDial(touch.clientX, touch.clientY, 'change');
      }
      window.removeEventListener('touchmove', onTouchMove);
      window.removeEventListener('touchend', onTouchEnd);
    };
    window.addEventListener('touchmove', onTouchMove, { passive: false });
    window.addEventListener('touchend', onTouchEnd);
  }, { passive: false });

  dashboardStateManager.registerBinding(w.variable, inputId, (newVal) => {
    const num = Number(newVal);
    w.value = num;
    valueDisplay.textContent = String(num);
    knob.style.transform = `rotate(${valueToAngle(num)}deg)`;
    const newOffset = arcLength - ((num - min) / range) * arcLength;
    activeArc.setAttribute('stroke-dashoffset', String(newOffset));
  });

  return el('div', { class: 'cf-widget-body text-center' }, [
    el('div', { class: 'd-flex justify-content-between align-items-baseline mb-2' }, [
      el('span', { class: 'cf-muted small' }, `${w.variable} (rotary)`),
      el('span', { class: 'cf-faint small font-mono' }, `${w.min}..${w.max}`)
    ]),
    dialContainer
  ]);
}

function renderSpinnerWidget(w) {
  const val = Number(dashboardStateManager.getVariable(w.variable) ?? w.value);
  const inputId = `spinner-${w.id}`;
  const displayId = `val-${w.id}`;

  const input = el('input', {
    type: 'number',
    class: 'form-control form-control-sm text-center font-mono cf-spinner-input',
    id: inputId,
    min: String(w.min),
    max: String(w.max),
    step: String(w.step),
    value: String(val)
  });

  const btnDec = el('button', { class: 'btn btn-sm btn-cf-ghost px-2.5', type: 'button' }, '−');
  const btnInc = el('button', { class: 'btn btn-sm btn-cf-ghost px-2.5', type: 'button' }, '+');

  const updateVal = (newVal, eventType = 'input') => {
    let clamped = Math.max(w.min, Math.min(w.max, newVal));
    if (w.step > 0) {
      clamped = Math.round((clamped - w.min) / w.step) * w.step + w.min;
    }
    clamped = Number(clamped.toFixed(4));
    input.value = String(clamped);
    w.value = clamped;
    dashboardStateManager.setVariable(w.variable, clamped, w.id, eventType);
  };

  btnDec.addEventListener('click', () => {
    updateVal(Number(input.value) - (w.step || 1), 'change');
  });

  btnInc.addEventListener('click', () => {
    updateVal(Number(input.value) + (w.step || 1), 'change');
  });

  input.addEventListener('change', () => {
    updateVal(Number(input.value), 'change');
  });

  input.addEventListener('input', () => {
    const num = Number(input.value);
    if (!isNaN(num)) {
      w.value = num;
      dashboardStateManager.setVariable(w.variable, num, w.id, 'input');
    }
  });

  dashboardStateManager.registerBinding(w.variable, inputId, (newVal) => {
    input.value = String(newVal);
    w.value = Number(newVal);
  });

  return el('div', { class: 'cf-widget-body' }, [
    el('div', { class: 'd-flex justify-content-between align-items-baseline mb-2' }, [
      el('span', { class: 'cf-muted small' }, `${w.variable} =`),
      el('span', { class: 'cf-faint small font-mono' }, `step \u00B1${w.step}`)
    ]),
    el('div', { class: 'input-group input-group-sm cf-spinner-group' }, [
      btnDec,
      input,
      btnInc
    ]),
    el('div', { class: 'd-flex justify-content-between cf-faint small mt-1 font-mono' }, [
      el('span', {}, `min: ${w.min}`),
      el('span', {}, `max: ${w.max}`)
    ])
  ]);
}

function renderToggleWidget(w) {
  const currentVal = dashboardStateManager.getVariable(w.variable) ?? w.value;
  const isChecked = Boolean(Number(currentVal));
  const inputId = `toggle-${w.id}`;
  const statusId = `status-${w.id}`;

  const checkbox = el('input', {
    class: 'form-check-input cf-toggle-switch-input',
    type: 'checkbox',
    role: 'switch',
    id: inputId,
    checked: isChecked
  });

  const statusBadge = el('span', {
    class: `cf-badge-unit font-mono ${isChecked ? 'active-toggle' : ''}`,
    id: statusId
  }, isChecked ? 'ACTIVE (1)' : 'INACTIVE (0)');

  const indicatorDot = el('span', {
    class: `cf-status-dot ${isChecked ? '' : 'offline'}`
  });

  const handleToggle = (eventType) => {
    const active = checkbox.checked;
    const numVal = active ? (w.max !== 0 ? w.max : 1) : (w.min !== 1 ? w.min : 0);
    w.value = numVal;
    statusBadge.textContent = active ? 'ACTIVE (1)' : 'INACTIVE (0)';
    statusBadge.classList.toggle('active-toggle', active);
    indicatorDot.classList.toggle('offline', !active);
    dashboardStateManager.setVariable(w.variable, numVal, w.id, eventType);
  };

  checkbox.addEventListener('input', () => handleToggle('input'));
  checkbox.addEventListener('change', () => handleToggle('change'));

  dashboardStateManager.registerBinding(w.variable, inputId, (newVal) => {
    const active = Boolean(Number(newVal));
    checkbox.checked = active;
    w.value = Number(newVal);
    statusBadge.textContent = active ? 'ACTIVE (1)' : 'INACTIVE (0)';
    statusBadge.classList.toggle('active-toggle', active);
    indicatorDot.classList.toggle('offline', !active);
  });

  return el('div', { class: 'cf-widget-body' }, [
    el('div', { class: 'd-flex justify-content-between align-items-baseline mb-2' }, [
      el('span', { class: 'cf-muted small' }, `${w.variable} (binary)`),
      el('div', { class: 'd-flex align-items-center gap-1.5' }, [
        indicatorDot,
        statusBadge
      ])
    ]),
    el('div', { class: 'form-check form-switch cf-dashboard-switch-wrap d-flex justify-content-center py-2' }, [
      checkbox
    ]),
    el('div', { class: 'text-center cf-faint small font-mono' }, isChecked ? 'State: High Signal' : 'State: Low Signal')
  ]);
}

function renderWidgetBlock(w) {
  const card = el('div', {
    class: 'cf-dashboard-widget-card',
    id: w.id,
    'data-widget-type': w.type
  });

  const header = el('div', { class: 'cf-widget-header d-flex justify-content-between align-items-center mb-2' }, [
    el('div', { class: 'd-flex align-items-center gap-2 overflow-hidden' }, [
      el('span', { class: 'cf-widget-type-icon' }, getTypeIcon(w.type)),
      el('strong', { class: 'cf-widget-title text-truncate' }, w.label),
      el('span', { class: 'cf-badge-unit font-mono' }, w.variable)
    ]),
    el('div', { class: 'd-flex gap-1' }, [
      el('button', {
        class: 'btn btn-sm btn-cf-ghost py-0 px-1.5',
        title: 'Configure Widget Binding',
        onclick: () => openWidgetConfig(w.id)
      }, '\u2699'),
      el('button', {
        class: 'btn btn-sm btn-cf-ghost py-0 px-1.5 text-danger',
        title: 'Remove Widget',
        onclick: () => removeWidget(w.id)
      }, '\u2715')
    ])
  ]);

  let body;
  switch (w.type) {
    case 'range': body = renderSliderWidget(w); break;
    case 'dial': body = renderDialWidget(w); break;
    case 'spinner': body = renderSpinnerWidget(w); break;
    case 'toggle': body = renderToggleWidget(w); break;
    default: body = renderSliderWidget(w); break;
  }

  card.appendChild(header);
  card.appendChild(body);
  return card;
}

function getTypeIcon(type) {
  switch (type) {
    case 'range': return '\u22B7';
    case 'dial': return '\u25CE';
    case 'spinner': return '\u21C5';
    case 'toggle': return '\u229F';
    default: return '\u25A3';
  }
}

export function renderCanvas() {
  const grid = document.getElementById('dashboard-canvas-grid');
  const empty = document.getElementById('dashboard-canvas-empty');
  if (!grid || !empty) return;

  grid.innerHTML = '';

  if (widgets.length === 0) {
    empty.classList.remove('d-none');
    return;
  }
  empty.classList.add('d-none');

  for (const w of widgets) {
    const col = el('div', { class: 'col-md-6 col-xl-4' }, [renderWidgetBlock(w)]);
    grid.appendChild(col);
  }
}

export function addSampleWidgets() {
  widgets = [];
  dashboardStateManager.reset();

  createWidget('range', { label: 'Mass', variable: 'mass', min: 1, max: 100, step: 1, value: 25 });
  createWidget('dial', { label: 'Theta Angle', variable: 'theta', min: 0, max: 360, step: 1, value: 45 });
  createWidget('spinner', { label: 'Length', variable: 'length', min: 0.5, max: 50, step: 0.5, value: 10 });
  createWidget('toggle', { label: 'Gravity Boost', variable: 'boost', min: 0, max: 1, step: 1, value: 1 });

  toast('Sample dashboard control layout loaded.', 'success');
}

export function clearDashboard() {
  if (widgets.length === 0) return;
  widgets.forEach((w) => dashboardStateManager.unregisterBinding(w.variable, w.id));
  widgets = [];
  saveDashboardState();
  renderCanvas();
  toast('Dashboard canvas cleared.', 'info');
}

function initToolbox() {
  const toolboxItems = document.querySelectorAll('.cf-toolbox-item');
  toolboxItems.forEach((item) => {
    const type = item.dataset.widgetType;

    item.setAttribute('draggable', 'true');

    item.addEventListener('dragstart', (e) => {
      e.dataTransfer.setData('text/plain', type);
      e.dataTransfer.effectAllowed = 'copy';
      item.classList.add('dragging');
    });

    item.addEventListener('dragend', () => {
      item.classList.remove('dragging');
    });

    item.addEventListener('click', () => {
      createWidget(type);
      toast(`Added ${type} control to canvas`, 'info', 2000);
    });
  });

  const dropzone = document.getElementById('dashboard-canvas-dropzone');
  if (dropzone) {
    dropzone.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'copy';
      dropzone.classList.add('drag-over');
    });

    dropzone.addEventListener('dragleave', (e) => {
      if (!dropzone.contains(e.relatedTarget)) {
        dropzone.classList.remove('drag-over');
      }
    });

    dropzone.addEventListener('drop', (e) => {
      e.preventDefault();
      dropzone.classList.remove('drag-over');
      const type = e.dataTransfer.getData('text/plain');
      if (type) {
        createWidget(type);
        toast(`Added ${type} control to canvas`, 'info', 2000);
      }
    });
  }
}

export function refreshDashboardView() {
  loadDashboardState();
  renderCanvas();
}

export function initDashboardView() {
  initToolbox();

  const clearBtn = document.getElementById('dashboard-clear-btn');
  if (clearBtn) clearBtn.addEventListener('click', clearDashboard);

  const sampleBtn = document.getElementById('dashboard-sample-btn');
  if (sampleBtn) sampleBtn.addEventListener('click', addSampleWidgets);

  window.addEventListener('cf:workspace-changed', () => {
    refreshDashboardView();
  });

  loadDashboardState();
  renderCanvas();
}
