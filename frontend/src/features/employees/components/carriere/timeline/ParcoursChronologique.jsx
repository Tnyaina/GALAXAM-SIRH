import { useMemo, useState } from 'react';
import {
    Briefcase,
    FileCheck,
    CheckCircle2,
    RotateCw,
    TrendingUp,
    MapPin,
    Calendar,
    Award,
    FileText,
    ArrowUpRight,
    ArrowDownUp,
    Building2,
    UserCheck,
    AlertCircle,
    Layers,
    Clock,
    DollarSign
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import HasPermission from '@/features/auth/components/HasPermission';
import { useAuth } from '@/context/AuthContext';
import { fmtDate } from '@/utils/dateUtils';

export default function ParcoursChronologique({
    workflowsList = [],
    contratsList = [],
    contratActif = null,
    onSelectWorkflowRecap,
    onOpenCertificat,
    onOpenAttestation
}) {
    const [ordreDecroissant, setOrdreDecroissant] = useState(true);
    const { hasPermission } = useAuth();
    const canSeePaie = hasPermission('PAIE_READ');

    const safeContrats = useMemo(() => {
        const list = Array.isArray(contratsList) ? [...contratsList] : (contratsList?.items || contratsList?.content || []);
        return list.filter(ct => ct && ct.codeStatutContrat !== 'BROUILLON');
    }, [contratsList]);

    const safeWorkflows = useMemo(() => {
        return Array.isArray(workflowsList) ? [...workflowsList] : [];
    }, [workflowsList]);

    // Tous les workflows ordonnés du plus ancien au plus récent pour calculer les évolutions (diffs)
    const workflowsChronologiques = useMemo(() => {
        return [...safeWorkflows].sort((a, b) => {
            const dateA = new Date(a.dateEffet || a.dateDemande || 0).getTime();
            const dateB = new Date(b.dateEffet || b.dateDemande || 0).getTime();
            return dateA - dateB;
        });
    }, [safeWorkflows]);

    // Carte des changements par workflow (diff par rapport à l'événement précédent)
    const mapEvolutions = useMemo(() => {
        const diffs = new Map();
        for (let i = 0; i < workflowsChronologiques.length; i++) {
            const current = workflowsChronologiques[i];
            const prev = i > 0 ? workflowsChronologiques[i - 1] : null;

            const evolutions = [];

            if (prev) {
                // Changement de fonction
                if (current.libelleFonction && prev.libelleFonction && current.libelleFonction !== prev.libelleFonction) {
                    evolutions.push({
                        type: 'FONCTION',
                        label: `Évolution de poste : ${prev.libelleFonction} → ${current.libelleFonction}`,
                        valeur: current.libelleFonction,
                        ancienneValeur: prev.libelleFonction,
                        icon: <TrendingUp size={12} className="text-[var(--color-info-500)]" />
                    });
                }

                // Changement de site
                if (current.libelleSite && prev.libelleSite && current.libelleSite !== prev.libelleSite) {
                    evolutions.push({
                        type: 'SITE',
                        label: `Mobilité de site : ${prev.libelleSite} → ${current.libelleSite}`,
                        valeur: current.libelleSite,
                        icon: <MapPin size={12} className="text-[var(--color-warning-500)]" />
                    });
                }

                // Changement de salaire
                if (canSeePaie && current.salaireBase && prev.salaireBase && Number(current.salaireBase) !== Number(prev.salaireBase)) {
                    const salCurrent = Number(current.salaireBase);
                    const salPrev = Number(prev.salaireBase);
                    const diffMontant = salCurrent - salPrev;
                    const diffPct = salPrev > 0 ? ((diffMontant / salPrev) * 100).toFixed(1) : null;
                    const isAugmentation = diffMontant > 0;

                    evolutions.push({
                        type: 'SALAIRE',
                        label: `Révision salariale : ${salCurrent.toLocaleString('fr-FR')} Ar (${isAugmentation ? '+' : ''}${diffMontant.toLocaleString('fr-FR')} Ar${diffPct ? ` · ${isAugmentation ? '+' : ''}${diffPct}%` : ''})`,
                        valeur: `${salCurrent.toLocaleString('fr-FR')} Ar`,
                        icon: <DollarSign size={12} className="text-[var(--color-success-500)]" />
                    });
                }

                // Changement de type de contrat
                if (current.libelleTypeContrat && prev.libelleTypeContrat && current.libelleTypeContrat !== prev.libelleTypeContrat) {
                    evolutions.push({
                        type: 'CONTRAT',
                        label: `Passage de ${prev.libelleTypeContrat} à ${current.libelleTypeContrat}`,
                        valeur: current.libelleTypeContrat,
                        icon: <Briefcase size={12} className="text-brand-500" />
                    });
                }
            }

            diffs.set(current.idWorkflow, evolutions);
        }
        return diffs;
    }, [workflowsChronologiques, canSeePaie]);

    // Groupement des workflows sous leurs contrats respectifs
    const chapitresContractuels = useMemo(() => {
        // 1. Liste des contrats triés
        const contratsTries = [...safeContrats].sort((a, b) => {
            const dateA = new Date(a.dateDebut || a.dateSignature || 0).getTime();
            const dateB = new Date(b.dateDebut || b.dateSignature || 0).getTime();
            return dateA - dateB;
        });

        const chapitres = [];
        const workflowsTraites = new Set();

        contratsTries.forEach((contrat, index) => {
            // Trouver tous les workflows liés à ce contrat
            const workflowsDuContrat = workflowsChronologiques.filter(wf => {
                const correspondCible = Number(wf.idContratCible) === Number(contrat.idContrat);
                const correspondSource = Number(wf.idContratSource) === Number(contrat.idContrat);
                const correspondNumero = (wf.numeroContratCible && wf.numeroContratCible === contrat.numeroContrat) ||
                    (wf.numeroContratSource && wf.numeroContratSource === contrat.numeroContrat);
                return correspondCible || correspondSource || correspondNumero;
            });

            workflowsDuContrat.forEach(wf => workflowsTraites.add(wf.idWorkflow));

            chapitres.push({
                index: index + 1,
                contrat,
                workflows: workflowsDuContrat,
                dateDebut: contrat.dateDebut || contrat.dateSignature,
                dateFin: contrat.dateFin,
                isActif: Boolean(contrat.isActif),
                isPlanifie: Boolean(contrat.isPlanifie),
                isCloture: Boolean(contrat.isCloture || contrat.isRompu),
                isAnnule: Boolean(contrat.isAnnule)
            });
        });

        // 2. Workflows isolés (s'il y en a sans contrat rattaché)
        const workflowsIsoles = workflowsChronologiques.filter(wf => !workflowsTraites.has(wf.idWorkflow));
        if (workflowsIsoles.length > 0) {
            chapitres.push({
                index: chapitres.length + 1,
                contrat: null,
                workflows: workflowsIsoles,
                dateDebut: workflowsIsoles[0]?.dateEffet || workflowsIsoles[0]?.dateDemande,
                dateFin: null,
                isActif: false,
                isPlanifie: false,
                isCloture: false,
                isAnnule: false
            });
        }

        // Tri selon l'ordre choisi (croissant ou décroissant)
        return chapitres.sort((a, b) => {
            const dateA = new Date(a.dateDebut || 0).getTime();
            const dateB = new Date(b.dateDebut || 0).getTime();
            return ordreDecroissant ? dateB - dateA : dateA - dateB;
        });
    }, [safeContrats, workflowsChronologiques, ordreDecroissant]);

    const premierEvenement = workflowsChronologiques[0] || null;
    const dernierEvenement = workflowsChronologiques[workflowsChronologiques.length - 1] || null;

    const getIconForType = (codeType = '', isActif = false) => {
        const code = codeType.toUpperCase();
        if (isActif) return <CheckCircle2 size={15} className="text-success-600 dark:text-success-400" />;
        if (code.includes('EMBAUCHE')) return <Briefcase size={15} className="text-brand-500" />;
        if (code.includes('CDI')) return <Award size={15} className="text-success-600 dark:text-success-400" />;
        if (code.includes('PROMOTION') || code.includes('REVALORISATION')) return <TrendingUp size={15} className="text-[var(--color-info-500)]" />;
        if (code.includes('MUTATION')) return <MapPin size={15} className="text-[var(--color-warning-500)]" />;
        if (code.includes('CONFIRMATION')) return <FileCheck size={15} className="text-[var(--color-success-500)]" />;
        if (code.includes('RENOUVELLEMENT')) return <RotateCw size={15} className="text-sky-500" />;
        return <FileText size={15} className="text-ink-500" />;
    };

    const getTypeContratBadgeVariant = (type) => {
        if (!type) return 'default';
        const t = type.toUpperCase();
        if (t.includes('CDI')) return 'success';
        if (t.includes('CDD')) return 'warning';
        if (t.includes('STAGE')) return 'info';
        if (t.includes('PRESTATION') || t.includes('CONSULTANT')) return 'primary';
        return 'default';
    };

    if (chapitresContractuels.length === 0 && safeWorkflows.length === 0) {
        return (
            <div className="p-8 border border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 text-center space-y-2">
                <FileText size={28} className="mx-auto text-ink-300 dark:text-ink-600" />
                <p className="text-xs font-semibold text-ink-600 dark:text-ink-300">Aucun historique enregistré</p>
                <p className="text-[11px] text-ink-400">Le parcours professionnel et les contrats du salarié s'afficheront ici dès la création de son contrat.</p>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Barre de Synthèse du Parcours Global */}
            <div className="p-4 border border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 dark:border-ink-800 pb-3">
                    <div className="flex items-center gap-2">
                        <Layers size={16} className="text-brand-500" />
                        <div>
                            <span className="text-[10px] font-bold uppercase tracking-wider text-brand-500">
                                Parcours Collaborateur
                            </span>
                            <h4 className="text-xs font-bold text-ink-900 dark:text-white">
                                {chapitresContractuels.length} période{chapitresContractuels.length > 1 ? 's' : ''} contractuelle{chapitresContractuels.length > 1 ? 's' : ''} et {safeWorkflows.length} événement{safeWorkflows.length > 1 ? 's' : ''}
                            </h4>
                        </div>
                    </div>

                    <Button
                        variant="ghost"
                        size="xs"
                        iconLeft={<ArrowDownUp size={12} />}
                        onClick={() => setOrdreDecroissant(prev => !prev)}
                    >
                        {ordreDecroissant ? 'Plus récent en premier' : 'Ordre chronologique'}
                    </Button>
                </div>

                {/* 3 Cartouches Récapitulatives */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                    <div className="p-2.5 bg-ink-50/60 dark:bg-ink-950/40 border border-ink-150 dark:border-ink-800">
                        <span className="text-[10px] font-semibold text-ink-400 uppercase tracking-wider block">
                            Arrivée dans l'entreprise
                        </span>
                        <p className="font-bold text-ink-900 dark:text-ink-100 mt-0.5 font-mono">
                            {premierEvenement ? fmtDate(premierEvenement.dateEffet || premierEvenement.dateDemande) : '-'}
                        </p>
                        <p className="text-[11px] text-ink-500 truncate">
                            {premierEvenement?.libelleTypeContrat || premierEvenement?.libelleFonction || 'Embauche initiale'}
                        </p>
                    </div>

                    <div className="p-2.5 bg-ink-50/60 dark:bg-ink-950/40 border border-ink-150 dark:border-ink-800">
                        <span className="text-[10px] font-semibold text-ink-400 uppercase tracking-wider block">
                            Poste & Statut en vigueur
                        </span>
                        <p className="font-bold text-brand-500 mt-0.5 truncate">
                            {contratActif?.libelleFonction || dernierEvenement?.libelleFonction || '-'}
                        </p>
                        <p className="text-[11px] text-ink-500 truncate">
                            {contratActif?.libelleTypeContrat || dernierEvenement?.libelleTypeContrat || '-'}
                        </p>
                    </div>

                    <div className="p-2.5 bg-ink-50/60 dark:bg-ink-950/40 border border-ink-150 dark:border-ink-800">
                        <span className="text-[10px] font-semibold text-ink-400 uppercase tracking-wider block">
                            Lieu d'affectation
                        </span>
                        <p className="font-bold text-ink-900 dark:text-ink-100 mt-0.5 truncate">
                            {contratActif?.libelleSite || dernierEvenement?.libelleSite || '-'}
                        </p>
                        <p className="text-[11px] text-ink-500 truncate">
                            {contratActif?.libelleDepartement || dernierEvenement?.libelleDepartement || '-'}
                        </p>
                    </div>
                </div>
            </div>

            {/* Liste structurée par Chapitres Contractuels */}
            <div className="space-y-6">
                {chapitresContractuels.map((chapitre) => {
                    const ct = chapitre.contrat;
                    const isContratActif = chapitre.isActif;
                    const isPlanifie = chapitre.isPlanifie;
                    const isCloture = chapitre.isCloture;
                    const isAnnule = chapitre.isAnnule;

                    const canGenerateCertificat = ct && !isContratActif && !isPlanifie && !isAnnule && isCloture;
                    const canGenerateAttestation = ct && isContratActif;

                    return (
                        <div
                            key={ct?.idContrat || `chapitre-${chapitre.index}`}
                            className={`
                                border transition-all duration-150
                                ${isContratActif
                                    ? 'bg-white dark:bg-ink-900 border-success-500/40 shadow-sm'
                                    : 'bg-white dark:bg-ink-900 border-ink-150 dark:border-ink-700'
                                }
                            `}
                        >
                            {/* En-tête du Contrat-Cadre (Chapitre) */}
                            <div className={`
                                px-5 py-4 border-b flex flex-wrap items-center justify-between gap-3
                                ${isContratActif
                                    ? 'bg-success-500/5 dark:bg-success-950/20 border-success-500/30'
                                    : 'bg-ink-50/70 dark:bg-ink-950/60 border-ink-150 dark:border-ink-800'
                                }
                            `}>
                                <div className="space-y-1">
                                    <div className="flex items-center gap-2 flex-wrap">
                                        <span className="text-[10px] font-bold uppercase tracking-wider text-ink-400">
                                            Contrat N°{chapitre.index}
                                        </span>
                                        {ct?.libelleTypeContrat && (
                                            <Badge variant={getTypeContratBadgeVariant(ct.libelleTypeContrat)} size="xs">
                                                {ct.libelleTypeContrat}
                                            </Badge>
                                        )}
                                        <span className="font-mono text-xs font-bold text-brand-500">
                                            {ct?.numeroContrat || 'Sans numéro'}
                                        </span>
                                        <StatusBadge
                                            status={isPlanifie ? 'PLANIFIE' : isAnnule ? 'ANNULE' : isContratActif ? 'ACTIF' : (ct?.codeStatutContrat || 'CLOTURE')}
                                            label={ct?.libelleStatutContrat}
                                            size="sm"
                                        />
                                    </div>

                                    <div className="flex items-center gap-3 text-xs text-ink-500 flex-wrap">
                                        <span className="flex items-center gap-1 font-mono">
                                            <Calendar size={12} className="text-ink-400" />
                                            {fmtDate(ct?.dateDebut || chapitre.dateDebut)} → {ct?.dateFin ? fmtDate(ct.dateFin) : (isContratActif ? 'En cours' : '-')}
                                        </span>
                                        {ct?.ancienneteContratTexte && (
                                            <span className="font-medium text-ink-700 dark:text-ink-200">
                                                (Durée : {ct.ancienneteContratTexte})
                                            </span>
                                        )}
                                        {ct?.libelleTypeIntervenant && (
                                            <span className="text-ink-400">
                                                · {ct.libelleTypeIntervenant}
                                            </span>
                                        )}
                                    </div>
                                </div>

                                {/* Actions liées au document légal du Contrat */}
                                <div className="flex items-center gap-2">
                                    {canGenerateAttestation && onOpenAttestation && (
                                        <Button
                                            onClick={() => onOpenAttestation(ct)}
                                            variant="outline"
                                            size="xs"
                                            iconLeft={<FileCheck size={13} />}
                                        >
                                            Attestation d'emploi
                                        </Button>
                                    )}
                                    {canGenerateCertificat && onOpenCertificat && (
                                        <Button
                                            onClick={() => onOpenCertificat(ct)}
                                            variant="outline"
                                            size="xs"
                                            iconLeft={<Award size={13} />}
                                        >
                                            Certificat de travail
                                        </Button>
                                    )}
                                </div>
                            </div>

                            {/* Corps du Chapitre : Événements & Affectations du Contrat */}
                            <div className="p-5">
                                {chapitre.workflows.length === 0 ? (
                                    <p className="text-xs text-ink-400 ">
                                        Aucun événement particulier n'a été enregistré pour cette période contractuelle.
                                    </p>
                                ) : (
                                    <div className="relative pl-6 space-y-5 before:content-[''] before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-ink-150 dark:before:bg-ink-800">
                                        {chapitre.workflows.map((wf) => {
                                            const isWfActif = Boolean(wf.isActif);
                                            const isWfBrouillon = Boolean(wf.isBrouillon);
                                            const isWfAnnule = Boolean(wf.isAnnule);
                                            const motifAnnulation = wf.motif || wf.commentaire;
                                            const evolutions = mapEvolutions.get(wf.idWorkflow) || [];

                                            return (
                                                <div key={wf.idWorkflow} className="relative group">
                                                    {/* Point connecteur sur la sous-frise */}
                                                    <div className={`
                                                        absolute -left-6 top-1 w-5 h-5 rounded-none flex items-center justify-center border
                                                        ${isWfActif
                                                            ? 'bg-success-500/10 border-success-500 text-success-600 dark:text-success-400 ring-2 ring-white dark:ring-ink-900'
                                                            : isWfBrouillon
                                                                ? 'bg-amber-500/10 border-amber-500 text-amber-600 ring-2 ring-white dark:ring-ink-900'
                                                                : isWfAnnule
                                                                    ? 'bg-danger-500/10 border-danger-500 text-danger-500 ring-2 ring-white dark:ring-ink-900'
                                                                    : 'bg-white dark:bg-ink-900 border-ink-300 dark:border-ink-600 text-ink-500 ring-2 ring-white dark:ring-ink-900'
                                                        }
                                                    `}>
                                                        {getIconForType(wf.codeTypeEvenement || wf.libelleTypeEvenement, isWfActif)}
                                                    </div>

                                                    {/* Bloc de l'événement */}
                                                    <div className={`
                                                        p-3.5 border transition-all duration-150
                                                        ${isWfActif
                                                            ? 'bg-white dark:bg-ink-900 border-ink-200 dark:border-ink-700'
                                                            : isWfBrouillon
                                                                ? 'bg-[var(--color-warning-500)]/5 dark:bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/30'
                                                                : isWfAnnule
                                                                    ? 'bg-ink-50/40 dark:bg-ink-950/40 border-ink-150 dark:border-ink-800 opacity-75'
                                                                    : 'bg-ink-50/40 dark:bg-ink-950/40 border-ink-150 dark:border-ink-800'
                                                        }
                                                    `}>
                                                        {/* Ligne 1 : Date + Titre de l'événement + Statut Workflow */}
                                                        <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-ink-100 dark:border-ink-800">
                                                            <div className="flex items-center gap-2">
                                                                <span className="font-mono text-xs font-bold text-ink-500 flex items-center gap-1">
                                                                    <Clock size={11} className="text-ink-400" />
                                                                    {fmtDate(wf.dateEffet || wf.dateDemande)}
                                                                </span>
                                                                <span className="text-xs font-bold text-ink-900 dark:text-white">
                                                                    {wf.libelleTypeEvenement || wf.codeTypeEvenement || 'Mouvement RH'}
                                                                </span>
                                                            </div>

                                                            <div className="flex items-center gap-2">
                                                                <StatusBadge status={wf.codeStatutWorkflow} label={wf.libelleStatutWorkflow} size="xs" />
                                                                {onSelectWorkflowRecap && (
                                                                    <Button
                                                                        onClick={() => onSelectWorkflowRecap(wf)}
                                                                        variant="ghost"
                                                                        size="xs"
                                                                        iconRight={<ArrowUpRight size={12} />}
                                                                    >
                                                                        Détails
                                                                    </Button>
                                                                )}
                                                            </div>
                                                        </div>

                                                        {/* Ligne 2 : Rubans d'Évolutions / Changements constatés */}
                                                        {evolutions.length > 0 && (
                                                            <div className="pt-2 pb-1 flex flex-wrap gap-1.5">
                                                                {evolutions.map((evo, eIdx) => (
                                                                    <span
                                                                        key={eIdx}
                                                                        className="inline-flex items-center gap-1.5 px-2 py-0.5 bg-brand-500/10 border border-brand-500/20 text-[11px] font-semibold text-brand-700 dark:text-brand-300"
                                                                    >
                                                                        {evo.icon}
                                                                        <span>{evo.label}</span>
                                                                    </span>
                                                                ))}
                                                            </div>
                                                        )}

                                                        {/* Ligne 3 : Informations Organisationnelles & Financières */}
                                                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2.5 text-xs pt-2">
                                                            {wf.libelleFonction && (
                                                                <div>
                                                                    <span className="text-[10px] uppercase tracking-wider font-semibold text-ink-400 block">
                                                                        Poste / Fonction
                                                                    </span>
                                                                    <p className="font-semibold text-ink-800 dark:text-ink-200">
                                                                        {wf.libelleFonction}
                                                                    </p>
                                                                </div>
                                                            )}

                                                            {(wf.libelleSite || wf.libelleDepartement) && (
                                                                <div>
                                                                    <span className="text-[10px] uppercase tracking-wider font-semibold text-ink-400 block flex items-center gap-1">
                                                                        <Building2 size={10} /> Implantation
                                                                    </span>
                                                                    <p className="text-ink-700 dark:text-ink-300">
                                                                        {wf.libelleSite || '-'}{wf.libelleDepartement ? ` · ${wf.libelleDepartement}` : ''}
                                                                    </p>
                                                                </div>
                                                            )}

                                                            {wf.salaireBase && (
                                                                <HasPermission authority="PAIE_READ">
                                                                    <div>
                                                                        <span className="text-[10px] uppercase tracking-wider font-semibold text-ink-400 block">
                                                                            Salaire de base
                                                                        </span>
                                                                        <p className="font-mono font-bold text-ink-900 dark:text-ink-100">
                                                                            {Number(wf.salaireBase).toLocaleString('fr-FR')} Ar
                                                                        </p>
                                                                    </div>
                                                                </HasPermission>
                                                            )}

                                                            {wf.nomPrenomResponsable && (
                                                                <div>
                                                                    <span className="text-[10px] uppercase tracking-wider font-semibold text-ink-400 block flex items-center gap-1">
                                                                        <UserCheck size={10} /> Responsable
                                                                    </span>
                                                                    <p className="text-ink-700 dark:text-ink-300">
                                                                        {wf.nomPrenomResponsable}
                                                                    </p>
                                                                </div>
                                                            )}
                                                        </div>

                                                        {/* Commentaire / Motif */}
                                                        {wf.motif && !isWfAnnule && (
                                                            <p className="text-[11px] text-ink-500  mt-2 pt-1 border-t border-ink-100 dark:border-ink-800">
                                                                "{wf.motif}"
                                                            </p>
                                                        )}

                                                        {isWfAnnule && motifAnnulation && (
                                                            <div className="mt-2 p-2 bg-danger-500/10 border border-danger-500/20 text-[11px] text-danger-600 dark:text-danger-400 flex items-center gap-1.5">
                                                                <AlertCircle size={12} className="shrink-0" />
                                                                <span>Motif d'annulation : {motifAnnulation}</span>
                                                            </div>
                                                        )}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
