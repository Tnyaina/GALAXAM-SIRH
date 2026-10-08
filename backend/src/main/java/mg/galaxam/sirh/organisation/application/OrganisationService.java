package mg.galaxam.sirh.organisation.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.common.exception.SirhExceptionTranslator;
import mg.galaxam.sirh.organisation.api.dto.*;
import mg.galaxam.sirh.organisation.persistence.*;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service Métier Unifié et Responsable de l'Architecture Organisationnelle (POO).
 * Centralise l'ensemble des règles métier, validations d'intégrité, audits
 * et gestion transactionnelle pour les filiales, sites, départements, services,
 * fonctions et référentiels RH.
 */
@Service
public class OrganisationService {

    @Autowired
    private DataSource dataSource;

    /** Référentiels actifs utilisés ensemble par les formulaires employé/carrière. */
    public Map<String, Object> listerReferentielsFormulaireEmploye() {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> result = new HashMap<>();
            result.put("filiales", RefFiliale.listActifs(c));
            result.put("sites", RefSite.listActifs(c));
            result.put("departements", RefDepartement.listActifs(c));
            result.put("services", RefService.listActifs(c));
            result.put("fonctions", RefFonction.listActifs(c));
            result.put("typesContrat", RefTypeContrat.listActifs(c));
            result.put("typesIntervenant", RefTypeIntervenant.listActifs(c));
            return result;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // I. GESTION DES FILIALES
    // =========================================================================

    public List<RefFiliale> listerFiliales(Boolean actifUniquement, String search) {
        try (Connection c = dataSource.getConnection()) {
            List<RefFiliale> list = Boolean.TRUE.equals(actifUniquement)
                    ? RefFiliale.listActifs(c) : RefFiliale.listAll(c);
            return list.stream()
                    .filter(f -> actifUniquement == null || !actifUniquement || f.isEstActif())
                    .filter(f -> search == null || search.isBlank()
                            || (f.getCodeFiliale() != null && f.getCodeFiliale().toLowerCase().contains(search.toLowerCase()))
                            || (f.getRaisonSociale() != null && f.getRaisonSociale().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFiliale getFiliale(int id) {
        try (Connection c = dataSource.getConnection()) {
            RefFiliale found = (RefFiliale) new RefFiliale().rechercherParId(id, c);
            if (found == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Filiale non trouvée.");
            }
            return found;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFiliale creerFiliale(FilialeRequest request) {
        if (request == null || request.getCodeFiliale() == null || request.getCodeFiliale().isBlank()) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Le code filiale est obligatoire.");
        }
        if (request.getRaisonSociale() == null || request.getRaisonSociale().isBlank()) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "La raison sociale est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                f.setCodeFiliale(request.getCodeFiliale().trim().toUpperCase());
                f.setRaisonSociale(request.getRaisonSociale().trim());
                f.setNif(request.getNif());
                f.setStat(request.getStat());
                f.setAdresseSiege(request.getAdresseSiege());
                f.setRcs(request.getRcs());
                f.setTelephone(request.getTelephone());
                f.setEmail(request.getEmail());

                f.save(c);
                AuditLog.audit(c, "CREATION", "ref_filiale", String.valueOf(f.getIdFiliale()), null, f);

                c.commit();
                return f;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFiliale modifierFiliale(int id, FilialeRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale existing = (RefFiliale) new RefFiliale().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Filiale non trouvée.");
                }

                RefFiliale oldState = new RefFiliale();
                oldState.setIdFiliale(existing.getIdFiliale());
                oldState.setCodeFiliale(existing.getCodeFiliale());
                oldState.setRaisonSociale(existing.getRaisonSociale());

                existing.setCodeFiliale(request.getCodeFiliale().trim().toUpperCase());
                existing.setRaisonSociale(request.getRaisonSociale().trim());
                existing.setNif(request.getNif());
                existing.setStat(request.getStat());
                existing.setAdresseSiege(request.getAdresseSiege());
                existing.setRcs(request.getRcs());
                existing.setTelephone(request.getTelephone());
                existing.setEmail(request.getEmail());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_filiale", String.valueOf(id), oldState, existing);

                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFiliale desactiverFiliale(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale existing = (RefFiliale) new RefFiliale().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Filiale non trouvée.");
                }

                validerDesactivationFiliale(existing, c);

                existing.setEstActif(false);
                existing.setDateDesactivation(new Timestamp(System.currentTimeMillis()));
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_filiale", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw (ex instanceof SirhBusinessException ? (SirhBusinessException) ex
                        : new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage()));
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFiliale activerFiliale(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale existing = (RefFiliale) new RefFiliale().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Filiale non trouvée.");
                }

                existing.setEstActif(true);
                existing.setDateDesactivation(null);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_filiale", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    private void validerDesactivationFiliale(RefFiliale filiale, Connection c) throws Exception {
        int nbSitesActifs = 0;
        String sqlCheckSites = "SELECT COUNT(*) FROM ref_site WHERE id_filiale_rattachement = ? AND est_actif = true";
        try (PreparedStatement ps = c.prepareStatement(sqlCheckSites)) {
            ps.setInt(1, filiale.getIdFiliale());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) nbSitesActifs = rs.getInt(1);
            }
        }

        int nbContratsEnCours = 0;
        String sqlCheckContrats = "SELECT COUNT(*) FROM contrat_travail WHERE id_filiale = ? AND (date_fin IS NULL OR date_fin >= CURRENT_DATE)";
        try (PreparedStatement ps = c.prepareStatement(sqlCheckContrats)) {
            ps.setInt(1, filiale.getIdFiliale());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) nbContratsEnCours = rs.getInt(1);
            }
        }

        if (nbSitesActifs > 0 || nbContratsEnCours > 0) {
            StringBuilder errMsg = new StringBuilder("Impossible de désactiver la filiale « ")
                    .append(filiale.getRaisonSociale())
                    .append(" » : elle comporte encore ");
            if (nbSitesActifs > 0) {
                errMsg.append(nbSitesActifs).append(" site(s) actif(s)");
            }
            if (nbSitesActifs > 0 && nbContratsEnCours > 0) {
                errMsg.append(" et ");
            }
            if (nbContratsEnCours > 0) {
                errMsg.append(nbContratsEnCours).append(" contrat(s) de travail en cours");
            }
            errMsg.append(". Veuillez réaffecter ou clôturer ces éléments avant de pouvoir désactiver la filiale.");
            throw new SirhBusinessException(SirhErrorCode.DATA_REFERENCED, errMsg.toString());
        }
    }

