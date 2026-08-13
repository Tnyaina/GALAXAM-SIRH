import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";
import ProtectedRoute from "@/routes/ProtectedRoute";
import { AuthProvider } from "@/context/AuthContext";
import Loading from "@/components/ui/Loading";

// Code-splitting / Lazy loading des pages pour des performances optimales
const Login = lazy(() => import("@/pages/auth/Login"));
const Dashboard = lazy(() => import("@/pages/dashboard/Dashboard"));
const EmployeList = lazy(() => import("@/pages/employe/EmployeList"));
const EmployeFicheDetailPage = lazy(() => import("@/pages/employe/EmployeFicheDetailPage"));
const SecuriteAdminPanel = lazy(() => import("@/pages/parametres/securite/SecuriteAdminPanel"));
const StructureAdminPanel = lazy(() => import("@/pages/parametres/structure/StructureAdminPanel"));
const PaieConfigAdminPanel = lazy(() => import("@/pages/parametres/paie/PaieConfigAdminPanel"));
const Unauthorized = lazy(() => import("@/pages/systeme/Unauthorized"));
const NotFound = lazy(() => import("@/pages/systeme/NotFound"));

const PageLoader = () => (
    <Loading display="fullscreen" size="lg" label="Chargement du module..." />
);

export default function Router() {
    return (
        <AuthProvider>
            <Suspense fallback={<PageLoader />}>
                <Routes>
                    {/* Route publique d'authentification */}
                    <Route path="/" element={<Login />} />
                    <Route path="/non-autorise" element={<Unauthorized />} />

                    {/* Routes protégées standard */}
                    <Route element={<ProtectedRoute />}>
                        <Route path="/dashboard" element={<Dashboard />} />
                    </Route>

                    {/* Module Employé : Nécessite la permission EMPLOYE_READ */}
                    <Route element={<ProtectedRoute requiredPermission="EMPLOYE_READ" />}>
                        <Route path="/employes" element={<EmployeList />} />
                        <Route path="/employes/:id" element={<EmployeFicheDetailPage />} />
                    </Route>

                    {/* Routes protégées admin (nécessite d'être ADMIN) */}
                    <Route element={<ProtectedRoute allowedRoles={["ROLE_ADMIN"]} />}>
                        <Route path="/admin" element={<SecuriteAdminPanel />} />
                        <Route path="/admin/structure" element={<StructureAdminPanel />} />
                        <Route path="/admin/paie-config" element={<PaieConfigAdminPanel />} />
                    </Route>

                    {/* Page d'erreur 404 - Wildcard */}
                    <Route path="*" element={<NotFound />} />
                </Routes>
            </Suspense>
        </AuthProvider>
    );
}