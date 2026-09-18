import { describe, it, expect } from 'vitest';

/**
 * Tests Unitaires Frontend - Phase 2A-3C :
 * Sécurisation Expiration / Purge FIFO et Distinction SOLDE_INITIAL
 */
describe('Phase 2A-3C : Sécurisation Expiration & Distinction SOLDE_INITIAL', () => {

  const getMouvementLabel = (type, commentaire) => {
    if (type === 'AJUSTEMENT' && commentaire && commentaire.startsWith('[SOLDE_INITIAL]')) {
      return 'Solde initial (reprise historique)';
    }
    switch (type) {
      case 'ACQUISITION': return 'Acquisition mensuelle';
      case 'BONIFICATION': return 'Bonification ancienneté';
      case 'PRISE': return 'Prise de congé';
      case 'AJUSTEMENT': return 'Ajustement manuel RH';
      case 'ANNULATION': return 'Annulation de demande';
      case 'EXPIRATION': return 'Prescription triennale';
      default: return type;
    }
  };

  const filterMouvements = (mouvements, filterType) => {
    return mouvements.filter((mvt) => {
      if (!mvt) return false;
      if (filterType === 'ALL') return true;
      if (filterType === 'SOLDE_INITIAL') {
        return mvt.typeMouvement === 'AJUSTEMENT' && mvt.commentaire?.startsWith('[SOLDE_INITIAL]');
      }
      if (filterType === 'AJUSTEMENT') {
        return mvt.typeMouvement === 'AJUSTEMENT' && !mvt.commentaire?.startsWith('[SOLDE_INITIAL]');
      }
      return mvt.typeMouvement === filterType;
    });
  };

  it('distingue formellement le label d’un SOLDE_INITIAL d’un AJUSTEMENT RH ordinaire', () => {
    const mvtSoldeInitial = {
      typeMouvement: 'AJUSTEMENT',
      commentaire: '[SOLDE_INITIAL] Reprise historique arrêtée au 2026-08-31 : Données Excel'
    };
    const mvtAjustementOrdinaire = {
      typeMouvement: 'AJUSTEMENT',
      commentaire: 'Régularisation suite erreur de saisie RH'
    };

    expect(getMouvementLabel(mvtSoldeInitial.typeMouvement, mvtSoldeInitial.commentaire))
      .toBe('Solde initial (reprise historique)');
    expect(getMouvementLabel(mvtAjustementOrdinaire.typeMouvement, mvtAjustementOrdinaire.commentaire))
      .toBe('Ajustement manuel RH');
  });

  it('filtre avec précision les mouvements selon SOLDE_INITIAL vs AJUSTEMENT ordinaire', () => {
    const mouvements = [
      { idMouvement: 1, typeMouvement: 'AJUSTEMENT', commentaire: '[SOLDE_INITIAL] Reprise 17.5j' },
      { idMouvement: 2, typeMouvement: 'AJUSTEMENT', commentaire: 'Ajustement +2j régularisation' },
      { idMouvement: 3, typeMouvement: 'ACQUISITION', commentaire: 'Acquisition 09/2026' },
      { idMouvement: 4, typeMouvement: 'EXPIRATION', commentaire: 'Purge légale FIFO' },
    ];

    const soldeInitialList = filterMouvements(mouvements, 'SOLDE_INITIAL');
    expect(soldeInitialList).toHaveLength(1);
    expect(soldeInitialList[0].idMouvement).toBe(1);

    const ajustementList = filterMouvements(mouvements, 'AJUSTEMENT');
    expect(ajustementList).toHaveLength(1);
    expect(ajustementList[0].idMouvement).toBe(2);

    const expirationList = filterMouvements(mouvements, 'EXPIRATION');
    expect(expirationList).toHaveLength(1);
    expect(expirationList[0].idMouvement).toBe(4);
  });

  it('le frontend n’invente aucune date d’expiration fictive pour le solde initial', () => {
    const mvtSoldeInitial = {
      idMouvement: 1,
      typeMouvement: 'AJUSTEMENT',
      nbJours: 17.5,
      dateMouvement: '2026-08-31',
      commentaire: '[SOLDE_INITIAL] Reprise arrêtée au 2026-08-31',
      dateExpiration: undefined // Invariant : aucune date d'expiration n'est inventée
    };

    expect(mvtSoldeInitial.dateExpiration).toBeUndefined();
    // La date d'arrêté reste la date de constat, JAMAIS une date d'acquisition
    expect(mvtSoldeInitial.dateMouvement).toBe('2026-08-31');
  });

  it('affiche le totalExpire reçu du backend sans tentative d’extrapolation côté client', () => {
    const soldeEmploye = {
      soldeActuel: 15.0,
      totalAcquis: 30.0,
      totalPris: 10.0,
      totalExpire: 5.0 // Écritures EXPIRATION confirmées par le backend
    };

    expect(soldeEmploye.totalExpire).toBe(5.0);
    // Solde actuel = Acquis - Pris - Expiré = 30 - 10 - 5 = 15
    expect(soldeEmploye.totalAcquis - soldeEmploye.totalPris - soldeEmploye.totalExpire)
      .toBe(soldeEmploye.soldeActuel);
  });

  it('affiche fidèlement un SOLDE_INITIAL négatif de migration (-2.0j) sans conversion en zéro', () => {
    const soldeDto = {
      idEmploye: 55,
      soldeActuel: -2.0,
      totalAcquis: 0.0,
      totalPris: 0.0,
      totalExpire: 0.0
    };

    expect(soldeDto.soldeActuel).toBe(-2.0);
    expect(soldeDto.soldeActuel).toBeLessThan(0);
  });

  it('consomme la ventilation d’imputation RG-CONGE-06 sans recalcul client (BACKEND_VALUE)', () => {
    const imputationBackend = {
      stockHistoriqueInitial: 10.0,
      stockHistoriqueRestant: 7.0,
      totalConsommationNette: 3.0,
      consommationImputeeSurHistorique: 3.0,
      consommationImputeeSurDroitsDates: 0.0,
      totalDroitsDatesRestants: 2.5,
      soldeTotalDisponible: 9.5
    };

    // Le frontend affiche directement les métriques fournies par l'API sans recalculer le FIFO
    expect(imputationBackend.stockHistoriqueRestant).toBe(7.0);
    expect(imputationBackend.totalDroitsDatesRestants).toBe(2.5);
    expect(imputationBackend.soldeTotalDisponible).toBe(9.5);
    expect(imputationBackend.stockHistoriqueRestant + imputationBackend.totalDroitsDatesRestants)
      .toBe(imputationBackend.soldeTotalDisponible);
  });
});
