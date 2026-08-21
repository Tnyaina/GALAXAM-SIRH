import { useState, useEffect } from 'react';
import congeService from '@/api/congeService';

const formatDateForInput = (val) => {
  if (!val) return '';
  if (typeof val === 'string' && /^\d{4}-\d{2}-\d{2}/.test(val)) {
    return val.substring(0, 10);
  }
  const d = new Date(val);
  if (isNaN(d.getTime())) return '';
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

export function useDemandeCongeForm({ idEmployeProp, initialDemande, referentiels, isOpen, onClose, onSuccess }) {
  const [selectedEmployeId, setSelectedEmployeId] = useState('');
  const [idTypeConge, setIdTypeConge] = useState('');
  const [idMotifEvenementFamilial, setIdMotifEvenementFamilial] = useState('');
  const [dateDebut, setDateDebut] = useState('');
  const [dateFin, setDateFin] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [justificatifFourni, setJustificatifFourni] = useState(false);

  // Demi-journée
  const [estDemiJournee, setEstDemiJournee] = useState(false);
  const [momentDemiJournee, setMomentDemiJournee] = useState('MATIN');

  // Dérogations RH
  const [derogationSoldeNegatif, setDerogationSoldeNegatif] = useState(false);
  const [derogationDelaiPreavis, setDerogationDelaiPreavis] = useState(false);
  const [extensionPathologiqueMaternite, setExtensionPathologiqueMaternite] = useState(false);
  const [showOptionsAvancees, setShowOptionsAvancees] = useState(false);

  // Simulation & State
  const [nbJoursDecomptes, setNbJoursDecomptes] = useState(0);
  const [nbJoursWeekEnd, setNbJoursWeekEnd] = useState(0);
  const [nbJoursOuvres, setNbJoursOuvres] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  const typesConge = referentiels?.typesConge || [];
  const motifsFamiliaux = referentiels?.motifsFamiliaux || [];

  const selectedType = typesConge.find((t) => String(t.idTypeConge) === String(idTypeConge));
  const isExceptionnel = selectedType && selectedType.code === 'EXCEPTIONNEL';
  const isMaternite = selectedType && selectedType.code === 'MATERNITE';

  useEffect(() => {
    if (isOpen) {
      if (initialDemande) {
        const empId = initialDemande.idEmploye || initialDemande.idEmployeConcerne || idEmployeProp || '';
        setSelectedEmployeId(empId ? String(empId) : '');
        setIdTypeConge(initialDemande.idTypeConge ? String(initialDemande.idTypeConge) : '');
        setIdMotifEvenementFamilial(initialDemande.idMotifEvenementFamilial ? String(initialDemande.idMotifEvenementFamilial) : '');
        setDateDebut(formatDateForInput(initialDemande.dateDebut));
        setDateFin(formatDateForInput(initialDemande.dateFin));
        setCommentaire(initialDemande.commentaire || '');
        setJustificatifFourni(!!initialDemande.justificatifFourni);
        setEstDemiJournee(!!initialDemande.estDemiJournee);
        setMomentDemiJournee(initialDemande.momentDemiJournee || 'MATIN');
        setDerogationSoldeNegatif(!!initialDemande.derogationSoldeNegatif);
        setDerogationDelaiPreavis(!!initialDemande.derogationDelaiPreavis);
        setExtensionPathologiqueMaternite(!!initialDemande.extensionPathologiqueMaternite);
        if (initialDemande.derogationSoldeNegatif || initialDemande.derogationDelaiPreavis || initialDemande.extensionPathologiqueMaternite) {
          setShowOptionsAvancees(true);
        } else {
          setShowOptionsAvancees(false);
        }
      } else {
        setSelectedEmployeId(idEmployeProp ? String(idEmployeProp) : '');
        setIdTypeConge('');
        setIdMotifEvenementFamilial('');
        setDateDebut('');
        setDateFin('');
        setCommentaire('');
        setJustificatifFourni(false);
        setEstDemiJournee(false);
        setMomentDemiJournee('MATIN');
        setDerogationSoldeNegatif(false);
        setDerogationDelaiPreavis(false);
        setExtensionPathologiqueMaternite(false);
        setShowOptionsAvancees(false);
      }
      setError(null);
      setFieldErrors({});
    }
  }, [isOpen, idEmployeProp, initialDemande]);

  // Si demi-journée activée, la date de fin doit égaler la date de début
  useEffect(() => {
    if (estDemiJournee && dateDebut) {
      setDateFin(dateDebut);
    }
  }, [estDemiJournee, dateDebut]);

  // Pré-remplissage des durées fixes légales (ex: Paternité 3j, Maternité 98j, Événements familiaux)
  useEffect(() => {
    if (estDemiJournee) return;

    let nbJoursFixes = null;

    if (selectedType && selectedType.dureeFixeJours) {
      nbJoursFixes = Number(selectedType.dureeFixeJours);
      if (isMaternite && extensionPathologiqueMaternite) {
        nbJoursFixes = 119;
      }
    } else if (isExceptionnel && idMotifEvenementFamilial) {
      const motif = motifsFamiliaux.find((m) => String(m.idMotif) === String(idMotifEvenementFamilial));
      if (motif && motif.nbJoursAutorises) {
        nbJoursFixes = Number(motif.nbJoursAutorises);
      }
    }

    if (nbJoursFixes && dateDebut) {
      const dDebut = new Date(dateDebut);
      const dFin = new Date(dDebut);
      dFin.setDate(dFin.getDate() + nbJoursFixes - 1);
      setDateFin(dFin.toISOString().split('T')[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idTypeConge, idMotifEvenementFamilial, dateDebut, extensionPathologiqueMaternite, estDemiJournee]);

  // Détection automatique de régularisation / saisie rétroactive (date de début dans le passé)
  useEffect(() => {
    if (dateDebut) {
      const dDebut = new Date(dateDebut);
      const today = new Date();
      dDebut.setHours(0, 0, 0, 0);
      today.setHours(0, 0, 0, 0);
      if (dDebut < today) {
        setDerogationDelaiPreavis(true);
      }
    }
  }, [dateDebut]);

  // Simulation automatique des jours calendaires stricts + validation inline des dates
  useEffect(() => {
    if (estDemiJournee) {
      if (dateDebut) {
        setNbJoursDecomptes(0.5);
        setNbJoursWeekEnd(0);
        setNbJoursOuvres(0.5);
        setFieldErrors((prev) => ({ ...prev, dateFin: null }));
      } else {
        setNbJoursDecomptes(0);
        setNbJoursWeekEnd(0);
        setNbJoursOuvres(0);
      }
      return;
    }

    if (dateDebut && dateFin) {
      const dDebut = new Date(dateDebut);
      const dFin = new Date(dateFin);
      if (dFin >= dDebut) {
        const diffTime = dFin.getTime() - dDebut.getTime();
        const diffDays = Math.ceil(diffTime / (1000 * 3600 * 24)) + 1;

        let countWeekends = 0;
        let currentDate = new Date(dDebut);
        while (currentDate <= dFin) {
          const day = currentDate.getDay(); // 0 is Sunday, 6 is Saturday
          if (day === 0 || day === 6) {
            countWeekends++;
          }
          currentDate.setDate(currentDate.getDate() + 1);
        }

        setNbJoursDecomptes(diffDays);
        setNbJoursWeekEnd(countWeekends);
        setNbJoursOuvres(diffDays - countWeekends);
        setFieldErrors((prev) => ({ ...prev, dateFin: null }));
      } else {
        setNbJoursDecomptes(0);
        setNbJoursWeekEnd(0);
        setNbJoursOuvres(0);
        setFieldErrors((prev) => ({ ...prev, dateFin: 'La date de fin doit être postérieure ou égale à la date de début.' }));
      }
    } else {
      setNbJoursDecomptes(0);
      setNbJoursWeekEnd(0);
      setNbJoursOuvres(0);
    }
  }, [dateDebut, dateFin, estDemiJournee]);

  const handleSubmit = async (e, showToast) => {
    e.preventDefault();

    const targetEmployeId = idEmployeProp || selectedEmployeId;
    const nextFieldErrors = {};
    if (!targetEmployeId) nextFieldErrors.idEmploye = 'Sélectionnez un employé.';
    if (!idTypeConge) nextFieldErrors.idTypeConge = 'Sélectionnez un type de congé.';
    if (!dateDebut) nextFieldErrors.dateDebut = 'Renseignez la date de début.';
    if (!dateFin) nextFieldErrors.dateFin = 'Renseignez la date de fin.';
    if (isExceptionnel && !idMotifEvenementFamilial) {
      nextFieldErrors.idMotifEvenementFamilial = "Sélectionnez un motif d'événement familial.";
    }
    if (estDemiJournee && !momentDemiJournee) {
      nextFieldErrors.momentDemiJournee = "Sélectionnez le moment de la demi-journée.";
    }

    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors((prev) => ({ ...prev, ...nextFieldErrors }));
      setError('Veuillez corriger les champs indiqués ci-dessous avant de continuer.');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const isRetroactive = dateDebut && (new Date(dateDebut).setHours(0,0,0,0) < new Date().setHours(0,0,0,0));
      const effectiveDerogationPreavis = derogationDelaiPreavis || isRetroactive;

      const payload = {
        idEmploye: parseInt(targetEmployeId),
        idTypeConge: parseInt(idTypeConge),
        idMotifEvenementFamilial: idMotifEvenementFamilial ? parseInt(idMotifEvenementFamilial) : null,
        dateDebut,
        dateFin: estDemiJournee ? dateDebut : dateFin,
        estDemiJournee,
        momentDemiJournee: estDemiJournee ? momentDemiJournee : null,
        commentaire,
        justificatifFourni,
        derogationSoldeNegatif,
        derogationDelaiPreavis: effectiveDerogationPreavis,
        extensionPathologiqueMaternite,
      };

      const isEditMode = !!initialDemande?.idDemandeConge;
      if (isEditMode) {
        await congeService.updateDemande(initialDemande.idDemandeConge, payload);
        showToast({
          title: 'Demande modifiée',
          message: 'La demande de congé a été mise à jour avec succès.',
          type: 'success',
          duration: 3000
        });
      } else {
        await congeService.creerDemande(payload);
        showToast({
          title: 'Demande soumise',
          message: 'Votre demande de congé a été enregistrée avec succès.',
          type: 'success',
          duration: 3000
        });
      }
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      const message = err?.response?.data?.message || err?.response?.data || err.message
        || "Impossible d'enregistrer la demande. Veuillez réessayer.";
      setError(typeof message === 'string' ? message : "Impossible d'enregistrer la demande. Veuillez réessayer.");
    } finally {
      setLoading(false);
    }
  };

  const inputErrorClass = (hasError) =>
    hasError ? 'border-danger-500 focus:border-danger-500' : '';

  return {
    selectedEmployeId, setSelectedEmployeId,
    idTypeConge, setIdTypeConge,
    idMotifEvenementFamilial, setIdMotifEvenementFamilial,
    dateDebut, setDateDebut,
    dateFin, setDateFin,
    estDemiJournee, setEstDemiJournee,
    momentDemiJournee, setMomentDemiJournee,
    commentaire, setCommentaire,
    justificatifFourni, setJustificatifFourni,
    
    derogationSoldeNegatif, setDerogationSoldeNegatif,
    derogationDelaiPreavis, setDerogationDelaiPreavis,
    extensionPathologiqueMaternite, setExtensionPathologiqueMaternite,
    showOptionsAvancees, setShowOptionsAvancees,

    nbJoursDecomptes, nbJoursWeekEnd, nbJoursOuvres,
    loading, error, fieldErrors, setFieldErrors,
    
    typesConge, motifsFamiliaux, selectedType,
    isExceptionnel, isMaternite,
    
    handleSubmit,
    inputErrorClass
  };
}
