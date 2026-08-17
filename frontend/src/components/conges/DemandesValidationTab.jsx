import { useState, useMemo, lazy, Suspense } from 'react';
import { Search, Plus, Check, X, Eye, XSquare, FileText, Clock, CheckCircle, XCircle, Ban } from 'lucide-react';
import congeService from '@/api/congeService';
import { useDebounce } from '@/hooks/useDebounce';
import { Card, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { Textarea } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmpyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';

const DemandeCongeModal = lazy(() => import('@/components/conges/DemandeCongeModal'));

// ─── Utilitaires ─────────────────────────────────────────────────────────────

const formatDate = (ts) => {
  if (!ts) return '-';
  return new Date(ts).toLocaleDateString('fr-FR');
};

const BADGE_VARIANT = {
  VALIDE:     'success',
  REFUSE:     'danger',
  EN_ATTENTE: 'warning',
  ANNULE:     'neutral',
  BROUILLON:  'default',
};

const STATUT_LABEL = {
  VALIDE:     'Valide',
  REFUSE:     'Refuse',
  EN_ATTENTE: 'En attente',
  ANNULE:     'Annule',
  BROUILLON:  'Brouillon',
};

const STATUT_ICON = {
  EN_ATTENTE: Clock,
  VALIDE:     CheckCircle,
  REFUSE:     XCircle,
  ANNULE:     Ban,
};

// ─── Composant ───────────────────────────────────────────────────────────────

export default function DemandesValidationTab({ loading, demandes = [], referentiels, onReload }) {
  const toast = useToast();

  // ── Filtres ────────────────────────────────────────────────────────────────
  const [activeStatut, setActiveStatut] = useState('EN_ATTENTE');
  const [searchRaw,    setSearchRaw]    = useState('');

  // Debounce de 350 ms (runtime.md §4 — évite les race conditions)
  const searchTerm = useDebounce(searchRaw, 350);

  // ── Modals ─────────────────────────────────────────────────────────────────
  const [showDemandeModal, setShowDemandeModal] = useState(false);
  const [selectedDemande,  setSelectedDemande]  = useState(null);
  const [showDetailModal,  setShowDetailModal]  = useState(false);
  const [showValiderModal, setShowValiderModal] = useState(false);
  const [showRefusModal,   setShowRefusModal]   = useState(false);
  const [motifRefus,       setMotifRefus]       = useState('');
  const [actionLoading,    setActionLoading]    = useState(false);
  const [actionError,      setActionError]      = useState(null);

  // ── Dérivés filtrés (memoized) ─────────────────────────────────────────────
  const counts = useMemo(() =>
    demandes.reduce((acc, d) => {
      acc[d.codeStatutDemande] = (acc[d.codeStatutDemande] || 0) + 1;
      return acc;
    }, {}),
    [demandes]
  );

  const filtered = useMemo(() => {
    const q = searchTerm.toLowerCase();
    return demandes.filter((d) => {
      const name = `${d.prenom || ''} ${d.nom || ''}`.trim().toLowerCase();
      const type = (d.codeTypeConge || '').toLowerCase();
      const matchSearch = !q || name.includes(q) || type.includes(q);
      const matchStatut = activeStatut === 'ALL' || d.codeStatutDemande === activeStatut;
      return matchSearch && matchStatut;
    });
  }, [demandes, searchTerm, activeStatut]);

  const nbEnAttente = counts['EN_ATTENTE'] || 0;

  // ── Handlers ───────────────────────────────────────────────────────────────
  const openValider = (d)  => { setSelectedDemande(d); setActionError(null); setShowValiderModal(true); };
  const openRefus   = (d)  => { setSelectedDemande(d); setActionError(null); setMotifRefus(''); setShowRefusModal(true); };
  const openDetail  = (d)  => { setSelectedDemande(d); setShowDetailModal(true); };
  const closeDetail = ()   => { setShowDetailModal(false); setSelectedDemande(null); };

  const handleValiderConfirm = async () => {
    if (!selectedDemande) return;
    try {
      setActionLoading(true);
      setActionError(null);
      await congeService.validerDemande(selectedDemande.idDemandeConge);
      toast({ title: 'Demande validee', message: `La demande #${selectedDemande.idDemandeConge} de ${selectedDemande.prenom} ${selectedDemande.nom} a ete validee.`, type: 'success', duration: 3000 });
      setShowValiderModal(false);
      setSelectedDemande(null);
      if (onReload) onReload();
    } catch (err) {
      setActionError(err?.response?.data || err.message || 'Erreur lors de la validation.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRefusConfirm = async () => {
    if (!motifRefus.trim()) { setActionError('Le motif de refus est obligatoire.'); return; }
    try {
      setActionLoading(true);
      setActionError(null);
      await congeService.refuserDemande(selectedDemande.idDemandeConge, motifRefus.trim());
      toast({ title: 'Demande refusee', message: `La demande #${selectedDemande.idDemandeConge} a ete refusee.`, type: 'success', duration: 3000 });
      setShowRefusModal(false);
      setSelectedDemande(null);
      setMotifRefus('');
      if (onReload) onReload();
    } catch (err) {
      setActionError(err?.response?.data || err.message || 'Erreur lors du refus.');
    } finally {
      setActionLoading(false);
    }
  };

  // ── Skeleton ───────────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="space-y-4 ">
        <div className="h-14 bg-[var(--color-bg-subtle)] " />
        <SkeletonTable rows={8} />
      </div>
    );
  }

  // ── Définition des onglets-filtres ─────────────────────────────────────────
  const STATUTS = [
    { key: 'ALL',        label: 'Toutes',     icon: FileText   },
    { key: 'EN_ATTENTE', label: 'En attente', icon: Clock      },
    { key: 'VALIDE',     label: 'Validees',   icon: CheckCircle },
    { key: 'REFUSE',     label: 'Refusees',   icon: XCircle    },
    { key: 'ANNULE',     label: 'Annulees',   icon: Ban        },
  ];

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-4 ">

      {/* ── Stepper workflow ─────────────────────────────────────────────── */}
      {activeStatut === 'EN_ATTENTE' && nbEnAttente > 0 && (
        <div className="flex items-center gap-3 bg-[var(--color-brand-500)]/5 border border-[var(--color-brand-500)]/20 p-4">
          {[
            { step: 1, label: 'Soumission',        active: false },
            { step: 2, label: 'Validation RH / DAF', active: true  },
            { step: 3, label: 'Traitement',         active: false },
          ].map((s, i) => (
            <div key={s.step} className="flex items-center gap-2 flex-1">
              {i > 0 && <div className={`h-px flex-1 ${s.active ? 'bg-[var(--color-brand-500)]' : 'bg-[var(--color-border-subtle)]'}`} />}
              <div className={`flex items-center gap-2 whitespace-nowrap ${s.active ? 'text-[var(--color-brand-500)]' : 'text-[var(--color-fg-subtle)]'}`}>
                <div className={`w-7 h-7 rounded-none flex items-center justify-center font-data text-xs font-bold shrink-0 ${
                  s.active
                    ? 'bg-[var(--color-brand-500)] text-white shadow-sm'
                    : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]'
                }`}>{s.step}</div>
                <span className="text-xs font-semibold uppercase tracking-wide">{s.label}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Barre filtres + actions ─────────────────────────────────────── */}
      <Card>
        <CardBody className="p-3">
          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">

            {/* Filtres-pilules par statut */}
            <div className="flex flex-wrap items-center gap-1.5 flex-1 min-w-0">
              {STATUTS.map((s) => {
                const count    = s.key === 'ALL' ? demandes.length : (counts[s.key] || 0);
                const isActive = activeStatut === s.key;
                const isUrgent = s.key === 'EN_ATTENTE' && count > 0;
                const Icon     = s.icon;
                return (
                  <button
                    key={s.key}
                    onClick={() => setActiveStatut(s.key)}
                    className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-semibold border  focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-400)] ${
                      isActive
                        ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)] shadow-sm'
                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:border-[var(--color-brand-400)] hover:text-[var(--color-brand-500)]'
                    }`}
                  >
                    <Icon size={12} />
                    {s.label}
                    <span className={`inline-flex items-center justify-center min-w-[18px] h-[18px] rounded-none text-[10px] font-bold px-1 ${
                      isActive    ? 'bg-white/25 text-white'
                      : isUrgent  ? 'bg-warning-500 text-white '
                                  : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'
                    }`}>
                      {count}
                    </span>
                  </button>
                );
              })}
            </div>

            {/* Recherche + bouton nouvelle demande */}
            <div className="flex items-center gap-2 w-full sm:w-auto shrink-0">
              <div className="relative w-full sm:w-52">
                <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none" />
                <Input
                  placeholder="Rechercher employe..."
                  value={searchRaw}
                  onChange={(e) => setSearchRaw(e.target.value)}
                  className="pl-9 h-8 text-xs"
                />
                {/* Indicateur visuel de debounce en cours */}
                {searchRaw !== searchTerm && (
                  <span className="absolute right-2.5 top-1/2 -translate-y-1/2 w-1.5 h-1.5 bg-[var(--color-brand-400)] rounded-none " title="Recherche en cours..." />
                )}
              </div>
              <Button variant="primary" size="sm" iconLeft={<Plus size={15} />} onClick={() => setShowDemandeModal(true)}>
                Nouvelle demande
              </Button>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* ── Tableau des demandes ───────────────────────────────────────────── */}
      <Card>
        <CardBody className="p-0">
          {/* En-tête contextuel */}
          {activeStatut === 'EN_ATTENTE' && nbEnAttente > 0 && (
            <div className="px-4 py-2.5 border-b border-[var(--color-border-subtle)] bg-warning-500/5 flex items-center gap-2">
              <Clock size={13} className="text-warning-500 shrink-0" />
              <span className="text-xs font-semibold text-warning-500">
                {nbEnAttente} demande{nbEnAttente > 1 ? 's' : ''} en attente de traitement
              </span>
            </div>
          )}

          {filtered.length === 0 ? (
            <EmptyState
              title={searchTerm
                ? `Aucun resultat pour "${searchTerm}"`
                : activeStatut === 'EN_ATTENTE'
                  ? 'Aucune demande en attente'
                  : 'Aucune demande trouvee'}
              description={
                searchTerm
                  ? 'Essayez un autre terme de recherche.'
                  : activeStatut === 'EN_ATTENTE'
                    ? 'Toutes les demandes ont ete traitees.'
                    : 'Aucune demande ne correspond a cette selection.'
              }
            />
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <Thead>
                  <Tr>
                    <Th>Ref.</Th>
                    <Th>Employe</Th>
                    <Th>Type</Th>
                    <Th>Periode</Th>
                    <Th className="text-center">Duree</Th>
                    <Th>Statut</Th>
                    <Th className="text-right">Actions</Th>
                  </Tr>
                </Thead>
                <Tbody>
                  {filtered.map((d) => {
                    const employeName = `${d.prenom || ''} ${d.nom || ''}`.trim() || `Employe ${d.idEmploye}`;
                    const isEnAttente = d.codeStatutDemande === 'EN_ATTENTE';
                    const StatutIcon  = STATUT_ICON[d.codeStatutDemande];
                    return (
                      <Tr
                        key={d.idDemandeConge}
                        className={`hover:bg-[var(--color-surface-hover)]  group ${
                          isEnAttente ? 'border-l-2 border-l-warning-500' : 'border-l-2 border-l-transparent'
                        }`}
                      >
                        <Td className="font-data text-xs text-[var(--color-fg-subtle)] whitespace-nowrap">
                          #{d.idDemandeConge}
                        </Td>
                        <Td>
                          <div className="font-semibold text-[var(--color-fg)] text-sm leading-tight">{employeName}</div>
                          {d.matricule && (
                            <div className="text-[11px] text-[var(--color-fg-subtle)] font-data">{d.matricule}</div>
                          )}
                        </Td>
                        <Td>
                          <Badge variant="brand" size="sm">{d.codeTypeConge || 'ANNUEL'}</Badge>
                          {d.libelleMotifEvenement && (
                            <div className="text-[11px] text-warning-500 mt-0.5 font-medium">{d.libelleMotifEvenement}</div>
                          )}
                        </Td>
                        <Td className="font-data text-xs text-[var(--color-fg-muted)] whitespace-nowrap">
                          <div className="leading-relaxed">
                            <span className="text-[var(--color-fg-subtle)]">Du</span> {formatDate(d.dateDebut)}
                          </div>
                          <div>
                            <span className="text-[var(--color-fg-subtle)]">Au</span> {formatDate(d.dateFin)}
                          </div>
                        </Td>
                        <Td className="text-center font-data whitespace-nowrap">
                          <span className="font-bold text-[var(--color-fg)] text-sm">{d.nbJoursDecomptes}</span>
                          <span className="text-xs text-[var(--color-fg-muted)]"> j</span>
                        </Td>
                        <Td>
                          <div className="inline-flex items-center gap-1.5">
                            {StatutIcon && <StatutIcon size={12} className={`shrink-0 ${
                              d.codeStatutDemande === 'VALIDE'     ? 'text-success-500' :
                              d.codeStatutDemande === 'REFUSE'     ? 'text-danger-500'  :
                              d.codeStatutDemande === 'EN_ATTENTE' ? 'text-warning-500' :
                              'text-[var(--color-fg-subtle)]'
                            }`} />}
                            <Badge variant={BADGE_VARIANT[d.codeStatutDemande] || 'default'} size="sm">
                              {STATUT_LABEL[d.codeStatutDemande] || d.codeStatutDemande}
                            </Badge>
                          </div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            {d.derogationSoldeNegatif   && <Badge variant="danger"  size="sm">Solde negatif</Badge>}
                            {!d.justificatifFourni && d.codeTypeConge === 'MALADIE' && <Badge variant="warning" size="sm">Justificatif manquant</Badge>}
                          </div>
                        </Td>
                        <Td>
                          <div className="flex items-center justify-end gap-1">
                            {isEnAttente && (
                              <>
                                <Button variant="danger"  size="xs" iconLeft={<X size={12}/>}     onClick={() => openRefus(d)}>Refuser</Button>
                                <Button variant="success" size="xs" iconLeft={<Check size={12}/>} onClick={() => openValider(d)}>Valider</Button>
                              </>
                            )}
                            <Button
                              variant="ghost"
                              size="xs"
                              iconLeft={<Eye size={13}/>}
                              onClick={() => openDetail(d)}
                              title="Voir le detail"
                              className="opacity-0 group-hover:opacity-100 "
                            />
                          </div>
                        </Td>
                      </Tr>
                    );
                  })}
                </Tbody>
              </Table>

              {/* Pied de tableau avec compteur de résultats */}
              <div className="px-4 py-2 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-between">
                <span className="text-[11px] text-[var(--color-fg-subtle)]">
                  {filtered.length} demande{filtered.length > 1 ? 's' : ''}
                  {activeStatut !== 'ALL' && ` · filtre : ${STATUTS.find(s => s.key === activeStatut)?.label}`}
                  {searchTerm && ` · recherche : "${searchTerm}"`}
                </span>
                {(activeStatut !== 'EN_ATTENTE' || !searchTerm) && demandes.length > 0 && (
                  <span className="text-[11px] text-[var(--color-fg-subtle)]">{demandes.length} au total</span>
                )}
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      {/* ── Modal : Detail demande ─────────────────────────────────────────── */}
      {showDetailModal && selectedDemande && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] max-w-lg w-full shadow-2xl overflow-hidden font-sans">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
              <div className="flex items-center gap-2">
                <FileText size={14} className="text-[var(--color-brand-500)]" />
                <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                  Detail de la demande #{selectedDemande.idDemandeConge}
                </h4>
              </div>
              <button onClick={closeDetail} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] font-bold text-lg leading-none ">&times;</button>
            </div>

            {/* Corps */}
            <div className="p-6 space-y-5 overflow-y-auto max-h-[65vh]">
              {/* Identite + statut */}
              <div className="flex items-start justify-between pb-4 border-b border-[var(--color-border-subtle)]">
                <div>
                  <h3 className="text-base font-bold text-[var(--color-fg)]">
                    {`${selectedDemande.prenom || ''} ${selectedDemande.nom || ''}`.trim() || `Employe ${selectedDemande.idEmploye}`}
                  </h3>
                  {selectedDemande.matricule && <p className="text-xs text-[var(--color-fg-subtle)] font-data">{selectedDemande.matricule}</p>}
                  <p className="text-xs text-[var(--color-fg-muted)] mt-0.5 font-medium">{selectedDemande.libelleTypeConge || selectedDemande.codeTypeConge}</p>
                </div>
                <Badge variant={BADGE_VARIANT[selectedDemande.codeStatutDemande] || 'default'} dot>
                  {STATUT_LABEL[selectedDemande.codeStatutDemande] || selectedDemande.codeStatutDemande}
                </Badge>
              </div>

              {/* Grille dates */}
              <div className="grid grid-cols-2 gap-x-6 gap-y-4">
                {[
                  ['Date de debut',      formatDate(selectedDemande.dateDebut)],
                  ['Date de fin',        formatDate(selectedDemande.dateFin)],
                  ['Decompte calendaire strict', `${selectedDemande.nbJoursDecomptes} jours`],
                  ['Date de soumission', formatDate(selectedDemande.dateDemande)],
                ].map(([label, value]) => (
                  <div key={label}>
                    <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">{label}</span>
                    <p className="font-data text-sm font-semibold text-[var(--color-fg)] mt-0.5">{value}</p>
                  </div>
                ))}
              </div>

              {/* Motif familial */}
              {selectedDemande.libelleMotifEvenement && (
                <div className="p-3 bg-warning-500/8 border border-warning-500/20 space-y-1">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-warning-500">Motif familial</span>
                  <p className="text-sm font-semibold text-[var(--color-fg)]">{selectedDemande.libelleMotifEvenement}</p>
                </div>
              )}

              {/* Commentaire */}
              {selectedDemande.commentaire && (
                <div className="space-y-1.5">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Commentaire</span>
                  <p className="text-sm bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 text-[var(--color-fg)] leading-relaxed">
                    {selectedDemande.commentaire}
                  </p>
                </div>
              )}

              {/* Motif de refus */}
              {selectedDemande.motifRefus && (
                <div className="space-y-1.5">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-danger-500">Motif du refus</span>
                  <p className="text-sm bg-danger-500/8 border border-danger-500/25 p-3 text-[var(--color-fg)] leading-relaxed">
                    {selectedDemande.motifRefus}
                  </p>
                </div>
              )}

              {/* Derogations */}
              {(selectedDemande.derogationSoldeNegatif || selectedDemande.derogationDelaiPreavis || selectedDemande.extensionPathologiqueMaternite || selectedDemande.justificatifFourni) && (
                <div className="space-y-1.5">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Mentions</span>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedDemande.derogationSoldeNegatif        && <Badge variant="danger"  size="sm">Derogation solde negatif</Badge>}
                    {selectedDemande.derogationDelaiPreavis        && <Badge variant="warning" size="sm">Derogation preavis</Badge>}
                    {selectedDemande.extensionPathologiqueMaternite && <Badge variant="danger"  size="sm">Extension pathologique (119j)</Badge>}
                    {selectedDemande.justificatifFourni             && <Badge variant="success" size="sm">Justificatif fourni</Badge>}
                  </div>
                </div>
              )}
            </div>

            {/* Footer avec actions contextuelles */}
            <div className="flex items-center justify-between p-4 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
              {selectedDemande.codeStatutDemande === 'EN_ATTENTE' ? (
                <div className="flex items-center gap-2">
                  <Button variant="danger"  size="sm" iconLeft={<X size={14}/>}
                    onClick={() => { closeDetail(); openRefus(selectedDemande); }}>Refuser</Button>
                  <Button variant="success" size="sm" iconLeft={<Check size={14}/>}
                    onClick={() => { closeDetail(); openValider(selectedDemande); }}>Valider</Button>
                </div>
              ) : <span />}
              <Button variant="outline" size="sm" onClick={closeDetail}>Fermer</Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Confirmation validation ────────────────────────────────────────── */}
      <ConfirmDialog
        isOpen={showValiderModal}
        title="Confirmer la validation"
        message={`Valider la demande de ${selectedDemande?.prenom || ''} ${selectedDemande?.nom || ''} (${selectedDemande?.nbJoursDecomptes} jours de ${selectedDemande?.codeTypeConge}) ?`}
        confirmLabel="Valider la demande"
        confirmVariant="success"
        onConfirm={handleValiderConfirm}
        onClose={() => { setShowValiderModal(false); setSelectedDemande(null); }}
        loading={actionLoading}
      />

      {/* ── Modal refus avec motif ─────────────────────────────────────────── */}
      <Modal
        isOpen={showRefusModal}
        onClose={() => { setShowRefusModal(false); setSelectedDemande(null); setMotifRefus(''); }}
        title={`Refuser la demande de ${selectedDemande?.prenom || ''} ${selectedDemande?.nom || ''}`}
        icon={<XSquare size={20} />}
        size="md"
        footer={
          <>
            <Button variant="ghost"
              onClick={() => { setShowRefusModal(false); setSelectedDemande(null); setMotifRefus(''); }}>
              Annuler
            </Button>
            <Button variant="danger" onClick={handleRefusConfirm} loading={actionLoading}>
              Confirmer le refus
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {actionError && <Alert type="error" message={typeof actionError === 'string' ? actionError : JSON.stringify(actionError)} />}
          <Alert type="warning" message="Le refus d'une demande de conge doit etre justifie." />
          <div className="space-y-2">
            <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
              Motif du refus *
            </label>
            <Textarea
              value={motifRefus}
              onChange={(e) => setMotifRefus(e.target.value)}
              placeholder="Indiquez la raison du refus (ex: necessite de service, effectif insuffisant...)"
              rows={4}
              disabled={actionLoading}
            />
          </div>
        </div>
      </Modal>

      {/* ── Modale nouvelle demande ────────────────────────────────────────── */}
      <Suspense fallback={null}>
        <DemandeCongeModal
          isOpen={showDemandeModal}
          onClose={() => setShowDemandeModal(false)}
          idEmploye={null}
          referentiels={referentiels}
          onSuccess={() => { setShowDemandeModal(false); if (onReload) onReload(); }}
        />
      </Suspense>
    </div>
  );
}
