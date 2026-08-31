import { state } from '../state.js';
import { LocalApi } from '../api.js';
import { toast } from '../utils.js';
import { sampleFunction } from '../engine/localEngine.js';
import { activeWorkspaceVariables } from './workspace.js';

function getCssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function drawGraph(points, variableName) {
  const canvas = document.getElementById('graph-canvas');
  const ctx = canvas.getContext('2d');
  const w = canvas.width, h = canvas.height;
  ctx.clearRect(0, 0, w, h);

  const defined = points.filter((p) => p.y !== null && Number.isFinite(p.y));
  if (defined.length === 0) {
    ctx.fillStyle = getCssVar('--cf-text-faint') || '#888';
    ctx.font = '14px sans-serif';
    ctx.fillText('No defined points in this range.', 20, h / 2);
    return;
  }

  const xs = points.map((p) => p.x);
  const ys = defined.map((p) => p.y);
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  let yMin = Math.min(...ys), yMax = Math.max(...ys);
  if (yMin === yMax) { yMin -= 1; yMax += 1; }
  const yPad = (yMax - yMin) * 0.08;
  yMin -= yPad; yMax += yPad;

  const marginL = 54, marginR = 16, marginT = 16, marginB = 30;
  const plotW = w - marginL - marginR, plotH = h - marginT - marginB;

  const toPx = (x) => marginL + ((x - xMin) / (xMax - xMin || 1)) * plotW;
  const toPy = (y) => marginT + plotH - ((y - yMin) / (yMax - yMin || 1)) * plotH;

  // grid + axes
  ctx.strokeStyle = getCssVar('--cf-border') || '#333';
  ctx.lineWidth = 1;
  ctx.font = '11px ui-monospace, monospace';
  ctx.fillStyle = getCssVar('--cf-text-faint') || '#888';

  const gridLines = 6;
  for (let i = 0; i <= gridLines; i++) {
    const gy = marginT + (plotH / gridLines) * i;
    ctx.beginPath(); ctx.moveTo(marginL, gy); ctx.lineTo(w - marginR, gy); ctx.stroke();
    const value = yMax - ((yMax - yMin) / gridLines) * i;
    ctx.fillText(value.toPrecision(3), 4, gy + 3);
  }
  for (let i = 0; i <= gridLines; i++) {
    const gx = marginL + (plotW / gridLines) * i;
    ctx.beginPath(); ctx.moveTo(gx, marginT); ctx.lineTo(gx, h - marginB); ctx.stroke();
    const value = xMin + ((xMax - xMin) / gridLines) * i;
    ctx.fillText(value.toPrecision(3), gx - 12, h - marginB + 16);
  }

  // x=0 / y=0 axis lines, if in range
  ctx.strokeStyle = getCssVar('--cf-text-faint') || '#888';
  if (xMin < 0 && xMax > 0) { ctx.beginPath(); ctx.moveTo(toPx(0), marginT); ctx.lineTo(toPx(0), h - marginB); ctx.stroke(); }
  if (yMin < 0 && yMax > 0) { ctx.beginPath(); ctx.moveTo(marginL, toPy(0)); ctx.lineTo(w - marginR, toPy(0)); ctx.stroke(); }

  // the function line - break the path wherever y is undefined so gaps render as gaps, not lies
  ctx.strokeStyle = getCssVar('--cf-signal-strong') || '#5fe0cb';
  ctx.lineWidth = 2.2;
  ctx.beginPath();
  let drawing = false;
  for (const p of points) {
    if (p.y === null || !Number.isFinite(p.y) || p.y < yMin - (yMax - yMin) || p.y > yMax + (yMax - yMin)) {
      drawing = false;
      continue;
    }
    const px = toPx(p.x), py = toPy(p.y);
    if (!drawing) { ctx.moveTo(px, py); drawing = true; } else { ctx.lineTo(px, py); }
  }
  ctx.stroke();

  ctx.fillStyle = getCssVar('--cf-text-dim') || '#aaa';
  ctx.fillText(variableName, w - marginR - 12, h - 6);
}

async function plot() {
  const expr = document.getElementById('graph-expr').value.trim();
  const variable = document.getElementById('graph-var').value.trim() || 'x';
  const min = Number(document.getElementById('graph-min').value);
  const max = Number(document.getElementById('graph-max').value);
  const statusEl = document.getElementById('graph-status');

  if (!expr) { toast('Enter a function to plot.', 'warning'); return; }
  if (!Number.isFinite(min) || !Number.isFinite(max) || min >= max) { toast('Min must be less than max.', 'warning'); return; }

  const fixedVars = { ...activeWorkspaceVariables() };
  delete fixedVars[variable.toLowerCase()];

  if (state.online) {
    try {
      const res = await LocalApi.graph({
        expression: expr, variable, min, max, samples: 300,
        variables: fixedVars, workspaceId: state.activeWorkspaceId || undefined, angleMode: state.angleMode,
      });
      drawGraph(res.points.map((p) => ({ x: Number(p.x), y: p.y === null ? null : Number(p.y) })), variable);
      statusEl.textContent = `Plotted ${res.points.length} points via the backend engine.`;
      return;
    } catch (err) {
      statusEl.textContent = 'Backend graphing failed (' + err.message + '); trying offline engine...';
    }
  }

  try {
    const points = sampleFunction(expr, variable, min, max, 300, { variables: fixedVars, angleMode: state.angleMode });
    drawGraph(points, variable);
    statusEl.textContent = 'Plotted offline (reduced precision, smaller function set).';
  } catch (err) {
    toast(err.message || 'Could not plot that function.', 'error');
  }
}

export function initGraphView() {
  document.getElementById('graph-plot-btn').addEventListener('click', plot);
}
