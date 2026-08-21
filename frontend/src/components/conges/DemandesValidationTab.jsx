import { useState, lazy, Suspense } from 'react';
import { useToast } from '@/components/ui/feedback/Toast';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

// Hooks
import { useDemandesValidation } from '@/hooks/conges/useDemandesValidation';

// Sous-composants
import ValidationFilters from './validation/ValidationFilters';
import ValidationTable from './validation/ValidationTable';
import ValidationModals from './validation/ValidationModals';

// Modales asynchrones
const DemandeCongeModal = lazy(() => import('@/components/conges/DemandeCongeModal'));

export default function DemandesValidationTab({ referentiels, onReload: parentReload }) {
  const toast = useToast();

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
    searchRaw,
    setSearchRaw,
    searchTerm,
    handleRefresh,
    handleValider,
    handleRefuser,
    loadData
  } = useDemandesValidation({ onReload: parentReload });

  // 2. Gestion d'états locaux purs de l'UI (modales)
  const [showDemandeModal, setShowDemandeModal] = useState(false);
  const [selectedDemande, setSelectedDemande] = useState(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showValiderModal, setShowValiderModal] = useState(false);
  const [showRefusModal, setShowRefusModal] = useState(false);
  const [motifRefus, setMotifRefus] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState(null);

  // Handlers pour les modales
  const openValider = (d) => { setSelectedDemande(d); setActionError(null); setShowValiderModal(true); };
  const openRefus = (d) => { setSelectedDemande(d); setActionError(null); setMotifRefus(''); setShowRefusModal(true); };
  const openDetail = (d) => { setSelectedDemande(d); setShowDetailModal(true); };
  const openModifier = (d) => { setSelectedDemande(d); setShowDemandeModal(true); };
  const closeDetail = () => { setShowDetailModal(false); setSelectedDemande(null); };

  const onConfirmValider = async (justificatifFourni) => {
    if (!selectedDemande) return;
    try {
      setActionLoading(true);
      setActionError(null);
      await handleValider(selectedDemande.idDemandeConge, { justificatifFourni });
      toast({ title: 'Demande validée', message: `La demande #${selectedDemande.idDemandeConge} a été validée.`, type: 'success', duration: 3000 });
      setShowValiderModal(false);
      setSelectedDemande(null);
    } catch (err) {
      setActionError(err?.response?.data || err.message || 'Erreur lors de la validation.');
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
      setActionError(err?.response?.data || err.message || 'Erreur lors du refus.');
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
    <div className="space-y-4">
      {/* Filtres */}
      <ValidationFilters
        activeStatut={activeStatut}
        setActiveStatut={setActiveStatut}
        displayTotal={totalElements}
        searchRaw={searchRaw}
        setSearchRaw={setSearchRaw}
        searchTerm={searchTerm}
        loading={loading}
        onRefresh={handleRefresh}
        onNewDemande={() => { setSelectedDemande(null); setShowDemandeModal(true); }}
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
        onOpenDetail={openDetail}
        onOpenRefus={openRefus}
        onOpenValider={openValider}
      />

      {/* Toutes les Modales d'action */}
      <ValidationModals
        selectedDemande={selectedDemande}

        showDetailModal={showDetailModal}
        onCloseDetail={closeDetail}
        onOpenValider={openValider}
        onOpenRefus={openRefus}
        onOpenModifier={openModifier}

        showValiderModal={showValiderModal}
        onCloseValider={() => { setShowValiderModal(false); setSelectedDemande(null); }}
        onConfirmValider={onConfirmValider}

        showRefusModal={showRefusModal}
        onCloseRefus={() => { setShowRefusModal(false); setSelectedDemande(null); setMotifRefus(''); }}
        onConfirmRefus={onConfirmRefus}
        motifRefus={motifRefus}
        setMotifRefus={setMotifRefus}

        actionLoading={actionLoading}
        actionError={actionError}
      />

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
