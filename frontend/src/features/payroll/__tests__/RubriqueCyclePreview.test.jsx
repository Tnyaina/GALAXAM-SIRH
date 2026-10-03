import { describe, expect, it } from 'vitest';
import { cycleContaining, firstCoveredCycle, lastCoveredCycle } from '../components/rubriqueCycle';

describe('validité des rubriques sur un cycle 26–25', () => {
    it('rattache le 1er au cycle déjà commencé le 26', () => {
        expect(cycleContaining('2026-08-01')).toEqual({ start: '2026-07-26', end: '2026-08-25' });
    });

    it('ne considère que les cycles entièrement couverts', () => {
        expect(firstCoveredCycle('2026-07-26')).toEqual({ start: '2026-07-26', end: '2026-08-25' });
        expect(firstCoveredCycle('2026-08-01')).toEqual({ start: '2026-08-26', end: '2026-09-25' });
        expect(lastCoveredCycle('2026-08-25')).toEqual({ start: '2026-07-26', end: '2026-08-25' });
        expect(lastCoveredCycle('2026-08-01')).toEqual({ start: '2026-06-26', end: '2026-07-25' });
    });

    it('gère le changement d’année', () => {
        expect(firstCoveredCycle('2027-01-01')).toEqual({ start: '2027-01-26', end: '2027-02-25' });
        expect(lastCoveredCycle('2027-01-01')).toEqual({ start: '2026-11-26', end: '2026-12-25' });
    });
});
