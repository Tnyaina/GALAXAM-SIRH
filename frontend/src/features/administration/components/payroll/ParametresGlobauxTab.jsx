import { useState, useEffect, useMemo, useCallback } from 'react';
import {
    Sliders, Search, LayoutGrid, List,
    Edit2, Plus, Calendar, ShieldCheck,
    Lock, CheckCircle2, XCircle, Power,
    Clock, Moon, Sun, Utensils, Timer, Bus,
    Coffee, Coins, Shield, Hourglass, AlertTriangle,
    Bell, CalendarClock, HeartHandshake, HeartPulse,
    Award, TrendingUp, Gift, FileText, Repeat, UserCheck,
    Layers, ChevronDown, ChevronUp, AlertCircle, Info,
    FolderKanban, Check, Eye
} from 'lucide-react';
import paieConfigService from '@/features/administration/api/paieConfigService';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import { Card, CardHeader, CardBody, CardFooter } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDateInput } from '@/utils/dateUtils';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import Modal from '@/components/ui/overlays/Modal';
import { useToast } from '@/components/ui/feedback/Toast';
import { getErrorMessage } from '@/api/apiClient';

// Métadonnées métier enrichies pour libellés humains, icônes et explications claires
const PARAMETRE_METADATA = {
    // Pointage & Temps de travail
    SEUIL_HEURES_JOUR: {
        titre: "Durée journalière de référence",
        icone: Clock,
        unite: "heures",
        explication: "Durée quotidienne normale utilisée pour le calcul du temps de travail et le suivi des retards.",
    },
    SEUIL_HEURES_SEMAINE_COMPENSATION: {
        titre: "Seuil hebdomadaire de compensation",
        icone: Clock,
        unite: "heures",
        explication: "Seuil hebdomadaire archivé pour traçabilité historique.",
    },
    SEUIL_NUIT_DEBUT: {
        titre: "Début de la plage de nuit",
        icone: Moon,
        unite: "heure",
        explication: "Heure légale marquant le début des heures de nuit (Code du Travail, Art. 83).",
    },
    SEUIL_NUIT_FIN: {
        titre: "Fin de la plage de nuit",
        icone: Sun,
        unite: "heure",
        explication: "Heure légale marquant la fin des heures de nuit.",
    },
    MONTANT_REPAS_MIDI: {
        titre: "Indemnité repas du midi",
        icone: Utensils,
        unite: "Ar",
        explication: "Montant journalier alloué pour le repas du midi selon les accords de site.",
    },
    SEUIL_PAUSE_MIDI_MIN: {
        titre: "Durée minimale de pause midi",
        icone: Timer,
        unite: "minutes",
        explication: "Durée minimale de pause prise en compte pour la détection de présence.",
    },
    REDUCTION_DEPLACT_REPAS_MIDI: {
        titre: "Déduction transport repas cantine",
        icone: Bus,
        unite: "Ar",
        explication: "Montant déduit des frais de déplacement lorsque le repas cantine est pris sur site.",
    },
    SEUIL_2ND_REPAS_1: {
        titre: "Seuil horaire 2ᵉ repas (Tranche 1)",
        icone: Coffee,
        unite: "heure",
        explication: "Heure de sortie ouvrant droit au deuxième repas.",
    },
    MONTANT_2ND_REPAS_1: {
        titre: "Montant 2ᵉ repas (Tranche 1)",
        icone: Coins,
        unite: "Ar",
        explication: "Montant alloué pour le deuxième repas de la tranche 1.",
    },
    SEUIL_2ND_REPAS_2: {
        titre: "Seuil horaire 2ᵉ repas (Tranche 2)",
        icone: Coffee,
        unite: "heure",
        explication: "Heure de sortie ouvrant droit au deuxième repas majoré.",
    },
    MONTANT_2ND_REPAS_2: {
        titre: "Montant 2ᵉ repas (Tranche 2)",
        icone: Coins,
        unite: "Ar",
        explication: "Montant alloué pour le deuxième repas majoré de la tranche 2.",
    },

    // Congés & Absences
    PLAFOND_CUMUL_SOLDE_CONGE_JOURS: {
        titre: "Plafond de cumul des congés",
        icone: Shield,
        unite: "jours",
        explication: "Nombre maximum de jours cumulables au solde de congés payés.",
    },
    DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS: {
        titre: "Délai de signalement pour revue des soldes",
        icone: Hourglass,
        unite: "mois",
        explication: "Ancienneté des droits à partir de laquelle une revue administrative est demandée.",
    },
    DUREE_REFERENCE_TCONGE_MOIS: {
        titre: "Période de référence des congés",
        icone: Calendar,
        unite: "mois",
        explication: "Nombre de mois de référence servant à l'acquisition annuelle des droits à congés.",
    },
    PLAFOND_SOLDE_CONGE_NEGATIF: {
        titre: "Plafond d'anticipation de congés (solde négatif)",
        icone: AlertTriangle,
        unite: "jours",
        explication: "Limite maximale autorisée pour la prise anticipée de congés lorsque la règle est active.",
    },
    SEUIL_ALERTE_SOLDE_CONGE_FAIBLE: {
        titre: "Seuil d'alerte de solde faible",
        icone: Bell,
        unite: "jours",
        explication: "Nombre de jours restants déclenchant l'alerte préventive.",
    },
    DELAI_PREAVIS_COURT_JOURS: {
        titre: "Délai de préavis pour congé court",
        icone: CalendarClock,
        unite: "jours",
        explication: "Délai minimal de soumission pour une demande de congé court.",
    },
    DELAI_PREAVIS_LONG_JOURS: {
        titre: "Délai de préavis pour congé long",
        icone: CalendarClock,
        unite: "jours",
        explication: "Délai minimal de soumission pour une demande de congé long.",
    },
    SEUIL_JOURS_PREAVIS_LONG: {
        titre: "Seuil de passage en préavis long",
        icone: CalendarClock,
        unite: "jours",
        explication: "Durée d'absence à partir de laquelle le préavis long est requis.",
    },
    DUREE_MAX_MATERNITE_STANDARD_JOURS: {
        titre: "Durée congé maternité standard",
        icone: HeartHandshake,
        unite: "jours",
        explication: "Durée légale maximale du congé maternité (14 semaines).",
    },
    DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS: {
        titre: "Durée congé maternité pathologique",
        icone: HeartPulse,
        unite: "jours",
        explication: "Durée maximale autorisée en cas de prescription médicale pathologique.",
    },
    SEUIL_ANCIENNETE_BONIFICATION_ANS: {
        titre: "Ancienneté requise pour bonification",
        icone: Award,
        unite: "ans",
        explication: "Ancienneté minimale ouvrant droit aux jours de congés supplémentaires.",
    },
    PALIER_ANNEES_BONIFICATION: {
        titre: "Intervalle d'ancienneté",
        icone: TrendingUp,
        unite: "ans",
        explication: "Nombre d'années d'intervalle entre chaque palier d'attribution.",
    },
    JOURS_BONIFICATION_PAR_PALIER: {
        titre: "Jours de congé par palier",
        icone: Gift,
        unite: "jours",
        explication: "Nombre de jours additionnels attribués à chaque palier atteint.",
    },

    // Contrats & Carrière
    DUREE_MAX_CONTRAT_CDD_MOIS: {
        titre: "Durée maximale par contrat CDD",
        icone: FileText,
        unite: "mois",
        explication: "Durée maximale d'un contrat CDD individuel ou avenant (Code du Travail, Art. 19).",
    },
    DUREE_MAX_CDD_MOIS: {
        titre: "Plafond cumulé de durée CDD",
        icone: FileText,
        unite: "mois",
        explication: "Plafond total de durée pour les CDD successifs d'un même salarié.",
    },
    NB_MAX_RENOUVELLEMENTS_CDD: {
        titre: "Nombre maximal de renouvellements CDD",
        icone: Repeat,
        unite: "fois",
        explication: "Nombre maximal d'avenants autorisés avant obligation de passage en CDI.",
    },
    MAX_RENOUVELLEMENTS_ESSAI: {
        titre: "Renouvellement maximal de période d'essai",
        icone: UserCheck,
        unite: "fois",
        explication: "Nombre maximal de renouvellements de période d'essai (1 fois selon le Code du Travail).",
    },

    // Paie & Fiscalité
    TAUX_ISI: {
        titre: "Taux de retenue ISI",
        icone: Coins,
        unite: "%",
        explication: "Taux de retenue à la source (5%) sur les honoraires de prestataires de services externes (CGI).",
    },
    MONTANT_PRIME_ASSIDUITE: {
        titre: "Montant forfaitaire de la prime d'assiduité",
        icone: Award,
        unite: "Ar",
        explication: "Montant attribué sur le bulletin si le cumul mensuel de retards reste inférieur au seuil.",
    },
    SEUIL_RETARD_ASSIDUITE_MIN: {
        titre: "Seuil de retard pour prime d'assiduité",
        icone: Timer,
        unite: "min",
        explication: "Cumul de retard mensuel maximal toléré pour percevoir la prime d'assiduité.",
    },
    SEUIL_RETARD_PENALITE_PP_MIN: {
        titre: "Seuil de retard pour pénalité performance",
        icone: AlertTriangle,
        unite: "min",
        explication: "Seuil de retard mensuel pris en compte pour la pénalité sur prime de performance.",
    },
    SEUIL_CONGES_PENALITE_PP_JOURS: {
        titre: "Seuil de congés pour pénalité performance",
        icone: Calendar,
        unite: "jours",
        explication: "Seuil d'absence pour congés pris en compte pour la pénalité sur prime de performance.",
    },
    TAUX_PENALITE_PP: {
        titre: "Taux de pénalité prime de performance",
        icone: TrendingUp,
        unite: "taux",
        explication: "Taux de réduction appliqué à la prime de performance si les seuils sont dépassés.",
    },
    EXONERATION_HS_IRSA_ACTIVE: {
        titre: "Exonération IRSA des heures supplémentaires",
        icone: ShieldCheck,
        unite: "",
        explication: "Application de l'exonération fiscale sur les heures supplémentaires (CGI Art. 01.03.03-9°).",
    },
    PLAFOND_MENSUEL_HS_EXONEREES: {
        titre: "Plafond mensuel d'heures exonérées",
        icone: Clock,
        unite: "heures",
        explication: "Volume maximal d'heures supplémentaires exonérées d'IRSA par mois.",
    },
};

