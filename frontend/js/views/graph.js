import { state } from '../state.js';
import { LocalApi } from '../api.js';
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
  anomalies: [],
  steepRegions: [],
};

let hoverCoord = null;
let isLockedToPlot = false;
let activeAnomalyHit = null;

function formatCoord(val) {
  if (val === null || val === undefined || !Number.isFinite(val)) return '—';
  if (Math.abs(val) < 1e-9) return '0';
  if (Math.abs(val) >= 10000 || (Math.abs(val) < 0.001 && val !== 0)) {
    return Number(val.toPrecision(4)).toString();
  }
  return Number(val.toFixed(3)).toString();
}

function getCurvePointAtMathX(mathX, points) {
  if (!points || points.length === 0) return null;
  const valid = points.filter((p) => p.y !== null && Number.isFinite(p.y));
  if (valid.length === 0) return null;

  if (mathX <= valid[0].x) return valid[0];
  if (mathX >= valid[valid.length - 1].x) return valid[valid.length - 1];

  for (let i = 0; i < valid.length - 1; i++) {
    const p1 = valid[i];
    const p2 = valid[i + 1];
    if (mathX >= p1.x && mathX <= p2.x) {
      const span = p2.x - p1.x;
      if (Math.abs(span) < 1e-12) return p1;
      const t = (mathX - p1.x) / span;
      return {
        x: mathX,
        y: p1.y + t * (p2.y - p1.y),
      };
    }
  }
  return valid[0];
}

function getDistanceToCurve(px, py, points, toPx, toPy, toMathX) {
  if (!points || points.length === 0) return { distance: Infinity, point: null, pixelX: 0, pixelY: 0 };
  const mathX = toMathX(px);
  const cp = getCurvePointAtMathX(mathX, points);
  if (!cp) return { distance: Infinity, point: null, pixelX: 0, pixelY: 0 };
  const cpx = toPx(cp.x);
  const cpy = toPy(cp.y);
  const dist = Math.hypot(px - cpx, py - cpy);
  return { distance: dist, point: cp, pixelX: cpx, pixelY: cpy };
}

function detectLocalAnomalies(points, xMin, xMax) {
  const anomalies = [];
  if (!points || points.length < 2) return anomalies;

  for (let i = 0; i < points.length - 1; i++) {
    const p1 = points[i];
    const p2 = points[i + 1];

    if (p1.y !== null && p2.y !== null) {
      const dx = p2.x - p1.x;
      if (Math.abs(dx) > 1e-12) {
        const slope = (p2.y - p1.y) / dx;
        const signFlip = (p1.y > 0 && p2.y < 0) || (p1.y < 0 && p2.y > 0);
        if (Math.abs(slope) > 40 && signFlip && (Math.abs(p1.y) > 1.2 || Math.abs(p2.y) > 1.2)) {
          const asympX = (p1.x + p2.x) / 2;
          anomalies.push({
            x: asympX,
            type: 'ASYMPTOTE',
            description: 'Vertical Asymptote (Sign Inversion)',
            rootCause: `Massive slope (${slope.toFixed(1)}) with diverging limits`,
            gradient: slope,
            leftValue: p1.y,
            rightValue: p2.y,
            leftBound: p1.x,
            rightBound: p2.x,
          });
        }
      }
    } else if (p1.y === null || p2.y === null) {
      const prevValid = i > 0 ? points[i - 1] : null;
      const nextValid = i + 2 < points.length ? points[i + 2] : null;

      if (prevValid && nextValid && prevValid.y !== null && nextValid.y !== null) {
        const signFlip = (prevValid.y > 0 && nextValid.y < 0) || (prevValid.y < 0 && nextValid.y > 0);
        if (signFlip && (Math.abs(prevValid.y) > 1.2 || Math.abs(nextValid.y) > 1.2)) {
          const nullX = p1.y === null ? p1.x : p2.x;
          if (!anomalies.some((a) => Math.abs(a.x - nullX) < 1e-4)) {
            anomalies.push({
              x: nullX,
              type: 'ASYMPTOTE',
              description: 'Vertical Asymptote (Division by Zero Pole)',
              rootCause: 'Values diverge to opposite infinite limits around undefined pole',
              leftValue: prevValid.y,
              rightValue: nextValid.y,
              leftBound: prevValid.x,
              rightBound: nextValid.x,
            });
            continue;
          }
        }
      }

      const holeX = p1.y === null ? p1.x : p2.x;
      if (!anomalies.some((a) => Math.abs(a.x - holeX) < 1e-4)) {
        anomalies.push({
          x: holeX,
          type: 'HOLE',
          description: 'Domain Discontinuity / Singularity',
          rootCause: 'Function transitioned into undefined non-real domain',
          leftBound: p1.x,
          rightBound: p2.x,
        });
      }
    }
  }

  return anomalies;
}

