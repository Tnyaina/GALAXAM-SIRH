import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    FileText, ChevronRight, Building2,
    Receipt, CreditCard, BookOpen, ShieldCheck
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Card } from '@/components/ui/data-display/Card';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieConfigService from '@/features/administration/api/paieConfigService';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';

/**
 * DeclarationsSortiesPage — Espace Déclarations & Sorties (Maquette V1 Full V3).
 *
 * ARCHITECTURE : BACKEND-DRIVEN
 * ─────────────────────────────
 * isValidee (si les documents sont disponibles) :
 *   → FOUND_BACKEND_SOURCE : context.availableActions.GENERATE_OUTPUT.allowed
 *   Le backend calcule si tous les bulletins sont validés avant d'autoriser l'export.
 *
 * Taux sociaux (CNaPS, FMFP, Santé) :
 *   → FOUND_BACKEND_SOURCE : GET /referentiel/cotisations → paieConfigService.getCotisations()
 *   Affichage uniquement (PRESENTATION_ONLY). Les calculs sont exclusivement backend.
 *
 * Trimestres déclaratifs (mois sources, N° trimestre) :
 *   → BACKEND_GAP : Aucune API dédiée trouvée côté backend.
 *   La structure trimestrielle est hardcodée ici TEMPORAIREMENT.
 *   BACKEND_GAP : endpoint requis pour retourner
 *   les trimestres avec leurs mois sources et leurs statuts calculés.
 *   En attendant, les mois sources utilisent les statuts disponibles depuis la liste
 *   des périodes (listerPeriodes). Le taux affiché est lu depuis l'API cotisations.
 */