    // =========================================================================
    // II. GESTION DES SITES
    // =========================================================================

    public List<RefSite> listerSites(Integer idFiliale, Boolean actifUniquement, String search) {
        try (Connection c = dataSource.getConnection()) {
            List<RefSite> list = idFiliale != null
                    ? RefSite.listByFiliale(idFiliale, Boolean.TRUE.equals(actifUniquement), c)
                    : Boolean.TRUE.equals(actifUniquement) ? RefSite.listActifs(c) : RefSite.listAll(c);
            return list.stream()
                    .filter(s -> idFiliale == null || Objects.equals(s.getIdFilialeRattachement(), idFiliale))
                    .filter(s -> actifUniquement == null || !actifUniquement || s.isEstActif())
                    .filter(s -> search == null || search.isBlank()
                            || (s.getCodeSite() != null && s.getCodeSite().toLowerCase().contains(search.toLowerCase()))
                            || (s.getLibelle() != null && s.getLibelle().toLowerCase().contains(search.toLowerCase()))
                            || (s.getFilialeRaisonSociale() != null && s.getFilialeRaisonSociale().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefSite getSite(int id) {
        try (Connection c = dataSource.getConnection()) {
            RefSite found = (RefSite) new RefSite().rechercherParId(id, c);
            if (found == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Site non trouvé.");
            }
            return found;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefSite creerSite(SiteRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite s = new RefSite();
                s.setCodeSite(request.getCodeSite());
                s.setLibelle(request.getLibelle());
                s.setIdFilialeRattachement(request.getIdFilialeRattachement());
                s.setHoraireContinu(request.isHoraireContinu());
                s.setHeureDebutMatin(request.getHeureDebutMatin());
                s.setHeureFinMatin(request.getHeureFinMatin());
                s.setHeureDebutApresMidi(request.getHeureDebutApresMidi());
                s.setHeureFinApresMidi(request.getHeureFinApresMidi());
                s.setPlancherArriveeActif(request.isPlancherArriveeActif());
                s.setHeureEntreeMinimaleValorisee(request.getHeureEntreeMinimaleValorisee());

                s.save(c);
                AuditLog.audit(c, "CREATION", "ref_site", String.valueOf(s.getIdSite()), null, s);

                c.commit();
                return s;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefSite modifierSite(int id, SiteRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite existing = (RefSite) new RefSite().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Site non trouvé.");
                }

                existing.setCodeSite(request.getCodeSite());
                existing.setLibelle(request.getLibelle());
                existing.setIdFilialeRattachement(request.getIdFilialeRattachement());
                existing.setHoraireContinu(request.isHoraireContinu());
                existing.setHeureDebutMatin(request.getHeureDebutMatin());
                existing.setHeureFinMatin(request.getHeureFinMatin());
                existing.setHeureDebutApresMidi(request.getHeureDebutApresMidi());
                existing.setHeureFinApresMidi(request.getHeureFinApresMidi());
                existing.setPlancherArriveeActif(request.isPlancherArriveeActif());
                existing.setHeureEntreeMinimaleValorisee(request.getHeureEntreeMinimaleValorisee());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_site", String.valueOf(id), null, existing);

                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefSite configurerPlancherSite(int id, Map<String, Object> payload) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite existing = (RefSite) new RefSite().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Site non trouvé.");
                }

                boolean actif = false;
                if (payload != null && payload.containsKey("actif")) {
                    actif = Boolean.parseBoolean(payload.get("actif").toString());
                }

                if (payload != null && payload.containsKey("heureEntreeMinimaleValorisee") && payload.get("heureEntreeMinimaleValorisee") != null) {
                    String timeStr = payload.get("heureEntreeMinimaleValorisee").toString().trim();
                    if (!timeStr.isEmpty()) {
                        if (timeStr.length() == 5) timeStr += ":00";
                        existing.setHeureEntreeMinimaleValorisee(Time.valueOf(timeStr));
                    }
                }

                existing.setPlancherArriveeActif(actif);
                existing.update(c);

                AuditLog.audit(c, existing.isPlancherArriveeActif() ? "ACTIVATION_PLANCHER" : "DESACTIVATION_PLANCHER",
                        "ref_site", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefSite desactiverSite(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite existing = (RefSite) new RefSite().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Site non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_site", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefSite activerSite(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite existing = (RefSite) new RefSite().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Site non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_site", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // III. GESTION DES DÉPARTEMENTS
    // =========================================================================

    public List<RefDepartement> listerDepartements(Boolean actifUniquement, String search) {
        try (Connection c = dataSource.getConnection()) {
            List<RefDepartement> list = Boolean.TRUE.equals(actifUniquement)
                    ? RefDepartement.listActifs(c) : RefDepartement.listAll(c);
            return list.stream()
                    .filter(d -> actifUniquement == null || !actifUniquement || d.isEstActif())
                    .filter(d -> search == null || search.isBlank()
                            || (d.getCodeDepartement() != null && d.getCodeDepartement().toLowerCase().contains(search.toLowerCase()))
                            || (d.getLibelle() != null && d.getLibelle().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefDepartement getDepartement(int id) {
        try (Connection c = dataSource.getConnection()) {
            RefDepartement found = (RefDepartement) new RefDepartement().rechercherParId(id, c);
            if (found == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Département non trouvé.");
            }
            return found;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefDepartement creerDepartement(DepartementRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefDepartement d = new RefDepartement();
                d.setCodeDepartement(request.getCodeDepartement());
                d.setLibelle(request.getLibelle());

                d.save(c);
                AuditLog.audit(c, "CREATION", "ref_departement", String.valueOf(d.getIdDepartement()), null, d);

                c.commit();
                return d;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefDepartement modifierDepartement(int id, DepartementRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefDepartement existing = (RefDepartement) new RefDepartement().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Département non trouvé.");
                }

                existing.setCodeDepartement(request.getCodeDepartement());
                existing.setLibelle(request.getLibelle());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_departement", String.valueOf(id), null, existing);

                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefDepartement desactiverDepartement(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefDepartement existing = (RefDepartement) new RefDepartement().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Département non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_departement", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefDepartement activerDepartement(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefDepartement existing = (RefDepartement) new RefDepartement().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Département non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_departement", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // IV. GESTION DES SERVICES
    // =========================================================================

    public List<RefService> listerServices(Integer idDepartement, Boolean actifUniquement, String search) {
        try (Connection c = dataSource.getConnection()) {
            List<RefService> list = (idDepartement != null)
                    ? RefService.listByDepartement(idDepartement, Boolean.TRUE.equals(actifUniquement), c)
                    : Boolean.TRUE.equals(actifUniquement) ? RefService.listActifs(c) : RefService.listAll(c);

            return list.stream()
                    .filter(s -> actifUniquement == null || !actifUniquement || s.isEstActif())
                    .filter(s -> search == null || search.isBlank()
                            || (s.getCodeService() != null && s.getCodeService().toLowerCase().contains(search.toLowerCase()))
                            || (s.getLibelle() != null && s.getLibelle().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefService getService(int id) {
        try (Connection c = dataSource.getConnection()) {
            RefService found = (RefService) new RefService().rechercherParId(id, c);
            if (found == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Service non trouvé.");
            }
            return found;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefService creerService(ServiceRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefService s = new RefService();
                s.setCodeService(request.getCodeService());
                s.setLibelle(request.getLibelle());
                s.setIdDepartement(request.getIdDepartement());

                s.save(c);
                AuditLog.audit(c, "CREATION", "ref_service", String.valueOf(s.getIdService()), null, s);

                c.commit();
                return s;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefService modifierService(int id, ServiceRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefService existing = (RefService) new RefService().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Service non trouvé.");
                }

                existing.setCodeService(request.getCodeService());
                existing.setLibelle(request.getLibelle());
                existing.setIdDepartement(request.getIdDepartement());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_service", String.valueOf(id), null, existing);

                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefService desactiverService(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefService existing = (RefService) new RefService().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Service non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_service", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefService activerService(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefService existing = (RefService) new RefService().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Service non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_service", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // V. GESTION DES FONCTIONS / POSTES
    // =========================================================================

    public List<RefFonction> listerFonctions(Boolean actifUniquement, String search) {
        try (Connection c = dataSource.getConnection()) {
            List<RefFonction> list = Boolean.TRUE.equals(actifUniquement)
                    ? RefFonction.listActifs(c) : RefFonction.listAll(c);
            return list.stream()
                    .filter(f -> actifUniquement == null || !actifUniquement || f.isEstActif())
                    .filter(f -> search == null || search.isBlank()
                            || (f.getCodeFonction() != null && f.getCodeFonction().toLowerCase().contains(search.toLowerCase()))
                            || (f.getLibelle() != null && f.getLibelle().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFonction getFonction(int id) {
        try (Connection c = dataSource.getConnection()) {
            RefFonction found = (RefFonction) new RefFonction().rechercherParId(id, c);
            if (found == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Fonction non trouvée.");
            }
            return found;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFonction creerFonction(FonctionRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFonction f = new RefFonction();
                f.setCodeFonction(request.getCodeFonction());
                f.setLibelle(request.getLibelle());
                f.setDispensePointage(request.isDispensePointage());

                f.save(c);
                AuditLog.audit(c, "CREATION", "ref_fonction", String.valueOf(f.getIdFonction()), null, f);

                c.commit();
                return f;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFonction modifierFonction(int id, FonctionRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFonction existing = (RefFonction) new RefFonction().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Fonction non trouvée.");
                }

                existing.setCodeFonction(request.getCodeFonction());
                existing.setLibelle(request.getLibelle());
                existing.setDispensePointage(request.isDispensePointage());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_fonction", String.valueOf(id), null, existing);

                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFonction desactiverFonction(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFonction existing = (RefFonction) new RefFonction().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Fonction non trouvée.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_fonction", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefFonction activerFonction(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFonction existing = (RefFonction) new RefFonction().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Fonction non trouvée.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_fonction", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // VI. RÉFÉRENTIELS RH : BANQUES
    // =========================================================================

    public List<RefBanque> listerBanques(Boolean actifUniquement, String search) {
        try (Connection c = dataSource.getConnection()) {
            List<RefBanque> list = Boolean.TRUE.equals(actifUniquement)
                    ? RefBanque.listActifs(c) : RefBanque.listAll(c);
            return list.stream()
                    .filter(b -> actifUniquement == null || !actifUniquement || b.isEstActif())
                    .filter(b -> search == null || search.isBlank()
                            || (b.getCodeBanque() != null && b.getCodeBanque().toLowerCase().contains(search.toLowerCase()))
                            || (b.getNomBanque() != null && b.getNomBanque().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefBanque getBanque(int id) {
        try (Connection c = dataSource.getConnection()) {
            RefBanque found = (RefBanque) new RefBanque().rechercherParId(id, c);
            if (found == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Banque non trouvée.");
            }
            return found;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefBanque creerBanque(RefBanque request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                request.save(c);
                AuditLog.audit(c, "CREATION", "ref_banque", String.valueOf(request.getIdBanque()), null, request);
                c.commit();
                return request;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefBanque modifierBanque(int id, RefBanque request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefBanque existing = (RefBanque) new RefBanque().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Banque non trouvée.");
                }

                existing.setCodeBanque(request.getCodeBanque());
                existing.setNomBanque(request.getNomBanque());
                existing.setCodeSwift(request.getCodeSwift());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_banque", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefBanque desactiverBanque(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefBanque existing = (RefBanque) new RefBanque().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Banque non trouvée.");
                }

                existing.setEstActif(false);
                existing.update(c);
                AuditLog.audit(c, "DESACTIVATION", "ref_banque", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefBanque activerBanque(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefBanque existing = (RefBanque) new RefBanque().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Banque non trouvée.");
                }

                existing.setEstActif(true);
                existing.update(c);
                AuditLog.audit(c, "ACTIVATION", "ref_banque", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // VII. RÉFÉRENTIELS RH : TYPES DE CONTRAT
    // =========================================================================

    public List<RefTypeContrat> listerTypesContrat(Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeContrat> list = Boolean.TRUE.equals(actifUniquement)
                    ? RefTypeContrat.listActifs(c) : RefTypeContrat.listAll(c);
            return list.stream()
                    .filter(tc -> actifUniquement == null || !actifUniquement || tc.isEstActif())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeContrat getTypeContrat(int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeContrat found = (RefTypeContrat) new RefTypeContrat().rechercherParId(id, c);
            if (found == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Type de contrat non trouvé.");
            }
            return found;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeContrat creerTypeContrat(RefTypeContrat request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                request.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_contrat", String.valueOf(request.getIdTypeContrat()), null, request);
                c.commit();
                return request;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeContrat modifierTypeContrat(int id, RefTypeContrat request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeContrat existing = (RefTypeContrat) new RefTypeContrat().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Type de contrat non trouvé.");
                }

                existing.setCode(request.getCode());
                existing.setLibelle(request.getLibelle());
                existing.setExigeDateFin(request.isExigeDateFin());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_contrat", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeContrat desactiverTypeContrat(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeContrat existing = (RefTypeContrat) new RefTypeContrat().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Type de contrat non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);
                AuditLog.audit(c, "DESACTIVATION", "ref_type_contrat", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeContrat activerTypeContrat(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeContrat existing = (RefTypeContrat) new RefTypeContrat().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Type de contrat non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);
                AuditLog.audit(c, "ACTIVATION", "ref_type_contrat", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // VIII. RÉFÉRENTIELS RH : TYPES D'INTERVENANT
    // =========================================================================

    public List<RefTypeIntervenant> listerTypesIntervenant(Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeIntervenant> list = Boolean.TRUE.equals(actifUniquement)
                    ? RefTypeIntervenant.listActifs(c) : RefTypeIntervenant.listAll(c);
            return list.stream()
                    .filter(ti -> actifUniquement == null || !actifUniquement || ti.isEstActif())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeIntervenant getTypeIntervenant(int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeIntervenant found = (RefTypeIntervenant) new RefTypeIntervenant().rechercherParId(id, c);
            if (found == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Type d'intervenant non trouvé.");
            }
            return found;
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeIntervenant creerTypeIntervenant(RefTypeIntervenant request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                request.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_intervenant", String.valueOf(request.getIdTypeIntervenant()), null, request);
                c.commit();
                return request;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeIntervenant modifierTypeIntervenant(int id, RefTypeIntervenant request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeIntervenant existing = (RefTypeIntervenant) new RefTypeIntervenant().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Type d'intervenant non trouvé.");
                }

                existing.setCode(request.getCode());
                existing.setLibelle(request.getLibelle());
                existing.setSoumisIrsa(request.isSoumisIrsa());
                existing.setSoumisIsi5(request.isSoumisIsi5());
                existing.setSoumisCnaps(request.isSoumisCnaps());
                existing.setSoumisSante(request.isSoumisSante());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_intervenant", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeIntervenant desactiverTypeIntervenant(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeIntervenant existing = (RefTypeIntervenant) new RefTypeIntervenant().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Type d'intervenant non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);
                AuditLog.audit(c, "DESACTIVATION", "ref_type_intervenant", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }

    public RefTypeIntervenant activerTypeIntervenant(int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeIntervenant existing = (RefTypeIntervenant) new RefTypeIntervenant().rechercherParId(id, c);
                if (existing == null) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Type d'intervenant non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);
                AuditLog.audit(c, "ACTIVATION", "ref_type_intervenant", String.valueOf(id), null, existing);
                c.commit();
                return existing;
            } catch (Exception ex) {
                c.rollback();
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            throw SirhExceptionTranslator.propagate(e);
        }
    }
}
