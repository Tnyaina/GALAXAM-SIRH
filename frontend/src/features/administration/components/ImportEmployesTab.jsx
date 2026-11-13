import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { AlertCircle, ArrowRight, Check, ChevronLeft, ChevronRight, Download, FileSpreadsheet, Search } from 'lucide-react';
import apiClient from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input, { AutocompleteInput, Checkbox, Radio } from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { Link } from 'react-router-dom';
import { useContractReferentiels } from '@/features/employees/hooks/useContractReferentiels';
import organisationService from '@/features/organization/api/organisationService';
import { VILLES_MADAGASCAR } from '@/constants/madagascarGeo';
import ContratFieldsSection, { PeriodeEssaiFieldsSection } from '@/features/employees/components/carriere/form/ContratFieldsSection';
import AffectationFieldsSection from '@/features/employees/components/carriere/form/AffectationFieldsSection';
import { pairContractAndIntervenant } from '@/features/employees/utils/contractTypePairing';

const InitialisationSoldeModal = lazy(() => import('@/features/leave/components/InitialisationSoldeModal'));

const MOCK = 'A COMPLETER PAR DAF';
const mockValues = new Set([MOCK, '00000000', '1980-01-01', '2000-01-01']);
const sections = [
  ['identite', 'Identité & CIN'],
  ['coordonnees', 'Coordonnées'],
  ['famille', 'Famille & urgence'],
  ['banque', 'Banque'],
  ['indemnites', 'Indemnités'],
  ['embauche', 'Préparation embauche'],
];
const fields = {
  identite: [['matricule', 'Matricule'], ['nom', 'Nom'], ['prenom', 'Prénoms'], ['sexe', 'Sexe'], ['dateNaissance', 'Date de naissance'], ['lieuNaissance', 'Lieu de naissance'], ['cinNumero', 'Numéro CIN'], ['cinDateDelivrance', 'Délivrance CIN'], ['cinLieuDelivrance', 'Lieu de délivrance'], ['cinDuplicataDate', 'Date duplicata']],
  coordonnees: [['adresse', 'Adresse'], ['telephone', 'Téléphone principal'], ['numAffiliationCnaps', 'Numéro CNaPS']],
};
const dateFields = new Set(['dateNaissance', 'cinDateDelivrance', 'cinDuplicataDate']);
const pending = row => (row.mockFields || []).filter(key => mockValues.has(row[key])).length + (row.contactsUrgence || []).reduce((total, contact) => total + Number(contact.nom === MOCK) + Number(contact.prenom === MOCK), 0);

