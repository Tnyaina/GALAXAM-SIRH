-- ============================================================
-- SPRINT 5 : NETTOYAGE DES VUES ET FONCTIONS SQL OBSOLÈTES
-- Date : 2026-09-04
-- Contexte : Migration de la logique métier du SQL vers Java
-- Les calculs d'heures supplémentaires, de proratisation de seuils
-- hebdomadaires, de majorations et de jours ouvrables ont été migrés
-- vers les moteurs Java purs :
--   - PointageCalculateurEngine
--   - PointageSyntheseService
--   - CalendrierOuvrableEngine
--
-- Ces objets de base de données ne sont plus consommés par le backend.
-- Ce script supprime définitivement l'ambiguïté architecturale.
-- ============================================================

-- 1. Suppression des vues de calculs de pointage remplacées par Java
DROP VIEW IF EXISTS v_hs_mensuelle CASCADE;
DROP VIEW IF EXISTS v_synthese_pointage_mensuelle CASCADE;
DROP VIEW IF EXISTS v_pointage_semaine CASCADE;
DROP VIEW IF EXISTS v_pointage_jour_cpl CASCADE;

-- 2. Suppression de la fonction de calcul de jours ouvrables remplacée par CalendrierOuvrableEngine
DROP FUNCTION IF EXISTS fn_jours_ouvrables(DATE, DATE, INT) CASCADE;
