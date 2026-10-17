// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DossierSortieModal from '../components/modals/stc/DossierSortieModal';
import DossiersSortiePage from '../pages/DossiersSortiePage';
import dossierSortieService from '../api/dossierSortieService';
import contratService from '@/features/employees/api/contratService';
const permissions = vi.hoisted(() => ({ canWrite: true }));
const navigateSpy = vi.hoisted(() => vi.fn());
const toastCalls = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() }));

vi.mock('../api/dossierSortieService', () => ({
    default: {
        listerDossiers: vi.fn(),
        obtenirDossier: vi.fn(),
        obtenirDossierEmploye: vi.fn(),
        sauvegarderDossier: vi.fn(),
        enregistrerDelivranceCertificat: vi.fn(),
        mettreAJourOffboarding: vi.fn(),
        simulerStc: vi.fn(),
        scellerStc: vi.fn(),
    }
}));

vi.mock('@/features/employees/api/contratService', () => ({
    default: { getMotifsSortie: vi.fn() }
}));

vi.mock('react-router-dom', () => ({
    useNavigate: () => navigateSpy,
    Link: ({ children, to }) => <a href={to}>{children}</a>,
}));

vi.mock('@/components/ui/overlays/Modal', () => ({
    default: ({ isOpen, children, footer, title }) => isOpen ? (
        <div data-testid="modal">
            <h2>{title}</h2>
            {children}
            {footer}
        </div>
    ) : null
}));
vi.mock('@/components/ui/overlays/ConfirmDialog', () => ({
    default: ({ isOpen, onConfirm }) => isOpen ? <button onClick={onConfirm}>Valider la confirmation</button> : null
}));

vi.mock('@/components/ui/feedback/Toast', () => ({
    useToast: () => toastCalls
}));

vi.mock('@/components/ui/forms/EmployeSearchSelect', () => ({
    default: ({ onChange }) => <button type="button" onClick={() => onChange(7, { idEmploye: 7 })}>Choisir le salarié</button>
}));

vi.mock('@/features/auth/components/HasPermission', () => ({
    default: ({ children }) => <>{children}</>
}));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ hasPermission: () => permissions.canWrite }) }));

