import { Printer, FileDown, Clock } from 'lucide-react';
import { useEmployePointages } from '@/hooks/pointage/useEmployePointages';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import { getStatutPointageBadge, isPointageRowGrayed } from '@/utils/pointageUtils';

export default function PointageTab({ idEmploye }) {
    const {
        selectedDate,
        setSelectedDate,
        viewPeriod,
        setViewPeriod,
        pointagesData,
        kpis,
        recap,
        reglesAppliquees,
        loading,
        error
    } = useEmployePointages(idEmploye);

    if (loading) return <SkeletonTable rows={6} cols={9} />;

    return (
        <div className="space-y-6">
            {error && <Alert type="danger" message={error} />}

            {/* Barre d'outils & Navigation temporelle */}
            <div className="bg-[var(--color-surface)] p-4 border border-[var(--color-border-subtle)] flex flex-col md:flex-row items-center justify-between gap-4 shadow-sm">
                <div className="flex items-center gap-3 w-full md:w-auto">
                    <PeriodNavigator
                        viewPeriod={viewPeriod}
                        selectedDate={selectedDate}
                        onDateChange={setSelectedDate}
                    />

                    <div className="flex bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-0.5 shrink-0">
                        <button
                            onClick={() => setViewPeriod('jour')}
                            className={`px-3 py-1 text-xs font-semibold uppercase tracking-wider transition-colors ${viewPeriod === 'jour' ? 'bg-[var(--color-brand-500)] text-white shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                        >
                            Jour
                        </button>
                        <button
                            onClick={() => setViewPeriod('semaine')}
                            className={`px-3 py-1 text-xs font-semibold uppercase tracking-wider transition-colors ${viewPeriod === 'semaine' ? 'bg-[var(--color-brand-500)] text-white shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                        >
                            Semaine
                        </button>
                        <button
                            onClick={() => setViewPeriod('mois')}
                            className={`px-3 py-1 text-xs font-semibold uppercase tracking-wider transition-colors ${viewPeriod === 'mois' ? 'bg-[var(--color-brand-500)] text-white shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                        >
                            Mois
                        </button>
                    </div>
                </div>

                <div className="flex items-center gap-2 w-full md:w-auto justify-end">
                    <Button variant="outline" size="sm" icon={<Printer size={15} />} className="shrink-0 whitespace-nowrap">
                        Imprimer
                    </Button>
                    <Button variant="primary" size="sm" icon={<FileDown size={15} />} className="shrink-0 whitespace-nowrap">
                        Exporter PDF
                    </Button>
                </div>
            </div>

            {/* 4 Cartes d'Indicateurs Clés RH (KPI) */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {/* KPI 1 : Heures réalisées */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-sm">
                    <div className="flex items-center justify-between">
                        <p className="text-xs font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider">Heures réalisées</p>
                        <Clock size={16} className="text-[var(--color-brand-500)]" />
                    </div>
                    <div className="mt-3 flex items-baseline gap-2">
                        <span className="text-2xl font-extrabold text-[var(--color-fg)]">{kpis.heuresRealisees}</span>
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1">{kpis.heuresObjectif}</p>
                </div>

                {/* KPI 2 : Retard cumulé */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-sm">
                    <div className="flex items-center justify-between">
                        <p className="text-xs font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider">Retard cumulé</p>
                        <span className="w-2 h-2 rounded-full bg-[var(--color-warning-500)] inline-block"></span>
                    </div>
                    <div className="mt-3 flex items-baseline gap-2">
                        <span className="text-2xl font-extrabold text-[var(--color-warning-600)]">{kpis.retardCumule}</span>
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1">{kpis.retardOccurrences}</p>
                </div>

                {/* KPI 3 : Heures supplémentaires */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-sm">
                    <div className="flex items-center justify-between">
                        <p className="text-xs font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider">Heures supplémentaires</p>
                        <span className="w-2 h-2 rounded-full bg-[var(--color-success-500)] inline-block"></span>
                    </div>
                    <div className="mt-3 flex items-baseline gap-2">
                        <span className="text-2xl font-extrabold text-[var(--color-success-600)]">{kpis.heuresSupp}</span>
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1">{kpis.heuresSuppStatut}</p>
                </div>

                {/* KPI 4 : Absences & Congés */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-sm">
                    <div className="flex items-center justify-between">
                        <p className="text-xs font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider">Absences & Congés</p>
                        <span className="w-2 h-2 rounded-full bg-[var(--color-brand-500)] inline-block"></span>
                    </div>
                    <div className="mt-3 flex items-baseline gap-2">
                        <span className="text-2xl font-extrabold text-[var(--color-fg)]">{kpis.absences}</span>
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1">{kpis.absencesStatut}</p>
                </div>
            </div>

            {/* Grille principale : Détail journalier (3 colonnes) + Panneau latéral (1 colonne) */}
            <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
                
                {/* Section Gauche : Détail journalier (Largeur 3 cols) */}
                <div className="lg:col-span-3 space-y-4">
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm">
                        <div className="p-4 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
                            <h3 className="text-base font-bold text-[var(--color-fg)]">Détail journalier</h3>
                            <span className="text-xs text-[var(--color-fg-muted)] font-medium">Pointages et calculs automatiques</span>
                        </div>

                        {pointagesData.length === 0 ? (
                            <EmptyState
                                icon={Clock}
                                title="Aucun pointage pour cette période"
                                description="Les enregistrements de pointage apparaîtront au fur et à mesure des arrivées et sorties."
                            />
                        ) : (
                            <Table>
                                <Thead>
                                    <Tr>
                                        <Th className="text-xs font-bold uppercase">Date</Th>
                                        <Th className="text-xs font-bold uppercase">Jour</Th>
                                        <Th className="text-xs font-bold uppercase">Entrée</Th>
                                        <Th className="text-xs font-bold uppercase">Sortie Pause</Th>
                                        <Th className="text-xs font-bold uppercase">Retour</Th>
                                        <Th className="text-xs font-bold uppercase">Sortie</Th>
                                        <Th className="text-xs font-bold uppercase">Heures</Th>
                                        <Th className="text-xs font-bold uppercase">Écart</Th>
                                        <Th className="text-xs font-bold uppercase">Statut</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {pointagesData.map((row) => {
                                        const isConge = Boolean(row.isConge ?? (row.statut === 'CONGE'));
                                        const rowClassName = isConge
                                            ? '!bg-orange-100 dark:!bg-orange-900/30 border-l-4 !border-l-orange-500 opacity-100'
                                            : isPointageRowGrayed(row.statut)
                                            ? 'bg-[var(--color-bg-subtle)]/70 opacity-80 border-l-4 border-l-transparent'
                                            : 'hover:bg-[var(--color-bg-subtle)]/30 border-l-4 border-l-transparent transition-colors';

                                        return (
                                        <Tr 
                                            key={row.idPointage || row.dateComplete} 
                                            className={rowClassName}
                                        >
                                            <Td className="font-mono text-xs font-bold text-[var(--color-fg)]">{row.date}</Td>
                                            <Td className="text-xs text-[var(--color-fg-muted)]">{row.jour}</Td>
                                            <Td className="font-mono text-xs font-bold text-[var(--color-fg)]">{row.entree}</Td>
                                            <Td className="font-mono text-xs text-[var(--color-fg-muted)]">{row.sortiePause}</Td>
                                            <Td className="font-mono text-xs text-[var(--color-fg-muted)]">{row.retourPause}</Td>
                                            <Td className="font-mono text-xs font-bold text-[var(--color-fg)]">{row.sortie}</Td>
                                            <Td className="font-mono text-xs font-bold text-[var(--color-fg)]">{row.heures}</Td>
                                            <Td className={`font-mono text-xs font-semibold ${
                                                row.ecart === '—' ? 'text-[var(--color-fg-muted)]' :
                                                row.ecartPositif ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'
                                            }`}>
                                                {row.ecart}
                                            </Td>
                                            <Td>{getStatutPointageBadge(row.statut)}</Td>
                                        </Tr>
                                        );
                                    })}
                                </Tbody>
                            </Table>
                        )}
                    </div>
                </div>

                {/* Section Droite : Récapitulatif, Règles & Observations (1 col) */}
                <div className="lg:col-span-1 space-y-6">
                    
                    {/* Carte 1 : Récapitulatif */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-sm space-y-4">
                        <h4 className="text-sm font-bold text-[var(--color-fg)] uppercase tracking-wider border-b border-[var(--color-border-subtle)] pb-2">
                            Récapitulatif
                        </h4>
                        <div className="space-y-3 text-xs">
                            <div className="flex justify-between items-center">
                                <span className="text-[var(--color-fg-muted)] font-medium">Temps théorique</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{recap.tempsTheorique}</span>
                            </div>
                            <div className="flex justify-between items-center border-t border-[var(--color-border-subtle)] pt-2">
                                <span className="text-[var(--color-fg-muted)] font-medium">Temps réalisé</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{recap.tempsRealise}</span>
                            </div>
                            <div className="flex justify-between items-center border-t border-[var(--color-border-subtle)] pt-2">
                                <span className="text-[var(--color-fg-muted)] font-medium">Temps d'absence</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{recap.tempsAbsence}</span>
                            </div>
                            <div className="flex justify-between items-center border-t border-[var(--color-border-subtle)] pt-2">
                                <span className="text-[var(--color-fg-muted)] font-medium">Heures supplémentaires</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{recap.heuresSupp}</span>
                            </div>
                            <div className="flex justify-between items-center border-t border-[var(--color-border-subtle)] pt-2">
                                <span className="text-[var(--color-fg-muted)] font-medium">Retards</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{recap.retards}</span>
                            </div>
                        </div>
                    </div>

                    {/* Carte 2 : Règles appliquées */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-sm space-y-4">
                        <h4 className="text-sm font-bold text-[var(--color-fg)] uppercase tracking-wider border-b border-[var(--color-border-subtle)] pb-2">
                            Règles appliquées
                        </h4>
                        <div className="space-y-3 text-xs">
                            <div className="flex justify-between items-center">
                                <span className="text-[var(--color-fg-muted)] font-medium">Horaire journalier</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{reglesAppliquees.horaireJournalier || '08h00'}</span>
                            </div>
                            <div className="flex justify-between items-center border-t border-[var(--color-border-subtle)] pt-2">
                                <span className="text-[var(--color-fg-muted)] font-medium">Pause déjeuner</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{reglesAppliquees.pause || '01h00'}</span>
                            </div>
                            <div className="flex justify-between items-center border-t border-[var(--color-border-subtle)] pt-2">
                                <span className="text-[var(--color-fg-muted)] font-medium">Prise de poste théorique</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{reglesAppliquees.debutNormal || '08:00'}</span>
                            </div>
                            <div className="flex justify-between items-center border-t border-[var(--color-border-subtle)] pt-2">
                                <span className="text-[var(--color-fg-muted)] font-medium">Heure minimale d'arrivée</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{reglesAppliquees.plafondArrivee || '07:30'}</span>
                            </div>
                            <div className="flex justify-between items-center border-t border-[var(--color-border-subtle)] pt-2">
                                <span className="text-[var(--color-fg-muted)] font-medium">Tolérance de retard</span>
                                <span className="font-mono font-bold text-[var(--color-fg)]">{reglesAppliquees.toleranceRetard || '0 min'}</span>
                            </div>
                        </div>
                    </div>

                    {/* Carte 3 : Observations & Notice RH */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-sm space-y-2">
                        <h4 className="text-sm font-bold text-[var(--color-fg)] uppercase tracking-wider">
                            Observations
                        </h4>
                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                            Les heures sont calculées à partir des pointages enregistrés. Toute correction doit être justifiée et validée par le responsable habilité.
                        </p>
                    </div>

                </div>

            </div>
        </div>
    );
}
