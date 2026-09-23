/**
 * Compatibilité de navigation de la feature Congés.
 *
 * Les règles métier (durée, préavis, solde et quota) sont calculées par
 * POST /conges/demandes/simuler. Ce fichier ne contient plus que des helpers
 * de présentation sans décision métier.
 */
export { formatDateForInput, isDateAnterieure } from '@/utils/dateUtils';
