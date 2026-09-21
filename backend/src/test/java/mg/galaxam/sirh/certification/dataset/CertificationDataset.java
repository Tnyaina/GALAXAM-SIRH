package mg.galaxam.sirh.certification.dataset;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DATASET DE CERTIFICATION PRINCIPAL DÉTERMINISTE (EMP001 à EMP008).
 * 
 * Définit 8 profils réels sans AUCUNE valeur aléatoire, couvrant
 * l'intégralité des règles et frontières financières certifiées.
 */
public class CertificationDataset {

    public record EmployeeProfile(
            String code,
            String matricule,
            String nom,
            String prenom,
            String siteCode,
            String categorieCode,
            boolean estCadre,
            boolean estEligibleExonerationHs,
            BigDecimal salaireBaseContractuel,
            String codeSante,
            BigDecimal tauxSanteSal,
            BigDecimal tauxSantePat,
            int nbEnfants,
            LocalDate dateEmbauche
    ) {}

    // 1. EMP001_STANDARD : Nominal plein temps sans incident
    public static final EmployeeProfile EMP001_STANDARD = new EmployeeProfile(
            "EMP001_STANDARD",
            "EMP001",
            "RAZAFY",
            "Jean",
            "TNR",
            "AGENT_EXECUTION",
            false,
            true,
            new BigDecimal("400000.00"),
            "OMSI",
            new BigDecimal("0.015"),
            new BigDecimal("0.050"),
            0,
            LocalDate.of(2024, 1, 15)
    );

    // 2. EMP002_ABSENCE : Retard et absence injustifiée décomptée
    public static final EmployeeProfile EMP002_ABSENCE = new EmployeeProfile(
            "EMP002_ABSENCE",
            "EMP002",
            "RABE",
            "Paul",
            "TNR",
            "OUVRIER_QUALIFIE",
            false,
            true,
            new BigDecimal("500000.00"),
            "OMSI",
            new BigDecimal("0.015"),
            new BigDecimal("0.050"),
            1,
            LocalDate.of(2023, 6, 1)
    );

    // 3. EMP003_CONGE : Congé payé posé et validé (anti-double déduction)
    public static final EmployeeProfile EMP003_CONGE = new EmployeeProfile(
            "EMP003_CONGE",
            "EMP003",
            "RASOA",
            "Marie",
            "TNR",
            "EMPLOYE_BUREAU",
            false,
            true,
            new BigDecimal("450000.00"),
            "OSTIE",
            new BigDecimal("0.010"),
            new BigDecimal("0.050"),
            2,
            LocalDate.of(2023, 1, 10)
    );

    // 4. EMP004_HS : Dépassement de plafond 20h (12h HS30 + 10h HS50 = 22h)
    public static final EmployeeProfile EMP004_HS = new EmployeeProfile(
            "EMP004_HS",
            "EMP004",
            "RANDRIA",
            "Michel",
            "TNR",
            "TECHNICIEN",
            false,
            true,
            new BigDecimal("380000.00"),
            "OMSI",
            new BigDecimal("0.015"),
            new BigDecimal("0.050"),
            0,
            LocalDate.of(2024, 3, 1)
    );

    // 5. EMP005_VARIABLES : Primes AUTIMP, transport/repas, acompte/avance
    public static final EmployeeProfile EMP005_VARIABLES = new EmployeeProfile(
            "EMP005_VARIABLES",
            "EMP005",
            "ANDRY",
            "Faly",
            "TNR",
            "AGENT_COMMERCIAL",
            false,
            true,
            new BigDecimal("420000.00"),
            "OMSI",
            new BigDecimal("0.015"),
            new BigDecimal("0.050"),
            1,
            LocalDate.of(2022, 11, 1)
    );

    // 6. EMP006_COMPLEX : Le salarié qui traverse TOUT le cycle complet
    public static final EmployeeProfile EMP006_COMPLEX = new EmployeeProfile(
            "EMP006_COMPLEX",
            "EMP006",
            "RAKOTO_COMPLEX",
            "Herizo",
            "TNR",
            "AGENT_MAINTENANCE",
            false,
            true,
            new BigDecimal("600000.00"),
            "OMSI",
            new BigDecimal("0.015"),
            new BigDecimal("0.050"),
            2,
            LocalDate.of(2022, 1, 1)
    );

    // 7. EMP007_CADRE : Classification Cadre -> Exclusion stricte exonération HS
    public static final EmployeeProfile EMP007_CADRE = new EmployeeProfile(
            "EMP007_CADRE",
            "EMP007",
            "RAMA_CADRE",
            "Hery",
            "TNR",
            "CADRE_DIRIGEANT",
            true,
            false,
            new BigDecimal("1200000.00"),
            "OSTIE",
            new BigDecimal("0.010"),
            new BigDecimal("0.050"),
            3,
            LocalDate.of(2020, 5, 1)
    );

    // 8. EMP008_HIGH_INCOME : Plafonnement CNaPS 8 SME & Tranche supérieure IRSA 20%
    public static final EmployeeProfile EMP008_HIGH_INCOME = new EmployeeProfile(
            "EMP008_HIGH_INCOME",
            "EMP008",
            "RAJAO_DIR",
            "Stephane",
            "TNR",
            "DIRECTEUR",
            true,
            false,
            new BigDecimal("3500000.00"),
            "OMSI",
            new BigDecimal("0.015"),
            new BigDecimal("0.050"),
            3,
            LocalDate.of(2019, 1, 1)
    );
}
