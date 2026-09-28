import {
    User, Users, Briefcase, Building2, Calendar, Phone, Mail,
    CreditCard, Folder, Clock, Banknote, ArrowRight, ShieldCheck, MapPin
} from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import AuthenticatedImage from '@/components/ui/data-display/AuthenticatedImage';
import { fmtDate } from '@/utils/dateUtils';

function MiniRow({ label, value, isMono = false, isLink = false, href = null, icon: Icon = null }) {
    if (!value || value === '-') return null;
    return (
        <div className="flex items-center justify-between gap-2.5 py-1.5 text-xs border-b border-[var(--color-border-subtle)] last:border-0">
            <span className="text-[var(--color-fg-muted)] text-[11px] shrink-0 font-medium flex items-center gap-1.5">
                {Icon && <Icon size={12} className="text-[var(--color-fg-subtle)] shrink-0" />}
                {label}
            </span>
            {isLink && href ? (
                <a
                    href={href}
                    className="text-right text-[11px] sm:text-xs font-semibold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] hover:underline truncate"
                >
                    {value}
                </a>
            ) : (
                <span className={`text-right text-[11px] sm:text-xs font-bold text-[var(--color-fg)] break-words leading-snug ${isMono ? 'font-mono' : ''}`}>
                    {value}
                </span>
            )}
        </div>
    );
}

