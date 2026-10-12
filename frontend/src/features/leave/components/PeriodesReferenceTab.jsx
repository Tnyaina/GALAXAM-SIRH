import { useState, useEffect, useCallback } from 'react';
import { CalendarRange, FileSpreadsheet, RefreshCw, SlidersHorizontal, ShieldAlert } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Modal from '@/components/ui/overlays/Modal';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import { useToast } from '@/components/ui/feedback/Toast';
import congeService from '@/features/leave/api/congeService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';
import InitialisationSoldeModal from '@/features/leave/components/InitialisationSoldeModal';

const jours = (value) => Number(value || 0).toFixed(2);
const dateFr = (value) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('fr-FR') : '—';
const moisFr = (mois, annee) => `${String(mois).padStart(2, '0')}/${annee}`;
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
  onReload = null
}) {
  const toast = useToast();
  const [employeId, setEmployeId] = useState(initialIdEmploye);
  const [periodes, setPeriodes] = useState([]);
  const [showCutoverModal, setShowCutoverModal] = useState(false);
  const [stock, setStock] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [periodeControle, setPeriodeControle] = useState(null);
  const [lignes, setLignes] = useState([]);
  const [historique, setHistorique] = useState([]);
  const [ligneCorrection, setLigneCorrection] = useState(null);
  const [valeur, setValeur] = useState('');
  const [motif, setMotif] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [activation, setActivation] = useState(null);
  const [situationDate, setSituationDate] = useState(null);
  const [activationError, setActivationError] = useState(null);
  const [confirmation, setConfirmation] = useState(false);
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
    congeService.getSituationEmploye(employeId, activation.dateEffet)
      .then((situation) => { if (active) setSituationDate(situation); })
      .catch((err) => { if (active) setActivationError(getErrorMessage(err, 'Taux applicable indisponible à cette date.')); });
    return () => { active = false; };
  }, [activation?.dateEffet, activation?.periode, employeId]);

  const chargerCycles = useCallback(async (id) => {
    if (!id) { setPeriodes([]); setStock(null); return; }
    setLoading(true);
    setError(null);
    try {
      const [cycles, lots] = await Promise.allSettled([
        congeService.getPeriodesReferenceEmploye(id),
        congeService.getValorisationLots(id),
      ]);
      if (cycles.status === 'rejected') throw cycles.reason;
      setPeriodes(Array.isArray(cycles.value) ? cycles.value : []);
      setStock(lots.status === 'fulfilled' ? lots.value : null);
    } catch (err) {
      setError(getErrorMessage(err, 'Impossible de charger les références congés.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { setEmployeId(initialIdEmploye); }, [initialIdEmploye]);
  useEffect(() => { chargerCycles(employeId); }, [employeId, chargerCycles]);

  const chargerMois = async (periode) => {
    setPeriodeControle(periode);
    setLigneCorrection(null);
    setActionLoading(true);
    try {
      const [acquisitions, corrections] = await Promise.all([
        congeService.getMoisReference(periode.idPeriodeReference),
        congeService.getCorrectionsValorisation(periode.idPeriodeReference),
      ]);
      setLignes(acquisitions);
      setHistorique(corrections);
    } catch (err) {
      setPeriodeControle(null);
      toast.error(getErrorMessage(err, 'Impossible de charger les acquisitions.'));
    } finally {
      setActionLoading(false);
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
    {periodesEnAttente && periodesEnAttente.length > 0 && (
      <div className="p-4 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 space-y-2.5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] flex items-center gap-2">
            <ShieldAlert size={16} className="text-[var(--color-warning-500)] shrink-0" />
            {periodesEnAttente.length} cycle{periodesEnAttente.length > 1 ? 's' : ''} annuel{periodesEnAttente.length > 1 ? 's' : ''} clôturé{periodesEnAttente.length > 1 ? 's' : ''} en attente d'activation DAF
          </span>
          <span className="text-xs text-[var(--color-fg-muted)]">Sélectionnez un collaborateur pour activer son taux</span>
        </div>
        <div className="flex flex-wrap gap-2 pt-1">
          {periodesEnAttente.map((item) => (
            <button
              key={item.idPeriodeReference}
              type="button"
              onClick={() => {
                setEmployeId(item.idEmploye);
                chargerCycles(item.idEmploye);
              }}
              className={`px-3 py-1.5 text-xs font-medium border flex items-center gap-2 transition-all cursor-pointer ${
                employeId === item.idEmploye
                  ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)] border-[var(--color-brand-500)] shadow-xs'
                  : 'bg-[var(--color-surface)] border-[var(--color-warning-500)]/40 text-[var(--color-fg)] hover:border-[var(--color-brand-500)]'
              }`}
            >
              <span className="font-semibold">{item.prenom} {item.nom}</span>
              {item.matricule && <span className="opacity-75">({item.matricule})</span>}
              <span className="text-[10px] font-mono px-1.5 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)]">
                {item.dateDebut.slice(0, 4)} : {formatMoneyAr(item.tauxCalculeCloture)}/j
              </span>
            </button>
          ))}
        </div>
      </div>
    )}

    <Card padding bordered>
      <div className="flex flex-wrap items-end gap-3 justify-between">
        <div className="flex-1 min-w-56 max-w-xl">
          <EmployeSearchSelect label="Collaborateur" value={employeId}
            placeholder="Rechercher par nom ou matricule…"
            onChange={(id) => setEmployeId(id ? Number(id) : null)} />
        </div>
        {employeId && <Button variant="outline" size="sm" iconLeft={<RefreshCw size={14} />}
          onClick={() => chargerCycles(employeId)} disabled={loading}>Actualiser</Button>}
      </div>
    </Card>

    <Alert type="info" className="py-2 px-2.5" message="La clôture de paie crée la référence annuelle et valorise les droits acquis. À la clôture du cycle, elle calcule un taux moyen ; la DAF l'active séparément avec une date d'effet explicite. Les anciens lots gardent leur valorisation et sont consommés en FIFO." />
    {error && <Alert type="error" message={error} />}
    {referencesAnciennesOuvertes.length > 0 && <Alert type="warning" className="py-2 px-2.5" message={
      `Cycle antérieur encore ouvert (${referencesAnciennesOuvertes.map((periode) => `${dateFr(periode.dateDebut)} → ${dateFr(periode.dateFin)}`).join(', ')}). Vérifiez la clôture de la paie de décembre et les acquisitions de ce cycle avant d'activer un nouveau taux. La référence de l'année suivante peut être créée séparément.`
    } />}

    {!employeId ? <EmptyState icon={CalendarRange} title="Choisissez un collaborateur"
      description="Consultez ses acquisitions annuelles et leurs valorisations." />
      : loading ? <SkeletonTable rows={4} columns={5} />
      : <>
        <Card padding bordered>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <p className="text-xs uppercase font-semibold text-[var(--color-fg-muted)]">Disponible aujourd'hui · tous lots historiques</p>
              {stock ? <p className="text-lg font-semibold">
                {jours(stock.soldeJours)} j · {formatMoneyAr(stock.soldeValorisation)}
              </p> : <p className="text-sm text-[var(--color-fg-muted)]">Solde valorisé indisponible ; vérifiez les lots.</p>}
              <p className="text-xs text-[var(--color-fg-muted)]">Les prises et ajustements sont déjà déduits ; les lots conservés sont consommés en FIFO.</p>
            </div>
            {canValidate && (
              <Button
                variant="outline"
                size="sm"
                iconLeft={<SlidersHorizontal size={14} />}
                onClick={() => setShowCutoverModal(true)}
              >
                Reprendre l'historique (Cutover)
              </Button>
            )}
          </div>
        </Card>
        <Card padding bordered>
        <CardHeader className="pb-3 border-b border-[var(--color-border-subtle)]">
          <CardTitle className="text-sm">Références et acquisitions</CardTitle>
          <p className="text-xs text-[var(--color-fg-muted)]">
            Les cumuls ci-dessous représentent les droits acquis pendant chaque année, pas le solde disponible toutes années confondues.
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
                    Reprise de solde antérieur (Cutover)
                  </p>
                  <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                    Pour un salarié ayant des droits acquis avant le déploiement de GALAXAM, initialisez le stock et le TCONGE certifiés par la DAF.
                  </p>
                </div>
                {canValidate && (
                  <Button
                    variant="primary"
                    size="sm"
                    iconLeft={<SlidersHorizontal size={14} />}
                    onClick={() => setShowCutoverModal(true)}
                  >
                    Reprendre l'historique
                  </Button>
                )}
              </div>
            </div>
          ) : <Table>
            <Thead><Tr><Th>Année de référence</Th><Th>Statut</Th>
              <Th className="text-right">Jours acquis</Th><Th className="text-right">Valeur acquise</Th>
              <Th className="text-right">Taux moyen calculé</Th><Th>Application du taux</Th><Th>Contrôle</Th></Tr></Thead>
            <Tbody>{periodes.map((periode) => {
              const ouverte = periode.statut === 'OUVERTE';
              return <Tr key={periode.idPeriodeReference}>
                <Td>{dateFr(periode.dateDebut)} → {dateFr(periode.dateFin)}</Td>
                <Td><Badge variant={ouverte ? 'success' : 'neutral'}>{periode.statut}</Badge></Td>
                <Td className="text-right">{jours(ouverte ? periode.cumulDroitsCourant : periode.cumulDroitsCloture)} j</Td>
                <Td className="text-right">{formatMoneyAr(ouverte ? periode.cumulValorisationCourante : periode.cumulValorisationCloture)}</Td>
                <Td className="text-right">{periode.tauxCalculeCloture ? `${formatMoneyAr(periode.tauxCalculeCloture)} / j` : '—'}</Td>
                <Td>{periode.dateEffetActivation
                  ? <span>Activé le {dateFr(periode.dateEffetActivation)}<br />{formatMoneyAr(periode.tauxActive)} / j</span>
                  : !ouverte && Number(periode.tauxCalculeCloture) > 0
                    ? <div className="space-y-1"><Badge variant="warning">À activer</Badge>
                      {canValidate && <div><Button size="xs" variant="primary" onClick={() => ouvrirActivation(periode)}>Activer le taux</Button></div>}</div>
                    : '—'}</Td>
                <Td><div className="flex gap-2">
                  <Button size="xs" variant="outline" onClick={() => chargerMois(periode)}>Vérifier les mois</Button>
                  <Button size="xs" variant="outline" iconLeft={<FileSpreadsheet size={13} />}
                    onClick={() => exporter(periode)}>Excel</Button>
                </div></Td>
              </Tr>;
            })}</Tbody>
          </Table>}
        </CardBody>
        </Card>
      </>}

    <Modal isOpen={Boolean(periodeControle)} onClose={() => setPeriodeControle(null)}
      title="Contrôle mensuel des acquisitions" size="2xl">
      <div className="p-4 space-y-4">
        <p className="text-sm text-[var(--color-fg-muted)]">
          La valeur de chaque mois vient du bulletin clôturé. Une rectification change la valeur du lot et les cumuls,
          jamais les jours ni le bulletin. Pour un cycle clôturé ou un lot déjà consommé, la DAF contrôle
          l'indemnité déjà payée puis saisit un complément dans Paie → Variables, type « Régularisation de congé antérieur ».
          Le motif doit contenir <code>JOURNAL_CONGE#id</code> et expliquer le calcul ; la variable passe par l'approbation de paie.
        </p>
        <div className="overflow-x-auto"><Table>
          <Thead><Tr><Th>Mois acquis</Th><Th>Journal</Th><Th className="text-right">Jours</Th>
            <Th className="text-right">Valeur du lot</Th><Th className="text-right">Cumul annuel</Th><Th>Action</Th></Tr></Thead>
          <Tbody>{lignes.map((ligne) => <Tr key={ligne.idJournal}>
            <Td>{moisFr(ligne.mois, ligne.annee)}</Td>
            <Td>#{ligne.idJournal}</Td>
            <Td className="text-right">{jours(ligne.jours)} j</Td>
            <Td className="text-right">{formatMoneyAr(ligne.valeur)}</Td>
            <Td className="text-right">{formatMoneyAr(ligne.cumul)}</Td>
            <Td>{canValidate && ligne.statutReference === 'OUVERTE' &&
              <Button size="xs" variant="outline" onClick={() => {
                setLigneCorrection(ligne); setValeur(String(ligne.valeur)); setMotif('');
              }}>Rectifier</Button>}</Td>
          </Tr>)}</Tbody>
        </Table></div>
        {lignes.length === 0 && <p className="text-sm">Aucun droit acquis sur cette référence.</p>}
        {historique.length > 0 && <div className="border-t pt-3 space-y-1">
          <p className="text-sm font-semibold">Rectifications DAF</p>
          {historique.map((item) => {
            const ligne = lignes.find((row) => row.idJournal === item.idJournal);
            return <p key={item.idCorrection} className="text-xs text-[var(--color-fg-muted)]">
              {ligne ? moisFr(ligne.mois, ligne.annee) : `Journal ${item.idJournal}`}
              {' · '}{formatMoneyAr(item.ancienneValeur)} → {formatMoneyAr(item.nouvelleValeur)}
              {' · '}{item.motif} · utilisateur #{item.corrigePar}
            </p>;
          })}
        </div>}
        {ligneCorrection && <form onSubmit={corriger} className="space-y-3 border-t pt-4">
          <p className="font-semibold">Rectifier {moisFr(ligneCorrection.mois, ligneCorrection.annee)}</p>
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
        <p className="text-sm">Taux calculé à la clôture : <strong>{formatMoneyAr(activation.periode.tauxCalculeCloture)} / jour</strong></p>
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
            ? `${formatMoneyAr(situationDate.tauxApplicable.montant)} / jour`
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
