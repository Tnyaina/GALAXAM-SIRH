import { useState, memo, lazy, Suspense } from 'react';
import { Link } from 'react-router-dom';
import {
    Users, FileText, Clock, AlertTriangle, CheckCheck,
    TrendingUp, RefreshCw, ChevronRight, Calendar,
    UserCheck, Briefcase, RotateCcw
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useDashboard } from '@/hooks/useDashboard';
import { useToast } from '@/components/ui/feedback/Toast'; // <-- Import de ton hook personnalisé
import StatCard from '@/components/ui/data-display/StatCard';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { SkeletonStatCard, Skeleton } from '@/components/ui/feedback/Skeleton';
import Loading from '@/components/ui/feedback/Loading';

// Lazy loading du graphique optionnel
const LineTrendChart = lazy(() => import('@/components/ui/data-display/Chart').then(m => ({ default: m.LineTrendChart })));

// =========================================================================
// Utilitaires de formatage
// =========================================================================

function fmtDate(d) {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

function fmtRelative(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    const diffH = Math.floor((Date.now() - d) / 3600000);
    if (diffH < 1) return 'il y a quelques min';
    if (diffH < 24) return `il y a ${diffH}h`;
    return `il y a ${Math.floor(diffH / 24)}j`;
}

function getEcheanceBadgeVariant(joursRestants) {
    if (joursRestants === 0) return 'danger';
    if (joursRestants <= 7) return 'warning';
    return 'primary';
}

function getBrouillonBadgeVariant(joursAttente) {
    return joursAttente >= 2 ? 'warning' : 'primary';
}

// =========================================================================
// Composants atomiques & Skeletons
// =========================================================================

function SkeletonListRows({ count = 3 }) {
    return (
        <div className="divide-y divide-[var(--color-border-subtle)] py-1">
            {Array.from({ length: count }).map((_, i) => (
                <div key={i} className="flex items-center gap-3 py-3 px-1">
                    <Skeleton className="w-2 h-2 rounded-full shrink-0" />
                    <div className="flex-1 space-y-1 min-w-0">
                        <Skeleton className="h-3.5 w-1/3" />
                        <Skeleton className="h-2.5 w-1/2" />
                    </div>
                    <Skeleton className="h-5 w-14 shrink-0" />
                </div>
            ))}
        </div>
    );
}

function EmptyState({ msg }) {
    return (
        <div className="py-6 text-center">
            <CheckCheck size={20} className="text-[var(--color-success-500)] mx-auto mb-2" aria-hidden="true" />
            <p className="text-sm text-[var(--color-fg-muted)]">{msg}</p>
        </div>
    );
}

function ExpandableList({ items, renderItem, initialCount = 5 }) {
    const [expanded, setExpanded] = useState(false);
    const visible = expanded ? items : items.slice(0, initialCount);
    const hidden = items.length - visible.length;

    return (
        <div className="divide-y divide-[var(--color-border-subtle)]">
            {visible.map(renderItem)}
            {hidden > 0 && (
                <button
                    onClick={() => setExpanded(true)}
                    className="w-full mt-2 py-2.5 min-h-11 text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] transition-colors rounded-none"
                >
                    Voir {hidden} de plus
                </button>
            )}
        </div>
    );
}

const AlerteRow = memo(function AlerteRow({ alerte }) {
    const isToday = alerte.joursRestants === 0;
    const isUrgent = alerte.joursRestants <= 7;
    return (
        <Link
            to={`/employes/${alerte.idEmploye}?tab=carriere`}
            className="flex items-center gap-3 py-3 px-1 hover:bg-[var(--color-bg-subtle)] transition-colors group"
        >
            <div className={`w-2 h-2 rounded-full shrink-0 ${isToday ? 'bg-[var(--color-danger-500)] animate-pulse' : isUrgent ? 'bg-[var(--color-warning-500)]' : 'bg-[var(--color-brand-500)]'}`} />
            <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-[var(--color-fg)] truncate">
                    {alerte.nomPrenom}
                </p>
                <p className="text-xs text-[var(--color-fg-muted)] truncate">
                    {alerte.typeContrat}
                    {alerte.numeroContrat ? ` · N°${alerte.numeroContrat}` : ''}
                    {' · '}<span className="font-data">{fmtDate(alerte.dateEcheance)}</span>
                </p>
            </div>
            <Badge variant={getEcheanceBadgeVariant(alerte.joursRestants)} size="sm">
                {isToday ? 'Auj.' : `J-${alerte.joursRestants}`}
            </Badge>
            <ChevronRight size={13} className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-500)] transition-colors shrink-0" aria-hidden="true" />
        </Link>
    );
});

