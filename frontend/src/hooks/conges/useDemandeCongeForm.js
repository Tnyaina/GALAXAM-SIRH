import { useState, useEffect } from 'react';
import congeService from '@/api/congeService';
import { getErrorMessage } from '@/api/apiClient';

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

export function useDemandeCongeForm({ idEmployeProp, initialDemande, initialDateDebut, initialDateFin, referentiels, isOpen, onClose, onSuccess }) {
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
  const [derogationContinuiteService, setDerogationContinuiteService] = useState(false);
  const [extensionPathologiqueMaternite, setExtensionPathologiqueMaternite] = useState(false);
  const [estRemunere, setEstRemunere] = useState(true);
  const [showOptionsAvancees, setShowOptionsAvancees] = useState(false);

  // Simulation, Conflits de poste & Continuité de service
  const [conflitReport, setConflitReport] = useState(null);
  const [checkingConflits, setCheckingConflits] = useState(false);

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

  const preavisInfo = (() => {
    if (!dateDebut) return { isDepasse: false, preavisRequis: 0, joursConstates: 0, isRetroactif: false };
    const dDebut = new Date(dateDebut);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const joursConstates = Math.ceil((dDebut.getTime() - today.getTime()) / (1000 * 3600 * 24));
    
    if (joursConstates < 0) {
      return { isDepasse: true, preavisRequis: 0, joursConstates, isRetroactif: true };
    }

    if (selectedType?.soumisDelaiPreavis) {
      const seuilLong = 3;
      const preavisRequis = (nbJoursDecomptes > seuilLong) ? 15 : 2;
      if (joursConstates < preavisRequis) {
        return { isDepasse: true, preavisRequis, joursConstates, isRetroactif: false };
      }
    }
    return { isDepasse: false, preavisRequis: 0, joursConstates, isRetroactif: false };
  })();

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
        setDerogationContinuiteService(!!initialDemande.derogationContinuiteService);
        setExtensionPathologiqueMaternite(!!initialDemande.extensionPathologiqueMaternite);
        if (initialDemande.estRemunere !== undefined && initialDemande.estRemunere !== null) {
          setEstRemunere(Boolean(initialDemande.estRemunere));
        } else if (initialDemande.estPaye !== undefined) {
          setEstRemunere(Boolean(initialDemande.estPaye));
        } else {
          setEstRemunere(true);
        }
        if (initialDemande.derogationSoldeNegatif || initialDemande.derogationDelaiPreavis || initialDemande.derogationContinuiteService || initialDemande.extensionPathologiqueMaternite) {
          setShowOptionsAvancees(true);
        } else {
          setShowOptionsAvancees(false);
        }
      } else {
        setSelectedEmployeId(idEmployeProp ? String(idEmployeProp) : '');
        setIdTypeConge('');
        setIdMotifEvenementFamilial('');
        setDateDebut(initialDateDebut ? formatDateForInput(initialDateDebut) : '');
        setDateFin(initialDateFin ? formatDateForInput(initialDateFin) : (initialDateDebut ? formatDateForInput(initialDateDebut) : ''));
        setCommentaire('');
        setJustificatifFourni(false);
        setEstDemiJournee(false);
        setMomentDemiJournee('MATIN');
        setDerogationSoldeNegatif(false);
        setEstRemunere(true);
        setDerogationDelaiPreavis(false);
        setDerogationContinuiteService(false);
        setExtensionPathologiqueMaternite(false);
        setShowOptionsAvancees(false);
      }
      setConflitReport(null);
      setError(null);
      setFieldErrors({});
    }
  }, [isOpen, idEmployeProp, initialDemande, initialDateDebut, initialDateFin]);

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

  // Analyse serveur de continuité de service & simulation des jours (backend-driven)
  useEffect(() => {
    let isMounted = true;
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
    }

    if (dateDebut && dateFin) {
      const dDebut = new Date(dateDebut);
      const dFin = new Date(dateFin);
      if (dFin >= dDebut) {
        setFieldErrors((prev) => ({ ...prev, dateFin: null }));
        setCheckingConflits(true);

        const timer = setTimeout(async () => {
          try {
            const report = await congeService.verifierConflitsDemande({
              idEmploye: selectedEmployeId ? Number(selectedEmployeId) : null,
              dateDebut,
              dateFin: estDemiJournee ? dateDebut : dateFin,
              estDemiJournee,
              momentDemiJournee: estDemiJournee ? momentDemiJournee : null,
              idDemandeConge: initialDemande?.idDemandeConge || null
            });
            if (isMounted && report) {
              setConflitReport(report);
              setNbJoursDecomptes(Number(report.nbJoursDecomptes || 0));
              setNbJoursOuvres(Number(report.nbJoursOuvres || 0));
              setNbJoursWeekEnd(Number(report.nbJoursWeekEnd || 0));
            }
          } catch {
            if (isMounted) {
              const diffTime = dFin.getTime() - dDebut.getTime();
              const diffDays = Math.ceil(diffTime / (1000 * 3600 * 24)) + 1;
              setNbJoursDecomptes(diffDays);
            }
          } finally {
            if (isMounted) setCheckingConflits(false);
          }
        }, 180);

        return () => {
          isMounted = false;
          clearTimeout(timer);
        };
      } else {
        setNbJoursDecomptes(0);
        setNbJoursWeekEnd(0);
        setNbJoursOuvres(0);
        setConflitReport(null);
        setCheckingConflits(false);
        setFieldErrors((prev) => ({ ...prev, dateFin: 'La date de fin doit être postérieure ou égale à la date de début.' }));
      }
    } else {
      setNbJoursDecomptes(0);
      setNbJoursWeekEnd(0);
      setNbJoursOuvres(0);
      setConflitReport(null);
      setCheckingConflits(false);
    }
  }, [dateDebut, dateFin, estDemiJournee, momentDemiJournee, selectedEmployeId, initialDemande?.idDemandeConge]);

  const handleSubmit = async (e, showToast) => {
    e.preventDefault();
    if (loading) return;

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

      const payload = {
        idEmploye: parseInt(targetEmployeId, 10),
        idTypeConge: parseInt(idTypeConge, 10),
        idMotifEvenementFamilial: isExceptionnel && idMotifEvenementFamilial ? parseInt(idMotifEvenementFamilial, 10) : null,
        dateDebut: dateDebut || null,
        dateFin: estDemiJournee ? dateDebut : (dateFin || null),
        estDemiJournee: Boolean(estDemiJournee),
        momentDemiJournee: estDemiJournee ? momentDemiJournee : null,
        commentaire: commentaire ? commentaire.trim() : null,
        justificatifFourni: Boolean(justificatifFourni),
        derogationSoldeNegatif: Boolean(derogationSoldeNegatif),
        derogationDelaiPreavis: Boolean(derogationDelaiPreavis),
        derogationContinuiteService: Boolean(derogationContinuiteService),
        extensionPathologiqueMaternite: Boolean(extensionPathologiqueMaternite),
        estRemunere: Boolean(estRemunere),
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
      setError(getErrorMessage(err, "Impossible d'enregistrer la demande. Veuillez vérifier les informations et réessayer."));
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
    derogationContinuiteService, setDerogationContinuiteService,
    extensionPathologiqueMaternite, setExtensionPathologiqueMaternite,
    estRemunere, setEstRemunere,
    showOptionsAvancees, setShowOptionsAvancees,
    preavisInfo,

    nbJoursDecomptes, nbJoursWeekEnd, nbJoursOuvres,
    conflitReport, checkingConflits,
    loading, error, fieldErrors, setFieldErrors,

    typesConge, motifsFamiliaux, selectedType,
    isExceptionnel, isMaternite,

    handleSubmit,
    inputErrorClass
  };
}
