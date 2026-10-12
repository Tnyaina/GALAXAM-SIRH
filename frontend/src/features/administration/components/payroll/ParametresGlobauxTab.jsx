import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import {
    Sliders, Search, Calendar, ShieldCheck,
    Clock, Moon, Sun, Utensils, Timer, Bus,
    Coffee, Coins, Shield, Hourglass, AlertTriangle,
    Bell, CalendarClock, HeartHandshake, HeartPulse, ChevronDown,
    Award, TrendingUp, Gift, FileText, Repeat, UserCheck,
} from 'lucide-react';
import paieConfigService from '@/features/administration/api/paieConfigService';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import { Card } from '@/components/ui/data-display/Card';
import StatCard from '@/components/ui/data-display/StatCard';
import { fmtDateInput } from '@/utils/dateUtils';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { useAuth } from '@/context/AuthContext';
import { useSearchParams } from 'react-router-dom';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
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

function getStatut(param) { return param.statutTemporel || 'NON_DATE'; }

function StatutBadge({ statut }) {
    const styles = {
        EN_VIGUEUR: 'bg-[var(--color-success-500)]/12 text-[var(--color-success-600)] border-[var(--color-success-500)]/25',
        PROGRAMME:  'bg-[var(--color-brand-500)]/12 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25',
        ARCHIVE:    'bg-[var(--color-fg-muted)]/10 text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]',
    };
    const labels = {
        EN_VIGUEUR: '● En vigueur',
        PROGRAMME:  '◆ Programmé',
        ARCHIVE:    '○ Archivé',
        NON_DATE: 'Non daté'
    };
    return (
        <span className={`inline-flex items-center px-2 py-0.5 text-[10px] font-bold border rounded-none uppercase tracking-wide ${styles[statut] || styles.ARCHIVE}`}>
            {labels[statut] || statut}
        </span>
    );
}

function formatPeriodeValidite(dateDebut, dateFin, statut) {
    const fmt = (d) => d ? new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }) : null;
    if (!dateDebut) return dateFin ? `Jusqu’au ${fmt(dateFin)}` : 'Dates non renseignées';
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


    return v;
}

