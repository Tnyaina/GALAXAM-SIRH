import { lazy, Suspense } from "react";
import { Routes, Route, Navigate, useParams } from "react-router-dom";
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

// Sprint 2B — Pages d'étapes de campagne de paie
const PreparationStepPage  = lazy(() => import("@/pages/paie/PreparationStepPage"));
const PopulationStepPage   = lazy(() => import("@/pages/paie/PopulationStepPage"));
const VariablesStepPage    = lazy(() => import("@/pages/paie/VariablesStepPage"));
const CalculStepPage       = lazy(() => import("@/pages/paie/CalculStepPage"));
const ControleStepPage     = lazy(() => import("@/pages/paie/ControleStepPage"));
const ValidationStepPage   = lazy(() => import("@/pages/paie/ValidationStepPage"));
const SortiesStepPage      = lazy(() => import("@/pages/paie/SortiesStepPage"));

// Sprint UX 2 — Espaces dédiés Paie V1 Full V3
const AcomptesAvancesPage       = lazy(() => import("@/pages/paie/AcomptesAvancesPage"));
const DeclarationsSortiesPage   = lazy(() => import("@/pages/paie/DeclarationsSortiesPage"));

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

import { usePayrollCampaign } from "@/hooks/paie/usePayrollCampaign";

/**
 * Redirection automatique vers l'étape active de la campagne courante
 */
function ActiveCampaignRedirect() {
    const { context, loading } = usePayrollCampaign();
    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-[400px]">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--color-success-600)]"></div>
            </div>
        );
    }
    const targetPeriod = context?.selectedPeriod || context?.currentPeriod;
    if (!targetPeriod?.id) {
        return <Navigate to="/paie" replace />;
    }
    const step = (context?.currentStepCode || 'PREPARATION').toLowerCase();
    return <Navigate to={`/paie/periodes/${targetPeriod.id}/${step}`} replace />;
}

/** Préserve les bookmarks historiques sans les laisser tomber sur un écran vide. */
function LegacyPayrollRedirect() {
    const { periodId, step } = useParams();
    const canonicalStep = {
        preparation: 'preparation', population: 'population', variables: 'variables',
        calcul: 'calcul', calculation: 'calcul', controle: 'controle', control: 'controle',
        validation: 'validation', sorties: 'sorties', outputs: 'sorties', overview: 'preparation',
        results: 'controle',
    }[step?.toLowerCase()] || 'preparation';
    return <Navigate to={`/paie/periodes/${periodId}/${canonicalStep}`} replace />;
}

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
                        <Route path="/pointages/validation" element={<Navigate to="/pointages?tab=validation" replace />} />
                        <Route path="/pointages/arbitrage" element={<Navigate to="/pointages?tab=validation" replace />} />

                        {/* Alias singuliers de compatibilité (/pointage/*) */}
                        <Route path="/pointage" element={<Navigate to="/pointages" replace />} />
                        <Route path="/pointage/saisie" element={<Navigate to="/pointages?tab=saisie" replace />} />
                        <Route path="/pointage/anomalies" element={<Navigate to="/pointages?tab=anomalies" replace />} />
                        <Route path="/pointage/synthese" element={<Navigate to="/pointages?tab=synthese" replace />} />
                        <Route path="/pointage/validation" element={<Navigate to="/pointages?tab=validation" replace />} />
                        <Route path="/pointage/arbitrage" element={<Navigate to="/pointages?tab=validation" replace />} />
                    </Route>


                    {/* ---------------------------------------------------- */}
                    {/* Module Paie & Rémunération (Admin DAF)              */}
                    {/* ---------------------------------------------------- */}
                    <Route element={<ProtectedRoute requiredPermission="PAIE_READ" allowedRoles={["ROLE_ADMIN"]} />}>
                        {/* Page d'accueil paie : Centre de paie opérationnel */}
                        <Route path="/paie" element={<GestionPaiePage />} />
                        <Route path="/paie/campagne" element={<ActiveCampaignRedirect />} />
                        <Route path="/paie/periodes" element={<ActiveCampaignRedirect />} />
                        <Route path="/paie/acomptes-avances" element={<AcomptesAvancesPage />} />
                        <Route path="/paie/declarations" element={<DeclarationsSortiesPage />} />
                        <Route path="/paie/sorties" element={<DeclarationsSortiesPage />} />
                        {/* L'historique est une vue du registre unique des cycles, pas une seconde source de vérité. */}
                        <Route path="/paie/historique" element={<Navigate to="/paie?tab=periodes" replace />} />

                        {/* Sprint 2B & Sprint UX 2 — Pages d'étapes de campagne sous Shell invariant */}
                        <Route path="/paie/periodes/:periodId/preparation"  element={<PreparationStepPage />} />
                        <Route path="/paie/periodes/:periodId/population"   element={<PopulationStepPage />} />
                        <Route path="/paie/periodes/:periodId/variables"    element={<VariablesStepPage />} />
                        <Route path="/paie/periodes/:periodId/calcul"       element={<CalculStepPage />} />
                        <Route path="/paie/periodes/:periodId/controle"     element={<ControleStepPage />} />
                        <Route path="/paie/periodes/:periodId"              element={<Navigate to="preparation" replace />} />
                        <Route path="/paie/periodes/:periodId/validation"   element={<ValidationStepPage />} />
                        <Route path="/paie/periodes/:periodId/sorties"      element={<SortiesStepPage />} />

                        {/* Redirections de compatibilité (anciennes URLs) */}
                        <Route path="/paie/periods/:periodId/:step/:employeeId" element={<LegacyPayrollRedirect />} />
                        <Route path="/paie/periods/:periodId/:step" element={<LegacyPayrollRedirect />} />
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
                        <Route path="/employes/:id/:section" element={<EmployeFicheDetailPage />} />
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

                        {/* Alias et redirections de compatibilité pour /parametres */}
                        <Route path="/parametres" element={<Navigate to="/admin/organisation" replace />} />
                        <Route path="/parametres/organisation" element={<Navigate to="/admin/organisation" replace />} />
                        <Route path="/parametres/referentiels" element={<Navigate to="/admin/referentiels-rh" replace />} />
                        <Route path="/parametres/securite" element={<Navigate to="/admin/securite" replace />} />
                        <Route path="/parametres/modules" element={<Navigate to="/admin/parametres" replace />} />
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
