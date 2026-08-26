import { useState, useEffect, useMemo, useCallback } from 'react';
import pointageParametreService from '@/api/admin/pointageParametreService';
import paieConfigService from '@/api/admin/paieConfigService';
import employeService from '@/api/employe/employeService';
import { useToast } from '@/components/ui/feedback/Toast';
import { fmtDateInput } from '@/utils/paieUtils';

export function useDerogationIndemnites() {
    const todayStr = fmtDateInput(new Date());

    const [derogations, setDerogations] = useState([]);
    const [indemnites, setIndemnites] = useState([]);
    const [employes, setEmployes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Filtres & Recherche
    const [searchTerm, setSearchTerm] = useState('');
    const [indemniteFilter, setIndemniteFilter] = useState('ALL');

    // Modale
    const [showModal, setShowModal] = useState(false);
    const [editingDerogation, setEditingDerogation] = useState(null);
    const [formData, setFormData] = useState({
        idEmploye: '',
        idIndemnite: '',
        beneficie: false,
        motif: '',
        dateDebut: todayStr,
        dateFin: ''
    });
    const [submitting, setSubmitting] = useState(false);

    const toast = useToast();

    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const [derogData, indData, empData] = await Promise.all([
                pointageParametreService.getDerogations(),
                paieConfigService.getIndemnites(),
                employeService.getEmployes({ size: 1000, actif: true })
            ]);
            const empList = Array.isArray(empData) ? empData : (empData?.content || []);
            setDerogations(derogData || []);
            setIndemnites(indData || []);
            setEmployes(empList);
        } catch (err) {
            setError("Erreur lors du chargement des dérogations d'indemnités.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const filteredDerogations = useMemo(() => {
        return derogations.filter(d => {
            const matchSearch = searchTerm === '' || 
                (d.matriculeEmploye && d.matriculeEmploye.toLowerCase().includes(searchTerm.toLowerCase())) ||
                (d.nomPrenomEmploye && d.nomPrenomEmploye.toLowerCase().includes(searchTerm.toLowerCase())) ||
                (d.motif && d.motif.toLowerCase().includes(searchTerm.toLowerCase()));
            
            const matchIndemnite = indemniteFilter === 'ALL' || String(d.idIndemnite) === String(indemniteFilter);

            return matchSearch && matchIndemnite;
        });
    }, [derogations, searchTerm, indemniteFilter]);

    const openModal = (derogation = null) => {
        setEditingDerogation(derogation);
        if (derogation) {
            setFormData({
                idEmploye: String(derogation.idEmploye),
                idIndemnite: String(derogation.idIndemnite),
                beneficie: derogation.beneficie,
                motif: derogation.motif || '',
                dateDebut: derogation.dateDebut ? derogation.dateDebut.substring(0, 10) : todayStr,
                dateFin: derogation.dateFin ? derogation.dateFin.substring(0, 10) : ''
            });
        } else {
            setFormData({
                idEmploye: employes.length > 0 ? String(employes[0].idEmploye) : '',
                idIndemnite: indemnites.length > 0 ? String(indemnites[0].idIndemnite) : '',
                beneficie: false,
                motif: '',
                dateDebut: todayStr,
                dateFin: ''
            });
        }
        setShowModal(true);
    };

    const closeModal = () => {
        setShowModal(false);
        setEditingDerogation(null);
    };

    const saveDerogation = async (e) => {
        e.preventDefault();
        if (!formData.idEmploye || !formData.idIndemnite || !formData.dateDebut) {
            toast.error("Veuillez remplir les champs obligatoires (Employé, Indemnité, Date de début).");
            return;
        }

        setSubmitting(true);
        try {
            const payload = {
                idEmploye: parseInt(formData.idEmploye),
                idIndemnite: parseInt(formData.idIndemnite),
                beneficie: false, // Toujours une exclusion d'indemnité
                motif: formData.motif,
                dateDebut: formData.dateDebut,
                dateFin: formData.dateFin || null
            };

            if (editingDerogation) {
                await pointageParametreService.updateDerogation(editingDerogation.idDerogation, payload);
                toast({ type: 'success', message: "Dérogation modifiée avec succès." });
            } else {
                await pointageParametreService.createDerogation(payload);
                toast({ type: 'success', message: "Dérogation créée avec succès." });
            }

            closeModal();
            await loadData();
        } catch (err) {
            toast({ type: 'error', message: err?.response?.data || "Erreur lors de l'enregistrement de la dérogation." });
        } finally {
            setSubmitting(false);
        }
    };

    const deleteDerogation = async (id) => {
        if (!window.confirm("Êtes-vous sûr de vouloir supprimer cette dérogation d'indemnité ?")) {
            return;
        }
        try {
            await pointageParametreService.deleteDerogation(id);
            toast({ type: 'success', message: "Dérogation supprimée avec succès." });
            await loadData();
        } catch {
            toast({ type: 'error', message: "Erreur lors de la suppression." });
        }
    };

    return {
        derogations: filteredDerogations,
        rawDerogationsCount: derogations.length,
        indemnites,
        employes,
        loading,
        error,
        searchTerm,
        setSearchTerm,
        indemniteFilter,
        setIndemniteFilter,
        showModal,
        editingDerogation,
        formData,
        setFormData,
        submitting,
        openModal,
        closeModal,
        saveDerogation,
        deleteDerogation,
        reload: loadData
    };
}
