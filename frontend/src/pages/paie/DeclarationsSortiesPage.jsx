import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    FileText, Download, ChevronRight, Building2,
    Receipt, CreditCard, BookOpen, ShieldCheck
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import SlideOver from '@/components/ui/overlays/SlideOver';
import { Card } from '@/components/ui/data-display/Card';
import WorkflowTimeline from '@/components/workflow/WorkflowTimeline';
import paieService from '@/api/paie/paieService';

/**
 * DeclarationsSortiesPage — Espace Déclarations & Sorties (Maquette V1 Full V3).
 *
 * Tabs :
 * - MENSUELLES (Bulletins, Journal / état nominatif, IRSA, ISI, Virement, Comptabilité)
 * - TRIMESTRIELLES (Mois sources M1+M2+M3 clôturés/validés, DNS CNaPS, FMFP, Santé)
 * - ARCHIVES
 *
 * Dossier déclaratif dans SlideOver :
 * - Readiness des mois sources
 * - WorkflowTimeline (Données consolidées -> Contrôles -> Document généré -> Transmission -> Paiement)
 * - ZÉRO recalcul de paie, ZÉRO ressaisie financière. Consommation directe des résultats scellés.
 */
export default function DeclarationsSortiesPage() {
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState('MENSUELLES'); // 'MENSUELLES' | 'TRIMESTRIELLES' | 'ARCHIVES'
    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriodId, setSelectedPeriodId] = useState(null);
    const [_loading, setLoading] = useState(false);
    const [selectedDeclaration, setSelectedDeclaration] = useState(null);
    const [feedback, setFeedback] = useState(null);

    const chargerPeriodes = useCallback(async () => {
        setLoading(true);
        try {
            const list = await paieService.listerPeriodes();
            setPeriodes(list || []);
            const active = list?.find(p => p.actif || p.state === 'EN_COURS') || list?.[0];
            if (active?.idPeriodePaie) {
                setSelectedPeriodId(active.idPeriodePaie);
            }
        } catch {
            setPeriodes([]);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        chargerPeriodes();
    }, [chargerPeriodes]);

    const activePeriod = useMemo(() => {
        return periodes.find(p => p.idPeriodePaie === selectedPeriodId) || periodes[0];
    }, [periodes, selectedPeriodId]);

    const codePeriode = activePeriod?.code || 'Période active';
    const isValidee = ['VALIDEE', 'PAIE_VALIDEE', 'CLOTUREE'].includes(activePeriod?.state);

    // Définition des dossiers trimestriels avec mois sources M1, M2, M3
    const trimestres = useMemo(() => {
        return [
            {
                code: 'T4 2025',
                libelle: 'Quatrième Trimestre 2025',
                periodeDates: '1er Octobre → 31 Décembre 2025',
                moisSources: [
                    { nom: 'Octobre 2025', statut: 'CLÔTURÉE', valide: true },
                    { nom: 'Novembre 2025', statut: 'CLÔTURÉE', valide: true },
                    { nom: 'Décembre 2025', statut: isValidee ? 'VALIDÉE' : 'EN COURS', valide: isValidee }
                ],
                organismes: [
                    {
                        code: 'CNAPS_DNS',
                        nom: 'DNS CNaPS',
                        organisme: 'Caisse Nationale de Prévoyance Sociale',
                        type: 'Cotisations de retraite et prestations familiales',
                        periodicite: 'Trimestrielle',
                        tauxGlobal: '14% (1% salarial + 13% patronal sous plafond)'
                    },
                    {
                        code: 'FMFP',
                        nom: 'FMFP',
                        organisme: 'Fonds Malgache de Formation Professionnelle',
                        type: 'Cotisation patronale pour la formation continue',
                        periodicite: 'Trimestrielle (échéance au 30 du mois suivant)',
                        tauxGlobal: '1% masse brute'
                    },
                    {
                        code: 'SANTE_OSTIE',
                        nom: 'Santé Interentreprises (OSTIE / AMIT / Funhece)',
                        organisme: 'Organisme Médical Interentreprises',
                        type: 'Couverture médicale et santé au travail',
                        periodicite: 'Trimestrielle',
                        tauxGlobal: '6% (1% salarial + 5% patronal)'
                    }
                ]
            }
        ];
    }, [isValidee]);

    // Timeline du dossier déclaratif trimestriel
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
                title: 'Télétransmission à l’organisme',
                date: 'En attente d’envoi',
                description: 'Dépôt du fichier numérique sur le portail de télédéclaration.',
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
                breadcrumbs={[{ label: 'Tableau de bord', href: '/dashboard' }, { label: 'Paie & Rémunération', href: '/paie' }, { label: 'Déclarations & Sorties' }]}
                actions={
                    <div className="flex items-center gap-1 bg-[var(--color-bg-subtle)] p-1 rounded-md border border-[var(--color-border-subtle)] text-xs">
                        <button
                            type="button"
                            onClick={() => setActiveTab('MENSUELLES')}
                            className={`px-3 py-1.5 rounded font-semibold transition-colors ${activeTab === 'MENSUELLES' ? 'bg-[var(--color-surface)] text-[var(--color-brand-600)] shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                        >
                            Mensuelles
                        </button>
                        <button
                            type="button"
                            onClick={() => setActiveTab('TRIMESTRIELLES')}
                            className={`px-3 py-1.5 rounded font-semibold transition-colors ${activeTab === 'TRIMESTRIELLES' ? 'bg-[var(--color-surface)] text-[var(--color-brand-600)] shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                        >
                            Trimestrielles
                        </button>
                        <button
                            type="button"
                            onClick={() => setActiveTab('ARCHIVES')}
                            className={`px-3 py-1.5 rounded font-semibold transition-colors ${activeTab === 'ARCHIVES' ? 'bg-[var(--color-surface)] text-[var(--color-brand-600)] shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                        >
                            Archives
                        </button>
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

                {/* ============================================================== */}
                {/* 1. ONGLET MENSUELLES                                           */}
                {/* ============================================================== */}
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
                            <Badge variant={isValidee ? 'success' : 'warning'} size="xs">
                                {isValidee ? 'Campagne validée' : 'Calcul en cours'}
                            </Badge>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
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

                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    <BookOpen size={16} className="text-emerald-600" />
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
                                    Ouvrir l’état nominatif
                                </Button>
                            </Card>

                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    <Receipt size={16} className="text-amber-600" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Bordereau IRSA (DGI)</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    État fiscal mensuel pour le versement de l'impôt sur les revenus.
                                </p>
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="w-full"
                                    disabled={!isValidee}
                                    onClick={() => setFeedback({ type: 'success', text: 'Bordereau mensuel IRSA généré.' })}
                                >
                                    Télécharger bordereau
                                </Button>
                            </Card>

                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    <Receipt size={16} className="text-orange-600" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Déclaration EDI-ISI</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Fichier électronique télétransmissible pour les entreprises assujetties.
                                </p>
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="w-full"
                                    disabled={!isValidee}
                                    onClick={() => setFeedback({ type: 'success', text: 'Fichier EDI-ISI généré.' })}
                                >
                                    Générer EDI-ISI
                                </Button>
                            </Card>

                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    <CreditCard size={16} className="text-blue-600" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Virement bancaire</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Fichier bancaire sécurisé d'ordre de virement des salaires nets.
                                </p>
                                <Button
                                    variant="primary"
                                    size="xs"
                                    className="w-full"
                                    disabled={!isValidee}
                                    onClick={() => navigate(selectedPeriodId ? `/paie/periodes/${selectedPeriodId}/sorties` : '/paie')}
                                >
                                    Télécharger ordre de virement
                                </Button>
                            </Card>

                            <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3">
                                <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                                    <Building2 size={16} className="text-purple-600" />
                                    <h3 className="text-xs font-bold text-[var(--color-fg)]">Écritures comptables</h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Export des écritures d'OD de paie vers le logiciel comptable.
                                </p>
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="w-full"
                                    disabled={!isValidee}
                                    onClick={() => setFeedback({ type: 'info', text: 'OD de paie exportée.' })}
                                >
                                    Exporter OD
                                </Button>
                            </Card>
                        </div>
                    </div>
                )}

                {/* ============================================================== */}
                {/* 2. ONGLET TRIMESTRIELLES (Consolidation M1+M2+M3)               */}
                {/* ============================================================== */}
                {activeTab === 'TRIMESTRIELLES' && (
                    <div className="space-y-6">
                        {trimestres.map((trim) => (
                            <div key={trim.code} className="space-y-4">
                                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 rounded-md space-y-3">
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

                                    {/* Mois sources M1, M2, M3 */}
                                    <div className="space-y-1.5">
                                        <span className="text-[11px] font-bold uppercase text-[var(--color-fg-muted)] tracking-wider block">
                                            Mois sources de consolidation
                                        </span>
                                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
                                            {trim.moisSources.map((m, idx) => (
                                                <div key={idx} className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded flex items-center justify-between">
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
                                        <Card key={org.code} className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
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
                                                <div className="text-[11px] text-[var(--color-fg-muted)] font-mono mt-2 p-1.5 bg-[var(--color-bg-subtle)] rounded">
                                                    Taux : {org.tauxGlobal}
                                                </div>
                                            </div>

                                            <div className="pt-2">
                                                <Button
                                                    variant="secondary"
                                                    size="xs"
                                                    className="w-full"
                                                    onClick={() => setSelectedDeclaration(org)}
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

                {/* ============================================================== */}
                {/* 3. ONGLET ARCHIVES                                             */}
                {/* ============================================================== */}
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
                            Accéder à l’historique des campagnes
                        </Button>
                    </Card>
                )}
            </div>

            {/* Drawer Dossier Déclaratif Trimestriel avec WorkflowTimeline */}
            <SlideOver
                isOpen={Boolean(selectedDeclaration)}
                onClose={() => setSelectedDeclaration(null)}
                title={selectedDeclaration ? `Dossier ${selectedDeclaration.nom} — T4 2025` : 'Dossier'}
                subtitle={selectedDeclaration ? selectedDeclaration.organisme : ''}
                width="lg"
            >
                {selectedDeclaration && (
                    <div className="space-y-6 text-xs">
                        {/* Readiness des mois sources */}
                        <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-md space-y-2">
                            <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
                                Contrôles préalables à la déclaration
                            </span>
                            <div className="space-y-1.5 text-xs">
                                <div className="flex items-center justify-between">
                                    <span className="text-[var(--color-fg)]">Octobre 2025 (Mois 1)</span>
                                    <span className="text-[var(--color-success-600)] font-bold">✓ Clôturé & Scellé</span>
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-[var(--color-fg)]">Novembre 2025 (Mois 2)</span>
                                    <span className="text-[var(--color-success-600)] font-bold">✓ Clôturé & Scellé</span>
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-[var(--color-fg)]">Décembre 2025 (Mois 3)</span>
                                    <span className={isValidee ? 'text-[var(--color-success-600)] font-bold' : 'text-[var(--color-warning-600)] font-bold'}>
                                        {isValidee ? '✓ Validé & Scellé' : '⚠ En cours'}
                                    </span>
                                </div>
                                <div className="flex items-center justify-between pt-1 border-t border-[var(--color-border-subtle)]">
                                    <span className="text-[var(--color-fg)]">Données sociales des collaborateurs</span>
                                    <span className="text-[var(--color-success-600)] font-bold">✓ Conformes</span>
                                </div>
                            </div>
                        </div>

                        {/* Règle stricte d'intégrité */}
                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-md space-y-1">
                            <span className="font-bold text-[var(--color-fg)] block">Consolidation scellée sans recalcul</span>
                            <p className="text-[11px] text-[var(--color-fg-muted)]">
                                Le bordereau trimestriel consomme exclusivement les résultats validés et scellés de chaque bulletin mensuel. Aucune formule de paie n’est réexécutée et aucune ressaisie financière n'est autorisée.
                            </p>
                        </div>

                        {/* WorkflowTimeline du dossier */}
                        <div className="space-y-2">
                            <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
                                Progression du dossier
                            </span>
                            <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-md">
                                <WorkflowTimeline steps={declarationTimelineSteps} />
                            </div>
                        </div>

                        {/* Action de téléchargement du bordereau certifié */}
                        <div className="pt-2">
                            <Button
                                variant="primary"
                                size="sm"
                                className="w-full"
                                onClick={() => setFeedback({ type: 'success', text: `Bordereau officiel ${selectedDeclaration.nom} T4 2025 généré.` })}
                                iconLeft={<Download size={14} />}
                            >
                                Générer le bordereau {selectedDeclaration.nom}
                            </Button>
                        </div>
                    </div>
                )}
            </SlideOver>
        </div>
    );
}
