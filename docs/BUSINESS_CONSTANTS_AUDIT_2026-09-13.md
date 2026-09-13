# Audit des constantes métier — 2026-09-13

Cet inventaire distingue les invariants techniques des paramètres qui doivent être
résolus depuis une configuration datée. Les valeurs de compatibilité conservées
dans les DTO sont explicitement signalées et ne constituent pas une source de
vérité du moteur.

| Valeur / expression | Emplacement | Signification | Type | Dynamique/d datée | Action |
|---|---|---|---|---|---|
| `60` | calculateurs Pointage/Paie | minutes par heure | TECHNIQUE | Non | conserver |
| `22:00`, `05:00` | services Pointage | seuil nuit par défaut | PARAMETRE_ENTREPRISE | Oui | résolution via paramètres datés; défaut uniquement compatibilité |
| `8.0` | synthèse Pointage | seuil journalier | PARAMETRE_ENTREPRISE | Oui | lire `SEUIL_HEURES_JOUR`; supprimer les usages de calcul parallèles |
| `40` | moteur hebdomadaire/Paie | seuil hebdomadaire | PARAMETRE_LEGAL | Oui | fournir la règle hebdomadaire résolue |
| `173.33` | `PayrollExecutionEngine` | diviseur mensuel légal | PARAMETRE_LEGAL | Oui | conserver temporairement comme défaut de compatibilité; migration vers config légale |
| `1.30`, `1.40`, `1.50`, `2.00`, `0.30` | valorisation HS/nuit | coefficients de majoration | PARAMETRE_LEGAL | Oui | moteur reçoit désormais les primes BigDecimal; résolution `RefTauxMajoration` stricte à brancher dans l'orchestrateur |
| `0.01`, `0.05`, `0.13` | cotisations/STC/exports | taux CNaPS/santé/FMFP | PARAMETRE_LEGAL | Oui | résolveur légal unique; STC/export doivent consommer le résultat |
| `350000` | Paie/STC | seuil exonération IRSA | PARAMETRE_LEGAL | Oui | `PaieConfigLegale` datée, sans fallback silencieux |
| `2000`, `1000` | Pointage | repas et réduction déplacement | PARAMETRE_ENTREPRISE | Oui | paramètres globaux datés |
| `31200`, `53200` | seeds/tests/config sites | forfaits déplacement de référence | REFERENTIEL | Oui | configuration site datée; ne pas dupliquer en Java |
| `22` | fallbacks de prorata | nombre de jours ouvrables conventionnel | A_VERIFIER | Oui | supprimer comme fallback métier; bloquer si calendrier absent |
| `40` (lignes Excel) | `SafiOdsExportService` | taille minimale de gabarit | TECHNIQUE | Non | conserver |

## Constats et dette restante

États : `MIGRÉ_BIGDECIMAL` pour les taux sociaux du moteur principal et les
majorations portées par `PayrollCalculationInput`; `PARTIEL` pour les résolveurs
et les frontières legacy.

- Les valeurs de taux encore représentées en `double` dans `PayrollCalculationInput`,
  `PayrollService` et `StcService` sont des frontières à migrer vers `BigDecimal`.
- Les services Pointage contiennent encore des conversions décimales pour DTO/export;
  elles ne doivent pas remonter dans les calculateurs purs.
- Les fallbacks `22` identifiés sont traités séparément : ils ne doivent jamais
  masquer un trou de calendrier ou de configuration.
- Les règles d’arrondi sont à centraliser dans le moteur Paie et à documenter avec
  des tests de frontière; les arrondis d’affichage/export restent des frontières.
