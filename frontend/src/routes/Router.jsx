import { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import ProtectedRoute from "@/routes/ProtectedRoute";
import { AuthProvider } from "@/context/AuthContext";
import Layout from "@/components/layout/Layout";
import Loading from "@/components/ui/feedback/Loading";

// =========================================================================
// Lazy loading des pages (Code-Splitting)
// =========================================================================

// Page publique & Authentification
const Login = lazy(() => import("@/pages/auth/Login"));

// Module Dashboard
const Dashboard = lazy(() => import("@/pages/dashboard/Dashboard"));

// Module Pointage
const Pointages = lazy(() => import("@/pages/pointage/Pointages"));

// Module Congés & Absences
const GestionCongesPage = lazy(() => import("@/pages/conges/GestionCongesPage"));

// Module Employés
const EmployeList = lazy(() => import("@/pages/employe/EmployeList"));
const EmployeFicheDetailPage = lazy(() => import("@/pages/employe/EmployeFicheDetailPage"));

// Administration / Paramètres
const SecuriteAdminPanel = lazy(() => import("@/pages/parametres/securite/SecuriteAdminPanel"));
const OrganisationAdminPanel = lazy(() => import("@/pages/parametres/organisation/OrganisationAdminPanel"));
const ReferentielsRhAdminPanel = lazy(() => import("@/pages/parametres/referentiels/ReferentielsRhAdminPanel"));
const ParametresModulesPanel = lazy(() => import("@/pages/parametres/modules/ParametresModulesPanel"));

// Pages Système & Erreurs
const Unauthorized = lazy(() => import("@/pages/systeme/Unauthorized"));
const NotFound = lazy(() => import("@/pages/systeme/NotFound"));
const ServerError = lazy(() => import("@/pages/systeme/ServerError"));
const Maintenance = lazy(() => import("@/pages/systeme/Maintenance"));
const NoConnection = lazy(() => import("@/pages/systeme/NoConnection"));

// =========================================================================
// Composant Principal Router
// =========================================================================

export default function Router() {
    return (
        <AuthProvider>
            {/* Fallback de chargement global pendant le téléchargement des chunks JS */}
            <Suspense fallback={<Loading display="fullscreen" size="lg" label="Chargement de la page..." />}>
                <Routes>
                    {/* ---------------------------------------------------- */}
                    {/* Routes Publiques / Hors Session                      */}
                    {/* ---------------------------------------------------- */}
                    <Route path="/" element={<Login />} />
                    <Route path="/maintenance" element={<Maintenance />} />
                    <Route path="/hors-ligne" element={<NoConnection />} />
                    <Route path="/erreur-serveur" element={<ServerError />} />

                    {/* ---------------------------------------------------- */}
                    {/* Routes Protégées Standard (Authentifié + Layout)    */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute allowedRoles={["ROLE_ADMIN"]} />}>
                        <Route path="/dashboard" element={<Dashboard />} />
                    </Route>

                    {/* ---------------------------------------------------- */}
                    {/* Module Congés & Absences                             */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute allowedRoles={["ROLE_ADMIN"]} />}>
                        <Route path="/conges" element={<GestionCongesPage />} />
                        <Route path="/conges/*" element={<GestionCongesPage />} />
                    </Route>

                    {/* ---------------------------------------------------- */}
                    {/* Module Pointage (Rôles requis)                      */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute allowedRoles={["ROLE_ADMIN", "ROLE_AGENT_POINTAGE"]} />}>
                        <Route path="/pointages" element={<Pointages />} />
                    </Route>

                    <Route element={<ProtectedRoute />}>
                        {/* 403 à l'intérieur du Layout pour garder le menu latéral */}
                        <Route path="/non-autorise" element={<Unauthorized />} />
                    </Route>

                    {/* ---------------------------------------------------- */}
                    {/* Module Employé (Permission EMPLOYE_READ)             */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute requiredPermission="EMPLOYE_READ" />}>
                        <Route path="/employes" element={<EmployeList />} />
                        <Route path="/employes/:id" element={<EmployeFicheDetailPage />} />
                    </Route>

                    {/* ---------------------------------------------------- */}
                    {/* Module Admin (Rôle ROLE_ADMIN requis)               */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute allowedRoles={["ROLE_ADMIN"]} />}>
                        <Route path="/admin" element={<Navigate to="/admin/securite" replace />} />
                        <Route path="/admin/securite" element={<SecuriteAdminPanel />} />
                        <Route path="/admin/organisation" element={<OrganisationAdminPanel />} />
                        <Route path="/admin/referentiels-rh" element={<ReferentielsRhAdminPanel />} />
                        <Route path="/admin/parametres" element={<ParametresModulesPanel />} />
                    </Route>

                    {/* ---------------------------------------------------- */}
                    {/* Page 404 (Introuvable)                               */}
                    {/* Enveloppée dans Layout (S'adapte si connecté ou non) */}
                    {/* ---------------------------------------------------- */}
                    <Route
                        path="*"
                        element={
                            <Layout>
                                <NotFound />
                            </Layout>
                        }
                    />
                </Routes>
            </Suspense>
        </AuthProvider>
    );
}   