export default function ParametresGlobauxTab({ fixedCategory = null }) {
    // État local : données, filtres, version et modale d'édition.
    const toast = useToast();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE');
    const submitLock = useRef(false);
    const toggleLock = useRef(false);
    const requestController = useRef(null);
    const [details, setDetails] = useState(null);
    const [detailsError, setDetailsError] = useState('');
    const [collapsedBlocks, setCollapsedBlocks] = useState(() => new Set(THEMATIC_BLOCKS.map(b => b.id)));
    const [showFilters, setShowFilters] = useState(false);
    const [urlParams, setUrlParams] = useSearchParams();
    const filterPrefix = `pg_${fixedCategory || 'all'}_`;
    const setFilter = (name, value) => setUrlParams(previous => { const next = new URLSearchParams(previous); next.set(filterPrefix + name, value); return next; });
    const [parametres, setParametres] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Filtres et Navigation
    const searchTerm = urlParams.get(filterPrefix + 'q') || '';
    const selectedCategory = fixedCategory || urlParams.get(filterPrefix + 'categorie') || 'ALL';
    const selectedStatut = urlParams.get(filterPrefix + 'statut') || 'EN_VIGUEUR';
    const selectedNature = urlParams.get(filterPrefix + 'nature') || 'ALL';
    const selectedEtatActif = urlParams.get(filterPrefix + 'etat') || 'ALL';

    // Actions
    const [togglingId, setTogglingId] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [isNouvelleVersion, setIsNouvelleVersion] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [modalError, setModalError] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});
    const [confirmAnnulation, setConfirmAnnulation] = useState(null);
    const [annulationLoading, setAnnulationLoading] = useState(false);
    const [confirmToggle, setConfirmToggle] = useState(null);

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

    const loadParametres = useCallback(async () => {
        requestController.current?.abort();
        const controller = new AbortController();
        requestController.current = controller;
        setLoading(true);
        setError(null);
        try {
            const data = await paieConfigService.getParametresGlobaux(fixedCategory, controller.signal);
            if (!controller.signal.aborted) setParametres(data || []);
        } catch {
            if (controller.signal.aborted) return;
            setError("Impossible de charger les paramètres. Veuillez réessayer.");
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, [fixedCategory]);

    // Effet : charge les paramètres à l'ouverture ou au changement de périmètre.
    useEffect(() => {
        loadParametres();
        return () => requestController.current?.abort();
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
            // Conservé en base pour l'historique, mais remplacé par la durée légale
            // hebdomadaire de PaieConfigLegale et sans consommateur métier.
            if (p.codeParametre === 'SEUIL_HEURES_SEMAINE_COMPENSATION') return false;

            // Exclure les paramètres fiscaux désormais entièrement pilotés par la Loi de Finances
            if (p.codeParametre === 'EXONERATION_HS_IRSA_ACTIVE' || p.codeParametre === 'PLAFOND_MENSUEL_HS_EXONEREES'
                    || p.codeParametre === 'TAUX_ISI' || p.codeParametre === 'TAUX_ISI_POURCENT') {
                return false;
            }

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

    const thematicBlocksWithData = useMemo(() => {
        const groups = new Map();
        filteredParametres.forEach(param => {
            const block = paramToBlockMap.get(param.codeParametre);
            const id = block?.id || 'autres';
            if (!groups.has(id)) groups.set(id, { id, titre: block?.titre || 'Autres paramètres', sousTitre: block?.sousTitre || '', icone: block?.icone || Sliders, params: [] });
            groups.get(id).params.push(param);
        });
        return [...groups.values()];
    }, [filteredParametres, paramToBlockMap]);

    // Gestion de l'interrupteur d'activation / désactivation avec date de prise d'effet
    const handleToggleActif = (param, e) => {
        if (e) e.stopPropagation();

        if (toggleLock.current) return;
        if (!param.peutBasculer) {
            toast.error("Seule une version en vigueur peut être basculée directement.");
            return;
        }

        if (!param.estDesactivable) {
            toast.error("Ce paramètre ne peut pas être désactivé.");
            return;
        }

        setDetailsError('');
        const nouveauStatut = !param.estActif;
        setConfirmToggle({
            param,
            nouveauStatut,
            dateEffet: fmtDateInput(new Date()),
            loading: false,
            error: ''
        });
    };

    const handleConfirmToggle = async () => {
        if (!confirmToggle || confirmToggle.loading) return;
        const { param, nouveauStatut, dateEffet } = confirmToggle;
        if (!dateEffet) {
            setConfirmToggle(prev => ({ ...prev, error: "La date de prise d'effet est obligatoire." }));
            return;
        }

        setConfirmToggle(prev => ({ ...prev, loading: true, error: '' }));
        try {
            await paieConfigService.toggleParametreGlobalActif(param.idParametre, nouveauStatut, dateEffet);
            const meta = PARAMETRE_METADATA[param.codeParametre];
            const nomParam = meta?.titre || param.codeParametre;
            const dateStr = new Date(dateEffet).toLocaleDateString('fr-FR');
            toast.success(
                nouveauStatut
                    ? `${nomParam} réactivé avec prise d'effet au ${dateStr}.`
                    : (param.effetDesactivation || `${nomParam} désactivé avec prise d'effet au ${dateStr}.`)
            );
            setConfirmToggle(null);
            setDetails(null);
            await loadParametres();
        } catch (err) {
            setConfirmToggle(prev => ({
                ...prev,
                loading: false,
                error: getErrorMessage(err, "L’état n’a pas été modifié. Réessayez.")
            }));
        }
    };

    // Annulation d'une version PROGRAMMÉE (remise en vigueur de la version précédente)
    const handleConfirmAnnuler = async () => {
        if (!confirmAnnulation) return;
        setAnnulationLoading(true);
        try {
            await paieConfigService.annulerParametreGlobalProgramme(confirmAnnulation.idParametre);
            const meta = PARAMETRE_METADATA[confirmAnnulation.codeParametre];
            const nomParam = meta?.titre || confirmAnnulation.codeParametre;
            toast.success(`La version programmée de « ${nomParam} » a été annulée. La version précédente reste en vigueur.`);
            setConfirmAnnulation(null);
            setDetails(null);
            loadParametres();
        } catch (err) {
            toast.error(getErrorMessage(err, "Erreur lors de l'annulation de la version programmée."));
        } finally {
            setAnnulationLoading(false);
        }
    };

    // Ouverture Modale Modification ou Versionnage
    const handleOpenEdit = (param, asNewVersion = false) => {
        setFieldErrors({});
        setIsNouvelleVersion(asNewVersion);
        setModalError(null);

        const todayStr = fmtDateInput(new Date());
        const nextDay = new Date();
        nextDay.setDate(nextDay.getDate() + 1);
        const nextDayStr = fmtDateInput(nextDay);
        const isHolidayLeaveRule = param.codeParametre === 'CONGE_DECOMPTE_FERIES';

        setFormData({
            idParametre: asNewVersion ? null : param.idParametre,
            codeParametre: param.codeParametre,
            valeur: param.valeur || '',
            description: param.description || '',
            categorie: param.categorie || fixedCategory || 'PAIE',
            dateDebut: asNewVersion ? (isHolidayLeaveRule ? nextDayStr : todayStr) : fmtDateInput(param.dateDebut),
            dateFin: asNewVersion ? '' : fmtDateInput(param.dateFin),
            estActif: param.estActif != null ? param.estActif : true,
            estDesactivable: isHolidayLeaveRule || (param.estDesactivable != null ? param.estDesactivable : false),
            effetDesactivation: param.effetDesactivation || '',
            historiqueNonConsomme: Boolean(param.historiqueNonConsomme)
        });

        setShowModal(true);
    };

    const validateField = (field, value, draft = formData) => {
        if (field === 'valeur' && !String(value ?? '').trim()) return 'Renseignez une valeur.';
        if (field === 'dateDebut' && !value) return 'Choisissez la date de début.';
        if (field === 'dateFin' && value && (!draft.dateDebut || value < draft.dateDebut)) return 'La date de fin ne peut pas précéder la date de début.';
        return '';
    };
    const handleField = (field, value) => {
        setFormData(previous => ({ ...previous, [field]: value }));
        if (fieldErrors[field]) setFieldErrors(previous => ({ ...previous, [field]: validateField(field,value) }));
    };

    const handleSave = async (e) => {
        e.preventDefault();
        if (submitLock.current || submitting) return;
        const errors = Object.fromEntries(['valeur','dateDebut','dateFin'].map(field => [field,validateField(field,formData[field])]));
        setFieldErrors(errors);
        if (Object.values(errors).some(Boolean)) return;
        submitLock.current = true;
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
                toast.success(`Nouvelle version de « ${nomParam} » enregistrée.`);
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
            submitLock.current = false;
            setSubmitting(false);
        }
    };

    if (loading && parametres.length === 0) {
        return (
            <div className="space-y-4">
                <SkeletonTable rows={6} columns={5} />
            </div>
        );
    }

    return (
        <div className="space-y-3.5">
            {error && <Alert type="error" message={error} />}
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3.5">
                <StatCard title="Paramètres" value={metrics.total} subtext="Toutes les versions" icon={<Sliders size={18} />} color="brand" active={selectedStatut === 'ALL'} onClick={() => setFilter('statut', 'ALL')} />
                <StatCard title="En vigueur" value={metrics.enVigueur} subtext={`${metrics.reglesActives} règle(s) active(s)`} icon={<ShieldCheck size={18} />} color="success" active={selectedStatut === 'EN_VIGUEUR'} onClick={() => setFilter('statut', 'EN_VIGUEUR')} />
                <StatCard title="Programmés" value={metrics.programme} subtext="Versions à venir" icon={<Calendar size={18} />} color="info" active={selectedStatut === 'PROGRAMME'} onClick={() => setFilter('statut', 'PROGRAMME')} />
                <StatCard title="Archivés" value={metrics.archive} subtext="Versions précédentes" icon={<Clock size={18} />} color="neutral" active={selectedStatut === 'ARCHIVE'} onClick={() => setFilter('statut', 'ARCHIVE')} />
            </div>

            <Card className="overflow-visible p-0" shadow="sm">
                <div className="p-3.5 sm:p-4 space-y-3">
                    <div className="flex flex-col xl:flex-row xl:items-end gap-3">
                        <div className="flex-1 min-w-[220px]"><Input label="Rechercher" aria-label="Rechercher un paramètre" placeholder="Nom, code ou valeur…" value={searchTerm} onChange={event => setFilter('q', event.target.value)} iconLeft={<Search size={16} />} /></div>
                        <Select label="Validité" aria-label="Validité" value={selectedStatut} onChange={event => setFilter('statut',event.target.value)}>
                            <option value="EN_VIGUEUR">En vigueur ({metrics.enVigueur})</option><option value="PROGRAMME">Programmés ({metrics.programme})</option><option value="ARCHIVE">Archivés ({metrics.archive})</option><option value="ALL">Toutes les versions ({metrics.total})</option>
                        </Select>
                        <div className="flex gap-2">
                            <Button variant="outline" aria-expanded={showFilters} onClick={() => setShowFilters(previous => !previous)} iconLeft={<Sliders size={16} />}>{showFilters ? 'Masquer les filtres' : 'Plus de filtres'}</Button>
                        </div>
                    </div>
                    {showFilters && <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 border-t border-[var(--color-border-subtle)] pt-3">
                        {!fixedCategory && <Select label="Domaine" value={selectedCategory} onChange={event => setFilter('categorie',event.target.value)}><option value="ALL">Tous les domaines</option>{availableCategories.map(category => <option key={category.value} value={category.value}>{category.label}</option>)}</Select>}
                        <Select label="Nature" value={selectedNature} onChange={event => setFilter('nature',event.target.value)}><option value="ALL">Toutes les natures</option><option value="RULES">Règles désactivables</option><option value="SYSTEM">Constantes protégées</option></Select>
                        <Select label="Activation" value={selectedEtatActif} onChange={event => setFilter('etat',event.target.value)}><option value="ALL">Tous les états</option><option value="ACTIF">Actifs</option><option value="INACTIF">Désactivés</option></Select>
                    </div>}
                    <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--color-fg-muted)]">
                        <span>{filteredParametres.length} version(s) affichée(s) sur {parametres.length} dans ce domaine.</span>
                        {(searchTerm || selectedNature !== 'ALL' || selectedEtatActif !== 'ALL') && <Button variant="ghost" size="sm" onClick={() => { setFilter('q', ''); setFilter('nature', 'ALL'); setFilter('etat', 'ALL'); }}>Effacer les filtres</Button>}
                    </div>
                </div>
            </Card>

            {filteredParametres.length === 0 ? <Card><EmptyState title="Aucun paramètre" description="Aucune version ne correspond aux filtres sélectionnés." bordered={false} /></Card> : (
                <div className="space-y-3.5" aria-label="Paramètres globaux">
                    {thematicBlocksWithData.map(block => {
                        const BlockIcon = block.icone;
                        const isCollapsed = collapsedBlocks.has(block.id);
                        return <Card key={block.id} className="overflow-hidden p-0" shadow="sm">
                            <button
                                type="button"
                                aria-expanded={!isCollapsed}
                                aria-controls={`param-block-${block.id}`}
                                onClick={() => setCollapsedBlocks(previous => {
                                    const next = new Set(previous);
                                    if (next.has(block.id)) next.delete(block.id);
                                    else next.add(block.id);
                                    return next;
                                })}
                                className="w-full flex items-center gap-3 px-4 py-3 text-left bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] transition-colors"
                            >
                                <span className="flex h-9 w-9 items-center justify-center border border-[var(--color-brand-500)]/20 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)]"><BlockIcon size={18} /></span>
                                <span className="min-w-0 flex-1"><span role="heading" aria-level="2" className="block text-sm font-bold text-[var(--color-fg)]">{block.titre}</span>{block.sousTitre && <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">{block.sousTitre}</span>}</span>
                                <span className="text-xs font-semibold text-[var(--color-fg-muted)]">{block.params.length} règle(s)</span>
                                <ChevronDown size={16} aria-hidden="true" className={`shrink-0 text-[var(--color-fg-muted)] transition-transform ${isCollapsed ? '-rotate-90' : ''}`} />
                            </button>
                            <div id={`param-block-${block.id}`} hidden={isCollapsed} className="divide-y divide-[var(--color-border-subtle)]">
                                {block.params.map(param => {
                                    const meta = PARAMETRE_METADATA[param.codeParametre];
                                    const ParamIcon = meta?.icone || Sliders;
                                    const isProgramme = getStatut(param) === 'PROGRAMME';
                                    const hasActions = canWrite && (param.peutModifier || param.peutVersionner || isProgramme);
                                    return <article key={param.idParametre} className={`grid grid-cols-1 ${hasActions ? 'lg:grid-cols-[minmax(0,1fr)_auto]' : ''} gap-3 p-4 hover:bg-[var(--color-bg-subtle)]/50 transition-colors`}>
                                        <div className="flex gap-3 min-w-0">
                                            <span className="hidden sm:flex h-9 w-9 shrink-0 items-center justify-center border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)]"><ParamIcon size={17} /></span>
                                            <div className="min-w-0 flex-1">
                                                <div className="flex flex-wrap items-center gap-2">
                                                    <button type="button" onClick={() => { setDetailsError(''); setDetails(param); }} className="text-left text-sm font-semibold text-[var(--color-fg)] hover:text-[var(--color-brand-600)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus-ring)]">{meta?.titre || param.codeParametre}</button>
                                                    <StatutBadge statut={getStatut(param)} />
                                                </div>
                                                <p className="mt-1 text-xs text-[var(--color-fg-muted)] leading-relaxed">{param.description || meta?.explication || param.codeParametre}</p>
                                                <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-[var(--color-fg-muted)]">
                                                    <span><span className="text-[var(--color-fg-muted)]">Valeur actuelle :</span> <strong className="font-semibold text-[var(--color-fg)]">{formatValeurAffichage(param.codeParametre,param.valeur)}</strong></span>
                                                    <span>{formatPeriodeValidite(param.dateDebut,param.dateFin,getStatut(param))}</span>
                                                    <span className={param.estActif ? 'text-[var(--color-success-600)] font-medium' : 'text-[var(--color-fg-muted)]'}>{param.estActif ? 'Actif' : 'Désactivé'}{!param.estDesactivable ? ' · constante protégée' : ''}</span>
                                                </div>
                                            </div>
                                        </div>
                                        {hasActions && <div className="flex flex-wrap items-center justify-end gap-1 lg:min-w-[220px]">
                                            {param.peutModifier && <Button variant="outline" size="sm" onClick={() => handleOpenEdit(param,false)}>Modifier</Button>}
                                            {param.peutVersionner && <Button variant="ghost" size="sm" onClick={() => handleOpenEdit(param,true)}>Nouvelle version</Button>}
                                            {param.peutBasculer && (
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    className={param.estActif ? "text-[var(--color-danger-600)] hover:text-[var(--color-danger-700)] hover:bg-[var(--color-danger-500)]/10" : "text-[var(--color-success-600)] hover:text-[var(--color-success-700)] hover:bg-[var(--color-success-500)]/10"}
                                                    onClick={(e) => handleToggleActif(param, e)}
                                                >
                                                    {param.estActif ? 'Désactiver' : 'Activer'}
                                                </Button>
                                            )}
                                            {isProgramme && (
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    className="text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10"
                                                    onClick={() => setConfirmAnnulation(param)}
                                                >
                                                    Annuler
                                                </Button>
                                            )}
                                        </div>}
                                    </article>;
                                })}
                            </div>
                        </Card>;
                    })}
                </div>
            )}
            <Modal isOpen={!!details} onClose={() => setDetails(null)} title="Détail du paramètre" size="lg">
                {details && <div className="space-y-4">
                    <div><h3 className="font-semibold">{PARAMETRE_METADATA[details.codeParametre]?.titre || details.codeParametre}</h3><p className="font-mono text-xs text-[var(--color-fg-muted)]">{details.codeParametre}</p></div>
                    <p className="text-sm">{details.description || 'Description non renseignée.'}</p>
                    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm"><div><dt className="text-[var(--color-fg-muted)]">Valeur</dt><dd>{formatValeurAffichage(details.codeParametre,details.valeur)}</dd></div><div><dt className="text-[var(--color-fg-muted)]">Domaine</dt><dd>{CATEGORIE_LABELS[details.categorie] || details.categorie}</dd></div><div><dt className="text-[var(--color-fg-muted)]">Validité</dt><dd>{formatPeriodeValidite(details.dateDebut,details.dateFin,getStatut(details))}</dd></div><div><dt className="text-[var(--color-fg-muted)]">Version</dt><dd><StatutBadge statut={getStatut(details)} /></dd></div></dl>
                    {details.historiqueNonConsomme && <p className="text-sm text-[var(--color-fg-muted)]">Paramètre historique non consommé par le moteur.</p>}
                    {parametres.filter(param => param.codeParametre === details.codeParametre && param.idParametre !== details.idParametre).length > 0 && <div className="border-t border-[var(--color-border-subtle)] pt-3 space-y-2"><h4 className="font-semibold text-sm">Autres versions</h4>{parametres.filter(param => param.codeParametre === details.codeParametre && param.idParametre !== details.idParametre).map(param => <div key={param.idParametre} className="flex flex-wrap justify-between gap-2 text-sm"><span>{formatPeriodeValidite(param.dateDebut,param.dateFin,getStatut(param))}</span><span className="font-mono">{formatValeurAffichage(param.codeParametre,param.valeur)}</span></div>)}</div>}
                    {details.effetDesactivation && <div className="border-t border-[var(--color-border-subtle)] pt-3"><h4 className="font-semibold text-sm">Effet de la désactivation</h4><p className="text-sm text-[var(--color-fg-muted)]">{details.effetDesactivation}</p></div>}
                    {detailsError && <FormError message={detailsError} />}
                    {canWrite && details.peutBasculer && <Button variant="outline" onClick={event => handleToggleActif(details, event)}>{details.estActif ? 'Désactiver' : 'Activer'}</Button>}
                    {canWrite && getStatut(details) === 'PROGRAMME' && (
                        <Button
                            variant="ghost"
                            className="text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10"
                            onClick={() => setConfirmAnnulation(details)}
                        >
                            Annuler la programmation
                        </Button>
                    )}
                </div>}
            </Modal>
            {/* MODALE D'ÉDITION / NOUVELLE VERSION AGRANDIE */}
            {(() => {
                const meta = PARAMETRE_METADATA[formData.codeParametre];
                const ModalIcon = meta?.icone || Sliders;
                const titreHumain = meta?.titre || formData.codeParametre;

                return (
                    <Modal
                        isOpen={showModal}
                        onClose={() => { if (!submitLock.current) setShowModal(false); }}
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
                                    <span>{isNouvelleVersion ? 'Nouvelle version' : 'Version existante'}</span>
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
                        <form noValidate id="param-global-form" onSubmit={handleSave} className="space-y-4">
                            {modalError && <FormError message={modalError} />}

                            <p className="text-sm text-[var(--color-fg-muted)]">{isNouvelleVersion ? 'La nouvelle date de début clôture la version précédente à la veille.' : 'Vous modifiez la valeur et les dates de cette version.'}</p>

                            {/* Description métier détaillée */}
                            {formData.description && (
                                <div className="p-2.5 bg-[var(--color-brand-500)]/5 border border-[var(--color-brand-500)]/20 text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                    <span className="font-semibold text-[var(--color-brand-600)] mr-1">Rôle opérationnel :</span>
                                    {formData.description}
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
                                        onChange={(e) => handleField('valeur', e.target.value)}
                                        error={fieldErrors.valeur}
                                        onBlur={() => setFieldErrors(previous => ({ ...previous, valeur: validateField('valeur', formData.valeur) }))}
                                        placeholder={meta?.unite === 'Ar' ? "Ex: 350000.01 ou 2000" : "Ex: 8 ou 19:30"}
                                        hint={meta?.unite ? `Unité de compte : ${meta.unite}. Accepte les valeurs décimales (ex: 350000.01).` : "Saisie de la valeur pivot du moteur."}
                                        className="font-mono font-bold text-sm"
                                    />
                                </div>

                                {/* Option d'activation si le paramètre est débrayable */}
                                {formData.estDesactivable && (
                                    <div className="md:col-span-2 space-y-2">
                                        <div className="flex items-center justify-between p-3.5 border bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]">
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

                                        {!isNouvelleVersion && formData.dateDebut && (
                                            <div className="p-2.5 bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/25 text-xs text-[var(--color-fg-muted)] flex items-start gap-2">
                                                <AlertTriangle size={15} className="text-[var(--color-brand-600)] shrink-0 mt-0.5" />
                                                <div className="leading-relaxed">
                                                    <span className="font-semibold text-[var(--color-fg)]">Conseil de gestion :</span> Pour désactiver sans modifier cette version historique (débutée le {fmtDateInput(formData.dateDebut)}), préférez renseigner une <strong>Date Fin</strong> ou utiliser le bouton <strong>« Nouvelle version »</strong>.
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}

                                <div>
                                    <Input
                                        label="Date Début (Prise d'effet)"
                                        type="date"
                                        required
                                        value={fmtDateInput(formData.dateDebut)}
                                        onChange={(e) => handleField('dateDebut', e.target.value)}
                                        error={fieldErrors.dateDebut}
                                        onBlur={() => setFieldErrors(previous => ({ ...previous, dateDebut: validateField('dateDebut', formData.dateDebut) }))}
                                        hint={isNouvelleVersion ? "Date future d'entrée en vigueur" : "Début de la période d'application"}
                                    />
                                </div>

                                <div>
                                    <Input
                                        label="Date Fin (Optionnel)"
                                        type="date"
                                        value={fmtDateInput(formData.dateFin)}
                                        onChange={(e) => handleField('dateFin', e.target.value)}
                                        error={fieldErrors.dateFin}
                                        onBlur={() => setFieldErrors(previous => ({ ...previous, dateFin: validateField('dateFin', formData.dateFin) }))}
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
            {/* Modal de confirmation d'annulation d'une version programmée */}
            <ConfirmDialog
                isOpen={Boolean(confirmAnnulation)}
                onClose={() => setConfirmAnnulation(null)}
                onConfirm={handleConfirmAnnuler}
                title="Annuler la version programmée"
                confirmLabel="Annuler la version"
                cancelLabel="Conserver"
                variant="danger"
                loading={annulationLoading}
            >
                {confirmAnnulation && (
                    <div className="space-y-2">
                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                            Vous êtes sur le point d'annuler la version programmée de <strong className="text-[var(--color-fg)]">{PARAMETRE_METADATA[confirmAnnulation.codeParametre]?.titre || confirmAnnulation.codeParametre}</strong> (effet prévu le <strong className="text-[var(--color-fg)]">{fmtDateInput(confirmAnnulation.dateDebut)}</strong>).
                        </p>
                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                            Cette version future sera définitivement supprimée et la version précédente restera en vigueur sans date de fin.
                        </p>
                    </div>
                )}
            </ConfirmDialog>

            {/* MODALE DE CONFIRMATION AVEC DATE DE PRISE D'EFFET */}
            <Modal
                isOpen={Boolean(confirmToggle)}
                onClose={() => { if (!confirmToggle?.loading) setConfirmToggle(null); }}
                title={
                    <div className="flex items-center gap-2.5">
                        <div className={`p-1.5 ${confirmToggle?.nouveauStatut ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] border border-[var(--color-success-500)]/20' : 'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)] border border-[var(--color-danger-500)]/20'}`}>
                            <Shield size={18} />
                        </div>
                        <div>
                            <div className="text-sm font-bold text-[var(--color-fg)]">
                                {confirmToggle?.nouveauStatut ? "Confirmer la réactivation" : "Confirmer la désactivation"}
                            </div>
                            <div className="text-xs text-[var(--color-fg-muted)]">
                                {PARAMETRE_METADATA[confirmToggle?.param?.codeParametre]?.titre || confirmToggle?.param?.codeParametre}
                            </div>
                        </div>
                    </div>
                }
                size="md"
                footer={
                    <div className="flex items-center justify-end gap-2 w-full">
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => setConfirmToggle(null)}
                            disabled={confirmToggle?.loading}
                        >
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            variant={confirmToggle?.nouveauStatut ? "primary" : "danger"}
                            size="sm"
                            onClick={handleConfirmToggle}
                            loading={confirmToggle?.loading}
                        >
                            {confirmToggle?.nouveauStatut ? "Confirmer la réactivation" : "Confirmer la désactivation"}
                        </Button>
                    </div>
                }
            >
                {confirmToggle && (
                    <div className="space-y-4">
                        {confirmToggle.error && <FormError message={confirmToggle.error} />}

                        <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)] leading-relaxed space-y-1.5">
                            <p>
                                <strong className="text-[var(--color-fg)]">Préservation de l'historique :</strong>
                            </p>
                            <p>
                                {confirmToggle.nouveauStatut
                                    ? "La règle sera réactivée à la date d'effet choisie. Les calculs de paie antérieurs à cette date continueront de respecter l'ancien état."
                                    : (confirmToggle.param.effetDesactivation || "La règle cessera d'être appliquée à compter de la date d'effet choisie. La version en vigueur sera automatiquement clôturée à la veille pour sanctuariser les paies passées.")}
                            </p>
                        </div>

                        <div>
                            <Input
                                label="Date de prise d'effet"
                                type="date"
                                required
                                value={confirmToggle.dateEffet}
                                onChange={(e) => setConfirmToggle({ ...confirmToggle, dateEffet: e.target.value, error: '' })}
                                hint="Par défaut aujourd'hui. L'historique antérieur à cette date est préservé."
                                min={confirmToggle.param.dateDebut ? fmtDateInput(confirmToggle.param.dateDebut) : undefined}
                            />
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
}
