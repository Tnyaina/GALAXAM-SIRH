const labels = {
    ENTREPRISE_ADRESSE: 'Adresse de l’entreprise', ENTREPRISE_NOM: 'Nom de l’entreprise',
    ENTREPRISE_NIF: 'NIF', ENTREPRISE_STAT: 'STAT', SIGNATAIRE_NOM: 'Nom du signataire',
    SIGNATAIRE_FONCTION: 'Fonction du signataire', SIGNATAIRE_VILLE: 'Ville de signature',
    TEMPS_TRAVAIL: 'Temps de travail', CONTRAT_DUREE_ESSAI: 'Durée de la période d’essai',
    CONTRAT_DATE_DEBUT: 'Date de début', CONTRAT_DATE_FIN: 'Date de fin', CONTRAT_NUMERO: 'Numéro du contrat',
    CONTRAT_DESCRIPTION: 'Description des missions', EMPLOYE_NOM_COMPLET: 'Nom de l’employé',
    EMPLOYE_MATRICULE: 'Matricule', EMPLOYE_DATE_NAISSANCE: 'Date de naissance',
    EMPLOYE_LIEU_NAISSANCE: 'Lieu de naissance', EMPLOYE_CIN: 'Numéro de CIN',
    EMPLOYE_CIN_DELIVRANCE: 'Délivrance de la CIN', EMPLOYE_ADRESSE: 'Adresse de l’employé',
    POSTE_FONCTION: 'Fonction', POSTE_SITE: 'Site', POSTE_DEPARTEMENT: 'Département',
    POSTE_SERVICE: 'Service', POSTE_CATEGORIE: 'Catégorie professionnelle',
    REMUNERATION_BASE: 'Rémunération de base', REMUNERATION_MODE: 'Mode de rémunération',
    STAGE_ETABLISSEMENT: 'Établissement de formation', STAGE_GRATIFICATION: 'Gratification de stage',
    STAGE_REFERENT_ETABLISSEMENT: 'Référent de l’établissement', PRESTATION_MONTANT: 'Honoraires',
    PRESTATION_MODALITES_PAIEMENT: 'Modalités de paiement', TEMPORAIRE_MOTIF: 'Motif du contrat temporaire',
    TEMPORAIRE_UNITE_REMUNERATION: 'Unité de rémunération',
};
export const contractDocumentLabel = variable => labels[variable.replace(/[{}]/g, '')] || 'Information contractuelle à compléter';
