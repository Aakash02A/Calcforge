// Small, dependency-free helpers shared across views.

export function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function debounce(fn, waitMs) {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), waitMs);
  };
}

export function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key === 'class') node.className = value;
    else if (key === 'html') node.innerHTML = value;
    else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
    else if (value !== undefined && value !== null) node.setAttribute(key, value);
  }
  for (const child of [].concat(children)) {
    if (child === null || child === undefined) continue;
    node.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
  }
  return node;
}

let toastStack = null;
export function toast(message, variant = 'info', timeoutMs = 4000) {
  if (!toastStack) toastStack = document.getElementById('toast-stack');
  if (!toastStack) return;

  const colors = {
    info: 'var(--cf-signal)',
    success: 'var(--cf-success)',
    error: 'var(--cf-danger)',
    warning: 'var(--cf-warning)',
  };

  const node = el('div', {
    class: 'cf-card',
    style: `border-left: 3px solid ${colors[variant] || colors.info}; font-size: .85rem; box-shadow: var(--cf-shadow); animation: none;`,
  }, [message]);

  toastStack.appendChild(node);
  setTimeout(() => {
    node.style.transition = 'opacity .25s ease';
    node.style.opacity = '0';
    setTimeout(() => node.remove(), 260);
  }, timeoutMs);
}

/**
 * Renders arbitrary HTML into the shared "generic-modal" and resolves when the confirm
 * button is clicked (with an object of collected form values), or rejects (with null) if
 * the modal is dismissed without confirming. Avoids needing a bespoke modal per form.
 */
export function openFormModal({ title, bodyHtml, confirmLabel = 'Save', onOpen = null }) {
  return new Promise((resolve) => {
    const modalEl = document.getElementById('generic-modal');
    document.getElementById('generic-modal-title').textContent = title;
    document.getElementById('generic-modal-body').innerHTML = bodyHtml;
    const confirmBtn = document.getElementById('generic-modal-confirm');
    confirmBtn.textContent = confirmLabel;

    // bootstrap is loaded globally via vendor/bootstrap/js/bootstrap.bundle.min.js
    const modal = new window.bootstrap.Modal(modalEl);

    let settled = false;
    const cleanup = () => {
      confirmBtn.removeEventListener('click', onConfirm);
      modalEl.removeEventListener('hidden.bs.modal', onHidden);
    };
    const onConfirm = () => {
      settled = true;
      const form = document.getElementById('generic-modal-body');
      const values = {};
      form.querySelectorAll('[data-field]').forEach((input) => {
        values[input.dataset.field] = input.type === 'checkbox' ? input.checked : input.value;
      });
      cleanup();
      modal.hide();
      resolve(values);
    };
    const onHidden = () => {
      cleanup();
      if (!settled) resolve(null);
    };

    confirmBtn.addEventListener('click', onConfirm);
    modalEl.addEventListener('hidden.bs.modal', onHidden);
    if (onOpen) onOpen(modalEl);
    modal.show();
  });
}

export function confirmDialog(message) {
  return openFormModal({
    title: 'Please confirm',
    bodyHtml: `<p>${escapeHtml(message)}</p>`,
    confirmLabel: 'Yes, continue',
  }).then((result) => result !== null);
}

export function formatDate(iso) {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export function formatUnitHtml(unitStr) {
  if (!unitStr) return '';
  let clean = String(unitStr).trim();
  if (clean.startsWith('[') && clean.endsWith(']')) {
    clean = clean.slice(1, -1).trim();
  }
  if (!clean || clean === '1' || clean.toLowerCase() === 'dimensionless') return '';

  const formatUnitPart = (part) => {
    return escapeHtml(part)
      .replace(/\^([-\d]+)/g, '<sup>$1</sup>')
      .replace(/\*|\u00B7/g, '·');
  };

  const slashIdx = clean.indexOf('/');
  if (slashIdx !== -1) {
    const num = clean.slice(0, slashIdx).trim();
    const den = clean.slice(slashIdx + 1).trim();
    return `<span class="cf-unit-frac"><span class="cf-unit-num">${formatUnitPart(num)}</span><span class="cf-unit-slash">/</span><span class="cf-unit-den">${formatUnitPart(den)}</span></span>`;
  }
  return `<span class="cf-unit">${formatUnitPart(clean)}</span>`;
}

const STAGE_LABELS = {
  INPUT: 'Input',
  ASSUMPTIONS: 'Assumptions',
  FORMULA: 'Formula',
  COMPUTATION: 'Computation',
  RESULT: 'Result',
};

/** Renders a CalculationTrailDto-shaped { steps: [...] } object as the stepped ledger UI. */
export function renderTrail(container, trail) {
  if (!trail || !trail.steps || trail.steps.length === 0) {
    container.innerHTML = '<p class="cf-ledger-empty">No trail available.</p>';
    return;
  }
  let html = '';
  let currentStage = null;
  for (const s of trail.steps) {
    if (s.stage !== currentStage) {
      if (currentStage !== null) html += `</div>`;
      currentStage = s.stage;
      html += `<div class="cf-ledger-stage"><div class="cf-ledger-stage-label">${escapeHtml(STAGE_LABELS[s.stage] || s.stage)}</div>`;
    }
    const isResult = s.stage === 'RESULT';
    html += `<div class="cf-ledger-step ${isResult ? 'result' : ''}">`;
    if (s.title) html += `<span class="cf-step-title">${escapeHtml(s.title)}</span>`;
    if (s.expression) html += `<span class="cf-step-expr">${escapeHtml(s.expression)}</span>`;

    if (s.value) {
      const unit = s.unit || s.unit_dimension_string || s.unitDimension;
      let unitHtml = '';
      if (unit) {
        unitHtml = ` <span class="cf-unit-badge">${formatUnitHtml(unit)}</span>`;
      }
      html += `<span class="cf-step-value">${isResult ? '' : '= '}${escapeHtml(s.value)}${unitHtml}</span>`;
    }

    if (s.note) html += `<span class="cf-faint" style="flex-basis:100%; font-size:.72rem;">${escapeHtml(s.note)}</span>`;
    html += `</div>`;
  }
  html += `</div>`;
  container.innerHTML = html;
}