// Blocs Thématiques métier regroupant les paramètres interdépendants et leurs règles conjointes
const THEMATIC_BLOCKS = [
    // --- PAIE & PRIMES ---
    {
        id: 'PAIE_ASSIDUITE',
        categorie: 'PAIE',
        titre: "Prime d'Assiduité",
        sousTitre: "Attribution mensuelle conditionnée par la ponctualité",
        icone: Award,
        regleConjointe: "Attribuée automatiquement au bulletin de paie si le cumul mensuel de retards reste inférieur ou égal au seuil.",
        effetCascade: "Si le montant est désactivé ou égal à 0 Ar, aucune prime d'assiduité n'est versée.",
        masterCode: 'MONTANT_PRIME_ASSIDUITE',
        codes: ['MONTANT_PRIME_ASSIDUITE', 'SEUIL_RETARD_ASSIDUITE_MIN']
    },
    {
        id: 'PAIE_PERFORMANCE',
        categorie: 'PAIE',
        titre: "Prime de Performance",
        sousTitre: "Pénalité sur prime en cas de retards et congés combinés",
        icone: TrendingUp,
        regleConjointe: "Une réduction de 25% s'applique si les seuils de retard et de congés sont tous deux dépassés.",
        effetCascade: "Si le taux ou l'un des deux seuils est désactivé, la pénalité conjointe n'est pas appliquée.",
        masterCode: 'TAUX_PENALITE_PP',
        codes: ['TAUX_PENALITE_PP', 'SEUIL_RETARD_PENALITE_PP_MIN', 'SEUIL_CONGES_PENALITE_PP_JOURS']
    },
    {
        id: 'PAIE_EXO_HS',
        categorie: 'PAIE',
        titre: "Exonération Fiscale des Heures Supplémentaires",
        sousTitre: "Exonération d'IRSA dans la limite du contingent mensuel légal",
        icone: ShieldCheck,
        regleConjointe: "Exonération d'IRSA sur les heures supplémentaires dans la limite du plafond mensuel (CGI Art. 01.03.03-9°).",
        effetCascade: "Si cette règle est désactivée, toutes les heures supplémentaires sont soumises à l'IRSA.",
        masterCode: 'EXONERATION_HS_IRSA_ACTIVE',
        codes: ['EXONERATION_HS_IRSA_ACTIVE', 'PLAFOND_MENSUEL_HS_EXONEREES']
    },
    {
        id: 'PAIE_FISCALITE',
        categorie: 'PAIE',
        titre: "Fiscalité Prestataires (ISI)",
        sousTitre: "Retenue à la source sur prestations de services",
        icone: Coins,
        regleConjointe: "Retenue libératoire de 5% appliquée sur les règlements de prestataires sans NIF/STAT régulier (CGI).",
        effetCascade: "Constante fiscale obligatoire.",
        codes: ['TAUX_ISI']
    },

    // --- POINTAGE & TEMPS DE TRAVAIL ---
    {
        id: 'POINTAGE_MIDI',
        categorie: 'POINTAGE',
        titre: "Restauration du Midi & Déplacements",
        sousTitre: "Indemnité repas et déduction transport en cas de cantine",
        icone: Utensils,
        regleConjointe: "Attribution du repas de midi avec déduction sur le transport si consommé sur site.",
        effetCascade: "Si la déduction est désactivée, l'indemnité déplacement reste intégrale.",
        codes: ['MONTANT_REPAS_MIDI', 'SEUIL_PAUSE_MIDI_MIN', 'REDUCTION_DEPLACT_REPAS_MIDI']
    },
    {
        id: 'POINTAGE_2ND_REPAS',
        categorie: 'POINTAGE',
        titre: "Deuxième Repas pour Sorties Tardives",
        sousTitre: "Indemnités accordées selon l'horaire de fin de poste",
        icone: Coffee,
        regleConjointe: "Repas alloué après 19h30 (Tranche 1) et majoré après 21h00 (Tranche 2).",
        effetCascade: "Si une tranche est désactivée, l'indemnité correspondante n'est pas attribuée.",
        codes: ['SEUIL_2ND_REPAS_1', 'MONTANT_2ND_REPAS_1', 'SEUIL_2ND_REPAS_2', 'MONTANT_2ND_REPAS_2']
    },
    {
        id: 'POINTAGE_NUIT',
        categorie: 'POINTAGE',
        titre: "Travail de Nuit (Art. 83)",
        sousTitre: "Plage horaire pour la majoration des heures de nuit (22h - 05h)",
        icone: Moon,
        regleConjointe: "Heures travaillées entre 22h et 5h ouvrant droit à une majoration légale de salaire.",
        effetCascade: "Constantes légales obligatoires.",
        codes: ['SEUIL_NUIT_DEBUT', 'SEUIL_NUIT_FIN']
    },
    {
        id: 'POINTAGE_HISTORIQUE',
        categorie: 'POINTAGE',
        titre: "Paramètres Historiques",
        sousTitre: "Référence hebdomadaire archivée",
        icone: Clock,
        regleConjointe: "Seuil hebdomadaire de 40 heures conservé pour traçabilité.",
        effetCascade: "Sans effet sur les calculs de paie actuels.",
        codes: ['SEUIL_HEURES_SEMAINE_COMPENSATION']
    },

    // --- CONGÉS & ABSENCES ---
    {
        id: 'CONGE_SOLDES_PLAFONDS',
        categorie: 'CONGE',
        titre: "Soldes & Prise Anticipée de Congés",
        sousTitre: "Plafonds de cumul, alertes et congés par anticipation",
        icone: Shield,
        regleConjointe: "Encadre le cumul des congés payés et les conditions de prise avec solde négatif.",
        effetCascade: "Si le plafond négatif est désactivé, la prise anticipée de congés est strictement bloquée.",
        masterCode: 'PLAFOND_SOLDE_CONGE_NEGATIF',
        codes: ['PLAFOND_CUMUL_SOLDE_CONGE_JOURS', 'PLAFOND_SOLDE_CONGE_NEGATIF', 'SEUIL_ALERTE_SOLDE_CONGE_FAIBLE', 'DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS', 'DUREE_REFERENCE_TCONGE_MOIS']
    },
    {
        id: 'CONGE_BONIFICATION',
        categorie: 'CONGE',
        titre: "Bonification d'Ancienneté",
        sousTitre: "Attribution progressive de congés supplémentaires",
        icone: Award,
        regleConjointe: "Attribution de congés additionnels par palier au-delà du seuil d'ancienneté.",
        effetCascade: "Si la bonification est désactivée, aucun jour supplémentaire d'ancienneté n'est accordé.",
        masterCode: 'JOURS_BONIFICATION_PAR_PALIER',
        codes: ['SEUIL_ANCIENNETE_BONIFICATION_ANS', 'PALIER_ANNEES_BONIFICATION', 'JOURS_BONIFICATION_PAR_PALIER']
    },
    {
        id: 'CONGE_PREAVIS',
        categorie: 'CONGE',
        titre: "Préavis de Dépôt de Congé",
        sousTitre: "Délais minimaux de soumission des demandes employés",
        icone: CalendarClock,
        regleConjointe: "Application du préavis court (≤ 3j) ou long (> 3j) selon la durée d'absence demandée.",
        effetCascade: "Règles appliquées lors de la validation des demandes sur le portail employé.",
        codes: ['SEUIL_JOURS_PREAVIS_LONG', 'DELAI_PREAVIS_COURT_JOURS', 'DELAI_PREAVIS_LONG_JOURS']
    },
    {
        id: 'CONGE_MATERNITE',
        categorie: 'CONGE',
        titre: "Congé Maternité",
        sousTitre: "Durées légales standard et pathologique",
        icone: HeartPulse,
        regleConjointe: "Durées maximales de suspension de contrat avec maintien des droits légaux.",
        effetCascade: "Constantes de protection légale de la maternité.",
        codes: ['DUREE_MAX_MATERNITE_STANDARD_JOURS', 'DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS']
    },

    // --- CONTRATS & CARRIÈRE ---
    {
        id: 'CONTRAT_CDD',
        categorie: 'CONTRAT',
        titre: "Encadrement des CDD (Art. 19)",
        sousTitre: "Durées maximales et renouvellements autorisés",
        icone: FileText,
        regleConjointe: "Plafonds légaux avant obligation de requalification du contrat en CDI.",
        effetCascade: "Tout dépassement déclenche une alerte de conformité juridique.",
        codes: ['DUREE_MAX_CONTRAT_CDD_MOIS', 'DUREE_MAX_CDD_MOIS', 'NB_MAX_RENOUVELLEMENTS_CDD']
    },

    // --- RH GÉNÉRAL ---
    {
        id: 'RH_GENERAL_NORMES',
        categorie: 'RH_GENERAL',
        titre: "Normes Opérationnelles RH",
        sousTitre: "Base journalière standard et renouvellement d'essai",
        icone: UserCheck,
        regleConjointe: "Norme quotidienne de 8 heures et limite d'un seul renouvellement d'essai.",
        effetCascade: "Constantes organisationnelles de référence.",
        codes: ['SEUIL_HEURES_JOUR', 'MAX_RENOUVELLEMENTS_ESSAI']
    }
];

const CATEGORIE_LABELS = {
    POINTAGE: "Pointage & Temps",
    CONGE: "Congés & Absences",
    CONTRAT: "Contrats & Carrière",
    RH_GENERAL: "RH Général",
    PAIE: "Constantes de Paie & Primes"
};

function getStatut(param) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const debut = param.dateDebut ? new Date(param.dateDebut) : null;
    if (debut) debut.setHours(0, 0, 0, 0);

    const fin = param.dateFin ? new Date(param.dateFin) : null;
    if (fin) fin.setHours(0, 0, 0, 0);

    if (!debut) return 'EN_VIGUEUR';
    if (debut > today) return 'PROGRAMME';
    if (!fin || fin >= today) return 'EN_VIGUEUR';
    return 'ARCHIVE';
}

function StatutBadge({ statut }) {
    const styles = {
        EN_VIGUEUR: 'bg-[var(--color-success-500)]/12 text-[var(--color-success-600)] border-[var(--color-success-500)]/25',
        PROGRAMME:  'bg-[var(--color-brand-500)]/12 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25',
        ARCHIVE:    'bg-[var(--color-fg-muted)]/10 text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]',
    };
    const labels = {
        EN_VIGUEUR: '● En vigueur',
        PROGRAMME:  '◆ Programmé',
        ARCHIVE:    '○ Archivé'
    };
    return (
        <span className={`inline-flex items-center px-2 py-0.5 text-[10px] font-bold border rounded-none uppercase tracking-wide ${styles[statut] || styles.ARCHIVE}`}>
            {labels[statut] || statut}
        </span>
    );
}

