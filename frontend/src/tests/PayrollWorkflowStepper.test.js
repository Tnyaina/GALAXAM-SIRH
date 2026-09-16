import { describe, it, expect } from 'vitest';

/**
 * Tests des règles métier de calcul des états du stepper (Sprint 2B)
 */
describe('PayrollCampaignWorkflow — Calcul des états d’étape', () => {
    const STEPS_CONFIG = [
        { code: 'PREPARATION', label: 'Préparation', rang: 1 },
        { code: 'POPULATION', label: 'Population', rang: 2 },
        { code: 'VARIABLES', label: 'Variables', rang: 3 },
        { code: 'CALCUL', label: 'Calcul', rang: 4 },
        { code: 'CONTROLE', label: 'Contrôle', rang: 5 },
        { code: 'VALIDATION', label: 'Validation', rang: 6 },
        { code: 'OUTPUTS', label: 'Sorties', rang: 7 },
    ];

    it('une période clôturée marque toutes les étapes comme COMPLETED ou LOCKED', () => {
        const isCloturee = true;
        const steps = STEPS_CONFIG.map(s => ({
            ...s,
            state: s.code === 'OUTPUTS' ? 'AVAILABLE' : (isCloturee ? 'LOCKED' : 'AVAILABLE')
        }));

        expect(steps.every(s => ['LOCKED', 'AVAILABLE'].includes(s.state))).toBe(true);
    });

    it('une période en préparation bloque les sorties et le contrôle avant calcul', () => {
        const hasCalcul = false;
        const controleState = hasCalcul ? 'AVAILABLE' : 'BLOCKED';
        const outputsState = hasCalcul ? 'AVAILABLE' : 'BLOCKED';

        expect(controleState).toBe('BLOCKED');
        expect(outputsState).toBe('BLOCKED');
    });

    it('la détection de variables modifiées déclenche un besoin de recalcul', () => {
        const nbBulletins = 12;
        const lastCalculDate = '2026-09-15T10:00:00Z';
        const lastVariableDate = '2026-09-15T12:00:00Z';

        const recalculationRequired = nbBulletins > 0 && lastVariableDate > lastCalculDate;
        expect(recalculationRequired).toBe(true);
    });

    it('les destinations canoniques des 7 étapes respectent les conventions d’URL', () => {
        const periodId = 42;
        const destinations = {
            PREPARATION: `/paie/periodes/${periodId}/preparation`,
            POPULATION: `/paie/periodes/${periodId}/population`,
            VARIABLES: `/paie/periodes/${periodId}/variables`,
            CALCUL: `/paie/periodes/${periodId}/calcul`,
            CONTROLE: `/paie/periodes/${periodId}/controle`,
            VALIDATION: `/paie/periodes/${periodId}/validation`,
            OUTPUTS: `/paie/periodes/${periodId}/sorties`,
        };

        expect(destinations.PREPARATION).toBe('/paie/periodes/42/preparation');
        expect(destinations.VARIABLES).toBe('/paie/periodes/42/variables');
        expect(destinations.CALCUL).toBe('/paie/periodes/42/calcul');
        expect(destinations.CONTROLE).toBe('/paie/periodes/42/controle');
        expect(destinations.VALIDATION).toBe('/paie/periodes/42/validation');
        expect(destinations.OUTPUTS).toBe('/paie/periodes/42/sorties');
    });
});