function downloadCorrections(corrections) {
  const escape = value => `"${String(value ?? '').replaceAll('"', '""')}"`;
  const lines = [['matricule', 'champ_a_corriger', 'valeur_provisoire_import', 'valeur_confirmee_par_DAF', 'statut'], ...corrections.map(item => [item.matricule, item.champ, item.valeurProvisoire, '', 'A_COMPLETER'])];
  const csv = '\uFEFF' + lines.map(line => line.map(escape).join(';')).join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url; link.download = 'corrections_daf_employes.csv'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function Field({ label, value, onChange, type = 'text', provisional = false, hint }) {
  return <Input
    label={<span className="inline-flex flex-wrap items-center gap-2">{label}{provisional && <span className="bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-900">PROVISOIRE</span>}</span>}
    type={type}
    value={value ?? ''}
    onChange={event => onChange(event.target.value)}
    hint={hint}
    className={provisional ? '!border-l-[3px] !border-l-amber-600' : ''}
  />;
}

const referenceId = (items, idKeys, value) => {
  if (value === null || value === undefined || value === '') return '';
  const match = items.find(item => [...idKeys, 'code', 'codeSite', 'codeFiliale', 'codeDepartement', 'libelle', 'raisonSociale']
    .some(key => item[key] != null && String(item[key]).toLocaleLowerCase('fr') === String(value).toLocaleLowerCase('fr')));
  return match ? String(itemId(match, idKeys)) : '';
};
const itemId = (item, keys) => keys.map(key => item[key]).find(value => value != null);

function preparedForm(embauche = {}, refs) {
  return {
    ...embauche,
    idFiliale: referenceId(refs.filiales, ['idFiliale'], embauche.idFiliale || embauche.filiale),
    idTypeContrat: referenceId(refs.typesContrat, ['idTypeContrat'], embauche.idTypeContrat || embauche.typeContrat),
    idTypeIntervenant: referenceId(refs.typesIntervenant, ['idTypeIntervenant'], embauche.idTypeIntervenant || embauche.typeIntervenant),
    idSite: referenceId(refs.sites, ['idSite'], embauche.idSite || embauche.site),
    idDepartement: referenceId(refs.departements, ['idDepartement'], embauche.idDepartement || embauche.departement),
    idService: referenceId(refs.services, ['idService'], embauche.idService),
    idFonction: referenceId(refs.fonctions, ['idFonction'], embauche.idFonction),
    idCategorieProfessionnelle: referenceId(refs.categoriesPro, ['idCategorieProfessionnelle', 'idCategorie'], embauche.idCategorieProfessionnelle || embauche.categorie),
    idModeCalculPaie: referenceId(refs.modesPaie, ['idModeCalculPaie', 'idModeCalcul'], embauche.idModeCalculPaie || embauche.modeCalculPaie),
    dateDebut: embauche.dateDebut || '', dateSignature: embauche.dateSignature || '', dateFin: embauche.dateFin || '',
    numeroContrat: embauche.numeroContrat || '', motifCddCode: embauche.motifCddCode || '',
    comptabiliseEffectif: embauche.comptabiliseEffectif !== false,
    aPeriodeEssai: Boolean(embauche.aPeriodeEssai), dureeEssaiMois: embauche.dureeEssaiMois || '3', dateFinEssai: embauche.dateFinEssai || '',
    idResponsableHierarchique: embauche.idResponsableHierarchique || '', salaireBase: embauche.salaireBase || '',
  };
}

export default function ImportEmployesTab() {
  const [rows, setRows] = useState([]);
  const [selected, setSelected] = useState(0);
  const [section, setSection] = useState('identite');
  const [query, setQuery] = useState('');
  const [onlyPending, setOnlyPending] = useState(false);
  const [onlyMockFields, setOnlyMockFields] = useState(false);
  const [fileName, setFileName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const [leaveEmployee, setLeaveEmployee] = useState(null);
  const [leaveCompleted, setLeaveCompleted] = useState([]);
  const [confirmImportOpen, setConfirmImportOpen] = useState(false);
  const [importContracts, setImportContracts] = useState(false);
  const [banques, setBanques] = useState([]);
  const refs = useContractReferentiels(true);
  useEffect(() => {
    let active = true;
    organisationService.getBanques().then(data => { if (active) setBanques((data || []).filter(bank => bank.estActif !== false && bank.actif !== false)); })
      .catch(() => { if (active) setError('Impossible de charger le référentiel des banques.'); });
    return () => { active = false; };
  }, []);
  const employee = rows[selected];
  const preparation = employee ? preparedForm(employee.embauche, refs) : null;
  const preparationRequired = preparation ? [
    ['idFiliale', 'Filiale'], ['idTypeContrat', 'Type de contrat'], ['idTypeIntervenant', 'Type d’intervenant'],
    ['dateDebut', 'Date de début'], ['idSite', 'Site'], ['idDepartement', 'Département'],
    ['idService', 'Service'], ['idFonction', 'Fonction'], ['idCategorieProfessionnelle', 'Catégorie'],
    ['idModeCalculPaie', 'Mode de calcul'], ['salaireBase', 'Salaire de base'],
  ].filter(([key]) => !preparation[key]).map(([, label]) => label) : [];
  const selectedContractType = refs.typesContrat.find(item => String(item.idTypeContrat) === preparation?.idTypeContrat);
  if (preparation && selectedContractType?.exigeDateFin && !preparation.dateFin) preparationRequired.push('Date de fin');
  if (preparation && selectedContractType?.code === 'CDD' && !preparation.motifCddCode) preparationRequired.push('Motif du CDD');
  const incompleteContracts = rows.map(row => ({ matricule: row.matricule, form: preparedForm(row.embauche, refs) })).filter(({ form }) =>
    ['idFiliale', 'idTypeContrat', 'idTypeIntervenant', 'dateDebut', 'idSite', 'idDepartement', 'idService', 'idFonction', 'idCategorieProfessionnelle', 'idModeCalculPaie', 'salaireBase'].some(key => !form[key]) ||
    !(Number(String(form.salaireBase).replace(/\s/g, '').replace(',', '.')) > 0) ||
    (form.dateDebut && form.dateDebut > new Date().toISOString().slice(0, 10)) ||
    Boolean(refs.typesContrat.find(item => String(item.idTypeContrat) === form.idTypeContrat)?.exigeDateFin && !form.dateFin) ||
    Boolean(refs.typesContrat.find(item => String(item.idTypeContrat) === form.idTypeContrat)?.code === 'CDD' && !form.motifCddCode));
  const visible = useMemo(() => rows.map((row, index) => ({ row, index })).filter(({ row }) => `${row.matricule} ${row.nom} ${row.prenom}`.toLocaleLowerCase('fr').includes(query.toLocaleLowerCase('fr')) && (!onlyPending || pending(row) > 0)), [rows, query, onlyPending]);
  const pendingCount = rows.reduce((total, row) => total + pending(row), 0);
  const exclusionCount = rows.filter(row => row.exclureIndemniteDeplacement).length;
  const sectionPending = section === 'identite' || section === 'coordonnees'
    ? fields[section].filter(([key]) => (employee?.mockFields || []).includes(key) && mockValues.has(employee?.[key])).length
    : section === 'famille' ? (employee?.contactsUrgence || []).reduce((total, contact) => total + Number(contact.nom === MOCK) + Number(contact.prenom === MOCK), 0) : 0;
  const update = (key, value) => setRows(previous => previous.map((row, index) => index === selected ? { ...row, [key]: value } : row));
  const updateItem = (collection, itemIndex, key, value) => setRows(previous => previous.map((row, rowIndex) => rowIndex === selected ? { ...row, [collection]: row[collection].map((item, index) => index === itemIndex ? { ...item, [key]: value } : item) } : row));
  const selectBank = (index, code) => {
    const bank = banques.find(item => item.codeBanque === code);
    setRows(previous => previous.map((row, rowIndex) => rowIndex === selected ? { ...row, banques: row.banques.map((item, bankIndex) => bankIndex === index ? { ...item, codeBanque: code, nomBanque: bank?.nomBanque || item.nomBanque } : item) } : row));
  };
  const bankOptions = bank => {
    const options = banques.map(item => ({ value: item.codeBanque, label: item.nomBanque, sublabel: item.codeBanque }));
    if (bank.codeBanque && !options.some(item => item.value === bank.codeBanque)) {
      options.unshift({ value: bank.codeBanque, label: bank.nomBanque || bank.codeBanque, sublabel: 'Absent du référentiel actif' });
    }
    return options;
  };
  const updateEmbauche = (key, value) => {
    const updated = pairContractAndIntervenant(preparation, key, value, refs.typesContrat, refs.typesIntervenant);
    if (key === 'idDepartement' && String(value) !== String(preparation.idDepartement)) updated.idService = '';
    if (key === 'idTypeContrat' && String(value) !== String(preparation.idTypeContrat)) {
      updated.dateFin = ''; updated.motifCddCode = '';
    }
    if (['dateDebut', 'dureeEssaiMois', 'aPeriodeEssai'].includes(key)) {
      if (updated.aPeriodeEssai && updated.dateDebut && updated.dureeEssaiMois) {
        const end = new Date(`${updated.dateDebut}T12:00:00`);
        end.setMonth(end.getMonth() + Number(updated.dureeEssaiMois));
        updated.dateFinEssai = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`;
      } else {
        updated.dateFinEssai = '';
      }
    }
    update('embauche', updated);
  };
  const updatePhone = (collection, index, value) => update(collection, (employee[collection] || []).map((phone, phoneIndex) => phoneIndex === index ? value : phone));
  const removeItem = (collection, index) => update(collection, employee[collection].filter((_, itemIndex) => itemIndex !== index));

  async function preview(file) {
    if (!file) return;
    setBusy(true); setError(''); setResult(null); setLeaveEmployee(null); setLeaveCompleted([]);
    try {
      const form = new FormData(); form.append('file', file);
      const response = await apiClient.post('/employes/import/preview', form, { headers: { 'Content-Type': 'multipart/form-data' } });
      setRows(response.data.map(row => ({ ...row, contactsUrgence: row.contactsUrgence.map(contact => ({ ...contact, initialMock: contact.nom === MOCK || contact.prenom === MOCK })) }))); setFileName(file.name); setSelected(0); setSection('identite'); setQuery(''); setOnlyPending(false); setOnlyMockFields(false);
    } catch (e) { setError(e.response?.data?.message || e.message || 'Prévisualisation impossible.'); }
    finally { setBusy(false); }
  }
  async function commit() {
    setBusy(true); setError('');
    try {
      const response = await apiClient.post(importContracts ? '/employes/import/commit-with-contracts' : '/employes/import/commit',
        rows.map(row => {
          const form = preparedForm(row.embauche, refs);
          return { ...row, embauche: { ...form,
            salaireBase: form.salaireBase ? String(form.salaireBase).replace(/\s/g, '').replace(',', '.') : '',
            dureeEssaiMois: form.aPeriodeEssai ? form.dureeEssaiMois : '',
            dateFinEssai: form.aPeriodeEssai ? form.dateFinEssai : '',
          } };
        }));
      setResult({ ...response.data, employees: rows.map(row => ({
        idEmploye: response.data.employeeIds?.[row.matricule], matricule: row.matricule,
        nom: row.nom, prenom: row.prenom,
      })) });
      setRows([]); setFileName(''); setConfirmImportOpen(false); downloadCorrections(response.data.corrections);
    } catch (e) { setError(e.response?.data?.message || e.message || 'Import impossible. Vérifiez les données et les banques.'); setConfirmImportOpen(false); }
    finally { setBusy(false); }
  }

  return <div className="space-y-4">
    <section className="flex flex-wrap items-center justify-between gap-3 border border-[var(--color-border)] bg-[var(--color-surface)] p-3 sm:p-4">
      <div className="min-w-0"><h2 className="text-sm font-bold text-[var(--color-fg)]">{rows.length ? 'Fichier prêt pour la prévisualisation' : 'Classeur des employés'}</h2><p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">{rows.length ? 'Vérifiez et complétez les fiches avant l’import.' : 'Chargez le fichier initial pour préparer les dossiers employés.'}</p></div>
      <label className="flex min-w-0 max-w-full cursor-pointer items-center gap-3 border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 focus-within:ring-2 focus-within:ring-[var(--color-focus-ring)]">
        <FileSpreadsheet size={18} className="shrink-0 text-[var(--color-brand-500)]" />
        <span className="min-w-0 text-xs"><strong className="block max-w-64 truncate text-[var(--color-fg)]">{busy ? 'Analyse du fichier…' : fileName || 'Aucun fichier sélectionné'}</strong><span className="text-[var(--color-fg-muted)]">.xlsx · 5 Mo maximum</span></span>
        <span className="shrink-0 border border-[var(--color-border)] px-2 py-1 text-xs font-semibold text-[var(--color-fg)]">{rows.length ? 'Changer de fichier' : 'Parcourir'}</span>
        <input type="file" accept=".xlsx" disabled={busy} onChange={event => preview(event.target.files?.[0])} className="sr-only" aria-label="Choisir le classeur des employés" />
      </label>
    </section>
    {error && <Alert type="error" message={error} />}
    {result && <Alert type="success" title={`${result.created.length} dossiers créés`} action={<Button type="button" variant="outline" size="xs" onClick={() => downloadCorrections(result.corrections)} iconLeft={<Download size={14} />}>Télécharger le fichier DAF</Button>}>{result.contratsActives ? `${result.contratsActives} embauches actives · ` : ''}{result.corrections.length} corrections à transmettre à la DAF · {result.exclusionsDeplacement?.length || 0} exclusions de déplacement créées.</Alert>}
    {result?.employees?.length > 0 && <section className="border border-[var(--color-border)] bg-[var(--color-surface)]" aria-label="Reprise des congés après import">
      <div className="border-b border-[var(--color-border)] p-4"><h3 className="text-sm font-bold text-[var(--color-fg)]">Étape suivante · Reprise des congés</h3><p className="mt-1 text-xs text-[var(--color-fg-muted)]">Les colonnes de congés du classeur sont vides. La DAF doit fournir un arrêté certifié. Pour chaque salarié, choisissez dans le formulaire existant un solde global ou des lots détaillés. Effectuez la reprise avant les premières écritures de droits.</p><p className="mt-2 text-xs font-semibold text-[var(--color-brand-700)]">{leaveCompleted.length} reprise(s) enregistrée(s) sur {result.employees.length} dans cette session.</p></div>
      <div className="max-h-96 divide-y divide-[var(--color-border-subtle)] overflow-y-auto">{result.employees.map(item => <div key={item.matricule} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-xs"><span><strong>{item.nom} {item.prenom}</strong><span className="ml-2 text-[var(--color-fg-muted)]">Matricule {item.matricule}</span></span><div className="flex items-center gap-2">{leaveCompleted.includes(item.matricule) ? <span className="font-semibold text-[var(--color-success-700)]">Reprise enregistrée</span> : <span className="text-[var(--color-fg-muted)]">À reprendre</span>}{item.idEmploye && !leaveCompleted.includes(item.matricule) && <Button type="button" variant="outline" size="xs" onClick={() => setLeaveEmployee(item)}>Initialiser les congés</Button>}<Link className="font-semibold text-[var(--color-brand-700)] underline" to={`/employes/${item.idEmploye}/conges`}>Ouvrir la fiche</Link></div></div>)}</div>
    </section>}
    {leaveEmployee && <Suspense fallback={null}><InitialisationSoldeModal isOpen={Boolean(leaveEmployee)} employe={leaveEmployee} onClose={() => setLeaveEmployee(null)} onSuccess={() => { setLeaveCompleted(previous => [...previous, leaveEmployee.matricule]); setLeaveEmployee(null); }} /></Suspense>}
    {rows.length > 0 && <>
      <ol aria-label="Progression de l’import" className="grid gap-2 border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3 text-xs font-semibold sm:grid-cols-3 sm:gap-4">
        <li className="flex items-center gap-2 text-[var(--color-success-700)]"><span className="flex size-6 items-center justify-center rounded-full bg-[var(--color-success-600)] text-white"><Check size={13} /></span>Fichier</li>
        <li className="flex items-center gap-2 text-[var(--color-fg)]"><span className="flex size-6 items-center justify-center rounded-full bg-[var(--color-brand-600)] text-white">2</span>Prévisualisation et corrections</li>
        <li className="flex items-center gap-2 text-[var(--color-fg-muted)]"><span className="flex size-6 items-center justify-center rounded-full bg-[var(--color-bg-subtle)]">3</span>Import</li>
      </ol>
      <div className="grid border border-[var(--color-border)] bg-[var(--color-surface)] sm:grid-cols-4">
        <div className="flex items-baseline gap-3 border-b border-[var(--color-border)] px-4 py-3 sm:border-b-0 sm:border-r"><strong className="text-xl">{rows.length}</strong><span className="text-xs text-[var(--color-fg-muted)]">Salariés dans le fichier</span></div>
        <div className="flex items-baseline gap-3 border-b border-[var(--color-border)] px-4 py-3 sm:border-b-0 sm:border-r"><strong className="text-xl text-amber-800">{pendingCount}</strong><span className="text-xs text-[var(--color-fg-muted)]">Valeurs provisoires à compléter par la DAF</span></div>
        <div className="flex items-baseline gap-3 px-4 py-3"><strong className="text-xl">{rows.reduce((total, row) => total + row.enfants.length, 0)}</strong><span className="text-xs text-[var(--color-fg-muted)]">Enfants rattachés</span></div>
        <div className="flex items-baseline gap-3 border-t border-[var(--color-border)] px-4 py-3 sm:border-l sm:border-t-0"><strong className="text-xl text-amber-800">{exclusionCount}</strong><span className="text-xs text-[var(--color-fg-muted)]">Exclusions de déplacement proposées</span></div>
      </div>
      <div className="grid min-w-0 gap-4 lg:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="min-w-0 border border-[var(--color-border)] bg-[var(--color-surface)]"><div className="space-y-2 border-b border-[var(--color-border)] p-3"><Input label="Rechercher un salarié" value={query} onChange={event => setQuery(event.target.value)} placeholder="Matricule ou nom" iconLeft={<Search size={16} />} /><Checkbox label="Fiches à corriger seulement" checked={onlyPending} onChange={event => setOnlyPending(event.target.checked)} /></div><div className="max-h-[650px] overflow-y-auto" aria-label="Salariés prévisualisés">{visible.length === 0 && <p className="p-3 text-sm text-[var(--color-fg-muted)]">Aucun salarié ne correspond au filtre.</p>}{visible.map(({ row, index }) => <button key={`${row.matricule}-${index}`} type="button" onClick={() => { setSelected(index); setSection('identite'); }} aria-current={index === selected ? 'true' : undefined} className={`flex min-h-14 w-full items-center justify-between gap-2 border-b border-[var(--color-border-subtle)] px-3 py-2 text-left text-xs ${index === selected ? 'border-l-[3px] border-l-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' : 'hover:bg-[var(--color-bg-subtle)]'}`}><span className="min-w-0"><strong className="block truncate">{row.nom} {row.prenom}</strong><span className="opacity-75">Matricule {row.matricule}</span></span>{pending(row) > 0 && <span className="shrink-0 bg-amber-100 px-2 py-0.5 font-semibold text-amber-900">{pending(row)}</span>}</button>)}</div></aside>
        {employee && <article className="min-w-0 border border-[var(--color-border)] bg-[var(--color-surface)]">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--color-border)] p-4"><div><p className="text-xs text-[var(--color-fg-muted)]">Dossier {selected + 1} sur {rows.length}</p><h3 className="text-base font-bold">{employee.nom} {employee.prenom}</h3><p className="text-xs text-[var(--color-fg-muted)]">Matricule {employee.matricule}</p></div><div className="flex gap-2"><button type="button" disabled={selected === 0} onClick={() => setSelected(selected - 1)} aria-label="Salarié précédent" className="border border-[var(--color-border)] p-2 disabled:opacity-40"><ChevronLeft size={16} /></button><button type="button" disabled={selected === rows.length - 1} onClick={() => setSelected(selected + 1)} aria-label="Salarié suivant" className="border border-[var(--color-border)] p-2 disabled:opacity-40"><ChevronRight size={16} /></button></div></div>
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--color-border)] bg-[var(--color-brand-500)]/5 px-4 py-2 text-xs"><span className="flex items-center gap-2"><AlertCircle size={15} className="text-amber-700" />{pending(employee) > 0 ? `${pending(employee)} valeur(s) provisoire(s) seront signalées à la DAF, dont ${sectionPending} dans cet onglet.` : 'Aucune valeur provisoire sur cette fiche.'}</span><Checkbox label="Champs initialement provisoires" checked={onlyMockFields} onChange={event => setOnlyMockFields(event.target.checked)} /></div>
          <nav aria-label="Sections du dossier" className="flex overflow-x-auto border-b border-[var(--color-border)] px-2">{sections.map(([id, label]) => <button key={id} type="button" onClick={() => setSection(id)} aria-current={section === id ? 'page' : undefined} className={`inline-flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-xs ${section === id ? 'border-[var(--color-brand-600)] font-semibold text-[var(--color-fg)]' : 'border-transparent text-[var(--color-fg-muted)]'}`}>{label}{id === section && sectionPending > 0 && <span className="bg-amber-100 px-1.5 py-0.5 text-amber-900">{sectionPending}</span>}</button>)}</nav>
          <div className="space-y-5 p-4 sm:p-5">
            {(section === 'identite' || section === 'coordonnees') && <><div className="grid gap-4 sm:grid-cols-2">{fields[section].filter(([key]) => !onlyMockFields || (employee.mockFields || []).includes(key)).map(([key, label]) => key === 'sexe' ? <Select key={key} label={label} value={employee.sexe} onChange={value => update('sexe', value)} options={[{ value: 'M', label: 'Masculin' }, { value: 'F', label: 'Féminin' }]} /> : key === 'lieuNaissance' || key === 'cinLieuDelivrance' ? <AutocompleteInput key={key} label={label} value={employee[key] || ''} onChange={event => update(key, event.target.value)} options={VILLES_MADAGASCAR} hint="Choisir une ville proposée ou saisir le lieu exact." /> : <Field key={key} label={label} value={employee[key]} onChange={value => update(key, value)} type={dateFields.has(key) ? 'date' : 'text'} provisional={(employee.mockFields || []).includes(key) && mockValues.has(employee[key])} />)}</div>{section === 'coordonnees' && !onlyMockFields && <div className="space-y-2 border-t pt-3"><h4 className="text-sm font-semibold">Autres téléphones du salarié</h4>{(employee.telephonesSecondaires || []).map((phone, index) => <div key={index} className="flex items-end gap-2"><Field label={`Numéro ${index + 2}`} value={phone} onChange={value => updatePhone('telephonesSecondaires', index, value)} /><Button type="button" variant="ghost" size="xs" onClick={() => update('telephonesSecondaires', employee.telephonesSecondaires.filter((_, i) => i !== index))}>Retirer</Button></div>)}<Button type="button" variant="ghost" size="xs" onClick={() => update('telephonesSecondaires', [...(employee.telephonesSecondaires || []), ''])}>+ Ajouter un numéro</Button></div>}</>}
            {section === 'famille' && <>
              {!onlyMockFields && <section className="space-y-3"><div className="flex flex-wrap items-baseline justify-between gap-2"><h4 className="font-semibold">Enfants · {employee.enfants.length}</h4>{employee.nombreEnfantsDeclare && <span className="text-xs text-[var(--color-fg-muted)]">Nombre déclaré : {employee.nombreEnfantsDeclare}</span>}</div>{employee.enfants.length === 0 && <p className="text-sm text-[var(--color-fg-muted)]">Aucun enfant dans le fichier.</p>}{employee.enfants.map((child, index) => <div key={index} className="space-y-3 border-t pt-3"><div className="flex justify-between"><strong className="text-sm">Enfant {index + 1}</strong><Button type="button" variant="ghost" size="xs" onClick={() => removeItem('enfants', index)}>Retirer</Button></div><div className="grid gap-3 sm:grid-cols-3"><Field label="Nom" value={child.nom} onChange={value => updateItem('enfants', index, 'nom', value)} /><Field label="Prénoms" value={child.prenom} onChange={value => updateItem('enfants', index, 'prenom', value)} /><Field label="Naissance" type="date" value={child.dateNaissance} onChange={value => updateItem('enfants', index, 'dateNaissance', value)} /></div><Checkbox label="Enfant à charge" checked={child.aCharge !== false && child.aCharge !== 'false'} onChange={event => updateItem('enfants', index, 'aCharge', event.target.checked)} /></div>)}<Button type="button" variant="ghost" size="sm" onClick={() => update('enfants', [...employee.enfants, { nom: '', prenom: '', dateNaissance: '', aCharge: true }])}>+ Ajouter un enfant</Button></section>}
              <section className="space-y-3 border-t pt-4"><h4 className="font-semibold">Contacts d’urgence · {employee.contactsUrgence.length}</h4>{employee.contactsUrgence.length === 0 && <p className="text-sm text-[var(--color-fg-muted)]">Aucun contact dans le fichier.</p>}{employee.contactsUrgence.map((contact, index) => onlyMockFields && !contact.initialMock ? null : <div key={index} className="space-y-3 border-t pt-3"><div className="flex justify-between"><strong className="text-sm">Contact {index + 1}</strong><Button type="button" variant="ghost" size="xs" onClick={() => removeItem('contactsUrgence', index)}>Retirer</Button></div><div className="grid gap-3 sm:grid-cols-3"><Field label="Nom" value={contact.nom} provisional={contact.nom === MOCK} onChange={value => updateItem('contactsUrgence', index, 'nom', value)} /><Field label="Prénoms" value={contact.prenom} provisional={contact.prenom === MOCK} onChange={value => updateItem('contactsUrgence', index, 'prenom', value)} /><Field label="Téléphone" value={contact.telephone} onChange={value => updateItem('contactsUrgence', index, 'telephone', value)} hint={(contact.telephonesSecondaires || []).length ? 'Autres numéros rattachés provisoirement à ce contact ; à confirmer par la DAF.' : undefined} /></div><div className="space-y-2">{(contact.telephonesSecondaires || []).map((phone, phoneIndex) => <div key={phoneIndex} className="flex items-end gap-2"><Field label={`Autre numéro ${phoneIndex + 1}`} value={phone} onChange={value => updateItem('contactsUrgence', index, 'telephonesSecondaires', contact.telephonesSecondaires.map((item, i) => i === phoneIndex ? value : item))} /><Button type="button" variant="ghost" size="xs" onClick={() => updateItem('contactsUrgence', index, 'telephonesSecondaires', contact.telephonesSecondaires.filter((_, i) => i !== phoneIndex))}>Retirer</Button></div>)}<Button type="button" variant="ghost" size="xs" onClick={() => updateItem('contactsUrgence', index, 'telephonesSecondaires', [...(contact.telephonesSecondaires || []), ''])}>+ Ajouter un numéro à ce contact</Button></div></div>)}<Button type="button" variant="ghost" size="sm" onClick={() => update('contactsUrgence', [...employee.contactsUrgence, { nom: '', prenom: '', telephone: '', telephonesSecondaires: [] }])}>+ Ajouter un contact</Button></section>
            </>}
            {section === 'banque' && !onlyMockFields && <section className="space-y-3"><div><h4 className="font-semibold">Compte bancaire principal</h4><p className="text-xs text-[var(--color-fg-muted)]">Un seul compte sera créé pour ce salarié. Les zéros initiaux des codes sont conservés.</p></div>{employee.banques.length === 0 && <p className="border border-dashed p-4 text-sm text-[var(--color-fg-muted)]">Aucun compte dans le fichier. La DAF pourra l’ajouter plus tard.</p>}{employee.banques.map((bank, index) => <div key={index} className={`space-y-3 border p-4 ${employee.comptePrincipalIndex === index ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5' : 'border-[var(--color-border-subtle)]'}`}><Radio name={`bank-${selected}`} checked={employee.comptePrincipalIndex === index} onChange={() => update('comptePrincipalIndex', index)} label={`${employee.comptePrincipalIndex === index ? 'Compte principal' : 'Choisir ce compte'} · ${bank.nomBanque}`} /><div className="grid gap-3 sm:grid-cols-2"><SearchSelect label="Banque" value={bank.codeBanque} onChange={value => selectBank(index, value)} options={bankOptions(bank)} placeholder="Rechercher une banque…" /><Input label="Code banque" value={bank.codeBanque} readOnly hint="Défini par la banque sélectionnée" /><Field label="Code guichet" value={bank.codeGuichet} onChange={value => updateItem('banques', index, 'codeGuichet', value)} /><Field label="Numéro de compte" value={bank.numeroCompte} onChange={value => updateItem('banques', index, 'numeroCompte', value)} /><Field label="Clé RIB" value={bank.cleRib} onChange={value => updateItem('banques', index, 'cleRib', value)} /></div><p className="break-all border-t border-[var(--color-border)] pt-3 text-xs text-[var(--color-fg-muted)]">RIB assemblé : <strong className="font-mono text-[var(--color-fg)]">{bank.codeBanque}{bank.codeGuichet}{bank.numeroCompte}{bank.cleRib}</strong></p></div>)}</section>}
            {section === 'indemnites' && <section className="space-y-4"><div><h4 className="font-semibold">Indemnité de déplacement</h4><p className="mt-1 text-xs text-[var(--color-fg-muted)]">Une exclusion est proposée pour les matricules 1, 35, 55 et 135. Vérifiez-la avant l’import.</p></div><div className="border border-[var(--color-border)] p-4"><Checkbox label="Exclure cet employé de l’indemnité de déplacement" checked={Boolean(employee.exclureIndemniteDeplacement)} onChange={event => update('exclureIndemniteDeplacement', event.target.checked)} hint="Si cochée, la dérogation DPLCT prend effet à la date de l’import, sans date de fin. Elle sera modifiable dans les paramètres de pointage." /></div></section>}
            {section === 'embauche' && <section className="space-y-5">
              <Alert type="info">Les champs reprennent le formulaire normal d’embauche. La date d’effet du workflow sera la date de début du contrat. Avec l’option d’activation, le contrat, l’affectation et le workflow seront validés dans le même import.</Alert>
              {refs.error && <Alert type="error" message="Référentiels RH indisponibles. Les choix du contrat ne peuvent pas être vérifiés." />}
              {!refs.loading && <Alert type={preparationRequired.length ? 'warning' : 'success'} message={preparationRequired.length ? `À compléter avant la création d’un brouillon : ${preparationRequired.join(', ')}.` : 'Champs obligatoires renseignés pour préparer un brouillon. Les valeurs restent à vérifier et ne sont pas encore importées.'} />}
              <ContratFieldsSection formData={preparation} setField={updateEmbauche} signatureOptional filiales={refs.filiales} typesContrat={refs.typesContrat.filter(item => item.code !== 'PRESTATION')} typesIntervenant={refs.typesIntervenant.filter(item => !['CONSULTANT', 'PRESTATAIRE'].includes(item.code))} />
              <PeriodeEssaiFieldsSection formData={preparation} setField={updateEmbauche} />
              <AffectationFieldsSection formData={preparation} setField={updateEmbauche} sites={refs.sites} departements={refs.departements} services={refs.services} fonctions={refs.fonctions} categoriesPro={refs.categoriesPro} modesPaie={refs.modesPaie} responsables={refs.responsables} />
              {(employee.embauche?.serviceSource || employee.embauche?.fonctionSource || employee.embauche?.categorieAConfirmer === 'true') && <p className="border-l-[3px] border-amber-600 bg-amber-50 p-3 text-xs text-amber-900">Valeurs du fichier à confirmer : {employee.embauche?.serviceSource && `service « ${employee.embauche.serviceSource} » ; `}{employee.embauche?.fonctionSource && `fonction « ${employee.embauche.fonctionSource} » ; `}{employee.embauche?.categorieAConfirmer === 'true' && 'catégorie AGENT proposée.'}</p>}
              <p className="text-xs text-[var(--color-fg-muted)]">Le numéro de contrat peut être généré. La date de signature proposée reprend la date d’embauche du fichier et doit être vérifiée. Complétez tous les salariés avant d’activer les embauches.</p>
              <div className="flex flex-wrap gap-3 text-sm font-semibold"><Link className="text-[var(--color-brand-700)] underline" to="/admin/organisation" target="_blank" rel="noreferrer">Gérer les services et fonctions</Link><Link className="text-[var(--color-brand-700)] underline" to="/admin/referentiels-rh" target="_blank" rel="noreferrer">Gérer les référentiels RH</Link></div>
            </section>}


          </div>
        </article>}
      </div>
      <div className="sticky bottom-0 z-10 flex flex-wrap items-center justify-between gap-3 border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3 shadow-sm">
        <div className="text-xs text-[var(--color-fg-muted)]"><strong className="text-[var(--color-fg)]">{rows.length} salariés prêts à importer</strong> · {pendingCount} valeurs provisoires seront signalées à la DAF<Checkbox label="Créer et activer les contrats, affectations et workflows" checked={importContracts} onChange={event => setImportContracts(event.target.checked)} hint={incompleteContracts.length ? `${incompleteContracts.length} embauche(s) incomplète(s) : ${incompleteContracts.slice(0, 5).map(item => item.matricule).join(', ')}${incompleteContracts.length > 5 ? '…' : ''}` : 'Toutes les embauches sont complètes.'} /></div>
        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" size="sm" onClick={() => { setRows([]); setFileName(''); setResult(null); }}>Annuler</Button>
          <Button type="button" variant="secondary" size="sm" disabled={selected === rows.length - 1} onClick={() => { setSelected(selected + 1); setSection('identite'); }} iconRight={<ArrowRight size={15} />}>Fiche suivante</Button>
          <Button type="button" size="sm" loading={busy} disabled={importContracts && (refs.loading || Boolean(refs.error) || incompleteContracts.length > 0)} onClick={() => setConfirmImportOpen(true)}>Importer les {rows.length} salariés{importContracts ? ' et activer les embauches' : ''}</Button>
        </div>
      </div>
    </>}
    <ConfirmDialog
      isOpen={confirmImportOpen}
      onClose={() => setConfirmImportOpen(false)}
      onConfirm={commit}
      title="Confirmer l’import initial des employés"
      message={`${rows.length} dossiers employés seront créés${importContracts ? ' avec leurs contrats, affectations et workflows actifs' : ''}. ${exclusionCount} exclusion(s) de l’indemnité de déplacement prendront effet à la date de l’import.`}
      confirmLabel={`Importer ${rows.length} salariés`}
      variant="primary"
      loading={busy}
    >
      <p className="text-xs text-[var(--color-fg-muted)]">{pendingCount} valeurs personnelles provisoires seront inscrites dans le fichier de corrections de la DAF. Vérifiez les exclusions sélectionnées avant de confirmer.</p>
    </ConfirmDialog>
  </div>;
}
