package mg.galaxam.sirh.paie.api;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.domain.PayrollBusinessTime;
import mg.galaxam.sirh.paie.application.PaymentEvidence;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Le statut financier du bulletin reste indépendant de sa validation comptable. */
@RestController
@RequestMapping({"/api/v1/paie/reglements", "/v1/paie/reglements", "/paie/reglements"})
public class BulletinReglementController {
    private final DataSource dataSource;
    public BulletinReglementController(DataSource dataSource) { this.dataSource = dataSource; }

    public record DecisionRequest(LocalDate date, String reference, String motif) {}
    public record AnnulationRequest(String motif) {}

    private static SirhBusinessException invalid(String message) {
        return new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, message);
    }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw invalid(label + " obligatoire.");
        return value.trim();
    }
    static void validatePaymentDate(LocalDate date) {
        if (date == null || date.isAfter(PayrollBusinessTime.today()))
            throw invalid("La date du paiement doit être renseignée et ne peut pas être dans le futur.");
    }
    private static int user(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details)
            return details.getIdUtilisateur();
        throw new SirhBusinessException(SirhErrorCode.AUTHENTICATION_REQUIRED);
    }
    record Justificatif(String nom, String mime, byte[] contenu) {}
    static Justificatif justificatif(MultipartFile file) throws Exception {
        PaymentEvidence proof = PaymentEvidence.from(file);
        return new Justificatif(proof.name(), proof.mime(), proof.bytes());
    }
    private interface SqlWork<T> { T run(Connection connection) throws Exception; }
    private <T> T transaction(SqlWork<T> work) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) item.put(meta.getColumnLabel(i), rs.getObject(i));
        return item;
    }
    private static List<Map<String, Object>> query(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                while (rs.next()) result.add(row(rs));
                return result;
            }
        }
    }
    private static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeUpdate();
        }
    }
    private static long insertId(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return rs.getLong(1); }
        }
    }
    private static Map<String, Object> bulletin(Connection c, int id, boolean lock) throws SQLException {
        List<Map<String, Object>> found = query(c, "SELECT id_bulletin,id_periode_paie,reference_bulletin,nom_prenom,matricule,net_a_payer,mode_paiement,banque_nom,banque_rib,statut,type_bulletin FROM bulletin_paie WHERE id_bulletin=?" + (lock ? " FOR UPDATE" : ""), id);
        if (found.isEmpty()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Bulletin introuvable.");
        return found.get(0);
    }
    private static void eligible(Map<String, Object> b) {
        if (!"MENSUEL".equalsIgnoreCase(String.valueOf(b.get("type_bulletin"))) ||
            !Set.of("VALIDE", "CLOTURE").contains(String.valueOf(b.get("statut"))))
            throw invalid("Seul un bulletin mensuel validé ou clôturé peut être réglé.");
    }
    private static BigDecimal due(Connection c, Map<String, Object> b) throws SQLException {
        BigDecimal paid = (BigDecimal) query(c, "SELECT COALESCE(SUM(montant),0) AS total FROM paie_bulletin_reglement WHERE id_bulletin=? AND annule_at IS NULL", b.get("id_bulletin")).get(0).get("total");
        return ((BigDecimal) b.get("net_a_payer")).subtract(paid);
    }
    private static boolean pending(Connection c, int id) throws SQLException {
        return !query(c, "SELECT id_ligne FROM paie_bulletin_virement_ligne WHERE id_bulletin=? AND statut='EN_ATTENTE' LIMIT 1", id).isEmpty();
    }
    private static boolean includedInActiveStc(Connection c, int id) throws SQLException {
        return !query(c, "SELECT id_stc FROM paie_stc WHERE id_bulletin_mensuel=? "
                + "AND statut IN ('VALIDE','REGLE') AND COALESCE(solde_bulletin_a_regler,0)>0 LIMIT 1", id).isEmpty();
    }

    @GetMapping("/periode/{idPeriode}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public List<Map<String, Object>> periode(@PathVariable int idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return query(c, "SELECT b.id_bulletin AS \"idBulletin\",b.reference_bulletin AS \"referenceBulletin\",b.nom_prenom AS \"nomPrenom\",b.matricule,b.mode_paiement AS \"modePaiement\",b.banque_nom AS banque,b.banque_rib AS rib,b.statut,b.net_a_payer AS \"netAPayer\",COALESCE(SUM(r.montant) FILTER (WHERE r.annule_at IS NULL),0) AS verse, b.net_a_payer-COALESCE(SUM(r.montant) FILTER (WHERE r.annule_at IS NULL),0) AS reste, EXISTS(SELECT 1 FROM paie_bulletin_virement_ligne l WHERE l.id_bulletin=b.id_bulletin AND l.statut='EN_ATTENTE') AS \"virementEnAttente\", EXISTS(SELECT 1 FROM paie_stc s WHERE s.id_bulletin_mensuel=b.id_bulletin AND s.statut IN ('VALIDE','REGLE') AND COALESCE(s.solde_bulletin_a_regler,0)>0) AS \"inclusDansStc\" FROM bulletin_paie b LEFT JOIN paie_bulletin_reglement r ON r.id_bulletin=b.id_bulletin WHERE b.id_periode_paie=? AND b.type_bulletin='MENSUEL' AND b.statut IN ('VALIDE','CLOTURE') GROUP BY b.id_bulletin ORDER BY b.nom_prenom,b.id_bulletin", idPeriode);
        }
    }

    @GetMapping("/bulletin/{idBulletin}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public List<Map<String, Object>> historique(@PathVariable int idBulletin) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            bulletin(c, idBulletin, false);
            return query(c, "SELECT id_reglement AS id,id_ligne_virement AS \"idLigneVirement\",id_stc_reglement AS \"idStcReglement\",montant,mode_reglement AS mode,date_reglement AS date,reference_reglement AS reference,commentaire,nom_justificatif AS \"nomJustificatif\",cree_at AS \"creeAt\",annule_at AS \"annuleAt\",motif_annulation AS \"motifAnnulation\" FROM paie_bulletin_reglement WHERE id_bulletin=? ORDER BY id_reglement DESC", idBulletin);
        }
    }

    @GetMapping("/bulletin/{idBulletin}/justificatif/{idReglement}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> telechargerJustificatif(@PathVariable int idBulletin, @PathVariable long idReglement) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT mime_justificatif,contenu_justificatif FROM paie_bulletin_reglement WHERE id_reglement=? AND id_bulletin=?")) {
            ps.setLong(1, idReglement);
            ps.setInt(2, idBulletin);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return ResponseEntity.notFound().build();
                String mime = rs.getString(1);
                byte[] proof = rs.getBytes(2);
                if (mime == null || proof == null || proof.length == 0) return ResponseEntity.notFound().build();
                String ext = "application/pdf".equals(mime) ? "pdf" : "image/png".equals(mime) ? "png" : "jpg";
                return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"justificatif-reglement-" + idReglement + "." + ext + "\"")
                        .body(proof);
            }
        }
    }

    @GetMapping("/bulletin/{idBulletin}/situation")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> situation(@PathVariable int idBulletin) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> b = bulletin(c, idBulletin, false);
            eligible(b);
            BigDecimal net = (BigDecimal) b.get("net_a_payer");
            BigDecimal reste = due(c, b);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("idBulletin", idBulletin);
            result.put("idPeriodePaie", b.get("id_periode_paie"));
            result.put("referenceBulletin", b.get("reference_bulletin"));
            result.put("nomPrenom", b.get("nom_prenom"));
            result.put("matricule", b.get("matricule"));
            result.put("modePaiement", b.get("mode_paiement"));
            result.put("netAPayer", net);
            result.put("verse", net.subtract(reste));
            result.put("reste", reste);
            result.put("virementEnAttente", pending(c, idBulletin));
            result.put("inclusDansStc", includedInActiveStc(c, idBulletin));
            return result;
        }
    }

    @PostMapping(value = "/bulletin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> enregistrer(@RequestParam Integer idBulletin, @RequestParam BigDecimal montant,
            @RequestParam String mode, @RequestParam LocalDate date, @RequestParam String reference,
            @RequestParam(required = false) String commentaire, @RequestParam("file") MultipartFile file,
            Authentication auth) throws Exception {
        if (idBulletin == null || montant == null || montant.signum() <= 0)
            throw invalid("Bulletin, montant positif et date obligatoires.");
        validatePaymentDate(date);
        Justificatif piece = justificatif(file);
        mode = required(mode, "Mode").toUpperCase(Locale.ROOT);
        if (!Set.of("VIREMENT", "CHEQUE", "ESPECES", "MOBILE_MONEY").contains(mode)) throw invalid("Mode de règlement inconnu.");
        reference = required(reference, "Référence du règlement");
        final String modeFinal = mode;
        final String referenceFinal = reference;
        return transaction(c -> {
            Map<String, Object> b = bulletin(c, idBulletin, true);
            eligible(b);
            if (includedInActiveStc(c, idBulletin)) throw invalid("Ce bulletin est repris dans un STC actif. Enregistrez et affectez le versement depuis le dossier de sortie.");
            if (pending(c, idBulletin)) throw invalid("Un virement est en attente pour ce bulletin.");
            if (montant.compareTo(due(c, b)) > 0) throw invalid("Le règlement dépasse le solde restant.");
            long id = insertId(c, "INSERT INTO paie_bulletin_reglement(id_bulletin,montant,mode_reglement,date_reglement,reference_reglement,commentaire,nom_justificatif,mime_justificatif,contenu_justificatif,taille_justificatif,cree_par) VALUES (?,?,?,?,?,?,?,?,?,?,?)", idBulletin,montant,modeFinal,java.sql.Date.valueOf(date),referenceFinal,commentaire,piece.nom(),piece.mime(),piece.contenu(),piece.contenu().length,user(auth));
            return Map.of("id", id);
        });
    }

    @PostMapping("/bulletin/{idReglement}/annuler")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public void annuler(@PathVariable long idReglement, @RequestBody AnnulationRequest request, Authentication auth) throws Exception {
        String motif = required(request == null ? null : request.motif(), "Motif d'annulation");
        transaction(c -> {
            List<Map<String, Object>> rows = query(c, "SELECT id_bulletin,id_ligne_virement,id_stc_reglement FROM paie_bulletin_reglement WHERE id_reglement=? AND annule_at IS NULL", idReglement);
            if (rows.isEmpty()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Règlement actif introuvable.");
            bulletin(c, ((Number) rows.get(0).get("id_bulletin")).intValue(), true);
            if (rows.get(0).get("id_ligne_virement") != null) throw invalid("Un virement confirmé ne peut pas être annulé ici : contactez le contrôle financier.");
            if (rows.get(0).get("id_stc_reglement") != null) throw invalid("Cette affectation suit le versement STC source. Annulez celui-ci depuis le dossier de sortie.");
            update(c, "UPDATE paie_bulletin_reglement SET annule_at=CURRENT_TIMESTAMP,annule_par=?,motif_annulation=? WHERE id_reglement=? AND annule_at IS NULL", user(auth),motif,idReglement);
            return null;
        });
    }

    @PostMapping("/periode/{idPeriode}/lots")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> preparer(@PathVariable int idPeriode, @RequestBody List<Integer> ids, Authentication auth) throws Exception {
        if (ids == null || ids.isEmpty() || ids.size() != new HashSet<>(ids).size()) throw invalid("Sélectionnez des bulletins distincts.");
        return transaction(c -> {
            long lot = insertId(c, "INSERT INTO paie_bulletin_virement_lot(id_periode_paie,cree_par) VALUES (?,?)", idPeriode,user(auth));
            for (Integer id : ids.stream().sorted().toList()) {
                Map<String, Object> b = bulletin(c, id, true);
                eligible(b);
                if (!Objects.equals(((Number)b.get("id_periode_paie")).intValue(), idPeriode) || !"VIREMENT".equalsIgnoreCase(String.valueOf(b.get("mode_paiement"))))
                    throw invalid("Bulletin non éligible au lot de virements.");
                if (includedInActiveStc(c, id)) throw invalid("Un bulletin repris dans un STC actif ne peut pas rejoindre un lot mensuel.");
                String rib = required((String)b.get("banque_rib"), "RIB du bulletin " + id);
                String banque = required((String)b.get("banque_nom"), "Banque du bulletin " + id);
                if (pending(c, id)) throw invalid("Un virement est déjà en attente pour le bulletin " + id + ".");
                BigDecimal montant = due(c, b);
                if (montant.signum() <= 0) throw invalid("Le bulletin " + id + " est déjà réglé.");
                insertId(c, "INSERT INTO paie_bulletin_virement_ligne(id_lot,id_bulletin,montant,beneficiaire,banque,rib) VALUES (?,?,?,?,?,?)",lot,id,montant,b.get("nom_prenom"),banque,rib);
            }
            return Map.of("idLot",lot);
        });
    }

    @GetMapping("/periode/{idPeriode}/lots")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public List<Map<String, Object>> lots(@PathVariable int idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return query(c, "SELECT l.id_lot AS id,l.statut,l.cree_at AS \"creeAt\",l.transmis_at AS \"transmisAt\",COUNT(i.id_ligne) AS lignes,COALESCE(SUM(i.montant),0) AS montant FROM paie_bulletin_virement_lot l JOIN paie_bulletin_virement_ligne i ON i.id_lot=l.id_lot WHERE l.id_periode_paie=? GROUP BY l.id_lot ORDER BY l.id_lot DESC",idPeriode);
        }
    }
    @GetMapping("/lots/{idLot}/lignes")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public List<Map<String, Object>> lignes(@PathVariable long idLot) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return query(c,"SELECT l.id_ligne AS id,l.id_bulletin AS \"idBulletin\",l.montant,l.beneficiaire,l.banque,l.rib,l.statut,l.reference_banque AS reference,l.date_execution AS \"dateExecution\",l.motif_rejet AS \"motifRejet\",b.reference_bulletin AS \"referenceBulletin\" FROM paie_bulletin_virement_ligne l JOIN bulletin_paie b ON b.id_bulletin=l.id_bulletin WHERE l.id_lot=? ORDER BY l.id_ligne",idLot);
        }
    }
    @GetMapping("/lots/{idLot}/csv")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> csv(@PathVariable long idLot) throws Exception {
        List<Map<String,Object>> rows;
        try (Connection c = dataSource.getConnection()) {
            List<Map<String,Object>> lot = query(c,
                    "SELECT statut FROM paie_bulletin_virement_lot WHERE id_lot=?", idLot);
            if (lot.isEmpty()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,"Lot introuvable.");
            if ("ANNULE".equals(lot.get(0).get("statut"))) throw invalid("Un lot annulé ne peut plus être exporté.");
            rows = query(c, "SELECT b.reference_bulletin AS \"referenceBulletin\",l.beneficiaire,l.banque,l.rib,l.montant "
                    + "FROM paie_bulletin_virement_ligne l JOIN bulletin_paie b ON b.id_bulletin=l.id_bulletin "
                    + "WHERE l.id_lot=? AND l.statut='EN_ATTENTE' ORDER BY l.id_ligne", idLot);
        }
        if (rows.isEmpty()) throw invalid("Ce lot ne contient plus de virement en attente.");
        StringBuilder csv = new StringBuilder("\ufeffReference;Beneficiaire;Banque;RIB;Montant MGA\n");
        for (Map<String,Object> r : rows) csv.append(csvCell(r.get("referenceBulletin"))).append(';')
                .append(csvCell(r.get("beneficiaire"))).append(';').append(csvCell(r.get("banque"))).append(';')
                .append(csvCell(r.get("rib"))).append(';').append(r.get("montant")).append('\n');
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"lot_virements_"+idLot+".csv\"").contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String csvCell(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    @PostMapping("/lots/{idLot}/transmettre")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public void transmettre(@PathVariable long idLot, Authentication auth) throws Exception {
        transaction(c -> {
            if (update(c,"UPDATE paie_bulletin_virement_lot SET statut='TRANSMIS',transmis_at=CURRENT_TIMESTAMP,transmis_par=? WHERE id_lot=? AND statut='PREPARE'",user(auth),idLot)!=1)
                throw invalid("Le lot est déjà transmis ou introuvable.");
            return null;
        });
    }
    @PostMapping("/lots/{idLot}/annuler")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public void annulerLot(@PathVariable long idLot) throws Exception {
        transaction(c -> {
            List<Map<String,Object>> found = query(c,"SELECT statut FROM paie_bulletin_virement_lot WHERE id_lot=? FOR UPDATE",idLot);
            if (found.isEmpty() || !"PREPARE".equals(found.get(0).get("statut")))
                throw invalid("Seul un lot non transmis peut être annulé.");
            update(c,"UPDATE paie_bulletin_virement_ligne SET statut='ANNULE' WHERE id_lot=? AND statut='EN_ATTENTE'",idLot);
            update(c,"UPDATE paie_bulletin_virement_lot SET statut='ANNULE' WHERE id_lot=?",idLot);
            return null;
        });
    }
    @PostMapping(value = "/lots/lignes/{idLigne}/confirmer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public void confirmer(@PathVariable long idLigne, @RequestParam LocalDate date, @RequestParam String reference,
            @RequestParam("file") MultipartFile file, Authentication auth) throws Exception {
        validatePaymentDate(date);
        Justificatif piece = justificatif(file);
        reference = required(reference,"Référence bancaire");
        final String referenceFinal = reference;
        transaction(c -> {
            List<Map<String,Object>> found = query(c,"SELECT l.id_bulletin,l.montant,lot.statut AS statut_lot,l.statut FROM paie_bulletin_virement_ligne l JOIN paie_bulletin_virement_lot lot ON lot.id_lot=l.id_lot WHERE l.id_ligne=?",idLigne);
            if (found.isEmpty()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,"Ligne de virement introuvable.");
            Map<String,Object> line = found.get(0);
            Map<String,Object> b = bulletin(c,((Number)line.get("id_bulletin")).intValue(),true);
            if (!"TRANSMIS".equals(line.get("statut_lot")) || !"EN_ATTENTE".equals(line.get("statut"))) throw invalid("Seule une ligne transmise en attente peut être confirmée.");
            if (((BigDecimal)line.get("montant")).compareTo(due(c,b))>0) throw invalid("Le solde du bulletin a changé depuis la préparation du lot.");
            insertId(c,"INSERT INTO paie_bulletin_reglement(id_bulletin,id_ligne_virement,montant,mode_reglement,date_reglement,reference_reglement,nom_justificatif,mime_justificatif,contenu_justificatif,taille_justificatif,cree_par) VALUES (?,?,?,'VIREMENT',?,?,?,?,?,?,?)",b.get("id_bulletin"),idLigne,line.get("montant"),java.sql.Date.valueOf(date),referenceFinal,piece.nom(),piece.mime(),piece.contenu(),piece.contenu().length,user(auth));
            update(c,"UPDATE paie_bulletin_virement_ligne SET statut='CONFIRME',reference_banque=?,date_execution=?,traite_at=CURRENT_TIMESTAMP,traite_par=? WHERE id_ligne=?",referenceFinal,java.sql.Date.valueOf(date),user(auth),idLigne);
            return null;
        });
    }
    @PostMapping("/lots/lignes/{idLigne}/rejeter")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public void rejeter(@PathVariable long idLigne, @RequestBody DecisionRequest request, Authentication auth) throws Exception {
        String motif = required(request == null ? null : request.motif(),"Motif du rejet");
        transaction(c -> {
            List<Map<String,Object>> found = query(c,"SELECT id_bulletin FROM paie_bulletin_virement_ligne WHERE id_ligne=?",idLigne);
            if (found.isEmpty()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,"Ligne introuvable.");
            bulletin(c,((Number)found.get(0).get("id_bulletin")).intValue(),true);
            if (update(c,"UPDATE paie_bulletin_virement_ligne SET statut='REJETE',motif_rejet=?,traite_at=CURRENT_TIMESTAMP,traite_par=? WHERE id_ligne=? AND statut='EN_ATTENTE' AND id_lot IN (SELECT id_lot FROM paie_bulletin_virement_lot WHERE statut='TRANSMIS')",motif,user(auth),idLigne)!=1)
                throw invalid("Seule une ligne transmise en attente peut être rejetée.");
            return null;
        });
    }
}
