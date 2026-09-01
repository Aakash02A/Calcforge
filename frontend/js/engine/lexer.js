export class EngineError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

export function tokenize(source) {
  const tokens = [];
  let i = 0;
  const isDigit = (c) => c >= '0' && c <= '9';
  const isIdentStart = (c) => /[A-Za-z_\u03c0]/.test(c);
  const isIdentPart = (c) => /[A-Za-z0-9_]/.test(c);

  while (i < source.length) {
    const c = source[i];
    if (/\s/.test(c)) {
      i++;
      continue;
    }

    if (isDigit(c) || c === '.') {
      let start = i;
      let dotCount = 0;
      let sawDigit = false;
      while (i < source.length && (isDigit(source[i]) || source[i] === '.')) {
        if (source[i] === '.') dotCount++;
        else sawDigit = true;
        i++;
      }
      if (dotCount > 1 || !sawDigit) {
        throw new EngineError('SYNTAX_ERROR', `Malformed number at position ${start}`);
      }
      if (i < source.length && (source[i] === 'e' || source[i] === 'E')) {
        let look = i + 1;
        if (look < source.length && (source[look] === '+' || source[look] === '-')) look++;
        if (look < source.length && isDigit(source[look])) {
          i++;
          if (source[i] === '+' || source[i] === '-') i++;
          while (i < source.length && isDigit(source[i])) i++;
        }
      }

      const numStr = source.slice(start, i);

      if (i < source.length && source[i] === '[') {
        const unitStart = i + 1;
        const closeIdx = source.indexOf(']', unitStart);
        if (closeIdx === -1) {
          throw new EngineError('SYNTAX_ERROR', `Unclosed unit bracket at position ${i}`);
        }
        const unitStr = source.slice(unitStart, closeIdx).trim();
        if (unitStr.length === 0) {
          throw new EngineError('SYNTAX_ERROR', `Empty unit brackets at position ${i}`);
        }
        tokens.push({
          type: 'NUMBER_WITH_UNIT',
          text: source.slice(start, closeIdx + 1),
          value: numStr,
          unit: unitStr,
        });
        i = closeIdx + 1;
        continue;
      }

      tokens.push({
        type: 'NUMBER',
        text: numStr,
        value: numStr,
      });
      continue;
    }

    if (isIdentStart(c)) {
      let start = i;
      while (i < source.length && isIdentPart(source[i])) i++;
      tokens.push({ type: 'IDENTIFIER', text: source.slice(start, i) });
      continue;
    }

    const single = {
      '+': 'PLUS',
      '-': 'MINUS',
      '\u2212': 'MINUS',
      '*': 'STAR',
      '\u00D7': 'STAR',
      '/': 'SLASH',
      '\u00F7': 'SLASH',
      '%': 'PERCENT',
      '^': 'CARET',
      '!': 'BANG',
      '(': 'LPAREN',
      ')': 'RPAREN',
      ',': 'COMMA',
      '=': 'EQUALS',
    };

    if (single[c]) {
      tokens.push({ type: single[c], text: c });
      i++;
      continue;
    }

    throw new EngineError('SYNTAX_ERROR', `Unexpected character '${c}' at position ${i}`);
  }

  tokens.push({ type: 'EOF', text: '' });
  return tokens;
}

export function validateUnitTokenization(expr = 'mass = 50[kg] * 9.81[m/s^2]') {
  const tokens = tokenize(expr);
  const unitsOnly = tokens.filter((t) => t.type === 'NUMBER_WITH_UNIT');
  return {
    input: expr,
    tokens,
    extractedUnits: unitsOnly.map((t) => ({ value: t.value, unit: t.unit })),
    valid:
      unitsOnly.length === 2 &&
      unitsOnly[0].value === '50' &&
      unitsOnly[0].unit === 'kg' &&
      unitsOnly[1].value === '9.81' &&
      unitsOnly[1].unit === 'm/s^2',
  };
}
