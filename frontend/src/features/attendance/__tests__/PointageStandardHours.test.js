import { describe, expect, it } from 'vitest';
import { getConfiguredPause, getConfiguredStandardHours } from '../utils/pointageStandardHours';

describe('horaire proposé par le serveur', () => {
    it('refuse de fabriquer des heures quand la configuration du site est absente ou incomplète', () => {
        expect(getConfiguredStandardHours(null)).toBeNull();
        expect(getConfiguredStandardHours({ heureDebutMatin: '09:00' })).toBeNull();
        expect(getConfiguredPause({ heureFinMatin: '12:30' })).toBeNull();
    });

    it('reprend les quatre heures configurées, y compris un horaire non standard', () => {
        const config = {
            heureDebutMatin: '07:15', heureFinMatin: '11:45',
            heureDebutAm: '12:45', heureFinAm: '16:15'
        };
        expect(getConfiguredStandardHours(config)).toEqual({
            heureEntreeMatin: '07:15', heureSortieMatin: '11:45',
            heureEntreeAm: '12:45', heureSortieAm: '16:15'
        });
        expect(getConfiguredPause(config)).toEqual({
            heureSortieMatin: '11:45', heureEntreeAm: '12:45'
        });
    });

    it('propose les deux bornes du site en journée continue', () => {
        expect(getConfiguredStandardHours({
            horaireContinu: true, heureDebutMatin: '06:30', heureFinAm: '14:30'
        })).toEqual({
            heureEntreeMatin: '06:30', heureSortieMatin: '',
            heureEntreeAm: '', heureSortieAm: '14:30',
            horaireContinu: true, horaireContinuJour: true
        });
        expect(getConfiguredPause({
            horaireContinu: true, heureFinMatin: '11:00', heureDebutAm: '12:00'
        })).toBeNull();
    });
});
