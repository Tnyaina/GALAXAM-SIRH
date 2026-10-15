import { describe, expect, it } from 'vitest';
import { filterPointageDays } from '../utils/pointageDayFilter';

describe('filtres communs des journées de pointage', () => {
    const days = [
        { statut: 'PRESENT' },
        { statut: 'CONGE', congeApprouve: true },
        { statut: 'FERIE_CHOME' },
        { statut: 'ABSENCE_NON_JUSTIFIEE' },
        { statut: 'PRESENT', minutesRetard: 30 }
    ];

    it('conserve toutes les journées par défaut', () => {
        expect(filterPointageDays(days)).toHaveLength(5);
    });

    it('sépare les exceptions des anomalies tout en permettant leur recouvrement', () => {
        expect(filterPointageDays(days, 'EXCEPTIONS')).toEqual([days[1], days[2], days[3]]);
        expect(filterPointageDays(days, 'ANOMALIES')).toEqual([days[3], days[4]]);
    });
});
