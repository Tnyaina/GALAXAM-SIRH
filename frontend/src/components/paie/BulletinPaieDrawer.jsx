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
    Info
} from 'lucide-react';
import SlideOver from '@/components/ui/overlays/SlideOver';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import paieService from '@/api/paie/paieService';
import { getErrorMessage } from '@/api/apiClient';
import BulletinPaiePrintView from './BulletinPaiePrintView';

/**
 * Tiroir Latéral — Consultation et Édition Contextuelle du Bulletin (GALAXAM SIRH)
 * Conforme au Wireframe Écran 2 du Rapport d'Audit.
 */
export default function BulletinPaieDrawer({ isOpen, onClose, idBulletin, onRecalculateSuccess }) {
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const [showPrintModal, setShowPrintModal] = useState(false);
    const [showAddVariable, setShowAddVariable] = useState(false);
    const [showDetailRubriques, setShowDetailRubriques] = useState(false);

    // Formulaire d'ajout d'ajustement contextuel
    const [typeMouvements, setTypeMouvements] = useState([]);
    const [submittingAjustement, setSubmittingAjustement] = useState(false);
    const [recalculating, setRecalculating] = useState(false);
    const [ajustementForm, setAjustementForm] = useState({
        typeMouvement: 'PRIM_EXC',
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
            console.error('Erreur chargement types mouvements :', err);
        }
    }, []);

    useEffect(() => {
        if (isOpen && idBulletin) {
            chargerBulletin();
            chargerTypesMouvements();
        } else {
            setData(null);
            setError(null);
            setFeedbackMsg(null);
            setShowAddVariable(false);
            setShowDetailRubriques(false);
        }
    }, [isOpen, idBulletin, chargerBulletin, chargerTypesMouvements]);

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
                typeMouvement: 'PRIM_EXC',
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

    const formatAr = (val) => {
        if (val === null || val === undefined) return '0 Ar';
        return Number(val).toLocaleString('fr-MG', { minimumFractionDigits: 0, maximumFractionDigits: 0 }) + ' Ar';
    };

    const formatMinutes = (totalMin) => {
        if (!totalMin || totalMin <= 0) return '0 min';
        const h = Math.floor(totalMin / 60);
        const m = totalMin % 60;
        if (h === 0) return `${m} min`;
        if (m === 0) return `${h} h`;
        return `${h} h ${m} min`;
    };

    const bulletin = data?.bulletin;
    const explication = data?.explication;
    const lignes = Array.isArray(data?.lignes) ? data.lignes : [];

    // Classification des lignes pour l'affichage fidèle au Wireframe 2
    const lignesPointage = lignes.filter(l => 
        ['HEURES_SUP', 'HEURES_SUP_30', 'HEURES_SUP_50', 'HEURES_NUIT', 'HEURES_DIMANCHE', 'MAJORATION_FERIE',
         'INDEMNITE_REPAS', 'INDEMNITE_DEPLACEMENT', 'RETENUE_ABSENCE', 'ABSENCE_RETENUE',
         'DEDUCTION_CONGE_BASE', 'INDEMNITE_CONGE_PAYE'].includes(l.codeRubrique) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('heure')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('nuit')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('absence')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('congé')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('repas')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('deplacement'))
    );

    const lignesVariables = lignes.filter(l => 
        ['PRIME_PERF', 'PRIME_EXC', 'PRIME_ASSI', 'ACOMPTE_15', 'AVANCE_SPEC', 'PRET_ENTREPRISE'].includes(l.codeRubrique) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('prime')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('acompte')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('avance')) ||
        (l.libelleRubrique && l.libelleRubrique.toLowerCase().includes('pret'))
    );

    return (
        <>
            <SlideOver
                isOpen={isOpen}
                onClose={onClose}
                title={bulletin ? `BULLETIN DE PAIE — ${bulletin.nomEmploye} ${bulletin.prenomEmploye}` : 'Bulletin de Paie'}
                subtitle={bulletin ? `Matricule : ${bulletin.matricule} | Période : ${bulletin.codePeriode || 'En cours'} | Site : ${bulletin.nomSite || 'Siège'} | Contrat : ${bulletin.typeContrat || 'CDI'}` : 'Chargement...'}
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
                    <div className={`p-3 mb-4 text-xs flex items-center gap-2 border ${
                        feedbackMsg.type === 'success' 
                            ? 'bg-[var(--color-success-500)]/10 border-[var(--color-success-500)]/25 text-[var(--color-success-700)] dark:text-[var(--color-success-300)]' 
                            : 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/25 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]'
                    }`}>
                        {feedbackMsg.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
                        <span>{feedbackMsg.text}</span>
                    </div>
                )}

                {bulletin && (
                    <div className="space-y-4 text-xs font-sans">
                        {/* 1. SALAIRE DE BASE CONTRACTUEL (Wireframe 2) */}
                        <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border)] p-3.5 flex items-center justify-between">
                            <span className="font-bold text-[var(--color-fg)] uppercase tracking-wider text-xs">
                                SALAIRE DE BASE CONTRACTUEL
                            </span>
                            <span className="font-mono font-black text-base text-[var(--color-fg)]">
                                {formatAr(bulletin.salaireBase)}
                            </span>
                        </div>

                        {/* 2. ÉLÉMENTS CALCULÉS (POINTAGE 26/08 → 25/09) (Wireframe 2) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2.5">
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="font-bold text-[var(--color-fg)] text-[11px] uppercase tracking-wider flex items-center gap-1.5">
                                    <Clock size={14} className="text-[var(--color-brand-500)]" />
                                    ÉLÉMENTS CALCULÉS (POINTAGE {bulletin.codePeriode || 'En cours'})
                                </span>
                                <span className="text-[10px] text-[var(--color-fg-muted)] font-mono">
                                    {bulletin.nbJoursTravailles == null || bulletin.heuresNormales == null
                                        ? 'Détail de pointage indisponible'
                                        : `${bulletin.nbJoursTravailles}j travaillés • ${bulletin.heuresNormales}h`}
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
                                        <div className="text-[11px] text-[var(--color-fg-muted)] pl-2 border-l-2 border-blue-400 dark:border-blue-600 space-y-0.5">
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
                                        <div className="text-[11px] text-[var(--color-fg-muted)] pl-2 border-l-2 border-red-400 dark:border-red-600 space-y-0.5">
                                            <div>
                                                Déficit net retenu : <span className="font-medium text-[var(--color-fg)]">{formatMinutes(explication.minutesAbsenceRetenues)}</span> ({explication.minutesAbsenceRetenues} min • {explication.heuresAbsenceRetenues} h)
                                            </div>
                                            <div>
                                                Diviseur légal : <span className="font-medium text-[var(--color-fg)]">{explication.diviseurMensuelHeures} h</span> • Taux horaire : <span className="font-medium text-[var(--color-fg)]">{formatAr(explication.tauxHoraire)}/h</span>
                                            </div>
                                            {explication.minutesRetard > 0 && (
                                                <div className="text-[10px] text-amber-700 dark:text-amber-400">
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
                                            + Heures Supplémentaires (+30%: {bulletin.heuresHs30 || 0}h / +50%: {bulletin.heuresHs50 || 0}h)
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
                                    <div className="text-[11px] text-[var(--color-fg-muted)] italic py-1">
                                        Pointage standard appliqué (aucun écart d'heures ou absence).
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* 3. VARIABLES DU MOIS (AJUSTEMENTS DAF) (Wireframe 2) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2.5">
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="font-bold text-[var(--color-fg)] text-[11px] uppercase tracking-wider flex items-center gap-1.5">
                                    <DollarSign size={14} className="text-blue-500" />
                                    VARIABLES DU MOIS (AJUSTEMENTS DAF)
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
                                                { value: 'PRIM_EXC', label: 'Prime exceptionnelle (+ Gain)' },
                                                { value: 'ACOMPTE_15', label: 'Acompte quinzaine (- Retenue)' },
                                                { value: 'AVANCE_SPEC', label: 'Avance spéciale (- Retenue)' },
                                                { value: 'DED_DIV', label: 'Déduction diverse (- Retenue)' },
                                                ...(Array.isArray(typeMouvements) ? typeMouvements : []).map(t => ({
                                                    value: t.code,
                                                    label: `${t.libelle} (${t.sens})`
                                                }))
                                            ]}
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
                                    <div className="text-[11px] text-[var(--color-fg-muted)] italic py-1">
                                        Aucune variable ou acompte enregistré ce mois-ci.
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* 4. SYNTHÈSE LÉGALE, SOCIALE & FISCALE (Wireframe 2) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2 bg-slate-50/50">
                            <div className="flex justify-between items-center font-bold text-xs py-1 border-b border-slate-200">
                                <span className="uppercase tracking-wide text-slate-800">TOTAL BRUT</span>
                                <span className="font-mono text-sm text-slate-900">{formatAr(bulletin.totalGainsBruts)}</span>
                            </div>

                            <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                <span>- CNaPS Salariale (1% plafonné 8× SMIG)</span>
                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">{bulletin.cnapsSalariale == null ? 'Détail indisponible' : `-${formatAr(bulletin.cnapsSalariale)}`}</span>
                            </div>

                            <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                <span>- Santé OSTIE / AMIT Salariale (1%)</span>
                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">{bulletin.ostieSalariale == null ? 'Détail indisponible' : `-${formatAr(bulletin.ostieSalariale)}`}</span>
                            </div>

                            <div className="flex justify-between items-center font-bold text-xs py-1 border-t border-b border-slate-200 bg-white px-2">
                                <span className="text-slate-800">= NET IMPOSABLE</span>
                                <span className="font-mono text-slate-900">{formatAr(bulletin.netImposable)}</span>
                            </div>

                            <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                <span>- IRSA Net (Barème progressif LF 2026 - min 3 000 Ar)</span>
                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(bulletin.irsaNet)}</span>
                            </div>

                            {Number(bulletin.totalRetenuesAvances) > 0 && (
                                <div className="flex justify-between items-center text-[var(--color-fg-muted)] py-0.5">
                                    <span>- Mensualité Prêt / Acomptes prélevés</span>
                                    <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(bulletin.totalRetenuesAvances)}</span>
                                </div>
                            )}

                            {/* Encadré NET À PAYER (Wireframe 2) */}
                            <div className="mt-3 pt-3 border-t-2 border-slate-800 flex justify-between items-center bg-slate-900 text-white p-3">
                                <div>
                                    <span className="text-[10px] uppercase font-bold tracking-wider text-slate-300 block">
                                        NET À PAYER
                                    </span>
                                    <span className="text-[10px] text-slate-400 font-mono">
                                        Arrondi bancaire légal
                                    </span>
                                </div>
                                <span className="text-xl font-mono font-black text-white">
                                    {formatAr(bulletin.netAPayer)}
                                </span>
                            </div>
                        </div>

                        {/* Bouton pour afficher/masquer le grand livre détaillé des rubriques */}
                        <div className="pt-1">
                            <button
                                onClick={() => setShowDetailRubriques(!showDetailRubriques)}
                                className="text-[11px] font-semibold text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] flex items-center gap-1 cursor-pointer"
                            >
                                <Info size={13} />
                                {showDetailRubriques ? 'Masquer la ventilation complète des rubriques' : `Voir la ventilation détaillée (${lignes.length} rubriques)`}
                            </button>

                            {showDetailRubriques && (
                                <div className="mt-2 border border-[var(--color-border-subtle)] overflow-hidden">
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
                                            {lignes.map((l, i) => (
                                                <tr key={i} className="hover:bg-[var(--color-bg-subtle)]/40 font-mono text-[11px]">
                                                    <td className="p-2 text-slate-500">{l.codeRubrique}</td>
                                                    <td className="p-2 font-sans font-medium text-slate-800">{l.libelleRubrique}</td>
                                                    <td className="p-2 text-right text-slate-600">{l.baseCalcul ? Number(l.baseCalcul).toLocaleString('fr-MG') : '-'}</td>
                                                    <td className="p-2 text-right text-[var(--color-success-600)] font-bold">{l.montantGain && Number(l.montantGain) > 0 ? Number(l.montantGain).toLocaleString('fr-MG') : '-'}</td>
                                                    <td className="p-2 text-right text-[var(--color-danger-600)] font-bold">{l.montantRetenue && Number(l.montantRetenue) > 0 ? Number(l.montantRetenue).toLocaleString('fr-MG') : '-'}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
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
