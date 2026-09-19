import { useState, useEffect } from 'react';
import { Calendar, Coins, History, Info } from 'lucide-react';
import congeService from '@/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';

export default function InitialisationSoldeModal({
  isOpen,
  onClose,
  onSuccess,
  employe,
  soldeData = null
}) {
  const [soldeInitial, setSoldeInitial] = useState('');
  const [dateArrete, setDateArrete] = useState('');
  const [inclutAcquisition, setInclutAcquisition] = useState(true);
  const [modeTconge, setModeTconge] = useState('HISTORIQUE'); // 'HISTORIQUE' ou 'BASE_30'
  const [tauxMigration, setTauxMigration] = useState('');
  const [motif, setMotif] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const toast = useToast();

  const salaireBase = Number(
    soldeData?.salaireBase ||
    employe?.salaireBase ||
    employe?.salaireBaseContractuel ||
    employe?.dernierSalaireBase ||
    0
  );
  const tauxBase30 = salaireBase > 0 ? (salaireBase / 30).toFixed(2) : '0';

  useEffect(() => {
    if (!isOpen) return;

    // Date du jour par défaut
    const today = new Date();
    const defaultDate = today.toISOString().split('T')[0];
    setDateArrete(defaultDate);

    // Solde actuel ou 0
    if (soldeData?.soldeActuel !== undefined && soldeData?.soldeActuel !== null) {
      setSoldeInitial(String(soldeData.soldeActuel));
    } else {
      setSoldeInitial('0');
    }

    // TCONGE actuel ou prérempli
    if (soldeData?.tauxJournalierConge && Number(soldeData.tauxJournalierConge) > 0) {
      setTauxMigration(String(Number(soldeData.tauxJournalierConge).toFixed(2)));
      setModeTconge(soldeData?.sourceTauxConge === 'INITIALISATION_BASE_30' ? 'BASE_30' : 'HISTORIQUE');
    } else {
      setTauxMigration('');
      setModeTconge('HISTORIQUE');
    }

    setInclutAcquisition(true);
    setMotif('Reprise des données historiques');
    setError(null);
  }, [isOpen, soldeData, employe]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    const sInitial = parseFloat(soldeInitial);
    if (isNaN(sInitial)) {
      setError('Veuillez renseigner un solde initial valide en jours (ex: 1.5).');
      return;
    }

    if (!dateArrete) {
      setError("Veuillez sélectionner la date d'arrêté historique.");
      return;
    }

    let tMigrationValue = null;
    if (modeTconge === 'HISTORIQUE') {
      const val = parseFloat(tauxMigration);
      if (isNaN(val) || val <= 0) {
        setError('Veuillez saisir un taux journalier TCONGE valide (ex: 8669.52).');
        return;
      }
      tMigrationValue = val;
    } else if (modeTconge === 'BASE_30') {
      if (salaireBase <= 0) {
        setError('Le salaire de base du salarié est introuvable ou nul. Veuillez saisir le taux manuellement.');
        return;
      }
      tMigrationValue = parseFloat(tauxBase30);
    }

    setLoading(true);
    try {
      const payload = {
        idEmploye: employe?.idEmploye || employe?.id,
        soldeInitial: sInitial,
        dateArrete,
        inclutAcquisitionMoisArrete: inclutAcquisition,
        tauxJournalierMigration: tMigrationValue,
        motif: motif || 'Reprise des données historiques'
      };

      await congeService.initialiserSoldeHistorique(payload);

      toast({
        title: 'Reprise enregistrée avec succès',
        message: `Solde (${sInitial} j) et TCONGE (${formatMoneyAr(tMigrationValue)}) mis à jour.`,
        type: 'success'
      });

      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, "Erreur lors de l'enregistrement de l'initialisation."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Initialisation & Reprise de Solde / TCONGE"
      size="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4 font-sans text-sm">
        {error && <Alert type="error" message={error} />}

        {/* Collaborateur Info Card */}
        <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between text-xs">
          <div>
            <span className="text-[var(--color-fg-muted)]">Salarié :</span>{' '}
            <strong className="text-[var(--color-fg)]">
              {employe?.nom} {employe?.prenom}
            </strong>{' '}
            <span className="font-mono text-[var(--color-brand-600)]">
              ({employe?.matricule || `#${employe?.idEmploye}`})
            </span>
          </div>
          {salaireBase > 0 && (
            <div className="text-right">
              <span className="text-[var(--color-fg-muted)]">Base contractuelle :</span>{' '}
              <strong className="text-[var(--color-fg)]">{formatMoneyAr(salaireBase)}</strong>
            </div>
          )}
        </div>

        {/* Section 1 : Solde en Jours */}
        <div className="p-3 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-bg-surface)]">
          <div className="flex items-center gap-2 font-bold text-xs text-[var(--color-fg)] uppercase tracking-wider">
            <History size={15} className="text-[var(--color-brand-500)]" />
            <span>1. Solde Initial des Congés (Jours)</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Solde initial arrêté (jours) <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="number"
                step="0.5"
                value={soldeInitial}
                onChange={(e) => setSoldeInitial(e.target.value)}
                placeholder="Ex: 1.5 ou 15.0"
                required
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Date d'arrêté historique <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="date"
                value={dateArrete}
                onChange={(e) => setDateArrete(e.target.value)}
                required
              />
            </div>
          </div>

          <label className="flex items-center gap-2 text-xs text-[var(--color-fg-muted)] cursor-pointer select-none">
            <input
              type="checkbox"
              checked={inclutAcquisition}
              onChange={(e) => setInclutAcquisition(e.target.checked)}
              className="rounded border-[var(--color-border-subtle)] text-[var(--color-brand-600)]"
            />
            <span>Le mois d'arrêté est déjà inclus dans ce solde (évite les doublons lors de la prochaine acquisition)</span>
          </label>
        </div>

        {/* Section 2 : Taux TCONGE (Ariary) */}
        <div className="p-3 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-bg-surface)]">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 font-bold text-xs text-[var(--color-fg)] uppercase tracking-wider">
              <Coins size={15} className="text-[var(--color-brand-500)]" />
              <span>2. Valorisation du Taux Journalier (TCONGE en Ariary)</span>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-4 text-xs">
              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="radio"
                  name="modeTconge"
                  value="HISTORIQUE"
                  checked={modeTconge === 'HISTORIQUE'}
                  onChange={() => setModeTconge('HISTORIQUE')}
                  className="text-[var(--color-brand-600)]"
                />
                <span className="font-semibold text-[var(--color-fg)]">
                  Taux issu de l'historique précédent (Recommandé)
                </span>
              </label>

              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="radio"
                  name="modeTconge"
                  value="BASE_30"
                  checked={modeTconge === 'BASE_30'}
                  onChange={() => {
                    setModeTconge('BASE_30');
                    if (salaireBase > 0) setTauxMigration(tauxBase30);
                  }}
                  className="text-[var(--color-brand-600)]"
                />
                <span className="text-[var(--color-fg-muted)]">
                  Règle légale standard : Salaire Base / 30 {salaireBase > 0 ? `(${formatMoneyAr(tauxBase30)}/j)` : ''}
                </span>
              </label>
            </div>

            {modeTconge === 'HISTORIQUE' ? (
              <div>
                <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                  Taux Journalier TCONGE arrêté (Ar / jour) <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <Input
                  type="number"
                  step="0.01"
                  value={tauxMigration}
                  onChange={(e) => setTauxMigration(e.target.value)}
                  placeholder="Ex: 8669.52 ou 10828.49"
                  required
                />
                <span className="text-[11px] text-[var(--color-fg-subtle)] mt-1 block">
                  Taux journalier moyen de congé arrêté lors de la dernière clôture dans l'ancien système de gestion.
                </span>
              </div>
            ) : (
              <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs flex items-center gap-2">
                <Info size={16} className="text-[var(--color-brand-500)] shrink-0" />
                <span>
                  Le taux initial sera calculé sur la base contractuelle : <strong>{formatMoneyAr(tauxBase30)} / jour</strong> jusqu'à la première clôture annuelle dans le SIRH.
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Section 3 : Motif */}
        <div>
          <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
            Motif / Référence de reprise
          </label>
          <Input
            type="text"
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
            placeholder="Ex: Reprise des données historiques - Arrêté au 31/08/2026"
          />
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-2 pt-2 border-t border-[var(--color-border-subtle)]">
          <Button variant="ghost" size="sm" onClick={onClose} disabled={loading} type="button">
            Annuler
          </Button>
          <Button variant="primary" size="sm" type="submit" loading={loading}>
            Enregistrer la reprise de données
          </Button>
        </div>
      </form>
    </Modal>
  );
}
