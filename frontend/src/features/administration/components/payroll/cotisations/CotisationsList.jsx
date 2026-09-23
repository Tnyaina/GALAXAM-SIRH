import { Plus, Edit2, ShieldCheck, Building2, MapPin, Info, Power, Check } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/dateUtils';
import EmptyState from '@/components/ui/data-display/EmptyState';

const PORTEE_BADGE = {
    ENTREPRISE: {
        label: 'Entreprise entière',
        icon: Building2,
        cls: 'text-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/25',
    },
    SITE: {
        label: 'Par site',
        icon: MapPin,
        cls: 'text-[var(--color-success-600)] bg-[var(--color-success-500)]/10 border border-[var(--color-success-500)]/25',
    },
};

export default function CotisationsList({
    cotisations = [],
    onOpenCreate,
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <ShieldCheck size={18} className="text-[var(--color-brand-500)]" />
                        Règles &amp; Cotisations Sociales ({cotisations.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Taux salariaux et patronaux applicables — CNaPS, Santé, FMFP et autres organismes.
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouvelle Cotisation
                </Button>
            </div>

            {/* Bandeau explication périmètre */}
            <div className="mx-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 flex items-start gap-3">
                <Info size={15} className="text-[var(--color-fg-muted)] shrink-0 mt-0.5" />
                <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                    Le champ <strong className="text-[var(--color-fg)]">Périmètre</strong> indique si la cotisation s'applique à
                    {' '}<span className="font-semibold text-[var(--color-brand-500)]">toute l'entreprise</span>{' '}
                    (ex : FMFP, ISI — sans affectation par site) ou
                    {' '}<span className="font-semibold text-[var(--color-success-600)]">par site géographique</span>{' '}
                    (ex : CNaPS, OSTIE, OMSI — à associer dans l'onglet <em>Cotisations par Site</em>).
                </p>
            </div>

            {cotisations.length === 0 ? (
                <div className="p-8">
                    <EmptyState
                        icon={ShieldCheck}
                        title="Aucune cotisation configurée"
                        description="Veuillez ajouter les règles et cotisations sociales pour la paie."
                        actionLabel="Nouvelle Cotisation"
                        onAction={onOpenCreate}
                    />
                </div>
            ) : (
                <Table>
                    <Thead>
                        <Th>Code</Th>
                        <Th>Libellé</Th>
                        <Th>Périmètre</Th>
                        <Th className="text-right">Taux Salarial</Th>
                        <Th className="text-right">Taux Patronal</Th>
                        <Th className="text-right">Taux Total</Th>
                        <Th>Période d'Application</Th>
                        <Th>Statut</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {cotisations.map((c) => {
                            const portee = c.portee || 'SITE';
                            const pm = PORTEE_BADGE[portee] || PORTEE_BADGE.SITE;
                            const Icon = pm.icon;
                            const isActif = c.estActif !== false;
                            return (
                                <Tr key={c.idTypeCotisation}>
                                    <Td isData className="font-bold text-[var(--color-brand-500)]">
                                        {c.code}
                                    </Td>
                                    <Td className="font-semibold text-[var(--color-fg)]">
                                        {c.libelle}
                                    </Td>
                                    <Td>
                                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${pm.cls}`}>
                                            <Icon size={10} />
                                            {pm.label}
                                        </span>
                                    </Td>
                                    <Td isData className="text-right font-bold text-sky-600 dark:text-sky-400">
                                        {c.tauxSalarial}%
                                    </Td>
                                    <Td isData className="text-right font-bold text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]">
                                        {c.tauxPatronal}%
                                    </Td>
                                    <Td isData className="text-right font-bold text-[var(--color-brand-500)]">
                                        {(Number(c.tauxSalarial || 0) + Number(c.tauxPatronal || 0)).toFixed(2)}%
                                    </Td>
                                    <Td isData muted className="text-xs">
                                        Du {fmtDate(c.dateDebut)} {c.dateFin ? `au ${fmtDate(c.dateFin)}` : '(Toujours actif)'}
                                    </Td>
                                    <Td>
                                        <Badge
                                            variant={isActif ? 'success' : 'default'}
                                            size="sm"
                                            dot
                                        >
                                            {isActif ? 'Actif' : 'Désactivé'}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right">
                                        <div className="flex items-center justify-end gap-1">
                                            <button
                                                onClick={() => onOpenEdit(c)}
                                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                                title="Modifier"
                                            >
                                                <Edit2 size={16} />
                                            </button>
                                            {onToggleStatus && (
                                                isActif ? (
                                                    <button
                                                        onClick={() => onToggleStatus(c)}
                                                        className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer"
                                                        title="Désactiver la cotisation et ses rattachements par site"
                                                    >
                                                        <Power size={16} />
                                                    </button>
                                                ) : (
                                                    <button
                                                        onClick={() => onToggleStatus(c)}
                                                        className="p-1.5 text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10 rounded-none cursor-pointer"
                                                        title="Réactiver la cotisation"
                                                    >
                                                        <Check size={16} />
                                                    </button>
                                                )
                                            )}
                                        </div>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}