import { Link } from 'react-router-dom';
import {
    Users, FileText, Clock, AlertTriangle, CheckCheck,
    TrendingUp, RefreshCw, ChevronRight, Calendar,
    UserCheck, Briefcase, ArrowUpRight
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useDashboard } from '@/hooks/useDashboard';

// =========================================================================
// Utilitaires
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

function getBadgeClass(joursRestants) {
    if (joursRestants === 0) return 'bg-danger-500/15 text-danger-600 dark:text-danger-400';
    if (joursRestants <= 7) return 'bg-warning-500/15 text-warning-700 dark:text-warning-400';
    return 'bg-brand-500/10 text-brand-700 dark:text-brand-400';
}

// =========================================================================
// Composants atomiques
// =========================================================================

function SkeletonCard() {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-xl border border-ink-150 dark:border-ink-800 p-5 animate-pulse">
            <div className="flex items-center justify-between mb-4">
                <div className="w-8 h-8 bg-ink-100 dark:bg-ink-800 rounded-lg" />
                <div className="w-16 h-4 bg-ink-100 dark:bg-ink-800 rounded" />
            </div>
            <div className="w-12 h-8 bg-ink-100 dark:bg-ink-800 rounded mb-1" />
            <div className="w-24 h-3 bg-ink-100 dark:bg-ink-800 rounded" />
        </div>
    );
}

function KpiCard({ icon: Icon, label, value, sublabel, color, linkTo }) {
    const content = (
        <div className={`bg-white dark:bg-ink-900 rounded-xl border border-ink-150 dark:border-ink-800 p-5 group transition-all duration-200 ${linkTo ? 'hover:shadow-lg hover:border-brand-300 dark:hover:border-brand-700 cursor-pointer' : ''}`}>
            <div className="flex items-start justify-between mb-3">
                <div className={`w-9 h-9 ${color} rounded-lg flex items-center justify-center shrink-0`}>
                    <Icon size={18} className="text-white" />
                </div>
                {linkTo && (
                    <ArrowUpRight size={14} className="text-ink-300 group-hover:text-brand-500 transition-colors mt-0.5" />
                )}
            </div>
            <p className="text-2xl font-extrabold text-ink-900 dark:text-white tabular-nums">
                {value ?? <span className="text-ink-300">—</span>}
            </p>
            <p className="text-sm font-medium text-ink-600 dark:text-ink-400 mt-0.5">{label}</p>
            {sublabel && <p className="text-xs text-ink-400 mt-1">{sublabel}</p>}
        </div>
    );
    return linkTo ? <Link to={linkTo}>{content}</Link> : content;
}

