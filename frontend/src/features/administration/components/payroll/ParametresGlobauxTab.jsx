import { useState, useEffect, useMemo, useCallback } from 'react';
import {
    Sliders, Search, LayoutGrid, List,
    Edit2, Plus, Calendar, ShieldCheck,
    Lock, CheckCircle2, XCircle, Power,
    Clock, Moon, Sun, Utensils, Timer, Bus,
    Coffee, Coins, Shield, Hourglass, AlertTriangle,
    Bell, CalendarClock, HeartHandshake, HeartPulse,
    Award, TrendingUp, Gift, FileText, Repeat, UserCheck
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
        titre: "Durée Normale Journalière",
        icone: Clock,
        unite: "heures",
        explication: "Base quotidienne standard utilisée par le pointage pour mesurer le temps normal et les retards.",
    },
    SEUIL_NUIT_DEBUT: {
        titre: "Début Plage Travail de Nuit",
        icone: Moon,
        unite: "heure",
        explication: "Heure légale à partir de laquelle débute la majoration de travail de nuit (Art. 83).",
    },
    SEUIL_NUIT_FIN: {
        titre: "Fin Plage Travail de Nuit",
        icone: Sun,
        unite: "heure",
        explication: "Heure de fin du décompte des heures de nuit.",
    },
    MONTANT_REPAS_MIDI: {
        titre: "Indemnité Repas Midi (Référence)",
        icone: Utensils,
        unite: "Ar",
        explication: "Montant forfaitaire journalier accordé pour le repas du midi selon les accords de site.",
    },
    SEUIL_PAUSE_MIDI_MIN: {
        titre: "Pause Midi Minimale Détectée",
        icone: Timer,
        unite: "minutes",
        explication: "Seuil de durée de pause midi en-dessous duquel le salarié est considéré comme n'étant pas rentré.",
    },
    REDUCTION_DEPLACT_REPAS_MIDI: {
        titre: "Déduction Transport si Repas Cantine",
        icone: Bus,
        unite: "Ar",
        explication: "Déduction appliquée sur l'indemnité déplacement lorsque le repas midi de cantine est consommé sur site.",
    },
    SEUIL_2ND_REPAS_1: {
        titre: "Seuil Horaire 2ᵉ Repas (Tranche 1)",
        icone: Coffee,
        unite: "heure",
        explication: "Heure de sortie à partir de laquelle un deuxième repas distinct est accordé automatiquement.",
    },
    MONTANT_2ND_REPAS_1: {
        titre: "Montant 2ᵉ Repas (Tranche 1)",
        icone: Coins,
        unite: "Ar",
        explication: "Montant du deuxième repas, indépendant du premier repas configuré pour le site.",
    },
    SEUIL_2ND_REPAS_2: {
        titre: "Seuil Horaire 2ᵉ Repas (Tranche 2)",
        icone: Coffee,
        unite: "heure",
        explication: "Heure de sortie à partir de laquelle le deuxième repas majoré remplace la première tranche.",
    },
    MONTANT_2ND_REPAS_2: {
        titre: "Montant 2ᵉ Repas (Tranche 2)",
        icone: Coins,
        unite: "Ar",
        explication: "Montant du deuxième repas majoré, à la place du deuxième repas normal.",
    },

    // Congés & Absences
    PLAFOND_CUMUL_SOLDE_CONGE_JOURS: {
        titre: "Plafond optionnel du solde de congés",
        icone: Shield,
        unite: "jours",
        explication: "Actif, limite les acquisitions et bonifications au montant configuré. Désactivé, aucun plafond n'est appliqué.",
    },
    DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS: {
        titre: "Délai avant examen des droits de congé",
        icone: Hourglass,
        unite: "mois",
        explication: "Ancienneté à partir de laquelle les droits disponibles sont signalés pour examen par la DAF. Aucun retrait automatique du solde.",
    },
    PLAFOND_SOLDE_CONGE_NEGATIF: {
        titre: "Prise anticipée : plafond du solde négatif",
        icone: AlertTriangle,
        unite: "jours",
        explication: "Actif, autorise sous dérogation une prise anticipée dans la limite indiquée. Désactivé, la prise anticipée est refusée.",
    },
    SEUIL_ALERTE_SOLDE_CONGE_FAIBLE: {
        titre: "Seuil Alerte Solde Faible",
        icone: Bell,
        unite: "jours",
        explication: "Nombre de jours restants déclenchant automatiquement une notification de solde faible.",
    },
    DELAI_PREAVIS_COURT_JOURS: {
        titre: "Préavis Dépôt Congé Court",
        icone: CalendarClock,
        unite: "jours",
        explication: "Délai de prévenance minimal requis pour poser un congé de courte durée.",
    },
    DELAI_PREAVIS_LONG_JOURS: {
        titre: "Préavis Dépôt Congé Long",
        icone: CalendarClock,
        unite: "jours",
        explication: "Délai de prévenance minimal requis pour poser un congé de longue durée.",
    },
    SEUIL_JOURS_PREAVIS_LONG: {
        titre: "Seuil Bascule Préavis Long",
        icone: CalendarClock,
        unite: "jours",
        explication: "Nombre de jours demandés à partir duquel le préavis long devient obligatoire.",
    },
    DUREE_MAX_MATERNITE_STANDARD_JOURS: {
        titre: "Congé Maternité Standard",
        icone: HeartHandshake,
        unite: "jours",
        explication: "Durée légale maximale de base du congé maternité (14 semaines).",
    },
    DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS: {
        titre: "Congé Maternité Pathologique",
        icone: HeartPulse,
        unite: "jours",
        explication: "Durée maximale autorisée en cas d'extension pathologique médicalement justifiée.",
    },
    SEUIL_ANCIENNETE_BONIFICATION_ANS: {
        titre: "Seuil Ancienneté Bonification",
        icone: Award,
        unite: "ans",
        explication: "Ancienneté requise pour commencer à acquérir des jours de bonification d'ancienneté.",
    },
    PALIER_ANNEES_BONIFICATION: {
        titre: "Intervalle Tranche Bonification",
        icone: TrendingUp,
        unite: "ans",
        explication: "Rythme de progression pour acquérir un jour de bonification d'ancienneté additionnel.",
    },
    JOURS_BONIFICATION_PAR_PALIER: {
        titre: "Jours Accordés par Tranche",
        icone: Gift,
        unite: "jours",
        explication: "Nombre de jours de congés supplémentaires crédités à chaque palier d'ancienneté atteint.",
    },

    // Contrats & Carrière
    DUREE_MAX_CONTRAT_CDD_MOIS: {
        titre: "Durée Max Contrat CDD",
        icone: FileText,
        unite: "mois",
        explication: "Durée maximale d'un contrat CDD individuel ou d'un avenant (24 mois selon le Code du Travail malgache, Art. 19).",
    },
    DUREE_MAX_CDD_MOIS: {
        titre: "Plafond Durée Cumulée CDD",
        icone: FileText,
        unite: "mois",
        explication: "Plafond cumulé maximal de l'ensemble des CDD successifs d'un salarié (72 mois / 6 ans).",
    },
    NB_MAX_RENOUVELLEMENTS_CDD: {
        titre: "Renouvellements Max CDD",
        icone: Repeat,
        unite: "fois",
        explication: "Nombre maximal d'avenants de renouvellement avant obligation de CDI (2 fois max).",
    },
    MAX_RENOUVELLEMENTS_ESSAI: {
        titre: "Renouvellements Max Période d'Essai",
        icone: UserCheck,
        unite: "fois",
        explication: "Nombre maximal de renouvellements de période d'essai autorisés par le Code du Travail (1 fois).",
    },

    // Paie & Fiscalité
    TAUX_ISI: {
        titre: "Taux Retenue Libératoire ISI",
        icone: Coins,
        unite: "%",
        explication: "Taux légal d'Impôt Synthétique Intermédiaire (5%) retenu à la source sur le montant brut des honoraires des consultants et travailleurs temporaires (CGI).",
    },
    EXONERATION_HS_IRSA_ACTIVE: {
        titre: "Exonération Fiscale des HS",
        icone: ShieldCheck,
        unite: "",
        explication: "Activation globale de l'exonération d'IRSA sur les heures supplémentaires (CGI Art. 01.03.03-9°).",
    },
    PLAFOND_MENSUEL_HS_EXONEREES: {
        titre: "Plafond Mensuel HS Exonérées",
        icone: Clock,
        unite: "heures",
        explication: "Nombre maximum d'heures supplémentaires exonérées d'IRSA par mois (CGI Art. 01.03.03-9°).",
    },
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
    const [viewMode, setViewMode] = useState('TABLE'); // TABLE par défaut, CARDS disponible

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
        const mapLabels = {
            POINTAGE: "Pointage & Temps",
            CONGE: "Congés & Absences",
            CONTRAT: "Contrats & Carrière",
            RH_GENERAL: "RH Général"
        };
        return cats.map(c => ({ value: c, label: mapLabels[c] || c }));
    }, [parametres]);

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

    // Regroupement par Catégorie
    const groupedParametres = useMemo(() => {
        const map = new Map();
        filteredParametres.forEach(p => {
            const cat = p.categorie || 'RH_GENERAL';
            if (!map.has(cat)) map.set(cat, []);
            map.get(cat).push(p);
        });
        return map;
    }, [filteredParametres]);

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
            toast.success(
                nouveauStatut
                    ? `La règle ${param.codeParametre} a été activée.`
                    : (param.effetDesactivation || `La règle ${param.codeParametre} a été désactivée.`)
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
            categorie: param.categorie || 'POINTAGE',
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

            if (isNouvelleVersion) {
                await paieConfigService.createParametreGlobal(payload);
                toast.success(`Nouvelle version du paramètre ${formData.codeParametre} programmée.`);
            } else {
                await paieConfigService.updateParametreGlobal(formData.idParametre, payload);
                toast.success(`Paramètre ${formData.codeParametre} mis à jour.`);
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
                        Pivots de calcul sanctuarisés
                    </div>
                </button>
            </div>

            {/* Barre de Recherche, Filtres & Bascule de vue */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none p-4 space-y-3.5 shadow-xs">
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
                    <div className="relative flex-1">
                        <Input
                            placeholder="Rechercher par nom (ex: Cantine, Nuit, CDD, Ancienneté...)"
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

                        {/* Bascule Vue Cartes / Tableau */}
                        <div className="flex border border-[var(--color-border-subtle)] rounded-none overflow-hidden">
                            <button
                                type="button"
                                onClick={() => setViewMode('CARDS')}
                                className={`p-2 transition-colors cursor-pointer rounded-none ${
                                    viewMode === 'CARDS'
                                        ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                }`}
                                title="Affichage en cartes interactives"
                                aria-label="Affichage en cartes"
                            >
                                <LayoutGrid size={15} />
                            </button>
                            <button
                                type="button"
                                onClick={() => setViewMode('TABLE')}
                                className={`p-2 transition-colors cursor-pointer rounded-none ${
                                    viewMode === 'TABLE'
                                        ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                }`}
                                title="Affichage en tableau synthétique"
                                aria-label="Affichage en tableau"
                            >
                                <List size={15} />
                            </button>
                        </div>
                    </div>
                </div>

                {/* Indicateur contextuel sur la protection des constantes */}
                <div className="text-xs text-[var(--color-fg-muted)] flex items-center justify-between pt-2 border-t border-[var(--color-border-subtle)]">
                    <span className="flex items-center gap-1.5">
                        <ShieldCheck size={14} className="text-[var(--color-brand-500)] shrink-0" />
                        <span>
                            Les <strong>règles débrayables</strong> peuvent être coupées individuellement. Les <strong>constantes système</strong> (8h/jour, seuils légaux de nuit) sont sanctuarisées.
                        </span>
                    </span>
                    <span className="font-mono text-[11px] text-[var(--color-fg-muted)]">
                        {filteredParametres.length} / {parametres.length} affichés
                    </span>
                </div>
            </div>

            {/* VUE CARTES */}
            {viewMode === 'CARDS' && (
                <div className="space-y-6">
                    {filteredParametres.length === 0 ? (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none p-12">
                            <EmptyState
                                icon={Sliders}
                                title="Aucun paramètre trouvé"
                                description="Aucun paramètre ou règle ne correspond aux critères sélectionnés."
                            />
                        </div>
                    ) : (
                        Array.from(groupedParametres.entries()).map(([categorieKey, items]) => {
                            const catLabel = categorieKey === 'POINTAGE' ? 'Pointage & Horaires de Travail'
                                           : categorieKey === 'CONGE' ? 'Congés & Politiques d’Absences'
                                           : categorieKey === 'CONTRAT' ? 'Contrats & Périodes d’Essai'
                                           : categorieKey === 'RH_GENERAL' ? 'Règles RH Générales' : categorieKey;

                            return (
                                <div key={categorieKey} className="space-y-4">
                                    <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                                        <div className="flex items-center gap-2">
                                            <span className="text-xs font-bold uppercase tracking-widest text-[var(--color-brand-500)]">
                                                {catLabel}
                                            </span>
                                            <span className="text-[10px] font-bold text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] px-2 py-0.5 rounded-none font-mono">
                                                {items.length}
                                            </span>
                                        </div>
                                    </div>

                                    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                                        {items.map(param => {
                                            const statut = getStatut(param);
                                            const meta = PARAMETRE_METADATA[param.codeParametre];
                                            const IconComponent = meta?.icone || Sliders;
                                            const titreAffiche = meta?.titre || param.codeParametre;
                                            const valeurAffichee = formatValeurAffichage(param.codeParametre, param.valeur);
                                            const estInactif = !param.estActif;

                                            return (
                                                <Card
                                                    key={param.idParametre}
                                                    bordered
                                                    className={`flex flex-col justify-between rounded-none border transition-all ${
                                                        estInactif
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
                                                                    <span className="text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                                        {param.codeParametre}
                                                                    </span>
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
                                                                    param.estActif ? (
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
                                                                    <span className="text-[11px] text-[var(--color-fg-muted)]">Ancien paramètre sans effet</span>
                                                                ) : (
                                                                    <span className="flex items-center gap-1.5 text-[11px] font-bold text-[var(--color-brand-600)]" title="Cette constante est indispensable et ne peut pas être coupée">
                                                                        <Lock size={12} />
                                                                    Système Obligatoire
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
                                                                    {param.historiqueNonConsomme ? 'Sans effet sur les calculs' : 'Non débrayable'}
                                                                </span>
                                                            )}
                                                        </div>

                                                        {/* Valeur numérique / textuelle */}
                                                        <div>
                                                            <div className="text-[10px] text-[var(--color-fg-muted)] uppercase tracking-wider font-semibold">
                                                                {estInactif ? 'Valeur enregistrée (inactive)' : 'Valeur d’Application'}
                                                            </div>
                                                            <div className={`text-xl font-black mt-0.5 tracking-tight ${
                                                                estInactif ? 'text-[var(--color-fg-muted)] line-through' : 'text-[var(--color-fg)]'
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

            {/* VUE TABLEAU DÉTAILLÉ */}
            {viewMode === 'TABLE' && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-xs overflow-hidden">
                    <Table bordered={false}>
                        <Thead>
                            <Th>Paramètre Métier</Th>
                            <Th>Domaine</Th>
                            <Th>Nature</Th>
                            <Th>État (Actif/Inactif)</Th>
                            <Th>Valeur</Th>
                            <Th>Période de Validité</Th>
                            <Th>Statut</Th>
                            <Th className="text-right">Actions</Th>
                        </Thead>
                        <Tbody>
                            {filteredParametres.length === 0 ? (
                                <Tr>
                                    <Td colSpan={8} className="p-0">
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

                                    return (
                                        <Tr key={p.idParametre} className={`hover:bg-[var(--color-bg-subtle)] ${estInactif ? 'opacity-75 bg-[var(--color-warning-500)]/5' : ''}`}>
                                            <Td className="font-medium text-[var(--color-fg)]">
                                                <div className="flex items-center gap-2">
                                                    <div className={`p-1.5 rounded-none border ${
                                                        estInactif
                                                            ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] border-[var(--color-warning-500)]/25'
                                                            : p.estDesactivable
                                                            ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] border-[var(--color-success-500)]/25'
                                                            : 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/25'
                                                    }`}>
                                                        <IconComponent size={14} />
                                                    </div>
                                                    <div>
                                                        <div className="font-bold text-xs">{titreAffiche}</div>
                                                        <div className="text-[10px] font-mono text-[var(--color-fg-muted)]">{p.codeParametre}</div>
                                                        {!p.estActif && p.effetDesactivation && (
                                                            <div className="text-[11px] text-[var(--color-warning-600)] mt-1">{p.effetDesactivation}</div>
                                                        )}
                                                    </div>
                                                </div>
                                            </Td>
                                            <Td>
                                                <span className="text-xs text-[var(--color-fg-muted)]">{p.categorie}</span>
                                            </Td>
                                            <Td>
                                                {p.estDesactivable ? (
                                                    <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-success-600)] font-bold">
                                                        <CheckCircle2 size={12} /> Règle débrayable
                                                    </span>
                                                ) : p.historiqueNonConsomme ? (
                                                    <span className="text-[11px] text-[var(--color-fg-muted)]">Ancien paramètre sans effet</span>
                                                ) : (
                                                    <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-brand-600)] font-bold">
                                                        <Lock size={12} /> Système protégé
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
                                                        <Lock size={11} /> {p.historiqueNonConsomme ? 'Sans effet' : 'Toujours actif'}
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
                                                            aria-label={`Programmer nouvelle version pour ${p.codeParametre}`}
                                                        >
                                                            <Plus size={15} />
                                                        </button>
                                                    )}
                                                    <button
                                                        onClick={() => handleOpenEdit(p, false)}
                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none border border-transparent hover:border-[var(--color-border-subtle)] cursor-pointer"
                                                        title="Modifier cette version"
                                                        aria-label={`Modifier ${p.codeParametre}`}
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
                                    <div className="text-[11px] font-mono text-[var(--color-fg-muted)]">
                                        {titreHumain} ({formData.codeParametre})
                                    </div>
                                </div>
                            </div>
                        }
                        size="2xl"
                        footer={
                            <div className="flex items-center justify-between w-full">
                                <div className="text-[11px] text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <ShieldCheck size={14} className="text-[var(--color-brand-500)]" />
                                    <span>Audit trail garanti • Zéro rupture historique</span>
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
                                        label="Code Paramètre"
                                        value={formData.codeParametre}
                                        readOnly
                                        disabled
                                        className="font-mono text-xs"
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
