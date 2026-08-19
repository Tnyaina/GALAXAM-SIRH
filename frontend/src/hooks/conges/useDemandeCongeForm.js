import { useState, useEffect } from 'react';
import congeService from '@/api/congeService';

export function useDemandeCongeForm({ idEmployeProp, referentiels, isOpen, onClose, onSuccess }) {
  const [selectedEmployeId, setSelectedEmployeId] = useState('');
  const [idTypeConge, setIdTypeConge] = useState('');
  const [idMotifEvenementFamilial, setIdMotifEvenementFamilial] = useState('');
  const [dateDebut, setDateDebut] = useState('');
  const [dateFin, setDateFin] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [justificatifFourni, setJustificatifFourni] = useState(false);

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
      setSelectedEmployeId(idEmployeProp ? String(idEmployeProp) : '');
      setError(null);
      setFieldErrors({});
    }
  }, [isOpen, idEmployeProp]);

  // Pré-remplissage des durées fixes légales (ex: Paternité 3j, Maternité 98j, Événements familiaux)
  useEffect(() => {
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
  }, [idTypeConge, idMotifEvenementFamilial, dateDebut, extensionPathologiqueMaternite]);

  // Simulation automatique des jours calendaires stricts + validation inline des dates
  useEffect(() => {
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
  }, [dateDebut, dateFin]);

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

    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors((prev) => ({ ...prev, ...nextFieldErrors }));
      setError('Veuillez corriger les champs indiqués ci-dessous avant de continuer.');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const payload = {
        idEmploye: parseInt(targetEmployeId),
        idTypeConge: parseInt(idTypeConge),
        idMotifEvenementFamilial: idMotifEvenementFamilial ? parseInt(idMotifEvenementFamilial) : null,
        dateDebut,
        dateFin,
        commentaire,
        justificatifFourni,
        derogationSoldeNegatif,
        derogationDelaiPreavis,
        extensionPathologiqueMaternite,
      };

      await congeService.creerDemande(payload);
      showToast({
        title: 'Demande soumise',
        message: 'Votre demande de congé a été enregistrée avec succès.',
        type: 'success',
        duration: 3000
      });
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
