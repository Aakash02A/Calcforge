// A pure-JS mirror of the backend's expression engine (com.calcforge.engine.*), used ONLY
// when the backend is unreachable, so basic/scientific calculation never fully breaks even
// with no server running at all. It intentionally supports a smaller function set and uses
// standard IEEE-754 double precision rather than arbitrary precision - the backend remains
// the source of truth for full precision, unit conversion, finance, persistence, etc. This
// trade-off is surfaced in the UI (an "offline mode - reduced precision" notice), never
// silently.
//
// The trail objects produced here intentionally match the shape of the backend's
// CalculationTrailDto ({ steps: [{stage, title, expression, value, note}] }) so the exact
// same rendering code in views/calculator.js works for both online and offline results.

const FUNCTIONS_1ARG = new Set([
  'sin', 'cos', 'tan', 'asin', 'acos', 'atan', 'sinh', 'cosh', 'tanh',
  'sqrt', 'cbrt', 'ln', 'log', 'log10', 'log2', 'exp', 'abs', 'floor', 'ceil', 'round', 'sign', 'fact',
]);
const FUNCTIONS_2ARG = new Set(['pow', 'mod', 'gcd', 'lcm', 'ncr', 'npr', 'hypot', 'root']);
const FUNCTIONS_VARIADIC = new Set(['min', 'max', 'avg', 'mean', 'sum']);

const CONSTANTS = {
  pi: Math.PI, '\u03c0': Math.PI, e: Math.E, tau: 2 * Math.PI, phi: (1 + Math.sqrt(5)) / 2,
};

function isKnownFunction(name) {
  const n = name.toLowerCase();
  return FUNCTIONS_1ARG.has(n) || FUNCTIONS_2ARG.has(n) || FUNCTIONS_VARIADIC.has(n);
}

// ---------------------------------------------------------------- lexer

function tokenize(source) {
  const tokens = [];
  let i = 0;
  const isDigit = (c) => c >= '0' && c <= '9';
  const isIdentStart = (c) => /[A-Za-z_\u03c0]/.test(c);
  const isIdentPart = (c) => /[A-Za-z0-9_]/.test(c);

  while (i < source.length) {
    const c = source[i];
    if (/\s/.test(c)) { i++; continue; }

    if (isDigit(c) || c === '.') {
      let start = i, dotCount = 0, sawDigit = false;
      while (i < source.length && (isDigit(source[i]) || source[i] === '.')) {
        if (source[i] === '.') dotCount++; else sawDigit = true;
        i++;
      }
      if (dotCount > 1) throw new EngineError('SYNTAX_ERROR', `Malformed number at position ${start}`);
      if (!sawDigit) throw new EngineError('SYNTAX_ERROR', `Malformed number at position ${start}`);
      if (i < source.length && (source[i] === 'e' || source[i] === 'E')) {
        let look = i + 1;
        if (look < source.length && (source[look] === '+' || source[look] === '-')) look++;
        if (look < source.length && isDigit(source[look])) {
          i++;
          if (source[i] === '+' || source[i] === '-') i++;
          while (i < source.length && isDigit(source[i])) i++;
        }
      }
      tokens.push({ type: 'NUMBER', text: source.slice(start, i) });
      continue;
    }

    if (isIdentStart(c)) {
      let start = i;
      while (i < source.length && isIdentPart(source[i])) i++;
      tokens.push({ type: 'IDENTIFIER', text: source.slice(start, i) });
      continue;
    }

    const single = { '+': 'PLUS', '-': 'MINUS', '\u2212': 'MINUS', '*': 'STAR', '\u00D7': 'STAR',
      '/': 'SLASH', '\u00F7': 'SLASH', '%': 'PERCENT', '^': 'CARET', '!': 'BANG',
      '(': 'LPAREN', ')': 'RPAREN', ',': 'COMMA' };
    if (single[c]) { tokens.push({ type: single[c], text: c }); i++; continue; }

    throw new EngineError('SYNTAX_ERROR', `Unexpected character '${c}' at position ${i}`);
  }
  tokens.push({ type: 'EOF', text: '' });
  return tokens;
}

