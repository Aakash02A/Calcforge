import { state } from '../state.js';
import { LocalApi } from '../api.js';
import { toast } from '../utils.js';
import { sampleFunction } from '../engine/localEngine.js';
import { activeWorkspaceVariables } from './workspace.js';

let lastPlotData = {
  points: null,
  variableName: 'x',
  expr: 'sin(x)',
  xMin: -10,
  xMax: 10,
  yMin: -1.2,
  yMax: 1.2,
  errorMsg: null,
};

let hoverCoord = null;

function renderCanvas() {
  const canvas = document.getElementById('graph-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');

  // Handle HiDPI crisp rendering
  const rect = canvas.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  const containerW = canvas.parentElement ? canvas.parentElement.clientWidth - 20 : 0;
  const displayW = rect.width > 0 ? rect.width : (containerW > 0 ? containerW : 900);
  const displayH = 420;

  if (canvas.width !== Math.round(displayW * dpr) || canvas.height !== Math.round(displayH * dpr)) {
    canvas.width = Math.round(displayW * dpr);
    canvas.height = Math.round(displayH * dpr);
  }

  ctx.save();
  ctx.scale(dpr, dpr);

  const w = displayW;
  const h = displayH;

  const isDark = state.theme !== 'light';
  const bgColor = isDark ? '#14181f' : '#ffffff';
  const gridColorMinor = isDark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(0, 0, 0, 0.04)';
  const gridColorMajor = isDark ? 'rgba(255, 255, 255, 0.12)' : 'rgba(0, 0, 0, 0.10)';
  const axisColor = isDark ? 'rgba(255, 255, 255, 0.55)' : 'rgba(0, 0, 0, 0.55)';
  const textColor = isDark ? '#a9b1bc' : '#5b6270';
  const curveColor = isDark ? '#45c4b0' : '#0d9488';
  const crosshairColor = isDark ? 'rgba(232, 116, 59, 0.85)' : 'rgba(232, 116, 59, 0.95)';

  // Fill canvas background
  ctx.fillStyle = bgColor;
  ctx.fillRect(0, 0, w, h);

  const { points, variableName, expr, errorMsg } = lastPlotData;
  let { xMin, xMax, yMin, yMax } = lastPlotData;

  if (xMin >= xMax) { xMin = -10; xMax = 10; }
  if (yMin >= yMax || !Number.isFinite(yMin) || !Number.isFinite(yMax)) { yMin = -1.5; yMax = 1.5; }

  const marginL = 56, marginR = 24, marginT = 28, marginB = 36;
  const plotW = Math.max(10, w - marginL - marginR);
  const plotH = Math.max(10, h - marginT - marginB);

  const toPx = (x) => marginL + ((x - xMin) / (xMax - xMin || 1)) * plotW;
  const toPy = (y) => marginT + plotH - ((y - yMin) / (yMax - yMin || 1)) * plotH;
  const toMathX = (px) => xMin + ((px - marginL) / plotW) * (xMax - xMin);
  const toMathY = (py) => yMax - ((py - marginT) / plotH) * (yMax - yMin);

  const xRange = xMax - xMin;
  const yRange = yMax - yMin;

  // ------------------------------------------------------------- 1. Minor Sub-Grid
  const subDivsX = 24;
  const subDivsY = 18;
  ctx.strokeStyle = gridColorMinor;
  ctx.lineWidth = 0.75;
  ctx.beginPath();
  for (let i = 1; i < subDivsX; i++) {
    const gx = marginL + (plotW / subDivsX) * i;
    ctx.moveTo(gx, marginT);
    ctx.lineTo(gx, marginT + plotH);
  }
  for (let i = 1; i < subDivsY; i++) {
    const gy = marginT + (plotH / subDivsY) * i;
    ctx.moveTo(marginL, gy);
    ctx.lineTo(marginL + plotW, gy);
  }
  ctx.stroke();

  // ------------------------------------------------------------- 2. Major Grid & Ticks
  const numXSteps = 8;
  const numYSteps = 6;

  ctx.font = '11px ui-monospace, SFMono-Regular, Consolas, monospace';

  // Horizontal major grid lines & Y labels
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  for (let i = 0; i <= numYSteps; i++) {
    const val = yMin + (yRange / numYSteps) * i;
    const py = toPy(val);
    const isZero = Math.abs(val) < (yRange / numYSteps * 0.001);

    ctx.strokeStyle = isZero ? axisColor : gridColorMajor;
    ctx.lineWidth = isZero ? 1.5 : 1;
    ctx.beginPath();
    ctx.moveTo(marginL, py);
    ctx.lineTo(marginL + plotW, py);
    ctx.stroke();

    ctx.fillStyle = isZero ? (isDark ? '#e9ebef' : '#1f2328') : textColor;
    const label = Math.abs(val) < 1e-8 ? '0' : Number(val.toPrecision(4)).toString();
    ctx.fillText(label, marginL - 8, py);
  }

  // Vertical major grid lines & X labels
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  for (let i = 0; i <= numXSteps; i++) {
    const val = xMin + (xRange / numXSteps) * i;
    const px = toPx(val);
    const isZero = Math.abs(val) < (xRange / numXSteps * 0.001);

    ctx.strokeStyle = isZero ? axisColor : gridColorMajor;
    ctx.lineWidth = isZero ? 1.5 : 1;
    ctx.beginPath();
    ctx.moveTo(px, marginT);
    ctx.lineTo(px, marginT + plotH);
    ctx.stroke();

    ctx.fillStyle = isZero ? (isDark ? '#e9ebef' : '#1f2328') : textColor;
    const label = Math.abs(val) < 1e-8 ? '0' : Number(val.toPrecision(4)).toString();
    ctx.fillText(label, px, marginT + plotH + 8);
  }

  // Outer plot frame
  ctx.strokeStyle = gridColorMajor;
  ctx.lineWidth = 1;
  ctx.strokeRect(marginL, marginT, plotW, plotH);

  // ------------------------------------------------------------- 3. Coordinate Axes (x=0, y=0)
  ctx.strokeStyle = axisColor;
  ctx.lineWidth = 1.6;
  if (xMin <= 0 && xMax >= 0) {
    const x0 = toPx(0);
    ctx.beginPath();
    ctx.moveTo(x0, marginT);
    ctx.lineTo(x0, marginT + plotH);
    ctx.stroke();
  }
  if (yMin <= 0 && yMax >= 0) {
    const y0 = toPy(0);
    ctx.beginPath();
    ctx.moveTo(marginL, y0);
    ctx.lineTo(marginL + plotW, y0);
    ctx.stroke();
  }

  // Axis Titles
  ctx.fillStyle = isDark ? '#ff8a4c' : '#e8743b';
  ctx.font = 'bold 12px sans-serif';
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  ctx.fillText(`→ ${variableName || 'x'}`, marginL + plotW, marginT + plotH + 22);

  ctx.textAlign = 'left';
  ctx.fillText(expr ? `f(${variableName || 'x'}) = ${expr}` : '2D Function Grid', marginL, 14);

  // ------------------------------------------------------------- 4. Plotted Function Curve
  if (points && points.length > 0) {
    const defined = points.filter((p) => p.y !== null && Number.isFinite(p.y));
    if (defined.length > 0) {
      ctx.save();
      ctx.beginPath();
      ctx.rect(marginL, marginT, plotW, plotH);
      ctx.clip();

      // Subtle area fill under the curve
      const zeroPy = Math.max(marginT, Math.min(marginT + plotH, toPy(0)));
      const grad = ctx.createLinearGradient(0, marginT, 0, marginT + plotH);
      grad.addColorStop(0, isDark ? 'rgba(69, 196, 176, 0.18)' : 'rgba(13, 148, 136, 0.14)');
      grad.addColorStop(1, 'rgba(69, 196, 176, 0.01)');

      ctx.fillStyle = grad;
      ctx.beginPath();
      let inArea = false;
      for (let i = 0; i < points.length; i++) {
        const p = points[i];
        if (p.y === null || !Number.isFinite(p.y)) {
          if (inArea) {
            ctx.lineTo(toPx(points[i - 1].x), zeroPy);
            ctx.closePath();
            ctx.fill();
            ctx.beginPath();
            inArea = false;
          }
          continue;
        }
        const px = toPx(p.x), py = toPy(p.y);
        if (!inArea) {
          ctx.moveTo(px, zeroPy);
          ctx.lineTo(px, py);
          inArea = true;
        } else {
          ctx.lineTo(px, py);
        }
      }
      if (inArea) {
        ctx.lineTo(toPx(points[points.length - 1].x), zeroPy);
        ctx.closePath();
        ctx.fill();
      }

      // Smooth Curve line
      ctx.strokeStyle = curveColor;
      ctx.lineWidth = 2.4;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.beginPath();
      let drawing = false;
      for (const p of points) {
        if (p.y === null || !Number.isFinite(p.y)) {
          drawing = false;
          continue;
        }
        const px = toPx(p.x), py = toPy(p.y);
        if (!drawing) {
          ctx.moveTo(px, py);
          drawing = true;
        } else {
          ctx.lineTo(px, py);
        }
      }
      ctx.stroke();
      ctx.restore();
    }
  }

  // ------------------------------------------------------------- 5. Error or Info Overlay
  if (errorMsg) {
    ctx.save();
    ctx.fillStyle = isDark ? 'rgba(20, 24, 31, 0.75)' : 'rgba(255, 255, 255, 0.75)';
    ctx.fillRect(marginL + 20, marginT + 20, plotW - 40, 40);
    ctx.strokeStyle = isDark ? '#e8b73b' : '#e8743b';
    ctx.lineWidth = 1;
    ctx.strokeRect(marginL + 20, marginT + 20, plotW - 40, 40);

    ctx.fillStyle = isDark ? '#e8b73b' : '#e8743b';
    ctx.font = '13px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(errorMsg, marginL + plotW / 2, marginT + 40);
    ctx.restore();
  }

  // ------------------------------------------------------------- 6. Interactive Crosshair & Tooltip
  if (hoverCoord && hoverCoord.x >= marginL && hoverCoord.x <= marginL + plotW && hoverCoord.y >= marginT && hoverCoord.y <= marginT + plotH) {
    const mathX = toMathX(hoverCoord.x);

    let nearest = null;
    if (points && points.length > 0) {
      let minDist = Infinity;
      for (const p of points) {
        if (p.y !== null && Number.isFinite(p.y)) {
          const dist = Math.abs(p.x - mathX);
          if (dist < minDist) {
            minDist = dist;
            nearest = p;
          }
        }
      }
    }

    const targetX = nearest ? toPx(nearest.x) : hoverCoord.x;
    const targetY = nearest ? toPy(nearest.y) : hoverCoord.y;
    const dispX = nearest ? nearest.x : mathX;
    const dispY = nearest ? nearest.y : toMathY(hoverCoord.y);

    // Crosshair guidelines
    ctx.save();
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = crosshairColor;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(marginL, targetY);
    ctx.lineTo(marginL + plotW, targetY);
    ctx.moveTo(targetX, marginT);
    ctx.lineTo(targetX, marginT + plotH);
    ctx.stroke();
    ctx.restore();

    // Intersection highlight dot
    ctx.fillStyle = isDark ? '#ff8a4c' : '#e8743b';
    ctx.strokeStyle = isDark ? '#14181f' : '#ffffff';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.arc(targetX, targetY, 5.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();

    // Tooltip badge
    const tipText = `(${Number(dispX.toFixed(3))}, ${Number(dispY.toFixed(3))})`;
    ctx.font = 'bold 11px ui-monospace, monospace';
    const tipW = ctx.measureText(tipText).width + 16;
    const tipH = 24;
    let tipX = targetX + 12;
    let tipY = targetY - 30;
    if (tipX + tipW > marginL + plotW) tipX = targetX - tipW - 12;
    if (tipY < marginT) tipY = targetY + 12;

    ctx.fillStyle = isDark ? '#242933' : '#1f2328';
    ctx.strokeStyle = isDark ? '#343b47' : '#ddd7c6';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.roundRect(tipX, tipY, tipW, tipH, 4);
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText(tipText, tipX + 8, tipY + tipH / 2);
  }

  ctx.restore();
}

export async function plot() {
  const exprInput = document.getElementById('graph-expr');
  const varInput = document.getElementById('graph-var');
  const minInput = document.getElementById('graph-min');
  const maxInput = document.getElementById('graph-max');
  const statusEl = document.getElementById('graph-status');

  const expr = exprInput ? exprInput.value.trim() : 'sin(x)';
  const variable = (varInput ? varInput.value.trim() : 'x') || 'x';
  const min = minInput ? Number(minInput.value) : -10;
  const max = maxInput ? Number(maxInput.value) : 10;

  lastPlotData.expr = expr;
  lastPlotData.variableName = variable;
  lastPlotData.xMin = Number.isFinite(min) ? min : -10;
  lastPlotData.xMax = Number.isFinite(max) ? max : 10;
  lastPlotData.errorMsg = null;

  if (!expr) {
    lastPlotData.points = null;
    lastPlotData.errorMsg = 'Enter a mathematical function above and click Plot.';
    renderCanvas();
    if (statusEl) statusEl.textContent = 'Enter an expression to plot.';
    return;
  }

  if (!Number.isFinite(min) || !Number.isFinite(max) || min >= max) {
    lastPlotData.points = null;
    lastPlotData.errorMsg = 'Min range value must be strictly less than Max.';
    renderCanvas();
    if (statusEl) statusEl.textContent = 'Min must be less than max.';
    return;
  }

  const fixedVars = { ...activeWorkspaceVariables() };
  delete fixedVars[variable.toLowerCase()];

  const applyPoints = (pts, sourceDesc) => {
    lastPlotData.points = pts;
    const defined = pts.filter((p) => p.y !== null && Number.isFinite(p.y));
    if (defined.length === 0) {
      lastPlotData.yMin = -2;
      lastPlotData.yMax = 2;
      lastPlotData.errorMsg = 'No defined real values found in this range.';
    } else {
      const ys = defined.map((p) => p.y);
      let yMin = Math.min(...ys), yMax = Math.max(...ys);
      if (yMin === yMax) { yMin -= 1; yMax += 1; }
      const yPad = Math.max((yMax - yMin) * 0.1, 0.2);
      lastPlotData.yMin = yMin - yPad;
      lastPlotData.yMax = yMax + yPad;
      lastPlotData.errorMsg = null;
    }
    renderCanvas();
    if (statusEl) statusEl.textContent = sourceDesc;
  };

  if (state.online) {
    try {
      const res = await LocalApi.graph({
        expression: expr,
        variable,
        min,
        max,
        samples: 300,
        variables: fixedVars,
        workspaceId: state.activeWorkspaceId || undefined,
        angleMode: state.angleMode,
      });
      const points = res.points.map((p) => ({
        x: Number(p.x),
        y: p.y === null ? null : Number(p.y),
      }));
      applyPoints(points, `Plotted ${points.length} points via Spring Boot backend engine.`);
      return;
    } catch (err) {
      if (statusEl) statusEl.textContent = `Backend graphing notice (${err.message}); falling back to local engine...`;
    }
  }

  try {
    const points = sampleFunction(expr, variable, min, max, 300, {
      variables: fixedVars,
      angleMode: state.angleMode,
    });
    applyPoints(points, 'Plotted using client-side offline calculation engine.');
  } catch (err) {
    lastPlotData.points = null;
    lastPlotData.errorMsg = err.message || 'Could not evaluate expression.';
    renderCanvas();
    if (statusEl) statusEl.textContent = err.message || 'Plotting failed.';
  }
}

export function refreshGraphView() {
  requestAnimationFrame(() => {
    if (!lastPlotData.points) {
      plot();
    } else {
      renderCanvas();
    }
  });
}

export function initGraphView() {
  const plotBtn = document.getElementById('graph-plot-btn');
  if (plotBtn) {
    plotBtn.addEventListener('click', plot);
  }

  const inputs = ['graph-expr', 'graph-var', 'graph-min', 'graph-max'];
  inputs.forEach((id) => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          plot();
        }
      });
    }
  });

  const canvas = document.getElementById('graph-canvas');
  if (canvas) {
    canvas.addEventListener('mousemove', (e) => {
      const rect = canvas.getBoundingClientRect();
      hoverCoord = {
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      };
      renderCanvas();
    });

    canvas.addEventListener('mouseleave', () => {
      hoverCoord = null;
      renderCanvas();
    });

    if (window.ResizeObserver && canvas.parentElement) {
      const ro = new ResizeObserver(() => {
        renderCanvas();
      });
      ro.observe(canvas.parentElement);
    }
  }

  window.addEventListener('resize', () => {
    renderCanvas();
  });

  // Initial plot on load
  plot();
}
