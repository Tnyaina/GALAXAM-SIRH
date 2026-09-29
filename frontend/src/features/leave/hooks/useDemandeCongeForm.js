import { useState, useEffect } from 'react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import { 
  formatDateForInput, 
  isDateAnterieure
} from '@/features/leave/utils/congeBusinessRules';

export function useDemandeCongeForm({ idEmployeProp, initialDemande, initialDateDebut, initialDateFin, referentiels, isOpen, onClose, onSuccess }) {
  // État local : formulaire, dérogations, simulation et validation.
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
  const [simulationReport, setSimulationReport] = useState(null);

  // Simulation & State
  const [nbJoursDecomptes, setNbJoursDecomptes] = useState(0);
  const [nbJoursWeekEnd, setNbJoursWeekEnd] = useState(0);
  const [nbJoursOuvres, setNbJoursOuvres] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  // Données dérivées : référentiels actifs et règles applicables.
  const typesConge = (referentiels?.typesConge || []).filter(t => t.estActif !== false && t.actif !== false || String(t.idTypeConge) === String(idTypeConge));
  const motifsFamiliaux = (referentiels?.motifsFamiliaux || []).filter(m => m.estActif !== false && m.actif !== false || String(m.idMotif) === String(idMotifEvenementFamilial));

  const selectedType = typesConge.find((t) => String(t.idTypeConge) === String(idTypeConge));
  const isExceptionnel = selectedType && selectedType.code === 'EXCEPTIONNEL';
  const isMaternite = selectedType && selectedType.code === 'MATERNITE';

  // Le backend est l'unique propriétaire de la règle de préavis.
  const preavisInfo = simulationReport ? {
    isDepasse: simulationReport.violations?.includes('LEAVE_NOTICE_NOT_RESPECTED')
      || simulationReport.warnings?.includes('LEAVE_NOTICE_NOT_RESPECTED'),
    preavisRequis: simulationReport.joursPreavisRequis || 0,
    joursConstates: simulationReport.joursPreavisConstates || 0,
    isRetroactif: Number(simulationReport.joursPreavisConstates) < 0,
  } : { isDepasse: false, preavisRequis: 0, joursConstates: 0, isRetroactif: false };

  // Effet : initialise le formulaire à l'ouverture, en création ou en édition.
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
        const isPastInit = initialDateDebut && (() => {
          const [y, m, d] = String(initialDateDebut).split('T')[0].split('-').map(Number);
          const t = (y && m && d) ? new Date(y, m - 1, d, 0, 0, 0, 0) : new Date(initialDateDebut);
          const today = new Date();
          today.setHours(0, 0, 0, 0);
          return t < today;
        })();
        setDerogationDelaiPreavis(Boolean(isPastInit));
        setDerogationContinuiteService(false);
        setExtensionPathologiqueMaternite(false);
        setShowOptionsAvancees(false);
      }
      setConflitReport(null);
      setError(null);
      setFieldErrors({});
    }
  }, [isOpen, idEmployeProp, initialDemande, initialDateDebut, initialDateFin]);

  // Dérogation de préavis automatique si la date de début est antérieure à aujourd'hui (oubli, saisie rétroactive)
  useEffect(() => {
    if (isDateAnterieure(dateDebut)) {
      setDerogationDelaiPreavis(true);
    }
  }, [dateDebut]);

  // Si demi-journée activée, la date de fin doit égaler la date de début
  useEffect(() => {
    if (estDemiJournee && dateDebut) {
      setDateFin(dateDebut);
    }
  }, [estDemiJournee, dateDebut]);

  // Simulation canonique : durée fixe, décompte, solde, préavis et quota viennent du backend.
  useEffect(() => {
    let active = true;
    if (!dateDebut || !idTypeConge || (!dateFin && !selectedType?.dureeFixeJours && !idMotifEvenementFamilial)) {
      setSimulationReport(null);
      return () => { active = false; };
    }
    const timer = setTimeout(async () => {
      try {
        const report = await congeService.simulerDemande({
          idEmploye: Number(idEmployeProp || selectedEmployeId) || null,
          idTypeConge: Number(idTypeConge),
          idMotifEvenementFamilial: idMotifEvenementFamilial ? Number(idMotifEvenementFamilial) : null,
          dateDebut,
          dateFin: estDemiJournee ? dateDebut : (dateFin || null),
          estDemiJournee,
          derogationSoldeNegatif,
          derogationDelaiPreavis,
          extensionPathologiqueMaternite,
        });
        if (!active) return;
        setSimulationReport(report);
        setNbJoursDecomptes(Number(report.nbJours || 0));
        if (report.dateFin && formatDateForInput(report.dateFin) !== dateFin) {
          setDateFin(formatDateForInput(report.dateFin));
        }
      } catch {
        if (active) setSimulationReport(null);
      }
    }, 180);
    return () => { active = false; clearTimeout(timer); };
  }, [dateDebut, dateFin, idTypeConge, idMotifEvenementFamilial, estDemiJournee,
    extensionPathologiqueMaternite, derogationSoldeNegatif, derogationDelaiPreavis,
    idEmployeProp, selectedEmployeId, selectedType?.dureeFixeJours]);

  // Analyse serveur de continuité de service & simulation des jours (backend-driven)
  useEffect(() => {
    let isMounted = true;
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
            // Le formulaire n'invente pas de décompte si le backend est indisponible.
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
        setFieldErrors((prev) => ({ ...prev, dateFin: 'La reprise doit être postérieure au premier jour de congé.' }));
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
    if (!dateFin) nextFieldErrors.dateFin = 'Renseignez la date de reprise prévue.';
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
    conflitReport, simulationReport, checkingConflits,
    loading, error, fieldErrors, setFieldErrors,

    typesConge, motifsFamiliaux, selectedType,
    isExceptionnel, isMaternite,

    handleSubmit,
    inputErrorClass
  };
}