function checkAnomalyHit(mouseX, mouseY, anomalies, points, toPx, toPy, marginT, marginB, plotH) {
  if (!anomalies || anomalies.length === 0) return null;
  const radius = 8;

  for (const anomaly of anomalies) {
    const numX = Number(anomaly.x);
    if (!Number.isFinite(numX)) continue;
    const px = toPx(numX);

    const isAsymptote = anomaly.type === 'ASYMPTOTE';
    const isHole = anomaly.type === 'HOLE';
    const isPrecision = anomaly.type === 'PRECISION_LOSS';

    if (isAsymptote) {
      const dotY = marginT + 22;
      const dotDist = Math.hypot(mouseX - px, mouseY - dotY);
      const lineDist = Math.abs(mouseX - px);

      if (dotDist <= radius || (lineDist <= radius && mouseY >= marginT && mouseY <= marginT + plotH)) {
        return {
          anomaly,
          pixelX: px,
          pixelY: dotDist <= radius ? dotY : mouseY,
          hitType: 'ASYMPTOTE',
        };
      }
    } else if (isHole) {
      let targetPy = toPy(0);
      if (anomaly.y !== null && anomaly.y !== undefined && Number.isFinite(Number(anomaly.y))) {
        targetPy = toPy(Number(anomaly.y));
      } else {
        const cp = getCurvePointAtMathX(numX, points);
        if (cp && cp.y !== null && Number.isFinite(cp.y)) {
          targetPy = toPy(cp.y);
        }
      }
      targetPy = Math.max(marginT + 10, Math.min(marginT + plotH - 10, targetPy));
      const holeDist = Math.hypot(mouseX - px, mouseY - targetPy);
      const lineDist = Math.abs(mouseX - px);

      if (holeDist <= radius || (lineDist <= radius && mouseY >= marginT && mouseY <= marginT + plotH)) {
        return {
          anomaly,
          pixelX: px,
          pixelY: targetPy,
          hitType: 'HOLE',
        };
      }
    } else if (isPrecision) {
      const lineDist = Math.abs(mouseX - px);
      if (lineDist <= radius && mouseY >= marginT && mouseY <= marginT + plotH) {
        return {
          anomaly,
          pixelX: px,
          pixelY: mouseY,
          hitType: 'PRECISION_LOSS',
        };
      }
    }
  }

  return null;
}

