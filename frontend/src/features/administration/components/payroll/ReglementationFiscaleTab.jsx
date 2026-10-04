import { useState, useEffect } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import {
    Scale, TrendingUp, CheckCircle2, Clock, Edit2, Eye, X,
    Banknote, Percent, Timer, Palmtree, Info, FileText, AlertTriangle,
    Sparkles, Trash2, Plus, Zap, Moon, Sun, Calendar
} from 'lucide-react';
import paieConfigService from '@/features/administration/api/paieConfigService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useToast } from '@/components/ui/feedback/Toast';
import { fmtDateInput } from '@/utils/dateUtils';

// ---- Helpers ----
const fmtAr = (v) => v != null && v !== '' ? Number(v).toLocaleString('fr-MG', { minimumFractionDigits: 0, maximumFractionDigits: 2 }) + ' Ar' : '—';
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
        PROGRAMME: 'bg-[var(--color-brand-500)]/12 text-[var(--color-brand-500)] border-[var(--color-brand-500)]/25',
        ARCHIVE: 'bg-[var(--color-fg-muted)]/10 text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]',
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

const MAJORATION_ORDER = { 'HS_30': 1, 'HS_50': 2, 'NUIT': 3, 'DIMANCHE': 4, 'FERIE': 5 };

const EMPTY_FORM = {
    codeVersion: '', libelle: '', dateDebut: '', dateFin: '',
    smeNational: '', smeAgricole: '', plafondCotisationNbSme: '', plafondAvanceEmployeurPct: '10', quotiteCessiblePct: '33.333333',
    seuilExonerationIrsa: '', minimumPerceptionIrsa: '', deductionChargeFamille: '',
    arrondiBaseIrsa: '',
    dureeHebdoLegale: '', diviseurMensuelHeures: '', seuilHsTranche1: '', plafondHsHebdo: '',
    exonerationHsIrsaActive: false, plafondMensuelHsExonerees: '',
    acquisitionCongeMensuelJours: '', diviseurValorisationCongeJours: '',
    tranchesIrsa: [],
    majorations: [],
};

