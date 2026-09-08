/**
 * Utilitaire d'exportation Excel / CSV pour le SIRH GALAXAM.
 * 
 * Génère un fichier CSV enrichi avec BOM UTF-8 (\uFEFF) et séparateurs point-virgule,
 * garantissant une ouverture immédiate, sans boîte de dialogue de conversion,
 * avec respect des accents et caractères malgaches dans Microsoft Excel, LibreOffice Calc et Google Sheets.
 */

/**
 * Formate un nombre pour l'export Excel (ex: 8.5 -> "8,50" ou "8.50").
 */
const formatNumber = (num, decimals = 2) => {
    if (num === null || num === undefined || isNaN(num)) return '0,00';
    return Number(num).toFixed(decimals).replace('.', ',');
};

/**
 * Échappe une chaîne pour le format CSV.
 */
const escapeCsv = (val) => {
    if (val === null || val === undefined) return '""';
    const str = String(val).replace(/"/g, '""');
    return `"${str}"`;
};

/**
 * Exporte la synthèse mensuelle de validation Pointage vers un tableur Excel.
 * 
 * @param {object} params
 * @param {object} params.periode Objet période (code, libelle)
 * @param {string} params.siteNom Nom du site filtré
 * @param {Array<object>} params.syntheseData Données des collaborateurs consolidées
 */
export function exportPointageValidationToExcel({ periode, siteNom = 'Tous les sites', syntheseData = [] }) {
    if (!syntheseData || syntheseData.length === 0) {
        throw new Error("Aucune donnée à exporter pour cette période.");
    }

    const dateExport = new Date().toLocaleDateString('fr-FR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });

    const lines = [];

    // 1. En-tête du document
    lines.push(escapeCsv("GALAXAM SIRH — SYNTHÈSE MENSUELLE DE POINTAGE ET CONSOLIDATION PAIE"));
    lines.push(`${escapeCsv("Période :")};${escapeCsv(periode?.libelle || periode?.code || 'Période courante')};${escapeCsv("Site :")};${escapeCsv(siteNom)}`);
    lines.push(`${escapeCsv("Date d'extraction :")};${escapeCsv(dateExport)};${escapeCsv("Nombre de collaborateurs :")};${escapeCsv(String(syntheseData.length))}`);
    lines.push(""); // Ligne vide de séparation

    // 2. Colonnes de données
    const headers = [
        "Matricule",
        "Nom",
        "Prénom",
        "Fonction",
        "Catégorie",
        "Site",
        "Heures Normales (h)",
        "HS 30% (h)",
        "HS 50% (h)",
        "Total Heures (h)",
        "Heures Nuit (h)",
        "Retard Cumulé (min)",
        "Jours Présents (j)",
        "Jours Absents (j)",
        "Congés (j)",
        "Primes Repas (Ar)",
        "Indemnités Déplacement (Ar)",
        "Statut Validation"
    ];
    lines.push(headers.map(escapeCsv).join(';'));

    // Variables pour les totaux
    let totHNorm = 0;
    let totHs30 = 0;
    let totHs50 = 0;
    let totHTot = 0;
    let totHNuit = 0;
    let totRetard = 0;
    let totJoursPres = 0;
    let totJoursAbs = 0;
    let totConges = 0;
    let totRepas = 0;
    let totDepl = 0;

    // 3. Lignes de données
    syntheseData.forEach(row => {
        const hNorm = Number(row.heuresNormales || row.totalHeures || 0);
        const hs30 = Number(row.heuresSupplementaires30 || row.heuresHs30 || 0);
        const hs50 = Number(row.heuresSupplementaires50 || row.heuresHs50 || 0);
        const hTot = Number(row.totalHeures || 0);
        const hNuit = Number(row.heuresNuit || 0);
        const retMin = Number(row.minutesRetard || row.totalMinutesRetard || 0);
        const jPres = Number(row.joursPresents || row.totalJoursPresents || 0);
        const jAbs = Number(row.joursAbsents || row.totalJoursAbsents || 0);
        const jCong = Number(row.joursConges || 0);
        const mRepas = Number(row.totalIndemniteRepas || row.montantRepas || 0);
        const mDepl = Number(row.totalIndemniteDeplacement || row.montantDeplacement || 0);

        totHNorm += hNorm;
        totHs30 += hs30;
        totHs50 += hs50;
        totHTot += hTot;
        totHNuit += hNuit;
        totRetard += retMin;
        totJoursPres += jPres;
        totJoursAbs += jAbs;
        totConges += jCong;
        totRepas += mRepas;
        totDepl += mDepl;

        const rowValues = [
            escapeCsv(row.matricule || '—'),
            escapeCsv(row.nom || '—'),
            escapeCsv(row.prenom || ''),
            escapeCsv(row.fonction || row.intitulePoste || '—'),
            escapeCsv(row.categorie || row.codeCategorie || '—'),
            escapeCsv(row.siteNom || siteNom),
            formatNumber(hNorm),
            formatNumber(hs30),
            formatNumber(hs50),
            formatNumber(hTot),
            formatNumber(hNuit),
            String(retMin),
            formatNumber(jPres, 1),
            formatNumber(jAbs, 1),
            formatNumber(jCong, 1),
            formatNumber(mRepas, 0),
            formatNumber(mDepl, 0),
            escapeCsv(row.estValide ? 'Validé' : (row.statutLibelle || 'En attente'))
        ];

        lines.push(rowValues.join(';'));
    });

    // 4. Ligne des totaux généraux
    lines.push("");
    const totalsRow = [
        escapeCsv("TOTAL GÉNÉRAL"),
        escapeCsv(`${syntheseData.length} collaborateurs`),
        escapeCsv(""),
        escapeCsv(""),
        escapeCsv(""),
        escapeCsv(""),
        formatNumber(totHNorm),
        formatNumber(totHs30),
        formatNumber(totHs50),
        formatNumber(totHTot),
        formatNumber(totHNuit),
        String(totRetard),
        formatNumber(totJoursPres, 1),
        formatNumber(totJoursAbs, 1),
        formatNumber(totConges, 1),
        formatNumber(totRepas, 0),
        formatNumber(totDepl, 0),
        escapeCsv("")
    ];
    lines.push(totalsRow.join(';'));

    // 5. Création du Blob avec BOM UTF-8 pour Excel
    const csvContent = '\uFEFF' + lines.join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });

    // 6. Téléchargement immédiat
    const codePeriodeClean = (periode?.code || 'periode').replace(/[^a-zA-Z0-9-_]/g, '_');
    const dateFileClean = new Date().toISOString().split('T')[0];
    const fileName = `Synthese_Pointage_${codePeriodeClean}_${dateFileClean}.csv`;

    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', fileName);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    return fileName;
}
