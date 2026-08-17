import { useState, useEffect, memo } from 'react';
import { Link } from 'react-router-dom';
import {
    Users, FileText, Clock, AlertTriangle, CheckCheck,
    TrendingUp, RefreshCw, ChevronRight, Calendar,
    UserCheck, Briefcase, RotateCcw, ArrowRight
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useDashboard } from '@/hooks/useDashboard';
import { useToast } from '@/components/ui/feedback/Toast';
import StatCard from '@/components/ui/data-display/StatCard';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { SkeletonStatCard, Skeleton } from '@/components/ui/feedback/Skeleton';

// Lazy loading du graphique optionnel
// const LineTrendChart = lazy(() => import('@/components/ui/data-display/Chart').then(m => ({ default: m.LineTrendChart })));

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

function EmptyState({ msg }) {
    return (
        <div className="py-6 text-center">
            <CheckCheck size={20} className="text-[var(--color-success-500)] mx-auto mb-2" aria-hidden="true" />
            <p className="text-xs text-[var(--color-fg-muted)]">{msg}</p>
        </div>
    );
}

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
                    className="w-full mt-2 py-2.5 min-h-11 text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] transition-colors"
                >
                    Voir {hidden} de plus
                </button>
            )}
        </div>
    );
}

// Composants de rangées
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
                <p className="text-xs font-semibold text-[var(--color-fg)] truncate">
                    {alerte.nomPrenom}
                </p>
                <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                    {alerte.typeContrat}
                    {alerte.numeroContrat ? ` · N°${alerte.numeroContrat}` : ''}
                    {' · '}<span className="font-data">{fmtDate(alerte.dateEcheance)}</span>
                </p>
            </div>
            <Badge variant={isToday ? 'danger' : isUrgent ? 'warning' : 'primary'} size="sm">
                {isToday ? 'Auj.' : `J-${alerte.joursRestants}`}
            </Badge>
            <ChevronRight size={13} className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-500)] transition-colors shrink-0" />
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
                <p className="text-xs font-semibold text-[var(--color-fg)] truncate">{ev.nomPrenom}</p>
                <p className="text-[11px] text-[var(--color-fg-muted)] truncate">{ev.typeEvenement}</p>
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
    const toast = useToast();

    const kpis = data?.kpis;
    const alertesCritiques = data?.alertesCritiques || [];
    const alertes7j = data?.alertes7j || [];
    const alertes30j = data?.alertes30j || [];

    const activite = data?.activiteRecente || [];
    const absentsAujourdhui = data?.absentsAujourdhui || [];
    const demandesCongeAValider = data?.demandesCongeAValider || [];

    const totalAlertesContrats = alertesCritiques.length + alertes7j.length;

    const now = new Date();
    const greeting =
        now.getHours() < 12 ? 'Bonjour' :
            now.getHours() < 18 ? 'Bon après-midi' : 'Bonsoir';

    const [isOnline, setIsOnline] = useState(navigator.onLine);

    useEffect(() => {
        const handleOnline = () => {
            setIsOnline(true);
            toast({
                title: 'Connexion rétablie',
                message: 'Vous êtes à nouveau connecté. Actualisation des données...',
                type: 'success',
                duration: 3000
            });
            refresh();
        };

        const handleOffline = () => {
            setIsOnline(false);
        };

        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);

        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
        };
    }, [refresh, toast]);

    const handleRefresh = async () => {
        await refresh();
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
            <div className="bg-[var(--color-brand-800)] text-white p-6 flex items-center justify-between gap-4 shadow-md">
                <div>
                    <p className="text-[var(--color-brand-200)] text-xs font-medium mb-1 uppercase tracking-wider font-data">
                        {now.toLocaleDateString('fr-FR', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                    </p>
                    <h2 className="text-xl font-extrabold tracking-tight mb-1">
                        {greeting}, {user?.identifiant || 'utilisateur'}
                    </h2>
                    {loading ? (
                        <Skeleton className="h-4 w-64 bg-white/20 mt-1" />
                    ) : (
                        <p className="text-[var(--color-brand-100)] text-xs">
                            {demandesCongeAValider.length > 0
                                ? ` ${demandesCongeAValider.length} demande(s) de congé en attente de validation DAF/RH.`
                                : totalAlertesContrats > 0
                                    ? `⚠️ ${totalAlertesContrats} alerte(s) de contrat nécessitent votre attention.`
                                    : 'Tout est à jour dans l\'application SIRH.'}
                        </p>
                    )}
                </div>
                <div className="flex flex-col items-end gap-2">
                    <Button
                        onClick={handleRefresh}
                        loading={loading}
                        variant="ghost"
                        size="sm"
                        className="bg-white/15 hover:bg-white/25 text-white border-transparent"
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

            {/* Message offline */}
            {!isOnline && (
                <div role="alert" className="bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 px-5 py-4 text-xs text-[var(--color-warning-500)] flex items-center gap-3">
                    <AlertTriangle size={16} className="shrink-0" />
                    <span className="font-semibold">Connexion perdue. Affichage des dernières données en cache.</span>
                </div>
            )}

            {/* Message d'erreur */}
            {error && (
                <div role="alert" className="bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 px-5 py-4 text-xs text-[var(--color-danger-500)] flex items-center justify-between gap-4">
                    <div className="flex items-center gap-2">
                        <AlertTriangle size={16} className="shrink-0" />
                        <span>{typeof error === 'string' ? error : 'Impossible de charger le dashboard.'}</span>
                    </div>
                    <Button
                        onClick={refresh}
                        variant="ghost"
                        size="sm"
                        loading={loading}
                        className="text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/30 shrink-0"
                        icon={<RotateCcw size={14} />}
                    >
                        Réessayer
                    </Button>
                </div>
            )}

            {/* ----------------------------------------------------------------- */}
            {/* GRILLE KPIs (6 Métriques Clés)                                   */}
            {/* ----------------------------------------------------------------- */}
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
                            title="Congés à valider"
                            value={demandesCongeAValider.length}
                            icon={<Calendar size={18} />}
                            color={demandesCongeAValider.length > 0 ? 'warning' : 'brand'}
                        />
                        <StatCard
                            title="Absents ce jour"
                            value={absentsAujourdhui.length}
                            icon={<UserCheck size={18} />}
                            color={absentsAujourdhui.length > 0 ? 'info' : 'brand'}
                        />
                        <StatCard
                            title="Expirations 30j"
                            value={kpis?.contratsExpirant30j ?? '—'}
                            icon={<Clock size={18} />}
                            color={kpis?.contratsExpirant30j > 0 ? 'warning' : 'brand'}
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

            {/* ----------------------------------------------------------------- */}
            {/* SECTION 1 : MODULE CONGÉS & ABSENCES                             */}
            {/* ----------------------------------------------------------------- */}
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
                {/* Carte 1 : Demandes de Congés à Valider */}
                <Card>
                    <CardHeader>
                        <div className="flex items-center justify-between w-full">
                            <div className="flex items-center gap-2">
                                <Calendar size={16} className="text-warning-500" />
                                <CardTitle>Demandes de Congé à Valider (DAF / RH)</CardTitle>
                            </div>
                            <div className="flex items-center gap-2">
                                <Badge variant={demandesCongeAValider.length > 0 ? 'warning' : 'default'} size="sm">
                                    {loading ? '…' : demandesCongeAValider.length} en attente
                                </Badge>
                                <Link
                                    to="/conges"
                                    className="text-xs font-bold text-[var(--color-brand-500)] hover:text-[var(--color-brand-300)] flex items-center gap-1"
                                >
                                    Gérer <ArrowRight size={12} />
                                </Link>
                            </div>
                        </div>
                    </CardHeader>
                    <CardBody>
                        {loading && <SkeletonListRows count={3} />}
                        {!loading && demandesCongeAValider.length === 0 && (
                            <EmptyState msg="Aucune demande de congé en attente de validation." />
                        )}
                        {!loading && demandesCongeAValider.length > 0 && (
                            <div className="divide-y divide-[var(--color-border-subtle)]">
                                {demandesCongeAValider.slice(0, 5).map((d) => (
                                    <div key={d.idDemandeConge} className="py-3 flex items-center justify-between gap-3 text-xs">
                                        <div className="min-w-0">
                                            <p className="font-bold text-[var(--color-fg)] truncate">
                                                {d.nomPrenom} <span className="font-data text-[10px] text-[var(--color-fg-subtle)]">(#{d.matricule})</span>
                                            </p>
                                            <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                                {d.typeConge} · Du <span className="font-data">{fmtDate(d.dateDebut)}</span> au <span className="font-data">{fmtDate(d.dateFin)}</span>
                                            </p>
                                        </div>
                                        <div className="flex items-center gap-3 shrink-0">
                                            <span className="font-data font-extrabold text-[var(--color-brand-500)] text-xs">
                                                {d.nbJoursDecomptes}j
                                            </span>
                                            <Link
                                                to="/conges"
                                                className="px-2.5 py-1 rounded-none text-[11px] font-semibold uppercase tracking-wide bg-[var(--color-brand-500)]/15 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]/30 hover:bg-[var(--color-brand-500)] hover:text-white transition-all"
                                            >
                                                Valider
                                            </Link>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </CardBody>
                </Card>

                {/* Carte 2 : Salariés Actuellement Absents / En Congé */}
                <Card>
                    <CardHeader>
                        <div className="flex items-center justify-between w-full">
                            <div className="flex items-center gap-2">
                                <UserCheck size={16} className="text-info-500" />
                                <CardTitle>Salariés Actuellement Absents / En Congé</CardTitle>
                            </div>
                            <Badge variant={absentsAujourdhui.length > 0 ? 'info' : 'default'} size="sm">
                                {loading ? '…' : absentsAujourdhui.length} absent(s)
                            </Badge>
                        </div>
                    </CardHeader>
                    <CardBody>
                        {loading && <SkeletonListRows count={3} />}
                        {!loading && absentsAujourdhui.length === 0 && (
                            <EmptyState msg="Aucun collaborateur en congé ou absent aujourd'hui." />
                        )}
                        {!loading && absentsAujourdhui.length > 0 && (
                            <div className="divide-y divide-[var(--color-border-subtle)]">
                                {absentsAujourdhui.slice(0, 5).map((a) => (
                                    <div key={a.idDemandeConge} className="py-3 flex items-center justify-between gap-3 text-xs">
                                        <div className="min-w-0">
                                            <p className="font-bold text-[var(--color-fg)] truncate">
                                                {a.nomPrenom} <span className="font-data text-[10px] text-[var(--color-fg-subtle)]">(#{a.matricule})</span>
                                            </p>
                                            <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                                {a.typeConge} · Retour prévu : <span className="font-data font-semibold text-success-500">{fmtDate(a.dateFin)}</span>
                                            </p>
                                        </div>
                                        <span className="px-2 py-0.5 rounded-none text-[10px] font-data font-bold bg-info-500/10 text-info-500 border border-info-500/20 shrink-0">
                                            {a.nbJoursDecomptes}j posés
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </CardBody>
                </Card>
            </div>

            {/* ----------------------------------------------------------------- */}
            {/* SECTION 2 : ÉCHÉANCES CONTRATS & ACTIVITÉ RH                     */}
            {/* ----------------------------------------------------------------- */}
            <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">
                {/* Échéances Contrats & Essais */}
                <div className="xl:col-span-2 space-y-5">
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between w-full">
                                <div className="flex items-center gap-2">
                                    <AlertTriangle size={16} className="text-danger-500" />
                                    <CardTitle>Échéances de Contrats & Périodes d'Essai (7 à 30 jours)</CardTitle>
                                </div>
                                <Badge variant={totalAlertesContrats > 0 ? 'warning' : 'default'} size="sm">
                                    {loading ? '…' : totalAlertesContrats} alerte(s)
                                </Badge>
                            </div>
                        </CardHeader>
                        <CardBody>
                            {loading && <SkeletonListRows count={3} />}
                            {!loading && alertesCritiques.length === 0 && alertes7j.length === 0 && alertes30j.length === 0 && (
                                <EmptyState msg="Aucun contrat ni période d'essai n'arrive à échéance dans les 30 prochains jours." />
                            )}
                            {!loading && (alertesCritiques.length > 0 || alertes7j.length > 0 || alertes30j.length > 0) && (
                                <ExpandableList
                                    items={[...alertesCritiques, ...alertes7j, ...alertes30j]}
                                    initialCount={6}
                                    renderItem={(a, i) => <AlerteRow key={i} alerte={a} />}
                                />
                            )}
                        </CardBody>
                    </Card>
                </div>

                {/* Activité RH & Accès Rapides */}
                <div className="space-y-5">
                    {/* Activité récente */}
                    <Card>
                        <CardHeader>
                            <div className="flex items-center gap-2">
                                <TrendingUp size={16} className="text-[var(--color-brand-500)]" />
                                <CardTitle>Activité Récente & Mouvements RH</CardTitle>
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
                                    initialCount={5}
                                    renderItem={(ev, i) => <ActiviteRow key={i} ev={ev} />}
                                />
                            )}
                        </CardBody>
                    </Card>

                    {/* Accès Rapides */}
                    <Card>
                        <CardHeader>
                            <CardTitle>Accès Rapides SIRH</CardTitle>
                        </CardHeader>
                        <CardBody className="space-y-2">
                            {[
                                { to: '/conges', label: 'Gestion des Congés & Absences', icon: Calendar },
                                { to: '/employes', label: 'Gestion du Personnel & Fiches', icon: Users },
                                { to: '/pointages', label: 'Pointage & Temps de travail', icon: Clock },
                            ].map(({ to, label, icon: Icon }) => (
                                <Link
                                    key={to}
                                    to={to}
                                    className="flex items-center justify-between py-2.5 px-3 bg-[var(--color-bg-subtle)] hover:bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] transition-all text-xs font-semibold text-[var(--color-fg)]"
                                >
                                    <span className="flex items-center gap-2">
                                        <Icon size={14} className="text-[var(--color-brand-500)]" />
                                        {label}
                                    </span>
                                    <ChevronRight size={13} className="text-[var(--color-fg-subtle)]" />
                                </Link>
                            ))}
                        </CardBody>
                    </Card>
                </div>
            </div>
        </div>
    );
}