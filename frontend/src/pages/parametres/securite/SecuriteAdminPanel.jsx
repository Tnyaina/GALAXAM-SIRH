import { lazy, Suspense } from 'react';
import { Shield, Users, ArrowLeft, Menu, FileText, Award } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

const UserManagementTab = lazy(() => import('@/components/parametres/securite/UserManagementTab'));
const RolesAndPermissionsTab = lazy(() => import('@/components/parametres/securite/RolesAndPermissionsTab'));
const MenuManagementTab = lazy(() => import('@/components/parametres/securite/MenuManagementTab'));
const DocumentTypeManagementTab = lazy(() => import('@/components/parametres/referentiels/DocumentTypeManagementTab'));
const RefCarriereManagementTab = lazy(() => import('@/components/parametres/referentiels/RefCarriereManagementTab'));

export default function SecuriteAdminPanel() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'users';

    const handleTabChange = (tabKey) => {
        setSearchParams({ tab: tabKey });
    };

    const mainTabs = [
        { id: 'users', label: 'Gestion des Utilisateurs', icon: <Users size={16} /> },
        { id: 'roles', label: 'Rôles & Permissions', icon: <Shield size={16} /> },
        { id: 'menus', label: 'Gestion des Menus', icon: <Menu size={16} /> },
        { id: 'docTypes', label: 'Types de Documents RH', icon: <FileText size={16} /> },
        { id: 'carriere', label: 'Référentiels Carrière & Paie', icon: <Award size={16} /> },
    ];

    return (
        <div className="space-y-6">
            {/* Lien retour */}
            <Link 
                to="/dashboard" 
                className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold "
            >
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            {/* En-tête de la page */}
            <div className="bg-[var(--color-surface)] rounded-none shadow-sm border border-[var(--color-border-subtle)] p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-[var(--color-brand-500)] text-white rounded-none flex items-center justify-center shadow-md shrink-0">
                        <Shield size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-[var(--color-fg)]">Sécurité & Administration RH</h1>
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            Gestion des utilisateurs, des rôles, des menus et des référentiels de documents et de carrière.
                        </p>
                    </div>
                </div>
            </div>

            {/* Sélecteur d'onglets principal avec Tabs Design System et persistance URL */}
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden">
                <Tabs 
                    tabs={mainTabs} 
                    activeTab={activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                {/* Contenu de l'onglet actif avec Suspense & SkeletonTable standardisé */}
                <div className="p-6">
                    <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                        {activeTab === 'users' && <UserManagementTab />}
                        {activeTab === 'roles' && <RolesAndPermissionsTab />}
                        {activeTab === 'menus' && <MenuManagementTab />}
                        {activeTab === 'docTypes' && <DocumentTypeManagementTab />}
                        {activeTab === 'carriere' && <RefCarriereManagementTab />}
                    </Suspense>
                </div>
            </div>
        </div>
    );
}