// ---------------------------------------------------------------- parser (recursive descent, mirrors Parser.java)

class TokenStream {
  constructor(tokens) { this.tokens = tokens; this.pos = 0; }
  peek() { return this.tokens[this.pos]; }
  check(type) { return this.peek().type === type; }
  advance() { const t = this.tokens[this.pos]; if (t.type !== 'EOF') this.pos++; return t; }
  consume(type, message) {
    if (this.check(type)) return this.advance();
    throw new EngineError('UNEXPECTED_TOKEN', `${message} (found '${this.peek().text}')`);
  }
}

export class EngineError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

export function parse(source) {
  const tokens = tokenize(source);
  const ts = new TokenStream(tokens);
  if (ts.check('EOF')) throw new EngineError('EMPTY_EXPRESSION', 'Expression is empty');
  const ast = expression(ts);
  if (!ts.check('EOF')) throw new EngineError('UNEXPECTED_TOKEN', `Unexpected '${ts.peek().text}'`);
  return ast;
}

function expression(ts) {
  let node = term(ts);
  while (ts.check('PLUS') || ts.check('MINUS')) {
    const op = ts.advance().type === 'PLUS' ? '+' : '-';
    node = { type: 'Binary', op, left: node, right: term(ts) };
  }
  return node;
}

function startsImplicitFactor(ts) {
  return ts.check('NUMBER') || ts.check('IDENTIFIER') || ts.check('LPAREN');
}

function term(ts) {
  let node = unary(ts);
  while (true) {
    if (ts.check('STAR')) { ts.advance(); node = { type: 'Binary', op: '*', left: node, right: unary(ts) }; }
    else if (ts.check('SLASH')) { ts.advance(); node = { type: 'Binary', op: '/', left: node, right: unary(ts) }; }
    else if (startsImplicitFactor(ts)) { node = { type: 'Binary', op: '*', left: node, right: unary(ts) }; }
    else break;
  }
  return node;
}

function unary(ts) {
  if (ts.check('PLUS') || ts.check('MINUS')) {
    const op = ts.advance().type === 'PLUS' ? '+' : '-';
    return { type: 'Unary', op, operand: unary(ts) };
  }
  return power(ts);
}

function power(ts) {
  const base = postfix(ts);
  if (ts.check('CARET')) { ts.advance(); return { type: 'Binary', op: '^', left: base, right: unary(ts) }; }
  return base;
}

function postfix(ts) {
  let node = primary(ts);
  while (true) {
    if (ts.check('BANG')) { ts.advance(); node = { type: 'Factorial', operand: node }; }
    else if (ts.check('PERCENT')) { ts.advance(); node = { type: 'Percent', operand: node }; }
    else break;
  }
  return node;
}

function primary(ts) {
  if (ts.check('NUMBER')) {
    const t = ts.advance();
    const value = Number(t.text);
    if (!Number.isFinite(value)) throw new EngineError('SYNTAX_ERROR', `Malformed number '${t.text}'`);
    return { type: 'Number', value };
  }
  if (ts.check('IDENTIFIER')) {
    const t = ts.advance();
    const name = t.text;
    if (ts.check('LPAREN')) {
      ts.advance();
      const args = [];
      if (!ts.check('RPAREN')) {
        args.push(expression(ts));
        while (ts.check('COMMA')) { ts.advance(); args.push(expression(ts)); }
      }
      ts.consume('RPAREN', `Missing closing ')' for '${name}(...)'`);
      return { type: 'Call', name: name.toLowerCase(), args };
    }
    return { type: 'Variable', name };
  }
  if (ts.check('LPAREN')) {
    ts.advance();
    const inner = expression(ts);
    ts.consume('RPAREN', "Missing closing ')'");
    return inner;
  }
  const bad = ts.peek();
  if (bad.type === 'EOF') throw new EngineError('UNBALANCED_PARENTHESES', 'Expression ended unexpectedly');
  throw new EngineError('UNEXPECTED_TOKEN', `Unexpected '${bad.text}'`);
}

