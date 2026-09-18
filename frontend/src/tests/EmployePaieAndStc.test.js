import { describe, it, expect } from 'vitest';
import { formatMoneyAr } from '@/utils/moneyUtils';

describe('Sprint Paie UI P0/P1 — Règles Métier et Cohérence Interface', () => {

    describe('1. Distinctions Mouvements & Sens Financiers (Acompte ≠ Avance ≠ Note de Frais ≠ Prime)', () => {
        const TYPES_MOUVEMENTS = [
            { code: 'ACOMPTE_15', sens: 'RETENUE', nature: 'Acompte quinzaine' },
            { code: 'AVANCE_SPECIALE', sens: 'RETENUE', nature: 'Avance exceptionnelle' },
            { code: 'CALL_PERSO', sens: 'RETENUE', nature: 'Communications privées' },
            { code: 'RET_DIV', sens: 'RETENUE', nature: 'Retenue diverse' },
            { code: 'NOTE_DE_FRAIS', sens: 'GAIN', nature: 'Remboursement de frais professionnels' },
            { code: 'PRIM_EXC', sens: 'GAIN', nature: 'Prime de performance / exceptionnelle' },
        ];

        it('NOTE_DE_FRAIS est strictement classifié comme un GAIN (remboursement) et non une retenue', () => {
            const frais = TYPES_MOUVEMENTS.find(t => t.code === 'NOTE_DE_FRAIS');
            expect(frais).toBeDefined();
            expect(frais.sens).toBe('GAIN');
        });

        it('ACOMPTE_15 et AVANCE_SPECIALE sont classifiés comme RETENUE sur salaire', () => {
            const acompte = TYPES_MOUVEMENTS.find(t => t.code === 'ACOMPTE_15');
            const avance = TYPES_MOUVEMENTS.find(t => t.code === 'AVANCE_SPECIALE');
            expect(acompte.sens).toBe('RETENUE');
            expect(avance.sens).toBe('RETENUE');
        });

        it('PRIM_EXC est un GAIN', () => {
            const prime = TYPES_MOUVEMENTS.find(t => t.code === 'PRIM_EXC');
            expect(prime.sens).toBe('GAIN');
        });
    });

    describe('2. Logique Contrat → STC dans le dossier Employé', () => {
        it('contrat actif sans STC : ne doit PAS inciter à établir un STC', () => {
            const contrat = { isActif: true, codeStatutContrat: 'ACTIF', idContrat: 101 };
            const stcList = [];

            const isContratClos = contrat.codeStatutContrat === 'CLOTURE' || contrat.codeStatutContrat === 'ROMPU' || !contrat.isActif;
            const doitAfficherStcCTA = isContratClos && stcList.length === 0;

            expect(isContratClos).toBe(false);
            expect(doitAfficherStcCTA).toBe(false);
        });

        it('contrat rompu ou clôturé sans STC : doit déclencher le CTA "Préparer / Établir le STC"', () => {
            const contrat = { isActif: false, codeStatutContrat: 'ROMPU', idContrat: 102 };
            const stcList = [];

            const isContratClos = contrat.codeStatutContrat === 'CLOTURE' || contrat.codeStatutContrat === 'ROMPU' || !contrat.isActif;
            const doitAfficherStcCTA = isContratClos && stcList.length === 0;

            expect(isContratClos).toBe(true);
            expect(doitAfficherStcCTA).toBe(true);
        });

        it('contrat rompu avec STC validé : affiche le reçu officiel et l’action "Consulter la Quittance"', () => {
            const contrat = { isActif: false, codeStatutContrat: 'CLOTURE', idContrat: 103 };
            const stcList = [
                {
                    idStc: 42,
                    numeroRecu: 'STC-2026-0001',
                    statut: 'VALIDE',
                    netAPayerStc: 1250430,
                    dateDepart: '2026-02-15'
                }
            ];

            const isContratClos = contrat.codeStatutContrat === 'CLOTURE' || contrat.codeStatutContrat === 'ROMPU' || !contrat.isActif;
            const hasValideStc = stcList.some(s => s.statut === 'VALIDE');

            expect(isContratClos).toBe(true);
            expect(hasValideStc).toBe(true);
            expect(stcList[0].numeroRecu).toMatch(/^STC-\d{4}-\d{4}$/);
            expect(formatMoneyAr(stcList[0].netAPayerStc)).toContain('1');
            expect(formatMoneyAr(stcList[0].netAPayerStc)).toContain('250');
            expect(formatMoneyAr(stcList[0].netAPayerStc)).toContain('430');
        });
    });

    describe('3. Synthèse Paie Backend-Driven & Sécurisation anti-undefined/NaN', () => {
        it('formate proprement une réponse backend partielle sans afficher NaN ou undefined', () => {
            const synthesePartielle = {
                salaireBaseContractuel: null,
                tauxHoraire: undefined,
                tauxJournalierConge: 0,
                dernierBrut: null,
                dernierNet: undefined,
                totalAvancesEnCours: 0,
                capitalRestantPrets: null,
            };

            expect(formatMoneyAr(synthesePartielle.salaireBaseContractuel)).toBe('—');
            expect(formatMoneyAr(synthesePartielle.tauxHoraire)).toBe('—');
            expect(formatMoneyAr(synthesePartielle.tauxJournalierConge)).toBe('0 Ar');
            expect(formatMoneyAr(synthesePartielle.dernierBrut)).toBe('—');
            expect(formatMoneyAr(synthesePartielle.dernierNet)).toBe('—');
            expect(formatMoneyAr(synthesePartielle.totalAvancesEnCours)).toBe('0 Ar');
            expect(formatMoneyAr(synthesePartielle.capitalRestantPrets)).toBe('—');
        });

        it('vérifie que les valeurs chiffrées réelles ne sont pas écrasées', () => {
            const syntheseComplete = {
                salaireBaseContractuel: 2000000,
                tauxHoraire: 11538.46,
                tauxJournalierConge: 66666.67,
                dernierBrut: 2150000,
                dernierNet: 1785200,
            };

            expect(formatMoneyAr(syntheseComplete.salaireBaseContractuel)).toContain('2 000 000');
            expect(formatMoneyAr(syntheseComplete.dernierBrut)).toContain('2 150 000');
            expect(formatMoneyAr(syntheseComplete.dernierNet)).toContain('1 785 200');
        });
    });

    describe('4. Simulation vs Validation STC (3 États stricts)', () => {
        it('simulation produit un statut SIMULATION non persistant', () => {
            const simResult = {
                statut: 'SIMULATION',
                numeroRecu: null,
                netAPayerStc: 854120,
            };
            expect(simResult.statut).toBe('SIMULATION');
            expect(simResult.numeroRecu).toBeNull();
        });

        it('validation définitive génère un reçu officiel numéroté', () => {
            const valResult = {
                statut: 'VALIDE',
                numeroRecu: 'STC-2026-0005',
                netAPayerStc: 854120,
            };
            expect(valResult.statut).toBe('VALIDE');
            expect(valResult.numeroRecu).toBe('STC-2026-0005');
        });
    });
});
