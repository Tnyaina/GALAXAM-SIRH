import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Layout from '@/components/layout/Layout';

export default function ProtectedRoute({ allowedRoles, requiredPermission, children }) {
    const { isAuthenticated, user, hasPermission, loading } = useAuth();

    // Écran de chargement lors de la récupération de la session
    if (loading) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-[#f7f7f7]">
                <div className="flex flex-col items-center gap-3">
                    <div className="w-10 h-10 border-4 border-[#1A82B1] border-t-transparent rounded-full animate-spin"></div>
                    <p className="text-sm text-neutral-500 font-medium">Chargement de la session...</p>
                </div>
            </div>
        );
    }

    // Redirige vers la page de connexion si non authentifié
    if (!isAuthenticated) {
        return <Navigate to="/" replace />;
    }

    // Redirige vers la page non autorisée si le rôle ou la permission est insuffisante
    if (allowedRoles && !allowedRoles.includes(user?.role)) {
        return <Navigate to="/non-autorise" replace />;
    }

    if (requiredPermission && !hasPermission(requiredPermission)) {
        return <Navigate to="/non-autorise" replace />;
    }

    return (
        <Layout>
            {children ? children : <Outlet />}
        </Layout>
    );
}