function formatPeriodeValidite(dateDebut, dateFin, statut) {
    const fmt = (d) => d ? new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }) : null;
    const dStr = fmt(dateDebut);
    const fStr = fmt(dateFin);

    if (statut === 'PROGRAMME') {
        return fStr ? `Prend effet le ${dStr} (jusqu'au ${fStr})` : `Prend effet le ${dStr}`;
    }
    if (statut === 'ARCHIVE') {
        return fStr ? `Applicable du ${dStr} au ${fStr}` : `Clos le ${fStr || dStr}`;
    }
    return fStr ? `En vigueur depuis le ${dStr} (jusqu'au ${fStr})` : `En vigueur depuis le ${dStr}`;
}

function formatValeurAffichage(code, valeur) {
    if (valeur == null || valeur === '') return '-';
    const v = String(valeur).trim();
    if (code === 'TAUX_PENALITE_PP') {
        const num = Number(v);
        return isNaN(num) ? v : `${Math.round(num * 100)}% (${num})`;
    }
    const meta = PARAMETRE_METADATA[code];
    if (meta?.unite) {
        if (meta.unite === 'Ar') {
            const num = Number(v);
            return isNaN(num) ? `${v} Ar` : `${num.toLocaleString('fr-MG', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} Ar`;
        }
        if (meta.unite === '%') {
            const num = Number(v);
            return isNaN(num) ? `${v}%` : `${num}%`;
        }
        if (meta.unite === 'heure') return v;
        return `${v} ${meta.unite}`;
    }

    const c = String(code).toUpperCase();
    if (c.includes('MONTANT') || c.includes('REDUCTION')) {
        const num = Number(v);
        return isNaN(num) ? `${v} Ar` : `${num.toLocaleString('fr-MG', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} Ar`;
    }
    if (c.includes('HEURE') || (c.includes('DUREE') && !c.includes('MOIS') && !c.includes('JOUR'))) {
        return `${v} h`;
    }
    if (c.includes('JOUR')) return `${v} j`;
    if (c.includes('MOIS')) return `${v} mois`;
    if (c.includes('ANS') || c.includes('ANNEE')) return `${v} ans`;

    return v;
}

