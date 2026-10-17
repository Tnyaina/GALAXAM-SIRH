import { describe, expect, it } from 'vitest';
import { isValidIsoDate, shiftIsoDate, isDateAnterieure } from '../dateUtils';

describe('dateUtils - robustesse de la saisie de dates', () => {
  describe('isValidIsoDate', () => {
    it('valide les dates ISO complètes', () => {
      expect(isValidIsoDate('2026-08-10')).toBe(true);
      expect(isValidIsoDate('1999-12-31')).toBe(true);
    });

    it('rejette les dates partielles en cours de frappe', () => {
      expect(isValidIsoDate('0002-08-11')).toBe(false);
      expect(isValidIsoDate('0020-08-11')).toBe(false);
      expect(isValidIsoDate('0202-08-11')).toBe(false);
      expect(isValidIsoDate('2026-08')).toBe(false);
      expect(isValidIsoDate('')).toBe(false);
      expect(isValidIsoDate(null)).toBe(false);
    });

    it('rejette les dates hors limites ou invalides', () => {
      expect(isValidIsoDate('1850-01-01')).toBe(false);
      expect(isValidIsoDate('2150-01-01')).toBe(false);
      expect(isValidIsoDate('2026-13-01')).toBe(false);
      expect(isValidIsoDate('2026-00-10')).toBe(false);
    });
  });

  describe('shiftIsoDate', () => {
    it('décale correctement une date valide', () => {
      expect(shiftIsoDate('2026-08-10', 1)).toBe('2026-08-11');
      expect(shiftIsoDate('2026-08-11', -1)).toBe('2026-08-10');
      expect(shiftIsoDate('2026-03-01', -1)).toBe('2026-02-28');
    });

    it('renvoie chaîne vide pour une date incomplète ou invalide', () => {
      expect(shiftIsoDate('0006-08-11', -1)).toBe('');
      expect(shiftIsoDate('', 1)).toBe('');
      expect(shiftIsoDate(null, 1)).toBe('');
    });
  });

  describe('isDateAnterieure', () => {
    it('ne marque pas une date incomplète comme antérieure', () => {
      expect(isDateAnterieure('0002-08-11')).toBe(false);
      expect(isDateAnterieure('')).toBe(false);
    });
  });
});
