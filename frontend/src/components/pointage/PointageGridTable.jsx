import { Link } from 'react-router-dom';
import { Moon } from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { getFullPhotoUrl } from '@/utils/fileUtils';
import TimeInput from '@/components/ui/forms/TimeInput';
import Badge from '@/components/ui/data-display/Badge';
import Pagination from '@/components/ui/navigation/Pagination';

export default function PointageGridTable({
    grilleData = [],
    _siteParams,
    siteIndemnites = [],
    onUpdateHours,
    onUpdateIndemnite,
    onUpdateDynamicIndemnite,
    onRegisterInputRef,
    onKeyDown,
    onFocusNext,
    currentPage = 1,
    pageSize = 25,
    onPageChange,
    onPageSizeChange,
    isReadOnly = false
}) {
    const startIndex = (currentPage - 1) * pageSize;
    const paginatedData = grilleData.slice(startIndex, startIndex + pageSize);

    const fmtMoney = (val) => {
        if (!val || isNaN(val)) return '0 Ar';
        return new Intl.NumberFormat('fr-FR').format(Math.round(val)) + ' Ar';
    };

    const getStatutPointageBadge = (statutCode, libelle) => {
        switch (statutCode) {
            case 'PRESENT':
                return <Badge variant="success" size="sm" className="font-bold">Présent</Badge>;
            case 'DISPENSE_POINTAGE':
                return <Badge variant="brand" size="sm" className="font-bold">Forfait 8h</Badge>;
            case 'CONGE':
                return <Badge variant="warning" size="sm" className="font-bold">{libelle || 'Congé'}</Badge>;
            case 'CONGE_DEMI_JOURNEE':
                return <Badge variant="warning" size="sm" className="font-bold">Demi-journée</Badge>;
            case 'FERIE_CHOME':
                return <Badge variant="neutral" size="sm" className="font-bold">{libelle || 'Férié chômé'}</Badge>;
            case 'FERIE_TRAVAILLE':
                return <Badge variant="error" size="sm" className="font-bold">Férié (+100%)</Badge>;
            case 'RETARD':
                return <Badge variant="error" size="sm" className="font-bold">Retard</Badge>;
            case 'ANOMALIE_SAISIE':
                return <Badge variant="error" size="sm" className="font-bold">Incomplet</Badge>;
            case 'ABSENCE_NON_JUSTIFIEE':
                return <Badge variant="error" size="sm" className="font-bold">Absent</Badge>;
            default:
                return <Badge variant="neutral" size="sm">Non saisi</Badge>;
        }
    };

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
            {/* Légende Métier */}
            <div className="px-4 py-2 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] flex flex-wrap items-center gap-4 text-xs font-semibold text-[var(--color-fg-muted)]">
                <span className="text-[var(--color-fg)] font-bold">Légende :</span>
                <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-amber-500 inline-block"></span> Congé</span>
                <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-stone-400 inline-block"></span> Férié chômé</span>
                <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-indigo-500 inline-block"></span> Cadre autonome (8h pré-rempli)</span>
                <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-neutral-200 dark:bg-neutral-700 inline-block"></span> Non saisi</span>
            </div>

            <div className="overflow-x-auto">
                <Table className="w-full text-left border-collapse">
                    <Thead>
                        <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            <Th className="py-3 px-4 min-w-[220px]">Employé</Th>
                            <Th className="py-3 px-2 text-center min-w-[80px]">Entrée M.</Th>
                            <Th className="py-3 px-2 text-center min-w-[80px]">Sortie M.</Th>
                            <Th className="py-3 px-2 text-center min-w-[80px]">Retour P.</Th>
                            <Th className="py-3 px-2 text-center min-w-[80px]">Sortie S.</Th>
                            <Th className="py-3 px-3 text-center min-w-[85px]">Total</Th>
                            <Th className="py-3 px-3 text-center min-w-[85px]">Retard</Th>
                            <Th className="py-3 px-3 text-center min-w-[110px]">Statut</Th>
                            {/* Colonnes Dynamiques d'Indemnités du Site (100% Backend-Driven) */}
                            {siteIndemnites.map((ind) => (
                                <Th key={ind.idIndemnite} className="py-3 px-3 text-center min-w-[110px]" title={ind.libelle}>
                                    <div className="flex flex-col items-center">
                                        <span>{ind.libelle || ind.code}</span>
                                        <span className="text-[9px] font-normal text-[var(--color-fg-muted)] lowercase">
                                            {ind.sousTitre || (ind.estProratise ? `prorata (${fmtMoney(ind.montantJour)}/j)` : `${fmtMoney(ind.montantJour)}/j`)}
                                        </span>
                                    </div>
                                </Th>
                            ))}
                        </Tr>
                    </Thead>
                    <Tbody>
                        {paginatedData.map((row) => {
                            const isCongeFull = row.typeCongeJour === 'FULL_DAY' || row.codeStatut === 'CONGE';
                            const isCongeMatin = row.typeCongeJour === 'MATIN';
                            const isCongeAm = row.typeCongeJour === 'APRES_MIDI';
                            const isFerieChome = row.codeStatut === 'FERIE_CHOME' || (row.ferieJour && !row.totalHeures);
                            const isReadOnlyForUser = isReadOnly;

                            let rowBgClass = '';
                            if (isCongeFull) rowBgClass = 'bg-amber-500/5 hover:bg-amber-500/10';
                            else if (isFerieChome) rowBgClass = 'bg-stone-500/5 hover:bg-stone-500/10';
                            else if (row.estAutonome) rowBgClass = 'bg-indigo-500/5 hover:bg-indigo-500/10';

                            return (
                                <Tr key={row.idEmploye} className={`border-b border-[var(--color-border-subtle)] transition-colors ${rowBgClass}`}>
                                    {/* 1. Identification Salarié */}
                                    <Td className="py-2.5 px-4">
                                        <div className="flex items-center gap-3">
                                            <Avatar
                                                src={row.photoUrl ? getFullPhotoUrl(row.photoUrl) : null}
                                                name={`${row.prenom || ''} ${row.nom || ''}`}
                                                size="sm"
                                                shape="square"
                                            />
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <Link
                                                        to={`/employes/${row.idEmploye}`}
                                                        className="text-xs font-bold text-[var(--color-fg)] hover:text-[var(--color-brand-600)] hover:underline"
                                                    >
                                                        {row.nom} {row.prenom}
                                                    </Link>
                                                    {row.estAutonome && (
                                                        <span className="text-[9px] font-bold px-1.5 py-0.5 bg-indigo-500/15 text-indigo-700 dark:text-indigo-300">
                                                            AUTONOME
                                                        </span>
                                                    )}
                                                </div>
                                                <p className="text-[11px] font-mono text-[var(--color-fg-muted)]">
                                                    {row.matricule}
                                                </p>
                                            </div>
                                        </div>
                                    </Td>

                                    {/* 2. Entrée Matin */}
                                    <Td className="text-center py-2.5 px-2">
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 0, el)}
                                            value={row.heureEntreeMatin}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeMatin}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureEntreeMatin', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 0)}
                                            onComplete={() => onFocusNext(row.idEmploye, 0)}
                                            className={row.hasValidationError ? '!border-red-500 !bg-red-50' : ''}
                                        />
                                    </Td>

                                    {/* 3. Sortie Matin */}
                                    <Td className="text-center py-2.5 px-2">
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 1, el)}
                                            value={row.heureSortieMatin}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeMatin}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureSortieMatin', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 1)}
                                            onComplete={() => onFocusNext(row.idEmploye, 1)}
                                            className={row.hasValidationError ? '!border-red-500 !bg-red-50' : ''}
                                        />
                                    </Td>

                                    {/* 4. Retour Pause */}
                                    <Td className="text-center py-2.5 px-2">
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 2, el)}
                                            value={row.heureEntreeAm}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeAm}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureEntreeAm', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 2)}
                                            onComplete={() => onFocusNext(row.idEmploye, 2)}
                                            className={row.hasValidationError ? '!border-red-500 !bg-red-50' : ''}
                                        />
                                    </Td>

                                    {/* 5. Sortie Soir */}
                                    <Td className="text-center py-2.5 px-2">
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 3, el)}
                                            value={row.heureSortieAm}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeAm}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureSortieAm', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 3)}
                                            onComplete={() => onFocusNext(row.idEmploye, 3)}
                                            className={row.hasValidationError ? '!border-red-500 !bg-red-50' : ''}
                                        />
                                    </Td>

                                    {/* 6. Total Heures */}
                                    <Td className="text-center font-mono font-bold text-xs py-2.5 px-3">
                                        <div className="flex flex-col items-center justify-center">
                                            <span className={row.totalHeures > 0 ? 'text-[var(--color-fg)]' : 'text-[var(--color-fg-muted)]'}>
                                                {row.totalHeures ? `${Math.floor(row.totalHeures)}h${Math.round((row.totalHeures % 1) * 60).toString().padStart(2, '0')}` : '00h00'}
                                            </span>
                                            {/* Détection heures de nuit : uniquement si sortie > 22:00 et pas en congé/férié */}
                                            {row.heureSortieAm && row.heureSortieAm !== '—' && !isCongeFull && !isFerieChome && row.heureSortieAm > '22:00' && (
                                                <span className="inline-flex items-center gap-0.5 text-[9px] font-bold text-indigo-700 dark:text-indigo-300 bg-indigo-500/10 px-1">
                                                    <Moon size={9} /> nuit
                                                </span>
                                            )}
                                        </div>
                                    </Td>

                                    {/* 7. Retard */}
                                    <Td className="text-center font-mono font-bold text-xs py-2.5 px-3">
                                        {row.minutesRetard > 0 ? (
                                            <span className="text-amber-600 dark:text-amber-400 bg-amber-500/10 px-1.5 py-0.5">
                                                +{row.minutesRetard} min
                                            </span>
                                        ) : (
                                            <span className="text-emerald-700 dark:text-emerald-400">
                                                00 min
                                            </span>
                                        )}
                                    </Td>

                                    {/* 8. Statut Badge */}
                                    <Td className="text-center py-2.5 px-3">
                                        {getStatutPointageBadge(row.codeStatut, row.libelleStatut)}
                                    </Td>

                                    {/* Colonnes Dynamiques d'Indemnités (Fractionnées M/S ou Journalières selon configuration) */}
                                    {siteIndemnites.map((ind) => {
                                        const isFerieNonTravaille = row.ferieJour && !row.totalHeures;
                                        const dynObj = row.dynamicIndemnites?.[String(ind.idIndemnite)] || {};
                                        const isAccordee = dynObj.accordee !== false && !isCongeFull && !isFerieNonTravaille;
                                        const montantJour = ind.montantJour || ind.montantReference || 0;

                                        const isFractionnable = ind.estFractionnable !== undefined ? ind.estFractionnable : ind.fractionnable;

                                        if (isFractionnable) {
                                            // Indemnité fractionnable par demi-journée (ex: Déplacement/Transport - Trajet matin & Trajet soir)
                                            const montantTrajet = ind.montantTrajet || Math.round(montantJour / 2.0);
                                            const matVal = dynObj.matin !== undefined ? dynObj.matin : (row.deplacementMatin !== false && isAccordee);
                                            const amVal = dynObj.am !== undefined ? dynObj.am : (row.deplacementAm !== false && isAccordee);

                                            return (
                                                <Td key={ind.idIndemnite} className="text-center py-2.5 px-3">
                                                    <div className="flex items-center justify-center gap-2">
                                                        <label className={`inline-flex items-center gap-0.5 text-[10px] font-bold ${isCongeFull || isFerieNonTravaille || isReadOnlyForUser ? 'cursor-not-allowed' : 'cursor-pointer'} select-none`} title={`Trajet matin aller — ${fmtMoney(montantTrajet)}`}>
                                                            <input
                                                                type="checkbox"
                                                                checked={matVal}
                                                                disabled={isReadOnlyForUser || isCongeFull || isFerieNonTravaille}
                                                                onChange={(e) => {
                                                                    onUpdateIndemnite(row.idEmploye, 'deplacementMatin', e.target.checked);
                                                                    onUpdateDynamicIndemnite?.(row.idEmploye, ind.idIndemnite, e.target.checked, 'MATIN');
                                                                }}
                                                                className="w-3.5 h-3.5 cursor-pointer accent-[var(--color-brand-500)]"
                                                            />
                                                            <span className={matVal ? 'text-[var(--color-fg)]' : 'text-[var(--color-fg-muted)]'}>M</span>
                                                        </label>

                                                        <label className={`inline-flex items-center gap-0.5 text-[10px] font-bold ${isCongeFull || isFerieNonTravaille || isReadOnlyForUser ? 'cursor-not-allowed' : 'cursor-pointer'} select-none`} title={`Trajet soir retour — ${fmtMoney(montantTrajet)}`}>
                                                            <input
                                                                type="checkbox"
                                                                checked={amVal}
                                                                disabled={isReadOnlyForUser || isCongeFull || isFerieNonTravaille}
                                                                onChange={(e) => {
                                                                    onUpdateIndemnite(row.idEmploye, 'deplacementAm', e.target.checked);
                                                                    onUpdateDynamicIndemnite?.(row.idEmploye, ind.idIndemnite, e.target.checked, 'APRES_MIDI');
                                                                }}
                                                                className="w-3.5 h-3.5 cursor-pointer accent-[var(--color-brand-500)]"
                                                            />
                                                            <span className={amVal ? 'text-[var(--color-fg)]' : 'text-[var(--color-fg-muted)]'}>S</span>
                                                        </label>
                                                    </div>
                                                </Td>
                                            );
                                        }

                                        // Indemnité journalière standard (1 case à cocher pour la journée entière)
                                        const isRepas = ind.code === 'REPAS';
                                        const checked = isRepas ? !!row.indemniteRepasRecue : isAccordee;

                                        return (
                                            <Td key={ind.idIndemnite} className="text-center py-2.5 px-3">
                                                <div className="flex flex-col items-center justify-center gap-1">
                                                    <label className={`inline-flex items-center gap-1.5 text-[11px] font-medium ${isCongeFull || isFerieNonTravaille || isReadOnlyForUser ? 'cursor-not-allowed' : 'cursor-pointer'} select-none`} title={`Accordée — ${fmtMoney(montantJour)}`}>
                                                        <input
                                                            type="checkbox"
                                                            checked={checked}
                                                            disabled={isReadOnlyForUser || isCongeFull || isFerieNonTravaille}
                                                            onChange={(e) => {
                                                                if (isRepas) {
                                                                    onUpdateIndemnite(row.idEmploye, 'indemniteRepasRecue', e.target.checked);
                                                                }
                                                                onUpdateDynamicIndemnite?.(row.idEmploye, ind.idIndemnite, e.target.checked);
                                                            }}
                                                            className="w-3.5 h-3.5 cursor-pointer accent-[var(--color-brand-500)]"
                                                        />
                                                        <span className={checked ? 'text-emerald-700 dark:text-emerald-400 font-bold' : 'text-[var(--color-fg-muted)]'}>
                                                            {checked && montantJour > 0 ? fmtMoney(montantJour) : '0 Ar'}
                                                        </span>
                                                    </label>

                                                    {/* Si repas : afficher badge éventuel de soirée */}
                                                    {isRepas && (() => {
                                                        if (isCongeFull || isFerieChome || !row.heureSortieAm || row.heureSortieAm === '—') return null;
                                                        if (row.heureSortieAm >= '21:00') {
                                                            return (
                                                                <span className="inline-flex items-center text-[9px] font-bold text-amber-700 dark:text-amber-400 bg-amber-500/15 border border-amber-500/30 px-1 py-0.5" title="Sortie après 21h00 : 2ème repas majoré (+4 000 Ar)">
                                                                    +4 000 Ar (Soirée)
                                                                </span>
                                                            );
                                                        }
                                                        if (row.heureSortieAm >= '19:30') {
                                                            return (
                                                                <span className="inline-flex items-center text-[9px] font-bold text-amber-600 dark:text-amber-300 bg-amber-500/10 border border-amber-500/20 px-1 py-0.5" title="Sortie entre 19h30 et 21h00 : 2ème repas (+2 000 Ar)">
                                                                    +2 000 Ar (Soirée)
                                                                </span>
                                                            );
                                                        }
                                                        return null;
                                                    })()}
                                                </div>
                                            </Td>
                                        );
                                    })}
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>

                {/* Pagination */}
                <Pagination
                    currentPage={currentPage}
                    totalItems={grilleData.length}
                    pageSize={pageSize}
                    onPageChange={onPageChange}
                    onPageSizeChange={onPageSizeChange}
                />
            </div>
        </div>
    );
}
