import { describe, it, expect } from 'vitest';
import { formatMoneyAr } from '@/utils/moneyUtils';

describe('moneyUtils — formatMoneyAr helper canonique (P0/P1)', () => {
    it('gère null, undefined, NaN et chaîne vide en retournant le tiret cadratin canonique', () => {
        expect(formatMoneyAr(null)).toBe('—');
        expect(formatMoneyAr(undefined)).toBe('—');
        expect(formatMoneyAr(NaN)).toBe('—');
        expect(formatMoneyAr('')).toBe('—');
        expect(formatMoneyAr('abc')).toBe('—');
    });

    it('gère 0 en retournant "0 Ar" et non un tiret ou un fallback vide', () => {
        expect(formatMoneyAr(0)).toBe('0 Ar');
        expect(formatMoneyAr('0')).toBe('0 Ar');
        expect(formatMoneyAr(0.0)).toBe('0 Ar');
    });

    it('formate correctement les montants entiers avec espace et suffixe Ar', () => {
        const res = formatMoneyAr(1500000);
        expect(res).toContain('1');
        expect(res).toContain('500');
        expect(res).toContain('000');
        expect(res).toContain('Ar');
    });

    it('formate correctement les montants avec décimales', () => {
        const res = formatMoneyAr(2500.5);
        expect(res).toContain('2');
        expect(res).toContain('500');
        expect(res).toContain('Ar');
    });
});