// ---------------------------------------------------------------- formatting

export function formatNumber(value) {
  if (!Number.isFinite(value)) return String(value);
  if (Number.isInteger(value) && Math.abs(value) < 1e15) return String(value);
  // Round to 14 significant digits to hide double-precision floating point noise
  // (e.g. 0.1 + 0.2 -> 0.30000000000000004 becomes 0.3), then trim trailing zeros.
  let rounded = Number(value.toPrecision(14));
  return String(rounded);
}

function render(node) {
  const wrap = (n) => (n.type === 'Binary' || n.type === 'Unary') ? `(${render(n)})` : render(n);
  switch (node.type) {
    case 'Number': return formatNumber(node.value);
    case 'Variable': return node.name;
    case 'Unary': return node.op + wrap(node.operand);
    case 'Percent': return wrap(node.operand) + '%';
    case 'Factorial': return wrap(node.operand) + '!';
    case 'Call': return `${node.name}(${node.args.map(render).join(', ')})`;
    case 'Binary': return `${wrap(node.left)} ${node.op} ${wrap(node.right)}`;
    default: return '?';
  }
}

// ---------------------------------------------------------------- evaluator

function toRadians(value, mode) {
  if (mode === 'RADIANS') return value;
  if (mode === 'GRADIANS') return value * (Math.PI / 200);
  return (value * Math.PI) / 180; // DEGREES
}
function fromRadians(value, mode) {
  if (mode === 'RADIANS') return value;
  if (mode === 'GRADIANS') return value * (200 / Math.PI);
  return (value * 180) / Math.PI;
}

function factorial(n) {
  if (!Number.isInteger(n) || n < 0) throw new EngineError('DOMAIN_ERROR', 'Factorial is only defined for non-negative whole numbers');
  if (n > 170) throw new EngineError('LIMIT_EXCEEDED', 'Factorial argument too large for offline mode');
  let r = 1;
  for (let i = 2; i <= n; i++) r *= i;
  return r;
}

function gcd(a, b) { a = Math.abs(a); b = Math.abs(b); while (b) { [a, b] = [b, a % b]; } return a; }

