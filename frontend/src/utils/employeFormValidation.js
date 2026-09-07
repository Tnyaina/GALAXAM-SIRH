/**
 * Utilitaires de Validation et Formatage pour le Dossier Employé (Frontend).
 * 
 * Centralise les contrôles de conformité par étape (Identité, CIN, Coordonnées, etc.)
 * pour assurer un guidage clair de l'utilisateur dans l'assistant de création / modification.
 */

/**
 * Patterns de validation stricts conformes au backend (EmployeValidationEngine.java)
 */
export const PATTERN_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const PATTERN_CIN = /^[0-9]{12}$/;
export const PATTERN_TELEPHONE = /^[+]?[0-9\s.-]{8,20}$/;
export const AGE_MINIMUM_LEGAL = 16;

/**
 * Valide le format d'une adresse email.
 * 
 * @param {string|null} email
 * @returns {boolean}
 */
export const validerEmail = (email) => {
  if (!email || !email.trim()) return true; // Facultatif si vide
  return PATTERN_EMAIL.test(email.trim());
};

/**
 * Calcule l'âge en années révolues entre une date de naissance et une date de référence.
 * 
 * @param {string|Date} dateNaissance 
 * @param {Date} [referenceDate=new Date()]
 * @returns {number|null}
 */
export const calculerAge = (dateNaissance, referenceDate = new Date()) => {
  if (!dateNaissance) return null;
  const [y, m, d] = String(dateNaissance).substring(0, 10).split('-').map(Number);
  if (!y || !m || !d) return null;
  const birth = new Date(y, m - 1, d, 0, 0, 0, 0);
  const ref = new Date(referenceDate);
  ref.setHours(0, 0, 0, 0);

  let age = ref.getFullYear() - birth.getFullYear();
  const mDiff = ref.getMonth() - birth.getMonth();
  if (mDiff < 0 || (mDiff === 0 && ref.getDate() < birth.getDate())) {
    age--;
  }
  return age;
};

/**
 * Valide les champs obligatoires et règles légales d'une étape du formulaire collaborateur.
 * 
 * @param {string} stepId 'civilite' | 'cin' | 'coordonnees' | 'famille' | 'urgence' | 'banque'
 * @param {object} formData Données complètes du formulaire
 * @param {string|null} matriculeError Erreur d'unicité éventuelle sur le matricule
 * @param {object} [options={}] Options contextuelles (customNationalite, isCustomNat)
 * @returns {string|null} Message d'erreur bloquant ou null si l'étape est valide
 */
