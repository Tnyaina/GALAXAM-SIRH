import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Layout from '@/components/layout/Layout';
import Loading from '@/components/ui/feedback/Loading';

export default function ProtectedRoute({ allowedRoles, requiredPermission, children }) {
    const location = useLocation();
    const { isAuthenticated, user, hasPermission, getDefaultHomeRoute, loading } = useAuth();

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
        const message = typeof window !== 'undefined' ? localStorage.getItem('galaxam-auth-message') : null;
        return <Navigate to="/" replace state={message ? { message } : undefined} />;
    }

    // Redirige vers la page non autorisée si le rôle est insuffisant
    if (allowedRoles && !allowedRoles.includes(user?.role) && user?.role !== 'ROLE_ADMIN') {
        return <Navigate to="/non-autorise" replace />;
    }

    // Redirige si la permission requise est manquante
    if (requiredPermission && !hasPermission(requiredPermission)) {
        // Si l'utilisateur atterrit sur /dashboard sans avoir EMPLOYE_READ, on le redirige vers son module principal
        if (location.pathname === '/dashboard') {
            const defaultHome = getDefaultHomeRoute();
            if (defaultHome && defaultHome !== '/dashboard' && defaultHome !== '/non-autorise') {
                return <Navigate to={defaultHome} replace />;
            }
        }
        return <Navigate to="/non-autorise" replace />;
    }

    return (
        <Layout>
            {children ? children : <Outlet />}
        </Layout>
    );
}
