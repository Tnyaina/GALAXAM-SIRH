import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Layout from '@/components/layout/Layout';
import Loading from '@/components/ui/feedback/Loading';

export default function ProtectedRoute({ allowedRoles, requiredPermission, children }) {
    const { isAuthenticated, user, hasPermission, loading } = useAuth();

    // Écran de chargement lors de la récupération de la session (Thème & Token dynamique)
    if (loading) {
        return (
            <Loading
                display="fullscreen"
                size="lg"
                label="Chargement de la session..."
            />
        );
    }

    // Redirige vers la page de connexion si non authentifié
    if (!isAuthenticated) {
        return <Navigate to="/" replace />;
    }

    // Redirige vers la page non autorisée si le rôle ou la permission est insuffisante
    if (allowedRoles && !allowedRoles.includes(user?.role)) {
        if (user?.role === 'ROLE_AGENT_POINTAGE') {
            return <Navigate to="/pointages" replace />;
        }
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