function updateAnomalyPopover(hitResult) {
  let popover = document.getElementById('graph-anomaly-popover');
  if (!popover) {
    const wrap = document.querySelector('.cf-graph-canvas-wrap');
    if (wrap) {
      popover = document.createElement('div');
      popover.id = 'graph-anomaly-popover';
      popover.className = 'cf-anomaly-popover d-none';
      wrap.appendChild(popover);
    }
  }
  if (!popover) return;

  if (!hitResult) {
    popover.classList.add('d-none');
    return;
  }

  const { anomaly, pixelX, pixelY } = hitResult;
  const numX = Number(anomaly.x);
  const formattedX = formatCoord(numX);

  popover.className = 'cf-anomaly-popover';

  let badgeHtml = '';
  let titleHtml = '';
  let explanationHtml = '';
  let detailHtml = '';

  if (anomaly.type === 'ASYMPTOTE') {
    popover.classList.add('popover-asymptote');
    badgeHtml = '<div class="cf-anomaly-badge badge-asymptote">⚠️ Critical Vulnerability</div>';
    titleHtml = `<div class="cf-anomaly-title font-mono">Asymptotic Discontinuity detected at x=${formattedX}</div>`;
    explanationHtml = `<div class="cf-anomaly-explanation">Critical Vulnerability: Asymptotic Discontinuity detected at x=${formattedX}; value approaches infinity.</div>`;
    detailHtml = `<div class="cf-anomaly-detail font-mono">${anomaly.rootCause || 'Denominator approaches zero with diverging limits.'}</div>`;
  } else if (anomaly.type === 'HOLE') {
    popover.classList.add('popover-hole');
    badgeHtml = '<div class="cf-anomaly-badge badge-hole">⭕ Structural Discontinuity</div>';
    titleHtml = `<div class="cf-anomaly-title font-mono">Mathematical Discontinuity / Hole at x=${formattedX}</div>`;
    explanationHtml = `<div class="cf-anomaly-explanation">Structural Discontinuity: Function transitions into undefined or non-real domain at coordinate x=${formattedX}.</div>`;
    detailHtml = `<div class="cf-anomaly-detail font-mono">${anomaly.rootCause || anomaly.description || 'Non-real or division by zero singularity.'}</div>`;
  } else {
    popover.classList.add('popover-precision');
    badgeHtml = '<div class="cf-anomaly-badge badge-precision">⚡ Precision Warning</div>';
    titleHtml = `<div class="cf-anomaly-title font-mono">Numerical Precision Loss at x=${formattedX}</div>`;
    explanationHtml = `<div class="cf-anomaly-explanation">Precision Loss: Extreme high-frequency oscillation exceeds sampling resolution at x=${formattedX}.</div>`;
    detailHtml = `<div class="cf-anomaly-detail font-mono">${anomaly.rootCause || anomaly.description || 'Rapid gradient inversion.'}</div>`;
  }

  popover.innerHTML = `${badgeHtml}${titleHtml}${explanationHtml}${detailHtml}`;

  const canvas = document.getElementById('graph-canvas');
  const canvasW = canvas ? canvas.clientWidth : 900;
  const canvasH = canvas ? canvas.clientHeight : 420;
  const offsetLeft = canvas ? canvas.offsetLeft : 10;
  const offsetTop = canvas ? canvas.offsetTop : 10;

  const popoverW = 340;
  let posX = offsetLeft + pixelX + 16;
  if (pixelX + 16 + popoverW > canvasW - 10) {
    posX = Math.max(offsetLeft + 10, offsetLeft + pixelX - popoverW - 16);
  }

  let posY = offsetTop + Math.max(12, Math.min(canvasH - 120, pixelY - 30));

  popover.style.left = `${posX}px`;
  popover.style.top = `${posY}px`;
  popover.classList.remove('d-none');
}

