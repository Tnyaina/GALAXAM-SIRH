import { Users, AlertTriangle, CheckCircle2, Calendar, Clock, AlertCircle, ChevronDown } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDateInput } from '@/utils/dateUtils';

const formatDateFr = (val) => {
  if (!val) return '';
  const d = new Date(val);
  return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
};

export default function ConflitsPostePreview({
  conflitReport,
  checkingConflits,
  dateDebut,
  dateFin,
  estDemiJournee,
  momentDemiJournee,
  derogationContinuiteService,
  setDerogationContinuiteService
}) {
  const hasDates = Boolean(dateDebut && dateFin);

  // Construction de la fenêtre temporelle pour le mini-Gantt (autour de dateDebut et dateFin)
  let timelineDays = [];
  if (hasDates) {
    const dStart = new Date(dateDebut);
    const dEnd = new Date(dateFin);

    // Marge de 3 jours avant et 4 jours après pour donner du contexte visuel
    const winStart = new Date(dStart);
    winStart.setDate(winStart.getDate() - 3);

    const winEnd = new Date(dEnd);
    winEnd.setDate(winEnd.getDate() + 4);

    let cur = new Date(winStart);
    while (cur <= winEnd) {
      const dateStr = fmtDateInput(cur);
      const isWeekend = cur.getDay() === 0 || cur.getDay() === 6;
      const dayNum = cur.getDate();
      const dayLabel = cur.toLocaleDateString('fr-FR', { weekday: 'short' });
      const isInSelectedRange = dateStr >= dateDebut && dateStr <= (estDemiJournee ? dateDebut : dateFin);
      timelineDays.push({
        dateStr,
        dayNum,
        dayLabel,
        isWeekend,
        isInSelectedRange
      });
      cur.setDate(cur.getDate() + 1);
    }
  }

  const totalTimelineCols = timelineDays.length || 1;

  const calculateBarPosition = (startStr, endStr) => {
    if (!timelineDays.length) return { left: 0, width: 0 };
    const winFirst = timelineDays[0].dateStr;
    const winLast = timelineDays[timelineDays.length - 1].dateStr;

    const effStart = startStr < winFirst ? winFirst : startStr;
    const effEnd = endStr > winLast ? winLast : endStr;

    if (effEnd < winFirst || effStart > winLast) return { left: 0, width: 0, visible: false };

    const startIdx = timelineDays.findIndex((d) => d.dateStr === effStart);
    const endIdx = timelineDays.findIndex((d) => d.dateStr === effEnd);

    const safeStartIdx = startIdx === -1 ? 0 : startIdx;
    const safeEndIdx = endIdx === -1 ? timelineDays.length - 1 : endIdx;

    const span = safeEndIdx - safeStartIdx + 1;
    const leftPercent = (safeStartIdx / totalTimelineCols) * 100;
    const widthPercent = (span / totalTimelineCols) * 100;

    return { left: leftPercent, width: widthPercent, visible: true };
  };

  return (
    <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3.5 flex flex-col space-y-3">
      {/* En-tête du panneau */}
      <div className="border-b border-[var(--color-border-subtle)] pb-2 flex items-center justify-between">
        <h4 className="text-xs font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
          <Users className="w-3.5 h-3.5 text-brand-600" />
          Continuité de service
        </h4>
        <span className="text-[11px] text-[var(--color-fg-muted)]">{hasDates ? 'Vérifié' : 'À vérifier'}</span>
      </div>

      {/* Chargement */}
      {checkingConflits && (
        <div className="flex items-center gap-2 p-2.5 bg-brand-500/10 border border-brand-500/20 text-brand-700 dark:text-brand-300 text-xs">
          <Clock className="w-3.5 h-3.5 animate-spin" />
          <span>Vérification du planning d'équipe...</span>
        </div>
      )}

      {/* Si aucune date */}
      {!hasDates && !checkingConflits && (
        <div className="flex flex-col items-center justify-center text-center p-6 text-[var(--color-fg-subtle)] space-y-1 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
          <Calendar className="w-5 h-5 opacity-40 mb-1" />
          <p className="text-xs font-medium text-[var(--color-fg-muted)]">
            Indiquez les dates pour afficher le comparatif d'équipe
          </p>
        </div>
      )}

      {/* Contenu actif */}
      {hasDates && !checkingConflits && (
        <div className="space-y-3 flex-1 flex flex-col">
          <details className="group">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-2 text-xs font-semibold text-[var(--color-brand-700)]">
              <span>Voir le planning du poste</span><ChevronDown size={14} className="transition-transform group-open:rotate-180" />
            </summary>
            <div className="mt-3 space-y-3">
          {conflitReport?.affectationsSurPeriode?.length > 1 && (
            <div className="text-[11px] text-[var(--color-fg-muted)] space-y-0.5">
              <p className="font-semibold">Affectations prises en compte :</p>
              {conflitReport.affectationsSurPeriode.map((segment, index) => (
                <p key={`${segment.debut}-${index}`}>
                  {formatDateFr(segment.debut)} → {formatDateFr(segment.fin)} · {segment.nomFonction || 'Poste inconnu'} · {segment.nomSite || 'Site inconnu'}
                </p>
              ))}
            </div>
          )}
          {/* Frise chronologique / Mini-Gantt comparatif fluide */}
          <div className="border border-[var(--color-border)] bg-[var(--color-surface)] shadow-2xs w-full overflow-hidden">
            <div className="w-full">
              {/* En-tête des jours */}
              <div className="flex bg-[var(--color-bg-subtle)] border-b border-[var(--color-border)] text-center select-none">
                <div className="w-28 shrink-0 p-1.5 text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] border-r border-[var(--color-border)] flex items-center">
                  Ressource
                </div>
                <div className="flex-1 flex min-w-0">
                  {timelineDays.map((td, i) => (
                    <div
                      key={i}
                      className={`flex-1 min-w-0 py-1 border-r border-[var(--color-border-subtle)] last:border-0 text-[10px] font-mono ${
                        td.isInSelectedRange
                          ? 'bg-brand-500/15 font-bold text-brand-800 dark:text-brand-200 border-b-2 border-b-brand-600'
                          : td.isWeekend
                            ? 'bg-[var(--color-bg-subtle)]/80 text-[var(--color-fg-subtle)]'
                            : 'text-[var(--color-fg)]'
                      }`}
                      title={td.dateStr}
                    >
                      <div className="text-[9px] uppercase opacity-75">{td.dayLabel.charAt(0)}</div>
                      <div className="text-[10px]">{td.dayNum}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Ligne 1 : Nouvelle demande (Demandeur) */}
              <div className="flex items-center border-b border-[var(--color-border-subtle)] relative h-9 hover:bg-[var(--color-surface-hover)]">
                <div className="w-28 shrink-0 px-2 text-[11px] font-bold text-[var(--color-fg)] truncate border-r border-[var(--color-border)] flex items-center justify-between">
                  <span className="truncate">Demandeur</span>
                  <span className="text-[9px] font-mono bg-brand-500/20 text-brand-800 dark:text-brand-300 px-1 py-0.2">
                    Nouveau
                  </span>
                </div>
                <div className="flex-1 relative h-full flex items-center">
                  {/* Grille de fond */}
                  <div className="absolute inset-0 flex pointer-events-none">
                    {timelineDays.map((td, idx) => (
                      <div
                        key={idx}
                        className={`flex-1 border-r border-[var(--color-border-subtle)]/30 last:border-0 ${
                          td.isWeekend ? 'bg-[var(--color-bg-subtle)]/30' : ''
                        }`}
                      />
                    ))}
                  </div>

                  {/* Barre de la demande en cours */}
                  {(() => {
                    const pos = calculateBarPosition(dateDebut, estDemiJournee ? dateDebut : dateFin);
                    if (!pos.visible) return null;
                    return (
                      <div
                        className="absolute h-6 bg-[var(--color-brand-600)] text-[var(--color-on-color)] text-[10px] font-bold px-1.5 flex items-center truncate shadow-2xs z-10 border border-[var(--color-brand-700)]"
                        style={{ left: `${pos.left}%`, width: `${Math.max(pos.width, 3)}%` }}
                        title={`Période demandée : ${formatDateFr(dateDebut)} au ${formatDateFr(dateFin)}`}
                      >
                        <span className="truncate">
                          Sélection ({conflitReport?.nbJoursDecomptes ?? 1}j{estDemiJournee && momentDemiJournee ? ` - ${momentDemiJournee}` : ''})
                        </span>
                      </div>
                    );
                  })()}
                </div>
              </div>

              {/* Lignes 2..N : Collègues du même poste */}
              {conflitReport?.colleguesAbsents && conflitReport.colleguesAbsents.length > 0 ? (
                conflitReport.colleguesAbsents.map((col, cIdx) => (
                  <div
                    key={cIdx}
                    className="flex items-center border-b border-[var(--color-border-subtle)] last:border-0 relative h-9 hover:bg-[var(--color-surface-hover)]"
                  >
                    <div className="w-28 shrink-0 px-2 text-[11px] text-[var(--color-fg)] truncate border-r border-[var(--color-border)] flex items-center gap-1.5">
                      <span className="w-4 h-4 rounded-none bg-[var(--color-warning-500)]/20 text-[var(--color-warning-600)] text-[9px] font-bold flex items-center justify-center shrink-0">
                        {col.prenom?.[0] || 'C'}
                      </span>
                      <span className="truncate font-medium">{col.prenom} {col.nom}</span>
                    </div>

                    <div className="flex-1 relative h-full flex items-center">
                      <div className="absolute inset-0 flex pointer-events-none">
                        {timelineDays.map((td, idx) => (
                          <div
                            key={idx}
                            className={`flex-1 border-r border-[var(--color-border-subtle)]/30 last:border-0 ${
                              td.isWeekend ? 'bg-[var(--color-bg-subtle)]/30' : ''
                            }`}
                          />
                        ))}
                      </div>

                      {/* Barre du congé collègue */}
                      {(() => {
                        const sStr = fmtDateInput(col.debutConflit || col.dateDebut);
                        const eStr = fmtDateInput(col.finConflit || col.dateFin);
                        const pos = calculateBarPosition(sStr, eStr);
                        if (!pos.visible) return null;
                        return (
                          <div
                            className="absolute h-6 bg-[var(--color-warning-500)] text-[var(--color-on-color)] text-[10px] font-semibold px-1.5 flex items-center truncate shadow-2xs z-10 border border-[var(--color-warning-600)]"
                            style={{ left: `${pos.left}%`, width: `${Math.max(pos.width, 3)}%` }}
                            title={`${col.prenom} ${col.nom} : conflit au ${col.nomFonction || 'poste'}${col.nomSite ? ` · ${col.nomSite}` : ''} du ${formatDateFr(col.debutConflit || col.dateDebut)} au ${formatDateFr(col.finConflit || col.dateFin)}`}
                          >
                            <span className="truncate">
                              {col.typeConge || 'Congé'}
                            </span>
                          </div>
                        );
                      })()}
                    </div>
                  </div>
                ))
              ) : (
                <div className="p-3 text-center text-xs text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)]/30">
                  Aucun autre collaborateur au même poste n'est planifié absent sur cette fenêtre.
                </div>
              )}
            </div>
          </div>
            </div>
          </details>

          {/* Alertes et Quorum */}
          {conflitReport?.collisionPersonnelle && (
            <div className="p-3 bg-danger-500/10 border border-danger-500/30 text-danger-800 dark:text-danger-300 space-y-1.5">
              <div className="flex items-center gap-2 font-bold text-xs">
                <AlertCircle className="w-4 h-4 text-danger-600 shrink-0" />
                <span>Chevauchement avec un autre congé personnel</span>
              </div>
              <p className="text-[11px] leading-relaxed">
                Ce collaborateur a déjà une demande enregistrée sur ces dates :
              </p>
              {conflitReport.collisionsPersonnelles?.map((c, i) => (
                <div key={i} className="text-[11px] font-medium flex items-center justify-between bg-[var(--color-surface)] px-2 py-1 border border-danger-500/20">
                  <span>{c.typeConge}</span>
                  <span className="font-mono">{formatDateFr(c.dateDebut)} → {formatDateFr(c.dateFin)}</span>
                </div>
              ))}
            </div>
          )}

          {conflitReport?.conflitPoste ? (
            <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-[var(--color-fg)] space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 font-semibold text-xs text-[var(--color-warning-600)]">
                  <AlertTriangle className="w-4 h-4 shrink-0" />
                  <span>Absence simultanée au même poste</span>
                </div>
                {conflitReport.nomFonction && conflitReport.affectationsSurPeriode?.length <= 1 && (
                  <Badge variant="warning" size="xs">
                    {conflitReport.nomFonction}
                  </Badge>
                )}
              </div>

              <p className="text-[11px] leading-normal text-[var(--color-fg-muted)]">
                {conflitReport.colleguesAbsents?.length || 1} collègue(s) absent(s) aux mêmes dates, au même poste et sur le même site.
              </p>

              <label className="flex items-start gap-2 pt-1 border-t border-[var(--color-warning-500)]/30 cursor-pointer">
                <input
                  type="checkbox"
                  checked={derogationContinuiteService}
                  onChange={(e) => setDerogationContinuiteService(e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[var(--color-brand-600)] cursor-pointer shrink-0"
                />
                <span className="text-xs font-medium text-[var(--color-fg)]">
                  Dérogation de continuité de service
                  <span className="block text-[10.5px] font-normal text-[var(--color-fg-muted)]">
                    Confirme la validation de l'absence simultanée.
                  </span>
                </span>
              </label>
            </div>
          ) : (
            <div className="p-2.5 bg-[var(--color-success-500)]/10 border border-[var(--color-success-500)]/30 text-[var(--color-fg)] space-y-0.5">
              <div className="flex items-center gap-1.5 font-semibold text-xs text-[var(--color-success-600)]">
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                <span>Continuité de service assurée</span>
              </div>
              <p className="text-[11px] text-[var(--color-fg-muted)]">
                Aucun autre collaborateur au même poste n'est absent sur ces dates.
              </p>
            </div>
          )}

        </div>
      )}
    </div>
  );
}