function SectionCard({ title, icon: Icon, iconColor, count, countColor, children, className = '' }) {
    return (
        <div className={`bg-white dark:bg-ink-900 rounded-xl border border-ink-150 dark:border-ink-800 overflow-hidden ${className}`}>
            <div className="px-5 py-4 border-b border-ink-100 dark:border-ink-800 flex items-center gap-3">
                <Icon size={16} className={iconColor} />
                <h2 className="text-sm font-bold text-ink-800 dark:text-white flex-1">{title}</h2>
                {count !== undefined && (
                    <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${countColor || 'bg-ink-100 dark:bg-ink-800 text-ink-500'}`}>
                        {count}
                    </span>
                )}
            </div>
            <div className="p-4">{children}</div>
        </div>
    );
}

function EmptyState({ msg }) {
    return (
        <div className="py-6 text-center">
            <CheckCheck size={20} className="text-success-500 mx-auto mb-2" />
            <p className="text-sm text-ink-400">{msg}</p>
        </div>
    );
}

function AlerteRow({ alerte }) {
    const isToday = alerte.joursRestants === 0;
    const isUrgent = alerte.joursRestants <= 7;
    return (
        <Link
            to={`/employes/${alerte.idEmploye}?tab=carriere`}
            className="flex items-center gap-3 py-2.5 px-1 rounded-lg hover:bg-ink-50 dark:hover:bg-ink-800/40 transition-colors group"
        >
            <div className={`w-2 h-2 rounded-full shrink-0 ${isToday ? 'bg-danger-500 animate-pulse' : isUrgent ? 'bg-warning-500' : 'bg-brand-400'}`} />
            <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-ink-800 dark:text-ink-100 truncate">
                    {alerte.nomPrenom}
                </p>
                <p className="text-xs text-ink-500 dark:text-ink-400 truncate">
                    {alerte.typeContrat}
                    {alerte.numeroContrat ? ` · N°${alerte.numeroContrat}` : ''}
                    {' · '}{fmtDate(alerte.dateEcheance)}
                </p>
            </div>
            <span className={`text-xs font-bold px-2 py-0.5 rounded-full shrink-0 ${getBadgeClass(alerte.joursRestants)}`}>
                {isToday ? 'Auj.' : `J-${alerte.joursRestants}`}
            </span>
            <ChevronRight size={13} className="text-ink-300 group-hover:text-brand-500 transition-colors shrink-0" />
        </Link>
    );
}

function BrouillonRow({ b }) {
    return (
        <Link
            to={`/employes/${b.idEmploye}?tab=carriere&wf=${b.idWorkflow}`}
            className="flex items-center gap-3 py-2.5 px-1 rounded-lg hover:bg-ink-50 dark:hover:bg-ink-800/40 transition-colors group"
        >
            <div className={`w-2 h-2 rounded-full shrink-0 ${b.joursAttente >= 2 ? 'bg-warning-500' : 'bg-brand-400'}`} />
            <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-ink-800 dark:text-ink-100 truncate">{b.nomPrenom}</p>
                <p className="text-xs text-ink-500 dark:text-ink-400 truncate">{b.typeEvenement} · Effet : {fmtDate(b.dateEffet)}</p>
            </div>
            <span className={`text-xs font-bold px-2 py-0.5 rounded-full shrink-0 ${b.joursAttente >= 2 ? 'bg-warning-500/15 text-warning-700 dark:text-warning-400' : 'bg-brand-500/10 text-brand-700 dark:text-brand-400'}`}>
                {b.joursAttente}j d'attente
            </span>
            <ChevronRight size={13} className="text-ink-300 group-hover:text-brand-500 transition-colors shrink-0" />
        </Link>
    );
}

function ActiviteRow({ ev }) {
    // Correspond au libelle de ref_statut_workflow pour le code 'VALIDE'
    const isValide = ev.statut === 'Valide et applique par la DAF' ||
                     ev.statut?.toLowerCase().includes('valid');
    return (
        <Link
            to={`/employes/${ev.idEmploye}?tab=carriere`}
            className="flex items-center gap-3 py-2.5 px-1 rounded-lg hover:bg-ink-50 dark:hover:bg-ink-800/40 transition-colors group"
        >
            <div className={`w-7 h-7 rounded-lg flex items-center justify-center shrink-0 text-white text-[10px] font-bold ${isValide ? 'bg-success-500' : 'bg-ink-400'}`}>
                {ev.nomPrenom?.charAt(0) || '?'}
            </div>
            <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-ink-800 dark:text-ink-100 truncate">{ev.nomPrenom}</p>
                <p className="text-xs text-ink-500 dark:text-ink-400 truncate">{ev.typeEvenement}</p>
            </div>
            <div className="text-right shrink-0">
                <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${isValide ? 'bg-success-500/10 text-success-700 dark:text-success-400' : 'bg-ink-100 dark:bg-ink-800 text-ink-500'}`}>
                    {isValide ? 'Validé' : 'Annulé'}
                </span>
                <p className="text-[10px] text-ink-400 mt-0.5">{fmtRelative(ev.updatedAt)}</p>
            </div>
        </Link>
    );
}

// =========================================================================
// Dashboard — Composant principal
// =========================================================================

export default function Dashboard() {
    const { user } = useAuth();
    const { data, loading, error, refresh, lastRefresh } = useDashboard();

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
        now.getHours() < 18 ? 'Bon apres-midi' : 'Bonsoir';

    return (
        <div className="space-y-6">

            {/* ── Bannière de bienvenue ─────────────────────────────────── */}
            <div className="bg-gradient-to-r from-brand-800 to-brand-600 dark:from-brand-950 dark:to-brand-800 rounded-xl p-6 text-white flex items-center justify-between gap-4 shadow-md">
                <div>
                    <p className="text-brand-200 text-sm font-medium mb-1">
                        {now.toLocaleDateString('fr-FR', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                    </p>
                    <h2 className="text-2xl font-extrabold mb-1">
                        {greeting}, {user?.identifiant}
                    </h2>
                    <p className="text-brand-200 text-sm">
                        {totalAlertes > 0
                            ? `${totalAlertes} alerte(s) RH nécessitent votre attention.`
                            : 'Aucune alerte critique aujourd\'hui. Tout est à jour.'}
                    </p>
                </div>
                <div className="flex flex-col items-end gap-2">
                    <button
                        onClick={refresh}
                        disabled={loading}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-white/15 hover:bg-white/25 text-white rounded-lg transition-colors disabled:opacity-50"
                    >
                        <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
                        Actualiser
                    </button>
                    {lastRefresh && (
                        <p className="text-[10px] text-brand-300">
                            Dernière MAJ : {lastRefresh.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                        </p>
                    )}
                </div>
            </div>

            {/* ── Erreur ───────────────────────────────────────────────── */}
            {error && (
                <div className="bg-danger-50 dark:bg-danger-500/10 border border-danger-200 dark:border-danger-500/30 rounded-xl px-5 py-4 text-sm text-danger-700 dark:text-danger-400 flex items-center gap-2">
                    <AlertTriangle size={15} className="shrink-0" />
                    {error}
                </div>
            )}

            {/* ── KPIs ─────────────────────────────────────────────────── */}
            <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-4">
                {loading ? (
                    Array.from({ length: 6 }).map((_, i) => <SkeletonCard key={i} />)
                ) : (
                    <>
                        <KpiCard
                            icon={Users}
                            label="Effectif actif"
                            value={kpis?.totalEffectifActif}
                            sublabel="Tous employés actifs"
                            color="bg-brand-500"
                            linkTo="/employes"
                        />
                        <KpiCard
                            icon={Briefcase}
                            label="Effectif comptabilisé"
                            value={kpis?.effectifComptabilise}
                            sublabel="Hors stagiaires / consultants"
                            color="bg-brand-700"
                            linkTo="/employes"
                        />
                        <KpiCard
                            icon={TrendingUp}
                            label="Nouvelles embauches"
                            value={kpis?.nouvellesEmbauchesMois}
                            sublabel="Ce mois-ci"
                            color="bg-success-500"
                        />
                        <KpiCard
                            icon={Calendar}
                            label="Contrats expirant"
                            value={kpis?.contratsExpirant30j}
                            sublabel="Dans les 30 jours"
                            color={kpis?.contratsExpirant30j > 0 ? 'bg-warning-500' : 'bg-ink-400'}
                        />
                        <KpiCard
                            icon={UserCheck}
                            label="Essais en cours"
                            value={kpis?.essaisEnCours}
                            sublabel="Périodes d'essai actives"
                            color="bg-info-600"
                        />
                        <KpiCard
                            icon={FileText}
                            label="Brouillons en attente"
                            value={kpis?.brouillonsEnAttente}
                            sublabel="Validation DAF requise"
                            color={kpis?.brouillonsEnAttente > 0 ? 'bg-warning-600' : 'bg-ink-400'}
                        />
                    </>
                )}
            </div>

            {/* ── Ligne principale ─────────────────────────────────────── */}
            <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">

                {/* ALERTES CRITIQUES + 7j */}
                <div className="xl:col-span-2 space-y-4">

                    {/* Critiques */}
                    <SectionCard
                        title="Alertes critiques — Aujourd'hui"
                        icon={AlertTriangle}
                        iconColor="text-danger-500"
                        count={loading ? '…' : alertesCritiques.length}
                        countColor={alertesCritiques.length > 0 ? 'bg-danger-500 text-white' : 'bg-ink-100 dark:bg-ink-800 text-ink-400'}
                    >
                        {loading && (
                            <div className="space-y-2">
                                {[...Array(2)].map((_, i) => (
                                    <div key={i} className="h-10 bg-ink-100 dark:bg-ink-800 rounded-lg animate-pulse" />
                                ))}
                            </div>
                        )}
                        {!loading && alertesCritiques.length === 0 && (
                            <EmptyState msg="Aucun contrat ou essai n'expire aujourd'hui." />
                        )}
                        {!loading && alertesCritiques.map((a, i) => <AlerteRow key={i} alerte={a} />)}
                    </SectionCard>

                    {/* 7 jours */}
                    <SectionCard
                        title="Échéances dans les 7 jours"
                        icon={Clock}
                        iconColor="text-warning-500"
                        count={loading ? '…' : alertes7j.length}
                        countColor={alertes7j.length > 0 ? 'bg-warning-500 text-white' : 'bg-ink-100 dark:bg-ink-800 text-ink-400'}
                    >
                        {loading && (
                            <div className="space-y-2">
                                {[...Array(3)].map((_, i) => (
                                    <div key={i} className="h-10 bg-ink-100 dark:bg-ink-800 rounded-lg animate-pulse" />
                                ))}
                            </div>
                        )}
                        {!loading && alertes7j.length === 0 && (
                            <EmptyState msg="Aucune échéance dans les 7 prochains jours." />
                        )}
                        {!loading && alertes7j.map((a, i) => <AlerteRow key={i} alerte={a} />)}
                    </SectionCard>

                    {/* 30 jours */}
                    {alertes30j.length > 0 && (
                        <SectionCard
                            title="Échéances dans les 30 jours"
                            icon={Calendar}
                            iconColor="text-brand-500"
                            count={alertes30j.length}
                            countColor="bg-brand-500/15 text-brand-700 dark:text-brand-400"
                        >
                            {alertes30j.map((a, i) => <AlerteRow key={i} alerte={a} />)}
                        </SectionCard>
                    )}
                </div>

                {/* Colonne droite */}
                <div className="space-y-4">

                    {/* Brouillons */}
                    <SectionCard
                        title="Brouillons en attente"
                        icon={FileText}
                        iconColor="text-warning-500"
                        count={loading ? '…' : brouillons.length}
                        countColor={brouillons.length > 0 ? 'bg-warning-500/15 text-warning-700 dark:text-warning-400' : 'bg-ink-100 dark:bg-ink-800 text-ink-400'}
                    >
                        {loading && (
                            <div className="space-y-2">
                                {[...Array(2)].map((_, i) => (
                                    <div key={i} className="h-10 bg-ink-100 dark:bg-ink-800 rounded-lg animate-pulse" />
                                ))}
                            </div>
                        )}
                        {!loading && brouillons.length === 0 && (
                            <EmptyState msg="Aucun brouillon en attente de validation." />
                        )}
                        {!loading && brouillons.map((b, i) => <BrouillonRow key={i} b={b} />)}
                    </SectionCard>

                    {/* Activité récente */}
                    <SectionCard
                        title="Activité récente"
                        icon={TrendingUp}
                        iconColor="text-brand-500"
                    >
                        {loading && (
                            <div className="space-y-2">
                                {[...Array(4)].map((_, i) => (
                                    <div key={i} className="h-10 bg-ink-100 dark:bg-ink-800 rounded-lg animate-pulse" />
                                ))}
                            </div>
                        )}
                        {!loading && activite.length === 0 && (
                            <EmptyState msg="Aucun mouvement RH récent." />
                        )}
                        {!loading && activite.map((ev, i) => <ActiviteRow key={i} ev={ev} />)}
                    </SectionCard>

                    {/* Accès rapides */}
                    <SectionCard title="Accès rapides" icon={ChevronRight} iconColor="text-ink-400">
                        <div className="space-y-2">
                            {[
                                { to: '/employes', label: 'Liste des employés', icon: Users, color: 'text-brand-500' },
                            ].map(({ to, label, icon: Icon, color }) => (
                                <Link
                                    key={to}
                                    to={to}
                                    className="flex items-center gap-3 py-2.5 px-3 rounded-lg bg-ink-50 dark:bg-ink-800/50 hover:bg-brand-50 dark:hover:bg-brand-900/20 hover:border-brand-200 dark:hover:border-brand-800 border border-ink-100 dark:border-ink-800 transition-all group"
                                >
                                    <Icon size={15} className={color} />
                                    <span className="text-sm font-medium text-ink-700 dark:text-ink-200 group-hover:text-brand-600 dark:group-hover:text-brand-400 transition-colors">
                                        {label}
                                    </span>
                                    <ChevronRight size={13} className="ml-auto text-ink-300 group-hover:text-brand-500 transition-colors" />
                                </Link>
                            ))}
                        </div>
                    </SectionCard>
                </div>
            </div>
        </div>
    );
}
