import { useState, useEffect } from 'react';
import {
    Scale, TrendingUp, CheckCircle2, Clock, Edit2,
    Banknote, Percent, Timer, Palmtree, Info, FileText, AlertTriangle,
    Sparkles
} from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import { useToast } from '@/components/ui/feedback/Toast';
import { fmtDateInput } from '@/utils/paieUtils';

// ---- Helpers ----
const fmtAr = (v) => v != null ? Number(v).toLocaleString('fr-MG') + ' Ar' : '—';
const fmtNb = (v, dec = 2) => v != null ? Number(v).toFixed(dec) : '—';
const parseSafe = (v, fallback = 0) => { const n = parseFloat(v); return isNaN(n) ? fallback : n; };

function getStatut(config) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const debut = config.dateDebut ? new Date(config.dateDebut) : null;
    if (debut) debut.setHours(0, 0, 0, 0);
    const fin = config.dateFin ? new Date(config.dateFin) : null;
    if (fin) fin.setHours(23, 59, 59, 999);

    if (!debut) return 'INCONNU';
    if (debut > today) return 'PROGRAMME';
    if (!fin || fin >= today) return 'EN_VIGUEUR';
    return 'ARCHIVE';
}

function StatutBadge({ statut }) {
    const styles = {
        EN_VIGUEUR: 'bg-[var(--color-success-500)]/12 text-[var(--color-success-600)] border-[var(--color-success-500)]/25',
        PROGRAMME:  'bg-[var(--color-brand-500)]/12 text-[var(--color-brand-500)] border-[var(--color-brand-500)]/25',
        ARCHIVE:    'bg-[var(--color-fg-muted)]/10 text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]',
    };
    const labels = { EN_VIGUEUR: '● En vigueur', PROGRAMME: '◆ Programmé', ARCHIVE: '○ Archivé' };
    return (
        <span className={`inline-flex items-center px-2 py-0.5 text-[10px] font-bold border rounded-none uppercase tracking-wide ${styles[statut] || styles.ARCHIVE}`}>
            {labels[statut] || statut}
        </span>
    );
}

function PeriodeBadge({ config }) {
    const fmt = (d) => d ? new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }) : null;
    return (
        <span className="text-xs text-[var(--color-fg-muted)]">
            {fmt(config.dateDebut) || '?'} → {fmt(config.dateFin) || 'En vigueur'}
        </span>
    );
}

function SectionTitle({ icon: Icon, label, color = 'var(--color-brand-500)', badge }) {
    return (
        <div className="flex items-center gap-2 pb-2 border-b border-[var(--color-border-subtle)] mb-3">
            <Icon size={14} style={{ color }} />
            <span className="text-[11px] font-bold uppercase tracking-widest" style={{ color }}>{label}</span>
            {badge && <span className="ml-auto text-[10px] text-[var(--color-fg-muted)]">{badge}</span>}
        </div>
    );
}

