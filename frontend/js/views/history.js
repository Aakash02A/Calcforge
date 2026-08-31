import { state } from '../state.js';
import { LocalApi } from '../api.js';
import { el, toast, escapeHtml, renderTrail, formatDate, debounce, confirmDialog } from '../utils.js';
import { setExpression } from './calculator.js';

let currentPage = 0;
const PAGE_SIZE = 20;

async function load() {
  const listEl = document.getElementById('history-list');
  const emptyEl = document.getElementById('history-empty');
  const paginationEl = document.getElementById('history-pagination');

  if (!state.online) {
    listEl.innerHTML = '';
    emptyEl.classList.remove('d-none');
    emptyEl.querySelector('p').textContent = 'History search requires the backend to be reachable.';
    paginationEl.innerHTML = '';
    return;
  }

  const q = document.getElementById('history-search').value.trim();
  const tag = document.getElementById('history-tag-filter').value.trim();

  let page;
  try {
    page = await LocalApi.searchHistory({ q, tag, page: currentPage, pageSize: PAGE_SIZE });
  } catch (err) {
    toast('Could not load history: ' + err.message, 'error');
    return;
  }

  listEl.innerHTML = '';
  if (page.items.length === 0) {
    emptyEl.classList.remove('d-none');
    emptyEl.querySelector('p').textContent = 'No calculations found.';
  } else {
    emptyEl.classList.add('d-none');
    for (const item of page.items) listEl.appendChild(renderEntry(item));
  }

  renderPagination(page);
}

function renderEntry(item) {
  const row = el('div', { class: 'cf-card py-2 px-3' });

  const header = el('div', { class: 'd-flex justify-content-between align-items-start gap-2' }, [
    el('div', { style: 'min-width:0;' }, [
      el('div', { class: 'font-mono', style: 'font-size:.85rem; word-break: break-word;' }, item.expression),
      el('div', { class: 'font-mono', style: 'font-size:1.05rem; font-weight:700; color: var(--cf-accent-strong);' }, item.result || ''),
      el('div', { class: 'cf-faint', style: 'font-size:.7rem;' }, [
        formatDate(item.createdAt),
        item.tags.length ? '  \u2022  ' + item.tags.join(', ') : '',
      ].join('')),
    ]),
    el('div', { class: 'd-flex flex-column gap-1' }, [
      el('button', { class: 'btn btn-sm btn-cf-ghost py-0 px-2', title: 'Reuse', onclick: () => setExpression(item.expression) }, '\u21ba'),
      el('button', { class: 'btn btn-sm btn-cf-ghost py-0 px-2', title: item.favorite ? 'Unfavorite' : 'Favorite', onclick: () => toggleFavorite(item) }, item.favorite ? '\u2605' : '\u2606'),
      el('button', { class: 'btn btn-sm btn-cf-ghost py-0 px-2', title: 'Delete', onclick: () => remove(item) }, '\u2715'),
    ]),
  ]);

  const trailEl = el('div', { class: 'cf-ledger d-none mt-2' });
  const trailToggleBtn = el('button', {
    class: 'btn btn-sm btn-cf-ghost py-0 px-2 mt-1', style: 'font-size:.7rem;',
  }, 'Show trail');
  trailToggleBtn.addEventListener('click', () => {
    trailEl.classList.toggle('d-none');
    const hidden = trailEl.classList.contains('d-none');
    trailToggleBtn.textContent = hidden ? 'Show trail' : 'Hide trail';
    if (!hidden) renderTrail(trailEl, item.trail);
  });

  row.appendChild(header);
  row.appendChild(trailEl);
  row.appendChild(trailToggleBtn);
  return row;
}

async function toggleFavorite(item) {
  try {
    await LocalApi.updateHistory(item.id, { favorite: !item.favorite });
    load();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function remove(item) {
  if (!(await confirmDialog('Delete this history entry?'))) return;
  try {
    await LocalApi.deleteHistory(item.id);
    load();
  } catch (err) {
    toast(err.message, 'error');
  }
}

function renderPagination(page) {
  const el2 = document.getElementById('history-pagination');
  el2.innerHTML = '';
  if (page.totalPages <= 1) return;
  for (let i = 0; i < page.totalPages; i++) {
    const li = document.createElement('li');
    li.className = 'page-item' + (i === page.page ? ' active' : '');
    const a = document.createElement('a');
    a.href = '#';
    a.className = 'page-link';
    a.textContent = String(i + 1);
    a.addEventListener('click', (e) => { e.preventDefault(); currentPage = i; load(); });
    li.appendChild(a);
    el2.appendChild(li);
  }
}

const debouncedLoad = debounce(() => { currentPage = 0; load(); }, 300);

export function refreshHistoryView() {
  currentPage = 0;
  load();
}

export function initHistoryView() {
  document.getElementById('history-search').addEventListener('input', debouncedLoad);
  document.getElementById('history-tag-filter').addEventListener('input', debouncedLoad);
  document.getElementById('history-refresh-btn').addEventListener('click', () => load());
}
