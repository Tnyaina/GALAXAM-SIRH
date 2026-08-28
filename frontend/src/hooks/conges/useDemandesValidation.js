import { useState, useEffect, useCallback } from 'react';
import congeService from '@/api/congeService';
import { useDebounce } from '@/hooks/useDebounce';
import { useToast } from '@/components/ui/feedback/Toast';

export function useDemandesValidation({ onReload: parentReload } = {}) {
  const toast = useToast();

  // ── Filtres ────────────────────────────────────────────────────────────────
  const [activeStatut,       setActiveStatut]       = useState('EN_ATTENTE');
  const [selectedTypeConge,  setSelectedTypeConge]  = useState('');
  const [searchRaw,          setSearchRaw]          = useState('');
  
  // Pagination & Données
  const [demandes, setDemandes] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Debounce de 350 ms
  const searchTerm = useDebounce(searchRaw, 350);

  // Charger les données depuis le serveur
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page: currentPage,
        size: pageSize
      };
      if (activeStatut !== 'ALL') params.statut = activeStatut;
      if (selectedTypeConge) params.idTypeConge = selectedTypeConge;
      if (searchTerm) params.q = searchTerm;

      const data = await congeService.getAllDemandes(params);
      setDemandes(data?.content || []);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      console.error('Erreur de chargement:', err);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize, activeStatut, selectedTypeConge, searchTerm]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Réinitialiser la page à 1 si filtres changent
  useEffect(() => {
    setCurrentPage(1);
  }, [activeStatut, selectedTypeConge, searchTerm]);

  const totalPages = Math.ceil(totalElements / pageSize) || 1;

  // Sécurité ajustement : si la page courante dépasse le total de pages
  useEffect(() => {
    if (currentPage > totalPages && totalPages > 0) {
      setCurrentPage(1);
    }
  }, [totalPages, currentPage]);

  const handleRefresh = async () => {
    await loadData();
    toast({ title: 'Actualisé', message: 'La liste des demandes a été mise à jour.', type: 'info', duration: 2000 });
  };

  const handleValider = async (idDemandeConge, options = {}) => {
    await congeService.validerDemande(idDemandeConge, options);
    if (parentReload) parentReload();
    await loadData();
  };

  const handleRefuser = async (idDemandeConge, motif) => {
    await congeService.refuserDemande(idDemandeConge, motif);
    if (parentReload) parentReload();
    await loadData();
  };

  return {
    // Data
    demandes,
    totalElements,
    loading,
    
    // Pagination
    currentPage,
    setCurrentPage,
    pageSize,
    setPageSize,

    // Filters
    activeStatut,
    setActiveStatut,
    selectedTypeConge,
    setSelectedTypeConge,
    searchRaw,
    setSearchRaw,
    searchTerm,

    // Actions
    loadData,
    handleRefresh,
    handleValider,
    handleRefuser
  };
}
