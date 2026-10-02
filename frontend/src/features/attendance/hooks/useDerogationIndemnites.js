import { useState, useEffect, useMemo, useCallback } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import pointageParametreService from '@/features/administration/api/pointageParametreService';
import paieConfigService from '@/features/administration/api/paieConfigService';
import { useToast } from '@/components/ui/feedback/Toast';
import { fmtDateInput } from '@/utils/dateUtils';

export function useDerogationIndemnites() {
    // Données dérivées et contexte
    const todayStr = fmtDateInput(new Date());

    // État local : données serveur, filtres et formulaire.
    const [derogations, setDerogations] = useState([]);
    const [indemnites, setIndemnites] = useState([]);
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
    const [fieldErrors, setFieldErrors] = useState({});
    const [actionError, setActionError] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const { toast } = useToast();

    // Chargement et actions exposées au composant propriétaire.
    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const [derogData, indData] = await Promise.all([
                pointageParametreService.getDerogations(),
                paieConfigService.getIndemnites(),
            ]);
            setDerogations(derogData || []);
            setIndemnites(indData || []);
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
        setFieldErrors({});
        setActionError(null);
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
                idEmploye: '',
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
        setFieldErrors({});
        setActionError(null);
    };

    const saveDerogation = async (e) => {
        e.preventDefault();
        setActionError(null);

        const errors = {};
        if (!formData.idEmploye) {
            errors.idEmploye = "Veuillez sélectionner un employé.";
        }
        if (!formData.idIndemnite) {
            errors.idIndemnite = "Veuillez sélectionner une indemnité.";
        }
        if (!formData.dateDebut) {
            errors.dateDebut = "La date de début est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setFieldErrors(errors);
            return;
        }
        setFieldErrors({});

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
                toast({ title: 'Succès', message: "Dérogation modifiée avec succès.", type: 'success' });
            } else {
                await pointageParametreService.createDerogation(payload);
                toast({ title: 'Succès', message: "Dérogation créée avec succès.", type: 'success' });
            }

            closeModal();
            await loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur lors de l'enregistrement de la dérogation."));
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
        fieldErrors,
        setFieldErrors,
        actionError,
        setActionError,
        openModal,
        closeModal,
        saveDerogation,
        deleteDerogation,
        reload: loadData
    };
}
