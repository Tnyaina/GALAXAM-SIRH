# Traçabilité réglementaire IRSA

| Rule set | Période effective | Source / article | Arrondi assiette | Tranches | Minimum | Réduction familiale |
|---|---|---|---|---|---|---|
| `LF_2024_2025` | jusqu'au 31/12/2025 | CGI Madagascar 2025, art. 01.03.16 | Centaine inférieure | BDD `bareme_irsa` datée | BDD `minimum_perception_irsa` | BDD `deduction_charge_famille` |
| `LF_2026_JAN_FEB` | 01/01–28/02/2026 | Loi de finances 2026 / CGI, art. 01.03.16 | Centaine inférieure | BDD `bareme_irsa` datée | BDD | BDD |
| `LF_2026` | depuis le 01/03/2026 | Loi de finances 2026 / CGI, art. 01.03.16 | Centaine inférieure | BDD `bareme_irsa` datée | BDD | BDD |

La tranche jusqu'à 350 000 Ar est une tranche à taux zéro. Le minimum de perception est une règle distincte appliquée après arrondi fiscal et réduction familiale. Aucune de ces valeurs n'est une constante de `IrsaCalculator`.