const BrouillonRow = memo(function BrouillonRow({ b }) {
    return (
        <Link
            to={`/employes/${b.idEmploye}?tab=carriere&wf=${b.idWorkflow}`}
            className="flex items-center gap-3 py-3 px-1 hover:bg-[var(--color-bg-subtle)] transition-colors group"
        >
            <div className={`w-2 h-2 rounded-full shrink-0 ${b.joursAttente >= 2 ? 'bg-[var(--color-warning-500)]' : 'bg-[var(--color-brand-500)]'}`} />
            <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-[var(--color-fg)] truncate">{b.nomPrenom}</p>
                <p className="text-xs text-[var(--color-fg-muted)] truncate">{b.typeEvenement} · Effet : <span className="font-data">{fmtDate(b.dateEffet)}</span></p>
            </div>
            <Badge variant={getBrouillonBadgeVariant(b.joursAttente)} size="sm">
                {b.joursAttente}j d'attente
            </Badge>
            <ChevronRight size={13} className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-500)] transition-colors shrink-0" aria-hidden="true" />
        </Link>
    );
});

const ActiviteRow = memo(function ActiviteRow({ ev }) {
    const isValide = ev.statut === 'Valide et applique par la DAF' || ev.statut?.toLowerCase().includes('valid');
    return (
        <Link
            to={`/employes/${ev.idEmploye}?tab=carriere`}
            className="flex items-center gap-3 py-3 px-1 hover:bg-[var(--color-bg-subtle)] transition-colors group"
        >
            <div className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 text-white text-[10px] font-bold ${isValide ? 'bg-[var(--color-success-500)]' : 'bg-[var(--color-fg-subtle)]'}`}>
                {ev.nomPrenom?.charAt(0) || '?'}
            </div>
            <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-[var(--color-fg)] truncate">{ev.nomPrenom}</p>
                <p className="text-xs text-[var(--color-fg-muted)] truncate">{ev.typeEvenement}</p>
            </div>
            <div className="text-right shrink-0">
                <Badge variant={isValide ? 'success' : 'default'} size="sm">
                    {isValide ? 'Validé' : 'Annulé'}
                </Badge>
                <p className="text-[10px] text-[var(--color-fg-subtle)] mt-0.5 font-data">{fmtRelative(ev.updatedAt)}</p>
            </div>
        </Link>
    );
});

// =========================================================================
// Dashboard — Composant principal
// =========================================================================

export default function Dashboard() {
    const { user } = useAuth();
    const { data, loading, error, refresh, lastRefresh } = useDashboard();
    const toast = useToast(); // <-- Appel de ton hook de Toast

    const kpis = data?.kpis;
    const alertesCritiques = data?.alertesCritiques || [];
    const alertes7j = data?.alertes7j || [];
    const alertes30j = data?.alertes30j || [];
    const brouillons = data?.brouillons || [];
    const activite = data?.activiteRecente || [];

    const totalAlertes = alertesCritiques.length + alertes7j.length;

    const now = new Date();
    const greeting =
        now.getHours() < 12 ? 'Bonjour' :
            now.getHours() < 18 ? 'Bon après-midi' : 'Bonsoir';

    const handleRefresh = async () => {
        await refresh();
        // Utilisation conforme de ton composant toast({ title, message, type })
        toast({
            title: 'Actualisation réussie',
            message: 'Les données du dashboard sont à jour.',
            type: 'success',
            duration: 3000
        });
    };

    return (
        <div className="space-y-6">

            {/* Bannière de bienvenue */}
            <div className="bg-[var(--color-brand-800)] text-white p-6 flex items-center justify-between gap-4 shadow-md rounded-none">
                <div>
                    <p className="text-[var(--color-brand-200)] text-sm font-medium mb-1 uppercase tracking-wider font-data">
                        {now.toLocaleDateString('fr-FR', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                    </p>
                    <h2 className="text-2xl font-extrabold tracking-tight mb-1">
                        {greeting}, {user?.identifiant || 'utilisateur'}
                    </h2>
                    {loading ? (
                        <Skeleton className="h-4 w-64 bg-white/20 mt-1" />
                    ) : (
                        <p className="text-[var(--color-brand-100)] text-sm">
                            {totalAlertes > 0
                                ? `${totalAlertes} alerte(s) RH nécessitent votre attention.`
                                : 'Aucune alerte critique aujourd\'hui. Tout est à jour.'}
                        </p>
                    )}
                </div>
                <div className="flex flex-col items-end gap-2">
                    <Button
                        onClick={handleRefresh}
                        loading={loading}
                        variant="ghost"
                        size="sm"
                        className="bg-white/15 hover:bg-white/25 text-white border-transparent min-h-11"
                        icon={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
                    >
                        Actualiser
                    </Button>
                    {lastRefresh && (
                        <p className="text-[10px] text-[var(--color-brand-200)] font-data">
                            Dernière MAJ : {lastRefresh.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                        </p>
                    )}
                </div>
            </div>

            {/* Message d'erreur avec bouton de réessai explicite (Actionnable) */}
            {error && (
                <div role="alert" className="bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 px-5 py-4 text-sm text-[var(--color-danger-500)] flex items-center justify-between gap-4 rounded-none">
                    <div className="flex items-center gap-2">
                        <AlertTriangle size={16} className="shrink-0" aria-hidden="true" />
                        <span>{typeof error === 'string' ? error : 'Impossible de charger les données du dashboard.'}</span>
                    </div>
                    <Button
                        onClick={refresh}
                        variant="ghost"
                        size="sm"
                        loading={loading}
                        className="text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 shrink-0 min-h-11"
                        icon={<RotateCcw size={14} />}
                    >
                        Réessayer
                    </Button>
                </div>
            )}

            {/* KPIs */}
            <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-4">
                {loading ? (
                    Array.from({ length: 6 }).map((_, i) => <SkeletonStatCard key={i} />)
                ) : (
                    <>
                        <StatCard
                            title="Effectif actif"
                            value={kpis?.totalEffectifActif ?? '—'}
                            icon={<Users size={18} />}
                            color="brand"
                        />
                        <StatCard
                            title="Comptabilisé"
                            value={kpis?.effectifComptabilise ?? '—'}
                            icon={<Briefcase size={18} />}
                            color="brand"
                        />
                        <StatCard
                            title="Embauches"
                            value={kpis?.nouvellesEmbauchesMois ?? '—'}
                            icon={<TrendingUp size={18} />}
                            color="success"
                        />
                        <StatCard
                            title="Expirations 30j"
                            value={kpis?.contratsExpirant30j ?? '—'}
                            icon={<Calendar size={18} />}
                            color={kpis?.contratsExpirant30j > 0 ? 'warning' : 'brand'}
                        />
                        <StatCard
                            title="Essais actifs"
                            value={kpis?.essaisEnCours ?? '—'}
                            icon={<UserCheck size={18} />}
                            color="info"
                        />
                        <StatCard
                            title="Brouillons DAF"
                            value={kpis?.brouillonsEnAttente ?? '—'}
                            icon={<FileText size={18} />}
                            color={kpis?.brouillonsEnAttente > 0 ? 'warning' : 'brand'}
                        />
                    </>
                )}
            </div>

            {/* Ligne principale */}
            <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">

                {/* ALERTES CRITIQUES + 7j + 30j */}
                <div className="xl:col-span-2 space-y-5">

                    {/* Critiques */}
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between w-full">
                                <div className="flex items-center gap-2">
                                    <AlertTriangle size={16} className="text-[var(--color-danger-500)]" />
                                    <CardTitle>Alertes critiques — Aujourd'hui</CardTitle>
                                </div>
                                <Badge variant={alertesCritiques.length > 0 ? 'danger' : 'default'} size="sm" dot>
                                    {loading ? '…' : alertesCritiques.length}
                                </Badge>
                            </div>
                        </CardHeader>
                        <CardBody>
                            {loading && <SkeletonListRows count={2} />}
                            {!loading && alertesCritiques.length === 0 && (
                                <EmptyState msg="Aucun contrat ou essai n'expire aujourd'hui." />
                            )}
                            {!loading && alertesCritiques.length > 0 && (
                                <ExpandableList
                                    items={alertesCritiques}
                                    initialCount={5}
                                    renderItem={(a, i) => <AlerteRow key={i} alerte={a} />}
                                />
                            )}
                        </CardBody>
                    </Card>

                    {/* 7 jours */}
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between w-full">
                                <div className="flex items-center gap-2">
                                    <Clock size={16} className="text-[var(--color-warning-500)]" />
                                    <CardTitle>Échéances dans les 7 jours</CardTitle>
                                </div>
                                <Badge variant={alertes7j.length > 0 ? 'warning' : 'default'} size="sm" dot>
                                    {loading ? '…' : alertes7j.length}
                                </Badge>
                            </div>
                        </CardHeader>
                        <CardBody>
                            {loading && <SkeletonListRows count={2} />}
                            {!loading && alertes7j.length === 0 && (
                                <EmptyState msg="Aucune échéance dans les 7 prochains jours." />
                            )}
                            {!loading && alertes7j.length > 0 && (
                                <ExpandableList
                                    items={alertes7j}
                                    initialCount={5}
                                    renderItem={(a, i) => <AlerteRow key={i} alerte={a} />}
                                />
                            )}
                        </CardBody>
                    </Card>

                    {/* 30 jours */}
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between w-full">
                                <div className="flex items-center gap-2">
                                    <Calendar size={16} className="text-[var(--color-brand-500)]" />
                                    <CardTitle>Échéances dans les 30 jours</CardTitle>
                                </div>
                                <Badge variant="primary" size="sm">
                                    {loading ? '…' : alertes30j.length}
                                </Badge>
                            </div>
                        </CardHeader>
                        <CardBody>
                            {loading && <SkeletonListRows count={2} />}
                            {!loading && alertes30j.length === 0 && (
                                <EmptyState msg="Aucune échéance dans les 30 prochains jours." />
                            )}
                            {!loading && alertes30j.length > 0 && (
                                <ExpandableList
                                    items={alertes30j}
                                    initialCount={5}
                                    renderItem={(a, i) => <AlerteRow key={i} alerte={a} />}
                                />
                            )}
                        </CardBody>
                    </Card>

                    {/* Graphique de tendance optionnel */}
                    {data?.tendances && (
                        <Card>
                            <CardHeader>
                                <CardTitle>Évolution mensuelle des mouvements RH</CardTitle>
                            </CardHeader>
                            <CardBody>
                                <Suspense fallback={<Loading display="overlay" label="Chargement du graphique..." />}>
                                    <LineTrendChart
                                        labels={data.tendances.labels}
                                        datasets={data.tendances.datasets}
                                    />
                                </Suspense>
                            </CardBody>
                        </Card>
                    )}
                </div>

                {/* Colonne droite */}
                <div className="space-y-5">

                    {/* Brouillons */}
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between w-full">
                                <div className="flex items-center gap-2">
                                    <FileText size={16} className="text-[var(--color-warning-500)]" />
                                    <CardTitle>Brouillons en attente</CardTitle>
                                </div>
                                <Badge variant={brouillons.length > 0 ? 'warning' : 'default'} size="sm">
                                    {loading ? '…' : brouillons.length}
                                </Badge>
                            </div>
                        </CardHeader>
                        <CardBody>
                            {loading && <SkeletonListRows count={2} />}
                            {!loading && brouillons.length === 0 && (
                                <EmptyState msg="Aucun brouillon en attente de validation." />
                            )}
                            {!loading && brouillons.length > 0 && (
                                <ExpandableList
                                    items={brouillons}
                                    initialCount={5}
                                    renderItem={(b, i) => <BrouillonRow key={i} b={b} />}
                                />
                            )}
                        </CardBody>
                    </Card>

                    {/* Activité récente */}
                    <Card>
                        <CardHeader>
                            <div className="flex items-center gap-2">
                                <TrendingUp size={16} className="text-[var(--color-brand-500)]" />
                                <CardTitle>Activité récente</CardTitle>
                            </div>
                        </CardHeader>
                        <CardBody>
                            {loading && <SkeletonListRows count={3} />}
                            {!loading && activite.length === 0 && (
                                <EmptyState msg="Aucun mouvement RH récent." />
                            )}
                            {!loading && activite.length > 0 && (
                                <ExpandableList
                                    items={activite}
                                    initialCount={6}
                                    renderItem={(ev, i) => <ActiviteRow key={i} ev={ev} />}
                                />
                            )}
                        </CardBody>
                    </Card>

                    {/* Accès rapides */}
                    <Card>
                        <CardHeader>
                            <CardTitle>Accès rapides</CardTitle>
                        </CardHeader>
                        <CardBody className="space-y-2">
                            {[
                                { to: '/employes', label: 'Liste des employés', icon: Users },
                            ].map(({ to, label, icon: Icon }) => (
                                <Link
                                    key={to}
                                    to={to}
                                    className="flex items-center gap-3 py-3 px-3 min-h-11 bg-[var(--color-bg-subtle)] hover:bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] transition-all group rounded-none"
                                >
                                    <Icon size={15} className="text-[var(--color-brand-500)]" aria-hidden="true" />
                                    <span className="text-sm font-semibold text-[var(--color-fg)] group-hover:text-[var(--color-brand-500)] transition-colors">
                                        {label}
                                    </span>
                                    <ChevronRight size={13} className="ml-auto text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-500)] transition-colors" aria-hidden="true" />
                                </Link>
                            ))}
                        </CardBody>
                    </Card>
                </div>
            </div>
        </div>
    );
}