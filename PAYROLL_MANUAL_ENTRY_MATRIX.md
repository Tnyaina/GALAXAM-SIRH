# Matrice des saisies manuelles de paie

Cette matrice décrit le catalogue configuré dans `ref_type_mouvement_paie`. L'API n'expose que les lignes dont `saisie_manuelle_autorisee` est vraie et dont la période autorise `ADD_VARIABLE`.

| Type | Owner | Saisie manuelle | Période requise | Motif requis | Éditable jusqu'à | Impact Paie | Correction source |
|---|---|---:|---:|---:|---|---|---|
| `PRIME_EXCEPTIONNELLE` | DAF / Paie | Oui | Oui | Oui | Avant calcul/verrouillage | Gain brut | Campagne Variables |
| `PRIME_PERFORMANCE` | DAF / Paie + moteur | Oui, montant attribué | Oui | Oui | Avant calcul/verrouillage | Gain brut transformé | Attribution DAF puis recalcul |
| `AUTIMP` | DAF / Paie | Oui, legacy qualifié | Oui | Oui | Avant calcul/verrouillage | Gain brut imposable | Campagne Variables |
| `RETENUE_DIVERSE` | DAF / Paie | Oui | Oui | Oui | Avant calcul/verrouillage | Retenue nette | Campagne Variables |
| `CALL_PERSO` | DAF / Paie | Oui | Oui | Oui | Avant calcul/verrouillage | Retenue nette | Source décrite dans le motif |
| `EXTRA_DEPLACEMENT` | DAF / Paie | Oui, exception | Oui | Oui | Avant calcul/verrouillage | Gain net | Mission/source exceptionnelle |
| `REGULARISATION_CONGE` | Congés / Paie | Oui, correction | Oui | Oui | Avant calcul/verrouillage | Régularisation | Congé ou période antérieure référencée |
| `ACOMPTE_15` | Trésorerie | Oui via workflow | Oui | Oui | Avant calcul/verrouillage | Retenue nette unique | Domaine Acompte |
| `AVANCE_SPECIALE` | Trésorerie | Oui via workflow | Oui | Oui | Avant calcul/verrouillage | Retenue nette plafonnée | Domaine Avance / solde |
| `PRIME_ASSIDUITE` | Système / Pointage | Non | Oui | Non | Non applicable | Gain brut automatique futur | Configuration datée + Pointage |
| `NOTE_DE_FRAIS` | Futur domaine Frais | Non | Oui | Oui | Non applicable | Compatibilité historique | Futur workflow Frais |

## Types toujours automatiques

Ne sont jamais des variables ordinaires : salaire, absence, retard, HS, nuit, dimanche, férié, congé, TCONGE, repas ordinaire, déplacement ordinaire, CNaPS, santé, FMFP, IRSA, ISI, net à payer et échéance de prêt.

## Provenances

Les provenances configurées sont `VARIABLE_DAF`, `ACOMPTE`, `AVANCE`, `CONGE`, `SYSTEME` et `IMPORT`. `IMPORT` est réservé à une compatibilité ou un futur owner explicite ; aucun import télécom ou frais fictif n'est implémenté.
