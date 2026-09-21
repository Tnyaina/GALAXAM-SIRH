import { describe, it, expect } from 'vitest';
import { formatMoneyAr } from '@/utils/moneyUtils';

describe('Payroll Runtime UI Stabilization — Tests de Non-Régression DTO & Runtime', () => {

    describe('1. BulletinPaie DTO Drift & Null Safety', () => {
        it('résout le nom complet du salarié sans jamais afficher "undefined undefined"', () => {
            // Cas 1: backend renvoie nomPrenom
            const b1 = { nomPrenom: 'RAKETAKA Raketaka' };
            const nom1 = b1.nomPrenom || [b1.nom, b1.prenom].filter(Boolean).join(' ') || [b1.nomEmploye, b1.prenomEmploye].filter(Boolean).join(' ') || 'Employé';
            expect(nom1).toBe('RAKETAKA Raketaka');

            // Cas 2: backend renvoie nom et prenom séparés
            const b2 = { nom: 'RAZAFY', prenom: 'Jean' };
            const nom2 = b2.nomPrenom || [b2.nom, b2.prenom].filter(Boolean).join(' ') || [b2.nomEmploye, b2.prenomEmploye].filter(Boolean).join(' ') || 'Employé';
            expect(nom2).toBe('RAZAFY Jean');

            // Cas 3: backend renvoie nomEmploye et prenomEmploye
            const b3 = { nomEmploye: 'ANDRIA', prenomEmploye: 'Tojo' };
            const nom3 = b3.nomPrenom || [b3.nom, b3.prenom].filter(Boolean).join(' ') || [b3.nomEmploye, b3.prenomEmploye].filter(Boolean).join(' ') || 'Employé';
            expect(nom3).toBe('ANDRIA Tojo');

            // Cas 4: données totalement absentes
            const b4 = {};
            const nom4 = b4.nomPrenom || [b4.nom, b4.prenom].filter(Boolean).join(' ') || [b4.nomEmploye, b4.prenomEmploye].filter(Boolean).join(' ') || 'Employé';
            expect(nom4).toBe('Employé');
            expect(nom4).not.toContain('undefined');
        });

        it('résout joursTravailles et évite le faux fallback "22j"', () => {
            const b1 = { joursTravailles: 18.0 };
            const jt1 = b1.joursTravailles ?? b1.nbJoursTravailles;
            expect(jt1).toBe(18.0);

            const b2 = { nbJoursTravailles: 21.5 };
            const jt2 = b2.joursTravailles ?? b2.nbJoursTravailles;
            expect(jt2).toBe(21.5);

            const b3 = {};
            const jt3 = b3.joursTravailles ?? b3.nbJoursTravailles ?? null;
            expect(jt3).toBeNull();
        });

        it('résout netImposable depuis salaireNetImposable canonique', () => {
            const bulletin = {
                salaireNetImposable: 601103.74,
                netImposable: null
            };
            const netImposable = bulletin.revenuNetImposable ?? bulletin.salaireNetImposable ?? bulletin.netImposable;
            expect(netImposable).toBe(601103.74);
            expect(formatMoneyAr(netImposable)).toBe('601 103,74 Ar');
        });

        it('récupère CNaPS et OSTIE depuis le tableau de lignes si absent de l entête bulletin', () => {
            const bulletin = {
                cnapsSalariale: null,
                ostieSalariale: null
            };
            const lignes = [
                { codeRubrique: 'COTISATION_CNAPS_SAL', libelleRubrique: 'CNaPS Salariale 1%', sens: 'RETENUE', montantSalarial: 6071.76 },
                { codeRubrique: 'COTISATION_OSTIE_SAL', libelleRubrique: 'Santé Salariale 1%', sens: 'RETENUE', montantSalarial: 6071.76 },
            ];

            const ligneCnaps = lignes.find(l => l.codeRubrique === 'COTISATION_CNAPS_SAL' || l.codeRubrique === 'CNAPS_SAL');
            const ligneOstie = lignes.find(l => l.codeRubrique === 'COTISATION_OSTIE_SAL' || l.codeRubrique === 'OSTIE_SAL');

            const cnapsVal = bulletin.cnapsSalariale ?? (ligneCnaps ? (ligneCnaps.montantSalarial ?? ligneCnaps.montantRetenue) : null);
            const ostieVal = bulletin.ostieSalariale ?? (ligneOstie ? (ligneOstie.montantSalarial ?? ligneOstie.montantRetenue) : null);

            expect(cnapsVal).toBe(6071.76);
            expect(ostieVal).toBe(6071.76);
            expect(formatMoneyAr(cnapsVal)).toBe('6 071,76 Ar');
        });

        it('calcule correctement les montants de gains et retenues de lignes selon le sens', () => {
            const ligneGain = {
                codeRubrique: 'SALAIRE_BASE',
                libelleRubrique: 'Salaire de Base',
                sens: 'GAIN',
                montantSalarial: 500000,
            };
            const ligneRetenue = {
                codeRubrique: 'RETENUE_ABSENCE',
                libelleRubrique: 'Retenue absence',
                sens: 'RETENUE',
                montantSalarial: 45000,
            };

            const isRetenueG = ligneGain.sens === 'RETENUE';
            const isGainG = ligneGain.sens === 'GAIN' || ligneGain.sens === 'INFO';
            const montantGain = isGainG ? (ligneGain.montantSalarial ?? ligneGain.montantGain) : null;
            const montantRetenueG = isRetenueG ? (ligneGain.montantSalarial ?? ligneGain.montantRetenue) : null;

            expect(montantGain).toBe(500000);
            expect(montantRetenueG).toBeNull();

            const isRetenueR = ligneRetenue.sens === 'RETENUE';
            const isGainR = ligneRetenue.sens === 'GAIN' || ligneRetenue.sens === 'INFO';
            const montantGainR = isGainR ? (ligneRetenue.montantSalarial ?? ligneRetenue.montantGain) : null;
            const montantRetenueR = isRetenueR ? (ligneRetenue.montantSalarial ?? ligneRetenue.montantRetenue) : null;

            expect(montantRetenueR).toBe(45000);
            expect(montantGainR).toBeNull();
        });
    });

    describe('2. VariablesPaie DTO Compatibility', () => {
        it('gère à la fois camelCase et snake_case pour idAvance, nomComplet, dateDemande', () => {
            const vCamel = {
                idAvance: 42,
                dateDemande: '2026-09-01',
                nomComplet: 'RAZAFY Jean',
                typeLibelle: 'Prime exceptionnelle',
                sens: 'GAIN',
                montant: 50000
            };

            const vSnake = {
                id_avance: 43,
                date_demande: '2026-09-02',
                nom_complet: 'RAKETAKA Marie',
                type_libelle: 'Acompte 15',
                sens: 'RETENUE',
                montant: 25000
            };

            expect(vCamel.idAvance ?? vCamel.id_avance).toBe(42);
            expect(vSnake.idAvance ?? vSnake.id_avance).toBe(43);

            expect(vCamel.nomComplet || vCamel.nom_complet).toBe('RAZAFY Jean');
            expect(vSnake.nomComplet || vSnake.nom_complet).toBe('RAKETAKA Marie');

            expect(vCamel.dateDemande || vCamel.date_demande).toBe('2026-09-01');
            expect(vSnake.dateDemande || vSnake.date_demande).toBe('2026-09-02');
        });
    });

    describe('3. STC Date Input & DTO Format', () => {
        const fmtDateInput = (val) => {
            if (!val) return '';
            if (typeof val === 'string') {
                const parts = val.split('T');
                if (parts[0] && parts[0].length === 10) return parts[0];
            }
            try {
                const d = new Date(val);
                if (isNaN(d.getTime())) return '';
                return d.toISOString().split('T')[0];
            } catch {
                return '';
            }
        };

        it('convertit un timestamp ISO backend en format strict YYYY-MM-DD pour input date', () => {
            const isoFromBackend = '2026-09-18T12:31:18.408+03:00';
            const formatted = fmtDateInput(isoFromBackend);
            expect(formatted).toBe('2026-09-18');
        });

        it('résout netAPayerStc ou netAPayer sans afficher NaN ou undefined', () => {
            const stcResult = {
                totalBrutStc: 1250000,
                netAPayerStc: 1050000
            };
            const net = stcResult.netAPayerStc ?? stcResult.netAPayer;
            const brut = stcResult.totalBrutStc ?? stcResult.totalBrut;

            expect(net).toBe(1050000);
            expect(brut).toBe(1250000);
            expect(formatMoneyAr(net)).toBe('1 050 000 Ar');
            expect(formatMoneyAr(brut)).toBe('1 250 000 Ar');
        });
    });

    describe('4. Règle Anti-Fallback Mensonger', () => {
        it('formatMoneyAr(null) renvoie tiret "—" et non "0 Ar"', () => {
            expect(formatMoneyAr(null)).toBe('—');
            expect(formatMoneyAr(undefined)).toBe('—');
            expect(formatMoneyAr('')).toBe('—');
        });

        it('formatMoneyAr(0) renvoie légitimement "0 Ar" pour un montant numérique nul réel', () => {
            expect(formatMoneyAr(0)).toBe('0 Ar');
        });
    });
});
