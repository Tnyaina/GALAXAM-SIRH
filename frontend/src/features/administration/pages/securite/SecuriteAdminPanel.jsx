import { lazy, Suspense } from 'react';
import { Shield, Users, Menu } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import AdministrationSettingsView from '@/features/administration/components/navigation/AdministrationSettingsView';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

const UserManagementTab = lazy(() => import('@/features/administration/components/security/UserManagementTab'));
const RolesAndPermissionsTab = lazy(() => import('@/features/administration/components/security/RolesAndPermissionsTab'));
const MenuManagementTab = lazy(() => import('@/features/administration/components/security/MenuManagementTab'));

export default function SecuriteAdminPanel() {
    const [searchParams, setSearchParams] = useSearchParams();
    const requestedTab = searchParams.get('tab') || 'users';

    const handleTabChange = (tabKey) => {
        setSearchParams(previous => { const next = new URLSearchParams(previous); next.set('tab', tabKey); return next; });
    };

    const mainTabs = [
        { id: 'users', label: 'Utilisateurs', icon: <Users size={16} /> },
        { id: 'roles', label: 'Rôles & permissions', icon: <Shield size={16} /> },
        { id: 'menus', label: 'Menus', icon: <Menu size={16} /> }
    ];

    const activeTab = mainTabs.some(tab => tab.id === requestedTab) ? requestedTab : mainTabs[0].id;

    return (
        <div className="space-y-3 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full">
            <SEO title="Sécurité & Accès" description="Administration des comptes utilisateurs, rôles, permissions et menus." />

            <PageHeader
                title="Accès & système"
                subtitle="Gérez les utilisateurs, leurs permissions et les menus accessibles."
                icon={Shield}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Paramètres', href: '/admin/organisation' },
                    { label: 'Sécurité & Contrôle d\'Accès' }
                ]}
            />

            {/* Les panneaux métier portent leur propre cadre ; la navigation reste intégrée à la page. */}
            <AdministrationSettingsView groups={[{ id: 'sections', label: 'Configuration', items: mainTabs }]} activeId={activeTab} onChange={handleTabChange}>
                <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                    {activeTab === 'users' && <UserManagementTab />}
                    {activeTab === 'roles' && <RolesAndPermissionsTab />}
                    {activeTab === 'menus' && <MenuManagementTab />}
                </Suspense>
            </AdministrationSettingsView>
        </div>
    );
}