function callFunction(name, args, mode) {
  const a = args[0];
  const oneArg = () => { if (args.length !== 1) throw new EngineError('WRONG_ARGUMENT_COUNT', `${name}() expects 1 argument`); };
  const twoArg = () => { if (args.length !== 2) throw new EngineError('WRONG_ARGUMENT_COUNT', `${name}() expects 2 arguments`); };

  switch (name) {
    case 'sin': oneArg(); return Math.sin(toRadians(a, mode));
    case 'cos': oneArg(); return Math.cos(toRadians(a, mode));
    case 'tan': oneArg(); return Math.tan(toRadians(a, mode));
    case 'asin': oneArg(); if (a < -1 || a > 1) throw new EngineError('DOMAIN_ERROR', 'asin is only defined for -1..1'); return fromRadians(Math.asin(a), mode);
    case 'acos': oneArg(); if (a < -1 || a > 1) throw new EngineError('DOMAIN_ERROR', 'acos is only defined for -1..1'); return fromRadians(Math.acos(a), mode);
    case 'atan': oneArg(); return fromRadians(Math.atan(a), mode);
    case 'sinh': oneArg(); return Math.sinh(a);
    case 'cosh': oneArg(); return Math.cosh(a);
    case 'tanh': oneArg(); return Math.tanh(a);
    case 'sqrt': oneArg(); if (a < 0) throw new EngineError('DOMAIN_ERROR', 'sqrt is not defined for negative numbers'); return Math.sqrt(a);
    case 'cbrt': oneArg(); return Math.cbrt(a);
    case 'ln': oneArg(); if (a <= 0) throw new EngineError('DOMAIN_ERROR', 'ln is only defined for x > 0'); return Math.log(a);
    case 'log': case 'log10': oneArg(); if (a <= 0) throw new EngineError('DOMAIN_ERROR', 'log is only defined for x > 0'); return Math.log10(a);
    case 'log2': oneArg(); if (a <= 0) throw new EngineError('DOMAIN_ERROR', 'log2 is only defined for x > 0'); return Math.log2(a);
    case 'exp': oneArg(); return Math.exp(a);
    case 'abs': oneArg(); return Math.abs(a);
    case 'floor': oneArg(); return Math.floor(a);
    case 'ceil': oneArg(); return Math.ceil(a);
    case 'round': oneArg(); return Math.round(a);
    case 'sign': oneArg(); return Math.sign(a);
    case 'fact': oneArg(); return factorial(a);
    case 'pow': twoArg(); return Math.pow(args[0], args[1]);
    case 'root': { twoArg(); const [x, n] = args; if (n === 0) throw new EngineError('DOMAIN_ERROR', 'Cannot take a 0th root');
      if (x < 0) { if (!(Number.isInteger(n) && Math.abs(n % 2) === 1)) throw new EngineError('DOMAIN_ERROR', 'Even root of a negative number is not supported'); return -Math.pow(-x, 1 / n); }
      return Math.pow(x, 1 / n); }
    case 'mod': { twoArg(); const [x, y] = args; if (y === 0) throw new EngineError('DOMAIN_ERROR', 'Division by zero in mod(a,b)'); const r = x % y; return (r !== 0 && Math.sign(r) !== Math.sign(y)) ? r + y : r; }
    case 'gcd': twoArg(); return gcd(args[0], args[1]);
    case 'lcm': { twoArg(); const [x, y] = args; if (x === 0 || y === 0) return 0; return Math.abs((x / gcd(x, y)) * y); }
    case 'ncr': { twoArg(); const [n, r] = args; return factorial(n) / (factorial(r) * factorial(n - r)); }
    case 'npr': { twoArg(); const [n, r] = args; return factorial(n) / factorial(n - r); }
    case 'hypot': twoArg(); return Math.hypot(args[0], args[1]);
    case 'min': return Math.min(...args);
    case 'max': return Math.max(...args);
    case 'sum': return args.reduce((s, v) => s + v, 0);
    case 'avg': case 'mean': return args.reduce((s, v) => s + v, 0) / args.length;
    default: throw new EngineError('UNKNOWN_FUNCTION', `Unknown function '${name}' (offline mode supports a smaller function set)`);
  }
}

function evalNode(node, ctx) {
  switch (node.type) {
    case 'Number': return node.value;
    case 'Variable': {
      const lower = node.name.toLowerCase();
      if (lower in CONSTANTS) return CONSTANTS[lower];
      if (lower in ctx.variables) return ctx.variables[lower];
      throw new EngineError('UNKNOWN_VARIABLE', `Unknown variable '${node.name}'`);
    }
    case 'Unary': {
      const v = evalNode(node.operand, ctx);
      const result = node.op === '-' ? -v : v;
      ctx.trail.push(step('Negate', `${node.op}(${formatNumber(v)})`, result));
      return result;
    }
    case 'Percent': {
      const v = evalNode(node.operand, ctx);
      const result = v / 100;
      ctx.trail.push(step('Percent (\u00f7 100)', `${formatNumber(v)}%`, result));
      return result;
    }
    case 'Factorial': {
      const v = evalNode(node.operand, ctx);
      const result = factorial(v);
      ctx.trail.push(step('Factorial', `${formatNumber(v)}!`, result));
      return result;
    }
    case 'Call': {
      const args = node.args.map((a) => evalNode(a, ctx));
      const nameLower = node.name.toLowerCase();
      if (isKnownFunction(nameLower)) {
        const result = callFunction(nameLower, args, ctx.angleMode);
        ctx.trail.push(step(`Apply ${node.name}()`, `${node.name}(${args.map(formatNumber).join(', ')})`, result));
        return result;
      }
      if (args.length === 1) {
        if (nameLower in CONSTANTS || nameLower in ctx.variables) {
          const varValue = nameLower in CONSTANTS ? CONSTANTS[nameLower] : ctx.variables[nameLower];
          const result = varValue * args[0];
          ctx.trail.push(step('Multiply (implicit)', `${node.name}(${formatNumber(args[0])})`, result));
          return result;
        }
      }
      throw new EngineError('UNKNOWN_FUNCTION', `Unknown function '${node.name}'`);
    }
    case 'Binary': {
      const left = evalNode(node.left, ctx);
      const right = evalNode(node.right, ctx);
      let result;
      switch (node.op) {
        case '+': result = left + right; break;
        case '-': result = left - right; break;
        case '*': result = left * right; break;
        case '/': if (right === 0) throw new EngineError('DIVISION_BY_ZERO', 'Division by zero'); result = left / right; break;
        case '^': result = Math.pow(left, right); break;
        default: throw new EngineError('SYNTAX_ERROR', `Unknown operator ${node.op}`);
      }
      const titles = { '+': 'Add', '-': 'Subtract', '*': 'Multiply', '/': 'Divide', '^': 'Raise to power' };
      ctx.trail.push(step(titles[node.op], `${formatNumber(left)} ${node.op} ${formatNumber(right)}`, result));
      return result;
    }
    default:
      throw new EngineError('SYNTAX_ERROR', `Unhandled node ${node.type}`);
  }
}