function KpiField({ label, value, sub }) {
    return (
        <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3">
            <div className="text-[10px] text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">{label}</div>
            <div className="text-base font-bold text-[var(--color-fg)]">{value}</div>
            {sub && <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">{sub}</div>}
        </div>
    );
}

// ---- Valeurs de pré-remplissage (guide de saisie pour un premier exercice vide) ----
const EMPTY_FORM = {
    codeVersion: '', libelle: '', dateDebut: '', dateFin: '',
    smeNational: '262680', smeAgricole: '266500', plafondCotisationNbSme: '8',
    seuilExonerationIrsa: '350000', minimumPerceptionIrsa: '3000', deductionChargeFamille: '2000',
    arrondiBaseIrsa: 'CENTAINE_INF',
    dureeHebdoLegale: '40', diviseurMensuelHeures: '173.3333', seuilHsTranche1: '8', plafondHsHebdo: '20',
    acquisitionCongeMensuelJours: '2.5', diviseurValorisationCongeJours: '30',
};

// ---- Modale de saisie ----
function ExerciceFormModal({ isOpen, onClose, onSave, mode, loading, error, formData, setFormData, activeConfig }) {
    const upd = (k, v) => setFormData(f => ({ ...f, [k]: v }));

    const plafondCalc = formData.smeNational && formData.plafondCotisationNbSme
        ? fmtAr(parseSafe(formData.smeNational) * parseSafe(formData.plafondCotisationNbSme))
        : '—';

    // Titre et sémantique selon le mode
    const isRevalorisation = mode === 'revaloriser';
    const isEdit          = mode === 'edit';
    const isInit          = mode === 'init';

    const TITLES = {
        revaloriser: { title: 'Nouvelle Loi de Finances — Date d\'Application', icon: <TrendingUp size={18} />, submit: 'Appliquer la nouvelle LF' },
        init:        { title: 'Initialiser le premier exercice fiscal', icon: <Sparkles size={18} />, submit: 'Créer l\'exercice' },
        edit:        { title: 'Corriger les valeurs de l\'exercice', icon: <Edit2 size={18} />, submit: 'Enregistrer la correction' },
    };
    const meta = TITLES[mode] || TITLES.init;

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={meta.title}
                   size="4xl"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={loading}>Annuler</Button>
                    <Button type="submit" form="exercice-form" variant="primary" size="sm" loading={loading}>
                        {meta.submit}
                    </Button>
                </>
            }
        >
            <form id="exercice-form" onSubmit={onSave} className="space-y-4">
                {error && <Alert type="error" message={error} />}

                {/* Message contextuel selon le mode */}
                {isRevalorisation && (
                    <div className="bg-[var(--color-warning-500)]/8 border border-[var(--color-warning-500)]/25 p-3.5 flex items-start gap-3">
                        <AlertTriangle size={16} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                        <div className="text-xs text-[var(--color-fg-muted)] space-y-1">
                            <p className="font-bold text-[var(--color-fg)]">Revalorisation légale (Nouvelle Loi de Finances) — Versionnement automatique</p>
                            <p>L'exercice en vigueur <strong className="text-[var(--color-fg)]">{activeConfig?.codeVersion}</strong> sera automatiquement
                            clôturé à la veille de la date d'application choisie ci-dessous. Le nouvel exercice prendra effet dès cette date.</p>
                            <p className="text-[var(--color-fg-muted)]">Exemple : pour une application au <em>01/01/2027</em>, l'exercice actuel se clôturera le <em>31/12/2026</em>.</p>
                        </div>
                    </div>
                )}
                {isEdit && (
                    <div className="bg-[var(--color-brand-500)]/8 border border-[var(--color-brand-500)]/20 p-3.5 flex items-start gap-3">
                        <Info size={16} className="text-[var(--color-brand-500)] shrink-0 mt-0.5" />
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            <strong>Mode correction :</strong> vous modifiez directement les valeurs de cette version sans créer d'historique ni clore de période.
                        </p>
                    </div>
                )}
                {isInit && (
                    <div className="bg-[var(--color-success-500)]/8 border border-[var(--color-success-500)]/20 p-3.5 flex items-start gap-3">
                        <Sparkles size={16} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            Initialisation du premier exercice fiscal. Les paramètres préremplis correspondent aux dispositions légales en vigueur à Madagascar.
                        </p>
                    </div>
                )}

                {/* SECTION 1 : Identification (Pleine largeur) */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 space-y-3">
                    <SectionTitle icon={FileText} label="1. Identification du millésime légal" color="var(--color-brand-500)" />
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                        <div>
                            <Input
                                label="Code Version *"
                                required
                                value={formData.codeVersion}
                                onChange={e => upd('codeVersion', e.target.value)}
                                placeholder="Ex : LF_2027"
                                hint="Clé unique d'archive (ex: LF_2027)"
                            />
                        </div>
                        <div className="md:col-span-3">
                            <Input
                                label="Libellé complet *"
                                required
                                value={formData.libelle}
                                onChange={e => upd('libelle', e.target.value)}
                                placeholder="Ex : Loi de Finances 2027 (Loi n°2026-XXX) — Barème IRSA 6 tranches"
                                hint="Texte de référence affiché sur les documents administratifs"
                            />
                        </div>
                        <div className="md:col-span-2">
                            <Input
                                label={isRevalorisation ? "Date d'application (Date d'effet) *" : "Date de début *"}
                                type="date"
                                required
                                value={formData.dateDebut}
                                onChange={e => upd('dateDebut', e.target.value)}
                                hint={isRevalorisation ? "Date à partir de laquelle ces règles s'appliquent" : "Premier jour de validité"}
                            />
                        </div>
                        <div className="md:col-span-2">
                            {!isRevalorisation ? (
                                <Input
                                    label="Date de fin (laisser vide si indéterminé)"
                                    type="date"
                                    value={formData.dateFin}
                                    onChange={e => upd('dateFin', e.target.value)}
                                    hint="Généralement vide (clôturé automatiquement lors de la prochaine LF)"
                                />
                            ) : (
                                <div className="text-xs text-[var(--color-fg-muted)] p-2 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] flex flex-col justify-center h-[62px]">
                                    <span className="font-semibold text-[var(--color-fg)]">Fin de validité :</span>
                                    <span>Laissée ouverte (En vigueur jusqu'à la prochaine revalorisation).</span>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* GRILLE 2 COLONNES POUR LES CATÉGORIES DE RÈGLES */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

                    {/* COLONNE GAUCHE : Social & Retraite */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3.5 flex flex-col justify-between">
                        <div>
                            <SectionTitle 
                                icon={Banknote} 
                                label="2. Salaire Minimum & Cotisations Sociales" 
                                color="var(--color-success-600)" 
                                badge="CNaPS / OSTIE / FMFP"
                            />
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <Input
                                    label="SME National (Ariary) *"
                                    type="number"
                                    required
                                    value={formData.smeNational}
                                    onChange={e => upd('smeNational', e.target.value)}
                                    hint="Secteur non agricole"
                                />
                                <Input
                                    label="SME Agricole (Ariary) *"
                                    type="number"
                                    required
                                    value={formData.smeAgricole}
                                    onChange={e => upd('smeAgricole', e.target.value)}
                                    hint="Secteur agricole"
                                />
                                <div className="sm:col-span-2">
                                    <Input
                                        label="Multiplicateur Plafond Social (× SME) *"
                                        type="number"
                                        step="0.01"
                                        required
                                        value={formData.plafondCotisationNbSme}
                                        onChange={e => upd('plafondCotisationNbSme', e.target.value)}
                                        hint="Facteur légal pour le plafond CNaPS / Santé (défaut : 8 × SME)"
                                    />
                                </div>
                            </div>
                        </div>

                        {/* Synthèse calculée en temps réel */}
                        <div className="p-3 bg-[var(--color-success-500)]/8 border border-[var(--color-success-500)]/25 text-xs text-[var(--color-fg-muted)] space-y-1">
                            <div className="flex justify-between items-center text-[var(--color-fg)]">
                                <span className="font-semibold">Plafond mensuel de cotisation :</span>
                                <span className="font-bold text-sm text-[var(--color-success-600)]">{plafondCalc}</span>
                            </div>
                            <p className="text-[11px]">
                                Soit {formData.smeNational || 0} Ar × {formData.plafondCotisationNbSme || 0}. Base maximale retenue pour CNaPS (1%), Santé (1%) et FMFP.
                            </p>
                        </div>
                    </div>

                    {/* COLONNE DROITE : Fiscalité IRSA */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3.5 flex flex-col justify-between">
                        <div>
                            <SectionTitle 
                                icon={Percent} 
                                label="3. Règles Fiscales IRSA (CGI)" 
                                color="var(--color-warning-600)" 
                                badge="Loi de Finances"
                            />
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <Input
                                    label="Seuil d'exonération (Ar) *"
                                    type="number"
                                    required
                                    value={formData.seuilExonerationIrsa}
                                    onChange={e => upd('seuilExonerationIrsa', e.target.value)}
                                    hint="Revenu imposable ≤ ce montant exonéré"
                                />
                                <Input
                                    label="Minimum de perception (Ar) *"
                                    type="number"
                                    required
                                    value={formData.minimumPerceptionIrsa}
                                    onChange={e => upd('minimumPerceptionIrsa', e.target.value)}
                                    hint="Plancher légal d'impôt exigible (ex: 3 000 Ar)"
                                />
                                <div className="sm:col-span-2">
                                    <Input
                                        label="Déduction / personne à charge (Ar) *"
                                        type="number"
                                        required
                                        value={formData.deductionChargeFamille}
                                        onChange={e => upd('deductionChargeFamille', e.target.value)}
                                        hint="Enfants légitimes et personnes à charge déclarées"
                                    />
                                </div>
                            </div>
                        </div>

                        <div className="p-3 bg-[var(--color-warning-500)]/8 border border-[var(--color-warning-500)]/25 text-xs text-[var(--color-fg-muted)] space-y-1">
                            <span className="font-semibold text-[var(--color-fg)]">Note sur les tranches d'impôt :</span>
                            <p className="text-[11px]">
                                Les tranches progressives (de 0% à 25%) et leurs seuils sont paramétrables dans le sous-onglet <strong>Barèmes IRSA</strong>.
                            </p>
                        </div>
                    </div>

                    {/* COLONNE GAUCHE : Temps de Travail */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3">
                        <SectionTitle 
                            icon={Timer} 
                            label="4. Temps de Travail & Heures Supplémentaires" 
                            color="var(--color-fg-muted)" 
                            badge="Code du Travail Art. 83"
                        />
                        <div className="grid grid-cols-2 gap-3">
                            <Input
                                label="Durée légale hebdo (h) *"
                                type="number"
                                step="0.5"
                                required
                                value={formData.dureeHebdoLegale}
                                onChange={e => {
                                    upd('dureeHebdoLegale', e.target.value);
                                    if (e.target.value) upd('diviseurMensuelHeures', ((parseFloat(e.target.value) * 52) / 12).toFixed(4));
                                }}
                                hint="Référence nationale (40h)"
                            />
                            <Input
                                label="Diviseur mensuel (h) *"
                                type="number"
                                step="0.0001"
                                required
                                value={formData.diviseurMensuelHeures}
                                onChange={e => upd('diviseurMensuelHeures', e.target.value)}
                                hint={`Calcul automatique : (${formData.dureeHebdoLegale || '40'}h × 52 sem.) / 12 mois = ${formData.diviseurMensuelHeures || '173.3333'}h`}
                            />
                            <Input
                                label="Seuil HS Tranche 1 (+30%) *"
                                type="number"
                                step="0.5"
                                required
                                value={formData.seuilHsTranche1}
                                onChange={e => upd('seuilHsTranche1', e.target.value)}
                                hint="Ex : 8 = 8 premières heures maj. à 30%"
                            />
                            <Input
                                label="Plafond HS / Semaine (h) *"
                                type="number"
                                step="0.5"
                                required
                                value={formData.plafondHsHebdo}
                                onChange={e => upd('plafondHsHebdo', e.target.value)}
                                hint="Limite légale max (ex: 20h)"
                            />
                        </div>
                    </div>

                    {/* COLONNE DROITE : Congés Payés */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3">
                        <SectionTitle 
                            icon={Palmtree} 
                            label="5. Droits & Valorisation des Congés Payés" 
                            color="var(--color-brand-500)" 
                            badge="Code du Travail Art. 85-88"
                        />
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <Input
                                label="Acquisition mensuelle (jours) *"
                                type="number"
                                step="0.01"
                                required
                                value={formData.acquisitionCongeMensuelJours}
                                onChange={e => upd('acquisitionCongeMensuelJours', e.target.value)}
                                hint="2.5 j/mois (= 30 jours ouvrables/an)"
                            />
                            <Input
                                label="Diviseur valorisation (jours) *"
                                type="number"
                                required
                                value={formData.diviseurValorisationCongeJours}
                                onChange={e => upd('diviseurValorisationCongeJours', e.target.value)}
                                hint="Valorisation: Brut ÷ 30 (calendaire)"
                            />
                        </div>
                        <p className="text-[11px] text-[var(--color-fg-muted)] pt-1">
                            À Madagascar, le décompte calendaire de 30 jours par an s'applique avec une valorisation calculée sur la base du salaire brut divisé par 30.
                        </p>
                    </div>

                </div>
            </form>
        </Modal>
    );
}

// ==============================
// COMPOSANT PRINCIPAL
// ==============================
export default function ReglementationFiscaleTab() {
    const [configs, setConfigs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [modalError, setModalError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const [modalMode, setModalMode] = useState('init'); // 'init' | 'revaloriser' | 'edit'
    const [editingId, setEditingId] = useState(null);
    const [formData, setFormData] = useState({ ...EMPTY_FORM });
    const [showArchived, setShowArchived] = useState(false);
    const toast = useToast();
    const todayStr = fmtDateInput(new Date());

    const load = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await paieConfigService.getAllConfigsLegales();
            setConfigs(data || []);
        } catch {
            setError("Impossible de charger les exercices fiscaux.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { load(); }, []);

    const activeConfig = configs.find(c => getStatut(c) === 'EN_VIGUEUR');
    const displayedConfigs = showArchived ? configs : configs.filter(c => getStatut(c) !== 'ARCHIVE');

    // ---- Ouverture "Initialiser" : aucun exercice actif ----
    const handleOpenInit = () => {
        setFormData({ ...EMPTY_FORM, dateDebut: todayStr });
        setModalMode('init');
        setEditingId(null);
        setModalError(null);
        setShowModal(true);
    };

    // ---- Ouverture "Nouvelle Loi de Finances" : clôture actif + crée le suivant ----
    const handleOpenRevaloriser = () => {
        if (!activeConfig) return;
        const f = {
            codeVersion: '', libelle: '',
            dateDebut: '', dateFin: '',
            smeNational:              String(activeConfig.smeNational ?? ''),
            smeAgricole:              String(activeConfig.smeAgricole ?? ''),
            plafondCotisationNbSme:   String(activeConfig.plafondCotisationNbSme ?? '8'),
            seuilExonerationIrsa:     String(activeConfig.seuilExonerationIrsa ?? ''),
            minimumPerceptionIrsa:    String(activeConfig.minimumPerceptionIrsa ?? ''),
            deductionChargeFamille:   String(activeConfig.deductionChargeFamille ?? ''),
            arrondiBaseIrsa:          activeConfig.arrondiBaseIrsa ?? 'CENTAINE_INF',
            dureeHebdoLegale:         String(activeConfig.dureeHebdoLegale ?? '40'),
            diviseurMensuelHeures:    String(activeConfig.diviseurMensuelHeures ?? '173.3333'),
            seuilHsTranche1:          String(activeConfig.seuilHsTranche1 ?? '8'),
            plafondHsHebdo:           String(activeConfig.plafondHsHebdo ?? '20'),
            acquisitionCongeMensuelJours:   String(activeConfig.acquisitionCongeMensuelJours ?? '2.5'),
            diviseurValorisationCongeJours: String(activeConfig.diviseurValorisationCongeJours ?? '30'),
        };
        setFormData(f);
        setModalMode('revaloriser');
        setEditingId(null);
        setModalError(null);
        setShowModal(true);
    };

    // ---- Ouverture "Corriger" : correction immédiate d'un exercice existant ----
    const handleOpenEdit = (config) => {
        setFormData({
            codeVersion:              config.codeVersion ?? '',
            libelle:                  config.libelle ?? '',
            dateDebut:                fmtDateInput(config.dateDebut) ?? '',
            dateFin:                  fmtDateInput(config.dateFin) ?? '',
            smeNational:              String(config.smeNational ?? ''),
            smeAgricole:              String(config.smeAgricole ?? ''),
            plafondCotisationNbSme:   String(config.plafondCotisationNbSme ?? '8'),
            seuilExonerationIrsa:     String(config.seuilExonerationIrsa ?? ''),
            minimumPerceptionIrsa:    String(config.minimumPerceptionIrsa ?? ''),
            deductionChargeFamille:   String(config.deductionChargeFamille ?? ''),
            arrondiBaseIrsa:          config.arrondiBaseIrsa ?? 'CENTAINE_INF',
            dureeHebdoLegale:         String(config.dureeHebdoLegale ?? '40'),
            diviseurMensuelHeures:    String(config.diviseurMensuelHeures ?? '173.3333'),
            seuilHsTranche1:          String(config.seuilHsTranche1 ?? '8'),
            plafondHsHebdo:           String(config.plafondHsHebdo ?? '20'),
            acquisitionCongeMensuelJours:   String(config.acquisitionCongeMensuelJours ?? '2.5'),
            diviseurValorisationCongeJours: String(config.diviseurValorisationCongeJours ?? '30'),
        });
        setModalMode('edit');
        setEditingId(config.idConfigLegale);
        setModalError(null);
        setShowModal(true);
    };

    const buildPayload = () => ({
        codeVersion:                  formData.codeVersion,
        libelle:                      formData.libelle,
        dateDebut:                    formData.dateDebut || null,
        dateFin:                      formData.dateFin || null,
        smeNational:                  parseSafe(formData.smeNational),
        smeAgricole:                  parseSafe(formData.smeAgricole),
        plafondCotisationNbSme:       parseSafe(formData.plafondCotisationNbSme, 8),
        seuilExonerationIrsa:         parseSafe(formData.seuilExonerationIrsa),
        minimumPerceptionIrsa:        parseSafe(formData.minimumPerceptionIrsa),
        deductionChargeFamille:       parseSafe(formData.deductionChargeFamille),
        arrondiBaseIrsa:              formData.arrondiBaseIrsa || 'CENTAINE_INF',
        dureeHebdoLegale:             parseSafe(formData.dureeHebdoLegale, 40),
        diviseurMensuelHeures:        parseSafe(formData.diviseurMensuelHeures, 173.3333),
        seuilHsTranche1:              parseSafe(formData.seuilHsTranche1, 8),
        plafondHsHebdo:               parseSafe(formData.plafondHsHebdo, 20),
        acquisitionCongeMensuelJours: parseSafe(formData.acquisitionCongeMensuelJours, 2.5),
        diviseurValorisationCongeJours: parseInt(formData.diviseurValorisationCongeJours, 10) || 30,
    });

    const handleSave = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setModalError(null);
        setSubmitting(true);
        try {
            const payload = buildPayload();
            if (modalMode === 'edit' && editingId) {
                await paieConfigService.updateConfigLegale(editingId, payload);
                toast({ title: 'Correction enregistrée', message: `Exercice "${payload.codeVersion}" mis à jour.`, type: 'success' });
            } else if (modalMode === 'revaloriser') {
                await paieConfigService.revaloriserConfigLegale(payload);
                toast({ title: 'Nouvelle Loi de Finances appliquée', message: `"${payload.codeVersion}" en vigueur dès le ${payload.dateDebut}. L'exercice précédent a été archivé.`, type: 'success' });
            } else {
                await paieConfigService.createConfigLegale(payload);
                toast({ title: 'Premier exercice créé', message: `"${payload.codeVersion}" est maintenant actif.`, type: 'success' });
            }
            setShowModal(false);
            load();
        } catch (err) {
            const errMsg = err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response?.data : null) || "Erreur lors de la sauvegarde. Vérifiez les données saisies.";
            setModalError(errMsg);
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) return <div className="p-4"><SkeletonTable rows={4} columns={4} /></div>;

    return (
        <div className="space-y-5">
            {error && <Alert type="error" message={error} />}

            {/* ---- Bannière Sources Légales — 100% dynamique depuis activeConfig ---- */}
            <div className="bg-[var(--color-brand-500)]/8 border border-[var(--color-brand-500)]/20 p-4 flex items-start gap-3">
                <Scale size={18} className="text-[var(--color-brand-500)] shrink-0 mt-0.5" />
                <div className="space-y-1 text-xs text-[var(--color-fg-muted)]">
                    <p className="font-bold text-[var(--color-fg)]">
                        Sources légales — Madagascar
                        {activeConfig && <span className="ml-2 font-normal text-[var(--color-fg-muted)]">(exercice actif : {activeConfig.codeVersion})</span>}
                    </p>
                    {activeConfig ? (
                        <>
                            <p>• <strong>IRSA</strong> (Code Général des Impôts — LF annuelle) :
                                SME national&nbsp;<strong>{fmtAr(activeConfig.smeNational)}</strong>,
                                seuil exonération&nbsp;<strong>{fmtAr(activeConfig.seuilExonerationIrsa)}</strong>,
                                min. perception&nbsp;<strong>{fmtAr(activeConfig.minimumPerceptionIrsa)}</strong>,
                                déduction charge famille&nbsp;<strong>{fmtAr(activeConfig.deductionChargeFamille)}</strong>
                            </p>
                            <p>• <strong>CNaPS / OSTIE / FMFP</strong> (Décrets sociaux) :
                                Plafond = <strong>{fmtNb(activeConfig.plafondCotisationNbSme, 0)} × SME</strong> = <strong>{fmtAr(activeConfig.plafondCotisationMensuel)}</strong>
                            </p>
                            <p>• <strong>Temps de travail</strong> (Code du Travail Art. 83, Loi n°2003-044) :
                                <strong> {fmtNb(activeConfig.dureeHebdoLegale, 0)}h</strong>/sem,
                                HS max&nbsp;<strong>{fmtNb(activeConfig.plafondHsHebdo, 0)}h</strong>/sem,
                                diviseur&nbsp;<strong>{fmtNb(activeConfig.diviseurMensuelHeures, 4)}h</strong>/mois
                            </p>
                            <p>• <strong>Congés payés</strong> (Art. 85) :
                                <strong> {fmtNb(activeConfig.acquisitionCongeMensuelJours, 2)} j</strong>/mois,
                                décompte calendaire ÷&nbsp;<strong>{activeConfig.diviseurValorisationCongeJours}</strong>
                            </p>
                        </>
                    ) : (
                        <p className=" text-[var(--color-warning-600)]">Aucun exercice fiscal actif — initialisez le premier exercice ci-dessous.</p>
                    )}
                </div>
            </div>

            {/* ---- Tableau de bord de l'exercice en vigueur ---- */}
            {activeConfig && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm">
                    <div className="px-5 py-3.5 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <CheckCircle2 size={18} className="text-[var(--color-success-500)]" />
                            <div>
                                <h4 className="font-bold text-[var(--color-fg)] text-sm">{activeConfig.codeVersion} — {activeConfig.libelle}</h4>
                                <PeriodeBadge config={activeConfig} />
                            </div>
                        </div>
                        <div className="flex items-center gap-2">
                            <StatutBadge statut="EN_VIGUEUR" />
                            <button
                                onClick={() => handleOpenEdit(activeConfig)}
                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] transition-colors"
                                title="Correction immédiate (sans créer d'historique)"
                            >
                                <Edit2 size={14} />
                            </button>
                        </div>
                    </div>

                    <div className="p-5 space-y-4">
                        <div>
                            <SectionTitle icon={Banknote} label="Salaire Minimum & Plafonds Sociaux" color="var(--color-success-600)" />
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                                <KpiField label="SME National" value={fmtAr(activeConfig.smeNational)} sub="Secteur non agricole" />
                                <KpiField label="SME Agricole" value={fmtAr(activeConfig.smeAgricole)} sub="Secteur agricole" />
                                <KpiField label="Multiplicateur Plafond Social" value={`× ${fmtNb(activeConfig.plafondCotisationNbSme, 0)} SME`} sub="= Plafond CNaPS / OSTIE / FMFP" />
                                <KpiField label="Plafond Mensuel Calculé" value={fmtAr(activeConfig.plafondCotisationMensuel)} sub="Base maximale soumise aux cotisations" />
                            </div>
                        </div>

                        <div>
                            <SectionTitle icon={Percent} label="Règles Fiscales IRSA" color="var(--color-warning-600)" />
                            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                                <KpiField label="Seuil d'exonération" value={fmtAr(activeConfig.seuilExonerationIrsa)} sub="Base imposable en dessous → IRSA = 0" />
                                <KpiField label="Minimum de Perception" value={fmtAr(activeConfig.minimumPerceptionIrsa)} sub="Plancher légal si IRSA calculé insuffisant" />
                                <KpiField label="Déduction / Personne à Charge" value={fmtAr(activeConfig.deductionChargeFamille)} sub="Conjoint, enfants légitimes à charge" />
                            </div>
                        </div>

                        <div>
                            <SectionTitle icon={Timer} label="Temps de Travail" color="var(--color-fg-muted)" />
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                                <KpiField label="Durée Légale Hebdo" value={`${fmtNb(activeConfig.dureeHebdoLegale, 0)}h`} sub="Code du Travail Art. 83" />
                                <KpiField label="Diviseur Mensuel" value={`${fmtNb(activeConfig.diviseurMensuelHeures, 4)}h`} sub="Taux horaire de référence" />
                                <KpiField label="HS Tranche 1 (+30%)" value={`${fmtNb(activeConfig.seuilHsTranche1, 0)} premières HS`} sub="Au-delà de ce seuil → +50%" />
                                <KpiField label="Plafond HS / Semaine" value={`${fmtNb(activeConfig.plafondHsHebdo, 0)}h max`} sub="Limite légale absolue" />
                            </div>
                        </div>

                        <div>
                            <SectionTitle icon={Palmtree} label="Congés Payés" color="var(--color-brand-500)" />
                            <div className="grid grid-cols-2 gap-2">
                                <KpiField label="Acquisition mensuelle" value={`${fmtNb(activeConfig.acquisitionCongeMensuelJours, 2)} j/mois`} sub="= 30 j/an (Art. 85)" />
                                <KpiField label="Valorisation d'1 jour de congé" value={`Salaire brut ÷ ${activeConfig.diviseurValorisationCongeJours}`} sub="Décompte calendaire standard" />
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* ---- Barre d'actions ---- */}
            <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2">
                    <h4 className="text-sm font-bold text-[var(--color-fg)]">Historique des Exercices Fiscaux</h4>
                    <span className="text-xs text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] px-2 py-0.5 border border-[var(--color-border-subtle)]">
                        {configs.length} exercice(s)
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => setShowArchived(v => !v)}
                        className="text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] flex items-center gap-1 px-2 py-1.5 border border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)] transition-colors"
                    >
                        <Clock size={13} />
                        {showArchived ? 'Masquer les archivés' : 'Afficher les archivés'}
                    </button>

                    {/* 'Nouvelle LF' = seul workflow autorisé lorsqu'un exercice est actif */}
                    {activeConfig && (
                        <Button variant="secondary" size="sm" onClick={handleOpenRevaloriser} icon={<TrendingUp size={14} />}>
                            Nouvelle Loi de Finances
                        </Button>
                    )}

                    {/* 'Initialiser' = uniquement affiché si la base est totalement vide */}
                    {!activeConfig && (
                        <Button variant="primary" size="sm" onClick={handleOpenInit} icon={<Sparkles size={14} />}>
                            Initialiser le premier exercice
                        </Button>
                    )}
                </div>
            </div>

            {/* ---- Timeline des exercices ---- */}
            <div className="space-y-2">
                {displayedConfigs.length === 0 && (
                    <div className="text-sm text-[var(--color-fg-muted)] text-center py-10 border border-dashed border-[var(--color-border-subtle)]">
                        Aucun exercice fiscal configuré.
                    </div>
                )}
                {displayedConfigs.map((config) => {
                    const statut = getStatut(config);
                    const isActive = statut === 'EN_VIGUEUR';
                    return (
                        <div
                            key={config.idConfigLegale}
                            className={`border flex items-center gap-4 px-4 py-3 transition-all ${
                                isActive
                                    ? 'bg-[var(--color-success-500)]/5 border-[var(--color-success-500)]/30'
                                    : statut === 'PROGRAMME'
                                    ? 'bg-[var(--color-brand-500)]/5 border-[var(--color-brand-500)]/25'
                                    : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] opacity-55'
                            }`}
                        >
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 mb-0.5">
                                    <span className="font-bold text-sm text-[var(--color-fg)]">{config.codeVersion}</span>
                                    <StatutBadge statut={statut} />
                                </div>
                                <div className="text-xs text-[var(--color-fg-muted)] truncate">{config.libelle}</div>
                                <PeriodeBadge config={config} />
                            </div>
                            <div className="hidden md:flex items-center gap-6 text-xs text-[var(--color-fg-muted)] shrink-0">
                                <span><span className="font-bold text-[var(--color-fg)]">{fmtAr(config.smeNational)}</span><br/>SME</span>
                                <span><span className="font-bold text-[var(--color-fg)]">{fmtAr(config.minimumPerceptionIrsa)}</span><br/>Min. IRSA</span>
                                <span><span className="font-bold text-[var(--color-fg)]">{config.dureeHebdoLegale}h</span><br/>Légale</span>
                                <span><span className="font-bold text-[var(--color-fg)]">{fmtAr(config.plafondCotisationMensuel)}</span><br/>Plafond social</span>
                            </div>
                            <button
                                onClick={() => handleOpenEdit(config)}
                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] transition-colors shrink-0"
                                title="Corriger les valeurs de cet exercice"
                            >
                                <Edit2 size={14} />
                            </button>
                        </div>
                    );
                })}
            </div>

            <ExerciceFormModal
                isOpen={showModal}
                onClose={() => setShowModal(false)}
                onSave={handleSave}
                mode={modalMode}
                loading={submitting}
                error={modalError}
                formData={formData}
                setFormData={setFormData}
                activeConfig={activeConfig}
            />
        </div>
    );
}
