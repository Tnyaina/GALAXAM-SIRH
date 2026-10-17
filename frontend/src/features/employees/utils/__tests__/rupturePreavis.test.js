import { describe, expect, it } from 'vitest';
import { calculerDateEffet, choisirDureePreavis, choisirTypeRupture, getPreavisGuide } from '../rupturePreavis';

const base = {
    codeTypeEvenement: 'DEMISSION',
    dateNotification: '2026-10-17',
    dateEffet: '',
    dureePreavisKey: '',
    customJours: '30',
    modePreavis: 'EFFECTUE',
};

describe('parcours guidé du préavis', () => {
    it('attend un délai avant de proposer une date de sortie', () => {
        expect(calculerDateEffet(base.dateNotification, '', 'EFFECTUE')).toBe('');
        expect(getPreavisGuide(base).calendrierComplet).toBe(false);
    });

    it('retire la dispense si aucun préavis est choisi, puis rétablit le travail si un délai est choisi', () => {
        const sansPreavis = choisirDureePreavis({ ...base, modePreavis: 'DISPENSE_PAYEE' }, 'AUCUN');
        expect(sansPreavis.modePreavis).toBe('DISPENSE_NON_PAYEE');
        expect(sansPreavis.dateEffet).toBe('2026-10-17');
        expect(getPreavisGuide(sansPreavis).moisIndemnite).toBe(0);

        const avecPreavis = choisirDureePreavis(sansPreavis, '1_MOIS');
        expect(avecPreavis.modePreavis).toBe('EFFECTUE');
        expect(avecPreavis.dateEffet).toBe('2026-11-17');
    });

    it('réinitialise le délai suggéré en quittant une fin de contrat', () => {
        const fin = choisirTypeRupture(base, 'FIN_CONTRAT', 'FIN_CONTRAT');
        expect(getPreavisGuide(fin).sansPreavis).toBe(true);
        const demission = choisirTypeRupture(fin, 'DEMISSION', 'DEMISSION');
        expect(demission.dureePreavisKey).toBe('');
        expect(demission.dateEffet).toBe('');
    });
});
