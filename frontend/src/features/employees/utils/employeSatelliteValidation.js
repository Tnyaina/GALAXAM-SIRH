function dateLocaleSansHeure(value) {
    if (!value) return null;
    const [annee, mois, jour] = String(value).substring(0, 10).split('-').map(Number);
    const date = new Date(annee, mois - 1, jour, 0, 0, 0, 0);
    return Number.isNaN(date.getTime()) ? null : date;
}

export function validerSatellitesEmploye(stepId, formData, referenceDate = new Date()) {
    const erreurs = {};
    const aujourdHui = new Date(referenceDate);
    aujourdHui.setHours(0, 0, 0, 0);

    if (stepId === 'famille') {
        (formData.enfants || []).forEach((enfant, index) => {
            if (!enfant) {
                erreurs[`enfant_${index}_objet`] = `L'enfant n°${index + 1} n'est pas renseigné.`;
                return;
            }
            if (!enfant.nom || !enfant.nom.trim()) {
                erreurs[`enfant_${index}_nom`] = `Le nom de l'enfant n°${index + 1} est obligatoire.`;
            }
            if (!enfant.prenom || !enfant.prenom.trim()) {
                erreurs[`enfant_${index}_prenom`] = `Le prénom de l'enfant n°${index + 1} est obligatoire.`;
            }
            if (!enfant.dateNaissance) {
                erreurs[`enfant_${index}_dateNaissance`] = `La date de naissance de l'enfant n°${index + 1} est obligatoire.`;
                return;
            }

            const dateNaissance = dateLocaleSansHeure(enfant.dateNaissance);
            if (dateNaissance && dateNaissance > aujourdHui) {
                erreurs[`enfant_${index}_dateNaissance`] = `Date de naissance dans le futur.`;
            }
            if (enfant.dateFinScolarite && dateNaissance) {
                const dateFin = dateLocaleSansHeure(enfant.dateFinScolarite);
                if (dateFin && dateFin < dateNaissance) {
                    erreurs[`enfant_${index}_dateFinScolarite`] = `Ne peut précéder la naissance.`;
                }
            }
        });
    }

    if (stepId === 'urgence') {
        (formData.contactsUrgence || []).forEach((contact, index) => {
            if (!contact) {
                erreurs[`contact_${index}_objet`] = `Le contact d'urgence n°${index + 1} n'est pas renseigné.`;
                return;
            }
            const estVide = (valeur) => !valeur || !valeur.trim();
            if (estVide(contact.nom) && estVide(contact.prenom) && estVide(contact.telephone)
                && estVide(contact.adresse) && estVide(contact.societe)) {
                return;
            }
            if (!contact.nom || !contact.nom.trim()) {
                erreurs[`contact_${index}_nom`] = `Le nom du contact d'urgence n°${index + 1} est obligatoire.`;
            }
            if (!contact.prenom || !contact.prenom.trim()) {
                erreurs[`contact_${index}_prenom`] = `Le prénom du contact d'urgence n°${index + 1} est obligatoire.`;
            }
            if (!contact.telephone || !contact.telephone.trim()) {
                erreurs[`contact_${index}_telephone`] = `Le téléphone du contact d'urgence n°${index + 1} est obligatoire.`;
            }
        });
    }

    return erreurs;
}
