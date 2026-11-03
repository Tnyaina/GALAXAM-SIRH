import { useState, useEffect, useCallback } from 'react';
import { CalendarRange, FileSpreadsheet, RefreshCw, SlidersHorizontal, ShieldAlert, Search, CheckCircle2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Modal from '@/components/ui/overlays/Modal';
import Loading from '@/components/ui/feedback/Loading';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Pagination from '@/components/ui/navigation/Pagination';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import { useToast } from '@/components/ui/feedback/Toast';
import congeService from '@/features/leave/api/congeService';
import employeService from '@/features/employees/api/employeService';
import { formatMoneyAr, formatLeaveRateAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';
import InitialisationSoldeModal from '@/features/leave/components/InitialisationSoldeModal';
import DroitsLotsPanel from '@/features/leave/components/DroitsLotsPanel';
import { ACQUISITION_CORRECTION_LIMIT, ACQUISITION_CORRECTION_STEPS } from '@/features/leave/utils/acquisitionCorrectionGuidance';

const jours = (value) => Number(value || 0).toFixed(2);
const dateFr = (value) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('fr-FR') : '—';
const moisFr = (mois, annee) => `${String(mois).padStart(2, '0')}/${annee}`;
const statutReference = (statut) => ({
  OUVERTE: { libelle: 'Ouverte', variant: 'success' },
  CLOTUREE: { libelle: 'Clôturée', variant: 'neutral' },
  ARCHIVEE: { libelle: 'Archivée', variant: 'neutral' },
}[statut] || { libelle: statut || 'État inconnu', variant: 'neutral' });
const lendemain = (date) => {
  const [annee, mois, jour] = date.split('-').map(Number);
  const valeur = new Date(Date.UTC(annee, mois - 1, jour + 1));
  return valeur.toISOString().slice(0, 10);
};
const premiereDateActivation = (periode) => lendemain(
  periode.derniereAcquisition && periode.derniereAcquisition >= periode.dateFin
    ? periode.derniereAcquisition : periode.dateFin
);

/** Contrôle DAF : la paie crée les références ; la DAF vérifie les mois et active le taux calculé. */
export default function PeriodesReferenceTab({
  initialIdEmploye = null,
  canValidate = false,
  periodesEnAttente = [],
  pendingStatus = 'success',
  onReload = null
}) {
  const toast = useToast();
  const [employeId, setEmployeId] = useState(initialIdEmploye);
  const [selectedEmployee, setSelectedEmployee] = useState(null);
  const [currentSituation, setCurrentSituation] = useState(null);
  const [employeePickerOpen, setEmployeePickerOpen] = useState(!initialIdEmploye);
  const [periodes, setPeriodes] = useState([]);
  const [showCutoverModal, setShowCutoverModal] = useState(false);
  const [stock, setStock] = useState(null);
  const [stockError, setStockError] = useState(null);
  const [mouvements, setMouvements] = useState([]);
  const [mouvementsError, setMouvementsError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [periodeControle, setPeriodeControle] = useState(null);
  const [lignes, setLignes] = useState([]);
  const [historique, setHistorique] = useState([]);
  const [chargementMois, setChargementMois] = useState(false);
  const [erreurMois, setErreurMois] = useState(null);
  const [ligneCorrection, setLigneCorrection] = useState(null);
  const [valeur, setValeur] = useState('');
  const [motif, setMotif] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [activation, setActivation] = useState(null);
  const [situationDate, setSituationDate] = useState(null);
  const [activationError, setActivationError] = useState(null);
  const [confirmation, setConfirmation] = useState(false);
  const [guideVisible, setGuideVisible] = useState(false);
  const [pendingSearch, setPendingSearch] = useState('');
  const [pendingPage, setPendingPage] = useState(1);
  const pendingMatches = periodesEnAttente.filter((item) =>
    `${item.prenom} ${item.nom} ${item.matricule} ${item.dateFin}`.toLocaleLowerCase('fr')
      .includes(pendingSearch.trim().toLocaleLowerCase('fr')));
  const pendingPageItems = pendingMatches.slice((pendingPage - 1) * 10, pendingPage * 10);
  useEffect(() => { setPendingPage(1); }, [periodesEnAttente.length]);
  const referencesAnciennesOuvertes = periodes.filter((periode) =>
    periode.statut === 'OUVERTE' && periode.dateFin
      && periode.dateFin < new Date().toLocaleDateString('sv-SE'));

  useEffect(() => {
    if (!activation?.dateEffet || activation.dateEffet < premiereDateActivation(activation.periode)) {
      setSituationDate(null);
      return;
    }
    let active = true;
    setSituationDate(null);
    setActivationError(null);
    congeService.getSituationEmploye(activation.periode.idEmploye, activation.dateEffet)
      .then((situation) => { if (active) setSituationDate(situation); })
      .catch((err) => { if (active) setActivationError(getErrorMessage(err, 'Taux applicable indisponible à cette date.')); });
    return () => { active = false; };
  }, [activation?.dateEffet, activation?.periode]);

  const chargerCycles = useCallback(async (id) => {
    if (!id) {
      setPeriodes([]); setStock(null); setStockError(null); setCurrentSituation(null);
      setMouvements([]); setMouvementsError(null);
      return;
    }
    setLoading(true);
    setError(null);
    setStockError(null);
    setMouvementsError(null);
    try {
      const [cycles, lots, mouvementResult, situationResult] = await Promise.allSettled([
        congeService.getPeriodesReferenceEmploye(id),
        congeService.getValorisationLots(id),
        congeService.getMouvementsEmploye(id),
        congeService.getSituationEmploye(id),
      ]);
      if (cycles.status === 'rejected') throw cycles.reason;
      setPeriodes(Array.isArray(cycles.value) ? cycles.value : []);
      setStock(lots.status === 'fulfilled' ? lots.value : null);
      setCurrentSituation(situationResult.status === 'fulfilled' ? situationResult.value : null);
      if (lots.status === 'rejected') setStockError(getErrorMessage(lots.reason, 'La situation des lots est indisponible.'));
      setMouvements(mouvementResult.status === 'fulfilled' && Array.isArray(mouvementResult.value)
        ? mouvementResult.value : []);
      if (mouvementResult.status === 'rejected') {
        setMouvementsError(getErrorMessage(mouvementResult.reason, 'Le registre des mouvements est indisponible.'));
      }
    } catch (err) {
      setError(getErrorMessage(err, 'Impossible de charger les références congés.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { setEmployeId(initialIdEmploye); }, [initialIdEmploye]);
  useEffect(() => { chargerCycles(employeId); }, [employeId, chargerCycles]);
  useEffect(() => {
    if (!employeId || selectedEmployee && Number(selectedEmployee.idEmploye ?? selectedEmployee.id_employe) === Number(employeId)) return undefined;
    let active = true;
    employeService.getEmployeFicheDetail(employeId).then((result) => {
      if (active) setSelectedEmployee(result?.employe || result);
    }).catch(() => { if (active) setSelectedEmployee(null); });
    return () => { active = false; };
  }, [employeId, selectedEmployee]);

  const chargerMois = async (periode) => {
    setPeriodeControle(periode);
    setLigneCorrection(null);
    setChargementMois(true);
    setErreurMois(null);
    try {
      const [acquisitions, corrections] = await Promise.all([
        congeService.getMoisReference(periode.idPeriodeReference),
        congeService.getCorrectionsValorisation(periode.idPeriodeReference),
      ]);
      setLignes(acquisitions);
      setHistorique(corrections);
    } catch (err) {
      setLignes([]);
      setHistorique([]);
      setErreurMois(getErrorMessage(err, 'Impossible de charger les acquisitions et leur historique.'));
    } finally {
      setChargementMois(false);
    }
  };

  const exporter = async (periode) => {
    try {
      await congeService.telechargerEtatCongesExcel(employeId, periode.idPeriodeReference);
    } catch (err) {
      toast.error(getErrorMessage(err, "Impossible d'exporter l'état Excel."));
    }
  };

  const corriger = async (event) => {
    event.preventDefault();
    if (!ligneCorrection || !motif.trim() || valeur === '') return;
    setActionLoading(true);
    try {
      await congeService.corrigerValorisationMois(periodeControle.idPeriodeReference,
        ligneCorrection.idJournal, { nouvelleValeur: Number(valeur), motif: motif.trim() });
      await chargerMois(periodeControle);
      await chargerCycles(employeId);
      toast.success('Valorisation rectifiée et historisée.');
    } catch (err) {
      toast.error(getErrorMessage(err, 'Correction impossible : vérifiez le cycle et les lots déjà consommés.'));
    } finally {
      setActionLoading(false);
    }
  };

  const ouvrirActivation = (periode) => {
    setEmployeId(periode.idEmploye);
    if (periode.nom) setSelectedEmployee(periode);
    setEmployeePickerOpen(false);
    setActivation({ periode, dateEffet: premiereDateActivation(periode) });
    setConfirmation(false);
    setActivationError(null);
  };

  const activer = async (event) => {
    event.preventDefault();
    if (!activation || !confirmation || !situationDate
      || activation.dateEffet < premiereDateActivation(activation.periode)) return;
    setActionLoading(true);
    try {
      await congeService.activerTauxCalculeReference(activation.periode.idPeriodeReference, activation.dateEffet);
      setActivation(null);
      await chargerCycles(employeId);
      if (onReload) onReload();
      toast.success(`TCONGE activé à compter du ${dateFr(activation.dateEffet)}.`);
    } catch (err) {
      setActivationError(getErrorMessage(err, 'Activation du TCONGE impossible.'));
    } finally {
      setActionLoading(false);
    }
  };

  return <div className="space-y-4">
    {pendingStatus === 'success' && periodesEnAttente.length === 0 && <div role="status" className="flex flex-wrap items-center gap-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-4 py-3 text-sm">
      <CheckCircle2 size={18} className="shrink-0 text-[var(--color-success-700)]" aria-hidden="true" />
      <strong className="text-[var(--color-fg)]">Aucun taux à activer actuellement</strong>
      <span className="text-[var(--color-fg-muted)]">Après la clôture annuelle des droits, les salariés concernés apparaîtront ici.</span>
    </div>}
    {periodesEnAttente && periodesEnAttente.length > 0 && (
      <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]" aria-label="Taux de congé prêts à activer">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--color-border-subtle)] px-4 py-3">
          <div>
            <h2 className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]"><ShieldAlert size={16} className="text-[var(--color-warning-700)]" />{periodesEnAttente.length} taux prêt{periodesEnAttente.length > 1 ? 's' : ''} à activer</h2>
            <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Vérifiez la date d’effet de chaque salarié avant de confirmer. Aucune activation automatique.</p>
          </div>
          <Input type="search" iconLeft={<Search size={14} />} value={pendingSearch} onChange={(event) => { setPendingSearch(event.target.value); setPendingPage(1); }} placeholder="Nom ou matricule…" aria-label="Rechercher un taux à activer" className="w-full sm:w-64" />
        </div>
        {pendingPageItems.length > 0 ? <div className="divide-y divide-[var(--color-border-subtle)]">{pendingPageItems.map((item) =>
          <div key={item.idPeriodeReference} className="flex flex-wrap items-center gap-3 px-4 py-3">
            <div className="min-w-40 flex-1"><p className="text-sm font-semibold text-[var(--color-fg)]">{item.prenom} {item.nom}</p><p className="text-xs text-[var(--color-fg-muted)]">{item.matricule} · Cycle terminé le {dateFr(item.dateFin)}</p></div>
            <div className="text-sm"><span className="text-xs text-[var(--color-fg-muted)]">Nouveau taux</span><strong className="block font-data">{formatLeaveRateAr(item.tauxCalculeCloture)} / jour</strong></div>
            <div className="text-xs text-[var(--color-fg-muted)]">Dès le {dateFr(premiereDateActivation(item))}</div>
            {canValidate ? <Button size="sm" variant="primary" onClick={() => ouvrirActivation(item)}>Vérifier et activer</Button>
              : <Button size="sm" variant="outline" onClick={() => setEmployeId(item.idEmploye)}>Voir le dossier</Button>}
          </div>)}</div> : <p className="px-4 py-5 text-sm text-[var(--color-fg-muted)]">Aucun salarié ne correspond à cette recherche.</p>}
        {pendingMatches.length > 10 && <Pagination currentPage={pendingPage} totalItems={pendingMatches.length} pageSize={10} onPageChange={setPendingPage} />}
      </section>
    )}

    <Card padding bordered>
      <div className="flex flex-wrap items-center gap-3">
        {employeId && !employeePickerOpen && <>
          <span className="grid h-10 w-10 shrink-0 place-items-center bg-[var(--color-brand-600)] text-sm font-bold text-[var(--color-on-color)]" aria-hidden="true">{(selectedEmployee?.prenom || selectedEmployee?.nom || 'S').slice(0, 1).toUpperCase()}</span>
          <div className="min-w-0 flex-1"><h2 className="text-base font-bold text-[var(--color-fg)]">{[selectedEmployee?.prenom, selectedEmployee?.nom].filter(Boolean).join(' ') || `Salarié #${employeId}`}</h2><p className="text-xs text-[var(--color-fg-muted)]">Matricule : {selectedEmployee?.matricule || '—'}</p></div>
          <Button variant="outline" size="sm" onClick={() => setEmployeePickerOpen(true)}>Changer de salarié</Button>
        </>}
        {(!employeId || employeePickerOpen) && <div className="min-w-60 flex-1 max-w-xl"><EmployeSearchSelect label="Collaborateur" value={employeePickerOpen ? null : employeId}
          focusOnMount={Boolean(employeId && employeePickerOpen)}
          placeholder="Rechercher par nom ou matricule…"
          onChange={(id, employee) => { if (id) { setEmployeId(Number(id)); setSelectedEmployee(employee); setEmployeePickerOpen(false); } }} /></div>}
        <div className="ml-auto flex flex-wrap gap-2">
          <Button type="button" variant="outline" size="sm" aria-expanded={guideVisible} onClick={() => setGuideVisible((visible) => !visible)}>{guideVisible ? 'Masquer l’explication' : 'Explication du calcul'}</Button>
          {employeId && <Button variant="outline" size="sm" iconLeft={<RefreshCw size={14} />}
            onClick={() => chargerCycles(employeId)} disabled={loading}>Actualiser</Button>}
        </div>
      </div>
      {guideVisible && <Alert type="info" className="mt-3 py-2 px-2.5" message="La clôture de paie crée la référence annuelle et valorise les droits acquis. À la clôture du cycle, elle calcule un taux moyen ; la DAF l'active séparément avec une date d'effet explicite. Les anciens lots gardent leur valorisation et sont consommés en FIFO." />}
    </Card>
    {error && <Alert type="error" message={error} />}
    {referencesAnciennesOuvertes.length > 0 && <Alert type="warning" className="py-2 px-2.5" message={
      `Cycle antérieur encore ouvert (${referencesAnciennesOuvertes.map((periode) => `${dateFr(periode.dateDebut)} → ${dateFr(periode.dateFin)}`).join(', ')}). Vérifiez la clôture de la paie de décembre et les acquisitions de ce cycle avant d'activer un nouveau taux. La référence de l'année suivante peut être créée séparément.`
    } />}

    {!employeId ? <EmptyState icon={CalendarRange} title="Choisissez un collaborateur"
      description="Consultez ses acquisitions annuelles et leurs valorisations." />
      : loading ? <SkeletonTable rows={4} columns={5} />
      : <>
        <div className="grid grid-cols-2 divide-x divide-y divide-[var(--color-border-subtle)] border border-[var(--color-border-subtle)] bg-[var(--color-surface)] lg:grid-cols-4 lg:divide-y-0">
          {[
            { label: 'Stock disponible', value: stock ? `${jours(stock.soldeJours)} j · ${formatMoneyAr(stock.soldeValorisation)}` : 'Indisponible', detail: 'Droits utilisables aujourd’hui' },
            { label: 'TCONGE applicable', value: currentSituation?.tauxApplicable?.montant != null ? `${formatLeaveRateAr(currentSituation.tauxApplicable.montant)} / j` : 'À vérifier', detail: currentSituation?.tauxApplicable?.dateEffet ? `Depuis le ${dateFr(currentSituation.tauxApplicable.dateEffet)}` : 'Aucun taux actif trouvé' },
            { label: `Acquis en ${periodes[0]?.dateDebut?.slice(0, 4) || 'année courante'}`, value: `${jours(periodes[0]?.cumulDroitsCourant)} j`, detail: `Valeur : ${formatMoneyAr(periodes[0]?.cumulValorisationCourante || 0)} · cumul annuel` },
            { label: `Période ${periodes[0]?.dateDebut?.slice(0, 4) || 'annuelle'}`, value: statutReference(periodes[0]?.statut).libelle, detail: periodes[0] ? `${dateFr(periodes[0].dateDebut)} → ${dateFr(periodes[0].dateFin)}` : 'Aucune période créée' },
          ].map((metric) => <div key={metric.label} className="min-w-0 px-4 py-3"><p className="text-xs text-[var(--color-fg-muted)]">{metric.label}</p><strong className="mt-1 block font-data text-base font-bold text-[var(--color-fg)]">{metric.value}</strong><p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">{metric.detail}</p></div>)}
        </div>
        <DroitsLotsPanel
          situation={stock}
          movements={mouvements}
          loading={loading}
          error={stockError}
          movementsError={mouvementsError}
          onRetry={() => chargerCycles(employeId)}
          headerAction={canValidate ? <Button variant="outline" size="sm" iconLeft={<SlidersHorizontal size={14} />} onClick={() => setShowCutoverModal(true)}>Reprendre l’historique</Button> : null}
        />
        <Card bordered className="overflow-hidden" shadow="none">
        <CardHeader className="px-5 py-4 sm:px-6 sm:py-5">
          <CardTitle size="lg">Acquisitions par période</CardTitle>
          <p className="mt-1 text-sm text-[var(--color-fg-muted)]">
            Droits acquis pendant chaque année, pas le solde disponible toutes années confondues.
          </p>
        </CardHeader>
        <CardBody className="p-0">
          {periodes.length === 0 ? (
            <div className="p-6 space-y-4">
              <p className="text-sm text-[var(--color-fg-muted)]">
                Aucune acquisition clôturée sur ce profil. La première clôture de paie de l'année ouvrira automatiquement sa référence annuelle.
              </p>
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                <div>
                  <p className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    Reprise de solde antérieur
                  </p>
                  <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                    Pour un salarié ayant des droits acquis avant le déploiement, initialisez le stock et le Taux congé certifiés par la DAF.
                  </p>
                </div>
              </div>
            </div>
          ) : <>
            <p className="mb-2 text-xs text-[var(--color-fg-muted)] sm:hidden">
              Faites défiler le tableau horizontalement pour consulter les autres colonnes.
            </p>
            <Table bordered={false} rowHover={false} aria-label="Périodes de référence et cumuls des congés" tableClassName="[&_th]:px-5 [&_th]:py-3 [&_td]:px-5 [&_td]:py-5 sm:[&_th]:px-6 sm:[&_td]:px-6">
            <Thead><Tr><Th>Période et état</Th><Th>Acquisitions de l'année</Th>
              <Th>TCONGE calculé et applicable</Th><Th>Contrôles</Th></Tr></Thead>
            <Tbody>{periodes.map((periode) => {
              const ouverte = periode.statut === 'OUVERTE';
              const statut = statutReference(periode.statut);
              return <Tr key={periode.idPeriodeReference}>
                <Td>
                  <div className="space-y-1.5">
                    <p className="text-base font-semibold">{dateFr(periode.dateDebut)} → {dateFr(periode.dateFin)}</p>
                    <Badge variant={statut.variant} size="xs">{statut.libelle}</Badge>
                  </div>
                </Td>
                <Td>
                  <strong className="block font-data text-base">{jours(ouverte ? periode.cumulDroitsCourant : periode.cumulDroitsCloture)} j</strong>
                  <span className="block text-sm text-[var(--color-fg-muted)]">Valeur acquise : {formatMoneyAr(ouverte ? periode.cumulValorisationCourante : periode.cumulValorisationCloture)}</span>
                </Td>
                <Td>
                  <strong className="block font-data text-base">{periode.dateEffetActivation ? `${formatLeaveRateAr(periode.tauxActive)} / j` : ouverte ? 'Taux calculé après clôture' : 'À activer'}</strong>
                  {periode.dateEffetActivation
                    ? <p className="text-sm text-[var(--color-fg-muted)]">Applicable depuis le {dateFr(periode.dateEffetActivation)} · calculé : {formatLeaveRateAr(periode.tauxCalculeCloture)}</p>
                    : !ouverte && Number(periode.tauxCalculeCloture) > 0
                      ? <div className="mt-1 space-y-1"><p className="text-xs text-[var(--color-fg-muted)]">Taux calculé : {formatLeaveRateAr(periode.tauxCalculeCloture)} / j</p><Badge variant="warning">En attente d’activation</Badge>
                        {canValidate
                          ? <div><Button size="xs" variant="primary" onClick={() => ouvrirActivation(periode)}>Activer le TCONGE</Button></div>
                          : <p className="text-[10px] text-[var(--color-fg-muted)]">Activation réservée à un utilisateur autorisé.</p>}</div>
                      : null}
                </Td>
                <Td><div className="flex flex-wrap gap-2">
                  <Button size="xs" variant="outline" onClick={() => chargerMois(periode)}>Vérifier les mois</Button>
                  <Button size="xs" variant="outline" iconLeft={<FileSpreadsheet size={13} />}
                    onClick={() => exporter(periode)}>Excel</Button>
                </div></Td>
              </Tr>;
            })}</Tbody>
            </Table>
          </>}
        </CardBody>
        </Card>
      </>}

    <Modal isOpen={Boolean(periodeControle)} onClose={() => setPeriodeControle(null)}
      title="Contrôle mensuel des acquisitions" size="2xl">
      <div className="p-4 space-y-4">
        <Alert type="info" title="Choisir la procédure selon l'état du droit">
          <ol className="list-decimal space-y-1 pl-5">
            {ACQUISITION_CORRECTION_STEPS.map((step) => <li key={step}>{step}</li>)}
          </ol>
          <p className="mt-2">{ACQUISITION_CORRECTION_LIMIT}</p>
        </Alert>
        {erreurMois && <Alert type="error" title="Données mensuelles indisponibles" message={erreurMois}
          actionLabel="Réessayer" onAction={() => chargerMois(periodeControle)} />}
        {chargementMois ? <div role="status" className="py-8 text-center"><Loading size="sm" label="Chargement des acquisitions et rectifications…" /></div> : !erreurMois && <>
          {lignes.length === 0 ? <EmptyState title="Aucun droit acquis sur cette référence" description="Aucune acquisition mensuelle n'est enregistrée pour cette période." /> : <div className="overflow-x-auto"><Table aria-label="Acquisitions mensuelles de la période">
            <Thead><Tr><Th>Mois · journal</Th><Th className="text-right">Jours acquis</Th>
              <Th className="text-right">Valeur du lot</Th><Th className="text-right">Cumul annuel</Th><Th>Action</Th></Tr></Thead>
            <Tbody>{lignes.map((ligne) => <Tr key={ligne.idJournal}>
              <Td><span className="font-medium">{moisFr(ligne.mois, ligne.annee)}</span><span className="block text-[10px] text-[var(--color-fg-muted)]">Journal #{ligne.idJournal}</span></Td>
              <Td className="text-right">{jours(ligne.jours)} j</Td>
              <Td className="text-right">{formatMoneyAr(ligne.valeur)}</Td>
              <Td className="text-right">{formatMoneyAr(ligne.cumul)}</Td>
              <Td>{canValidate && ligne.statutReference === 'OUVERTE' &&
                <Button size="xs" variant="outline" onClick={() => {
                  setLigneCorrection(ligne); setValeur(String(ligne.valeur)); setMotif('');
                }}>Rectifier</Button>}</Td>
            </Tr>)}</Tbody>
          </Table></div>}
          <section aria-labelledby="corrections-history-title" className="border-t border-[var(--color-border-subtle)] pt-3 space-y-2">
            <h3 id="corrections-history-title" className="text-sm font-semibold">Historique des rectifications</h3>
            {historique.length > 0 ? <div className="overflow-x-auto"><Table aria-label="Historique des rectifications de valorisation">
              <Thead><Tr><Th>Mois · journal</Th><Th>Valeur précédente → nouvelle</Th><Th>Motif</Th><Th>Utilisateur</Th></Tr></Thead>
              <Tbody>{historique.map((item) => {
                const ligne = lignes.find((row) => row.idJournal === item.idJournal);
                return <Tr key={item.idCorrection}>
                  <Td>{ligne ? moisFr(ligne.mois, ligne.annee) : 'Journal'}<span className="block text-[10px] text-[var(--color-fg-muted)]">#{item.idJournal}</span></Td>
                  <Td>{formatMoneyAr(item.ancienneValeur)} → {formatMoneyAr(item.nouvelleValeur)}</Td>
                  <Td className="max-w-xs whitespace-normal">{item.motif}</Td>
                  <Td>#{item.corrigePar}</Td>
                </Tr>;
              })}</Tbody>
            </Table></div> : <p className="text-xs text-[var(--color-fg-muted)]">Aucune rectification n'est enregistrée sur cette période.</p>}
          </section>
        </>}
        {ligneCorrection && <form onSubmit={corriger} className="space-y-3 border-t pt-4">
          <p className="font-semibold">Journal #{ligneCorrection.idJournal} · acquisition {moisFr(ligneCorrection.mois, ligneCorrection.annee)}</p>
          <p className="text-xs">Valeur actuelle : {formatMoneyAr(ligneCorrection.valeur)} ; {jours(ligneCorrection.jours)} jours inchangés.</p>
          <Input label="Nouvelle valeur acquise (Ar)" type="number" min="0" step="0.01"
            value={valeur} onChange={(event) => setValeur(event.target.value)} required />
          <Input label="Motif DAF" value={motif} onChange={(event) => setMotif(event.target.value)} required />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setLigneCorrection(null)}>Annuler</Button>
            <Button type="submit" variant="primary" loading={actionLoading}
              disabled={!motif.trim() || valeur === ''}>Enregistrer</Button>
          </div>
        </form>}
      </div>
    </Modal>

    <Modal isOpen={Boolean(activation)} onClose={() => !actionLoading && setActivation(null)}
      title="Activer le TCONGE calculé" size="lg">
      {activation && <form onSubmit={activer} className="space-y-4">
        <p className="text-sm">Cycle {dateFr(activation.periode.dateDebut)} → {dateFr(activation.periode.dateFin)}</p>
        <p className="text-sm">Taux calculé à la clôture : <strong>{formatLeaveRateAr(activation.periode.tauxCalculeCloture)} / jour</strong></p>
        <Input label="Date d'effet du nouveau taux" type="date" required
          min={premiereDateActivation(activation.periode)}
          value={activation.dateEffet} onChange={(event) => {
            setActivation({ ...activation, dateEffet: event.target.value });
            setConfirmation(false);
          }} />
        {activation.dateEffet < premiereDateActivation(activation.periode) &&
          <Alert type="error" message="La date d'effet doit suivre la fin du cycle et la dernière acquisition clôturée." />}
        {situationDate && <p className="text-sm">
          Taux actuellement applicable au {dateFr(activation.dateEffet)} :{' '}
          <strong>{situationDate.tauxApplicable?.montant != null
            ? `${formatLeaveRateAr(situationDate.tauxApplicable.montant)} / jour`
            : 'aucun taux actif'}</strong>
          {situationDate.tauxApplicable?.dateEffet && ` (depuis le ${dateFr(situationDate.tauxApplicable.dateEffet)})`}
        </p>}
        {activationError && <Alert type="error" message={activationError} />}
        <p className="text-xs text-[var(--color-fg-muted)]">La date proposée suit le cycle et toute acquisition déjà clôturée{activation.periode.derniereAcquisition ? ` (dernière le ${dateFr(activation.periode.derniereAcquisition)})` : ''}. Si janvier est déjà clôturé, régularisez les écarts dans la paie. L'activation ne revalorise ni les anciens lots ni les bulletins validés.</p>
        <label className="flex gap-2 text-sm items-start"><input type="checkbox" checked={confirmation}
          onChange={(event) => setConfirmation(event.target.checked)} />
          J'ai vérifié la date d'effet et le taux actuellement applicable.</label>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => setActivation(null)}>Annuler</Button>
          <Button type="submit" variant="primary" loading={actionLoading}
            disabled={!confirmation || !situationDate || activation.dateEffet < premiereDateActivation(activation.periode) || actionLoading}>Confirmer l'activation</Button>
        </div>
      </form>}
    </Modal>

    {showCutoverModal && employeId && (
      <InitialisationSoldeModal
        isOpen={showCutoverModal}
        onClose={() => setShowCutoverModal(false)}
        onSuccess={() => {
          chargerCycles(employeId);
          if (onReload) onReload();
        }}
        employe={{ idEmploye: employeId }}
      />
    )}
  </div>;
}
