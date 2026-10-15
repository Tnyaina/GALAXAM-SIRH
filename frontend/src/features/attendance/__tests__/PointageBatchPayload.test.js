import { expect, it } from 'vitest';
import { toPointageBatchPayload } from '../utils/pointageBatchPayload';

it('envoie le même contrat de pointage pour la grille et l’import, avec les versions de conflit', () => {
    expect(toPointageBatchPayload({
        idPointage: 42, version: 3, idEmploye: 8, idSite: 2, datePointage: '2026-08-06',
        heureEntreeMatin: '08:30', heureSortieMatin: '12:30', heureEntreeAm: '13:30', heureSortieAm: '17:00',
        codeStatut: 'PRESENT', dynamicIndemnites: { 5: { accordee: true } }
    })).toEqual({
        idPointage: 42, version: 3, idEmploye: 8, idSite: 2, datePointage: '2026-08-06',
        heureEntreeMatin: '08:30', heureSortieMatin: '12:30', heureEntreeAm: '13:30', heureSortieAm: '17:00',
        horaireContinu: false, horaireContinuJour: false, codeStatut: 'PRESENT', motifAjustement: null,
        estAutonome: false, estCadre: false, typeCongeJour: null, ferieJour: false,
        congePaye: true, congeDecompteSolde: true, idPeriodePaie: null,
        dynamicIndemnites: { 5: { accordee: true } }
    });
});
