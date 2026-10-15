// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import PointageTab from '@/features/employees/components/tabs/PointageTab';
import PointageSoldePeriodeModal from '../components/modals/PointageSoldePeriodeModal';
import { attendanceService, periodePaieService, pointageSyntheseService } from '../api/attendanceApi';
vi.mock('../api/attendanceApi', () => ({ attendanceService: { getFicheEmploye: vi.fn() }, periodePaieService: { getPeriodOptions: vi.fn() }, pointageSyntheseService: { getAjustements: vi.fn(), creerAjustement: vi.fn() } }));
vi.mock('@/components/ui/feedback/Toast', () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock('@/components/ui/overlays/Modal', () => ({ default: ({ children }) => <div>{children}</div> }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let root, host;
const summary = { idEmploye: 7, nom: 'Agent', minutesDeficitHebdo: 600, minutesHs30Hebdo: 0, minutesAjustementPeriode: 0, minutesSoldePeriode: -600 };
const sheet = { idPeriode: 9, idEmploye: 7, libellePeriode: 'Septembre', statutPeriode: 'EN_COURS', availableActions: { CORRECT_DAY: true, ADJUST_BALANCE: true }, soldePeriode: summary,
 kpis: { primeAssiduiteEligible: 'false', retardCumule: '00h00', retardBrutMinutes: '0' }, recap: { deficitHebdo: '10h00', hs30Hebdo: '00h00', ajustements: '+02h00', soldeFinal: '−08h00', absenceFinale: '08h00', hs30Finales: '00h00' }, reglesAppliquees: {}, bilansHebdo: [{ debutSemaine: '2026-09-01', finSemaine: '2026-09-06' }], pointagesData: [{ date: '01/09', dateComplete: '2026-09-01', statut: 'PRESENT', canCorrect: true }] };
beforeEach(() => { vi.clearAllMocks(); host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); attendanceService.getFicheEmploye.mockResolvedValue(sheet); periodePaieService.getPeriodOptions.mockResolvedValue([{ idPeriodePaie: 9 }]); pointageSyntheseService.getAjustements.mockResolvedValue([]); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });
const render = async element => { await act(async () => root.render(<MemoryRouter>{element}</MemoryRouter>)); };
it('affiche le bilan backend, le journal et les accès DAF avec les détails repliés', async () => {
 await render(<PointageTab idEmploye={7} />);
 expect(host.textContent).toContain('−08h00');
 expect(host.textContent).toContain('Journal quotidien');
 expect(host.querySelector('button[aria-label="Corriger la journée du 01/09"]')).not.toBeNull();
 expect(host.textContent).toContain('Ajuster le solde de période');
 expect([...host.querySelectorAll('details')].every(e => !e.open)).toBe(true);
 // Retard nul ne permet pas au client de changer la décision d'assiduité du serveur.
 expect(host.textContent).not.toContain('Éligible à la prime d’assiduité');
});
it('affiche les codes des indemnités dans le journal et garde les libellés en aide', async () => {
 attendanceService.getFicheEmploye.mockResolvedValue({ ...sheet, indemnitesDisponibles: {
  DPLCT: { code: 'DPLCT', libelle: 'Indemnité de Déplacement', periodicite: 'MENSUEL' },
  REPAS: { code: 'REPAS', libelle: 'Indemnité de Repas', periodicite: 'JOURNALIER' }
 } });
 await render(<PointageTab idEmploye={7} />);
 const headers = [...host.querySelectorAll('th')];
 expect(headers.find(header => header.textContent.includes('DPLCT'))?.title).toBe('Indemnité de Déplacement');
 expect(headers.find(header => header.textContent.includes('REPAS'))?.title).toBe('Indemnité de Repas');
 expect(headers.some(header => header.textContent.includes('Indemnité de Déplacement'))).toBe(false);
});
it('filtre le journal sans perdre la matérialisation du congé validé', async () => {
 attendanceService.getFicheEmploye.mockResolvedValue({ ...sheet, pointagesData: [
  sheet.pointagesData[0],
  { date: '02/09', dateComplete: '2026-09-02', statut: 'CONGE', congeApprouve: true, canCorrect: false },
  { date: '03/09', dateComplete: '2026-09-03', statut: 'FERIE_CHOME', canCorrect: false }
 ] });
 await render(<PointageTab idEmploye={7} />);
 const exceptions = [...host.querySelectorAll('button')].find(button => button.textContent === 'Exceptions');
 await act(async () => exceptions.click());
 expect(host.textContent).toContain('Congé validé');
 expect(host.querySelector('button[aria-label="Corriger la journée du 01/09"]')).toBeNull();
 expect(host.textContent).toContain('02/09');
 expect(host.textContent).toContain('03/09');
});
it('masque les corrections quand le serveur les interdit', async () => {
 attendanceService.getFicheEmploye.mockResolvedValue({ ...sheet, availableActions: {}, pointagesData: [{ ...sheet.pointagesData[0], canCorrect: false }] });
 await render(<PointageTab idEmploye={7} />);
 expect(host.querySelector('button[aria-label="Corriger la journée du 01/09"]')).toBeNull();
 expect(host.textContent).not.toContain('Ajuster le solde de période');
 expect(host.textContent).toContain('Voir les régularisations');
});
it('la modale reprend le solde serveur sans le recalculer depuis les quantités locales', async () => {
 await render(<PointageSoldePeriodeModal employe={summary} idPeriodePaie={9} onClose={vi.fn()} />);
 expect(host.textContent).toContain('−08h00');
 expect(host.textContent).toContain('+02h00');
 expect(attendanceService.getFicheEmploye).toHaveBeenCalledWith(7, { period: 'periode', idPeriode: 9 });
});
it('une erreur backend ne laisse pas de formulaire de modification actif', async () => {
 attendanceService.getFicheEmploye.mockRejectedValue(new Error('Période indisponible'));
 await render(<PointageSoldePeriodeModal employe={summary} idPeriodePaie={9} onClose={vi.fn()} />);
 expect(host.textContent).toContain('Période indisponible');
 expect(host.textContent).not.toContain('Enregistrer l’ajustement');
});
it('affiche les deux tranches finales fournies par le backend après régularisation', async () => {
 attendanceService.getFicheEmploye.mockResolvedValue({ ...sheet, recap: { ...sheet.recap,
  hsTotalHebdo: '16h00', deficitHebdo: '02h00', ajustements: '−05h00', soldeFinal: '+09h00',
  hs30Finales: '08h00', hs50Finales: '01h00', absenceFinale: '00h00' } });
 await render(<PointageSoldePeriodeModal employe={summary} idPeriodePaie={9} onClose={vi.fn()} />);
 expect(host.textContent).toContain('16h00');
 expect(host.textContent).toContain('+09h00');
 expect(host.textContent).toContain('HS 30 % finales : 08h00');
 expect(host.textContent).toContain('HS 50 % finales : 01h00');
 expect(host.textContent).not.toContain('HS 50 %, dimanche');
});
it('affiche un EmptyState bienveillant si aucune période n’est configurée pour la date', async () => {
 const err = new Error('La période de paie demandée est introuvable.');
 err.response = { status: 400, data: { errorCode: 'POINTAGE_PERIOD_CONTEXT_REQUIRED', message: 'La période de paie demandée est introuvable.' } };
 attendanceService.getFicheEmploye.mockRejectedValue(err);
 periodePaieService.getPeriodOptions.mockResolvedValue([]);
 await render(<PointageTab idEmploye={7} />);
 expect(host.textContent).toContain('Aucune période de paie active');
 expect(host.textContent).toContain('Gérer les périodes de paie');
 expect(host.textContent).not.toContain('Impossible de charger les données de pointage');
});
