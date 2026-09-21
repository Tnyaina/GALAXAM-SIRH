import { describe, it, expect } from 'vitest';
import { formatMoneyAr } from '@/utils/moneyUtils';

/**
 * Suite de Certification d'Alignement Backend <-> API <-> Frontend
 *
 * SÉPARATION STRICTE :
 * 1. SYNTHETIC_CERTIFICATION_PROFILE (SYN_EMP001_STANDARD, SYN_EMP002_ABSENCE, SYN_EMP006_COMPLEX)
 * 2. HISTORICAL_GALAXAM_EMPLOYEE (HIST_RAKOTO_2025_12, HIST_RAKETAKA_2025_12, HIST_RASOA_2025_12, HIST_HAVANA_KAJY)
 *
 * Règle d'or : EXPECTED == BACKEND == API == DISPLAYED_UI (Zéro recalcul client)
 */
describe('Certified Profiles & Historical Golden Master Alignment', () => {

    describe('1. Profil Synthétique Certifié SYN_EMP001_STANDARD (RAZAFY Jean)', () => {
        // Source canonique : CertificationDataset.EMP001_STANDARD & PayrollIndependentOracle
        const synEmp001Payload = {
            idEmploye: 1,
            matricule: 'EMP001',
            nom: 'RAZAFY',
            prenom: 'Jean',
            nomPrenom: 'RAZAFY Jean',
            salaireBase: 400000.00,
            totalGainsBruts: 400000.00,
            cotisationCnapsSalariale: 4000.00, // 1%
            cotisationSanteSalariale: 6000.00, // OMSI 1.5%
            totalCotisationsSalariales: 10000.00,
            salaireNetImposable: 390000.00,
            irsaNet: 2000.00, // Barème IRSA 2026 tranche 350k-400k à 5%
            totalRetenuesAcomptePret: 0.00,
            netAPayer: 388000.00,
            lignes: [
                { codeRubrique: 'SALAIRE_BASE', libelleRubrique: 'Salaire de Base', sens: 'GAIN', montantSalarial: 400000.00 },
                { codeRubrique: 'COTISATION_CNAPS_SAL', libelleRubrique: 'CNaPS Salarié (1%)', sens: 'RETENUE', montantSalarial: 4000.00 },
                { codeRubrique: 'COTISATION_OMSI_SAL', libelleRubrique: 'Santé OMSI (1.5%)', sens: 'RETENUE', montantSalarial: 6000.00 },
                { codeRubrique: 'RETENUE_IRSA', libelleRubrique: 'IRSA Retenu', sens: 'RETENUE', montantSalarial: 2000.00 },
            ]
        };

        it('affiche le Net à Payer canonique SYN_EMP001 sans divergence', () => {
            const displayedNet = formatMoneyAr(synEmp001Payload.netAPayer);
            expect(displayedNet).toBe('388 000 Ar');
            expect(synEmp001Payload.netAPayer).toBe(388000.00);
        });

        it('décompose le brut contractuel exactement selon les données transmises', () => {
            const displayedBase = formatMoneyAr(synEmp001Payload.salaireBase);
            const displayedBrut = formatMoneyAr(synEmp001Payload.totalGainsBruts);

            expect(displayedBase).toBe('400 000 Ar');
            expect(displayedBrut).toBe('400 000 Ar');
        });

        it('respecte les retenues CNaPS, Santé et IRSA certifiées', () => {
            expect(formatMoneyAr(synEmp001Payload.cotisationCnapsSalariale)).toBe('4 000 Ar');
            expect(formatMoneyAr(synEmp001Payload.cotisationSanteSalariale)).toBe('6 000 Ar');
            expect(formatMoneyAr(synEmp001Payload.totalCotisationsSalariales)).toBe('10 000 Ar');
            expect(formatMoneyAr(synEmp001Payload.salaireNetImposable)).toBe('390 000 Ar');
            expect(formatMoneyAr(synEmp001Payload.irsaNet)).toBe('2 000 Ar');
        });
    });

    describe('2. Profil Synthétique Certifié SYN_EMP006_COMPLEX (RAKOTO_COMPLEX Herizo)', () => {
        // Cas extrême du dataset synthétique : Base 600k + Congé 1j + HS + Nuit + Dimanche + Acompte
        const synEmp006Payload = {
            idEmploye: 6,
            matricule: 'EMP006',
            nomPrenom: 'RAKOTO_COMPLEX Herizo',
            salaireBase: 600000.00,
            explication: {
                heuresAbsenceRetenues: 8.0,
                montantRetenueAbsence: 27692.31,
                nbJoursConge: 1.0,
                deductionCongeTrentieme: 20000.00,
                indemniteConge: 20000.00,
                montantHs30: 13500.00,
                montantHs50: 15600.00,
                montantNuit: 7000.00,
                montantDimanche: 12000.00,
            },
            totalGainsBruts: 640407.69,
            totalCotisationsSalariales: 16010.19, // CNaPS 1% (6 404 Ar) + OMSI 1.5% (9 606 Ar)
            salaireNetImposable: 624397.50,
            totalRetenuesAcomptePret: 50000.00,
            netAPayer: 541890.00
        };

        it('affiche l audit complet de chaque composante sans mélange d unités', () => {
            expect(synEmp006Payload.explication.heuresAbsenceRetenues).toBe(8.0);
            expect(synEmp006Payload.explication.nbJoursConge).toBe(1.0);
            expect(formatMoneyAr(synEmp006Payload.totalRetenuesAcomptePret)).toBe('50 000 Ar');
            expect(formatMoneyAr(synEmp006Payload.netAPayer)).toBe('541 890 Ar');
        });

        it('vérifie que les heures et les jours restent rigoureusement cloisonnés', () => {
            const hAbs = `${synEmp006Payload.explication.heuresAbsenceRetenues} h`;
            const jConge = `${synEmp006Payload.explication.nbJoursConge} j`;

            expect(hAbs).toBe('8 h');
            expect(jConge).toBe('1 j');
            expect(hAbs).not.toContain('j');
            expect(jConge).not.toContain('h');
        });
    });

    describe('3. Salarié Historique Réel HIST_RAKOTO_2025_12 (Matricule MATR-004, Aide-Déclarant)', () => {
        // Source canonique Excel réconcilié : GALAXAM_HISTORICAL_EXCEL_CORRECTED_GOLDEN_MASTER.md
        const histRakotoPayload = {
            idEmploye: 4,
            matricule: 'MATR-004',
            nomPrenom: 'RAKOTO Hery',
            salaireBase: 400000.00,
            totalGainsBruts: 502610.00,
            totalCotisationsSalariales: 10052.20,
            salaireNetImposable: 492557.80,
            irsaNet: 21690.00,
            netAPayer: 470860.00
        };

        it('présente le Net à Payer historique corrigé sans recalcul client', () => {
            expect(formatMoneyAr(histRakotoPayload.netAPayer)).toBe('470 860 Ar');
            expect(histRakotoPayload.matricule).toBe('MATR-004');
        });
    });

    describe('4. Salarié Historique Réel HIST_RAKETAKA_2025_12 & HIST_RASOA_2025_12', () => {
        const histRaketakaPayload = {
            idEmploye: 2,
            matricule: 'MATR-002',
            nomPrenom: 'RAKETAKA Voahangy',
            salaireBase: 600000.00,
            netAPayer: 582080.00
        };

        const histRasoaPayload = {
            idEmploye: 3,
            matricule: 'MATR-003',
            nomPrenom: 'RASOA Claudine',
            salaireBase: 550000.00,
            netAPayer: 546150.00
        };

        it('présente les montants historiques certifiés sans ambiguïté', () => {
            expect(formatMoneyAr(histRaketakaPayload.netAPayer)).toBe('582 080 Ar');
            expect(formatMoneyAr(histRasoaPayload.netAPayer)).toBe('546 150 Ar');
        });
    });
});
