import { describe, it, expect } from 'vitest';
import { calculerDateFinAutomatique } from '../utils/referencePeriodDates';

describe('Bornes réelles du cycle TCONGE', () => {
  it.each([
    ['2025-12-26', '2026-12-25'],
    ['2025-01-01', '2025-12-31'],
    ['2024-02-29', '2025-02-27'],
    ['2025-01-31', '2026-01-30'],
  ])('calcule la fin inclusive de %s', (start, end) => {
    expect(calculerDateFinAutomatique(start)).toBe(end);
  });
  it('exige une date réelle', () => expect(calculerDateFinAutomatique('')).toBe(''));
});
