import { Eye, Edit2, Users, Mail, Phone, MapPin, ArrowUp, ArrowDown, Building, Briefcase } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { getFullPhotoUrl } from '@/utils/fileUtils';

const formatDateEmbauche = (dateStr) => {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '-';
    return d.toLocaleDateString('fr-FR');
};

export default function EmployeTable({
    employes = [],
    loading = false,
    canWrite = false,
    canValidate = false,
    onOpenDetail,
    onOpenEdit,
    onToggleStatus,
    sortConfig = { key: '', direction: 'asc' },
    onSortChange,
}) {
    if (loading) {
        return (
            <div className="p-4">
                <SkeletonTable rows={10} columns={8} />
            </div>
        );
    }

    if (employes.length === 0) {
        return (
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm p-12 text-center">
                <Users size={40} className="mx-auto text-ink-300 mb-3" />
                <p className="text-sm font-semibold text-ink-800 dark:text-white">Aucun employé trouvé</p>
                <p className="text-xs text-ink-400 mt-1">Aucun enregistrement ne correspond à vos critères de recherche ou filtres.</p>
            </div>
        );
    }

    const renderSortIcon = (key) => {
        if (sortConfig.key !== key) return null;
        return sortConfig.direction === 'asc' ? (
            <ArrowUp size={13} className="inline ml-1 text-brand-500" />
        ) : (
            <ArrowDown size={13} className="inline ml-1 text-brand-500" />
        );
    };

    const handleColumnHeaderClick = (key) => {
        if (onSortChange) {
            const isAsc = sortConfig.key === key && sortConfig.direction === 'asc';
            onSortChange(key, isAsc ? 'desc' : 'asc');
        }
    };

    return (
        <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden font-sans">
            <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-sm">
                    <thead>
                        <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-500 font-bold uppercase text-[10px] tracking-widest select-none">
                            <th 
                                className="px-5 py-3.5 cursor-pointer hover:bg-ink-100/50 dark:hover:bg-ink-900 transition-colors"
                                onClick={() => handleColumnHeaderClick('matricule')}
                            >
                                Employé & Matricule {renderSortIcon('matricule')}
                            </th>
                            <th 
                                className="px-5 py-3.5 cursor-pointer hover:bg-ink-100/50 dark:hover:bg-ink-900 transition-colors"
                                onClick={() => handleColumnHeaderClick('departement')}
                            >
                                Structure (Dépt. / Service) {renderSortIcon('departement')}
                            </th>
                            <th 
                                className="px-5 py-3.5 cursor-pointer hover:bg-ink-100/50 dark:hover:bg-ink-900 transition-colors"
                                onClick={() => handleColumnHeaderClick('fonction')}
                            >
                                Poste & Fonction {renderSortIcon('fonction')}
                            </th>
                            <th 
                                className="px-5 py-3.5 cursor-pointer hover:bg-ink-100/50 dark:hover:bg-ink-900 transition-colors"
                                onClick={() => handleColumnHeaderClick('site')}
                            >
                                Site {renderSortIcon('site')}
                            </th>
                            <th className="px-5 py-3.5">Coordonnées</th>
                            <th 
                                className="px-5 py-3.5 cursor-pointer hover:bg-ink-100/50 dark:hover:bg-ink-900 transition-colors"
                                onClick={() => handleColumnHeaderClick('dateEmbauche')}
                            >
                                Date d'embauche {renderSortIcon('dateEmbauche')}
                            </th>
                            <th 
                                className="px-5 py-3.5 cursor-pointer hover:bg-ink-100/50 dark:hover:bg-ink-900 transition-colors"
                                onClick={() => handleColumnHeaderClick('statut')}
                            >
                                Statut {renderSortIcon('statut')}
                            </th>
                            <th className="px-5 py-3.5 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                        {employes.map((emp) => {
                            const fullName = emp.nomComplet || `${emp.prenom || ''} ${emp.nom || ''}`.trim() || `Employé #${emp.idEmploye}`;
                            const initial = (emp.prenom?.[0] || emp.nom?.[0] || 'E').toUpperCase();
                            const deptName = emp.nomDepartement || emp.libelleDepartement || emp.departement || null;
                            const serviceName = emp.nomService || emp.libelleService || emp.service || null;
                            const fonctionName = emp.nomFonction || emp.libelleFonction || emp.fonction || null;
                            const siteName = emp.nomSite || emp.libelleSite || emp.site || null;
                            const dateRef = emp.dateEmbauche || emp.dateDeclaration || emp.createdAt;
                            const dateEmbaucheText = formatDateEmbauche(dateRef);

                            return (
                                <tr key={emp.idEmploye} className="hover:bg-ink-50/60 dark:hover:bg-ink-950/40 text-ink-900 dark:text-ink-100 transition-colors group">
                                    {/* 1. Employé & Matricule */}
                                    <td className="px-5 py-3.5">
                                        <div className="flex items-center gap-3">
                                            <div className="relative shrink-0">
                                                {emp.photoUrl ? (
                                                    <img 
                                                        src={getFullPhotoUrl(emp.photoUrl)} 
                                                        alt={fullName} 
                                                        className="w-9 h-9 rounded-none object-cover border border-ink-200 dark:border-ink-700" 
                                                        onError={(e) => {
                                                            e.currentTarget.onerror = null;
                                                            e.currentTarget.style.display = 'none';
                                                            if (e.currentTarget.nextElementSibling) {
                                                                e.currentTarget.nextElementSibling.classList.remove('hidden');
                                                            }
                                                        }}
                                                    />
                                                ) : null}
                                                <div className={`w-9 h-9 bg-brand-500 text-white flex items-center justify-center font-bold text-xs shrink-0 rounded-none shadow-xs ${emp.photoUrl ? 'hidden' : ''}`}>
                                                    {initial}
                                                </div>
                                                <span 
                                                    className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 border-white dark:border-ink-900 ${
                                                        emp.estActif ? 'bg-success-500' : 'bg-ink-400'
                                                    }`} 
                                                />
                                            </div>
                                            <div>
                                                <div 
                                                    onClick={() => onOpenDetail(emp)} 
                                                    className="font-bold text-ink-900 dark:text-white hover:text-brand-500 cursor-pointer text-sm leading-snug truncate max-w-[200px]" 
                                                    title={fullName}
                                                >
                                                    {fullName}
                                                </div>
                                                <div className="flex items-center gap-2 text-xs text-ink-400 mt-0.5">
                                                    <span className="font-mono font-semibold text-brand-500">{emp.matricule}</span>
                                                    {emp.cinNumero && (
                                                        <span className="text-[10px] text-ink-400 font-mono">CIN: {emp.cinNumero}</span>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </td>

                                    {/* 2. Structure & Organisation */}
                                    <td className="px-5 py-3.5">
                                        {deptName ? (
                                            <div className="space-y-1">
                                                <div className="inline-flex items-center gap-1 font-semibold text-xs text-ink-800 dark:text-ink-200">
                                                    <Building size={12} className="text-brand-500 shrink-0" />
                                                    <span className="truncate max-w-[160px]" title={deptName}>{deptName}</span>
                                                </div>
                                                {serviceName && (
                                                    <div className="text-[11px] text-ink-500 dark:text-ink-400 flex items-center gap-1">
                                                        <Briefcase size={11} className="text-ink-400 shrink-0" />
                                                        <span className="truncate max-w-[150px]" title={serviceName}>{serviceName}</span>
                                                    </div>
                                                )}
                                            </div>
                                        ) : (
                                            <span className="text-xs text-ink-400">-</span>
                                        )}
                                    </td>

                                    {/* 3. Poste & Fonction */}
                                    <td className="px-5 py-3.5">
                                        {fonctionName ? (
                                            <div className="font-semibold text-xs text-ink-800 dark:text-ink-100 truncate max-w-[180px]" title={fonctionName}>
                                                {fonctionName}
                                            </div>
                                        ) : (
                                            <span className="text-xs text-ink-400">-</span>
                                        )}
                                    </td>

                                    {/* 4. Site */}
                                    <td className="px-5 py-3.5">
                                        {siteName ? (
                                            <Badge variant="brand" size="sm" className="inline-flex items-center gap-1">
                                                <MapPin size={10} className="shrink-0" />
                                                <span className="truncate max-w-[130px]">{siteName}</span>
                                            </Badge>
                                        ) : (
                                            <span className="text-xs text-ink-400">-</span>
                                        )}
                                    </td>

                                    {/* 5. Coordonnées */}
                                    <td className="px-5 py-3.5">
                                        <div className="space-y-0.5 text-xs">
                                            {emp.email && (
                                                <a 
                                                    href={`mailto:${emp.email}`} 
                                                    className="flex items-center gap-1 text-ink-600 dark:text-ink-300 hover:text-brand-500 truncate max-w-[170px]"
                                                    title={emp.email}
                                                >
                                                    <Mail size={12} className="shrink-0 text-ink-400" />
                                                    <span className="truncate">{emp.email}</span>
                                                </a>
                                            )}
                                            {emp.telephone && (
                                                <a 
                                                    href={`tel:${emp.telephone}`} 
                                                    className="flex items-center gap-1 text-ink-600 dark:text-ink-300 hover:text-brand-500 font-mono text-[11px]"
                                                >
                                                    <Phone size={11} className="shrink-0 text-ink-400" />
                                                    <span>{emp.telephone}</span>
                                                </a>
                                            )}
                                            {!emp.email && !emp.telephone && (
                                                <span className="text-xs text-ink-400">-</span>
                                            )}
                                        </div>
                                    </td>

                                    {/* 6. Date d'embauche */}
                                    <td className="px-5 py-3.5 text-xs font-mono font-medium text-ink-700 dark:text-ink-300 whitespace-nowrap">
                                        {dateEmbaucheText}
                                    </td>

                                    {/* 7. Statut */}
                                    <td className="px-5 py-3.5 whitespace-nowrap">
                                        <div className="space-y-1">
                                            {emp.estActif ? (
                                                <Badge variant="success" size="sm" dot>Actif</Badge>
                                            ) : (
                                                <div>
                                                    <Badge variant="default" size="sm" dot>Inactif</Badge>
                                                    {emp.motifDesactivation && (
                                                        <div className="text-[10px] text-danger-500 font-medium mt-0.5" title={emp.motifDesactivation}>
                                                            {emp.motifDesactivation}
                                                        </div>
                                                    )}
                                                </div>
                                            )}
                                            {emp.estActif && emp.alerteContratTexte && (
                                                <div>
                                                    <Badge 
                                                        variant={emp.alerteContratNiveau === 'DANGER' ? 'danger' : emp.alerteContratNiveau === 'WARNING' ? 'warning' : 'brand'} 
                                                        size="xs"
                                                    >
                                                        {emp.alerteContratTexte}
                                                    </Badge>
                                                </div>
                                            )}
                                        </div>
                                    </td>

                                    {/* 8. Actions */}
                                    <td className="px-5 py-3.5 text-right whitespace-nowrap">
                                        <div className="flex justify-end items-center gap-1">
                                            <button
                                                onClick={() => onOpenDetail(emp)}
                                                className="p-1.5 text-ink-400 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 cursor-pointer"
                                                title="Voir la fiche complète"
                                            >
                                                <Eye size={16} />
                                            </button>
                                            {canWrite && (
                                                <button
                                                    onClick={() => onOpenEdit(emp)}
                                                    className="p-1.5 text-ink-400 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 cursor-pointer"
                                                    title="Modifier"
                                                >
                                                    <Edit2 size={16} />
                                                </button>
                                            )}
                                            {canValidate && (
                                                emp.estActif ? (
                                                    <Button
                                                        size="xs"
                                                        variant="danger"
                                                        onClick={() => onToggleStatus(emp)}
                                                        className="ml-1"
                                                    >
                                                        Désactiver
                                                    </Button>
                                                ) : (
                                                    <Button
                                                        size="xs"
                                                        variant="success"
                                                        onClick={() => onToggleStatus(emp)}
                                                        className="ml-1"
                                                    >
                                                        Réactiver
                                                    </Button>
                                                )
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
