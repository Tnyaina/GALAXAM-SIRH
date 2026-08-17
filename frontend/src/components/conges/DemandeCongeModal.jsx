import { useState, useEffect } from 'react';
import { Calendar, Info, ShieldAlert } from 'lucide-react';
import congeService from '@/api/congeService';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';

export default function DemandeCongeModal({ idEmploye, referentiels, isOpen, onClose, onSuccess }) {
  const [idTypeConge, setIdTypeConge] = useState('');
  const [idMotifEvenementFamilial, setIdMotifEvenementFamilial] = useState('');
  const [dateDebut, setDateDebut] = useState('');
  const [dateFin, setDateFin] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [justificatifFourni, setJustificatifFourni] = useState(false);

  // Dérogations RH
  const [derogationSoldeNegatif, setDerogationSoldeNegatif] = useState(false);
  const [derogationDelaiPreavis, setDerogationDelaiPreavis] = useState(false);
  const [extensionPathologiqueMaternite, setExtensionPathologiqueMaternite] = useState(false);

  // Simulation & State
  const [nbJoursDecomptes, setNbJoursDecomptes] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const toast = useToast();

  const typesConge = referentiels?.typesConge || [];
  const motifsFamiliaux = referentiels?.motifsFamiliaux || [];

  const selectedType = typesConge.find((t) => String(t.idTypeConge) === String(idTypeConge));
  const isExceptionnelOuPaternite = selectedType && (selectedType.code === 'EXCEPTIONNEL' || selectedType.code === 'PATERNITE');
  const isMaternite = selectedType && selectedType.code === 'MATERNITE';

  // Pré-remplissage des durées fixes légales (ex: Paternité 3j, Maternité 98j)
  useEffect(() => {
    if (selectedType && selectedType.dureeFixeJours && dateDebut) {
      const dDebut = new Date(dateDebut);
      let nbJoursFixes = Number(selectedType.dureeFixeJours);
      if (isMaternite && extensionPathologiqueMaternite) {
        nbJoursFixes = 119;
      }
      const dFin = new Date(dDebut);
      dFin.setDate(dFin.getDate() + nbJoursFixes - 1);
      setDateFin(dFin.toISOString().split('T')[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idTypeConge, dateDebut, extensionPathologiqueMaternite]);

  // Simulation automatique des jours calendaires stricts
  useEffect(() => {
    if (dateDebut && dateFin) {
      const dDebut = new Date(dateDebut);
      const dFin = new Date(dateFin);
      if (dFin >= dDebut) {
        const diffTime = dFin.getTime() - dDebut.getTime();
        const diffDays = Math.ceil(diffTime / (1000 * 3600 * 24)) + 1;
        setNbJoursDecomptes(diffDays);
      } else {
        setNbJoursDecomptes(0);
      }
    } else {
      setNbJoursDecomptes(0);
    }
  }, [dateDebut, dateFin]);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!idTypeConge || !dateDebut || !dateFin) {
      setError('Veuillez renseigner le type de congé et les dates de début et de fin.');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const payload = {
        idEmploye,
        idTypeConge: parseInt(idTypeConge),
        idMotifEvenementFamilial: idMotifEvenementFamilial ? parseInt(idMotifEvenementFamilial) : null,
        dateDebut,
        dateFin,
        commentaire,
        justificatifFourni,
        derogationSoldeNegatif,
        derogationDelaiPreavis,
        extensionPathologiqueMaternite,
      };

      await congeService.creerDemande(payload);
      toast({
        title: 'Demande soumise',
        message: 'Votre demande de congé a été enregistrée avec succès.',
        type: 'success',
        duration: 3000
      });
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setError(err?.response?.data || err.message || 'Erreur lors de la soumission de la demande.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-[var(--color-brand-900)]/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none max-w-xl w-full p-6 shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto font-sans">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-3">
          <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
            <Calendar className="w-4 h-4 text-[var(--color-brand-500)]" />
            Nouvelle Demande de Congé / Absence
          </h3>
          <button onClick={onClose} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] text-lg font-bold">
            ×
          </button>
        </div>

        {/* Message d'erreur */}
        {error && (
          <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />
        )}

        <form onSubmit={handleSubmit} className="space-y-4 text-xs">
          {/* Sélection du Type de Congé */}
          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] mb-1">
              Type de Congé / Absence *
            </label>
            <select
              value={idTypeConge}
              onChange={(e) => {
                setIdTypeConge(e.target.value);
                setIdMotifEvenementFamilial('');
              }}
              required
              className="w-full px-3 py-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)] cursor-pointer text-xs"
            >
              <option value="">-- Sélectionner un type de congé --</option>
              {typesConge.map((t) => (
                <option key={t.idTypeConge} value={t.idTypeConge}>
                  {t.libelle} {t.estPaye ? '(Rémunéré)' : '(Sans solde)'} {t.soumisDelaiPreavis ? '• Préavis' : ''}
                </option>
              ))}
            </select>
          </div>

          {/* Motif Événement Familial (Si EXCEPTIONNEL ou PATERNITE) */}
          {isExceptionnelOuPaternite && (
            <div className="p-3 bg-warning-500/10 border border-warning-500/30 rounded-none space-y-2">
              <label className="block text-xs font-bold uppercase tracking-wider text-warning-500">
                Motif d'Événement Familial *
              </label>
              <select
                value={idMotifEvenementFamilial}
                onChange={(e) => setIdMotifEvenementFamilial(e.target.value)}
                required
                className="w-full px-3 py-2 bg-[var(--color-bg-subtle)] border border-warning-500/40 rounded-none text-[var(--color-fg)] focus:outline-none focus:border-warning-500 cursor-pointer text-xs"
              >
                <option value="">-- Sélectionner un motif --</option>
                {motifsFamiliaux.map((m) => (
                  <option key={m.idMotif} value={m.idMotif}>
                    {m.libelle} ({m.nbJoursAutorises} jours autorisés)
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Dates & Simulation Jours Calendaires Stricts */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] mb-1">
                Date de Début *
              </label>
              <Input
                type="date"
                value={dateDebut}
                onChange={(e) => setDateDebut(e.target.value)}
                required
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] mb-1">
                Date de Fin *
              </label>
              <Input
                type="date"
                value={dateFin}
                onChange={(e) => setDateFin(e.target.value)}
                required
              />
            </div>
          </div>

          {/* Banner de simulation pédagogique du Décompte Calendaire Strict */}
          {nbJoursDecomptes > 0 && (
            <div className="p-3 bg-info-500/10 border border-info-500/30 rounded-none space-y-1">
              <div className="flex items-center justify-between text-info-500 font-bold">
                <span className="flex items-center gap-1.5 text-xs">
                  <Info className="w-4 h-4 text-info-500" />
                  Décompte Calendaire Strict (Loi n° 2003-044) :
                </span>
                <span className="font-data text-sm font-extrabold text-info-500">
                  {nbJoursDecomptes} jour{nbJoursDecomptes > 1 ? 's' : ''} décompté{nbJoursDecomptes > 1 ? 's' : ''}
                </span>
              </div>
              <p className="text-[11px] text-[var(--color-fg-muted)]">
                Formule : <code className="font-data">(date_fin - date_debut) + 1</code>. Tous les jours de l'intervalle (y compris samedis, dimanches et jours fériés chômés) sont comptabilisés dans le décompte du solde.
              </p>
            </div>
          )}

          {/* Banner d'information Bascule Maladie > 30j (Art. 13) */}
          {selectedType && selectedType.code === 'MALADIE' && nbJoursDecomptes > 30 && (
            <div className="p-3 bg-warning-500/10 border border-warning-500/30 rounded-none space-y-1">
              <div className="flex items-center gap-1.5 text-warning-500 font-bold text-xs">
                <ShieldAlert className="w-4 h-4 text-warning-500" />
                Bascule Automatique Art. 13 (Arrêt Maladie &gt; 30 jours) :
              </div>
              <p className="text-[11px] text-[var(--color-fg-muted)]">
                Cette demande de <strong>{nbJoursDecomptes} jours</strong> sera automatiquement scindée lors de l'enregistrement :
                <br />
                • <strong>30 jours</strong> en <em>Maladie (Maintien de salaire 100%)</em>
                <br />
                • <strong>{nbJoursDecomptes - 30} jours</strong> en <em>Maladie Sans Solde (Prise en charge CNaPS)</em>
              </p>
            </div>
          )}

          {/* Extension Pathologique Maternite (si Maternité) */}
          {isMaternite && (
            <div className="p-3 bg-warning-500/10 border border-warning-500/30 rounded-none flex items-center justify-between">
              <span className="text-warning-500 font-semibold text-xs">
                Extension Pathologique (portant le congé à 119j max / 17 semaines)
              </span>
              <input
                type="checkbox"
                checked={extensionPathologiqueMaternite}
                onChange={(e) => setExtensionPathologiqueMaternite(e.target.checked)}
                className="w-4 h-4 accent-warning-500 cursor-pointer"
              />
            </div>
          )}

          {/* Justificatif fourni & Commentaire */}
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="justificatifFourni"
                checked={justificatifFourni}
                onChange={(e) => setJustificatifFourni(e.target.checked)}
                className="w-4 h-4 accent-[var(--color-brand-500)] cursor-pointer"
              />
              <label htmlFor="justificatifFourni" className="text-xs font-medium text-[var(--color-fg)] cursor-pointer">
                Justificatif fourni (certificat médical, acte d'état civil, convocation)
              </label>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] mb-1">
                Commentaire / Complément d'information
              </label>
              <Textarea
                rows="2"
                placeholder="Remarques éventuelles..."
                value={commentaire}
                onChange={(e) => setCommentaire(e.target.value)}
              />
            </div>
          </div>

          {/* Dérogations RH (Réservées aux gestionnaires RH) */}
          <details className="group p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none cursor-pointer">
            <summary className="text-[11px] uppercase font-bold text-[var(--color-fg-muted)] flex items-center gap-1.5 outline-none">
              <ShieldAlert className="w-3.5 h-3.5 text-warning-500" />
              Options Avancées (RH)
            </summary>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-[11px] mt-3 pt-3 border-t border-[var(--color-border-subtle)]">
              <label className="flex items-center gap-2 text-[var(--color-fg)] cursor-pointer">
                <input
                  type="checkbox"
                  checked={derogationSoldeNegatif}
                  onChange={(e) => setDerogationSoldeNegatif(e.target.checked)}
                  className="w-3.5 h-3.5 accent-[var(--color-brand-500)] cursor-pointer"
                />
                Autoriser Solde Négatif
              </label>
              <label className="flex items-center gap-2 text-[var(--color-fg)] cursor-pointer">
                <input
                  type="checkbox"
                  checked={derogationDelaiPreavis}
                  onChange={(e) => setDerogationDelaiPreavis(e.target.checked)}
                  className="w-3.5 h-3.5 accent-[var(--color-brand-500)] cursor-pointer"
                />
                Ignorer le délai de préavis
              </label>
            </div>
          </details>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-[var(--color-border-subtle)]">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={onClose}
              className="rounded-none"
            >
              Annuler
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              loading={loading}
              className="rounded-none font-bold uppercase"
            >
              Soumettre la Demande
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