export const validerEtapeEmploye = (stepId, formData, matriculeError = '', options = {}) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  if (stepId === 'civilite') {
    if (!formData.matricule || !formData.nom || !formData.prenom || !formData.dateNaissance) {
      return "Veuillez renseigner tous les champs obligatoires (Matricule, Nom, Prénom, Date de Naissance).";
    }
    if (matriculeError) {
      return matriculeError;
    }

    const [y, m, d] = String(formData.dateNaissance).substring(0, 10).split('-').map(Number);
    const dateNaiss = new Date(y, m - 1, d, 0, 0, 0, 0);
    if (dateNaiss > today) {
      return "La date de naissance ne peut pas être postérieure à aujourd'hui.";
    }

    const age = calculerAge(formData.dateNaissance, today);
    if (age !== null && age < AGE_MINIMUM_LEGAL) {
      return `L'employé doit avoir au minimum ${AGE_MINIMUM_LEGAL} ans révolus (âge calculé : ${age} ans).`;
    }

    if (formData.nationalite === 'Autre' || options.isCustomNat) {
      const customNat = options.customNationalite || '';
      if (!customNat.trim()) {
        return "Veuillez préciser la nationalité pour le choix 'Autre'.";
      }
    }
  }

  if (stepId === 'cin') {
    if (!formData.cinNumero || !formData.cinDateDelivrance || !formData.cinLieuDelivrance) {
      return "Le numéro, la date et le lieu de délivrance du CIN sont obligatoires.";
    }

    const cleanCin = String(formData.cinNumero).replace(/\s+/g, '');
    if (!PATTERN_CIN.test(cleanCin)) {
      return "Le numéro de CIN doit comporter exactement 12 chiffres.";
    }

    const [yCin, mCin, dCin] = String(formData.cinDateDelivrance).substring(0, 10).split('-').map(Number);
    const dateCin = new Date(yCin, mCin - 1, dCin, 0, 0, 0, 0);
    if (dateCin > today) {
      return "La date de délivrance du CIN ne peut pas être dans le futur.";
    }

    if (formData.dateNaissance) {
      const ageDelivrance = calculerAge(formData.dateNaissance, dateCin);
      if (ageDelivrance !== null && ageDelivrance < AGE_MINIMUM_LEGAL) {
        return `La date de délivrance du CIN est incompatible avec la date de naissance (délivré avant l'âge légal de ${AGE_MINIMUM_LEGAL} ans).`;
      }
    }
  }

  if (stepId === 'coordonnees') {
    if (!formData.adresse || !formData.telephone) {
      return "L'adresse et le téléphone sont obligatoires.";
    }
    if (!PATTERN_TELEPHONE.test(String(formData.telephone).trim())) {
      return "Le format du numéro de téléphone est invalide (ex: 034 00 000 00 ou +261 34 00 000 00).";
    }
    if (formData.email && !validerEmail(formData.email)) {
      return "L'adresse email saisie est invalide.";
    }
  }

  if (stepId === 'famille') {
    if (formData.enfants && formData.enfants.length > 0) {
      for (let i = 0; i < formData.enfants.length; i++) {
        const enf = formData.enfants[i];
        if (!enf.nom || !enf.nom.trim()) {
          return `L'enfant n°${i + 1} doit obligatoirement avoir un nom.`;
        }
        if (enf.dateNaissance) {
          const [yE, mE, dE] = String(enf.dateNaissance).substring(0, 10).split('-').map(Number);
          const dateNaissEnf = new Date(yE, mE - 1, dE, 0, 0, 0, 0);
          if (dateNaissEnf > today) {
            return `La date de naissance de l'enfant n°${i + 1} ne peut pas être dans le futur.`;
          }
          if (enf.dateFinScolarite) {
            const [yS, mS, dS] = String(enf.dateFinScolarite).substring(0, 10).split('-').map(Number);
            const dateScol = new Date(yS, mS - 1, dS, 0, 0, 0, 0);
            if (dateScol < dateNaissEnf) {
              return `L'enfant n°${i + 1} : la date de fin de scolarité ne peut pas précéder sa date de naissance.`;
            }
          }
        }
      }
    }
  }

  if (stepId === 'banque') {
    const cb = formData.compteBanquePrincipal;
    if (cb) {
      const hasBanque = cb.idBanque !== null && cb.idBanque !== undefined && cb.idBanque !== '';
      const hasRib = cb.numCompteRib && cb.numCompteRib.trim().length > 0;
      if (hasRib && !hasBanque) {
        return "La banque doit être sélectionnée si un numéro de compte RIB est renseigné.";
      }
      if (hasBanque && !hasRib) {
        return "Le numéro de compte RIB est obligatoire si une banque est sélectionnée.";
      }
    }
  }

  return null;
};

/**
 * Valide l'intégralité des étapes du formulaire collaborateur et renvoie la première étape en erreur.
 * 
 * @param {Array<object>} steps Liste des étapes { id, label }
 * @param {object} formData Données du formulaire
 * @param {string} matriculeError Erreur matricule
 * @param {object} options Options supplémentaires
 * @returns {{ stepIndex: number, stepId: string, error: string }|null}
 */
export const validerToutesEtapesEmploye = (steps, formData, matriculeError = '', options = {}) => {
  for (let idx = 0; idx < steps.length; idx++) {
    const step = steps[idx];
    const err = validerEtapeEmploye(step.id, formData, matriculeError, options);
    if (err) {
      return { stepIndex: idx, stepId: step.id, error: err };
    }
  }
  return null;
};

/**
 * Formate une date en chaîne ISO YYYY-MM-DD.
 * 
 * @param {string|Date|null} d
 * @returns {string}
 */
export const formaterDateEmploye = (d) => {
  if (!d) return '';
  if (d instanceof Date) {
    if (isNaN(d.getTime())) return '';
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }
  if (typeof d === 'string') {
    const s = d.trim();
    if (s.includes('T')) return s.split('T')[0];
    if (s.includes(' ')) return s.split(' ')[0];
    if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
    const parsed = new Date(s);
    if (!isNaN(parsed.getTime())) {
      const yyyy = parsed.getFullYear();
      const mm = String(parsed.getMonth() + 1).padStart(2, '0');
      const dd = String(parsed.getDate()).padStart(2, '0');
      return `${yyyy}-${mm}-${dd}`;
    }
    return s.substring(0, 10);
  }
  return '';
};
