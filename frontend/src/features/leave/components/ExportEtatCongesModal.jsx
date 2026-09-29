import { useState, useEffect, useMemo } from 'react';
import { Download, Calendar, FileSpreadsheet, CheckCircle2, AlertCircle, Info } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import FormError from '@/components/ui/feedback/FormError';
import Loading from '@/components/ui/feedback/Loading';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';

const formatDateFr = (dStr) => {
  if (!dStr) return '—';
  try {
    const d = new Date(dStr);
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  } catch {
    return dStr;
  }
};

export default function ExportEtatCongesModal({
  isOpen,
  onClose,
  idEmploye,
  employe
}) {
  const toast = useToast();
  const [periodes, setPeriodes] = useState([]);
  const [selectedPeriodeId, setSelectedPeriodeId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState(null);

  // Charger les périodes annuelles à l'ouverture
  useEffect(() => {
    if (!isOpen || !idEmploye) return;

    let isMounted = true;
    const chargerPeriodes = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await congeService.getPeriodesReferenceEmploye(idEmploye);
        if (!isMounted) return;

        const list = Array.isArray(data) ? data : [];
        setPeriodes(list);

        // Sélectionner par défaut la période en cours ou la plus récente
        if (list.length > 0) {
          const active = list.find((p) => p.statut === 'OUVERTE') || list[0];
          setSelectedPeriodeId(active.idPeriodeReference || active.id_periode_reference);
        } else {
          setSelectedPeriodeId(null);
        }
      } catch (err) {
        if (isMounted) {
          setError(getErrorMessage(err, 'Impossible de charger les périodes de l\'employé.'));
        }
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    chargerPeriodes();
    return () => {
      isMounted = false;
    };
  }, [isOpen, idEmploye]);

  // Période sélectionnée
  const selectedPeriode = useMemo(() => {
    if (!selectedPeriodeId || !periodes.length) return null;
    return periodes.find(
      (p) => (p.idPeriodeReference || p.id_periode_reference) === Number(selectedPeriodeId)
    );
  }, [periodes, selectedPeriodeId]);

  const handleDownload = async () => {
    setExporting(true);
    setError(null);
    try {
      await congeService.telechargerEtatCongesExcel(idEmploye, selectedPeriodeId);
      toast.success('Bilan annuel téléchargé avec succès.');
      onClose();
    } catch (err) {
      console.error(err);
      setError(getErrorMessage(err, 'Erreur lors de la génération du document Excel.'));
    } finally {
      setExporting(false);
    }
  };

  const nomComplet = employe
    ? `${employe.nom || ''} ${employe.prenom || ''}`.trim() || employe.matricule
    : `Salarié #${idEmploye}`;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div className="flex items-center gap-2.5 text-sm font-bold text-[var(--color-fg)]">
          <FileSpreadsheet className="text-[var(--color-brand-600)]" size={18} />
          <span>Bilan annuel des congés</span>
        </div>
      }
      size="xl"
    >
      <div className="space-y-4 text-xs font-sans">
        {/* Bandeau d'information simple */}
        <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)]">
          <div className="flex items-start gap-2.5">
            <Info size={16} className="text-[var(--color-brand-600)] flex-shrink-0 mt-0.5" />
            <div className="space-y-1">
              <p className="font-semibold text-xs text-[var(--color-fg)]">
                Fiche individuelle annuelle — {nomComplet}
              </p>
              <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
                Ce document télécharge la fiche récapitulative au format Excel (.xlsx), avec le récapitulatif mois par mois des droits, l'historique complet des congés pris et le solde restant.
              </p>
            </div>
          </div>
        </div>

        {error && <FormError message={error} />}

        {loading ? (
          <div className="py-8 flex flex-col items-center justify-center gap-2">
            <Loading size="md" />
            <span className="text-[11px] text-[var(--color-fg-muted)]">
              Chargement des périodes disponibles...
            </span>
          </div>
        ) : (
          <div className="space-y-4">
            {/* Choix de la période annuelle */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="text-xs font-bold text-[var(--color-fg)] flex items-center gap-1.5 uppercase tracking-wide">
                  <Calendar size={13} className="text-[var(--color-brand-600)]" />
                  Choisir la période de référence
                </label>
                <span className="text-[11px] text-[var(--color-fg-muted)]">
                  {periodes.length} période(s) disponible(s)
                </span>
              </div>

              {periodes.length > 0 ? (
                <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                  {periodes.map((p) => {
                    const id = p.idPeriodeReference || p.id_periode_reference;
                    const isSelected = selectedPeriodeId === id;
                    const isOuverte = p.statut === 'OUVERTE';
                    const tauxVal = p.tauxCalculeCloture || p.taux_calcule_cloture;
                    const soldeOuv = p.cumulDroitsOuverture || p.cumul_droits_ouverture;

                    return (
                      <div
                        key={id}
                        onClick={() => setSelectedPeriodeId(id)}
                        className={`p-3.5 border transition-all cursor-pointer flex items-center justify-between gap-3 ${
                          isSelected
                            ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-100)]/30 ring-1 ring-[var(--color-brand-600)]'
                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <input
                            type="radio"
                            name="periodeRef"
                            checked={isSelected}
                            onChange={() => setSelectedPeriodeId(id)}
                            className="text-[var(--color-brand-600)] focus:ring-[var(--color-brand-500)]"
                          />
                          <div>
                            <div className="font-semibold text-xs text-[var(--color-fg)] flex items-center gap-2">
                              <span>
                                Du {formatDateFr(p.dateDebut || p.date_debut)} au {formatDateFr(p.dateFin || p.date_fin)}
                              </span>
                              {isOuverte ? (
                                <Badge variant="success" size="xs">
                                  En cours
                                </Badge>
                              ) : (
                                <Badge variant="neutral" size="xs">
                                  Clôturée
                                </Badge>
                              )}
                            </div>
                            <div className="text-[11px] text-[var(--color-fg-muted)] mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5">
                              {soldeOuv != null && (
                                <span>Report initial : {soldeOuv} j</span>
                              )}
                              {tauxVal && (
                                <span>Taux journalier : {formatMoneyAr(tauxVal)}</span>
                              )}
                            </div>
                          </div>
                        </div>

                        {isSelected && (
                          <CheckCircle2 size={16} className="text-[var(--color-brand-600)] flex-shrink-0" />
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="p-3 border border-[var(--color-border)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)]">
                  <div className="flex items-center gap-2 font-semibold text-xs">
                    <AlertCircle size={14} className="text-[var(--color-brand-600)]" />
                    <span>Aucune période spécifique enregistrée</span>
                  </div>
                  <p className="mt-1 text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
                    Aucune période de référence n'est disponible pour ce salarié. Créez ou régularisez sa période avant l'export.
                  </p>
                </div>
              )}
            </div>

            {/* Détails de la période sélectionnée */}
            {selectedPeriode && (
              <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                <span className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                  Résumé de la sélection
                </span>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  <div className="bg-[var(--color-bg-subtle)] p-2.5 border border-[var(--color-border-subtle)]">
                    <span className="text-[10px] text-[var(--color-fg-muted)] block">Date de début</span>
                    <strong className="font-mono text-xs text-[var(--color-fg)]">
                      {formatDateFr(selectedPeriode.dateDebut || selectedPeriode.date_debut)}
                    </strong>
                  </div>
                  <div className="bg-[var(--color-bg-subtle)] p-2.5 border border-[var(--color-border-subtle)]">
                    <span className="text-[10px] text-[var(--color-fg-muted)] block">Date de fin</span>
                    <strong className="font-mono text-xs text-[var(--color-fg)]">
                      {formatDateFr(selectedPeriode.dateFin || selectedPeriode.date_fin)}
                    </strong>
                  </div>
                  <div className="bg-[var(--color-bg-subtle)] p-2.5 border border-[var(--color-border-subtle)]">
                    <span className="text-[10px] text-[var(--color-fg-muted)] block">Statut</span>
                    <strong className="text-xs text-[var(--color-fg)]">
                      {selectedPeriode.statut === 'OUVERTE' ? 'En cours' : 'Clôturée'}
                    </strong>
                  </div>
                </div>
              </div>
            )}

            {/* Contenu du document Excel */}
            <div className="border border-[var(--color-border-subtle)] p-3 bg-[var(--color-bg-subtle)] space-y-2">
              <span className="text-[11px] font-bold text-[var(--color-fg)] flex items-center gap-1.5 uppercase tracking-wide">
                <Info size={13} className="text-[var(--color-brand-600)]" />
                Contenu du fichier Excel (.xlsx)
              </span>
              <ul className="space-y-1 text-[11px] text-[var(--color-fg-muted)] list-disc pl-4">
                <li>
                  <strong className="text-[var(--color-fg)]">Feuille 1 (État des congés) :</strong> droits et paie par période de référence, prises enregistrées et solde issu du journal.
                </li>
                <li>
                  <strong className="text-[var(--color-fg)]">Feuille 2 (Mouvements) :</strong> historique détaillé des débits, crédits et soldes progressifs sur la période.
                </li>
                <li>
                  <strong className="text-[var(--color-fg)]">Feuille 3 (Notice) :</strong> rappel simple des règles de calcul et barèmes légaux.
                </li>
              </ul>
            </div>
          </div>
        )}

        {/* Boutons d'action */}
        <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-[var(--color-border-subtle)]">
          <Button variant="ghost" size="sm" onClick={onClose} disabled={exporting}>
            Annuler
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleDownload}
            loading={exporting}
            disabled={loading || !selectedPeriodeId}
            iconLeft={<Download size={14} />}
            className="font-bold"
          >
            Télécharger le fichier Excel (.xlsx)
          </Button>
        </div>
      </div>
    </Modal>
  );
}
