import { useState, useEffect, useCallback, useRef } from 'react';
import congeService from '@/features/leave/api/congeService';
import { useDebounce } from '@/hooks/useDebounce';
import { useToast } from '@/components/ui/feedback/Toast';
import { getErrorMessage } from '@/api/apiClient';

export function useDemandesValidation({ onReload: parentReload } = {}) {
  // Contexte
  const toast = useToast();

  // État local : filtres, pagination et données serveur.
  const [activeStatut,       setActiveStatut]       = useState('EN_ATTENTE');
  const [selectedTypeConge,  setSelectedTypeConge]  = useState('');
  const [searchRaw,          setSearchRaw]          = useState('');
  
  // Pagination & Données
  const [demandes, setDemandes] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const requestSequence = useRef(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Debounce de 350 ms
  const searchTerm = useDebounce(searchRaw, 350);

  // Chargement serveur et actions exposées au composant.
  const loadData = useCallback(async () => {
    const requestId = ++requestSequence.current;
    setLoading(true);
    setError(null);
    try {
      const params = {
        page: currentPage,
        size: pageSize
      };
      if (activeStatut !== 'ALL') params.statut = activeStatut;
      if (selectedTypeConge) params.idTypeConge = selectedTypeConge;
      if (searchTerm) params.q = searchTerm;

      const data = await congeService.getAllDemandes(params);
      if (requestId !== requestSequence.current) return false;
      setDemandes(data?.content || []);
      setTotalElements(data?.totalElements ?? 0);
      return true;
    } catch (err) {
      if (requestId !== requestSequence.current) return false;
      console.error('Erreur de chargement:', err);
      setDemandes([]);
      setTotalElements(0);
      setError(getErrorMessage(err, 'Impossible de charger les demandes de congés.'));
      return false;
    } finally {
      if (requestId === requestSequence.current) setLoading(false);
    }
  }, [currentPage, pageSize, activeStatut, selectedTypeConge, searchTerm]);

  // Effets : chargement, remise à zéro de pagination et garde de pagination.
  useEffect(() => {
    loadData();
    return () => { requestSequence.current += 1; };
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
    if (await loadData()) {
      toast({ title: 'Actualisé', message: 'La liste des demandes a été mise à jour.', type: 'info', duration: 2000 });
    }
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

  const handleAnnuler = async (idDemandeConge, motif) => {
    await congeService.annulerDemande(idDemandeConge, motif);
    if (parentReload) parentReload();
    await loadData();
  };

  return {
    // Data
    demandes,
    totalElements,
    loading,
    error,
    
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
    handleRefuser,
    handleAnnuler
  };
}
