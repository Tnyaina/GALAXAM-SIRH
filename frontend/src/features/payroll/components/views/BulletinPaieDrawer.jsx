import { useState, useEffect, useCallback } from 'react';
import {
    FileText,
    Printer,
    RefreshCw,
    PlusCircle,
    AlertCircle,
    CheckCircle2,
    Clock,
    DollarSign,
    ChevronUp,
    Info,
    ExternalLink
} from 'lucide-react';
import SlideOver from '@/components/ui/overlays/SlideOver';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';
import { formatMoneyAr } from '@/utils/moneyUtils';
import BulletinPaiePrintView from './BulletinPaiePrintView';

/**
 * Tiroir Latéral — Consultation et Édition Contextuelle du Bulletin (GALAXAM SIRH)
 * Conforme au Wireframe Écran 2 du Rapport d'Audit.
 */
export default function BulletinPaieDrawer({ isOpen, onClose, idBulletin, onRecalculateSuccess }) {
    // État local : bulletin, vues de détail, ajustement et retours d'action.
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const [showPrintModal, setShowPrintModal] = useState(false);
    const [showAddVariable, setShowAddVariable] = useState(false);
    const [showDetailRubriques, setShowDetailRubriques] = useState(false);
    const [showTaxDetails, setShowTaxDetails] = useState(false);

    // Formulaire d'ajout d'ajustement contextuel
    const [typeMouvements, setTypeMouvements] = useState([]);
    const [submittingAjustement, setSubmittingAjustement] = useState(false);
    const [recalculating, setRecalculating] = useState(false);
    const [ajustementForm, setAjustementForm] = useState({
        typeMouvement: '',
        montant: '',
        sens: 'GAIN',
        motif: ''
    });
    const [feedbackMsg, setFeedbackMsg] = useState(null);

    const chargerBulletin = useCallback(async () => {
        if (!idBulletin) return;
        setLoading(true);
        setError(null);
        try {
            const res = await paieService.getBulletinComplet(idBulletin);
            setData(res);
        } catch (err) {
            setError(getErrorMessage(err, 'Impossible de charger le bulletin.'));
        } finally {
            setLoading(false);
        }
    }, [idBulletin]);

    const chargerTypesMouvements = useCallback(async () => {
        try {
            const types = await paieService.listerTypesMouvements();
            setTypeMouvements(Array.isArray(types) ? types : (types?.content || types?.items || []));
        } catch (err) {
            console.warn('Types mouvements non disponibles :', err?.message);
        }
    }, []);

    // Effets : chargent le bulletin à l'ouverture et nettoient les états de vue.
    useEffect(() => {
        if (isOpen && idBulletin) {
            chargerBulletin();
        } else {
            setData(null);
            setError(null);
            setFeedbackMsg(null);
            setShowAddVariable(false);
            setShowDetailRubriques(false);
            setShowTaxDetails(false);
        }
    }, [isOpen, idBulletin, chargerBulletin]);

    useEffect(() => {
        if (isOpen && showAddVariable && typeMouvements.length === 0) {
            chargerTypesMouvements();
        }
    }, [isOpen, showAddVariable, typeMouvements.length, chargerTypesMouvements]);

    const handleAjustementChange = (field, value) => {
        setAjustementForm(prev => {
            const next = { ...prev, [field]: value };
            if (field === 'typeMouvement') {
                const found = typeMouvements.find(t => t.code === value);
                if (found && found.sens) {
                    next.sens = found.sens;
                }
            }
            return next;
        });
    };

    const handleCreerAjustement = async (e) => {
        e.preventDefault();
        if (!ajustementForm.typeMouvement) {
            setFeedbackMsg({ type: 'error', text: 'Sélectionnez un type de variable configuré.' });
            return;
        }
        if (!ajustementForm.montant || Number(ajustementForm.montant) <= 0) {
            setFeedbackMsg({ type: 'error', text: 'Veuillez saisir un montant supérieur à 0.' });
            return;
        }

        setSubmittingAjustement(true);
        setFeedbackMsg(null);
        try {
            await paieService.creerAjustement({
                idEmploye: data.bulletin.idEmploye,
                idPeriodePaie: data.bulletin.idPeriodePaie,
                typeMouvement: ajustementForm.typeMouvement,
                sens: ajustementForm.sens,
                montant: Number(ajustementForm.montant),
                motif: ajustementForm.motif || 'Saisie rapide bulletin',
                statut: 'APPROUVE'
            });

            setFeedbackMsg({
                type: 'success',
                text: 'Variable enregistrée avec succès. Recalculez le bulletin pour mettre à jour le net à payer.'
            });
            setShowAddVariable(false);
            setAjustementForm({
                typeMouvement: '',
                montant: '',
                sens: 'GAIN',
                motif: ''
            });
            // Recalcul automatique direct
            await handleRecalculerEmploye();
        } catch (err) {
            setFeedbackMsg({
                type: 'error',
                text: getErrorMessage(err, 'Erreur lors de la création de la variable.')
            });
        } finally {
            setSubmittingAjustement(false);
        }
    };

    const handleRecalculerEmploye = async () => {
        if (!data?.bulletin) return;
        setRecalculating(true);
        setFeedbackMsg(null);
        try {
            await paieService.calculerBulletinEmploye(
                data.bulletin.idEmploye,
                data.bulletin.idPeriodePaie,
                false
            );
            setFeedbackMsg({ type: 'success', text: 'Bulletin recalculé et mis à jour !' });
            await chargerBulletin();
            if (onRecalculateSuccess) {
                onRecalculateSuccess();
            }
        } catch (err) {
            setFeedbackMsg({
                type: 'error',
                text: getErrorMessage(err, 'Erreur lors du recalcul.')
            });
        } finally {
            setRecalculating(false);
        }
    };

    const formatAr = (val) => formatMoneyAr(val);

    const formatMinutes = (totalMin) => {
        if (!totalMin || totalMin <= 0) return '0 min';
        const h = Math.floor(totalMin / 60);
        const m = totalMin % 60;
        if (h === 0) return `${m} min`;
        if (m === 0) return `${h} h`;
        return `${h} h ${m} min`;
    };
    const formatHours = (value) => Number(value || 0).toFixed(2).replace('.', ',');

    const bulletin = data?.bulletin;
    const explication = data?.explication;
    const fiscalite = data?.explicationFiscalite;
    const lignes = Array.isArray(data?.lignes) ? data.lignes : [];

    const nomAffiche = bulletin ? (bulletin.nomPrenom || [bulletin.nom, bulletin.prenom].filter(Boolean).join(' ') || [bulletin.nomEmploye, bulletin.prenomEmploye].filter(Boolean).join(' ') || 'Employé') : '';
    const joursTravailles = bulletin ? (bulletin.joursTravailles ?? bulletin.nbJoursTravailles) : null;
    const heuresNormales = bulletin?.heuresNormales;
    const netImposable = bulletin ? (bulletin.revenuNetImposable ?? bulletin.salaireNetImposable ?? bulletin.netImposable) : null;

    const ligneCnaps = lignes.find(l => l.codeRubrique === 'COTISATION_CNAPS_SAL' || l.codeRubrique === 'CNAPS_SAL' || (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('cnaps')));
    const ligneOstie = lignes.find(l => l.codeRubrique === 'COTISATION_OSTIE_SAL' || l.codeRubrique === 'OSTIE_SAL' || l.codeRubrique === 'COTISATION_OMSI_SAL' || (l.codeRubrique && l.codeRubrique.startsWith('COTISATION_') && l.codeRubrique.endsWith('_SAL') && !l.codeRubrique.includes('CNAPS')) || (l.categorie === 'COTISATION_SALARIALE' && !l.codeRubrique?.includes('CNAPS')) || (l.libelleRubrique && (l.libelleRubrique.toLowerCase().includes('ostie') || l.libelleRubrique.toLowerCase().includes('omsi') || l.libelleRubrique.toLowerCase().includes('santé') || l.libelleRubrique.toLowerCase().includes('amit'))));
    const cnapsVal = bulletin ? (bulletin.cnapsSalariale ?? (ligneCnaps ? (ligneCnaps.montantSalarial ?? ligneCnaps.montantRetenue) : null)) : null;
    const ostieVal = bulletin ? (bulletin.ostieSalariale ?? (ligneOstie ? (ligneOstie.montantSalarial ?? ligneOstie.montantRetenue) : null)) : null;

    // Classification des lignes pour l'affichage fidèle au Wireframe 2
    const lignesPointage = lignes.filter(l =>
        ['HEURES_SUP', 'HEURES_SUP_30', 'HEURES_SUP_50', 'HEURES_NUIT', 'HEURES_DIMANCHE', 'MAJORATION_FERIE',
            'INDEMNITE_REPAS', 'INDEMNITE_DEPLACEMENT', 'INDEMNITE_TRANSPORT', 'RETENUE_ABSENCE', 'ABSENCE_RETENUE',
            'DEDUCTION_CONGE_BASE', 'INDEMNITE_CONGE_PAYE'].includes(l.codeRubrique) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('heure')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('nuit')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('absence')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('congé')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('repas')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('deplacement'))
    );

    const lignesVariables = lignes.filter(l => !lignesPointage.includes(l) && (
        ['PRIME_PERF', 'PRIME_PERFORMANCE', 'PRIME_EXC', 'PRIM_EXC', 'PRIME_EXCEPTIONNELLE', 'PRIME_ASSI', 'PRIME_ASSIDUITE',
            'ACOMPTE_15', 'AVANCE_SPEC', 'AVANCE_SPECIALE', 'PRET_ENTREPRISE', 'AUTIMP', 'EXTRA_DEPLACEMENT',
            'NOTE_DE_FRAIS', 'CALL_PERSO', 'RETENUE_DIVERSE', 'REGULARISATION_CONGE'].includes(l.codeRubrique) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('prime')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('acompte')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('avance')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('pret')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('retenue'))
    ));

    // Rendu
    return (
        <>
            <SlideOver
                isOpen={isOpen}
                onClose={onClose}
                title={bulletin ? `BULLETIN DE PAIE — ${nomAffiche}` : 'Bulletin de Paie'}
                subtitle={bulletin ? `Matricule : ${bulletin.matricule || '—'} | Période : ${bulletin.codePeriode || bulletin.periodeCode || 'En cours'} | Site : ${bulletin.nomSite || 'Siège'} | Contrat : ${bulletin.typeContrat || 'CDI'}` : 'Chargement...'}
                icon={<FileText size={18} />}
                width="2xl"
                footer={
                    bulletin && (
                        <div className="flex items-center justify-between w-full">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setShowPrintModal(true)}
                                className="flex items-center gap-1.5"
                            >
                                <Printer size={15} />
                                Imprimer A4 / PDF
                            </Button>

                            <div className="flex items-center gap-2">
                                <Button
                                    variant="primary"
                                    size="sm"
                                    onClick={handleRecalculerEmploye}
                                    disabled={recalculating || bulletin.statut === 'CLOTURE'}
                                    className="flex items-center gap-1.5"
                                >
                                    <RefreshCw size={14} className={recalculating ? 'animate-spin' : ''} />
                                    {recalculating ? 'Recalcul...' : 'Recalculer le bulletin'}
                                </Button>
                                <Button variant="secondary" size="sm" onClick={onClose}>
                                    Fermer
                                </Button>
                            </div>
                        </div>
                    )
                }
            >
                {loading && <SkeletonTable rows={8} cols={4} />}

                {error && (
                    <div className="p-4 bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/25 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)] text-xs flex items-center gap-2">
                        <AlertCircle size={16} className="shrink-0" />
                        <span>{error}</span>
                    </div>
                )}

                {feedbackMsg && (
                    <div className={`p-3 mb-4 text-xs flex items-center gap-2 border ${feedbackMsg.type === 'success'
                            ? 'bg-[var(--color-success-500)]/10 border-[var(--color-success-500)]/25 text-[var(--color-success-700)] dark:text-[var(--color-success-300)]'
                            : 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/25 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]'
                        }`}>
                        {feedbackMsg.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
                        <span>{feedbackMsg.text}</span>
                    </div>
                )}

                {bulletin && (
                    <div className="space-y-4 text-xs font-sans">
                        {/* ============================================================== */}
                        {/* NIVEAU 1 — COMPRÉHENSION DAF                                  */}
                        {/* ============================================================== */}
                        <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none space-y-3">
                            {/* Identité collaborateur & Signal */}
                            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 pb-2 border-b border-[var(--color-border-subtle)]">
                                <div>
                                    <div className="font-bold text-sm text-[var(--color-fg)] flex items-center gap-2">
                                        <span>{nomAffiche}</span>
                                        <span className="text-xs font-normal text-[var(--color-fg-muted)] font-mono">({bulletin.matricule})</span>
                                    </div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        {bulletin.typeContrat || 'CDI'} • {bulletin.intitulePoste || 'Poste'} • {bulletin.nomSite || 'Siège'}
                                    </div>
                                </div>

                                {/* Signal / Alerte */}
                                {bulletin.ecartM1Pct != null && Math.abs(bulletin.ecartM1Pct) > 10 && (
                                    <div className="flex items-center gap-1.5 px-2.5 py-1 bg-[var(--color-warning-500)]/15 border border-[var(--color-warning-500)]/30 rounded text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] font-semibold">
                                        <AlertCircle size={13} className="shrink-0" />
                                        <span>Variation nette importante : {bulletin.ecartM1Pct > 0 ? `+${bulletin.ecartM1Pct}%` : `${bulletin.ecartM1Pct}%`}</span>
                                    </div>
                                )}
                            </div>

                            {/* Résumé 4 chiffres clés DAF */}
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                                <div className="bg-[var(--color-surface)] p-2 rounded border border-[var(--color-border-subtle)]">
                                    <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-bold block">Brut</span>
                                    <span className="font-mono font-bold text-xs text-[var(--color-fg)] mt-0.5 block">{formatAr(bulletin.totalGainsBruts)}</span>
                                </div>
                                <div className="bg-[var(--color-surface)] p-2 rounded border border-[var(--color-border-subtle)]">
                                    <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-bold block">Cotisations</span>
                                    <span className="font-mono font-bold text-xs text-[var(--color-danger-600)] mt-0.5 block">
                                        {bulletin.totalCotisationsSalariales ? `-${formatAr(bulletin.totalCotisationsSalariales)}` : '—'}
                                    </span>
                                </div>
                                <div className="bg-[var(--color-surface)] p-2 rounded border border-[var(--color-border-subtle)]">
                                    <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-bold block">Fiscalité (IRSA)</span>
                                    <span className="font-mono font-bold text-xs text-[var(--color-danger-600)] mt-0.5 block">
                                        {bulletin.irsaNet ? `-${formatAr(bulletin.irsaNet)}` : '0 Ar'}
                                    </span>
                                </div>
                                <div className="bg-[var(--color-success-50)]/30 dark:bg-[var(--color-success-950)]/30 p-2 rounded border border-[var(--color-success-500)]/40">
                                    <span className="text-[10px] text-[var(--color-success-700)] dark:text-[var(--color-success-400)] uppercase font-bold block">Net à Payer</span>
                                    <span className="font-mono font-black text-xs text-[var(--color-success-700)] dark:text-[var(--color-success-300)] mt-0.5 block">{formatAr(bulletin.netAPayer)}</span>
                                </div>
                            </div>

                            {/* Principaux écarts canoniques */}
                            <div className="bg-[var(--color-surface)] p-2.5 rounded border border-[var(--color-border-subtle)] text-[11px] space-y-1 font-mono">
                                <span className="font-sans font-bold text-[10px] uppercase text-[var(--color-fg-muted)] tracking-wider block mb-1">
                                    Principaux écarts sur le mois
                                </span>
                                <div className="flex justify-between items-center text-[var(--color-fg)]">
                                    <span className="font-sans">Salaire de base contractuel</span>
                                    <span>+{formatAr(bulletin.salaireBase)}</span>
                                </div>
                                {Number(bulletin.retenueAbsence || explication?.montantRetenueAbsence) > 0 && (
                                    <div className="flex justify-between items-center text-[var(--color-danger-600)]">
                                        <span className="font-sans">Déficit horaire / Absences</span>
                                        <span>-{formatAr(bulletin.retenueAbsence || explication?.montantRetenueAbsence)}</span>
                                    </div>
                                )}
                                {explication && (explication.nbJoursConge || 0) > 0 && (
                                    <div className="flex justify-between items-center">
                                        <span className="font-sans">Congés pris ({explication.nbJoursConge} j - TCONGE)</span>
                                        <span className={explication.impactNetConge >= 0 ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}>
                                            {explication.impactNetConge >= 0 ? `+${formatAr(explication.impactNetConge)}` : `-${formatAr(Math.abs(explication.impactNetConge))}`}
                                        </span>
                                    </div>
                                )}
                                {Number(bulletin.majorationHeuresSupplementaires || (explication ? (explication.montantHs30 || 0) + (explication.montantHs50 || 0) : 0)) > 0 && (
                                    <div className="flex justify-between items-center text-[var(--color-success-600)]">
                                        <span className="font-sans">Heures supplémentaires</span>
                                        <span>+{formatAr(bulletin.majorationHeuresSupplementaires || ((explication.montantHs30 || 0) + (explication.montantHs50 || 0)))}</span>
                                    </div>
                                )}
                                {Number(bulletin.totalPrimesEtAutres || (bulletin.primePerformance || 0) + (bulletin.primeAnciennete || 0) + (bulletin.primeAssiduite || 0)) > 0 && (
                                    <div className="flex justify-between items-center text-[var(--color-success-600)]">
                                        <span className="font-sans">Primes et autres gains</span>
                                        <span>+{formatAr(bulletin.totalPrimesEtAutres || ((bulletin.primePerformance || 0) + (bulletin.primeAnciennete || 0) + (bulletin.primeAssiduite || 0)))}</span>
                                    </div>
                                )}
                                {Number(bulletin.totalRetenuesAvances) > 0 && (
                                    <div className="flex justify-between items-center text-[var(--color-danger-600)]">
                                        <span className="font-sans">Acomptes & Avances prélevés</span>
                                        <span>-{formatAr(bulletin.totalRetenuesAvances)}</span>
                                    </div>
                                )}
                            </div>

                            {/* Liens contextuels vers modules propriétaires (La Paie ne corrige pas les sources) */}
                            <div className="flex items-center gap-2 pt-1">
                                <a
                                    href={`/pointages?tab=arbitrage&periode=${bulletin.idPeriodePaie}`}
                                    target="_blank"
                                    rel="noreferrer"
                                    className="inline-flex items-center gap-1 text-[11px] font-semibold text-[var(--color-brand-600)] hover:underline"
                                >
                                    <ExternalLink size={12} />
                                    Voir le pointage source{bulletin.versionSnapshotPointage ? ` · version ${bulletin.versionSnapshotPointage}` : ''}
                                </a>
                                <span className="text-[var(--color-fg-muted)]">•</span>
                                <a
                                    href="/conges"
                                    target="_blank"
                                    rel="noreferrer"
                                    className="inline-flex items-center gap-1 text-[11px] font-semibold text-[var(--color-brand-600)] hover:underline"
                                >
                                    <ExternalLink size={12} />
                                    Ouvrir Congés
                                </a>
                            </div>
                        </div>

                        {/* ============================================================== */}
                        {/* NIVEAU 2 — DÉTAILS MÉTIER                                      */}
                        {/* ============================================================== */}
                        {/* Éléments calculés (Pointage, Congés, HS) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2.5 rounded-none">
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="font-bold text-[var(--color-fg)] text-[11px] uppercase tracking-wider flex items-center gap-1.5">
                                    <Clock size={14} className="text-[var(--color-brand-500)]" />
                                    ÉLÉMENTS CALCULÉS (POINTAGE {bulletin.codePeriode || 'En cours'})
                                </span>
                                <span className="text-[10px] text-[var(--color-fg-muted)] font-mono">
                                    {joursTravailles == null
                                        ? 'Détail de pointage indisponible'
                                        : `${joursTravailles}j travaillés${heuresNormales != null ? ` • ${formatHours(heuresNormales)}h` : ''}`}
                                </span>
                            </div>

                            <div className="space-y-1.5">
                                {/* Congés Payés avec ventilation canonique Base/30 et TCONGE */}
                                {explication && (explication.nbJoursConge || 0) > 0 && (
                                    <div className="py-2 border-b border-[var(--color-border-subtle)]/50 space-y-1">
                                        <div className="flex justify-between items-center">
                                            <span className="font-semibold text-[var(--color-fg)]">
                                                Congés Payés ({explication.nbJoursConge} j pris)
                                            </span>
                                            <span className={`font-mono font-bold ${explication.impactNetConge >= 0 ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'}`}>
                                                {explication.impactNetConge >= 0 ? `+${formatAr(explication.impactNetConge)}` : `-${formatAr(Math.abs(explication.impactNetConge))}`}
                                            </span>
                                        </div>
                                        <div className="text-[11px] text-[var(--color-fg-muted)] pl-2 border-l-2 border-[var(--color-info-400)] dark:border-[var(--color-info-600)] space-y-0.5">
                                            <div>
                                                Déduction base (1/30ème) : <span className="font-medium text-[var(--color-fg)]">-{formatAr(explication.deductionCongeTrentieme)}</span> ({explication.nbJoursConge} j × {formatAr(explication.tauxJournalierTrentieme)}/j)
                                            </div>
                                            <div>
                                                Indemnité congé (TCONGE) : <span className="font-medium text-[var(--color-fg)]">+{formatAr(explication.indemniteConge)}</span> ({explication.nbJoursConge} j × {formatAr(explication.tauxJournalierConge)}/j)
                                            </div>
                                            <div className="text-[10px] text-[var(--color-fg-muted)]">
                                                Règle : déduction Base/30 + maintien de salaire au taux TCONGE applicable
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {/* Retenue Absences / Déficit horaire avec explicabilité backend-driven */}
                                {explication && (explication.montantRetenueAbsence > 0 || explication.minutesAbsenceRetenues > 0) && (
                                    <div className="py-2 border-b border-[var(--color-border-subtle)]/50 space-y-1">
                                        <div className="flex justify-between items-center">
                                            <span className="font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                - Retenue Déficit de Présence
                                            </span>
                                            <span className="font-mono font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                -{formatAr(explication.montantRetenueAbsence)}
                                            </span>
                                        </div>
                                        <div className="text-[11px] text-[var(--color-fg-muted)] pl-2 border-l-2 border-[var(--color-danger-400)] dark:border-[var(--color-danger-600)] space-y-0.5">
                                            <div>
                                                Déficit net retenu : <span className="font-medium text-[var(--color-fg)]">{formatMinutes(explication.minutesAbsenceRetenues)}</span> ({explication.minutesAbsenceRetenues} min • {formatHours(explication.heuresAbsenceRetenues)} h)
                                            </div>
                                            <div>
                                                Diviseur légal : <span className="font-medium text-[var(--color-fg)]">{explication.diviseurMensuelHeures} h</span> • Taux horaire : <span className="font-medium text-[var(--color-fg)]">{formatAr(explication.tauxHoraire)}/h</span>
                                            </div>
                                            {explication.minutesRetard > 0 && (
                                                <div className="text-[10px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">
                                                    Dont retards bruts enregistrés : {formatMinutes(explication.minutesRetard)} ({explication.minutesRetard} min) — <em>non déduits en doublon</em>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                )}

                                {/* Fallback retenue absence si explication absente */}
                                {!explication && (bulletin.retenueAbsence || 0) > 0 && (
                                    <div className="flex justify-between items-center py-1 border-b border-[var(--color-border-subtle)]/50">
                                        <span className="text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                            - Retenue Absences & Retards
                                        </span>
                                        <span className="font-mono font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                            -{formatAr(bulletin.retenueAbsence)}
                                        </span>
                                    </div>
                                )}

                                {((bulletin.heuresHs30 || 0) > 0 || (bulletin.heuresHs50 || 0) > 0) && (
                                    <div className="flex justify-between items-center py-1 border-b border-[var(--color-border-subtle)]/50">
                                        <span className="text-[var(--color-fg)]">
                                            + Heures Supplémentaires (+30%: {formatHours(bulletin.heuresHs30)}h / +50%: {formatHours(bulletin.heuresHs50)}h)
                                        </span>
                                        <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                            +{formatAr(bulletin.majorationHeuresSupplementaires || (explication ? (explication.montantHs30 || 0) + (explication.montantHs50 || 0) : 0))}
                                        </span>
                                    </div>
                                )}

                                {(bulletin.heuresNuit || 0) > 0 && (
                                    <div className="flex justify-between items-center py-1 border-b border-[var(--color-border-subtle)]/50">
                                        <span className="text-[var(--color-fg)]">
                                            + Majoration Nuit (+30% / {bulletin.heuresNuit}h)
                                        </span>
                                        <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                            +{formatAr(bulletin.majorationHeuresNuit || (explication ? explication.montantNuit : 0))}
                                        </span>
                                    </div>
                                )}

                                {bulletin.totalIndemnitesForfaitaires > 0 && (
                                    <div className="flex justify-between items-center py-1 border-b border-[var(--color-border-subtle)]/50">
                                        <span className="text-[var(--color-fg)]">
                                            + Indemnités Repas & Déplacement
                                        </span>
                                        <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                            +{formatAr(bulletin.totalIndemnitesForfaitaires)}
                                        </span>
                                    </div>
                                )}

                                {lignesPointage.length === 0 && !explication && (
                                    <div className="text-[11px] text-[var(--color-fg-muted)]  py-1">
                                        Pointage standard appliqué (aucun écart d'heures ou absence).
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* 3. VARIABLES DU MOIS (AJUSTEMENTS DAF) (Wireframe 2) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2.5">
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="font-bold text-[var(--color-fg)] text-[11px] uppercase tracking-wider flex items-center gap-1.5">
                                    <DollarSign size={14} className="text-[var(--color-info-500)]" />
                                    VARIABLES DU MOIS (AJUSTEMENTS MANUELS)
                                </span>
                                <button
                                    onClick={() => setShowAddVariable(!showAddVariable)}
                                    className="text-[11px] font-bold text-[var(--color-brand-500)] hover:underline flex items-center gap-1 cursor-pointer"
                                >
                                    {showAddVariable ? <ChevronUp size={13} /> : <PlusCircle size={13} />}
                                    {showAddVariable ? 'Fermer saisie' : '+ Ajouter un élément'}
                                </button>
                            </div>

                            {/* Formulaire dépliable d'ajout contextuel */}
                            {showAddVariable && (
                                <form onSubmit={handleCreerAjustement} className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] space-y-2.5 my-2">
                                    <div className="grid grid-cols-2 gap-2">
                                        <Select
                                            label="Type d'élément"
                                            value={ajustementForm.typeMouvement}
                                            onChange={(val) => handleAjustementChange('typeMouvement', val)}
                                            options={[
                                                { value: '', label: 'Sélectionner un type configuré' },
                                                ...(Array.isArray(typeMouvements) ? typeMouvements : []).map(t => ({
                                                    value: t.code,
                                                    label: `${t.libelle} — ${t.categorie || t.sens}`
                                                }))
                                            ]}
                                            required
                                        />

                                        <Input
                                            label="Montant (Ar)"
                                            type="number"
                                            step="1000"
                                            min="1000"
                                            required
                                            placeholder="Ex: 50000"
                                            value={ajustementForm.montant}
                                            onChange={(e) => handleAjustementChange('montant', e.target.value)}
                                            className="font-mono"
                                        />
                                    </div>

                                    <Input
                                        label="Motif / Justification"
                                        type="text"
                                        placeholder="Ex: Prime spéciale projet"
                                        value={ajustementForm.motif}
                                        onChange={(e) => handleAjustementChange('motif', e.target.value)}
                                    />

                                    <div className="flex justify-end gap-2 pt-1">
                                        <Button
                                            type="button"
                                            variant="secondary"
                                            size="sm"
                                            onClick={() => setShowAddVariable(false)}
                                        >
                                            Annuler
                                        </Button>
                                        <Button
                                            type="submit"
                                            variant="primary"
                                            size="sm"
                                            disabled={submittingAjustement}
                                        >
                                            {submittingAjustement ? 'Enregistrement...' : 'Enregistrer & Recalculer'}
                                        </Button>
                                    </div>
                                </form>
                            )}

                            {/* Liste des variables ventilées */}
                            <div className="space-y-1.5">
                                {bulletin.primeAnciennete > 0 && (
                                    <div className="flex justify-between items-center py-1 border-b border-[var(--color-border-subtle)]/50">
                                        <span className="text-[var(--color-fg)]">+ Prime d'ancienneté</span>
                                        <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">+{formatAr(bulletin.primeAnciennete)}</span>
                                    </div>
                                )}
                                {bulletin.primeAssiduite > 0 && (
                                    <div className="flex justify-between items-center py-1 border-b border-[var(--color-border-subtle)]/50">
                                        <span className="text-[var(--color-fg)]">+ Prime d'assiduité</span>
                                        <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">+{formatAr(bulletin.primeAssiduite)}</span>
                                    </div>
                                )}
                                {bulletin.primePerformance > 0 && (
                                    <div className="flex justify-between items-center py-1 border-b border-[var(--color-border-subtle)]/50">
                                        <span className="text-[var(--color-fg)]">+ Prime de performance</span>
                                        <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">+{formatAr(bulletin.primePerformance)}</span>
                                    </div>
                                )}
                                {bulletin.totalRetenuesAvances > 0 && (
                                    <div className="flex justify-between items-center py-1 border-b border-[var(--color-border-subtle)]/50">
                                        <span className="text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">- Acomptes & Avances sur salaire</span>
                                        <span className="font-mono font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(bulletin.totalRetenuesAvances)}</span>
                                    </div>
                                )}
                                {lignesVariables.length === 0 && bulletin.totalRetenuesAvances === 0 && (
                                    <div className="text-[11px] text-[var(--color-fg-muted)]  py-1">
                                        Aucune variable ou acompte enregistré ce mois-ci.
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* 4. SYNTHÈSE LÉGALE, SOCIALE & FISCALE (Wireframe 2) */}
                        <div className="p-3.5 space-y-2 bg-[var(--color-bg-subtle)]/40">
                            <div className="flex justify-between items-center font-bold text-xs py-1 border-b border-[var(--color-border-subtle)]">
                                <span className="uppercase tracking-wide text-[var(--color-fg)]">TOTAL BRUT</span>
                                <span className="font-mono text-sm text-[var(--color-fg)]">{formatAr(bulletin.totalGainsBruts)}</span>
                            </div>

                            <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                <span>- {ligneCnaps?.libelleRubrique || 'CNaPS — part salariale'}</span>
                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">{cnapsVal == null ? 'Détail indisponible' : `-${formatAr(cnapsVal)}`}</span>
                            </div>

                            <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                <span>- {ligneOstie?.libelleRubrique || 'Santé — part salariale'}</span>
                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">{ostieVal == null ? 'Détail indisponible' : `-${formatAr(ostieVal)}`}</span>
                            </div>

                            <div className="flex justify-between items-center font-bold text-xs py-1 border-t border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-2">
                                <span className="text-[var(--color-fg)]" title="Revenu après cotisations et traitements fiscaux, avant arrondi de l’assiette IRSA.">= REVENU NET IMPOSABLE</span>
                                <span className="font-mono text-[var(--color-fg)]">{formatAr(fiscalite?.revenuNetImposable ?? netImposable)}</span>
                            </div>

                            <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                <span title="Montant arrondi utilisé par le moteur comme assiette du barème IRSA.">Base imposable IRSA</span>
                                <span className="font-mono font-semibold text-[var(--color-fg)]">{formatAr(fiscalite?.assietteIrsa ?? bulletin.assietteIrsa ?? bulletin.baseIrsaArrondie)}</span>
                            </div>

                            <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                <span>- IRSA dû</span>
                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(fiscalite?.irsaDu ?? bulletin.irsaDu ?? bulletin.irsaNet)}</span>
                            </div>

                            {(fiscalite || bulletin.assietteIrsa != null || bulletin.baseIrsaArrondie != null) && (
                                <div className="border-t border-[var(--color-border-subtle)] pt-2">
                                    <button
                                        type="button"
                                        onClick={() => setShowTaxDetails(value => !value)}
                                        className="inline-flex items-center gap-1 text-[11px] font-semibold text-[var(--color-brand-600)] hover:underline focus:outline-none focus:ring-2 focus:ring-[var(--color-focus-ring)]"
                                        aria-expanded={showTaxDetails}
                                    >
                                        <Info size={13} aria-hidden="true" />
                                        {showTaxDetails ? 'Masquer le détail IRSA' : 'Voir le détail du calcul IRSA'}
                                    </button>
                                    {showTaxDetails && (
                                        <div className="mt-2 border border-[var(--color-border-subtle)] bg-[var(--color-surface)]" data-testid="irsa-breakdown">
                                            {(fiscalite?.detailsTranches || []).map((tranche, index) => (
                                                <div key={`${tranche.borneInferieure}-${index}`} className="grid grid-cols-[1fr_auto_auto] gap-3 px-3 py-2 border-b last:border-b-0 border-[var(--color-border-subtle)] text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">
                                                        {formatAr(tranche.borneInferieure)} → {tranche.borneSuperieure == null ? 'au-delà' : formatAr(tranche.borneSuperieure)}
                                                    </span>
                                                    <span className="font-mono text-[var(--color-fg-muted)]">
                                                        {formatAr(tranche.montantTaxable)} × {(Number(tranche.taux) * 100).toLocaleString('fr-FR')} %
                                                    </span>
                                                    <span className="font-mono font-semibold text-[var(--color-fg)]">{formatAr(tranche.impotProduit)}</span>
                                                </div>
                                            ))}
                                            {(!fiscalite?.detailsTranches || fiscalite.detailsTranches.length === 0) && (
                                                <p className="px-3 py-3 text-[11px] text-[var(--color-fg-muted)]">
                                                    Le détail des tranches n’est pas fourni pour ce bulletin. Aucune tranche n’est reconstruite côté interface.
                                                </p>
                                            )}
                                            <div className="grid grid-cols-2 gap-3 px-3 py-2 bg-[var(--color-bg-subtle)] text-[11px]">
                                                <span>IRSA calculé</span><span className="text-right font-mono font-semibold">{formatAr(fiscalite?.irsaCalcule ?? bulletin.irsaCalcule ?? bulletin.irsaBrut)}</span>
                                                <span>Réduction</span><span className="text-right font-mono">-{formatAr(fiscalite?.reductionChargeFamille ?? bulletin.reductionChargeFamille ?? bulletin.reductionEnfants)}</span>
                                                <span className="font-bold">IRSA dû</span><span className="text-right font-mono font-bold">{formatAr(fiscalite?.irsaDu ?? bulletin.irsaDu ?? bulletin.irsaNet)}</span>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}

                            {Number(bulletin.totalRetenuesAvances) > 0 && (
                                <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                    <span>- Mensualité Prêt / Acomptes prélevés</span>
                                    <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(bulletin.totalRetenuesAvances)}</span>
                                </div>
                            )}

                            {/* Encadré NET À PAYER (Wireframe 2) */}
                            <div className="mt-3 pt-3 border-t-2 border-[var(--color-brand-800)] flex justify-between items-center bg-[var(--color-ink-950)] text-[var(--color-on-color)] p-3">
                                <div>
                                    <span className="text-[10px] uppercase font-bold tracking-wider text-[var(--color-ink-200)] block">
                                        NET À PAYER
                                    </span>
                                    <span className="text-[10px] text-[var(--color-ink-350)] font-mono">
                                        Arrondi bancaire légal
                                    </span>
                                </div>
                                <span className="text-xl font-mono font-black text-[var(--color-on-color)]">
                                    {formatAr(bulletin.netAPayer)}
                                </span>
                            </div>
                        </div>

                        {/* ============================================================== */}
                        {/* NIVEAU 3 — AUDIT TECHNIQUE & TRAÇABILITÉ (Collapsed par défaut) */}
                        {/* ============================================================== */}
                        <div className="pt-2 border-t border-[var(--color-border-subtle)]">
                            <button
                                type="button"
                                onClick={() => setShowDetailRubriques(!showDetailRubriques)}
                                className="w-full p-2.5 bg-[var(--color-bg-subtle)] hover:bg-[var(--color-border-subtle)] text-[11px] font-bold text-[var(--color-fg)] flex items-center justify-between rounded transition-colors"
                            >
                                <span className="flex items-center gap-1.5 uppercase tracking-wider text-[10px] text-[var(--color-fg-muted)]">
                                    <Info size={13} />
                                    Niveau 3 — Audit technique & Grand livre ({lignes.length} rubriques)
                                </span>
                                <span className="text-[10px] font-medium text-[var(--color-brand-600)]">
                                    {showDetailRubriques ? 'Masquer' : 'Déplier'}
                                </span>
                            </button>

                            {showDetailRubriques && (
                                <div className="mt-2 space-y-2">
                                    {/* Méta-données de traçabilité */}
                                    <div className="p-2 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded text-[10px] font-mono text-[var(--color-fg-muted)] flex items-center justify-between">
                                        <span>Snapshot ID: {bulletin.idBulletin}</span>
                                        <span>Période: {bulletin.codePeriode || 'En cours'}</span>
                                        <span>Statut: {bulletin.statut}</span>
                                    </div>

                                    {/* Grand livre des rubriques */}
                                    <div className="border border-[var(--color-border-subtle)] rounded overflow-hidden">
                                        <table className="w-full text-left text-xs border-collapse">
                                            <thead>
                                                <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[10px] text-[var(--color-fg-muted)] uppercase">
                                                    <th className="p-2">Code</th>
                                                    <th className="p-2">Désignation</th>
                                                    <th className="p-2 text-right">Base</th>
                                                    <th className="p-2 text-right">Gain</th>
                                                    <th className="p-2 text-right">Retenue</th>
                                                </tr>
                                            </thead>
                                            <tbody className="divide-y divide-[var(--color-border-subtle)]">
                                                {lignes.map((l, i) => {
                                                    const isRetenue = l.sens === 'RETENUE';
                                                    const isGain = l.sens === 'GAIN' || l.sens === 'INFO';
                                                    const montantRet = isRetenue ? (l.montantSalarial ?? l.montantRetenue) : (l.montantRetenue && Number(l.montantRetenue) > 0 ? l.montantRetenue : null);
                                                    const montantGn = isGain ? (l.montantSalarial ?? l.montantGain) : (l.montantGain && Number(l.montantGain) > 0 ? l.montantGain : null);
                                                    return (
                                                        <tr key={i} className="hover:bg-[var(--color-bg-subtle)]/40 font-mono text-[11px]">
                                                            <td className="p-2 text-[var(--color-fg-subtle)]">{l.codeRubrique}</td>
                                                            <td className="p-2 font-sans font-medium text-[var(--color-fg)]">{l.libelleRubrique}</td>
                                                            <td className="p-2 text-right text-[var(--color-fg-muted)]">{formatMoneyAr(l.baseCalcul)}</td>
                                                            <td className="p-2 text-right text-[var(--color-success-600)] font-bold">{montantGn && Number(montantGn) > 0 ? formatMoneyAr(montantGn) : '—'}</td>
                                                            <td className="p-2 text-right text-[var(--color-danger-600)] font-bold">{montantRet && Number(montantRet) > 0 ? formatMoneyAr(montantRet) : '—'}</td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </SlideOver>

            {/* Modal d'impression A4 */}
            {showPrintModal && data && (
                <BulletinPaiePrintView
                    bulletinComplet={data}
                    onClose={() => setShowPrintModal(false)}
                />
            )}
        </>
    );
}