export default function ParametresGlobauxTab({ fixedCategory = null }) {
    // État local : données, filtres, version et modale d'édition.
    const toast = useToast();
    const [parametres, setParametres] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Filtres et Navigation
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedCategory, setSelectedCategory] = useState(fixedCategory || 'ALL');
    const [selectedStatut, setSelectedStatut] = useState('EN_VIGUEUR'); // Par défaut : uniquement les paramètres ayant pris effet (En vigueur)
    const [selectedNature, setSelectedNature] = useState('ALL'); // ALL, RULES, SYSTEM
    const [selectedEtatActif, setSelectedEtatActif] = useState('ALL'); // ALL, ACTIF, INACTIF
    const [viewMode, setViewMode] = useState('THEMES'); // THEMES par défaut (ergonomique et groupé), TABLE et CARDS disponibles
    // Blocs thématiques repliés par défaut (state vide = tous repliés)
    const [expandedBlocks, setExpandedBlocks] = useState({});

    // Actions
    const [togglingId, setTogglingId] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [isNouvelleVersion, setIsNouvelleVersion] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [modalError, setModalError] = useState(null);

    const [formData, setFormData] = useState({
        idParametre: null,
        codeParametre: '',
        valeur: '',
        description: '',
        categorie: 'POINTAGE',
        dateDebut: '',
        dateFin: '',
        estActif: true,
        estDesactivable: false
    });

    const toggleExpandBlock = (blockId) => {
        setExpandedBlocks(prev => ({
            ...prev,
            [blockId]: !prev[blockId]
        }));
    };

    const handleExpandAll = () => {
        const all = {};
        THEMATIC_BLOCKS.forEach(b => { all[b.id] = true; });
        all['ORPHANS'] = true;
        setExpandedBlocks(all);
    };

    const handleCollapseAll = () => setExpandedBlocks({});

    const loadParametres = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await paieConfigService.getParametresGlobaux(fixedCategory);
            setParametres(data || []);
        } catch (err) {
            console.error('Erreur chargement paramètres globaux:', err);
            setError("Impossible de charger les paramètres. Veuillez réessayer.");
        } finally {
            setLoading(false);
        }
    }, [fixedCategory]);

    // Effet : charge les paramètres à l'ouverture ou au changement de périmètre.
    useEffect(() => {
        loadParametres();
    }, [loadParametres]);

    // Calcul des statistiques globales
    const metrics = useMemo(() => {
        const total = parametres.length;
        let enVigueur = 0;
        let programme = 0;
        let archive = 0;
        let reglesActives = 0;
        let reglesInactives = 0;
        let constantesSysteme = 0;

        parametres.forEach(p => {
            const st = getStatut(p);
            if (st === 'EN_VIGUEUR') {
                enVigueur++;
                if (p.estDesactivable) {
                    if (p.estActif) reglesActives++;
                    else reglesInactives++;
                } else {
                    constantesSysteme++;
                }
            } else if (st === 'PROGRAMME') {
                programme++;
            } else if (st === 'ARCHIVE') {
                archive++;
            }
        });

        return { total, enVigueur, programme, archive, reglesActives, reglesInactives, constantesSysteme };
    }, [parametres]);

    // Catégories uniques disponibles
    const availableCategories = useMemo(() => {
        const setCats = new Set(parametres.map(p => p.categorie).filter(Boolean));
        const cats = Array.from(setCats);
        return cats.map(c => ({ value: c, label: CATEGORIE_LABELS[c] || c }));
    }, [parametres]);

    // Indexation rapide des paramètres vers leurs blocs thématiques
    const paramToBlockMap = useMemo(() => {
        const map = new Map();
        THEMATIC_BLOCKS.forEach(b => {
            b.codes.forEach(c => map.set(c, b));
        });
        return map;
    }, []);

    // Filtrage dynamique
    const filteredParametres = useMemo(() => {
        return parametres.filter(p => {
            // Filtre Catégorie
            if (selectedCategory !== 'ALL' && p.categorie !== selectedCategory) return false;

            // Filtre Statut temporel
            const statut = getStatut(p);
            if (selectedStatut !== 'ALL' && statut !== selectedStatut) return false;

            // Filtre Nature (Règles débrayables vs Constantes système protégées)
            if (selectedNature === 'RULES' && !p.estDesactivable) return false;
            if (selectedNature === 'SYSTEM' && p.estDesactivable) return false;

            // Filtre État Actif/Inactif
            if (selectedEtatActif === 'ACTIF' && !p.estActif) return false;
            if (selectedEtatActif === 'INACTIF' && p.estActif) return false;

            // Filtre Recherche
            if (searchTerm.trim()) {
                const q = searchTerm.toLowerCase();
                const code = (p.codeParametre || '').toLowerCase();
                const desc = (p.description || '').toLowerCase();
                const meta = PARAMETRE_METADATA[p.codeParametre];
                const titre = (meta?.titre || '').toLowerCase();
                const val = (p.valeur || '').toLowerCase();

                return code.includes(q) || desc.includes(q) || titre.includes(q) || val.includes(q);
            }

            return true;
        });
    }, [parametres, selectedCategory, selectedStatut, selectedNature, selectedEtatActif, searchTerm]);

    // Blocs thématiques enrichis avec leurs paramètres réels, statut conjoint et détection des effets cascade
    const thematicBlocksWithData = useMemo(() => {
        // Filtrer les blocs par catégorie
        const categoryBlocks = THEMATIC_BLOCKS.filter(b => {
            if (selectedCategory !== 'ALL' && b.categorie !== selectedCategory) return false;
            return true;
        });

        const mapped = categoryBlocks.map(block => {
            // Tous les paramètres de la base appartenant à ce bloc
            const blockParams = parametres.filter(p => block.codes.includes(p.codeParametre));

            // Paramètre maître éventuel (interrupteur principal)
            const masterParam = block.masterCode ? blockParams.find(p => p.codeParametre === block.masterCode) : null;
            const isMasterInactive = masterParam ? (!masterParam.estActif && getStatut(masterParam) === 'EN_VIGUEUR') : false;

            // Paramètres en vigueur
            const enVigueurParams = blockParams.filter(p => getStatut(p) === 'EN_VIGUEUR');
            const hasDeactivable = blockParams.some(p => p.estDesactivable);
            const activeCount = enVigueurParams.filter(p => p.estActif).length;
            const totalCount = enVigueurParams.length || blockParams.length;

            let statusType = 'ALL_ACTIVE';
            if (!hasDeactivable) {
                statusType = 'SYSTEM_PROTECTED';
            } else if (isMasterInactive) {
                statusType = 'MASTER_OFF';
            } else if (activeCount === totalCount) {
                statusType = 'ALL_ACTIVE';
            } else if (activeCount === 0) {
                statusType = 'ALL_OFF';
            } else {
                statusType = 'PARTIAL_OFF';
            }

            // Paramètres filtrés pour l'affichage selon la barre d'outils
            const displayParams = blockParams.filter(p => {
                const statut = getStatut(p);
                if (selectedStatut !== 'ALL' && statut !== selectedStatut) return false;
                if (selectedNature === 'RULES' && !p.estDesactivable) return false;
                if (selectedNature === 'SYSTEM' && p.estDesactivable) return false;
                if (selectedEtatActif === 'ACTIF' && !p.estActif) return false;
                if (selectedEtatActif === 'INACTIF' && p.estActif) return false;

                if (searchTerm.trim()) {
                    const q = searchTerm.toLowerCase();
                    const code = (p.codeParametre || '').toLowerCase();
                    const desc = (p.description || '').toLowerCase();
                    const meta = PARAMETRE_METADATA[p.codeParametre];
                    const titre = (meta?.titre || '').toLowerCase();
                    const val = (p.valeur || '').toLowerCase();
                    const bTitre = (block.titre || '').toLowerCase();
                    const bDesc = (block.sousTitre || '').toLowerCase();

                    return code.includes(q) || desc.includes(q) || titre.includes(q) || val.includes(q) || bTitre.includes(q) || bDesc.includes(q);
                }

                return true;
            });

            return {
                ...block,
                allParams: blockParams,
                displayParams,
                masterParam,
                isMasterInactive,
                statusType,
                activeCount,
                totalCount
            };
        });

        // Détection de paramètres additionnels non référencés explicitement dans les blocs statiques
        const knownCodes = new Set(THEMATIC_BLOCKS.flatMap(b => b.codes));
        const orphanParams = parametres.filter(p => !knownCodes.has(p.codeParametre));
        if (orphanParams.length > 0) {
            const orphanDisplayParams = orphanParams.filter(p => {
                if (selectedCategory !== 'ALL' && p.categorie !== selectedCategory) return false;
                const statut = getStatut(p);
                if (selectedStatut !== 'ALL' && statut !== selectedStatut) return false;
                if (selectedNature === 'RULES' && !p.estDesactivable) return false;
                if (selectedNature === 'SYSTEM' && p.estDesactivable) return false;
                if (selectedEtatActif === 'ACTIF' && !p.estActif) return false;
                if (selectedEtatActif === 'INACTIF' && p.estActif) return false;
                if (searchTerm.trim()) {
                    const q = searchTerm.toLowerCase();
                    return (p.codeParametre || '').toLowerCase().includes(q) || (p.description || '').toLowerCase().includes(q);
                }
                return true;
            });

            if (orphanDisplayParams.length > 0) {
                mapped.push({
                    id: 'AUTRES_PARAMETRES',
                    categorie: orphanParams[0]?.categorie || 'RH_GENERAL',
                    titre: "Autres Paramètres Système",
                    sousTitre: "Paramètres complémentaires configurés dans l'environnement",
                    icone: Sliders,
                    regleConjointe: "Paramètres transversaux sans regroupement spécifique.",
                    effetCascade: "Gestion standard selon la politique de chaque paramètre.",
                    codes: orphanParams.map(p => p.codeParametre),
                    allParams: orphanParams,
                    displayParams: orphanDisplayParams,
                    masterParam: null,
                    isMasterInactive: false,
                    statusType: 'ALL_ACTIVE',
                    activeCount: orphanParams.filter(p => p.estActif).length,
                    totalCount: orphanParams.length
                });
            }
        }

        return mapped;
    }, [parametres, selectedCategory, selectedStatut, selectedNature, selectedEtatActif, searchTerm]);

    // Gestion de l'interrupteur d'activation direct
    const handleToggleActif = async (param, e) => {
        if (e) e.stopPropagation();

        if (getStatut(param) !== 'EN_VIGUEUR') {
            toast.error("Seule une version en vigueur peut être basculée directement.");
            return;
        }

        if (!param.estDesactivable) {
            toast.error("Ce paramètre ne peut pas être désactivé.");
            return;
        }

        const nouveauStatut = !param.estActif;
        setTogglingId(param.idParametre);

        try {
            await paieConfigService.toggleParametreGlobalActif(param.idParametre, nouveauStatut);
            setParametres(prev => prev.map(item =>
                item.idParametre === param.idParametre
                    ? { ...item, estActif: nouveauStatut }
                    : item
            ));
            const meta = PARAMETRE_METADATA[param.codeParametre];
            const nomParam = meta?.titre || param.codeParametre;
            toast.success(
                nouveauStatut
                    ? `${nomParam} activé.`
                    : (param.effetDesactivation || `${nomParam} désactivé.`)
            );
        } catch (err) {
            console.error('Erreur bascule statut paramètre:', err);
            toast.error(getErrorMessage(err, "Impossible de modifier l'état du paramètre."));
        } finally {
            setTogglingId(null);
        }
    };

    // Ouverture Modale Modification ou Versionnage
    const handleOpenEdit = (param, asNewVersion = false) => {
        setIsNouvelleVersion(asNewVersion);
        setModalError(null);

        const todayStr = fmtDateInput(new Date());

        setFormData({
            idParametre: asNewVersion ? null : param.idParametre,
            codeParametre: param.codeParametre,
            valeur: param.valeur || '',
            description: param.description || '',
            categorie: param.categorie || fixedCategory || 'PAIE',
            dateDebut: asNewVersion ? todayStr : fmtDateInput(param.dateDebut),
            dateFin: asNewVersion ? '' : fmtDateInput(param.dateFin),
            estActif: param.estActif != null ? param.estActif : true,
            estDesactivable: param.estDesactivable != null ? param.estDesactivable : false,
            effetDesactivation: param.effetDesactivation || '',
            historiqueNonConsomme: Boolean(param.historiqueNonConsomme)
        });

        setShowModal(true);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        setModalError(null);

        try {
            const payload = {
                codeParametre: formData.codeParametre,
                valeur: formData.valeur,
                description: formData.description,
                categorie: formData.categorie,
                dateDebut: formData.dateDebut,
                dateFin: formData.dateFin || null,
                estActif: formData.estActif,
                estDesactivable: formData.estDesactivable
            };

            const meta = PARAMETRE_METADATA[formData.codeParametre];
            const nomParam = meta?.titre || formData.codeParametre;

            if (isNouvelleVersion) {
                await paieConfigService.createParametreGlobal(payload);
                toast.success(`Nouvelle version de « ${nomParam} » programmée.`);
            } else {
                await paieConfigService.updateParametreGlobal(formData.idParametre, payload);
                toast.success(`« ${nomParam} » mis à jour.`);
            }

            setShowModal(false);
            await loadParametres();
        } catch (err) {
            console.error('Erreur sauvegarde paramètre global:', err);
            const msg = getErrorMessage(err, "Erreur lors de la sauvegarde du paramètre.");
            setModalError(msg);
        } finally {
            setSubmitting(false);
        }
    };

    if (loading && parametres.length === 0) {
        return (
            <div className="space-y-4">
                <SkeletonTable rows={6} />
            </div>
        );
    }

    // Rendu
    return (
        <div className="space-y-4">
            {error && <Alert type="error" message={error} />}

            {/* KPI STATS CARDS - GALAXAM IDENTITY (NO ROUNDED, PURE CSS TOKENS) */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5">
                {/* Total */}
                <button
                    type="button"
                    onClick={() => { setSelectedStatut('EN_VIGUEUR'); setSelectedNature('ALL'); setSelectedEtatActif('ALL'); }}
                    className="group text-left p-4 rounded-none border border-[var(--color-border-subtle)] bg-[var(--color-surface)] hover:border-[var(--color-border)] transition-all cursor-pointer"
                >
                    <div className="flex items-center justify-between text-xs text-[var(--color-fg-muted)] font-medium">
                        <span>Total Paramètres</span>
                        <Sliders size={16} className="text-[var(--color-fg-muted)]" />
                    </div>
                    <div className="text-2xl font-black text-[var(--color-fg)] mt-1.5 tracking-tight">
                        {metrics.enVigueur}
                    </div>
                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-1 flex items-center gap-1">
                        <span>{metrics.programme > 0 ? `${metrics.programme} programmé(s) à venir` : `${metrics.enVigueur} en vigueur`}</span>
                    </div>
                </button>

                {/* Règles Métier Actives */}
                <button
                    type="button"
                    onClick={() => { setSelectedNature('RULES'); setSelectedEtatActif('ACTIF'); setSelectedStatut('EN_VIGUEUR'); }}
                    className="group text-left p-4 rounded-none border border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/10 hover:border-[var(--color-success-500)]/60 transition-all cursor-pointer"
                >
                    <div className="flex items-center justify-between text-xs text-[var(--color-success-600)] font-medium">
                        <span>Règles Actives</span>
                        <span className="inline-block w-2 h-2 rounded-none bg-[var(--color-success-500)]" />
                    </div>
                    <div className="text-2xl font-black text-[var(--color-success-600)] mt-1.5 tracking-tight">
                        {metrics.reglesActives}
                    </div>
                    <div className="text-[11px] text-[var(--color-success-600)]/80 mt-1">
                        Politiques opérationnelles actives
                    </div>
                </button>

                {/* Règles Métier Inactives */}
                <button
                    type="button"
                    onClick={() => { setSelectedNature('RULES'); setSelectedEtatActif('INACTIF'); setSelectedStatut('EN_VIGUEUR'); }}
                    className="group text-left p-4 rounded-none border border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/10 hover:border-[var(--color-warning-500)]/60 transition-all cursor-pointer"
                >
                    <div className="flex items-center justify-between text-xs text-[var(--color-warning-600)] font-medium">
                        <span>Règles Désactivées</span>
                        <Power size={15} className="text-[var(--color-warning-500)]" />
                    </div>
                    <div className="text-2xl font-black text-[var(--color-warning-600)] mt-1.5 tracking-tight">
                        {metrics.reglesInactives}
                    </div>
                    <div className="text-[11px] text-[var(--color-warning-600)]/80 mt-1">
                        Coupées volontairement
                    </div>
                </button>

                {/* Constantes Système Incompressibles */}
                <button
                    type="button"
                    onClick={() => { setSelectedNature('SYSTEM'); setSelectedEtatActif('ALL'); setSelectedStatut('EN_VIGUEUR'); }}
                    className="group text-left p-4 rounded-none border border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/10 hover:border-[var(--color-brand-500)]/60 transition-all cursor-pointer"
                >
                    <div className="flex items-center justify-between text-xs text-[var(--color-brand-600)] font-medium">
                        <span>Constantes Système</span>
                        <Lock size={15} className="text-[var(--color-brand-500)]" />
                    </div>
                    <div className="text-2xl font-black text-[var(--color-brand-600)] mt-1.5 tracking-tight">
                        {metrics.constantesSysteme}
                    </div>
                    <div className="text-[11px] text-[var(--color-brand-600)]/80 mt-1">
                        Constantes système requises
                    </div>
                </button>
            </div>

            {/* Barre de Recherche, Filtres & Bascule de vue */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none p-4 space-y-3.5 shadow-xs">
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
                    <div className="relative flex-1">
                        <Input
                            placeholder="Rechercher un paramètre, une règle ou un bloc thématique (ex: Assiduité, Repas, Préavis, CDD...)"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            iconLeft={<Search size={15} className="text-[var(--color-fg-muted)]" />}
                        />
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                        {/* Filtre Catégorie */}
                        {!fixedCategory && (
                            <Select
                                value={selectedCategory}
                                onChange={(e) => setSelectedCategory(e.target.value)}
                                className="w-48 text-xs"
                            >
                                <option value="ALL">Tous les domaines</option>
                                {availableCategories.map(c => (
                                    <option key={c.value} value={c.value}>{c.label}</option>
                                ))}
                            </Select>
                        )}

                        {/* Filtre Nature */}
                        <Select
                            value={selectedNature}
                            onChange={(e) => setSelectedNature(e.target.value)}
                            className="w-44 text-xs"
                        >
                            <option value="ALL">Toutes natures</option>
                            <option value="RULES">Règles débrayables</option>
                            <option value="SYSTEM">Constantes système</option>
                        </Select>

                        {/* Filtre Activation */}
                        <Select
                            value={selectedEtatActif}
                            onChange={(e) => setSelectedEtatActif(e.target.value)}
                            className="w-40 text-xs"
                        >
                            <option value="ALL">Tous états</option>
                            <option value="ACTIF">Actifs seulement</option>
                            <option value="INACTIF">Désactivés seulement</option>
                        </Select>

                        {/* Filtre Statut temporel */}
                        <Select
                            value={selectedStatut}
                            onChange={(e) => setSelectedStatut(e.target.value)}
                            className="w-48 text-xs"
                        >
                            <option value="EN_VIGUEUR">En vigueur ({metrics.enVigueur})</option>
                            <option value="PROGRAMME">Programmés ({metrics.programme})</option>
                            <option value="ARCHIVE">Archivés ({metrics.archive})</option>
                            <option value="ALL">Tous les statuts ({metrics.total})</option>
                        </Select>

                        {/* Bascule Vue Blocs Thématiques / Tableau / Cartes */}
                        <div className="flex border border-[var(--color-border-subtle)] rounded-none overflow-hidden">
                            <button
                                type="button"
                                onClick={() => setViewMode('THEMES')}
                                className={`px-2.5 py-1.5 flex items-center gap-1.5 text-xs font-bold transition-colors cursor-pointer rounded-none ${
                                    viewMode === 'THEMES'
                                        ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                }`}
                                title="Vue Blocs Thématiques & Règles Conjointes (Recommandée)"
                                aria-label="Vue par Blocs Thématiques"
                            >
                                <Layers size={14} />
                                <span className="hidden sm:inline">Blocs Thématiques</span>
                            </button>
                            <button
                                type="button"
                                onClick={() => setViewMode('TABLE')}
                                className={`px-2.5 py-1.5 flex items-center gap-1.5 text-xs font-bold transition-colors cursor-pointer rounded-none ${
                                    viewMode === 'TABLE'
                                        ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                }`}
                                title="Vue Tableau Exhaustif"
                                aria-label="Affichage en tableau exhaustif"
                            >
                                <List size={14} />
                                <span className="hidden sm:inline">Tableau</span>
                            </button>
                            <button
                                type="button"
                                onClick={() => setViewMode('CARDS')}
                                className={`p-1.5 transition-colors cursor-pointer rounded-none ${
                                    viewMode === 'CARDS'
                                        ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                }`}
                                title="Vue Cartes Individuelles"
                                aria-label="Affichage en cartes individuelles"
                            >
                                <LayoutGrid size={14} />
                            </button>
                        </div>
                    </div>
                </div>

                {/* Indicateur contextuel et raccourcis d'affichage */}
                <div className="text-xs text-[var(--color-fg-muted)] flex flex-col sm:flex-row sm:items-center justify-between gap-2 pt-2 border-t border-[var(--color-border-subtle)]">
                    <span className="flex items-center gap-1.5">
                        <ShieldCheck size={14} className="text-[var(--color-brand-500)] shrink-0" />
                        <span>
                            Les paramètres sont organisés en <strong>blocs thématiques</strong> avec mise en évidence des <strong>règles conjointes</strong> et des <strong>effets cascades</strong>.
                        </span>
                    </span>

                    <div className="flex items-center justify-between sm:justify-end gap-3">
                        {viewMode === 'THEMES' && (
                            <div className="flex items-center gap-2">
                                <button
                                    type="button"
                                    onClick={handleExpandAll}
                                    className="text-[11px] font-semibold text-[var(--color-brand-600)] hover:underline cursor-pointer"
                                >
                                    Déplier tout
                                </button>
                                <span className="text-[var(--color-border-subtle)]">|</span>
                                <button
                                    type="button"
                                    onClick={handleCollapseAll}
                                    className="text-[11px] font-semibold text-[var(--color-fg-muted)] hover:underline cursor-pointer"
                                >
                                    Replier tout
                                </button>
                            </div>
                        )}
                        <span className="font-mono text-[11px] text-[var(--color-fg-muted)]">
                            {filteredParametres.length} / {parametres.length} affichés
                        </span>
                    </div>
                </div>
            </div>

            {/* VUE BLOCS THÉMATIQUES (VUE PAR DÉFAUT ERGONOMIQUE) */}
            {viewMode === 'THEMES' && (
                <div className="space-y-4">
                    {thematicBlocksWithData.filter(b => b.displayParams.length > 0).length === 0 ? (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none p-12">
                            <EmptyState
                                icon={Sliders}
                                title="Aucun paramètre ou bloc trouvé"
                                description="Aucun bloc ou paramètre ne correspond aux critères de filtre ou de recherche sélectionnés."
                            />
                        </div>
                    ) : (
                        thematicBlocksWithData
                            .filter(block => block.displayParams.length > 0)
                            .map(block => {
                                const BlockIcon = block.icone || Sliders;
                                const isExpanded = Boolean(expandedBlocks[block.id]) || Boolean(searchTerm.trim());
                                const isCollapsed = !isExpanded;

                                return (
                                    <div
                                        key={block.id}
                                        className={`border rounded-none shadow-xs transition-all overflow-hidden ${
                                            block.isMasterInactive
                                                ? 'border-[var(--color-warning-500)]/40 bg-[var(--color-surface)]'
                                                : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)]'
                                        }`}
                                    >
                                        {/* En-tête du Bloc Thématique */}
                                        <div
                                            onClick={() => toggleExpandBlock(block.id)}
                                            className="py-3 px-4 flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/50 cursor-pointer hover:bg-[var(--color-bg-subtle)] transition-colors select-none"
                                        >
                                            <div className="flex items-start gap-3 min-w-0">
                                                <div className={`p-2 rounded-none border shrink-0 mt-0.5 ${
                                                    block.isMasterInactive
                                                        ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] border-[var(--color-warning-500)]/25'
                                                        : 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25'
                                                }`}>
                                                    <BlockIcon size={18} />
                                                </div>
                                                <div className="min-w-0">
                                                    <div className="flex flex-wrap items-center gap-2">
                                                        <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                                            {block.titre}
                                                        </h3>
                                                        <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 border rounded-none bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]">
                                                            {CATEGORIE_LABELS[block.categorie] || block.categorie}
                                                        </span>
                                                        <span className="text-[10px] font-mono text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] px-1.5 py-0.5 border border-[var(--color-border-subtle)]">
                                                            {block.displayParams.length} paramètre{block.displayParams.length > 1 ? 's' : ''}
                                                        </span>
                                                    </div>
                                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5 leading-relaxed">
                                                        {block.sousTitre}
                                                    </p>
                                                </div>
                                            </div>

                                            <div className="flex items-center gap-2 self-end md:self-auto shrink-0" onClick={(e) => e.stopPropagation()}>
                                                {/* Statut du Bloc */}
                                                {block.statusType === 'MASTER_OFF' && (
                                                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-bold border rounded-none bg-[var(--color-warning-500)]/12 text-[var(--color-warning-600)] border-[var(--color-warning-500)]/30">
                                                        <AlertTriangle size={13} /> Règle Coupée
                                                    </span>
                                                )}
                                                {block.statusType === 'ALL_ACTIVE' && (
                                                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-bold border rounded-none bg-[var(--color-success-500)]/12 text-[var(--color-success-600)] border-[var(--color-success-500)]/30">
                                                        <CheckCircle2 size={13} /> Active ({block.activeCount}/{block.totalCount})
                                                    </span>
                                                )}
                                                {block.statusType === 'PARTIAL_OFF' && (
                                                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-bold border rounded-none bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] border-[var(--color-warning-500)]/25">
                                                        <AlertCircle size={13} /> Partiel ({block.activeCount}/{block.totalCount})
                                                    </span>
                                                )}
                                                {block.statusType === 'ALL_OFF' && (
                                                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-bold border rounded-none bg-[var(--color-fg-muted)]/12 text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]">
                                                        <XCircle size={13} /> Coupé ({block.activeCount}/{block.totalCount})
                                                    </span>
                                                )}
                                                {block.statusType === 'SYSTEM_PROTECTED' && (
                                                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-bold border rounded-none bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25">
                                                        <Lock size={12} /> Constantes Système
                                                    </span>
                                                )}

                                                <button
                                                    type="button"
                                                    onClick={() => toggleExpandBlock(block.id)}
                                                    className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-surface)] border border-transparent hover:border-[var(--color-border-subtle)] cursor-pointer rounded-none ml-1 transition-colors"
                                                    title={isCollapsed ? "Déplier ce bloc" : "Replier ce bloc"}
                                                    aria-label={isCollapsed ? `Déplier le bloc ${block.titre}` : `Replier le bloc ${block.titre}`}
                                                >
                                                    {isCollapsed ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
                                                </button>
                                            </div>
                                        </div>

                                        {/* Contenu Dépliable du Bloc */}
                                        {!isCollapsed && (
                                            <div className="space-y-3 p-4">
                                                {/* Bannière si le paramètre maître est désactivé */}
                                                {block.isMasterInactive && (
                                                    <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/25 text-xs text-[var(--color-warning-600)] flex items-start gap-2.5">
                                                        <AlertTriangle size={15} className="shrink-0 mt-0.5" />
                                                        <div>
                                                            <span className="font-bold">Bloc inactif : </span>
                                                            <span>{block.effetCascade}</span>
                                                        </div>
                                                    </div>
                                                )}

                                                {/* Fiche de synthèse concise sur la règle */}
                                                <div className="px-3 py-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)] flex items-center gap-2.5">
                                                    <Info size={14} className="shrink-0 text-[var(--color-brand-500)]" />
                                                    <span className="leading-relaxed">{block.regleConjointe}</span>
                                                </div>

                                                {/* Tableau compact des paramètres de ce bloc */}
                                                <div className="border border-[var(--color-border-subtle)] rounded-none overflow-x-auto">
                                                    <Table bordered={false}>
                                                        <Thead>
                                                            <Th>Paramètre</Th>
                                                            <Th>Rôle</Th>
                                                            <Th>Type</Th>
                                                            <Th>État</Th>
                                                            <Th>Valeur</Th>
                                                            <Th>Validité</Th>
                                                            <Th>Statut</Th>
                                                            <Th className="text-right">Actions</Th>
                                                        </Thead>
                                                        <Tbody>
                                                            {block.displayParams.map(p => {
                                                                const statut = getStatut(p);
                                                                const meta = PARAMETRE_METADATA[p.codeParametre];
                                                                const IconComp = meta?.icone || Sliders;
                                                                const titreAff = meta?.titre || p.codeParametre;
                                                                const valAff = formatValeurAffichage(p.codeParametre, p.valeur);
                                                                const estInactif = !p.estActif;

                                                                const isMaster = block.masterCode === p.codeParametre;
                                                                const isDependent = block.masterCode && !isMaster;
                                                                const isSuspendedByMaster = block.isMasterInactive && isDependent;

                                                                return (
                                                                    <Tr
                                                                        key={p.idParametre}
                                                                        className={`hover:bg-[var(--color-bg-subtle)] transition-colors ${
                                                                            estInactif || isSuspendedByMaster
                                                                                ? 'opacity-85 bg-[var(--color-warning-500)]/5'
                                                                                : ''
                                                                        }`}
                                                                    >
                                                                        {/* Paramètre (Libellé uniquement, sans code brut) */}
                                                                        <Td className="font-medium text-[var(--color-fg)]">
                                                                            <div className="flex items-center gap-2.5">
                                                                                <div className={`p-1.5 rounded-none border shrink-0 ${
                                                                                    estInactif
                                                                                        ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] border-[var(--color-warning-500)]/25'
                                                                                        : p.estDesactivable
                                                                                        ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] border-[var(--color-success-500)]/25'
                                                                                        : 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25'
                                                                                }`}>
                                                                                    <IconComp size={14} />
                                                                                </div>
                                                                                <div>
                                                                                    <div className="font-bold text-xs flex items-center gap-2">
                                                                                        <span>{titreAff}</span>
                                                                                    </div>
                                                                                    {meta?.explication && (
                                                                                        <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 line-clamp-1 max-w-sm" title={meta.explication}>
                                                                                            {meta.explication}
                                                                                        </div>
                                                                                    )}
                                                                                </div>
                                                                            </div>
                                                                        </Td>

                                                                        {/* Rôle */}
                                                                        <Td>
                                                                            {isMaster ? (
                                                                                <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-[var(--color-brand-500)]/12 text-[var(--color-brand-600)] border border-[var(--color-brand-500)]/30">
                                                                                    <Power size={10} /> Paramètre Maître
                                                                                </span>
                                                                            ) : isSuspendedByMaster ? (
                                                                                <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-[var(--color-warning-500)]/15 text-[var(--color-warning-600)] border border-[var(--color-warning-500)]/30" title="Ce paramètre est inopérant car le paramètre maître du bloc est désactivé">
                                                                                    <AlertTriangle size={10} /> Inactif (Bloc coupé)
                                                                                </span>
                                                                            ) : isDependent ? (
                                                                                <span className="inline-flex items-center px-2 py-0.5 text-[10px] font-medium bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]">
                                                                                    Paramètre associé
                                                                                </span>
                                                                            ) : (
                                                                                <span className="text-[11px] text-[var(--color-fg-muted)]">
                                                                                    Standard
                                                                                </span>
                                                                            )}
                                                                        </Td>

                                                                        {/* Nature / Type */}
                                                                        <Td>
                                                                            {p.estDesactivable ? (
                                                                                <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-success-600)] font-bold">
                                                                                    <CheckCircle2 size={12} /> Débrayable
                                                                                </span>
                                                                            ) : p.historiqueNonConsomme ? (
                                                                                <span className="text-[11px] text-[var(--color-fg-muted)]">Non utilisé</span>
                                                                            ) : (
                                                                                <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-brand-600)] font-bold">
                                                                                    <Lock size={12} /> Système
                                                                                </span>
                                                                            )}
                                                                        </Td>

                                                                        {/* État / Switch direct */}
                                                                        <Td>
                                                                            {p.estDesactivable ? (
                                                                                <div className="flex items-center gap-2">
                                                                                    <button
                                                                                        type="button"
                                                                                        role="switch"
                                                                                        aria-checked={p.estActif}
                                                                                        disabled={togglingId === p.idParametre || statut !== 'EN_VIGUEUR'}
                                                                                        title={statut !== 'EN_VIGUEUR' ? 'Version non en vigueur' : (p.effetDesactivation || 'Basculer cette règle')}
                                                                                        onClick={(e) => handleToggleActif(p, e)}
                                                                                        className={`relative inline-flex h-4 w-7 shrink-0 cursor-pointer rounded-none border transition-colors duration-150 ease-in-out ${
                                                                                            p.estActif 
                                                                                                ? 'bg-[var(--color-success-500)] border-[var(--color-success-600)]' 
                                                                                                : 'bg-[var(--color-bg-subtle)] border-[var(--color-border)]'
                                                                                        }`}
                                                                                    >
                                                                                        <span
                                                                                            className={`pointer-events-none inline-block h-3 w-3 rounded-none shadow-xs transform transition duration-150 ease-in-out mt-[1px] ml-[1px] ${
                                                                                                p.estActif ? 'translate-x-3 bg-[var(--color-surface)]' : 'translate-x-0 bg-[var(--color-fg-muted)]'
                                                                                            }`}
                                                                                        />
                                                                                    </button>
                                                                                    <span className={`text-[11px] font-bold ${
                                                                                        isSuspendedByMaster
                                                                                            ? 'text-[var(--color-warning-600)]'
                                                                                            : p.estActif
                                                                                            ? 'text-[var(--color-success-600)]'
                                                                                            : 'text-[var(--color-warning-600)]'
                                                                                    }`}>
                                                                                        {isSuspendedByMaster ? 'Suspendu' : (p.estActif ? 'Actif' : 'Coupé')}
                                                                                    </span>
                                                                                </div>
                                                                            ) : (
                                                                                <span className="text-[11px] text-[var(--color-fg-muted)] font-medium flex items-center gap-1">
                                                                                    <Lock size={11} /> {p.historiqueNonConsomme ? 'Historique' : 'Toujours actif'}
                                                                                </span>
                                                                            )}
                                                                        </Td>

                                                                        {/* Valeur */}
                                                                        <Td className={`font-mono text-xs font-bold ${
                                                                            estInactif || isSuspendedByMaster ? 'line-through text-[var(--color-fg-muted)]' : 'text-[var(--color-fg)]'
                                                                        }`}>
                                                                            {valAff}
                                                                        </Td>

                                                                        {/* Période de Validité */}
                                                                        <Td className="text-xs text-[var(--color-fg-muted)]">
                                                                            {formatPeriodeValidite(p.dateDebut, p.dateFin, statut)}
                                                                        </Td>

                                                                        {/* Statut temporel */}
                                                                        <Td>
                                                                            <StatutBadge statut={statut} />
                                                                        </Td>

                                                                        {/* Actions */}
                                                                        <Td className="text-right">
                                                                            <div className="inline-flex items-center gap-1.5">
                                                                                {statut !== 'PROGRAMME' && (
                                                                                    <button
                                                                                        onClick={() => handleOpenEdit(p, true)}
                                                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-surface)] rounded-none border border-transparent hover:border-[var(--color-border-subtle)] cursor-pointer"
                                                                                        title="Programmer une nouvelle version à date future"
                                                                                        aria-label={`Programmer nouvelle version pour ${titreAff}`}
                                                                                    >
                                                                                        <Plus size={15} />
                                                                                    </button>
                                                                                )}
                                                                                <button
                                                                                    onClick={() => handleOpenEdit(p, false)}
                                                                                    className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-surface)] rounded-none border border-transparent hover:border-[var(--color-border-subtle)] cursor-pointer"
                                                                                    title="Modifier cette version"
                                                                                    aria-label={`Modifier ${titreAff}`}
                                                                                >
                                                                                    <Edit2 size={15} />
                                                                                </button>
                                                                            </div>
                                                                        </Td>
                                                                    </Tr>
                                                                );
                                                            })}
                                                        </Tbody>
                                                    </Table>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })
                    )}
                </div>
            )}

            {/* VUE TABLEAU EXHAUSTIF */}
            {viewMode === 'TABLE' && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-xs overflow-hidden">
                    <Table bordered={false}>
                        <Thead>
                            <Th>Paramètre Métier</Th>
                            <Th>Bloc Thématique</Th>
                            <Th>Domaine</Th>
                            <Th>Nature</Th>
                            <Th>État</Th>
                            <Th>Valeur</Th>
                            <Th>Période de Validité</Th>
                            <Th>Statut</Th>
                            <Th className="text-right">Actions</Th>
                        </Thead>
                        <Tbody>
                            {filteredParametres.length === 0 ? (
                                <Tr>
                                    <Td colSpan={9} className="p-0">
                                        <EmptyState
                                            icon={Sliders}
                                            title="Aucun paramètre trouvé"
                                            description="Aucun paramètre ne correspond aux critères de recherche."
                                        />
                                    </Td>
                                </Tr>
                            ) : (
                                filteredParametres.map(p => {
                                    const statut = getStatut(p);
                                    const meta = PARAMETRE_METADATA[p.codeParametre];
                                    const IconComponent = meta?.icone || Sliders;
                                    const titreAffiche = meta?.titre || p.codeParametre;
                                    const valeurAffichee = formatValeurAffichage(p.codeParametre, p.valeur);
                                    const estInactif = !p.estActif;
                                    const block = paramToBlockMap.get(p.codeParametre);
                                    const isMaster = block?.masterCode === p.codeParametre;

                                    return (
                                        <Tr key={p.idParametre} className={`hover:bg-[var(--color-bg-subtle)] ${estInactif ? 'opacity-75 bg-[var(--color-warning-500)]/5' : ''}`}>
                                            <Td className="font-medium text-[var(--color-fg)]">
                                                <div className="flex items-center gap-2">
                                                    <div className={`p-1.5 rounded-none border shrink-0 ${
                                                        estInactif
                                                            ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] border-[var(--color-warning-500)]/25'
                                                            : p.estDesactivable
                                                            ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] border-[var(--color-success-500)]/25'
                                                            : 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25'
                                                    }`}>
                                                        <IconComponent size={14} />
                                                    </div>
                                                    <div>
                                                        <div className="font-bold text-xs flex items-center gap-1.5">
                                                            <span>{titreAffiche}</span>
                                                            {isMaster && (
                                                                <span className="text-[9px] font-bold px-1.5 py-0.2 border rounded-none bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25">
                                                                    Paramètre Maître
                                                                </span>
                                                            )}
                                                        </div>
                                                        {meta?.explication && (
                                                            <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 line-clamp-1 max-w-sm" title={meta.explication}>
                                                                {meta.explication}
                                                            </div>
                                                        )}
                                                        {!p.estActif && p.effetDesactivation && (
                                                            <div className="text-[11px] text-[var(--color-warning-600)] mt-1">{p.effetDesactivation}</div>
                                                        )}
                                                    </div>
                                                </div>
                                            </Td>
                                            <Td>
                                                {block ? (
                                                    <span className="text-xs font-semibold text-[var(--color-fg)]" title={block.sousTitre}>
                                                        {block.titre}
                                                    </span>
                                                ) : (
                                                    <span className="text-xs text-[var(--color-fg-muted)]">-</span>
                                                )}
                                            </Td>
                                            <Td>
                                                <span className="text-xs text-[var(--color-fg-muted)]">{CATEGORIE_LABELS[p.categorie] || p.categorie}</span>
                                            </Td>
                                            <Td>
                                                {p.estDesactivable ? (
                                                    <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-success-600)] font-bold">
                                                        <CheckCircle2 size={12} /> Débrayable
                                                    </span>
                                                ) : p.historiqueNonConsomme ? (
                                                    <span className="text-[11px] text-[var(--color-fg-muted)]">Non utilisé</span>
                                                ) : (
                                                    <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-brand-600)] font-bold">
                                                        <Lock size={12} /> Système
                                                    </span>
                                                )}
                                            </Td>
                                            <Td>
                                                {p.estDesactivable ? (
                                                    <div className="flex items-center gap-2">
                                                        <button
                                                            type="button"
                                                            role="switch"
                                                            aria-checked={p.estActif}
                                                            disabled={togglingId === p.idParametre || statut !== 'EN_VIGUEUR'}
                                                            title={statut !== 'EN_VIGUEUR' ? 'Version non en vigueur' : (p.effetDesactivation || 'Basculer cette règle')}
                                                            onClick={(e) => handleToggleActif(p, e)}
                                                            className={`relative inline-flex h-4 w-7 shrink-0 cursor-pointer rounded-none border transition-colors duration-150 ease-in-out ${
                                                                p.estActif 
                                                                    ? 'bg-[var(--color-success-500)] border-[var(--color-success-600)]' 
                                                                    : 'bg-[var(--color-bg-subtle)] border-[var(--color-border)]'
                                                            }`}
                                                        >
                                                            <span
                                                                className={`pointer-events-none inline-block h-3 w-3 rounded-none shadow-xs transform transition duration-150 ease-in-out mt-[1px] ml-[1px] ${
                                                                    p.estActif ? 'translate-x-3 bg-[var(--color-surface)]' : 'translate-x-0 bg-[var(--color-fg-muted)]'
                                                                }`}
                                                            />
                                                        </button>
                                                        <span className={`text-[11px] font-bold ${p.estActif ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'}`}>
                                                            {p.estActif ? 'Actif' : 'Coupé'}
                                                        </span>
                                                    </div>
                                                ) : (
                                                    <span className="text-[11px] text-[var(--color-fg-muted)] font-medium flex items-center gap-1">
                                                        <Lock size={11} /> {p.historiqueNonConsomme ? 'Historique' : 'Toujours actif'}
                                                    </span>
                                                )}
                                            </Td>
                                            <Td className={`font-mono text-xs font-bold ${estInactif ? 'line-through text-[var(--color-fg-muted)]' : ''}`}>
                                                {valeurAffichee}
                                            </Td>
                                            <Td className="text-xs text-[var(--color-fg-muted)]">
                                                {formatPeriodeValidite(p.dateDebut, p.dateFin, statut)}
                                            </Td>
                                            <Td>
                                                <StatutBadge statut={statut} />
                                            </Td>
                                            <Td className="text-right">
                                                <div className="inline-flex items-center gap-1.5">
                                                    {statut !== 'PROGRAMME' && (
                                                        <button
                                                            onClick={() => handleOpenEdit(p, true)}
                                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none border border-transparent hover:border-[var(--color-border-subtle)] cursor-pointer"
                                                            title="Programmer une nouvelle version à date future"
                                                            aria-label={`Programmer nouvelle version pour ${titreAffiche}`}
                                                        >
                                                            <Plus size={15} />
                                                        </button>
                                                    )}
                                                    <button
                                                        onClick={() => handleOpenEdit(p, false)}
                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none border border-transparent hover:border-[var(--color-border-subtle)] cursor-pointer"
                                                        title="Modifier cette version"
                                                        aria-label={`Modifier ${titreAffiche}`}
                                                    >
                                                        <Edit2 size={15} />
                                                    </button>
                                                </div>
                                            </Td>
                                        </Tr>
                                    );
                                })
                            )}
                        </Tbody>
                    </Table>
                </div>
            )}

            {/* VUE CARTES INDIVIDUELLES (GROUPÉES PAR BLOCS THÉMATIQUES) */}
            {viewMode === 'CARDS' && (
                <div className="space-y-6">
                    {thematicBlocksWithData.filter(b => b.displayParams.length > 0).length === 0 ? (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none p-12">
                            <EmptyState
                                icon={Sliders}
                                title="Aucun paramètre trouvé"
                                description="Aucun paramètre ou règle ne correspond aux critères sélectionnés."
                            />
                        </div>
                    ) : (
                        thematicBlocksWithData
                            .filter(block => block.displayParams.length > 0)
                            .map(block => {
                                const BlockIcon = block.icone || Sliders;

                                return (
                                    <div key={block.id} className="space-y-3.5">
                                        <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                                            <div className="flex items-center gap-2">
                                                <BlockIcon size={16} className="text-[var(--color-brand-500)]" />
                                                <span className="text-xs font-bold uppercase tracking-widest text-[var(--color-fg)]">
                                                    {block.titre}
                                                </span>
                                                <span className="text-[10px] font-bold text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] px-2 py-0.5 rounded-none font-mono">
                                                    {block.displayParams.length}
                                                </span>
                                            </div>
                                            <span className="text-[11px] text-[var(--color-fg-muted)] hidden sm:inline">
                                                {block.sousTitre}
                                            </span>
                                        </div>

                                        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                                            {block.displayParams.map(param => {
                                                const statut = getStatut(param);
                                                const meta = PARAMETRE_METADATA[param.codeParametre];
                                                const IconComponent = meta?.icone || Sliders;
                                                const titreAffiche = meta?.titre || param.codeParametre;
                                                const valeurAffichee = formatValeurAffichage(param.codeParametre, param.valeur);
                                                const estInactif = !param.estActif;
                                                const isMaster = block.masterCode === param.codeParametre;
                                                const isSuspendedByMaster = block.isMasterInactive && !isMaster;

                                                return (
                                                    <Card
                                                        key={param.idParametre}
                                                        bordered
                                                        className={`flex flex-col justify-between rounded-none border transition-all ${
                                                            estInactif || isSuspendedByMaster
                                                                ? 'border-dashed border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/5 opacity-85'
                                                                : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)]'
                                                        }`}
                                                    >
                                                        <CardHeader className="py-3 px-4 border-b border-[var(--color-border-subtle)]">
                                                            <div className="flex items-start justify-between gap-2.5">
                                                                <div className="flex items-center gap-2.5 min-w-0">
                                                                    <div className={`p-2 rounded-none border shrink-0 ${
                                                                        estInactif
                                                                            ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] border-[var(--color-warning-500)]/25'
                                                                            : param.estDesactivable
                                                                            ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] border-[var(--color-success-500)]/25'
                                                                            : 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25'
                                                                    }`}>
                                                                        <IconComponent size={16} />
                                                                    </div>
                                                                    <div className="min-w-0">
                                                                        <h3 className="text-xs font-bold text-[var(--color-fg)] truncate" title={titreAffiche}>
                                                                            {titreAffiche}
                                                                        </h3>
                                                                        {isMaster && (
                                                                            <span className="text-[9px] font-bold px-1.5 py-0.2 border rounded-none bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25 mt-0.5 inline-block">
                                                                                Paramètre Maître
                                                                            </span>
                                                                        )}
                                                                    </div>
                                                                </div>
                                                                <div className="shrink-0 flex items-center gap-1.5">
                                                                    <StatutBadge statut={statut} />
                                                                </div>
                                                            </div>
                                                        </CardHeader>

                                                        <CardBody className="py-3.5 px-4 space-y-3 flex-1">
                                                            {/* Switch / Badge d'activation */}
                                                            <div className="flex items-center justify-between p-2.5 rounded-none bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                                                                <div className="flex items-center gap-2">
                                                                    {param.estDesactivable ? (
                                                                        isSuspendedByMaster ? (
                                                                            <span className="flex items-center gap-1.5 text-[11px] font-bold text-[var(--color-warning-600)]" title="Suspendu par le paramètre maître">
                                                                                <AlertTriangle size={13} />
                                                                                Suspendu
                                                                            </span>
                                                                        ) : param.estActif ? (
                                                                            <span className="flex items-center gap-1.5 text-[11px] font-bold text-[var(--color-success-600)]">
                                                                                <CheckCircle2 size={13} />
                                                                                Règle Active
                                                                            </span>
                                                                        ) : (
                                                                            <span className="flex items-center gap-1.5 text-[11px] font-bold text-[var(--color-warning-600)]">
                                                                                <XCircle size={13} />
                                                                                Règle Coupée
                                                                            </span>
                                                                        )
                                                                    ) : param.historiqueNonConsomme ? (
                                                                        <span className="text-[11px] text-[var(--color-fg-muted)]">Non utilisé</span>
                                                                    ) : (
                                                                        <span className="flex items-center gap-1.5 text-[11px] font-bold text-[var(--color-brand-600)]" title="Constante système requise par le moteur de paie">
                                                                            <Lock size={12} />
                                                                            Constante Système
                                                                        </span>
                                                                    )}
                                                                </div>

                                                                {/* Toggle Switch Rectiligne Galaxam (rounded-none) */}
                                                                {param.estDesactivable ? (
                                                                    <button
                                                                        type="button"
                                                                        role="switch"
                                                                        aria-checked={param.estActif}
                                                                        disabled={togglingId === param.idParametre || statut !== 'EN_VIGUEUR'}
                                                                        onClick={(e) => handleToggleActif(param, e)}
                                                                        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-none border transition-colors duration-150 ease-in-out focus:outline-none ${
                                                                            param.estActif 
                                                                                ? 'bg-[var(--color-success-500)] border-[var(--color-success-600)]' 
                                                                                : 'bg-[var(--color-bg-subtle)] border-[var(--color-border)]'
                                                                        }`}
                                                                        title={statut !== 'EN_VIGUEUR' ? 'Version non en vigueur' : (param.effetDesactivation || 'Basculer cette règle')}
                                                                    >
                                                                        <span
                                                                            className={`pointer-events-none inline-block h-3.5 w-3.5 rounded-none shadow-xs transform transition duration-150 ease-in-out mt-[2px] ml-[2px] ${
                                                                                param.estActif ? 'translate-x-4 bg-[var(--color-surface)]' : 'translate-x-0 bg-[var(--color-fg-muted)]'
                                                                            }`}
                                                                        />
                                                                    </button>
                                                                ) : (
                                                                    <span className="text-[10px] text-[var(--color-fg-muted)] font-mono">
                                                                        {param.historiqueNonConsomme ? 'Sans effet calcul' : 'Non débrayable'}
                                                                    </span>
                                                                )}
                                                            </div>

                                                            {/* Valeur numérique / textuelle */}
                                                            <div>
                                                                <div className="text-[10px] text-[var(--color-fg-muted)] uppercase tracking-wider font-semibold">
                                                                    {estInactif || isSuspendedByMaster ? 'Valeur enregistrée (inactive)' : 'Valeur d’Application'}
                                                                </div>
                                                                <div className={`text-xl font-black mt-0.5 tracking-tight ${
                                                                    estInactif || isSuspendedByMaster ? 'text-[var(--color-fg-muted)] line-through' : 'text-[var(--color-fg)]'
                                                                }`}>
                                                                    {valeurAffichee}
                                                                </div>
                                                            </div>

                                                            {/* Explication métier */}
                                                            <div className="text-xs text-[var(--color-fg-muted)] leading-relaxed line-clamp-2" title={meta?.explication || param.description}>
                                                                {meta?.explication || param.description || '-'}
                                                            </div>
                                                            {estInactif && param.effetDesactivation && (
                                                                <div className="text-xs text-[var(--color-warning-600)]" role="status">
                                                                    {param.effetDesactivation}
                                                                </div>
                                                            )}

                                                            {/* Validité */}
                                                            <div className="pt-2 border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[11px] text-[var(--color-fg-muted)]">
                                                                <span className="flex items-center gap-1.5 truncate">
                                                                    <Calendar size={12} className="shrink-0" />
                                                                    {formatPeriodeValidite(param.dateDebut, param.dateFin, statut)}
                                                                </span>
                                                            </div>
                                                        </CardBody>

                                                        <CardFooter className="py-2.5 px-4 flex items-center justify-end gap-2 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] rounded-none">
                                                            {statut !== 'PROGRAMME' && (
                                                                <Button
                                                                    variant="ghost"
                                                                    size="xs"
                                                                    onClick={() => handleOpenEdit(param, true)}
                                                                    title="Programmer une nouvelle version à date d'effet future"
                                                                >
                                                                    <Plus size={13} className="mr-1" />
                                                                    Versionner
                                                                </Button>
                                                            )}
                                                            <Button
                                                                variant="secondary"
                                                                size="xs"
                                                                onClick={() => handleOpenEdit(param, false)}
                                                            >
                                                                <Edit2 size={13} className="mr-1" />
                                                                Modifier
                                                            </Button>
                                                        </CardFooter>
                                                    </Card>
                                                );
                                            })}
                                        </div>
                                    </div>
                                );
                            })
                    )}
                </div>
            )}

            {/* MODALE D'ÉDITION / NOUVELLE VERSION AGRANDIE */}
            {(() => {
                const meta = PARAMETRE_METADATA[formData.codeParametre];
                const ModalIcon = meta?.icone || Sliders;
                const titreHumain = meta?.titre || formData.codeParametre;

                return (
                    <Modal
                        isOpen={showModal}
                        onClose={() => setShowModal(false)}
                        title={
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]/20">
                                    <ModalIcon size={18} />
                                </div>
                                <div>
                                    <div className="text-sm font-bold text-[var(--color-fg)]">
                                        {isNouvelleVersion ? `Programmer une nouvelle version` : `Modifier le paramètre`}
                                    </div>
                                    <div className="text-xs text-[var(--color-fg-muted)]">
                                        {titreHumain}
                                    </div>
                                </div>
                            </div>
                        }
                        size="2xl"
                        footer={
                            <div className="flex items-center justify-between w-full">
                                <div className="text-[11px] text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <ShieldCheck size={14} className="text-[var(--color-brand-500)]" />
                                    <span>Traçabilité et historique des versions conservés</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <Button type="button" onClick={() => setShowModal(false)} variant="ghost" size="sm" disabled={submitting}>
                                        Annuler
                                    </Button>
                                    <Button type="submit" form="param-global-form" variant="primary" size="sm" loading={submitting}>
                                        {isNouvelleVersion ? "Enregistrer Nouvelle Version" : "Enregistrer Modifications"}
                                    </Button>
                                </div>
                            </div>
                        }
                    >
                        <form id="param-global-form" onSubmit={handleSave} className="space-y-4">
                            {modalError && <FormError message={modalError} />}

                            {/* Fiche de synthèse métier & avertissement temporel */}
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                <div className="md:col-span-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 text-xs space-y-1.5">
                                    <div className="font-bold text-[var(--color-fg)] flex items-center gap-1.5">
                                        <ShieldCheck size={14} className="text-[var(--color-brand-500)]" />
                                        <span>Règle d'Intégrité Temporelle</span>
                                    </div>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
                                        {isNouvelleVersion
                                            ? "Cette nouvelle valeur sera enregistrée sous le statut « Programmé ». Elle prendra automatiquement effet dès la date d'application choisie sans altérer les périodes antérieures scellées."
                                            : "Les ajustements directs prennent effet immédiatement pour les calculs de paie et pointages actifs. Les périodes clôturées conservent leur instantané immuable."}
                                    </p>
                                </div>

                                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 text-xs flex flex-col justify-between">
                                    <span className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                        Nature & Domaine
                                    </span>
                                    <div>
                                        <div className="font-bold text-xs text-[var(--color-fg)]">
                                            {formData.categorie}
                                        </div>
                                        <div className="text-[11px] text-[var(--color-brand-600)] font-medium mt-0.5">
                                            {formData.historiqueNonConsomme ? 'Ancien paramètre sans effet' : (formData.estDesactivable ? 'Règle débrayable' : 'Constante système')}
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Description métier détaillée */}
                            {(meta?.explication || formData.description) && (
                                <div className="p-2.5 bg-[var(--color-brand-500)]/5 border border-[var(--color-brand-500)]/20 text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                    <span className="font-semibold text-[var(--color-brand-600)] mr-1">Rôle opérationnel :</span>
                                    {meta?.explication || formData.description}
                                </div>
                            )}

                            {/* Grille principale des champs */}
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
                                <div>
                                    <Input
                                        label="Désignation"
                                        value={titreHumain}
                                        readOnly
                                        disabled
                                    />
                                </div>

                                <div>
                                    <Input
                                        label="Domaine d'application"
                                        value={formData.categorie}
                                        readOnly
                                        disabled
                                    />
                                </div>

                                <div className="md:col-span-2">
                                    <Input
                                        label={`Valeur du Paramètre ${meta?.unite ? `(${meta.unite})` : ''}`}
                                        required
                                        value={formData.valeur}
                                        onChange={(e) => setFormData({ ...formData, valeur: e.target.value })}
                                        placeholder={meta?.unite === 'Ar' ? "Ex: 350000.01 ou 2000" : "Ex: 8 ou 19:30"}
                                        hint={meta?.unite ? `Unité de compte : ${meta.unite}. Accepte les valeurs décimales (ex: 350000.01).` : "Saisie de la valeur pivot du moteur."}
                                        className="font-mono font-bold text-sm"
                                    />
                                </div>

                                {/* Option d'activation si le paramètre est débrayable */}
                                {formData.estDesactivable && (
                                    <div className="md:col-span-2 flex items-center justify-between p-3.5 border bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]">
                                        <div>
                                            <div className="text-xs font-bold text-[var(--color-fg)]">
                                                État d'application de la règle
                                            </div>
                                            <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                                {formData.estActif
                                                    ? (formData.effetDesactivation
                                                        ? `Si désactivée : ${formData.effetDesactivation}`
                                                        : 'La règle est actuellement active.')
                                                    : (formData.effetDesactivation || 'La règle est désactivée.')}
                                            </div>
                                        </div>
                                        <button
                                            type="button"
                                            role="switch"
                                            aria-checked={formData.estActif}
                                            onClick={() => setFormData({ ...formData, estActif: !formData.estActif })}
                                            className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-none border transition-colors duration-150 ease-in-out focus:outline-none ${
                                                formData.estActif 
                                                    ? 'bg-[var(--color-success-500)] border-[var(--color-success-600)]' 
                                                    : 'bg-[var(--color-bg-subtle)] border-[var(--color-border)]'
                                            }`}
                                        >
                                            <span
                                                className={`pointer-events-none inline-block h-3.5 w-3.5 rounded-none shadow-xs transform transition duration-150 ease-in-out mt-[2px] ml-[2px] ${
                                                    formData.estActif ? 'translate-x-4 bg-[var(--color-surface)]' : 'translate-x-0 bg-[var(--color-fg-muted)]'
                                                }`}
                                            />
                                        </button>
                                    </div>
                                )}

                                <div>
                                    <Input
                                        label="Date Début (Prise d'effet)"
                                        type="date"
                                        required
                                        value={fmtDateInput(formData.dateDebut)}
                                        onChange={(e) => setFormData({ ...formData, dateDebut: e.target.value })}
                                        hint={isNouvelleVersion ? "Date future d'entrée en vigueur" : "Début de la période d'application"}
                                    />
                                </div>

                                <div>
                                    <Input
                                        label="Date Fin (Optionnel)"
                                        type="date"
                                        value={fmtDateInput(formData.dateFin)}
                                        onChange={(e) => setFormData({ ...formData, dateFin: e.target.value })}
                                        hint="Laisser vide si en vigueur jusqu'à prochaine version"
                                    />
                                </div>

                                <div className="md:col-span-2">
                                    <Textarea
                                        label="Note explicative / Justification de la version"
                                        value={formData.description}
                                        onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                                        rows={2}
                                        placeholder="Ex: Mise à jour consécutive au nouvel accord d'entreprise..."
                                    />
                                </div>
                            </div>
                        </form>
                    </Modal>
                );
            })()}
        </div>
    );
}
