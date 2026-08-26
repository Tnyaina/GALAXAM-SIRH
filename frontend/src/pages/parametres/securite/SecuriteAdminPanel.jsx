import { lazy, Suspense } from 'react';
import { Shield, Users, Menu } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import Tabs from '@/components/ui/navigation/Tabs';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

const UserManagementTab = lazy(() => import('@/components/parametres/securite/UserManagementTab'));
const RolesAndPermissionsTab = lazy(() => import('@/components/parametres/securite/RolesAndPermissionsTab'));
const MenuManagementTab = lazy(() => import('@/components/parametres/securite/MenuManagementTab'));

export default function SecuriteAdminPanel() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'users';

    const handleTabChange = (tabKey) => {
        setSearchParams({ tab: tabKey });
    };

    const mainTabs = [
        { id: 'users', label: 'Gestion des Utilisateurs', icon: <Users size={16} /> },
        { id: 'roles', label: 'Rôles & Permissions', icon: <Shield size={16} /> },
        { id: 'menus', label: 'Gestion des Menus', icon: <Menu size={16} /> }
    ];

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Sécurité & Accès" description="Administration des comptes utilisateurs, rôles, permissions et menus." />

            <PageHeader
                title="Sécurité & Contrôle d'Accès"
                subtitle="Gestion des comptes utilisateurs, assignation des rôles RBAC, permissions et visibilité des menus."
                icon={Shield}
                backLink="/dashboard"
                backLabel="Retour au Tableau de bord"
            />

            {/* Sélecteur d'onglets principal avec Tabs Design System et persistance URL */}
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden flex flex-col">
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
                    </Suspense>
                </div>
            </div>
        </div>
    );
}