export default function EmployeSideSummaryPanel({
    emp,
    documentsCount = 0,
    activeTab,
    onNavigateTab,
    canWrite: _canWrite = false
}) {
    if (!emp) return null;

    const hasContract = Boolean(emp.idContratActif || (emp.statutContrat && emp.statutContrat !== 'Aucun contrat' && emp.statutContrat !== 'BROUILLON'));
    const contratType = emp.libelleTypeContrat || emp.typeContrat || (hasContract ? emp.statutContrat : null);

    return (
        <div className="space-y-3.5">
            {/* 1. Carte Identité Rapide */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] border-t-2 border-t-[var(--color-brand-500)] p-3.5 flex items-center gap-3">
                <div className="w-12 h-12 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none overflow-hidden shrink-0 flex items-center justify-center shadow-2xs">
                    <AuthenticatedImage
                        src={emp.photoUrl}
                        alt={`Photo de ${emp.prenom || ''} ${emp.nom || ''}`.trim()}
                        className="w-full h-full object-cover"
                        fallback={<User size={22} className="text-[var(--color-fg-subtle)]" />}
                    />
                </div>
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5 flex-wrap">
                        <span className="font-bold text-xs sm:text-sm text-[var(--color-fg)] truncate">
                            {emp.nom} {emp.prenom}
                        </span>
                        <span className="font-mono text-[10px] font-bold bg-[var(--color-bg-subtle)] text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] px-1.5 py-0.5 border border-[var(--color-border-subtle)]">
                            #{emp.matricule}
                        </span>
                    </div>
                    <div className="flex items-center gap-1.5 mt-1 flex-wrap">
                        {emp.estActif ? (
                            <Badge variant="success" size="sm" dot>Actif</Badge>
                        ) : (
                            <Badge variant="danger" size="sm" dot>Inactif</Badge>
                        )}
                        {contratType && (
                            <Badge variant="brand" size="sm">
                                {contratType}
                            </Badge>
                        )}
                    </div>
                </div>
            </div>

            {/* 2. Affectation & Hiérarchie */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2">
                <h3 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                    <Building2 size={13} className="text-[var(--color-brand-500)]" />
                    Affectation &amp; Organisation
                </h3>
                <div className="space-y-0.5">
                    <MiniRow label="Site" value={emp.nomSite || emp.site || emp.libelleSite} icon={MapPin} />
                    <MiniRow label="Département" value={emp.nomDepartement || emp.departement || emp.libelleDepartement} icon={Building2} />
                    <MiniRow label="Fonction" value={emp.nomFonction || emp.fonction || emp.libelleFonction} icon={Briefcase} />
                    {emp.superieurHierarchique && (
                        <MiniRow label="Manager" value={emp.superieurHierarchique} icon={User} />
                    )}
                </div>
            </div>

            {/* 3. Statut Contractuel */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2">
                <div className="flex items-center justify-between gap-2">
                    <h3 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5 truncate">
                        <ShieldCheck size={13} className="text-[var(--color-brand-500)]" />
                        Statut Contractuel
                    </h3>
                    <Badge variant={hasContract ? "success" : "default"} size="sm" className="font-bold shrink-0 text-[10px]">
                        {hasContract ? "Sous contrat" : "Sans contrat"}
                    </Badge>
                </div>
                <div className="space-y-0.5">
                    <MiniRow label="N° contrat" value={emp.numeroContratActif || emp.numeroContrat} isMono />
                    <MiniRow label="Type" value={contratType} />
                    <MiniRow label="Date d'entrée" value={fmtDate(emp.dateEmbauche || emp.dateEntree)} icon={Calendar} />
                    <MiniRow label="Ancienneté" value={emp.ancienneteEffectiveTexte || emp.ancienneteContratTexte} icon={Clock} />
                </div>
            </div>

            {/* 4. Coordonnées & État Civil */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2">
                <h3 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                    <Phone size={13} className="text-[var(--color-brand-500)]" />
                    Coordonnées Directes
                </h3>
                <div className="space-y-0.5">
                    {emp.telephone && (
                        <MiniRow
                            label="Téléphone"
                            value={emp.telephone}
                            isMono
                            isLink
                            href={`tel:${emp.telephone}`}
                            icon={Phone}
                        />
                    )}
                    {emp.email && (
                        <MiniRow
                            label="Email"
                            value={emp.email}
                            isLink
                            href={`mailto:${emp.email}`}
                            icon={Mail}
                        />
                    )}
                    {emp.cinNumero && (
                        <MiniRow
                            label="N° CIN"
                            value={emp.cinNumero}
                            isMono
                            icon={CreditCard}
                        />
                    )}
                    {emp.situationFamiliale && (
                        <MiniRow
                            label="Situation"
                            value={emp.situationFamiliale}
                            icon={Users}
                        />
                    )}
                </div>
            </div>

            {/* 5. Raccourcis Navigation Dossier (KISS) */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border)] p-3.5 space-y-2.5">
                <h3 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                    <ArrowRight size={13} className="text-[var(--color-brand-500)]" />
                    Navigation Rapide Dossier
                </h3>
                <div className="flex flex-col gap-1 pt-0.5">
                    {[
                        { id: 'personnel', label: 'Fiche Personnelle', icon: User },
                        { id: 'famille', label: 'Famille & Contacts', icon: Users },
                        { id: 'documents', label: 'Documents RH', icon: Folder, count: documentsCount },
                        { id: 'carriere', label: 'Carrière & Contrats', icon: Briefcase },
                        { id: 'conges', label: 'Congés & Absences', icon: Calendar },
                        { id: 'pointage', label: 'Pointages & Présence', icon: Clock },
                        { id: 'paie', label: 'Paie & Rémunération', icon: Banknote },
                    ].map(({ id, label, icon: Icon, count }) => {
                        const isActive = activeTab === id;
                        return (
                            <button
                                key={id}
                                type="button"
                                onClick={() => onNavigateTab(id)}
                                className={`w-full py-2 px-3 text-xs flex items-center justify-between font-semibold border transition-colors rounded-none cursor-pointer leading-tight ${
                                    isActive
                                        ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)] shadow-2xs'
                                        : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] hover:border-[var(--color-border)]'
                                }`}
                            >
                                <span className="flex items-center gap-2">
                                    <Icon size={14} className={isActive ? 'text-white' : 'text-[var(--color-brand-500)]'} />
                                    <span>{label}</span>
                                </span>
                                {count !== undefined && (
                                    <span className={`text-[10px] font-bold px-1.5 py-0.5 border ${
                                        isActive
                                            ? 'bg-white/20 text-white border-white/30'
                                            : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]'
                                    }`}>
                                        {count}
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}