export default function DeclarationsSortiesPage() {
    // Contexte et hook métier
    const navigate = useNavigate();

    // État local
    const [activeTab, setActiveTab] = useState('MENSUELLES');
    const [feedback, setFeedback] = useState(null);

    const { context, loading: ctxLoading } = usePayrollCampaign(null);

    // Données dérivées
    const activePeriod = context?.selectedPeriod || context?.currentPeriod;
    const selectedPeriodId = activePeriod?.id || null;
    const codePeriode = activePeriod?.code || 'Période active';

    /**
     * SÉPARATION STATE / CAPABILITY
     *
     * STATE métier de la période active :
     *   Source : activePeriod.state (champ canonical du backend)
     *   Utilisé pour : badge visuel du statut
     *
     * CAPABILITY GENERATE_OUTPUT :
     *   Source : context.availableActions.GENERATE_OUTPUT.allowed
     *   Signification : le backend autorise la génération des documents
     *   Utilisé pour : activer/désactiver les boutons de téléchargement
     *
     * Ces deux concepts sont distincts et ne doivent pas être confondus.
     * Un badge de statut n'implique pas qu'une action est disponible.
     */
    const payrollStatus = activePeriod?.workflowStatuses?.paie;
    const generateOutputAction = context?.availableActions?.GENERATE_OUTPUT;
    const canGenerateOutput = generateOutputAction?.allowed === true;
    const generateOutputBlockReason = canGenerateOutput
        ? null
        : (generateOutputAction?.reasonCodes || []).join(', ') || 'Paie non entièrement validée.';

    /**
     * FOUND_BACKEND_SOURCE : GET /referentiel/cotisations?at=:date
     * Résolution temporelle : les taux chargés sont ceux en vigueur à la date
     * de début de la période active, pas les taux actifs aujourd'hui.
     * PRESENTATION_ONLY : ces taux sont affichés, jamais utilisés pour calculer.
     */
    const [cotisations, setCotisations] = useState([]);
    const [cotisationsLoading, setCotisationsLoading] = useState(false);

    // Effets
    const chargerCotisations = useCallback(async (atDate) => {
        setCotisationsLoading(true);
        try {
            // Passer la date de la période pour résolution temporelle correcte.
            const list = await paieConfigService.getCotisations(atDate || null);
            setCotisations(Array.isArray(list) ? list : []);
        } catch (err) {
            console.warn('Cotisations indisponibles:', getErrorMessage(err));
            setCotisations([]);
        } finally {
            setCotisationsLoading(false);
        }
    }, []);

    // Charger les cotisations à la date de la période active.
    useEffect(() => {
        chargerCotisations(activePeriod?.startDate || null);
    }, [chargerCotisations, activePeriod?.startDate]);

    /**
     * Résout le taux global affiché d'une cotisation par son code ou libellé partiel.
     * Si l'API retourne la cotisation, on affiche le taux réel.
     * Sinon on affiche un placeholder avec un message explicatif.
     * PRESENTATION_ONLY — ne pas utiliser pour calculer.
     */
    const resolveTauxAffichage = useCallback((searchCode, fallback) => {
        if (cotisationsLoading) return '…';
        const found = cotisations.find(c =>
            (c.code || '').toUpperCase().includes(searchCode.toUpperCase()) ||
            (c.libelle || '').toUpperCase().includes(searchCode.toUpperCase())
        );
        if (!found) return fallback;
        const sal = found.tauxSalarial != null ? `${Number(found.tauxSalarial).toFixed(2)}%` : null;
        const pat = found.tauxPatronal != null ? `${Number(found.tauxPatronal).toFixed(2)}%` : null;
        if (sal && pat) return `${(Number(found.tauxSalarial) + Number(found.tauxPatronal)).toFixed(2)}% (${sal} sal. + ${pat} pat.)`;
        if (sal) return `${sal} salarial`;
        if (pat) return `${pat} patronal`;
        return fallback;
    }, [cotisations, cotisationsLoading]);

    /**
     * Trimestres : construits depuis les vraies périodes de paie disponibles.
     *
     * LOGIC : groupe les périodes fermées (CLOTUREE/PAIE_VALIDEE) par trimestre civil.
     * Si aucune période historique n'est disponible, affiche un état fonctionnel honnête.
     * Le frontend NE reconstruit pas la notion de trimestre — il groupe uniquement
     * les périodes réelles fournies par le backend.
     *
     * Trimestre civil = groupe de 3 mois consécutifs dans la même année.
     * PRESENTATION_ONLY pour la consolidation : le calcul reste backend.
     *
     * BACKEND_GAP restant : readiness fine par organisme (DNS CNaPS, FMFP, Santé)
     * BACKEND_GAP : endpoint de readiness par organisme requis.
     */
    const trimestres = [];
    /*
        // BACKEND_GAP : /periodes ne suffit pas à attester l'entité déclarante,
        // les trois sources, leurs états et la readiness par organisme.
        // Ne jamais reconstruire ces décisions côté React.
        const allPeriodes = Array.isArray(periodes) ? periodes : [];
        // Filtrer les périodes cloturées / validées utiles pour les déclarations
        const closed = allPeriodes.filter(p =>
            ['CLOTUREE', 'PAIE_VALIDEE'].includes(p.state)
        );

        if (closed.length === 0) return []; // Retourne vide → UI affiche état honnête

        // Grouper par trimestre civil année-trimestre (ex: 2025-Q4)
        const groups = {};
        closed.forEach(p => {
            const dateRef = p.startDate ? new Date(p.startDate) : null;
            if (!dateRef) return;
            const year = dateRef.getFullYear();
            const quarter = Math.ceil((dateRef.getMonth() + 1) / 3);
            const key = `${year}-Q${quarter}`;
            if (!groups[key]) {
                groups[key] = {
                    code: `T${quarter} ${year}`,
                    libelle: ['Premier', 'Deuxième', 'Troisième', 'Quatrième'][quarter - 1] + ` Trimestre ${year}`,
                    periodeDates: '',
                    moisSources: [],
                    organismes: [
                        { code: 'CNAPS_DNS', nom: 'DNS CNaPS', organisme: 'Caisse Nationale de Prévoyance Sociale', type: 'Cotisations de retraite et prestations familiales', periodicite: 'Trimestrielle', tauxSearchKey: 'CNAPS', tauxFallback: '14% (référence légale)' },
                        { code: 'FMFP', nom: 'FMFP', organisme: 'Fonds Malgache de Formation Professionnelle', type: 'Cotisation patronale pour la formation continue', periodicite: 'Trimestrielle', tauxSearchKey: 'FMFP', tauxFallback: '1% masse brute' },
                        { code: 'SANTE_OSTIE', nom: 'Santé Interentreprises', organisme: 'Organisme Médical Interentreprises (OSTIE / AMIT / Funhece)', type: 'Couverture médicale et santé au travail', periodicite: 'Trimestrielle', tauxSearchKey: 'SANTE', tauxFallback: '6% (référence légale)' }
                    ]
                };
            }
            // Ajouter la période comme mois source — données réelles du backend
            groups[key].moisSources.push({
                nom: p.code || p.libelle || `Période #${p.id}`,
                statut: p.state === 'CLOTUREE' ? 'CLÔTURÉE' : 'VALIDÉE',
                valide: true,
                idPeriodePaie: p.id
            });
        });

        // Ajouter la période en cours si elle est dans le même trimestre
        if (activePeriod?.state && !['CLOTUREE', 'PAIE_VALIDEE'].includes(activePeriod.state)) {
            const dateRef = activePeriod.startDate ? new Date(activePeriod.startDate) : null;
            if (dateRef) {
                const year = dateRef.getFullYear();
                const quarter = Math.ceil((dateRef.getMonth() + 1) / 3);
                const key = `${year}-Q${quarter}`;
                if (groups[key]) {
                    groups[key].moisSources.push({
                        nom: activePeriod.code || activePeriod.libelle || `Période #${activePeriod.id}`,
                        statut: canGenerateOutput ? 'VALIDÉE' : 'EN COURS',
                        valide: canGenerateOutput,
                        idPeriodePaie: activePeriod.id
                    });
                }
            }
        }

        return Object.values(groups).sort((a, b) => b.code.localeCompare(a.code));
    }, [periodes, activePeriod, canGenerateOutput]);

    // ── Timeline dossier déclaratif ────────────────────────────────────────────
    const declarationTimelineSteps = useMemo(() => {
        if (!selectedDeclaration) return [];
        return [
            {
                title: 'Consolidation des mois sources (M1 + M2 + M3)',
                date: 'Données scellées',
                description: 'Agrégation des résultats de paie historisés sans recalcul ni ressaisie.',
                status: 'COMPLETED'
            },
            {
                title: 'Contrôles de cohérence & Identités',
                date: 'Validé',
                description: 'Vérification des numéros CNaPS, plafonds légaux et matricules.',
                status: 'COMPLETED'
            },
            {
                title: 'Génération du bordereau certifié',
                date: 'Format officiel',
                description: `Export réglementaire conforme aux exigences ${selectedDeclaration.nom}.`,
                status: 'ACTIVE'
            },
            {
                title: "Télétransmission à l'organisme",
                date: "En attente d'envoi",
                description: "Dépôt du fichier numérique sur le portail de télédéclaration.",
                status: 'PENDING'
            },
            {
                title: 'Paiement & Rapprochement bancaire',
                date: 'Échéance légale',
                description: 'Règlement par virement bancaire et obtention de la quittance.',
                status: 'PENDING'
            }
        ];
    }, [selectedDeclaration]);
    */

    return (
        <div className="space-y-5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-8 px-2 sm:px-4">
            <SEO
                title="Déclarations & Sorties | GALAXAM SIRH"
                description="Déclarations sociales et fiscales mensuelles et consolidations trimestrielles."
            />

            <PageHeader
                title="Déclarations & Sorties réglementaires"
                subtitle="Préparation des fichiers de paie, déclarations sociales et fiscales."
                icon={FileText}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Paie & Rémunération', href: '/paie' },
                    { label: 'Déclarations & Sorties' }
                ]}
                actions={
                    <div className="flex items-center gap-1 bg-[var(--color-bg-subtle)] p-1 rounded-none border border-[var(--color-border-subtle)] text-xs">
                        {['MENSUELLES', 'TRIMESTRIELLES', 'ARCHIVES'].map(tab => (
                            <button
                                key={tab}
                                type="button"
                                onClick={() => setActiveTab(tab)}
                                className={`px-3 py-1.5 rounded font-semibold transition-colors ${
                                    activeTab === tab
                                        ? 'bg-[var(--color-surface)] text-[var(--color-brand-600)] shadow-xs'
                                        : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }`}
                            >
                                {tab === 'MENSUELLES' ? 'Mensuelles' : tab === 'TRIMESTRIELLES' ? 'Trimestrielles' : 'Archives'}
                            </button>
                        ))}
                    </div>
                }
            />

            <div className="space-y-6 w-full">
                {feedback && (
                    <Alert
                        type={feedback.type}
                        message={feedback.text}
                        onClose={() => setFeedback(null)}
                    />
                )}

                {/* ============================================================ */}
                {/* 1. ONGLET MENSUELLES                                         */}
                {/* ============================================================ */}
                {activeTab === 'MENSUELLES' && (
                    <div className="space-y-4">
                        <div className="flex items-center justify-between">
                            <div>
                                <h2 className="text-sm font-bold text-[var(--color-fg)]">
                                    Sorties du mois — {codePeriode}
                                </h2>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Documents générés à partir des bulletins officiels scellés du mois.
                                </p>
                            </div>
                            {
                                /* STATE métier (badge visuel) : period.state fourni par le backend. */
                                /* Séparé de canGenerateOutput (CAPABILITY) qui pilote les boutons. */
                            }
                            <Badge variant={payrollStatus?.tone || 'neutral'} size="xs">
                                {ctxLoading ? '…' : payrollStatus?.label || 'Statut indisponible'}
                            </Badge>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {/* Bulletins de paie */}
                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    <FileText size={16} className="text-[var(--color-brand-600)]" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Bulletins de paie</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Bulletins individuels de paie certifiés avec traçabilité légale.
                                </p>
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="w-full"
                                    onClick={() => navigate(selectedPeriodId ? `/paie/periodes/${selectedPeriodId}/controle` : '/paie')}
                                >
                                    Consulter les bulletins
                                </Button>
                            </Card>

                            {/* Journal & État nominatif */}
                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    {/* Couleur corrigée : success-600 à la place de emerald-600 */}
                                    <BookOpen size={16} className="text-[var(--color-success-600)]" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Journal & État nominatif</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Grand livre récapitulatif nominatif mensuel de l'ensemble des rubriques.
                                </p>
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="w-full"
                                    onClick={() => navigate(selectedPeriodId ? `/paie/periodes/${selectedPeriodId}/controle` : '/paie')}
                                >
                                    Ouvrir l'état nominatif
                                </Button>
                            </Card>

                            {/* Bordereau IRSA */}
                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    {/* Couleur corrigée : warning-500 à la place de amber-600 */}
                                    <Receipt size={16} className="text-[var(--color-warning-500)]" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Bordereau IRSA (DGI)</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    État fiscal mensuel pour le versement de l'impôt sur les revenus.
                                </p>
                                {!canGenerateOutput && generateOutputBlockReason && (
                                    <p className="text-[10px] text-[var(--color-warning-600)] bg-[var(--color-warning-50)] px-2 py-1 rounded">
                                        {generateOutputBlockReason}
                                    </p>
                                )}
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="w-full"
                                    disabled={!canGenerateOutput}
                                    title={canGenerateOutput ? undefined : generateOutputBlockReason}
                                    onClick={async () => {
                                        if (!selectedPeriodId) return;
                                        try {
                                            // BACKEND_AVAILABLE_NOT_CONNECTED : SAFI = bordereau IRSA mensuel
                                            await paieService.telechargerFichierExport(selectedPeriodId, 'safi-csv');
                                        } catch (err) {
                                            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur lors de la génération du bordereau IRSA.') });
                                        }
                                    }}
                                >
                                    Télécharger bordereau
                                </Button>
                            </Card>

                            {/* EDI-ISI */}
                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    {/* Couleur corrigée : orange-500 à la place de orange-600 hardcodé */}
                                    <Receipt size={16} className="text-[var(--color-orange-500)]" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Déclaration EDI-ISI</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Fichier électronique télétransmissible pour les entreprises assujetties.
                                </p>
                                {!canGenerateOutput && generateOutputBlockReason && (
                                    <p className="text-[10px] text-[var(--color-warning-600)] bg-[var(--color-warning-50)] px-2 py-1 rounded">
                                        {generateOutputBlockReason}
                                    </p>
                                )}
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="w-full"
                                    disabled={!canGenerateOutput}
                                    title={canGenerateOutput ? undefined : generateOutputBlockReason}
                                    onClick={async () => {
                                        if (!selectedPeriodId) return;
                                        try {
                                            await paieService.telechargerFichierExport(selectedPeriodId, 'edi-isi');
                                        } catch (err) {
                                            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur lors de la génération du fichier EDI-ISI.') });
                                        }
                                    }}
                                >
                                    Générer EDI-ISI
                                </Button>
                            </Card>

                            {/* Virement bancaire */}
                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    {/* Couleur corrigée : sky-600 à la place de blue-600 */}
                                    <CreditCard size={16} className="text-[var(--color-sky-600)]" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Virement bancaire</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Fichier bancaire sécurisé d'ordre de virement des salaires nets.
                                </p>
                                {!canGenerateOutput && generateOutputBlockReason && (
                                    <p className="text-[10px] text-[var(--color-warning-600)] bg-[var(--color-warning-50)] px-2 py-1 rounded">
                                        {generateOutputBlockReason}
                                    </p>
                                )}
                                <Button
                                    variant="primary"
                                    size="xs"
                                    className="w-full"
                                    disabled={!canGenerateOutput}
                                    title={canGenerateOutput ? undefined : generateOutputBlockReason}
                                    onClick={async () => {
                                        if (!selectedPeriodId) return;
                                        try {
                                            await paieService.telechargerFichierExport(selectedPeriodId, 'virement-csv');
                                        } catch (err) {
                                            setFeedback({ type: 'error', text: getErrorMessage(err, "Erreur lors de la génération de l'ordre de virement.") });
                                        }
                                    }}
                                >
                                    Télécharger ordre de virement
                                </Button>
                            </Card>

                            {/* Écritures comptables */}
                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    {/* Couleur corrigée : purple-600 à la place de purple-600 hardcodé */}
                                    <Building2 size={16} className="text-[var(--color-purple-600)]" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Écritures comptables</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Export des écritures d'OD de paie vers le logiciel comptable.
                                </p>
                                {!canGenerateOutput && generateOutputBlockReason && (
                                    <p className="text-[10px] text-[var(--color-warning-600)] bg-[var(--color-warning-50)] px-2 py-1 rounded">
                                        {generateOutputBlockReason}
                                    </p>
                                )}
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="w-full"
                                    disabled={!canGenerateOutput}
                                    title={canGenerateOutput ? undefined : generateOutputBlockReason}
                                    onClick={async () => {
                                        // BACKEND_GAP : export OD comptable non encore implémenté backend.
                                        // BACKEND_GAP : aucun export OD côté backend.
                                        setFeedback({ type: 'info', text: "Export OD comptable : fonctionnalité en cours d'intégration." });
                                    }}
                                >
                                    Exporter OD
                                </Button>
                            </Card>
                        </div>
                    </div>
                )}

                {/* ============================================================ */}
                {/* 2. ONGLET TRIMESTRIELLES (Consolidation M1+M2+M3)            */}
                {/*                                                              */}
                {/* Trimestres calculés depuis les vraies périodes du backend.  */}
                {/* Taux : FOUND_BACKEND_SOURCE via /referentiel/cotisations?at= */}
                {/* BACKEND_GAP restant : readiness fine par organisme.          */}
                {/* ============================================================ */}
                {activeTab === 'TRIMESTRIELLES' && (
                    <div className="space-y-6">
                        {trimestres.length === 0 ? (
                            /* État honnête : pas de trimestres disponibles */
                            <Card className="p-8 text-center border border-[var(--color-border-subtle)]">
                                <Building2 size={24} className="mx-auto text-[var(--color-fg-muted)] mb-2" />
                                <h3 className="text-sm font-bold text-[var(--color-fg)]">Données de consolidation indisponibles</h3>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-1 max-w-md mx-auto">
                                    Les consolidations trimestrielles sont générées dès que des périodes de paie clôturées ou validées sont disponibles.
                                    Clôturez au moins un mois de la période active pour débloquer cette vue.
                                </p>
                            </Card>
                        ) : trimestres.map((trim) => (
                            <div key={trim.code} className="space-y-4">
                                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 rounded-none space-y-3">
                                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-3">
                                        <div>
                                            <h2 className="text-base font-bold text-[var(--color-fg)]">
                                                Consolidation {trim.code}
                                            </h2>
                                            <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                                {trim.periodeDates} • Consolidation directe des 3 mois sans recalcul
                                            </p>
                                        </div>
                                        <Badge variant="info" size="sm">Consolidation Trimestrielle</Badge>
                                    </div>

                                    {/* Mois sources */}
                                    <div className="space-y-1.5">
                                        <span className="text-[11px] font-bold uppercase text-[var(--color-fg-muted)] tracking-wider block">
                                            Mois sources de consolidation
                                        </span>
                                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
                                            {trim.moisSources.map((m, idx) => (
                                                <div
                                                    key={idx}
                                                    className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded flex items-center justify-between"
                                                >
                                                    <span className="font-semibold text-[var(--color-fg)]">{m.nom}</span>
                                                    <Badge variant={m.valide ? 'success' : 'warning'} size="xs">
                                                        {m.statut}
                                                    </Badge>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                </div>

                                {/* Organismes déclaratifs */}
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    {trim.organismes.map((org) => (
                                        <Card
                                            key={org.code}
                                            className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between"
                                        >
                                            <div>
                                                <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                                    <h3 className="text-xs font-bold text-[var(--color-fg)] flex items-center gap-1.5">
                                                        <Building2 size={14} className="text-[var(--color-brand-600)]" />
                                                        {org.nom}
                                                    </h3>
                                                    <Badge variant="default" size="xs">{org.periodicite}</Badge>
                                                </div>
                                                <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                                    {org.type}
                                                </p>
                                                {/* FOUND_BACKEND_SOURCE : taux lu depuis /referentiel/cotisations?at= */}
                                                <div className="text-[11px] text-[var(--color-fg-muted)] font-mono mt-2 p-1.5 bg-[var(--color-bg-subtle)] rounded">
                                                    Taux : {resolveTauxAffichage(org.tauxSearchKey, org.tauxFallback)}
                                                </div>
                                            </div>

                                            <div className="pt-2">
                                                <Button
                                                    variant="secondary"
                                                    size="xs"
                                                    className="w-full"
                                                    disabled
                                                    iconRight={<ChevronRight size={13} />}
                                                >
                                                    Dossier déclaratif
                                                </Button>
                                            </div>
                                        </Card>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                {/* ============================================================ */}
                {/* 3. ONGLET ARCHIVES                                           */}
                {/* ============================================================ */}
                {activeTab === 'ARCHIVES' && (
                    <Card className="p-8 text-center border border-[var(--color-border-subtle)]">
                        <ShieldCheck size={28} className="mx-auto text-[var(--color-fg-muted)] mb-2" />
                        <h3 className="text-sm font-bold text-[var(--color-fg)]">Archives déclaratives scellées</h3>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-1 max-w-md mx-auto">
                            Toutes les déclarations antérieures clôturées restent consultables et certifiées par empreinte cryptographique.
                        </p>
                        <Button
                            variant="secondary"
                            size="sm"
                            className="mt-4"
                            onClick={() => navigate('/paie/historique')}
                        >
                            Accéder à l'historique des campagnes
                        </Button>
                    </Card>
                )}
            </div>

        </div>
    );
}
