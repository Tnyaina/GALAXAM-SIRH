import { describe, it, expect } from 'vitest';
import { cleanUserFacingMessage, getErrorMessage } from '../api/apiClient';

describe('Error Message Extraction & Normalization', () => {
    it('prioritizes human-readable backend message over raw HTTP error phrases', () => {
        const backendError = {
            response: {
                status: 409,
                data: {
                    status: 409,
                    error: 'Conflict',
                    message: "Action impossible : La période de paie est en statut 'VALIDEE'. Vous ne pouvez pas modifier ou valider les semaines d'une période clôturée ou validée.",
                    path: '/api/pointage-synthese/recalculer-tout'
                }
            }
        };

        const result = getErrorMessage(backendError);
        expect(result).toBe("Action impossible : La période de paie est en statut 'VALIDEE'. Vous ne pouvez pas modifier ou valider les semaines d'une période clôturée ou validée.");
        expect(result).not.toBe('Conflict');
    });

    it('replaces raw HTTP status phrase "Conflict" with fallback or friendly description when message is missing', () => {
        const rawConflictError = {
            response: {
                status: 409,
                data: {
                    status: 409,
                    error: 'Conflict'
                }
            }
        };

        const resultWithFallback = getErrorMessage(rawConflictError, 'Erreur lors du recalcul.');
        expect(resultWithFallback).toBe('Erreur lors du recalcul.');
        expect(resultWithFallback).not.toBe('Conflict');

        const cleaned = cleanUserFacingMessage('Conflict', 'Erreur survenue.');
        expect(cleaned).toBe('Erreur survenue.');
    });

    it('extracts direct error string if backend returns plain text', () => {
        const textError = {
            response: {
                status: 400,
                data: 'Le format de date est invalide.'
            }
        };

        expect(getErrorMessage(textError)).toBe('Le format de date est invalide.');
    });

    it('uses userMessage when already normalized on error object', () => {
        const err = {
            userMessage: 'Message convivial déjà normalisé.',
            message: 'Conflict'
        };

        expect(getErrorMessage(err)).toBe('Message convivial déjà normalisé.');
    });

    it('translates known business codes and never exposes a JDBC diagnostic', () => {
        expect(getErrorMessage({ response: { data: { code: 'MISSING_WORKING_CALENDAR' } } }))
            .toBe("Le calendrier de travail n'est pas configuré pour cette période.");
        expect(getErrorMessage({ message: 'SQLException: duplicate key constraint at org.postgresql.Driver' }, 'Opération impossible.'))
            .toBe('Opération impossible.');
    });
});