vi.mock('../components/modals/stc/StcRecuModal', () => ({
    default: () => null
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host, root;
const changeField = async (field, value) => {
    const descriptor = Object.getOwnPropertyDescriptor(
        field.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value');
    await act(async () => {
        descriptor.set.call(field, value);
        field.dispatchEvent(new Event('input', { bubbles: true }));
        field.dispatchEvent(new Event('change', { bubbles: true }));
    });
};

const mockDossier = {
    idDossierSortie: 101,
    idEmploye: 7,
    matricule: 'EMP-007',
    nomComplet: 'Rakoto Jean',
    intitulePoste: 'Chauffeur Poids Lourd',
    departement: 'Logistique',
    site: 'Antananarivo',
    dateEmbauche: '2023-01-15',
    ancienneteAnnees: 3,
    ancienneteMois: 8,
    salaireBase: 450000,
    tauxHoraire: 2596.15,
    typeContrat: 'CDI',
    version: 1,
    referenceSimulation: null,
    snapshotSimulation: null,
    dateDepart: '2026-10-31',
    motifRupture: 'DEMISSION',
    statutDossier: 'EN_PREPARATION',
    joursPointageReels: 22,
    totalHeuresPointage: 176,
    joursSousContrat: 30,
    sourcePointage: 'Pointage validé',
    pointageGele: false,
    soldeCongesRestant: 5.5,
    iccpEstimee: 98500,
    demandesCongesEnAttente: 0,
    statutBulletinMensuel: 'NON GÉNÉRÉ',
    netBulletinMensuel: 0,
    montantBulletinDejaPaye: 0,
    soldeBulletinARegler: 0,
    capitalRestantPrets: 200000,
    pretAValider: false,
    certificatDelivre: false,
    dateDelivranceCertificat: null,
    checklist: [
        {
            code: 'CERTIFICAT_TRAVAIL_OBLIGATOIRE',
            titre: 'Délivrance obligatoire du certificat de travail (Art. 50)',
            niveau: 'BLOQUANT',
            sourceNorme: 'LEGAL_MADAGASCAR_2024',
            statut: 'NON_CONFORME',
            message: 'Le certificat de travail doit être remis au salarié dès la cessation effective.',
            actionRecommandee: 'Enregistrer la remise effective du certificat de travail.',
            lienModule: null,
        },
        {
            code: 'PRETS_NON_SOLDES',
            titre: 'Prêts et avances non soldés (Interdiction de retenue automatique)',
            niveau: 'BLOQUANT',
            sourceNorme: 'LEGAL_MADAGASCAR_2024',
            statut: 'NON_CONFORME',
            message: 'Un prêt de 200 000 Ar est en cours. Une décision formelle est requise.',
            actionRecommandee: 'Documenter la décision sur le prêt.',
            lienModule: null,
        },
        {
            code: 'CONGES_SOLDE_FIFO',
            titre: 'Solde des congés payés disponible',
            niveau: 'INFO',
            sourceNorme: 'LEGAL_MADAGASCAR_2024',
            statut: 'CONFORME',
            message: '5.5 jours de congés à liquider.',
            actionRecommandee: null,
            lienModule: null,
        }
    ]
};

beforeEach(() => {
    permissions.canWrite = true;
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    contratService.getMotifsSortie.mockResolvedValue([
        { code: 'DEMISSION', libelle: 'Démission', actif: true },
        { code: 'LICENCIEMENT_SIMPLE', libelle: 'Licenciement simple', actif: true }
    ]);
    dossierSortieService.obtenirDossier.mockResolvedValue(mockDossier);
    dossierSortieService.obtenirDossierEmploye.mockResolvedValue(mockDossier);
    dossierSortieService.sauvegarderDossier.mockResolvedValue({
        ...mockDossier,
        statutDossier: 'PRET_A_VALIDER',
    });
    dossierSortieService.enregistrerDelivranceCertificat.mockResolvedValue({
        ...mockDossier,
        certificatDelivre: true,
        dateDelivranceCertificat: '2026-10-31',
    });
    dossierSortieService.listerDossiers.mockResolvedValue([mockDossier]);
});

afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.clearAllMocks();
});

describe('Phase 1 — Dossier de sortie & Checklist métier', () => {
    it('laisse consulter la liste sans proposer de création à un lecteur', async () => {
        permissions.canWrite = false;
        await act(async () => root.render(<DossiersSortiePage />));
        expect(host.textContent).toContain('Rakoto Jean');
        expect(host.textContent).not.toContain('Nouveau dossier de sortie');
    });
    it('ouvre la page dédiée avec le salarié et les données de départ choisis', async () => {
        await act(async () => root.render(<DossiersSortiePage />));
        await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent.includes('Nouveau dossier de sortie')).click());
        await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent === 'Choisir le salarié').click());
        await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent.includes('Continuer vers le dossier')).click());
        expect(navigateSpy).toHaveBeenCalledWith(expect.stringMatching(/^\/paie\/dossiers-sortie\/nouveau\?employeId=7&dateDepart=.+&motif=DEMISSION$/));
    });
    it('affiche une date SQL horodatée dans le champ de date et confirme une sauvegarde réussie', async () => {
        dossierSortieService.obtenirDossier.mockResolvedValue({ ...mockDossier, dateDepart: '2026-10-31T00:00:00.000+03:00' });
        await act(async () => root.render(<DossierSortieModal isOpen idDossier={101} onClose={vi.fn()} />));
        expect(host.querySelector('input[type="date"]').value).toBe('2026-10-31');
        await changeField(host.querySelector('input[type="date"]'), '2026-10-30');
        await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent.includes('Sauvegarder le dossier')).click());
        expect(toastCalls.success).toHaveBeenCalledWith('Dossier préparatoire de sortie enregistré avec succès.');
    });
    it('affiche le dossier de sortie avec données sources en lecture seule et statuts', async () => {
        await act(async () => {
            root.render(
                <DossierSortieModal
                    isOpen
                    idDossier={101}
                    onClose={vi.fn()}
                />
            );
        });

        expect(dossierSortieService.obtenirDossier).toHaveBeenCalledWith(101);
        expect(host.textContent).toContain('Rakoto Jean');
        expect(host.textContent).toContain('EMP-007');
        await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent.includes('1. Situation')).click());
        expect(host.textContent).toContain('Pointage du mois');
        expect(host.textContent).toContain('22 j');
        expect(host.textContent).toContain('5.5 j');
        expect(host.textContent).toContain('1. Situation');
    });

    it('affiche la checklist dynamique avec distinction des règles légales et sévérité', async () => {
        await act(async () => {
            root.render(
                <DossierSortieModal
                    isOpen
                    idDossier={101}
                    onClose={vi.fn()}
                />
            );
        });

        const btnChecklist = [...host.querySelectorAll('button')].find(b => b.textContent.includes('Points à traiter'));
        expect(btnChecklist).toBeDefined();
        await act(async () => {
            btnChecklist.click();
        });

        expect(host.textContent).toContain('Cadre légal malgache');
        expect(host.textContent).toContain('Délivrance obligatoire du certificat de travail');
        expect(host.textContent).toContain('Prêts et avances non soldés');
        expect(host.textContent).toContain('BLOQUANT');
    });

    it('permet de documenter la décision sur prêt sans solde automatique', async () => {
        await act(async () => {
            root.render(
                <DossierSortieModal
                    isOpen
                    idDossier={101}
                    onClose={vi.fn()}
                />
            );
        });

        const btnPret = [...host.querySelectorAll('button')].find(b => b.textContent.includes('3. Prêts'));
        expect(btnPret).toBeDefined();
        await act(async () => {
            btnPret.click();
        });

        expect(host.textContent).toContain('Prêt d\'entreprise en cours de remboursement');
        expect(host.textContent).toContain('200 000 Ar');
        expect(host.textContent).toContain('Décision de gestion du prêt au départ du salarié');

        const justification = host.querySelector('textarea');
        await changeField(justification, 'Maintien par accord écrit');

        // Sauvegarder le dossier préparatoire
        const btnSave = [...host.querySelectorAll('button')].find(b => b.textContent.includes('Sauvegarder le dossier'));
        expect(btnSave).toBeDefined();
        await act(async () => {
            btnSave.click();
        });

        expect(dossierSortieService.sauvegarderDossier).toHaveBeenCalledWith(expect.objectContaining({
            idDossierSortie: 101,
            idEmploye: 7,
            decisionPret: 'MAINTIEN_ECHEANCIER',
        }));
    });

    it('permet d’enregistrer la délivrance du certificat de travail (Art. 50)', async () => {
        dossierSortieService.obtenirDossier.mockResolvedValue({
            ...mockDossier, statutContrat: 'ROMPU', dateFinContrat: '2026-10-01'
        });
        await act(async () => {
            root.render(
                <DossierSortieModal
                    isOpen
                    idDossier={101}
                    onClose={vi.fn()}
                />
            );
        });

        const btnOffboarding = [...host.querySelectorAll('button')].find(b => b.textContent.includes('5. Formalités'));
        expect(btnOffboarding).toBeDefined();
        await act(async () => {
            btnOffboarding.click();
        });

        expect(host.textContent).toContain('Certificat de travail');
        const btnDelivrer = [...host.querySelectorAll('button')].find(b => b.textContent.includes('Enregistrer la remise du certificat'));
        expect(btnDelivrer).toBeDefined();

        await act(async () => {
            btnDelivrer.click();
        });

        expect(dossierSortieService.enregistrerDelivranceCertificat).toHaveBeenCalledWith(101, expect.objectContaining({
            modeRemise: 'MAIN_PROPRE',
        }));
    });

    it('permet de simuler le STC, de vérifier le décompte chiffré et de sceller transactionnellement', async () => {
        const mockSimulation = {
            referenceSimulation: 'SIM-STC-101-ABCD1234EFEF5678',
            version: 2,
            decompte: {
                totalBrutStc: 450000,
                salaireProrata: 450000,
                soldeCongesRestant: 5.5,
                indemniteCongesPayes: 98500,
                moisPreavis: 0,
                indemnitePreavis: 0,
                indemniteLicenciement: 0,
                cotisationCnaps: 4500,
                cotisationSante: 4500,
                totalCotisationsSociales: 9000,
                netImposable: 441000,
                irsaNet: 15000,
                soldeAvancesDeduit: 0,
                soldePretsDeduit: 0,
                netAPayerStc: 520000,
                lignes: [
                    { code: 'SAL_PRORATA', libelle: 'Salaire prorata', sens: 'GAIN', base: 450000, taux: null, montant: 450000 },
                    { code: 'ICCP', libelle: 'Indemnité congés', sens: 'GAIN', base: 5.5, taux: null, montant: 98500 },
                    { code: 'CNAPS', libelle: 'Cotisation CNaPS', sens: 'RETENUE', base: 450000, taux: 1, montant: 45000 },
                ]
            }
        };

        const mockDossierPret = {
            ...mockDossier,
            pretAValider: true,
            checklist: mockDossier.checklist.map(c => ({ ...c, statut: 'CONFORME' }))
        };

        dossierSortieService.obtenirDossier.mockResolvedValue(mockDossierPret);
        dossierSortieService.simulerStc.mockResolvedValue(mockSimulation);
        dossierSortieService.scellerStc.mockResolvedValue({
            ...mockDossierPret,
            version: 3,
            statutDossier: 'VALIDE',
            idStc: 555,
            referenceSimulation: mockSimulation.referenceSimulation,
            snapshotSimulation: mockSimulation.decompte
        });

        await act(async () => {
            root.render(
                <DossierSortieModal
                    isOpen
                    idDossier={101}
                    onClose={vi.fn()}
                />
            );
        });

        // 1. Bouton Simuler le STC
        const btnSimuler = [...host.querySelectorAll('button')].find(b => b.textContent.includes('Simuler le STC'));
        expect(btnSimuler).toBeDefined();

        await act(async () => {
            btnSimuler.click();
        });

        expect(dossierSortieService.simulerStc).toHaveBeenCalledWith(101);

        // 2. Vérification de l'affichage du décompte et de la référence vérifiable
        expect(host.textContent).toContain('SIM-STC-101-ABCD1234EFEF5678');
        expect(host.textContent).toContain('520 000 Ar');
        expect(host.textContent).toContain('Confirmation et scellement transactionnel du STC');

        // 3. Scellement transactionnel
        const btnSceller = [...host.querySelectorAll('button')].find(b => b.textContent.includes('Confirmer et sceller définitivement le STC'));
        expect(btnSceller).toBeDefined();

        await act(async () => {
            btnSceller.click();
        });
        await act(async () => {
            [...host.querySelectorAll('button')].find(b => b.textContent === 'Valider la confirmation').click();
        });

        expect(dossierSortieService.scellerStc).toHaveBeenCalledWith(101, expect.objectContaining({
            referenceSimulation: 'SIM-STC-101-ABCD1234EFEF5678',
            version: 2
        }));
    });

    it('affiche le dashboard de suivi des dossiers de sortie avec les KPIs', async () => {
        await act(async () => {
            root.render(<DossiersSortiePage />);
        });

        expect(dossierSortieService.listerDossiers).toHaveBeenCalled();
        expect(host.textContent).toContain('Dossiers de sortie & STC');
        expect(host.textContent).toContain('Total départs enregistrés');
        expect(host.textContent).toContain('Rakoto Jean');
        expect(host.textContent).toContain('DEMISSION');
    });
});
