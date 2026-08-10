import { Routes, Route } from "react-router-dom";
import Login from "@/pages/auth/Login";
import Dashboard from "@/pages/dashboard/Dashboard";
import EmployeList from "@/pages/employe/EmployeList";
import SecuriteAdminPanel from "@/pages/parametres/securite/SecuriteAdminPanel";
import StructureAdminPanel from "@/pages/parametres/structure/StructureAdminPanel";
import PaieConfigAdminPanel from "@/pages/parametres/paie/PaieConfigAdminPanel";
import Unauthorized from "@/pages/systeme/Unauthorized";
import NotFound from "@/pages/systeme/NotFound";
import ProtectedRoute from "@/routes/ProtectedRoute";
import { AuthProvider } from "@/context/AuthContext";
import EmployeFicheDetailPage from "@/pages/employe/EmployeFicheDetailPage";

export default function Router() {
    return (
        <AuthProvider>
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
        </AuthProvider>
    );
}