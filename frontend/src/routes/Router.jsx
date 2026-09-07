import { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import ProtectedRoute from "@/routes/ProtectedRoute";
import { AuthProvider } from "@/context/AuthContext";
import { MenuProvider } from "@/context/MenuContext";
import Layout from "@/components/layout/Layout";
import Loading from "@/components/ui/feedback/Loading";

// =========================================================================
// Lazy loading des pages (Code-Splitting)
// =========================================================================

// Page publique & Authentification
const Login = lazy(() => import("@/pages/auth/Login"));

// Module Dashboard
const Dashboard = lazy(() => import("@/pages/dashboard/Dashboard"));

// Module Pointage & Présence
const GestionPointagesPage = lazy(() => import("@/pages/pointage/GestionPointagesPage"));

// Module Congés & Absences
const GestionCongesPage = lazy(() => import("@/pages/conges/GestionCongesPage"));

// Module Paie & Rémunération
const GestionPaiePage = lazy(() => import("@/pages/paie/GestionPaiePage"));

// Module Employés
const EmployeList = lazy(() => import("@/pages/employe/EmployeList"));
const EmployeFicheDetailPage = lazy(() => import("@/pages/employe/EmployeFicheDetailPage"));
const CarrieresSupervisionPage = lazy(() => import("@/pages/employe/CarrieresSupervisionPage"));

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
            <MenuProvider>
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
                    <Route element={<ProtectedRoute requiredPermission="EMPLOYE_READ" />}>
                        <Route path="/dashboard" element={<Dashboard />} />
                    </Route>

                    {/* ---------------------------------------------------- */}
                    {/* Module Congés & Absences                             */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute requiredPermission="CONGE_READ" />}>
                        <Route path="/conges" element={<GestionCongesPage />} />
                        <Route path="/conges/*" element={<GestionCongesPage />} />
                    </Route>

                    {/* ---------------------------------------------------- */}
                    {/* Module Pointage & Présence (Admin & Agent Pointage)  */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute requiredPermission="POINTAGE_READ" allowedRoles={["ROLE_ADMIN", "ROLE_AGENT_POINTAGE"]} />}>
                        <Route path="/pointages" element={<GestionPointagesPage />} />
                        <Route path="/pointages/saisie" element={<Navigate to="/pointages?tab=saisie" replace />} />
                        <Route path="/pointages/anomalies" element={<Navigate to="/pointages?tab=anomalies" replace />} />
                        <Route path="/pointages/synthese" element={<Navigate to="/pointages?tab=synthese" replace />} />
                        <Route path="/pointages/periodes" element={<Navigate to="/paie?tab=periodes" replace />} />
                    </Route>

                    {/* ---------------------------------------------------- */}
                    {/* Module Paie & Rémunération (Admin DAF)              */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute requiredPermission="PAIE_READ" allowedRoles={["ROLE_ADMIN"]} />}>
                        <Route path="/paie" element={<GestionPaiePage />} />
                        <Route path="/paie/periodes" element={<Navigate to="/paie?tab=periodes" replace />} />
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
                        <Route path="/carrieres" element={<CarrieresSupervisionPage />} />
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
            </MenuProvider>
        </AuthProvider>
    );
}   