function renderCanvas() {
  const canvas = document.getElementById('graph-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');

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

  ctx.fillStyle = bgColor;
  ctx.fillRect(0, 0, w, h);

  const { points, variableName, expr, errorMsg, anomalies = [] } = lastPlotData;
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

  const numXSteps = 8;
  const numYSteps = 6;

  ctx.font = '11px ui-monospace, SFMono-Regular, Consolas, monospace';

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

  ctx.strokeStyle = gridColorMajor;
  ctx.lineWidth = 1;
  ctx.strokeRect(marginL, marginT, plotW, plotH);

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

  ctx.fillStyle = isDark ? '#ff8a4c' : '#e8743b';
  ctx.font = 'bold 12px sans-serif';
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  ctx.fillText(`→ ${variableName || 'x'}`, marginL + plotW, marginT + plotH + 22);

  ctx.textAlign = 'left';
  ctx.fillText(expr ? `f(${variableName || 'x'}) = ${expr}` : '2D Function Grid', marginL, 14);

  if (points && points.length > 0) {
    const defined = points.filter((p) => p.y !== null && Number.isFinite(p.y));
    if (defined.length > 0) {
      ctx.save();
      ctx.beginPath();
      ctx.rect(marginL, marginT, plotW, plotH);
      ctx.clip();

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

  if (anomalies && anomalies.length > 0) {
    ctx.save();
    ctx.beginPath();
    ctx.rect(marginL, marginT, plotW, plotH);
    ctx.clip();

    anomalies.forEach((anomaly) => {
      const numX = Number(anomaly.x);
      if (!Number.isFinite(numX) || numX < xMin || numX > xMax) return;

      const px = toPx(numX);
      const isAsymptote = anomaly.type === 'ASYMPTOTE';
      const isHole = anomaly.type === 'HOLE';
      const isPrecision = anomaly.type === 'PRECISION_LOSS';

      const isHit = activeAnomalyHit && activeAnomalyHit.anomaly === anomaly;

      if (isAsymptote) {
        ctx.save();
        ctx.setLineDash([5, 4]);
        ctx.strokeStyle = isHit ? '#ff4d6d' : 'rgba(239, 68, 68, 0.85)';
        ctx.lineWidth = isHit ? 2.5 : 1.6;
        ctx.beginPath();
        ctx.moveTo(px, marginT);
        ctx.lineTo(px, marginT + plotH);
        ctx.stroke();

        const glowGrad = ctx.createRadialGradient(px, marginT + 22, 2, px, marginT + 22, 18);
        glowGrad.addColorStop(0, isHit ? 'rgba(255, 77, 109, 0.95)' : 'rgba(239, 68, 68, 0.85)');
        glowGrad.addColorStop(1, 'rgba(239, 68, 68, 0)');
        ctx.fillStyle = glowGrad;
        ctx.beginPath();
        ctx.arc(px, marginT + 22, 18, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = isHit ? '#ff4d6d' : '#ef4444';
        ctx.beginPath();
        ctx.arc(px, marginT + 22, isHit ? 6.5 : 4.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1.5;
        ctx.stroke();
        ctx.restore();
      } else if (isHole) {
        ctx.save();
        let targetPy = toPy(0);
        if (anomaly.y !== null && anomaly.y !== undefined && Number.isFinite(Number(anomaly.y))) {
          targetPy = toPy(Number(anomaly.y));
        } else {
          const cp = getCurvePointAtMathX(numX, points);
          if (cp && cp.y !== null && Number.isFinite(cp.y)) {
            targetPy = toPy(cp.y);
          }
        }
        targetPy = Math.max(marginT + 10, Math.min(marginT + plotH - 10, targetPy));

        ctx.setLineDash([3, 3]);
        ctx.strokeStyle = isHit ? 'rgba(244, 63, 94, 0.8)' : 'rgba(244, 63, 94, 0.55)';
        ctx.lineWidth = isHit ? 1.8 : 1.2;
        ctx.beginPath();
        ctx.moveTo(px, marginT);
        ctx.lineTo(px, marginT + plotH);
        ctx.stroke();

        const holeGlow = ctx.createRadialGradient(px, targetPy, 2, px, targetPy, 18);
        holeGlow.addColorStop(0, 'rgba(244, 63, 94, 0.9)');
        holeGlow.addColorStop(1, 'rgba(244, 63, 94, 0)');
        ctx.fillStyle = holeGlow;
        ctx.beginPath();
        ctx.arc(px, targetPy, 18, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = isDark ? '#14181f' : '#ffffff';
        ctx.strokeStyle = isHit ? '#fb7185' : '#f43f5e';
        ctx.lineWidth = isHit ? 3.5 : 2.4;
        ctx.beginPath();
        ctx.arc(px, targetPy, isHit ? 7 : 5, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        ctx.restore();
      } else if (isPrecision) {
        ctx.save();
        ctx.fillStyle = isHit ? 'rgba(245, 158, 11, 0.25)' : 'rgba(245, 158, 11, 0.15)';
        ctx.fillRect(px - 8, marginT, 16, plotH);

        ctx.strokeStyle = '#f59e0b';
        ctx.lineWidth = isHit ? 2 : 1.5;
        ctx.setLineDash([2, 2]);
        ctx.beginPath();
        ctx.moveTo(px, marginT);
        ctx.lineTo(px, marginT + plotH);
        ctx.stroke();
        ctx.restore();
      }
    });

    ctx.restore();
  }

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

  if (points && points.length > 0) {
    ctx.save();
    const anomalyCount = anomalies.length;
    let badgeLabel = isLockedToPlot
      ? `🔒 Locked to plot line • Double-click to free roam`
      : `🧭 Free roam mode • Click plot line to lock`;

    if (anomalyCount > 0) {
      badgeLabel = `⚠️ ${anomalyCount} Anomaly Detected • ${badgeLabel}`;
    }

    ctx.font = '600 10.5px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
    const modeBadgeW = ctx.measureText(badgeLabel).width + 20;
    const modeBadgeH = 22;
    const modeBadgeX = marginL + plotW - modeBadgeW - 8;
    const modeBadgeY = marginT + 8;

    ctx.fillStyle = isLockedToPlot
      ? (isDark ? 'rgba(232, 116, 59, 0.22)' : 'rgba(232, 116, 59, 0.15)')
      : (anomalyCount > 0 ? (isDark ? 'rgba(239, 68, 68, 0.18)' : 'rgba(239, 68, 68, 0.12)') : (isDark ? 'rgba(36, 41, 51, 0.85)' : 'rgba(240, 242, 245, 0.90)'));
    ctx.strokeStyle = isLockedToPlot
      ? (isDark ? '#e8743b' : '#d9652c')
      : (anomalyCount > 0 ? '#ef4444' : (isDark ? 'rgba(255, 255, 255, 0.14)' : 'rgba(0, 0, 0, 0.14)'));
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.roundRect(modeBadgeX, modeBadgeY, modeBadgeW, modeBadgeH, 11);
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = isLockedToPlot
      ? (isDark ? '#ff9d6b' : '#c2410c')
      : (anomalyCount > 0 ? (isDark ? '#fca5a5' : '#dc2626') : (isDark ? '#a9b1bc' : '#64748b'));
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText(badgeLabel, modeBadgeX + 10, modeBadgeY + modeBadgeH / 2);
    ctx.restore();
  }

  if (hoverCoord && !activeAnomalyHit && hoverCoord.x >= marginL && hoverCoord.x <= marginL + plotW && hoverCoord.y >= marginT && hoverCoord.y <= marginT + plotH) {
    const mathX = toMathX(hoverCoord.x);
    const mathY = toMathY(hoverCoord.y);

    const curveData = getDistanceToCurve(hoverCoord.x, hoverCoord.y, points, toPx, toPy, toMathX);
    const hasCurve = curveData.point !== null;
    const isNear = hasCurve && curveData.distance <= 32;

    let targetX, targetY, dispX, dispY;

    if (isLockedToPlot && hasCurve) {
      targetX = curveData.pixelX;
      targetY = curveData.pixelY;
      dispX = curveData.point.x;
      dispY = curveData.point.y;
    } else {
      targetX = hoverCoord.x;
      targetY = hoverCoord.y;
      dispX = mathX;
      dispY = mathY;
    }

    ctx.save();
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = isLockedToPlot ? crosshairColor : (isDark ? 'rgba(255, 255, 255, 0.35)' : 'rgba(0, 0, 0, 0.35)');
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(marginL, targetY);
    ctx.lineTo(marginL + plotW, targetY);
    ctx.moveTo(targetX, marginT);
    ctx.lineTo(targetX, marginT + plotH);
    ctx.stroke();
    ctx.restore();

    if (!isLockedToPlot && isNear) {
      ctx.save();
      ctx.strokeStyle = isDark ? 'rgba(69, 196, 176, 0.6)' : 'rgba(13, 148, 136, 0.6)';
      ctx.setLineDash([2, 3]);
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(targetX, targetY);
      ctx.lineTo(curveData.pixelX, curveData.pixelY);
      ctx.stroke();

      ctx.fillStyle = isDark ? '#45c4b0' : '#0d9488';
      ctx.beginPath();
      ctx.arc(curveData.pixelX, curveData.pixelY, 4, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    }

    ctx.save();
    ctx.font = 'bold 10px ui-monospace, SFMono-Regular, Consolas, monospace';

    const xBadgeText = `X: ${formatCoord(dispX)}`;
    const xBadgeW = ctx.measureText(xBadgeText).width + 12;
    const xBadgeH = 18;
    let xBadgeX = targetX - xBadgeW / 2;
    if (xBadgeX < marginL) xBadgeX = marginL;
    if (xBadgeX + xBadgeW > marginL + plotW) xBadgeX = marginL + plotW - xBadgeW;
    const xBadgeY = marginT + plotH + 3;

    ctx.fillStyle = isDark ? '#1f242d' : '#2b303b';
    ctx.strokeStyle = isLockedToPlot ? (isDark ? '#e8743b' : '#e8743b') : (isDark ? '#45c4b0' : '#0d9488');
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.roundRect(xBadgeX, xBadgeY, xBadgeW, xBadgeH, 3);
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(xBadgeText, xBadgeX + xBadgeW / 2, xBadgeY + xBadgeH / 2);

    const yBadgeText = isLockedToPlot ? `f(${variableName || 'x'}): ${formatCoord(dispY)}` : `Y: ${formatCoord(dispY)}`;
    const yBadgeW = ctx.measureText(yBadgeText).width + 12;
    const yBadgeH = 18;
    const yBadgeX = marginL - yBadgeW - 3;
    let yBadgeY = targetY - yBadgeH / 2;
    if (yBadgeY < marginT) yBadgeY = marginT;
    if (yBadgeY + yBadgeH > marginT + plotH) yBadgeY = marginT + plotH - yBadgeH;

    ctx.fillStyle = isDark ? '#1f242d' : '#2b303b';
    ctx.strokeStyle = isLockedToPlot ? (isDark ? '#e8743b' : '#e8743b') : (isDark ? '#45c4b0' : '#0d9488');
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.roundRect(yBadgeX, yBadgeY, yBadgeW, yBadgeH, 3);
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(yBadgeText, yBadgeX + yBadgeW / 2, yBadgeY + yBadgeH / 2);
    ctx.restore();

    ctx.save();
    if (isLockedToPlot) {
      ctx.fillStyle = isDark ? 'rgba(232, 116, 59, 0.25)' : 'rgba(232, 116, 59, 0.2)';
      ctx.beginPath();
      ctx.arc(targetX, targetY, 9, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = isDark ? '#ff8a4c' : '#e8743b';
      ctx.strokeStyle = isDark ? '#14181f' : '#ffffff';
      ctx.lineWidth = 2.5;
      ctx.beginPath();
      ctx.arc(targetX, targetY, 5.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
    } else {
      ctx.strokeStyle = isDark ? '#45c4b0' : '#0d9488';
      ctx.lineWidth = 1.8;
      ctx.beginPath();
      ctx.arc(targetX, targetY, 5, 0, Math.PI * 2);
      ctx.stroke();

      ctx.fillStyle = isDark ? '#45c4b0' : '#0d9488';
      ctx.beginPath();
      ctx.arc(targetX, targetY, 2, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();

    ctx.save();
    let tipPrimary = `(${formatCoord(dispX)}, ${formatCoord(dispY)})`;
    let tipSecondary = null;

    if (isLockedToPlot) {
      tipPrimary = `f(${variableName || 'x'}) = ${formatCoord(dispY)}`;
      tipSecondary = `x = ${formatCoord(dispX)} [LOCKED]`;
    } else if (isNear) {
      tipSecondary = `Click to snap to curve`;
    }

    ctx.font = 'bold 11px ui-monospace, SFMono-Regular, Consolas, monospace';
    const primW = ctx.measureText(tipPrimary).width;
    let secW = 0;
    if (tipSecondary) {
      ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
      secW = ctx.measureText(tipSecondary).width;
    }
    const tipW = Math.max(primW, secW) + 18;
    const tipH = tipSecondary ? 38 : 24;

    let tipX = targetX + 14;
    let tipY = targetY - tipH - 8;
    if (tipX + tipW > marginL + plotW) tipX = targetX - tipW - 14;
    if (tipY < marginT) tipY = targetY + 14;

    ctx.fillStyle = isDark ? '#1a1f29' : '#1f2328';
    ctx.strokeStyle = isLockedToPlot ? (isDark ? '#e8743b' : '#e8743b') : (isDark ? '#3d4452' : '#ddd7c6');
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.roundRect(tipX, tipY, tipW, tipH, 5);
    ctx.fill();
    ctx.stroke();

    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    ctx.font = 'bold 11px ui-monospace, SFMono-Regular, Consolas, monospace';
    ctx.fillStyle = isLockedToPlot ? (isDark ? '#ff9d6b' : '#ffedd5') : '#ffffff';
    ctx.fillText(tipPrimary, tipX + 9, tipY + (tipSecondary ? 6 : 6));

    if (tipSecondary) {
      ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
      ctx.fillStyle = isLockedToPlot ? (isDark ? '#fba779' : '#fdba74') : (isDark ? '#45c4b0' : '#2dd4bf');
      ctx.fillText(tipSecondary, tipX + 9, tipY + 21);
    }
    ctx.restore();
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

  isLockedToPlot = false;
  activeAnomalyHit = null;
  updateAnomalyPopover(null);

  lastPlotData.expr = expr;
  lastPlotData.variableName = variable;
  lastPlotData.xMin = Number.isFinite(min) ? min : -10;
  lastPlotData.xMax = Number.isFinite(max) ? max : 10;
  lastPlotData.errorMsg = null;
  lastPlotData.anomalies = [];
  lastPlotData.steepRegions = [];

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

  const applyPoints = (pts, anomaliesList, steepList, sourceDesc) => {
    lastPlotData.points = pts;
    lastPlotData.anomalies = anomaliesList || [];
    lastPlotData.steepRegions = steepList || [];

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
      const res = await LocalApi.analyzeGraph({
        expression: expr,
        variable,
        startX: min,
        endX: max,
        precision: 15,
        baseSamples: 150,
        subdivisionFactor: 10,
        thresholdPercentage: 10.0,
        variables: fixedVars,
        workspaceId: state.activeWorkspaceId || undefined,
        angleMode: state.angleMode,
      });

      const points = (res.points || []).map((p) => ({
        x: Number(p.x),
        y: p.y === null ? null : Number(p.y),
      }));

      let anomalies = [];
      if (res.anomalies) {
        if (Array.isArray(res.anomalies)) {
          anomalies = res.anomalies;
        } else if (typeof res.anomalies === 'object') {
          anomalies = Object.values(res.anomalies);
        }
      }

      const steepRegions = res.steepRegions || [];
      const anomText = anomalies.length > 0 ? ` • ${anomalies.length} anomaly detected` : '';
      const injectedText = res.injectedPoints > 0 ? ` (${res.injectedPoints} injected high-density points)` : '';

      applyPoints(
        points,
        anomalies,
        steepRegions,
        `Adaptive scan: ${points.length} points${injectedText}${anomText} via Spring Boot backend.`
      );
      return;
    } catch (err) {
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
        const anomalies = detectLocalAnomalies(points, min, max);
        applyPoints(points, anomalies, [], `Plotted ${points.length} points via Spring Boot backend.`);
        return;
      } catch (innerErr) {
        if (statusEl) statusEl.textContent = `Backend notice (${innerErr.message}); falling back to local engine...`;
      }
    }
  }

  try {
    const points = sampleFunction(expr, variable, min, max, 300, {
      variables: fixedVars,
      angleMode: state.angleMode,
    });
    const anomalies = detectLocalAnomalies(points, min, max);
    applyPoints(points, anomalies, [], `Plotted locally (${points.length} points, ${anomalies.length} anomalies detected).`);
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

      const marginL = 56, marginR = 24, marginT = 28, marginB = 36;
      const displayW = rect.width > 0 ? rect.width : 900;
      const displayH = 420;
      const plotW = Math.max(10, displayW - marginL - marginR);
      const plotH = Math.max(10, displayH - marginT - marginB);

      let { xMin, xMax, yMin, yMax, points, anomalies } = lastPlotData;
      if (xMin >= xMax) { xMin = -10; xMax = 10; }
      if (yMin >= yMax || !Number.isFinite(yMin) || !Number.isFinite(yMax)) { yMin = -1.5; yMax = 1.5; }

      const toPx = (x) => marginL + ((x - xMin) / (xMax - xMin || 1)) * plotW;
      const toPy = (y) => marginT + plotH - ((y - yMin) / (yMax - yMin || 1)) * plotH;
      const toMathX = (px) => xMin + ((px - marginL) / plotW) * (xMax - xMin);

      const hit = checkAnomalyHit(hoverCoord.x, hoverCoord.y, anomalies, points, toPx, toPy, marginT, marginB, plotH);
      activeAnomalyHit = hit;

      if (hit) {
        canvas.style.cursor = 'pointer';
        updateAnomalyPopover(hit);
      } else {
        updateAnomalyPopover(null);
        if (isLockedToPlot) {
          canvas.style.cursor = 'ew-resize';
        } else {
          const curveData = getDistanceToCurve(hoverCoord.x, hoverCoord.y, points, toPx, toPy, toMathX);
          if (curveData.point && curveData.distance <= 24) {
            canvas.style.cursor = 'pointer';
          } else {
            canvas.style.cursor = 'crosshair';
          }
        }
      }

      renderCanvas();
    });

    canvas.addEventListener('mouseleave', () => {
      hoverCoord = null;
      activeAnomalyHit = null;
      updateAnomalyPopover(null);
      renderCanvas();
    });

    canvas.addEventListener('click', (e) => {
      const rect = canvas.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const clickY = e.clientY - rect.top;

      const marginL = 56, marginR = 24, marginT = 28, marginB = 36;
      const displayW = rect.width > 0 ? rect.width : 900;
      const displayH = 420;
      const plotW = Math.max(10, displayW - marginL - marginR);
      const plotH = Math.max(10, displayH - marginT - marginB);

      let { xMin, xMax, yMin, yMax, points, anomalies } = lastPlotData;
      if (xMin >= xMax) { xMin = -10; xMax = 10; }
      if (yMin >= yMax || !Number.isFinite(yMin) || !Number.isFinite(yMax)) { yMin = -1.5; yMax = 1.5; }

      const toPx = (x) => marginL + ((x - xMin) / (xMax - xMin || 1)) * plotW;
      const toPy = (y) => marginT + plotH - ((y - yMin) / (yMax - yMin || 1)) * plotH;
      const toMathX = (px) => xMin + ((px - marginL) / plotW) * (xMax - xMin);

      const hit = checkAnomalyHit(clickX, clickY, anomalies, points, toPx, toPy, marginT, marginB, plotH);
      if (hit) {
        activeAnomalyHit = hit;
        updateAnomalyPopover(hit);
        return;
      }

      const curveData = getDistanceToCurve(clickX, clickY, points, toPx, toPy, toMathX);
      if (curveData.point && curveData.distance <= 24) {
        isLockedToPlot = true;
        canvas.style.cursor = 'ew-resize';
      } else {
        isLockedToPlot = false;
        canvas.style.cursor = 'crosshair';
      }
      renderCanvas();
    });

    canvas.addEventListener('dblclick', (e) => {
      e.preventDefault();
      isLockedToPlot = false;
      canvas.style.cursor = 'crosshair';
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

  plot();
}
