import { useState, useEffect, useCallback, useMemo } from 'react';
import { Search, AlertCircle, ChevronRight, Banknote } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import SlideOver from '@/components/ui/overlays/SlideOver';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import WorkflowTimeline from '@/components/workflow/WorkflowTimeline';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';

/**
 * AcomptesAvancesPage — Espace Acomptes & Avances (Maquette V1 Full V3).
 *
 * Distinct de Variables.
 * - 4 KPIs : À approuver, À verser, À imputer, Encours
 * - Table : Salarié, Type, Demandé, Approuvé, Versé, Imputé, Solde, Période cible, Statut
 * - Drawer détail avec WorkflowTimeline
 * - Règle stricte : Le reliquat d'une avance partiellement imputée n'est PAS
 *   automatiquement reporté sur N+1. Indication "Prochaine imputation NON PLANIFIÉE"
 *   avec action Trésorerie explicite [Imputer le reliquat].
 */
export default function AcomptesAvancesPage() {
    // État local
    const [loading, setLoading] = useState(false);
    const [dossiers, setDossiers] = useState([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [selectedDossier, setSelectedDossier] = useState(null);
    const [feedback, setFeedback] = useState(null);

    // Effets
    const chargerDossiers = useCallback(async () => {
        setLoading(true);
        try {
            // Récupérer les périodes récentes et les ajustements de type ACOMPTE / AVANCE
            const periodes = await paieService.listerPeriodes();
            const activePeriod = periodes?.find(p => p.actif || p.state === 'EN_COURS') || periodes?.[0];
            if (activePeriod?.idPeriodePaie) {
                const res = await paieService.listerAjustements(activePeriod.idPeriodePaie);
                const items = Array.isArray(res) ? res : (res?.content || []);
                // Filtrer sur acomptes / avances
                const filtered = items.filter(i =>
                    (i.typeMouvement && (i.typeMouvement.includes('ACOMPTE') || i.typeMouvement.includes('AVANCE') || i.typeMouvement.includes('PRET'))) ||
                    (i.libelle && (i.libelle.toLowerCase().includes('acompte') || i.libelle.toLowerCase().includes('avance') || i.libelle.toLowerCase().includes('prêt')))
                );
                setDossiers(filtered.length > 0 ? filtered : items);
            }
        } catch {
            setDossiers([]);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        chargerDossiers();
    }, [chargerDossiers]);

    // Données dérivées
    const kpis = useMemo(() => {
        let aApprouver = 0;
        let aVerser = 0;
        let aImputer = 0;
        let totalEncours = 0;

        dossiers.forEach(d => {
            const solde = Number(d.soldeRestant ?? d.montant) || 0;
            totalEncours += solde;

            if (d.statut === 'DEMANDE' || d.statut === 'A_APPROUVER') aApprouver++;
            else if (d.statut === 'APPROUVE' || d.statut === 'A_VERSER') aVerser++;
            else if (d.statut === 'VERSE' || d.statut === 'EN_COURS') aImputer++;
        });

        return { aApprouver, aVerser, aImputer, totalEncours };
    }, [dossiers]);

    // Filtrage
    const filteredDossiers = useMemo(() => {
        return dossiers.filter(d => {
            const matchesSearch = !searchQuery.trim() ||
                (d.nomEmploye || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
                (d.matricule || '').toLowerCase().includes(searchQuery.toLowerCase());

            const matchesStatus = statusFilter === 'ALL' || d.statut === statusFilter;
            return matchesSearch && matchesStatus;
        });
    }, [dossiers, searchQuery, statusFilter]);

    // Timeline steps pour le dossier sélectionné
    const timelineSteps = useMemo(() => {
        if (!selectedDossier) return [];
        const isPartiel = (Number(selectedDossier.soldeRestant) || 0) > 0 && Number(selectedDossier.montantImpute || 0) > 0;

        return [
            {
                title: 'Demande formulée',
                date: selectedDossier.dateDemande || selectedDossier.createdAt || 'Début de période',
                description: `Montant demandé : ${formatMoneyAr(selectedDossier.montant)}`,
                status: 'COMPLETED'
            },
            {
                title: 'Approuvée par la DAF',
                date: selectedDossier.dateApprobation || 'Validée',
                description: 'Conformité financière et quotité disponible vérifiées.',
                status: selectedDossier.statut === 'DEMANDE' ? 'ACTIVE' : 'COMPLETED'
            },
            {
                title: 'Versée par la Trésorerie',
                date: selectedDossier.datePaiement || 'Virement',
                description: 'Fonds mis à disposition du collaborateur.',
                status: ['DEMANDE', 'APPROUVE'].includes(selectedDossier.statut) ? 'PENDING' : 'COMPLETED'
            },
            {
                title: `Imputation sur paie ${selectedDossier.codePeriode || 'en cours'}`,
                date: selectedDossier.codePeriode || 'Mensuel',
                description: selectedDossier.montantImpute
                    ? `Imputé : ${formatMoneyAr(selectedDossier.montantImpute)}`
                    : 'Prélèvement prévu sur le bulletin',
                status: selectedDossier.statut === 'SOLDE' ? 'COMPLETED' : 'ACTIVE'
            },
            {
                title: isPartiel ? 'Prochaine imputation — NON PLANIFIÉE' : 'Clôture du dossier',
                date: isPartiel ? 'Action DAF requise' : 'Terminé',
                description: isPartiel
                    ? `Reliquat restant de ${formatMoneyAr(selectedDossier.soldeRestant)}. Aucun report automatique sur N+1.`
                    : 'Montant intégralement recouvré.',
                status: isPartiel ? 'WARNING' : (selectedDossier.statut === 'SOLDE' ? 'COMPLETED' : 'PENDING')
            }
        ];
    }, [selectedDossier]);

    // Rendu
    return (
        <div className="space-y-5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-8 px-2 sm:px-4">
            <SEO
                title="Acomptes & Avances | GALAXAM SIRH"
                description="Gestion des dossiers d'acomptes sur salaire, avances et remboursements."
            />

            <PageHeader
                title="Acomptes & Avances sur salaire"
                subtitle="Suivi des demandes, versements et imputations sur les campagnes de paie."
                icon={Banknote}
                breadcrumbs={[{ label: 'Tableau de bord', href: '/dashboard' }, { label: 'Paie & Rémunération', href: '/paie' }, { label: 'Acomptes & Avances' }]}
            />

            <div className="space-y-5 w-full">
                {feedback && (
                    <Alert
                        type={feedback.type}
                        message={feedback.text}
                        onClose={() => setFeedback(null)}
                    />
                )}

                {/* 4 KPIs Clés */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 rounded-none">
                        <span className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            À approuver
                        </span>
                        <span className="text-xl font-black font-mono text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] mt-1 block">
                            {kpis.aApprouver}
                        </span>
                    </div>

                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 rounded-none">
                        <span className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            À verser
                        </span>
                        <span className="text-xl font-black font-mono text-[var(--color-info-600)] mt-1 block">
                            {kpis.aVerser}
                        </span>
                    </div>

                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 rounded-none">
                        <span className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            À imputer
                        </span>
                        <span className="text-xl font-black font-mono text-[var(--color-brand-600)] mt-1 block">
                            {kpis.aImputer}
                        </span>
                    </div>

                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 rounded-none">
                        <span className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            Encours total
                        </span>
                        <span className="text-base font-black font-mono text-[var(--color-fg)] mt-1 block">
                            {formatMoneyAr(kpis.totalEncours)}
                        </span>
                    </div>
                </div>

                {/* Filtres & Recherche */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-2.5 rounded-none">
                    <div className="w-full sm:w-72">
                        <Input
                            size="xs"
                            placeholder="Rechercher par nom ou matricule..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            leftIcon={<Search size={13} />}
                        />
                    </div>

                    <div className="flex items-center gap-1.5 text-xs">
                        <button
                            type="button"
                            onClick={() => setStatusFilter('ALL')}
                            className={`px-2.5 py-1 rounded transition-colors ${statusFilter === 'ALL' ? 'bg-[var(--color-brand-600)] text-white font-bold' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'}`}
                        >
                            Tous
                        </button>
                        <button
                            type="button"
                            onClick={() => setStatusFilter('DEMANDE')}
                            className={`px-2.5 py-1 rounded transition-colors ${statusFilter === 'DEMANDE' ? 'bg-[var(--color-warning-500)] text-white font-bold' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'}`}
                        >
                            À approuver
                        </button>
                        <button
                            type="button"
                            onClick={() => setStatusFilter('SOLDE')}
                            className={`px-2.5 py-1 rounded transition-colors ${statusFilter === 'SOLDE' ? 'bg-[var(--color-brand-600)] text-white font-bold' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'}`}
                        >
                            Soldés
                        </button>
                    </div>
                </div>

                {/* Table des dossiers */}
                {loading ? (
                    <div className="p-8 text-center text-xs text-[var(--color-fg-muted)]">Chargement des dossiers...</div>
                ) : filteredDossiers.length === 0 ? (
                    <div className="p-8 text-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none text-xs text-[var(--color-fg-muted)]">
                        Aucun dossier d'acompte ou d'avance trouvé.
                    </div>
                ) : (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none overflow-hidden">
                        <div className="overflow-x-auto">
                            <Table className="w-full text-xs">
                                <Thead>
                                    <Tr>
                                        <Th>Salarié</Th>
                                        <Th>Type</Th>
                                        <Th className="text-right">Demandé</Th>
                                        <Th className="text-right">Approuvé</Th>
                                        <Th className="text-right">Versé</Th>
                                        <Th className="text-right">Imputé</Th>
                                        <Th className="text-right font-bold">Solde</Th>
                                        <Th className="text-center">Statut</Th>
                                        <Th className="text-center w-10"></Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {filteredDossiers.map((d, i) => {
                                        const typeLabel = d.typeMouvement?.includes('ACOMPTE') ? 'Acompte' : (d.typeMouvement?.includes('PRET') ? 'Prêt' : 'Avance');
                                        const solde = Number(d.soldeRestant ?? (Number(d.montant) - Number(d.montantImpute || 0)));

                                        return (
                                            <Tr
                                                key={d.idAjustement || i}
                                                onClick={() => setSelectedDossier(d)}
                                                className="hover:bg-[var(--color-bg-subtle)] cursor-pointer transition-colors"
                                            >
                                                <Td className="font-medium text-[var(--color-fg)]">
                                                    <div>{d.nomEmploye || `Employé #${d.idEmploye}`}</div>
                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-mono">{d.matricule || '—'}</div>
                                                </Td>
                                                <Td>
                                                    <Badge variant={typeLabel === 'Acompte' ? 'default' : 'info'} size="xs">
                                                        {typeLabel}
                                                    </Badge>
                                                </Td>
                                                <Td className="text-right font-mono">{formatMoneyAr(d.montant)}</Td>
                                                <Td className="text-right font-mono">{formatMoneyAr(d.montantApprouve ?? d.montant)}</Td>
                                                <Td className="text-right font-mono">{formatMoneyAr(d.montantVerse ?? d.montant)}</Td>
                                                <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                    {d.montantImpute ? `-${formatMoneyAr(d.montantImpute)}` : '0 Ar'}
                                                </Td>
                                                <Td className="text-right font-mono font-bold text-[var(--color-fg)]">
                                                    {formatMoneyAr(solde)}
                                                </Td>
                                                <Td className="text-center">
                                                    <Badge
                                                        variant={solde === 0 ? 'success' : (d.statut === 'DEMANDE' ? 'warning' : 'primary')}
                                                        size="xs"
                                                    >
                                                        {solde === 0 ? 'Soldé' : (d.statut || 'En cours')}
                                                    </Badge>
                                                </Td>
                                                <Td className="text-center text-[var(--color-fg-muted)]">
                                                    <ChevronRight size={14} />
                                                </Td>
                                            </Tr>
                                        );
                                    })}
                                </Tbody>
                            </Table>
                        </div>
                    </div>
                )}
            </div>

            {/* Drawer Dossier avec WorkflowTimeline */}
            <SlideOver
                isOpen={Boolean(selectedDossier)}
                onClose={() => setSelectedDossier(null)}
                title={selectedDossier ? `Dossier — ${selectedDossier.nomEmploye || 'Collaborateur'}` : 'Détail du dossier'}
                subtitle={selectedDossier ? `Matricule : ${selectedDossier.matricule || '—'} | Dossier #${selectedDossier.idAjustement || '1'}` : ''}
                width="lg"
            >
                {selectedDossier && (
                    <div className="space-y-6 text-xs">
                        {/* Synthèse financière */}
                        <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-2">
                            <div className="flex justify-between">
                                <span className="text-[var(--color-fg-muted)]">Montant demandé :</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{formatMoneyAr(selectedDossier.montant)}</span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-[var(--color-fg-muted)]">Montant déjà imputé :</span>
                                <span className="font-mono font-bold text-[var(--color-danger-600)]">
                                    -{formatMoneyAr(selectedDossier.montantImpute || 0)}
                                </span>
                            </div>
                            <div className="flex justify-between pt-1 border-t border-[var(--color-border-subtle)]">
                                <span className="font-bold text-[var(--color-fg)]">Solde restant à recouvrer :</span>
                                <span className="font-mono font-black text-sm text-[var(--color-brand-600)]">
                                    {formatMoneyAr(selectedDossier.soldeRestant ?? selectedDossier.montant)}
                                </span>
                            </div>
                        </div>

                        {/* Règle explicite d'avance partielle */}
                        {(Number(selectedDossier.soldeRestant) || 0) > 0 && (
                            <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 rounded text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] space-y-2">
                                <div className="font-bold flex items-center gap-1.5">
                                    <AlertCircle size={14} />
                                    <span>Avance partiellement imputée</span>
                                </div>
                                <p className="text-[11px]">
                                    Le reliquat restant n'est <strong>PAS</strong> automatiquement reporté ou planifié sur la période suivante. Une action explicite de trésorerie est requise pour programmer la prochaine échéance.
                                </p>
                                <Button
                                    variant="primary"
                                    size="xs"
                                    onClick={() => setFeedback({ type: 'info', text: 'Demande de planification transmise à la Trésorerie.' })}
                                >
                                    Imputer le reliquat
                                </Button>
                            </div>
                        )}

                        {/* Timeline d'avancement (WorkflowTimeline) */}
                        <div className="space-y-2">
                            <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
                                Parcours du dossier
                            </span>
                            <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none">
                                <WorkflowTimeline steps={timelineSteps} />
                            </div>
                        </div>
                    </div>
                )}
            </SlideOver>
        </div>
    );
}
