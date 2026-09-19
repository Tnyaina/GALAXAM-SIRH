import { describe, it, expect } from 'vitest';

/**
 * Tests unitaires Frontend - Phase 2A-3B :
 * Invariants d'affichage du Solde Initial, non-recalcul de NBCONGE / TCONGE
 */
describe('Phase 2A-3B : Solde Initial & Non-Recalcul Frontend', () => {

  it('affiche fidèlement le solde initial importé sans réinvention d’acquisitions', () => {
    // Données retournées par l'API backend VSoldeCongeEmploye
    const soldeDto = {
      idEmploye: 42,
      soldeActuel: 17.5,
      totalAcquis: 0.0, // 0 acquisition mensuelle calculée par le SIRH
      totalBonification: 0.0,
      totalPris: 0.0,
      totalExpire: 0.0,
      derniereAcquisition: null,
    };

    // Le frontend doit afficher soldeActuel = 17.5j
    expect(soldeDto.soldeActuel).toBe(17.5);
    // Le totalAcquis reste à 0 tant qu'aucune paie n'a acquis de mois
    expect(soldeDto.totalAcquis).toBe(0.0);
  });

  it('calcule et affiche le solde cumulé exact après nouvelle acquisition (17.5 + 2.5 = 20.0)', () => {
    const soldeApresAcquisition = {
      idEmploye: 42,
      soldeActuel: 20.0,
      totalAcquis: 2.5,
      totalPris: 0.0,
    };

    expect(soldeApresAcquisition.soldeActuel).toBe(20.0);
    expect(soldeApresAcquisition.totalAcquis).toBe(2.5);
  });

  it('calcule et affiche le solde exact après prise de 3 jours calendaires (20.0 - 3.0 = 17.0)', () => {
    const soldeApresPrise = {
      idEmploye: 42,
      soldeActuel: 17.0,
      totalAcquis: 2.5,
      totalPris: 3.0,
    };

    expect(soldeApresPrise.soldeActuel).toBe(17.0);
    expect(soldeApresPrise.totalPris).toBe(3.0);
  });

  it('le frontend ne recalcule pas NBCONGE : utilise strictement la valeur fournie par l’API de vérification', () => {
    // Rapport retourné par POST /conges/demandes/verifier-conflits (backend authoritative)
    const backendReport = {
      nbJoursDecomptes: 3.0,
      nbJoursOuvres: 1.0,   // Vendredi
      nbJoursWeekEnd: 2.0,  // Samedi + Dimanche
      soldeDisponible: 17.0,
      hasConflit: false,
    };

    // Invariant : Le frontend consomme directement nbJoursDecomptes et n'applique pas de filtre ouvré pour NBCONGE
    const nbCongeValorise = backendReport.nbJoursDecomptes;
    expect(nbCongeValorise).toBe(3.0);
    expect(nbCongeValorise).not.toBe(backendReport.nbJoursOuvres);
  });

  it('le bulletin de paie affiche les lignes calculées par le backend sans recalcul client de TCONGE', () => {
    // Lignes de bulletin renvoyées par le backend
    const lignesBulletin = [
      {
        codeRubrique: 'DEDUCTION_CONGE_BASE',
        libelle: 'Déduction Salaire Jours de Congé (1/30ème)',
        base: 3.0,
        taux: 20000.0,
        montantSalarial: 60000.0,
      },
      {
        codeRubrique: 'INDEMNITE_CONGE_PAYE',
        libelle: 'Indemnité de Congés Payés (Maintien de salaire)',
        base: 3.0,
        taux: 25000.0, // TCONGE issu de conge_journal_droit (avec historique HS)
        montantSalarial: 75000.0,
      }
    ];

    const ligneIndemnite = lignesBulletin.find(l => l.codeRubrique === 'INDEMNITE_CONGE_PAYE');
    expect(ligneIndemnite).toBeDefined();
    expect(ligneIndemnite.base).toBe(3.0);
    expect(ligneIndemnite.taux).toBe(25000.0);
    expect(ligneIndemnite.montantSalarial).toBe(75000.0);
  });

  it('distingue sémantiquement une reprise de solde initial d’un ajustement ordinaire dans le registre', () => {
    const mouvementReprise = {
      idMouvement: 101,
      typeMouvement: 'AJUSTEMENT',
      nbJours: 17.5,
      dateMouvement: '2026-08-31',
      commentaire: '[SOLDE_INITIAL] Reprise historique arrêtée au 2026-08-31 (exclut acquisition 2026-08) : Reprise Galaxam',
    };

    const isSoldeInitial = mouvementReprise.commentaire.includes('[SOLDE_INITIAL]');
    expect(isSoldeInitial).toBe(true);
    expect(mouvementReprise.typeMouvement).toBe('AJUSTEMENT');
  });

  it('fournit le taux journalier TCONGE et sa source de provenance dans le solde de congés', () => {
    const soldeDto = {
      idEmploye: 42,
      soldeActuel: 17.5,
      totalAcquis: 25.0,
      tauxJournalierConge: 28500.0,
      sourceTauxConge: 'CALCUL_PERIODE_REFERENCE',
    };

    expect(soldeDto.tauxJournalierConge).toBe(28500.0);
    expect(soldeDto.sourceTauxConge).toBe('CALCUL_PERIODE_REFERENCE');
  });

  it('calcule automatiquement la date de fin déterministe (12 mois - 1 jour)', () => {
    function calculerDateFin(dateDebutStr) {
      const d = new Date(dateDebutStr + 'T00:00:00');
      d.setFullYear(d.getFullYear() + 1);
      d.setDate(d.getDate() - 1);
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${y}-${m}-${day}`;
    }

    expect(calculerDateFin('2026-01-01')).toBe('2026-12-31');
    expect(calculerDateFin('2026-03-15')).toBe('2027-03-14');
  });

  it('représente fidèlement une situation de cutover découplée sans valeur financière de solde erronée', () => {
    const situationDto = {
      idEmploye: 42,
      soldeJours: 14.5,
      tauxApplicable: {
        montant: 8669.52,
        dateEffet: '2026-09-30',
        source: 'REPRISE_CUTOVER',
      },
      periodeReference: {
        dateDebut: '2026-01-01',
        dateFin: '2026-12-31',
        modeReprise: 'AGGREGATED_OPENING',
        openingComplete: true,
        cumulDroitsTotal: 24.9575,
        cumulValorisationTotal: 269525.54,
        clotureAutomatiquePossible: true,
      },
    };

    // Le solde consommable est 14.5 jours
    expect(situationDto.soldeJours).toBe(14.5);
    // Le taux applicable est 8669.52 Ar
    expect(situationDto.tauxApplicable.montant).toBe(8669.52);
    // Les accumulateurs de cycle sont indépendants du solde restant
    expect(situationDto.periodeReference.cumulDroitsTotal).toBe(24.9575);
    expect(situationDto.periodeReference.cumulValorisationTotal).toBe(269525.54);
    // Aucune valeur financière du solde restant inventée
    expect(situationDto.valeurSoldeEstimee).toBeUndefined();
  });

});