const MAJORATION_CONFIG = [
    { type: 'HS_30', title: 'HS Tranche 1', subtitle: 'Première tranche configurée', icon: Clock, color: 'text-[var(--color-brand-600)]', badgeBg: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' },
    { type: 'HS_50', title: 'HS Tranche 2', subtitle: 'Seconde tranche configurée', icon: Zap, color: 'text-[var(--color-brand-600)]', badgeBg: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' },
    { type: 'NUIT', title: 'Heures de Nuit', subtitle: 'Plage configurée en base', icon: Moon, color: 'text-[var(--color-brand-600)]', badgeBg: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' },
    { type: 'DIMANCHE', title: 'Dimanche', subtitle: 'Majoration configurée', icon: Sun, color: 'text-[var(--color-brand-600)]', badgeBg: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' },
    { type: 'FERIE', title: 'Jour Férié', subtitle: 'Majoration configurée', icon: Calendar, color: 'text-[var(--color-brand-600)]', badgeBg: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' },
];

function MajorationsFormSection({ majorations = [], onChange, isReadOnly }) {
    const handleTauxChange = (type, val) => {
        const pct = parseFloat(val) || 0;
        const next = majorations.map(m => m.typeMajoration === type ? { ...m, taux: pct / 100 } : m);
        onChange(next);
    };

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3 col-span-1 md:col-span-2">
            <SectionTitle
                icon={Clock}
                label="6. Taux de Majoration des Heures Spéciales"
                color="var(--color-warning-600)"
                badge="Code du Travail Art. 83-84"
            />
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
                {MAJORATION_CONFIG.map(item => {
                    const found = majorations.find(m => m.typeMajoration === item.type);
                    const rawTaux = found ? parseFloat(found.taux) : null;
                    const pct = rawTaux === null ? '' : Math.round(rawTaux * 100);
                    const mult = rawTaux === null ? '—' : (1 + rawTaux).toFixed(2);
                    const Icon = item.icon;

                    return (
                        <div
                            key={item.type}
                            className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 flex flex-col justify-between hover:border-[var(--color-brand-500)]/40 transition-colors"
                        >
                            <div>
                                <div className="flex items-center gap-1.5 mb-1">
                                    <Icon size={14} className={item.color} />
                                    <span className="text-xs font-bold text-[var(--color-fg)]">
                                        {item.title}
                                    </span>
                                </div>
                                <p className="text-[10px] text-[var(--color-fg-muted)] leading-tight mb-3">
                                    {item.subtitle}
                                </p>
                            </div>

                            <div className="pt-2 border-t border-[var(--color-border-subtle)] flex items-center justify-between gap-1.5">
                                <div className="flex items-center gap-1">
                                    <span className="text-xs font-bold text-[var(--color-brand-500)]">+</span>
                                    <input
                                        type="number"
                                        min="0"
                                        max="500"
                                        step="1"
                                        disabled={isReadOnly}
                                        value={pct}
                                        onChange={(e) => handleTauxChange(item.type, e.target.value)}
                                        className="w-14 h-7 text-xs font-bold px-1.5 py-0.5 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] text-right focus:outline-none focus:border-[var(--color-brand-500)] disabled:opacity-60"
                                    />
                                    <span className="text-xs font-semibold text-[var(--color-fg-muted)]">%</span>
                                </div>
                                <span className={`text-[10px] font-mono px-1.5 py-0.5 font-bold border border-[var(--color-border-subtle)] shrink-0 ${item.badgeBg}`}>
                                    x{mult}
                                </span>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

function TranchesIrsaFormSection({ tranches = [], onChange, isReadOnly }) {
    const [localError, setLocalError] = useState(null);

    const handleAdd = () => {
        const last = tranches[tranches.length - 1];
        if (last && (last.trancheMax === '' || last.trancheMax === null || last.trancheMax === undefined)) {
            setLocalError("Veuillez d'abord définir un montant maximum pour la dernière tranche avant d'en ajouter une nouvelle.");
            return;
        }
        setLocalError(null);
        const nextNum = tranches.length + 1;
        const nextMin = last ? (parseFloat(last.trancheMax) || 0) : 0;
        onChange([
            ...tranches,
            { numeroTranche: nextNum, trancheMin: nextMin, trancheMax: '', taux: 0 }
        ]);
    };

    const handleRemove = (idx) => {
        setLocalError(null);
        if (tranches.length <= 1) return;
        const next = tranches.filter((_, i) => i !== idx).map((t, i) => ({ ...t, numeroTranche: i + 1 }));
        onChange(next);
    };

    const handleFieldChange = (idx, field, val) => {
        setLocalError(null);
        const next = [...tranches];
        next[idx] = { ...next[idx], [field]: val };
        onChange(next);
    };

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3 col-span-1 md:col-span-2">
            {localError && (
                <div className="p-2.5 text-xs bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)] border border-[var(--color-danger-500)]/20">
                    {localError}
                </div>
            )}
            <div className="flex items-center justify-between">
                <SectionTitle
                    icon={Percent}
                    label="7. Barème Progressif IRSA (Tranches d'imposition)"
                    color="var(--color-brand-500)"
                    badge="Code Général des Impôts (CGI)"
                />
                {!isReadOnly && (
                    <Button type="button" variant="secondary" size="sm" onClick={handleAdd} iconLeft={<Plus size={13} />}>
                        Ajouter une tranche
                    </Button>
                )}
            </div>

            <div className="overflow-x-auto">
                <Table bordered={false}>
                    <Thead>
                        <Th className="w-24">Tranche</Th>
                        <Th className="text-right">Seuil Min (Ar)</Th>
                        <Th className="text-right">Seuil Max (Ar)</Th>
                        <Th className="text-right w-28">Taux IRSA</Th>
                        {!isReadOnly && <Th className="w-16 text-center">Action</Th>}
                    </Thead>
                    <Tbody>
                        {tranches.map((t, idx) => {
                            const isLast = idx === tranches.length - 1;
                            if (isReadOnly) {
                                return (
                                    <Tr key={t.numeroTranche || idx}>
                                        <Td isData className="font-bold text-[var(--color-brand-500)]">Tranche {t.numeroTranche}</Td>
                                        <Td isData className="text-right font-semibold">{fmtAr(t.trancheMin)}</Td>
                                        <Td isData className="text-right font-semibold">{t.trancheMax ? fmtAr(t.trancheMax) : 'Sans plafond (∞)'}</Td>
                                        <Td isData className="text-right font-bold text-[var(--color-danger-500)]">{t.taux}%</Td>
                                    </Tr>
                                );
                            }
                            return (
                                <Tr key={idx}>
                                    <Td isData className="font-bold text-[var(--color-brand-500)]">Tranche {t.numeroTranche}</Td>
                                    <Td>
                                        <MoneyInput
                                            maxDecimals={2}
                                            min="0"
                                            required
                                            value={t.trancheMin}
                                            disabled={idx === 0}
                                            onValueChange={value => handleFieldChange(idx, 'trancheMin', value)}
                                            className="text-right font-mono"
                                        />
                                    </Td>
                                    <Td>
                                        <MoneyInput
                                            maxDecimals={2}
                                            min={parseFloat(t.trancheMin) + 0.01 || 0}
                                            placeholder={isLast ? "Sans plafond (∞)" : "Requis"}
                                            value={t.trancheMax}
                                            onValueChange={value => handleFieldChange(idx, 'trancheMax', value)}
                                            className="text-right font-mono"
                                        />
                                    </Td>
                                    <Td>
                                        <div className="relative">
                                            <input
                                                type="number"
                                                min="0"
                                                max="100"
                                                step="0.01"
                                                required
                                                value={t.taux}
                                                onChange={e => handleFieldChange(idx, 'taux', e.target.value)}
                                                className="w-full text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] pl-2 pr-5 py-1 text-right font-mono font-bold text-[var(--color-danger-500)] focus:outline-none focus:border-[var(--color-brand-500)]"
                                            />
                                            <span className="absolute right-1.5 top-1 text-[11px] text-[var(--color-fg-muted)] pointer-events-none">%</span>
                                        </div>
                                    </Td>
                                    <Td className="text-center">
                                        <button
                                            type="button"
                                            onClick={() => handleRemove(idx)}
                                            disabled={tranches.length <= 1}
                                            className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer transition-colors"
                                            title="Supprimer cette tranche"
                                        >
                                            <Trash2 size={14} />
                                        </button>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            </div>
            {!isReadOnly && (
                <p className="text-[11px] text-[var(--color-fg-muted)]">
                    La première tranche (T1) commence obligatoirement à 0 Ar. La dernière tranche peut être laissée sans plafond (∞).
                </p>
            )}
        </div>
    );
}

// ---- Modale de saisie ----
function ExerciceFormModal({ isOpen, onClose, onSave, mode, loading, error, formData, setFormData, activeConfig }) {
    const upd = (k, v) => setFormData(f => ({ ...f, [k]: v }));

    const plafondCalc = formData.smeNational && formData.plafondCotisationNbSme
        ? fmtAr(parseSafe(formData.smeNational) * parseSafe(formData.plafondCotisationNbSme))
        : '—';

    // Titre et sémantique selon le mode
    const isRevalorisation = mode === 'revaloriser';
    const isEdit = mode === 'edit';
    const isInit = mode === 'init';
    const isView = mode === 'view';
    const isReadOnly = isView;
    const TITLES = {
        revaloriser: { title: 'Nouvelle Loi de Finances — Date d\'Application', icon: <TrendingUp size={18} />, submit: 'Appliquer la nouvelle LF' },
        init: { title: 'Initialiser le premier exercice fiscal', icon: <Sparkles size={18} />, submit: 'Créer l\'exercice' },
        edit: { title: 'Corriger les valeurs de l\'exercice', icon: <Edit2 size={18} />, submit: 'Enregistrer la correction' },
        view: { title: 'Détails de l\'exercice archivé', icon: <Eye size={18} />, submit: null },
    };
    const meta = TITLES[mode] || TITLES.init;

    const footer = <>
        <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={loading}>{isView ? 'Fermer' : 'Annuler'}</Button>
        {!isView && <Button type="submit" form="exercice-form" variant="primary" size="sm" loading={loading}>{meta.submit}</Button>}
    </>;
    const form = (
            <form id="exercice-form" onSubmit={onSave} className="space-y-5 scroll-mt-24">
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
                {isView && (
                    <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3.5 flex items-start gap-3">
                        <Eye size={16} className="text-[var(--color-fg-muted)] shrink-0 mt-0.5" />
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            <strong>Consultation en lecture seule.</strong> Cet exercice est archivé et ne peut plus être modifié.
                            Ces valeurs ont été appliquées aux bulletins de paie de la période correspondante.
                        </p>
                    </div>
                )}

                {/* SECTION 1 : Identification (Pleine largeur) */}
                <div id="exercice-identification" className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 space-y-3 scroll-mt-24">
                    <SectionTitle icon={FileText} label="1. Identification du millésime légal" color="var(--color-brand-500)" />
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                        <div>
                            <Input
                                label="Code Version"
                                required={!isReadOnly}
                                readOnly={isReadOnly}
                                disabled={isReadOnly}
                                value={formData.codeVersion}
                                onChange={e => !isReadOnly && upd('codeVersion', e.target.value)}
                                placeholder="Ex : LF_2027"
                                hint="Clé unique d'archive (ex: LF_2027)"
                            />
                        </div>
                        <div className="md:col-span-3">
                            <Input
                                label="Libellé complet"
                                required={!isReadOnly}
                                disabled={isReadOnly}
                                value={formData.libelle}
                                onChange={e => upd('libelle', e.target.value)}
                                placeholder="Ex : Loi de Finances 2027 (Loi n°2026-XXX) — Barème IRSA 6 tranches"
                                hint="Texte de référence affiché sur les documents administratifs"
                            />
                        </div>
                        <div className="md:col-span-2">
                            <Input
                                label={isRevalorisation ? "Date d'application (Date d'effet)" : "Date de début"}
                                type="date"
                                required={!isReadOnly}
                                disabled={isReadOnly}
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
                                    disabled={isReadOnly}
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

                {/* Sections de saisie dans l'ordre du sommaire */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

                    {/* COLONNE GAUCHE : Social & Retraite */}
                    <div id="exercice-salaires" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3.5 scroll-mt-24">
                        <div>
                            <SectionTitle
                                icon={Banknote}
                                label="2. Salaire Minimum & Cotisations Sociales"
                                color="var(--color-success-600)"
                                badge="CNaPS / OSTIE / FMFP"
                            />
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <MoneyInput
                                    label="SME National (Ariary)"
                                    required={!isReadOnly}
                                    disabled={isReadOnly}
                                    value={formData.smeNational}
                                    onValueChange={value => upd('smeNational', value)}
                                    hint="Secteur non agricole"
                                />
                                <MoneyInput
                                    label="SME Agricole (Ariary)"
                                    required={!isReadOnly}
                                    disabled={isReadOnly}
                                    value={formData.smeAgricole}
                                    onValueChange={value => upd('smeAgricole', value)}
                                    hint="Secteur agricole"
                                />
                                <div className="sm:col-span-2">
                                    <Input
                                        label="Multiplicateur Plafond Social (× SME)"
                                        type="number"
                                        step="0.01"
                                        required={!isReadOnly}
                                        disabled={isReadOnly}
                                        value={formData.plafondCotisationNbSme}
                                        onChange={e => upd('plafondCotisationNbSme', e.target.value)}
                                        hint="Facteur légal pour le plafond CNaPS / Santé (défaut : 8 × SME)"
                                    />
                                </div>
                                <div id="exercice-retenues" className="sm:col-span-2 border-t border-[var(--color-border-subtle)] pt-3 scroll-mt-24">
                                    <p className="text-sm font-semibold text-[var(--color-fg)] mb-2">Retenues sur salaire</p>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                        <Input label="Avances employeur (%)" type="number" min="0" max="100" step="0.000001" required={!isReadOnly} disabled={isReadOnly} value={formData.plafondAvanceEmployeurPct} onChange={e => upd('plafondAvanceEmployeurPct', e.target.value)} hint="Plafond applicable aux avances employeur." />
                                        <Input label="Quotité cessible (%)" type="number" min="0" max="100" step="0.000001" required={!isReadOnly} disabled={isReadOnly} value={formData.quotiteCessiblePct} onChange={e => upd('quotiteCessiblePct', e.target.value)} hint="Plafond applicable aux prêts et saisies." />
                                    </div>
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
                                Soit {fmtAr(parseSafe(formData.smeNational))} × {formData.plafondCotisationNbSme || 0}. Base maximale CNaPS et, selon l’organisme, santé. La FMFP utilise sa propre assiette.
                            </p>
                        </div>
                    </div>

                    {/* COLONNE DROITE : Fiscalité IRSA */}
                    <div id="exercice-irsa" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3.5 scroll-mt-24">
                        <div>
                            <SectionTitle
                                icon={Percent}
                                label="3. Règles Fiscales IRSA (CGI)"
                                color="var(--color-warning-600)"
                                badge="Loi de Finances"
                            />
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <MoneyInput
                                    label="Seuil d'exonération (Ar)"
                                    required={!isReadOnly}
                                    disabled={isReadOnly}
                                    value={formData.seuilExonerationIrsa}
                                    onValueChange={value => upd('seuilExonerationIrsa', value)}
                                    hint="Revenu imposable ≤ ce montant exonéré"
                                />
                                <MoneyInput
                                    label="Minimum de perception (Ar)"
                                    required={!isReadOnly}
                                    disabled={isReadOnly}
                                    value={formData.minimumPerceptionIrsa}
                                    onValueChange={value => upd('minimumPerceptionIrsa', value)}
                                    hint="Plancher légal d'impôt exigible (ex: 3 000 Ar)"
                                />
                                <div className="sm:col-span-2">
                                    <MoneyInput
                                        label="Déduction / personne à charge (Ar)"
                                        required={!isReadOnly}
                                        disabled={isReadOnly}
                                        value={formData.deductionChargeFamille}
                                        onValueChange={value => upd('deductionChargeFamille', value)}
                                        hint="Enfants légitimes et personnes à charge déclarées"
                                    />
                                </div>
                            </div>
                        </div>

                        <div className="p-3 bg-[var(--color-warning-500)]/8 border border-[var(--color-warning-500)]/25 text-xs text-[var(--color-fg-muted)] space-y-1">
                            <span className="font-semibold text-[var(--color-fg)]">Grille des tranches IRSA :</span>
                            <p className="text-[11px]">
                                Modifiez les taux et les seuils dans la section « Barème IRSA ».
                            </p>
                        </div>
                    </div>

                    {/* COLONNE GAUCHE : Temps de Travail */}
                    <div id="exercice-travail" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3 scroll-mt-24">
                        <SectionTitle
                            icon={Timer}
                            label="4. Temps de Travail & Heures Supplémentaires"
                            color="var(--color-fg-muted)"
                            badge="Code du Travail Art. 83"
                        />
                        <div className="grid grid-cols-2 gap-3">
                            <Input
                                label="Durée légale hebdo (h)"
                                type="number"
                                step="0.5"
                                required={!isReadOnly}
                                disabled={isReadOnly}
                                value={formData.dureeHebdoLegale}
                                onChange={e => setFormData(f => ({ ...f, dureeHebdoLegale: e.target.value, diviseurMensuelHeures: e.target.value ? ((parseFloat(e.target.value) * 52) / 12).toFixed(4) : f.diviseurMensuelHeures }))}
                                hint="Référence nationale (40h)"
                            />
                            <Input
                                label="Diviseur mensuel (h)"
                                type="number"
                                step="0.01"
                                required={!isReadOnly}
                                disabled={isReadOnly}
                                value={formData.diviseurMensuelHeures}
                                onChange={e => upd('diviseurMensuelHeures', e.target.value)}
                                hint={`Calcul automatique : (${formData.dureeHebdoLegale || '40'}h × 52 sem.) / 12 mois = ${formData.diviseurMensuelHeures || '173.33'}h`}
                            />
                            <Input
                                label="Seuil HS Tranche 1 (h)"
                                type="number"
                                step="0.5"
                                required={!isReadOnly}
                                disabled={isReadOnly}
                                value={formData.seuilHsTranche1}
                                onChange={e => upd('seuilHsTranche1', e.target.value)}
                                hint="Premières heures supp. avant bascule Tranche 2"
                            />
                            <Input
                                label="Plafond HS / Semaine (h)"
                                type="number"
                                step="0.5"
                                required={!isReadOnly}
                                disabled={isReadOnly}
                                value={formData.plafondHsHebdo}
                                onChange={e => upd('plafondHsHebdo', e.target.value)}
                                hint="Limite légale max (ex: 20h)"
                            />
                            <div className="col-span-2 pt-2 border-t border-[var(--color-border-subtle)] space-y-2">
                                <div className="flex items-center justify-between">
                                    <div>
                                        <span className="text-xs font-semibold text-[var(--color-fg)]">Exonération IRSA des Heures Supplémentaires</span>
                                        <p className="text-[11px] text-[var(--color-fg-muted)]">Article 01.03.03-9° CGI (salariés non-cadres éligibles)</p>
                                    </div>
                                    <label className="relative inline-flex items-center cursor-pointer">
                                        <input
                                            type="checkbox"
                                            className="sr-only peer"
                                            disabled={isReadOnly}
                                            checked={!!formData.exonerationHsIrsaActive}
                                            onChange={e => upd('exonerationHsIrsaActive', e.target.checked)}
                                        />
                                        <div className="w-9 h-5 bg-[var(--color-border-strong)] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-[var(--color-on-color)] after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-[var(--color-surface)] after:border-[var(--color-border)] after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-[var(--color-brand-500)]"></div>
                                    </label>
                                </div>
                                {!formData.exonerationHsIrsaActive ? (
                                    <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)] rounded">
                                        <span className="font-semibold text-[var(--color-fg)]">Exonération désactivée pour cet exercice :</span>
                                        <p className="text-[11px] mt-0.5">100% des heures supplémentaires accomplies seront intégrées dans l'assiette imposable IRSA (aucun abattement ni repli appliqué).</p>
                                    </div>
                                ) : (
                                    <Input
                                        label="Plafond mensuel HS exonérées (h)"
                                        type="number"
                                        step="0.5"
                                        min="0"
                                        required={!isReadOnly}
                                        disabled={isReadOnly}
                                        value={formData.plafondMensuelHsExonerees}
                                        onChange={e => upd('plafondMensuelHsExonerees', e.target.value)}
                                        hint="Plafond d'heures exonérées par mois civil (ex: 20h selon CGI Art. 01.03.03-9°)"
                                    />
                                )}
                            </div>
                        </div>
                    </div>

                    {/* COLONNE DROITE : Congés Payés */}
                    <div id="exercice-conges" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3 scroll-mt-24">
                        <SectionTitle
                            icon={Palmtree}
                            label="5. Droits & Valorisation des Congés Payés"
                            color="var(--color-brand-500)"
                            badge="Code du Travail -88"
                        />
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <Input
                                label="Acquisition mensuelle (jours)"
                                type="number"
                                step="0.01"
                                required={!isReadOnly}
                                disabled={isReadOnly}
                                value={formData.acquisitionCongeMensuelJours}
                                onChange={e => upd('acquisitionCongeMensuelJours', e.target.value)}
                                hint="Nombre de jours acquis par mois dans cet exercice"
                            />
                            <Input
                                label="Diviseur valorisation (jours)"
                                type="number"
                                required={!isReadOnly}
                                disabled={isReadOnly}
                                value={formData.diviseurValorisationCongeJours}
                                onChange={e => upd('diviseurValorisationCongeJours', e.target.value)}
                                hint="Diviseur appliqué au salaire brut pour valoriser un jour"
                            />
                        </div>
                    </div>

                    {/* SECTION 6 : Taux de Majoration des Heures Spéciales (Pleine largeur) */}
                    <div id="exercice-majorations" className="lg:col-span-2 scroll-mt-24"><MajorationsFormSection
                        majorations={formData.majorations}
                        onChange={m => setFormData(prev => ({ ...prev, majorations: m }))}
                        isReadOnly={isReadOnly}
                    /></div>

                    {/* SECTION 7 : Barème Progressif IRSA (Pleine largeur) */}
                    <div id="exercice-bareme" className="lg:col-span-2 scroll-mt-24"><TranchesIrsaFormSection
                        tranches={formData.tranchesIrsa || []}
                        onChange={v => upd('tranchesIrsa', v)}
                        isReadOnly={isReadOnly}
                    /></div>

                </div>
            </form>
    );

    return <Modal isOpen={isOpen} onClose={onClose} title={meta.title} size="full" className="!max-w-[96rem] max-h-[96vh]" footer={footer}>{form}</Modal>;
}

function getTranchesForConfig(config, baremesList) {
    if (!config || !baremesList) return [];
    if (config.idConfigLegale) {
        const byId = baremesList.filter(t => t.idConfigLegale === config.idConfigLegale);
        if (byId.length > 0) return byId.sort((a, b) => (a.numeroTranche || 0) - (b.numeroTranche || 0));
    }
    const cDebut = fmtDateInput(config.dateDebut);
    const cFin = fmtDateInput(config.dateFin) || '';
    return baremesList.filter(t => {
        const tDebut = fmtDateInput(t.dateDebut);
        const tFin = fmtDateInput(t.dateFin) || '';
        return tDebut === cDebut && tFin === cFin;
    }).sort((a, b) => (a.numeroTranche || 0) - (b.numeroTranche || 0));
}

function getMajorationsForConfig(config, majorationsList) {
    if (!config || !majorationsList) return [];
    let list = [];
    if (config.idConfigLegale) {
        const byId = majorationsList.filter(m => m.idConfigLegale === config.idConfigLegale);
        if (byId.length > 0) list = byId;
    }
    if (list.length === 0) {
        const cDebut = fmtDateInput(config.dateDebut);
        const cFin = fmtDateInput(config.dateFin) || '';
        list = majorationsList.filter(m => {
            const mDebut = fmtDateInput(m.dateDebut);
            const mFin = fmtDateInput(m.dateFin) || '';
            return mDebut === cDebut && mFin === cFin;
        });
    }
    return [...list].sort((a, b) => (MAJORATION_ORDER[a.typeMajoration] || 99) - (MAJORATION_ORDER[b.typeMajoration] || 99));
}

// ==============================
// COMPOSANT PRINCIPAL
// ==============================
export default function ReglementationFiscaleTab() {
    // État local : réglementation, formulaires, archives et confirmations.
    const [configs, setConfigs] = useState([]);
    const [baremesIrsa, setBaremesIrsa] = useState([]);
    const [majorations, setMajorations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [modalError, setModalError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const [modalMode, setModalMode] = useState('init'); // 'init' | 'revaloriser' | 'edit' | 'view'
    const [editingId, setEditingId] = useState(null);
    const [formData, setFormData] = useState({ ...EMPTY_FORM });
    const [showArchived, setShowArchived] = useState(false);
    const [activeSection, setActiveSection] = useState('salaires');
    // Confirmation annulation exercice programmé
    const [confirmAnnulation, setConfirmAnnulation] = useState(null); // { id, codeVersion } | null
    const [annulationLoading, setAnnulationLoading] = useState(false);
    const toast = useToast();
    const todayStr = fmtDateInput(new Date());

    const load = async () => {
        setLoading(true);
        setError(null);
        try {
            const [dataConfigs, dataIrsa, dataMajs] = await Promise.all([
                paieConfigService.getAllConfigsLegales(),
                paieConfigService.getBaremesIrsa(),
                paieConfigService.getTauxMajoration()
            ]);
            setConfigs(dataConfigs || []);
            setBaremesIrsa(dataIrsa || []);
            setMajorations(dataMajs || []);
        } catch {
            setError("Impossible de charger les exercices fiscaux.");
        } finally {
            setLoading(false);
        }
    };

    // Effet : charge la réglementation fiscale à l'ouverture.
    useEffect(() => { load(); }, []);

    const activeConfig = configs.find(c => getStatut(c) === 'EN_VIGUEUR');
    const activeTranches = getTranchesForConfig(activeConfig, baremesIrsa);
    const activeMajorations = getMajorationsForConfig(activeConfig, majorations);
    const displayedConfigs = showArchived ? configs : configs.filter(c => getStatut(c) !== 'ARCHIVE');

    // ---- Ouverture "Initialiser" : aucun exercice actif ----
    const handleOpenInit = () => {
        setFormData({ ...EMPTY_FORM, dateDebut: todayStr, tranchesIrsa: [], majorations: [] });
        setModalMode('init');
        setEditingId(null);
        setModalError(null);
        setShowModal(true);
    };

    // ---- Ouverture "Nouvelle Loi de Finances" : clôture actif + crée le suivant ----
    const handleOpenRevaloriser = () => {
        if (!activeConfig) return;
        const currentTranches = getTranchesForConfig(activeConfig, baremesIrsa);
        const clonedTranches = currentTranches.length > 0
            ? currentTranches.map(t => ({
                numeroTranche: t.numeroTranche,
                trancheMin: t.trancheMin,
                trancheMax: t.trancheMax ?? '',
                taux: t.taux
            }))
            : [];

        const currentMajs = getMajorationsForConfig(activeConfig, majorations);
        const clonedMajs = currentMajs.length > 0
            ? currentMajs.map(m => ({
                typeMajoration: m.typeMajoration,
                taux: m.taux,
                description: m.description || ''
            }))
            : [];

        const f = {
            codeVersion: '', libelle: '',
            dateDebut: '', dateFin: '',
            smeNational: String(activeConfig.smeNational ?? ''),
            smeAgricole: String(activeConfig.smeAgricole ?? ''),
            plafondCotisationNbSme: String(activeConfig.plafondCotisationNbSme ?? ''),
            plafondAvanceEmployeurPct: String(activeConfig.plafondAvanceEmployeurPct ?? '10'),
            quotiteCessiblePct: String(activeConfig.quotiteCessiblePct ?? '33.333333'),
            seuilExonerationIrsa: String(activeConfig.seuilExonerationIrsa ?? ''),
            minimumPerceptionIrsa: String(activeConfig.minimumPerceptionIrsa ?? ''),
            deductionChargeFamille: String(activeConfig.deductionChargeFamille ?? ''),
            arrondiBaseIrsa: activeConfig.arrondiBaseIrsa ?? 'CENTAINE_INF',
            dureeHebdoLegale: String(activeConfig.dureeHebdoLegale ?? ''),
            diviseurMensuelHeures: String(activeConfig.diviseurMensuelHeures ?? ''),
            seuilHsTranche1: String(activeConfig.seuilHsTranche1 ?? ''),
            plafondHsHebdo: String(activeConfig.plafondHsHebdo ?? ''),
            exonerationHsIrsaActive: activeConfig.exonerationHsIrsaActive ?? false,
            plafondMensuelHsExonerees: String(activeConfig.plafondMensuelHsExonerees ?? ''),
            acquisitionCongeMensuelJours: String(activeConfig.acquisitionCongeMensuelJours ?? ''),
            diviseurValorisationCongeJours: String(activeConfig.diviseurValorisationCongeJours ?? ''),
            tranchesIrsa: clonedTranches,
            majorations: clonedMajs,
        };
        setFormData(f);
        setModalMode('revaloriser');
        setEditingId(null);
        setModalError(null);
        setShowModal(true);
    };

    // ---- Ouverture "Détails" : lecture seule pour un exercice archivé ----
    const handleOpenView = (config) => {
        const tranches = getTranchesForConfig(config, baremesIrsa);
        const majs = getMajorationsForConfig(config, majorations);
        setFormData({
            codeVersion: config.codeVersion ?? '',
            libelle: config.libelle ?? '',
            dateDebut: fmtDateInput(config.dateDebut) ?? '',
            dateFin: fmtDateInput(config.dateFin) ?? '',
            smeNational: String(config.smeNational ?? ''),
            smeAgricole: String(config.smeAgricole ?? ''),
            plafondCotisationNbSme: String(config.plafondCotisationNbSme ?? ''),
            plafondAvanceEmployeurPct: String(config.plafondAvanceEmployeurPct ?? '10'),
            quotiteCessiblePct: String(config.quotiteCessiblePct ?? '33.333333'),
            seuilExonerationIrsa: String(config.seuilExonerationIrsa ?? ''),
            minimumPerceptionIrsa: String(config.minimumPerceptionIrsa ?? ''),
            deductionChargeFamille: String(config.deductionChargeFamille ?? ''),
            arrondiBaseIrsa: config.arrondiBaseIrsa ?? 'CENTAINE_INF',
            dureeHebdoLegale: String(config.dureeHebdoLegale ?? ''),
            diviseurMensuelHeures: String(config.diviseurMensuelHeures ?? ''),
            seuilHsTranche1: String(config.seuilHsTranche1 ?? ''),
            plafondHsHebdo: String(config.plafondHsHebdo ?? ''),
            exonerationHsIrsaActive: config.exonerationHsIrsaActive ?? false,
            plafondMensuelHsExonerees: String(config.plafondMensuelHsExonerees ?? ''),
            acquisitionCongeMensuelJours: String(config.acquisitionCongeMensuelJours ?? ''),
            diviseurValorisationCongeJours: String(config.diviseurValorisationCongeJours ?? ''),
            tranchesIrsa: tranches.map(t => ({
                numeroTranche: t.numeroTranche,
                trancheMin: t.trancheMin,
                trancheMax: t.trancheMax ?? '',
                taux: t.taux
            })),
            majorations: majs.length > 0 ? majs.map(m => ({
                typeMajoration: m.typeMajoration,
                taux: m.taux,
                description: m.description || ''
            })) : [],
        });
        setModalMode('view');
        setEditingId(null);
        setModalError(null);
        setShowModal(true);
    };

    // ---- Annulation d'un exercice PROGRAMMÉ ----
    const handleAnnuler = async (id, codeVersion) => {
        setAnnulationLoading(true);
        try {
            await paieConfigService.annulerConfigProgrammee(id);
            toast({ title: 'Exercice annulé', message: `"${codeVersion}" a été supprimé. L'exercice précédent est de nouveau en vigueur.`, type: 'success' });
            setConfirmAnnulation(null);
            load();
        } catch (err) {
            toast({ title: 'Annulation impossible', message: getErrorMessage(err, 'Erreur lors de l\'annulation.'), type: 'error' });
        } finally {
            setAnnulationLoading(false);
        }
    };

    // ---- Ouverture "Corriger" : correction immédiate d'un exercice existant ----
    const handleOpenEdit = (config) => {
        const tranches = getTranchesForConfig(config, baremesIrsa);
        const majs = getMajorationsForConfig(config, majorations);
        setFormData({
            codeVersion: config.codeVersion ?? '',
            libelle: config.libelle ?? '',
            dateDebut: fmtDateInput(config.dateDebut) ?? '',
            dateFin: fmtDateInput(config.dateFin) ?? '',
            smeNational: String(config.smeNational ?? ''),
            smeAgricole: String(config.smeAgricole ?? ''),
            plafondCotisationNbSme: String(config.plafondCotisationNbSme ?? ''),
            plafondAvanceEmployeurPct: String(config.plafondAvanceEmployeurPct ?? '10'),
            quotiteCessiblePct: String(config.quotiteCessiblePct ?? '33.333333'),
            seuilExonerationIrsa: String(config.seuilExonerationIrsa ?? ''),
            minimumPerceptionIrsa: String(config.minimumPerceptionIrsa ?? ''),
            deductionChargeFamille: String(config.deductionChargeFamille ?? ''),
            arrondiBaseIrsa: config.arrondiBaseIrsa ?? 'CENTAINE_INF',
            dureeHebdoLegale: String(config.dureeHebdoLegale ?? ''),
            diviseurMensuelHeures: String(config.diviseurMensuelHeures ?? ''),
            seuilHsTranche1: String(config.seuilHsTranche1 ?? ''),
            plafondHsHebdo: String(config.plafondHsHebdo ?? ''),
            exonerationHsIrsaActive: config.exonerationHsIrsaActive ?? false,
            plafondMensuelHsExonerees: String(config.plafondMensuelHsExonerees ?? ''),
            acquisitionCongeMensuelJours: String(config.acquisitionCongeMensuelJours ?? ''),
            diviseurValorisationCongeJours: String(config.diviseurValorisationCongeJours ?? ''),
            tranchesIrsa: tranches.length > 0 ? tranches.map(t => ({
                numeroTranche: t.numeroTranche,
                trancheMin: t.trancheMin,
                trancheMax: t.trancheMax ?? '',
                taux: t.taux
            })) : [],
            majorations: majs.length > 0 ? majs.map(m => ({
                typeMajoration: m.typeMajoration,
                taux: m.taux,
                description: m.description || ''
            })) : [],
        });
        setModalMode('edit');
        setEditingId(config.idConfigLegale);
        setModalError(null);
        setShowModal(true);
    };

    const buildPayload = () => ({
        codeVersion: formData.codeVersion,
        libelle: formData.libelle,
        dateDebut: formData.dateDebut || null,
        dateFin: formData.dateFin || null,
        tranchesIrsa: (formData.tranchesIrsa || []).map(t => ({
            numeroTranche: t.numeroTranche,
            trancheMin: parseFloat(t.trancheMin) || 0,
            trancheMax: t.trancheMax !== '' && t.trancheMax !== null && t.trancheMax !== undefined ? parseFloat(t.trancheMax) : null,
            taux: parseFloat(t.taux) || 0
        })),
        majorations: (formData.majorations || []).map(m => ({
            typeMajoration: m.typeMajoration,
            taux: parseSafe(m.taux, 0),
            description: m.description || ''
        })),
        smeNational: parseSafe(formData.smeNational),
        smeAgricole: parseSafe(formData.smeAgricole),
        plafondCotisationNbSme: parseSafe(formData.plafondCotisationNbSme, 8),
        plafondAvanceEmployeurPct: parseSafe(formData.plafondAvanceEmployeurPct, 10),
        quotiteCessiblePct: parseSafe(formData.quotiteCessiblePct, 33.333333),
        seuilExonerationIrsa: parseSafe(formData.seuilExonerationIrsa),
        minimumPerceptionIrsa: parseSafe(formData.minimumPerceptionIrsa),
        deductionChargeFamille: parseSafe(formData.deductionChargeFamille),
        arrondiBaseIrsa: formData.arrondiBaseIrsa || 'CENTAINE_INF',
        dureeHebdoLegale: parseSafe(formData.dureeHebdoLegale, 40),
        diviseurMensuelHeures: parseSafe(formData.diviseurMensuelHeures, 173.33),
        seuilHsTranche1: parseSafe(formData.seuilHsTranche1, null),
        plafondHsHebdo: parseSafe(formData.plafondHsHebdo, 20),
        exonerationHsIrsaActive: Boolean(formData.exonerationHsIrsaActive),
        plafondMensuelHsExonerees: formData.exonerationHsIrsaActive ? parseSafe(formData.plafondMensuelHsExonerees, 0) : 0,
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
            document.getElementById('exercice-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) return <div className="p-4"><SkeletonTable rows={4} columns={4} /></div>;

    // Rendu
    return (
        <div className="space-y-5">
            {error && <Alert type="error" message={error} />}

            {/* ---- Tableau de bord de l'exercice en vigueur ---- */}
            {activeConfig && (
                <div id="exercice-actif" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm scroll-mt-20">
                    <div className="px-5 py-4 border-b border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3">
                        <div className="flex items-center gap-3">
                            <CheckCircle2 size={18} className="text-[var(--color-success-500)]" />
                            <div>
                                <h4 className="font-bold text-[var(--color-fg)] text-base">{activeConfig.codeVersion} <StatutBadge statut="EN_VIGUEUR" /></h4>
                                <PeriodeBadge config={activeConfig} />
                                <p className="text-sm text-[var(--color-fg-muted)] mt-1">{activeConfig.libelle}</p>
                            </div>
                        </div>
                        <div className="flex items-center gap-2 flex-wrap">
                            <Button variant="secondary" size="sm" onClick={handleOpenRevaloriser} iconLeft={<TrendingUp size={14} />}>Nouvelle loi de finances</Button>
                            <Button variant="secondary" size="sm"
                                onClick={() => handleOpenEdit(activeConfig)}
                                iconLeft={<Edit2 size={14} />}
                            >
                                Corriger
                            </Button>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 p-5 border-b border-[var(--color-border-subtle)]">
                        <KpiField label="SME national" value={fmtAr(activeConfig.smeNational)} />
                        <KpiField label="Plafond social" value={fmtAr(activeConfig.plafondCotisationMensuel)} />
                        <KpiField label="Minimum IRSA" value={fmtAr(activeConfig.minimumPerceptionIrsa)} />
                    </div>
                    <div className="flex gap-1 overflow-x-auto px-5 pt-4" aria-label="Rubriques de l’exercice">
                        {[['salaires', 'Salaires'], ['irsa', 'IRSA'], ['travail', 'Travail'], ['conges', 'Congés'], ['retenues', 'Retenues']].map(([key, label]) => <button key={key} type="button" aria-pressed={activeSection === key} onClick={() => setActiveSection(key)} className={`px-4 py-2 text-sm font-medium shrink-0 border-b-2 ${activeSection === key ? 'border-[var(--color-brand-500)] text-[var(--color-brand-700)]' : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}>{label}</button>)}
                    </div>
                    <div className="p-5 min-h-40 space-y-4">
                        {activeSection === 'salaires' && <div>
                            <SectionTitle icon={Banknote} label="Salaire Minimum & Plafonds Sociaux" color="var(--color-success-600)" />
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                                <KpiField label="SME National" value={fmtAr(activeConfig.smeNational)} sub="Secteur non agricole" />
                                <KpiField label="SME Agricole" value={fmtAr(activeConfig.smeAgricole)} sub="Secteur agricole" />
                                <KpiField label="Multiplicateur Plafond Social" value={`× ${fmtNb(activeConfig.plafondCotisationNbSme, 0)} SME`} sub="Plafond CNaPS et santé si applicable" />
                                <KpiField label="Plafond Mensuel Calculé" value={fmtAr(activeConfig.plafondCotisationMensuel)} sub="Base maximale soumise aux cotisations" />
                            </div>
                        </div>}

                        {activeSection === 'irsa' && <div>
                            <SectionTitle icon={Percent} label="Règles Fiscales IRSA" color="var(--color-warning-600)" badge="Code Général des Impôts" />
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                                <KpiField label="Seuil d'exonération" value={fmtAr(activeConfig.seuilExonerationIrsa)} sub="Base imposable en dessous → IRSA = 0" />
                                <KpiField label="Minimum de Perception" value={fmtAr(activeConfig.minimumPerceptionIrsa)} sub="Plancher légal si IRSA calculé insuffisant" />
                                <KpiField label="Déduction / Personne à Charge" value={fmtAr(activeConfig.deductionChargeFamille)} sub="Conjoint, enfants légitimes à charge" />
                                <KpiField label="Exonération HS (CGI)" value={(activeConfig.exonerationHsIrsaActive && Number(activeConfig.plafondMensuelHsExonerees) > 0) ? `${fmtNb(activeConfig.plafondMensuelHsExonerees, 0)}h max / mois` : "Désactivée (0h)"} sub="Art. 01.03.03-9° non-cadres" />
                            </div>

                            {/* Grille des tranches progressives de l'exercice actif */}
                            {activeTranches.length > 0 && (
                                <div className="mt-3 border border-[var(--color-border-subtle)] overflow-hidden">
                                    <div className="px-3 py-2 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                        Barème Progressif IRSA en vigueur ({activeTranches.length} tranches)
                                    </div>
                                    <Table bordered={false}>
                                        <Thead>
                                            <Th className="w-28">Tranche</Th>
                                            <Th className="text-right">Seuil Minimum (Ar)</Th>
                                            <Th className="text-right">Seuil Maximum (Ar)</Th>
                                            <Th className="text-right w-24">Taux IRSA</Th>
                                        </Thead>
                                        <Tbody>
                                            {activeTranches.map(t => (
                                                <Tr key={t.idTranche}>
                                                    <Td isData className="font-bold text-[var(--color-brand-500)]">Tranche {t.numeroTranche}</Td>
                                                    <Td isData className="text-right font-semibold">{fmtAr(t.trancheMin)}</Td>
                                                    <Td isData className="text-right font-semibold">{t.trancheMax ? fmtAr(t.trancheMax) : 'Sans plafond (∞)'}</Td>
                                                    <Td isData className="text-right font-bold text-[var(--color-danger-500)]">{t.taux}%</Td>
                                                </Tr>
                                            ))}
                                        </Tbody>
                                    </Table>
                                </div>
                            )}
                        </div>}

                        {activeSection === 'retenues' && <div>
                            <SectionTitle icon={Scale} label="Plafonds de retenues" color="var(--color-brand-600)" />
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                                <KpiField label="Avances employeur" value={`${fmtNb(activeConfig.plafondAvanceEmployeurPct, 4)}%`} sub="Plafond de remboursement" />
                                <KpiField label="Quotité cessible" value={`${fmtNb(activeConfig.quotiteCessiblePct, 4)}%`} sub="Prêts et saisies" />
                            </div>
                        </div>}

                        {activeSection === 'travail' && <div>
                            <SectionTitle icon={Timer} label="Temps de Travail & Majorations Spéciales" color="var(--color-fg-muted)" />
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                                <KpiField label="Durée Légale Hebdo" value={`${fmtNb(activeConfig.dureeHebdoLegale, 0)}h`} sub="Code du Travail Art. 83" />
                                <KpiField label="Diviseur Mensuel" value={`${fmtNb(activeConfig.diviseurMensuelHeures, 2)}h`} sub="Taux horaire de référence" />
                                <KpiField label="HS Tranche 1" value={`${fmtNb(activeConfig.seuilHsTranche1, 0)} premières HS`} sub="Seuil bascule Tranche 2" />
                                <KpiField label="Plafond HS / Semaine" value={`${fmtNb(activeConfig.plafondHsHebdo, 0)}h max`} sub="Limite légale absolue" />
                            </div>

                            {/* Grille des majorations en vigueur pour cet exercice */}
                            {activeMajorations.length > 0 && (
                                <div className="mt-3 border border-[var(--color-border-subtle)] overflow-hidden">
                                    <div className="px-3 py-2 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center justify-between">
                                        <span>Taux de Majoration des Heures Spéciales en vigueur</span>
                                        <span className="text-[10px] font-normal lowercase text-[var(--color-fg-muted)]">Code du Travail Malgache (Art. 83-84)</span>
                                    </div>
                                    <Table bordered={false}>
                                        <Thead>
                                            <Th className="w-32">Type</Th>
                                            <Th>Description / Motif Légal</Th>
                                            <Th className="text-right w-28">Majoration (%)</Th>
                                            <Th className="text-right w-28">Multiplicateur</Th>
                                        </Thead>
                                        <Tbody>
                                            {activeMajorations.map(m => {
                                                const pct = Math.round((parseFloat(m.taux) || 0) * 100);
                                                const mult = (1 + (parseFloat(m.taux) || 0)).toFixed(2);
                                                return (
                                                    <Tr key={m.idTauxMajoration || m.typeMajoration}>
                                                        <Td isData className="font-bold text-[var(--color-brand-500)]">{m.typeMajoration}</Td>
                                                        <Td className="text-[var(--color-fg)]">{m.description || '—'}</Td>
                                                        <Td isData className="text-right font-bold text-[var(--color-warning-600)]">+{pct}%</Td>
                                                        <Td isData muted className="text-right">x{mult}</Td>
                                                    </Tr>
                                                );
                                            })}
                                        </Tbody>
                                    </Table>
                                </div>
                            )}
                        </div>}

                        {activeSection === 'conges' && <div>
                            <SectionTitle icon={Palmtree} label="Congés Payés" color="var(--color-brand-500)" />
                            <div className="grid grid-cols-2 gap-2">
                                <KpiField label="Acquisition mensuelle" value={`${fmtNb(activeConfig.acquisitionCongeMensuelJours, 2)} j/mois`} sub="= 30 j/an" />
                                <KpiField label="Valorisation d'1 jour de congé" value={`Salaire brut ÷ ${activeConfig.diviseurValorisationCongeJours}`} sub="Décompte calendaire standard" />
                            </div>
                        </div>}
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
                    const isProgramme = statut === 'PROGRAMME';
                    const isArchive = statut === 'ARCHIVE';
                    return (
                        <div
                            key={config.idConfigLegale}
                            className={`border flex items-center gap-4 px-4 py-3 transition-all ${isActive
                                    ? 'bg-[var(--color-success-500)]/5 border-[var(--color-success-500)]/30'
                                    : isProgramme
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
                            {!isActive && <div className="hidden md:flex items-center gap-6 text-xs text-[var(--color-fg-muted)] shrink-0">
                                <span><span className="font-bold text-[var(--color-fg)]">{fmtAr(config.smeNational)}</span><br />SME</span>
                                <span><span className="font-bold text-[var(--color-fg)]">{fmtAr(config.minimumPerceptionIrsa)}</span><br />Min. IRSA</span>
                                <span><span className="font-bold text-[var(--color-fg)]">{config.dureeHebdoLegale}h</span><br />Légale</span>
                                <span><span className="font-bold text-[var(--color-fg)]">{fmtAr(config.plafondCotisationMensuel)}</span><br />Plafond social</span>
                            </div>}

                            {/* Actions selon le statut */}
                            <div className="flex items-center gap-1 shrink-0">
                                {/* ARCHIVÉ → bouton Détails (lecture seule) */}
                                {isArchive && (
                                    <button
                                        onClick={() => handleOpenView(config)}
                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] transition-colors"
                                        title="Voir les détails de cet exercice archivé"
                                    >
                                        <Eye size={14} />
                                    </button>
                                )}
                                {/* EN_VIGUEUR → bouton Corriger */}
                                {isActive && (
                                    <button
                                        onClick={() => document.getElementById('exercice-actif')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
                                        className="px-2 py-1 text-xs text-[var(--color-brand-600)] hover:underline"
                                    >
                                        Voir la fiche
                                    </button>
                                )}
                                {/* PROGRAMMÉ → bouton Corriger + bouton Annuler */}
                                {isProgramme && (
                                    <>
                                        <button
                                            onClick={() => handleOpenEdit(config)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] transition-colors"
                                            title="Corriger les valeurs de cet exercice"
                                        >
                                            <Edit2 size={14} />
                                        </button>
                                        <button
                                            onClick={() => setConfirmAnnulation({ id: config.idConfigLegale, codeVersion: config.codeVersion })}
                                            className="p-1.5 text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 transition-colors"
                                            title="Annuler cet exercice programmé (finallement aucun changement)"
                                        >
                                            <X size={14} />
                                        </button>
                                    </>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>

            {/* ---- Références et valeurs enregistrées ---- */}
            <details className="bg-[var(--color-brand-500)]/8 border border-[var(--color-brand-500)]/20 p-4">
                <summary className="cursor-pointer font-semibold text-sm text-[var(--color-fg)]">Sources et références de l’exercice</summary>
                <div className="flex items-start gap-3 mt-3">
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
                            <p>• <strong>Congés payés</strong> :
                                <strong> {fmtNb(activeConfig.acquisitionCongeMensuelJours, 2)} j</strong>/mois,
                                décompte calendaire ÷&nbsp;<strong>{activeConfig.diviseurValorisationCongeJours}</strong>
                            </p>
                            <p>• <strong>Retenues sur salaire</strong> : avance employeur&nbsp;<strong>{fmtNb(activeConfig.plafondAvanceEmployeurPct, 4)}%</strong>, quotité cessible&nbsp;<strong>{fmtNb(activeConfig.quotiteCessiblePct, 4)}%</strong>.</p>
                        </>
                    ) : (
                        <p className=" text-[var(--color-warning-600)]">Aucun exercice fiscal actif — initialisez le premier exercice ci-dessous.</p>
                    )}
                </div>
                </div>
            </details>


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

            {/* ---- Modal de confirmation d'annulation ---- */}
            <ConfirmDialog
                isOpen={Boolean(confirmAnnulation)}
                onClose={() => setConfirmAnnulation(null)}
                onConfirm={() => handleAnnuler(confirmAnnulation.id, confirmAnnulation.codeVersion)}
                title="Annuler l'exercice programmé"
                confirmLabel="Annuler la LF programmée"
                cancelLabel="Conserver"
                variant="danger"
                loading={annulationLoading}
            >
                {confirmAnnulation && (
                    <div className="space-y-2">
                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                            Vous êtes sur le point d'annuler <strong className="text-[var(--color-fg)]">{confirmAnnulation.codeVersion}</strong>.
                            Cette action supprimera définitivement cet exercice et
                            l'exercice précédent sera remis <strong>en vigueur</strong> sans date de fin.
                        </p>
                        <p className="text-[11px] text-[var(--color-warning-600)]">
                            ⚠ Les barèmes IRSA et majorations associés seront également supprimés.
                        </p>
                    </div>
                )}
            </ConfirmDialog>
        </div>
    );
}
