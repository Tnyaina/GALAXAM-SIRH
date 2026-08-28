package mg.galaxam.sirh.model.conge.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.RefStatutDemandeConge;
import mg.galaxam.sirh.model.conge.RefTypeConge;
import mg.galaxam.sirh.model.conge.VDemandeCongeCpl;
import org.springframework.stereotype.Service;

@Service
public class CongeService {

    public BigDecimal simulerNombreJours(Date dateDebut, Date dateFin, boolean estDemiJournee) {
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires pour la simulation.");
        }
        if (estDemiJournee && !dateDebut.equals(dateFin)) {
            throw new IllegalArgumentException(
                    "Une simulation en demi-journée doit avoir des dates de début et de fin identiques.");
        }
        return DemandeConge.calculerNombreJoursDecomptes(dateDebut, dateFin, estDemiJournee);
    }

    public DemandeConge creerDemande(DemandeConge d, Integer currentUserId, Connection c) throws Exception {
        if (d.getIdEmploye() == null) {
            throw new IllegalArgumentException("L'identifiant de l'employé est obligatoire.");
        }
        if (d.getIdTypeConge() == null) {
            throw new IllegalArgumentException("Le type de congé est obligatoire.");
        }
        if (d.getDateDebut() == null || d.getDateFin() == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }

        RefStatutDemandeConge enAttente = RefStatutDemandeConge.rechercherParCode("EN_ATTENTE", c);
        if (enAttente == null) {
            throw new IllegalStateException("Statut EN_ATTENTE non trouvé dans le référentiel.");
        }
        d.setIdStatutDemandeConge(enAttente.getIdStatutDemandeConge());
        d.setSaisiPar(currentUserId);

        // Bascule automatique Arrêt Maladie si > 30 jours (Art. 13)
        RefTypeConge typeConge = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
        Date originalDateFin = d.getDateFin();
        BigDecimal totalJoursOrigine = DemandeConge.calculerNombreJoursDecomptes(d.getDateDebut(), originalDateFin,
                d.isEstDemiJournee());

        if (typeConge != null && "MALADIE".equals(typeConge.getCode())
                && totalJoursOrigine.compareTo(BigDecimal.valueOf(30)) > 0) {
            Date dateFinT1 = Date.valueOf(d.getDateDebut().toLocalDate().plusDays(29));
            d.setDateFin(dateFinT1);
            d.save(c);

            if (typeConge.getIdTypeCongeBascule() != null) {
                DemandeConge d2 = new DemandeConge();
                d2.setIdEmploye(d.getIdEmploye());
                d2.setIdTypeConge(typeConge.getIdTypeCongeBascule());
                d2.setIdStatutDemandeConge(enAttente.getIdStatutDemandeConge());
                d2.setDateDebut(Date.valueOf(dateFinT1.toLocalDate().plusDays(1)));
                d2.setDateFin(originalDateFin);
                d2.setJustificatifFourni(d.isJustificatifFourni());
                d2.setCommentaire(
                        "(Bascule automatique Art. 13) " + (d.getCommentaire() != null ? d.getCommentaire() : ""));
                d2.setSaisiPar(d.getSaisiPar());
                d2.save(c);
            }

            DemandeConge.creerNotificationRh(
                    "MALADIE_PLAFOND_EPISODE_ATTEINT",
                    "HAUTE",
                    "Bascule Automatique Arrêt Maladie (> 30j)",
                    "L'arrêt maladie continu (" + totalJoursOrigine
                            + "j) a été automatiquement scindé : 30 jours en maintien de salaire et la suite en Maladie Sans Solde (CNaPS).",
                    "/conges/demandes/employe/" + d.getIdEmploye(),
                    d.getIdEmploye(),
                    c);
        } else {
            d.save(c);

            DemandeConge.creerNotificationRh(
                    "CONGE_EN_ATTENTE",
                    "NORMALE",
                    "Nouvelle demande de congé",
                    "Une nouvelle demande de congé (" + (typeConge != null ? typeConge.getLibelle() : "Absence")
                            + ") a été enregistrée et est en attente de validation.",
                    "/conges",
                    d.getIdEmploye(),
                    c);
        }

        return d;
    }

    public DemandeConge validerDemande(int idDemande, Integer currentUserId, String commentaireValidation, Connection c)
            throws Exception {
        DemandeConge d = new DemandeConge().rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        d.valider(currentUserId, c);
        return d;
    }

    public DemandeConge refuserDemande(int idDemande, Integer currentUserId, String motifRefus, Connection c)
            throws Exception {
        DemandeConge d = new DemandeConge().rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        d.refuser(currentUserId, motifRefus, c);
        return d;
    }

    public DemandeConge annulerDemande(int idDemande, Integer currentUserId, String motifAnnulation, Connection c)
            throws Exception {
        DemandeConge d = new DemandeConge().rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        d.annuler(currentUserId, c);
        return d;
    }
}
