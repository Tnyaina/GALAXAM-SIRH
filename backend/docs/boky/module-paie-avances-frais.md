# 💳 Spécification & Cadrage : Module Paie, Avances & Notes de Frais

**Projet :** GALAXAM-SIRH  
**Auteur :** Architecte Logiciel Senior SIRH  
**Statut :** Cadrage pour développement futur (V2)  
**Dépendances :** `employe`, `contrat_travail`, `periode_paie`, `pointage_cumul_periode`

---

## 🎯 1. Contexte & Objectifs Métier

Le module **Paie & Déductions/Remboursements** a pour but d'alimenter les bulletins de paie mensuels générés par le moteur de paie GALAXAM avec :
1. **Les Avances sur salaire / Acomptes** accordés en cours de mois aux employés (à déduire du net à payer).
2. **Les Notes de frais professionnels** (déplacements hors barème, achats pour le compte de l'entreprise) validées par la DAF (à ajouter aux gains non imposables ou rembourser).
3. **Les Prêts aux employés** (remboursement échelonné sur plusieurs mois).

---

## 🗄️ 2. Modélisation de la Base de Données

```sql
-- ============================================================
-- MODULE PAIE : AVANCES, ACOMPTES ET NOTES DE FRAIS
-- ============================================================

-- 1. Types de mouvements de paie variables
CREATE TABLE ref_type_variable_paie (
    id_type_variable SERIAL PRIMARY KEY,
    code VARCHAR(30) UNIQUE NOT NULL,      -- AVANCE_SALAIRE, ACOMPTE, NOTE_FRAIS, PRET_EMPLOYE, RETENUE_DIVERS
    libelle VARCHAR(100) NOT NULL,
    est_gain BOOLEAN NOT NULL,              -- TRUE = ajout sur bulletin, FALSE = déduction sur net
    soumis_irsa BOOLEAN NOT NULL DEFAULT FALSE,
    soumis_cnaps BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO ref_type_variable_paie (code, libelle, est_gain, soumis_irsa, soumis_cnaps) VALUES
    ('AVANCE_SALAIRE', 'Avance sur salaire exceptionnel', FALSE, FALSE, FALSE),
    ('ACOMPTE_QUINZAINE', 'Acompte de quinzaine', FALSE, FALSE, FALSE),
    ('NOTE_FRAIS', 'Remboursement Note de Frais', TRUE, FALSE, FALSE),
    ('PRET_EMPLOYE', 'Échéance Prêt Entreprise', FALSE, FALSE, FALSE),
    ('PRIME_EXCEPTIONNELLE', 'Prime Exceptionnelle', TRUE, TRUE, TRUE);

-- 2. Demandes et Saisies d'Avances / Notes de Frais
CREATE TABLE variable_paie_mensuelle (
    id_variable SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,
    id_periode_paie INTEGER REFERENCES periode_paie(id_periode_paie), -- Peut être NULL au moment de la demande
    id_type_variable INTEGER NOT NULL REFERENCES ref_type_variable_paie(id_type_variable),
    
    montant DECIMAL(12,2) NOT NULL CHECK (montant > 0),
    date_demande DATE NOT NULL DEFAULT CURRENT_DATE,
    date_paiement DATE,                     -- Date à laquelle la trésorerie a versé l'avance/acompte
    
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, VALIDE, REFUSE, DECOMPTE_PAIE
    motif TEXT NOT NULL,
    piece_justificative_url VARCHAR(255),  -- URL ou chemin du reçu/facture uploadé
    
    saisi_par INTEGER REFERENCES utilisateur(id_utilisateur),
    valide_par INTEGER REFERENCES utilisateur(id_utilisateur),
    date_validation TIMESTAMP,
    motif_refus TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_variable_paie_employe ON variable_paie_mensuelle(id_employe);
CREATE INDEX idx_variable_paie_periode ON variable_paie_mensuelle(id_periode_paie);
CREATE INDEX idx_variable_paie_statut ON variable_paie_mensuelle(statut);

-- 3. Suivi des Prêts Entreprise (Échelonnés)
CREATE TABLE pret_employe (
    id_pret SERIAL PRIMARY KEY,
    id_employe INTEGER NOT NULL REFERENCES employe(id_employe),
    montant_total DECIMAL(12,2) NOT NULL CHECK (montant_total > 0),
    montant_mensualite DECIMAL(12,2) NOT NULL CHECK (montant_mensualite > 0),
    date_debut_remboursement DATE NOT NULL,
    nb_mensualites INTEGER NOT NULL,
    montant_restant_du DECIMAL(12,2) NOT NULL,
    est_solde BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## ⚙️ 3. Intégration dans le Moteur de Paie Dual (IRSA / ISI)

Lors de la clôture et du calcul du bulletin de paie d'un employé pour la période `P` :

1. **Calcul du Bruts & Imposables (IRSA / ISI)** :
   * Salaire de Base + Heures Supplémentaires + Indemnités (Pointage).
   * Addition des Primes Exceptionnelles imposables (`soumis_irsa = TRUE`).

2. **Calcul du Net à Payer (Avant Avances)** :
   * Déduction de l'IRSA (6 tranches) ou ISI (5%).
   * Déduction des cotisations sociales (CNaPS 1%, OSTIE/OMSI 1%).

3. **Application des Retenues et Remboursements de Trésorerie (Net à Verser Final)** :
   * Net a Verser = Net Imposable - Avances/Acomptes - Mensualité Prêt + Notes de Frais Validées.
   * Mettre à jour les variables de paie consommées avec le statut `DECOMPTE_PAIE`.

---

## 📋 4. Interface & Ergonomie (Frontend)

* **Onglet Paie > Saisie des Avances & Frais** :
  * Formulaire avec sélection d'employé, choix du type (Avance sur salaire, Acompte, Note de frais), montant et fichier justificatif (ex: ticket de caisse / facture).
  * Tableau récapitulatif filtrable par statut (`EN_ATTENTE`, `VALIDEE`, `INTEGREE_EN_PAIE`).
* **Intégration au Bulletin de Paie PDF** :
  * Section dédiée en bas du bulletin : *"Acomptes déjà versés au 15 du mois : -X Ar"*, *"Remboursement de frais : +Y Ar"*, facilitant la justification comptable.
