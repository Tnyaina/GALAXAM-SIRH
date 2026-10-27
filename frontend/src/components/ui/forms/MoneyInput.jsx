import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import Input from './Input';

function normalizeMoneyValue(value, maxDecimals) {
  let raw = String(value ?? '').replace(/[\s\u00a0\u202f]/g, '');
  if (!raw) return '';

  if (raw.includes(',') && raw.includes('.')) {
    raw = raw.replace(/\./g, '').replace(',', '.');
  } else if (raw.includes(',')) {
    raw = raw.replace(',', '.');
  } else if ((raw.match(/\./g) || []).length > 1) {
    raw = raw.replace(/\./g, '');
  }

  raw = raw.replace(/[^\d.-]/g, '');
  const negative = raw.startsWith('-');
  raw = raw.replace(/-/g, '');
  const [integer = '', ...decimalParts] = raw.split('.');
  const decimal = decimalParts.join('').slice(0, maxDecimals);
  return `${negative ? '-' : ''}${integer}${decimalParts.length ? `.${decimal}` : ''}`;
}

function formatMoneyValue(value, maxDecimals) {
  if (value === '' || value === '-' || value.endsWith('.')) return value;
  const number = Number(value);
  if (!Number.isFinite(number)) return value;
  return new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: maxDecimals,
  }).format(number).replace(/\u202f/g, ' ');
}

function formatEditingValue(value) {
  if (value === '' || value === '-') return value;
  const negative = value.startsWith('-');
  const unsigned = negative ? value.slice(1) : value;
  const [integer = '', ...decimalParts] = unsigned.split('.');
  const groupedInteger = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  const decimal = decimalParts.join('');
  const hasDecimalSeparator = value.includes('.');
  return `${negative ? '-' : ''}${groupedInteger}${hasDecimalSeparator ? `,${decimal}` : ''}`;
}

function getLogicalCaret(value, caret) {
  return (value.slice(0, caret).match(/[\d.,]/g) || []).length;
}

function getDisplayCaret(value, logicalCaret) {
  if (logicalCaret === 0) return 0;
  let count = 0;
  for (let index = 0; index < value.length; index += 1) {
    if (/[\d,]/.test(value[index])) count += 1;
    if (count === logicalCaret) return index + 1;
  }
  return value.length;
}

function getConstraintError(value, min, max) {
  if (value === '' || value === '-' || value.endsWith('.')) return '';
  const number = Number(value);
  if (!Number.isFinite(number)) return 'Saisissez un montant valide.';
  if (min !== undefined && min !== '' && number < Number(min)) return `Le montant minimum est ${min}.`;
  if (max !== undefined && max !== '' && number > Number(max)) return `Le montant maximum est ${max}.`;
  return '';
}

/** Saisie monétaire avec groupement visuel et valeur numérique transmise à onValueChange. */
export default function MoneyInput({
  value,
  onValueChange,
  min,
  max,
  maxDecimals = 2,
  onFocus,
  onBlur,
  ...props
}) {
  const [focused, setFocused] = useState(false);
  const [displayValue, setDisplayValue] = useState(() => formatMoneyValue(normalizeMoneyValue(value, maxDecimals), maxDecimals));
  const [constraintError, setConstraintError] = useState('');
  const inputRef = useRef(null);
  const pendingCaret = useRef(null);

  useEffect(() => {
    if (!focused) setDisplayValue(formatMoneyValue(normalizeMoneyValue(value, maxDecimals), maxDecimals));
  }, [value, maxDecimals, focused]);

  useLayoutEffect(() => {
    if (pendingCaret.current === null || !inputRef.current) return;
    const caret = getDisplayCaret(displayValue, pendingCaret.current);
    inputRef.current.setSelectionRange(caret, caret);
    pendingCaret.current = null;
  }, [displayValue]);

  const handleChange = event => {
    const rawValue = event.target.value;
    const logicalCaret = getLogicalCaret(rawValue, event.currentTarget.selectionStart ?? rawValue.length);
    const normalized = normalizeMoneyValue(rawValue, maxDecimals);
    const error = getConstraintError(normalized, min, max);
    pendingCaret.current = logicalCaret;
    setDisplayValue(formatEditingValue(normalized));
    setConstraintError(error);
    event.currentTarget.setCustomValidity(error);
    onValueChange?.(normalized);
  };

  const handleFocus = event => {
    setFocused(true);
    const normalized = normalizeMoneyValue(value, maxDecimals);
    const nextDisplayValue = formatEditingValue(normalized);
    pendingCaret.current = nextDisplayValue === event.currentTarget.value
      ? null
      : getLogicalCaret(event.currentTarget.value, event.currentTarget.selectionStart ?? event.currentTarget.value.length);
    setDisplayValue(nextDisplayValue);
    onFocus?.(event);
  };

  const handleBlur = event => {
    setFocused(false);
    const normalized = normalizeMoneyValue(value, maxDecimals);
    const error = getConstraintError(normalized, min, max);
    setConstraintError(error);
    event.currentTarget.setCustomValidity(error);
    setDisplayValue(formatMoneyValue(normalized, maxDecimals));
    onBlur?.(event);
  };

  return (
    <Input
      {...props}
      type="text"
      inputMode={maxDecimals > 0 ? 'decimal' : 'numeric'}
      value={displayValue}
      inputRef={inputRef}
      onChange={handleChange}
      onFocus={handleFocus}
      onBlur={handleBlur}
      error={props.error || constraintError}
      autoComplete="off"
    />
  );
}