function step(title, expr, value) {
  return { stage: 'COMPUTATION', title, expression: expr, value: formatNumber(value), note: null };
}

/**
 * Evaluates an expression fully offline. Returns { result, trail } where trail matches
 * the backend's CalculationTrailDto shape (see docs/CALCULATION_TRAIL.md).
 */
export function evaluateOffline(expression, { variables = {}, angleMode = 'DEGREES', precision = 14 } = {}) {
  const ast = parse(expression);
  const lowerVars = {};
  Object.entries(variables).forEach(([k, v]) => { lowerVars[k.toLowerCase()] = v; });
  const ctx = { variables: lowerVars, angleMode, trail: [] };
  const result = evalNode(ast, ctx);

  const steps = [];
  steps.push({ stage: 'INPUT', title: 'Input', expression, value: null, note: null });
  steps.push({ stage: 'ASSUMPTIONS', title: 'Angle mode', expression: null, value: angleMode, note: 'Offline mode (client-side engine)' });
  steps.push({ stage: 'ASSUMPTIONS', title: 'Precision', expression: null, value: '~14-15 significant digits (double precision)', note: 'Full arbitrary precision requires the backend' });
  steps.push({ stage: 'FORMULA', title: 'Normalized formula', expression: render(ast), value: null, note: null });
  if (ctx.trail.length === 0) {
    steps.push({ stage: 'COMPUTATION', title: 'Direct value', expression, value: formatNumber(result), note: null });
  } else {
    steps.push(...ctx.trail);
  }
  steps.push({ stage: 'RESULT', title: 'Result', expression: null, value: formatNumber(result), note: null });

  return { result, trail: { steps } };
}

/**
 * Samples a function of one variable across [min, max] for offline graphing. Parses the
 * expression once and reuses the AST for every sample, unlike evaluateOffline (which is
 * optimized for trail clarity on a single calculation, not throughput).
 */
export function sampleFunction(expression, variableName, min, max, samples, { variables = {}, angleMode = 'DEGREES' } = {}) {
  const ast = parse(expression);
  const lowerVars = {};
  Object.entries(variables).forEach(([k, v]) => { lowerVars[k.toLowerCase()] = v; });

  const points = [];
  const step = (max - min) / (samples - 1);
  for (let i = 0; i < samples; i++) {
    const x = i === samples - 1 ? max : min + step * i;
    const ctx = { variables: { ...lowerVars, [variableName.toLowerCase()]: x }, angleMode, trail: [] };
    let y = null;
    try {
      y = evalNode(ast, ctx);
      if (!Number.isFinite(y)) y = null;
    } catch {
      y = null;
    }
    points.push({ x, y });
  }
  return points;
}
