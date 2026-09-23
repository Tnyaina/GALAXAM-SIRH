import { useState, lazy, Suspense } from 'react';
import { useToast } from '@/components/ui/feedback/Toast';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { getErrorMessage } from '@/api/apiClient';

// Hooks
import { useAuth } from '@/context/AuthContext';
import { useDemandesValidation } from '@/features/leave/hooks/useDemandesValidation';

// Sous-composants
import ValidationFilters from './validation/ValidationFilters';
import ValidationTable from './validation/ValidationTable';

// Modales asynchrones
const DemandeCongeModal = lazy(() => import('@/features/leave/components/DemandeCongeModal'));
const ValidationModals = lazy(() => import('./validation/ValidationModals'));

export default function DemandesValidationTab({ referentiels, onReload: parentReload }) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canValidate = hasPermission('CONGE_VALIDATE');
  const canWrite = hasPermission('CONGE_WRITE');

  // 1. Initialisation du Hook Logique
  const {
    demandes,
    totalElements,
    loading,
    currentPage,
    setCurrentPage,
    pageSize,
    setPageSize,
    activeStatut,
    setActiveStatut,
    selectedTypeConge,
    setSelectedTypeConge,
    searchRaw,
    setSearchRaw,
    searchTerm,
    handleRefresh,
    handleValider,
    handleRefuser,
    handleAnnuler,
    loadData
  } = useDemandesValidation({ onReload: parentReload });

  // 2. Gestion d'états locaux purs de l'UI (modales)
  const [showDemandeModal, setShowDemandeModal] = useState(false);
  const [selectedDemande, setSelectedDemande] = useState(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showValiderModal, setShowValiderModal] = useState(false);
  const [showRefusModal, setShowRefusModal] = useState(false);
  const [showAnnulerModal, setShowAnnulerModal] = useState(false);
  const [motifRefus, setMotifRefus] = useState('');
  const [motifAnnulation, setMotifAnnulation] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState(null);

  // Handlers pour les modales
  const openValider = (d) => { setSelectedDemande(d); setActionError(null); setShowValiderModal(true); };
  const openRefus = (d) => { setSelectedDemande(d); setActionError(null); setMotifRefus(''); setShowRefusModal(true); };
  const openAnnuler = (d) => { setSelectedDemande(d); setActionError(null); setMotifAnnulation(''); setShowAnnulerModal(true); };
  const openDetail = (d) => { setSelectedDemande(d); setShowDetailModal(true); };
  const openModifier = (d) => { setSelectedDemande(d); setShowDemandeModal(true); };
  const closeDetail = () => { setShowDetailModal(false); setSelectedDemande(null); };

  const onConfirmValider = async (justificatifFourni, estRemunere, derogationSoldeNegatif) => {
    if (!selectedDemande) return;
    try {
      setActionLoading(true);
      setActionError(null);
      await handleValider(selectedDemande.idDemandeConge, { justificatifFourni, estRemunere, derogationSoldeNegatif });
      toast({ title: 'Demande validée', message: `La demande #${selectedDemande.idDemandeConge} a été validée.`, type: 'success', duration: 3000 });
      setShowValiderModal(false);
      setSelectedDemande(null);
    } catch (err) {
      const msg = getErrorMessage(err, 'Erreur lors de la validation de la demande.');
      setActionError(msg);
      toast({ title: 'Validation impossible', message: msg, type: 'error', duration: 6000 });
    } finally {
      setActionLoading(false);
    }
  };

  const onConfirmRefus = async () => {
    if (!motifRefus.trim()) { setActionError('Le motif de refus est obligatoire.'); return; }
    try {
      setActionLoading(true);
      setActionError(null);
      await handleRefuser(selectedDemande.idDemandeConge, motifRefus.trim());
      toast({ title: 'Demande refusée', message: `La demande #${selectedDemande.idDemandeConge} a été refusée.`, type: 'success', duration: 3000 });
      setShowRefusModal(false);
      setSelectedDemande(null);
      setMotifRefus('');
    } catch (err) {
      setActionError(getErrorMessage(err, 'Erreur lors du refus de la demande.'));
    } finally {
      setActionLoading(false);
    }
  };

  const onConfirmAnnuler = async () => {
    if (!selectedDemande) return;
    if (!motifAnnulation.trim()) {
      setActionError("Le motif d'annulation est obligatoire.");
      return;
    }
    try {
      setActionLoading(true);
      setActionError(null);
      await handleAnnuler(selectedDemande.idDemandeConge, motifAnnulation.trim());
      toast({ title: 'Congé annulé', message: `La demande #${selectedDemande.idDemandeConge} a été annulée.`, type: 'success', duration: 3000 });
      setShowAnnulerModal(false);
      setSelectedDemande(null);
      setMotifAnnulation('');
    } catch (err) {
      setActionError(getErrorMessage(err, "Erreur lors de l'annulation de la demande."));
    } finally {
      setActionLoading(false);
    }
  };

  // 3. Rendu Conditionnel
  if (loading && demandes.length === 0) {
    return (
      <div className="space-y-4">
        <div className="h-14 bg-[var(--color-bg-subtle)]" />
        <SkeletonTable rows={8} />
      </div>
    );
  }

  // 4. Rendu Principal (Assemblage)
  return (
    <div className="space-y-5">
      {/* Filtres */}
      <ValidationFilters
        activeStatut={activeStatut}
        setActiveStatut={setActiveStatut}
        selectedTypeConge={selectedTypeConge}
        setSelectedTypeConge={setSelectedTypeConge}
        referentiels={referentiels}
        displayTotal={totalElements}
        searchRaw={searchRaw}
        setSearchRaw={setSearchRaw}
        searchTerm={searchTerm}
        loading={loading}
        onRefresh={handleRefresh}
        onNewDemande={canWrite ? () => { setSelectedDemande(null); setShowDemandeModal(true); } : null}
      />

      {/* Tableau des données */}
      <ValidationTable
        demandes={demandes}
        activeStatut={activeStatut}
        displayTotal={totalElements}
        searchTerm={searchTerm}
        currentPage={currentPage}
        pageSize={pageSize}
        setCurrentPage={setCurrentPage}
        setPageSize={setPageSize}
        canValidate={canValidate}
        onOpenDetail={openDetail}
        onOpenRefus={openRefus}
        onOpenValider={openValider}
        onOpenModifier={openModifier}
      />

      {/* Toutes les Modales d'action (Lazy Loaded) */}
      {(showDetailModal || showValiderModal || showRefusModal || showAnnulerModal) && (
        <Suspense fallback={null}>
          <ValidationModals
            selectedDemande={selectedDemande}

            showDetailModal={showDetailModal}
            onCloseDetail={closeDetail}
            onOpenValider={openValider}
            onOpenRefus={openRefus}
            onOpenModifier={openModifier}
            onOpenAnnuler={openAnnuler}

            showValiderModal={showValiderModal}
            onCloseValider={() => { setShowValiderModal(false); setSelectedDemande(null); }}
            onConfirmValider={onConfirmValider}

            showRefusModal={showRefusModal}
            onCloseRefus={() => { setShowRefusModal(false); setSelectedDemande(null); setMotifRefus(''); }}
            onConfirmRefus={onConfirmRefus}
            motifRefus={motifRefus}
            setMotifRefus={setMotifRefus}

            showAnnulerModal={showAnnulerModal}
            onCloseAnnuler={() => { setShowAnnulerModal(false); setSelectedDemande(null); setMotifAnnulation(''); }}
            onConfirmAnnuler={onConfirmAnnuler}
            motifAnnulation={motifAnnulation}
            setMotifAnnulation={setMotifAnnulation}

            actionLoading={actionLoading}
            actionError={actionError}
          />
        </Suspense>
      )}

      {/* Modale de création d'une nouvelle demande (Lazy Loaded) */}
      <Suspense fallback={null}>
        <DemandeCongeModal
          isOpen={showDemandeModal}
          onClose={() => { setShowDemandeModal(false); setSelectedDemande(null); }}
          idEmploye={null}
          initialDemande={selectedDemande}
          referentiels={referentiels}
          onSuccess={() => { setShowDemandeModal(false); setSelectedDemande(null); if (parentReload) parentReload(); loadData(); }}
        />
      </Suspense>
    </div>
  );
}
