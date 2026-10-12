package mg.galaxam.sirh.paie.api;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Contrats et factures des indépendants, hors paie et sans retenue ISI générale. */
@RestController
@RequestMapping({"/api/v1/prestataires", "/v1/prestataires", "/prestataires"})
public class PrestataireController {
    private final DataSource dataSource;

    @Value("${app.upload.prestataire-dir:uploads/prestataires}")
    private String documentDirectory;

    public PrestataireController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record PrestataireRequest(String nom, String nif, String stat, String adresse, String email) {}
    public record ContratRequest(Integer idPrestataire, String reference, String objet,
            LocalDate dateDebut, LocalDate dateFin, BigDecimal tarifConvenu) {}
    public record FactureRequest(Integer idContratPrestataire, String reference,
            LocalDate dateFacture, BigDecimal montant) {}
    public record PaiementRequest(LocalDate datePaiement, String referencePaiement) {}
    public record AnnulationRequest(String motif) {}

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public List<Map<String, Object>> listerPrestataires() throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_prestataire, nom, nif, stat, adresse, email, est_actif FROM prestataire_independant ORDER BY nom");
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> result = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String key : List.of("id_prestataire", "nom", "nif", "stat", "adresse", "email", "est_actif"))
                    row.put(key, rs.getObject(key));
                result.add(row);
            }
            return result;
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public Map<String, Integer> creerPrestataire(@RequestBody PrestataireRequest req) throws Exception {
        if (req == null || blank(req.nom()) || blank(req.nif()))
            throw new IllegalArgumentException("Le nom et le NIF du prestataire sont obligatoires.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO prestataire_independant (nom,nif,stat,adresse,email) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, req.nom().trim()); ps.setString(2, req.nif().trim());
            ps.setString(3, trim(req.stat())); ps.setString(4, trim(req.adresse()));
            ps.setString(5, trim(req.email())); ps.executeUpdate();
            return Map.of("idPrestataire", generatedId(ps));
        }
    }

    @GetMapping("/contrats")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public List<Map<String, Object>> listerContrats() throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT pc.id_contrat_prestataire,pc.id_prestataire,pi.nom AS prestataire,pc.reference,pc.objet,pc.date_debut,pc.date_fin,pc.tarif_convenu,pc.chemin_piece "
                + "FROM prestataire_contrat pc JOIN prestataire_independant pi ON pi.id_prestataire=pc.id_prestataire ORDER BY pc.id_contrat_prestataire DESC");
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> result = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String key : List.of("id_contrat_prestataire", "id_prestataire", "prestataire", "reference", "objet", "date_debut", "date_fin", "tarif_convenu", "chemin_piece"))
                    row.put(key, rs.getObject(key));
                result.add(row);
            }
            return result;
        }
    }

    @PostMapping("/contrats")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public Map<String, Integer> creerContrat(@RequestBody ContratRequest req) throws Exception {
        if (req == null || req.idPrestataire() == null || blank(req.reference()) || blank(req.objet())
                || req.dateDebut() == null || req.dateFin() != null && req.dateFin().isBefore(req.dateDebut())
                || req.tarifConvenu() != null && req.tarifConvenu().signum() < 0)
            throw new IllegalArgumentException("Le prestataire, la référence, l'objet et les dates du contrat sont obligatoires et cohérents.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO prestataire_contrat (id_prestataire,reference,objet,date_debut,date_fin,tarif_convenu) "
                + "SELECT id_prestataire,?,?,?,?,? FROM prestataire_independant WHERE id_prestataire=? AND est_actif=TRUE",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, req.reference().trim()); ps.setString(2, req.objet().trim());
            ps.setDate(3, Date.valueOf(req.dateDebut()));
            ps.setDate(4, req.dateFin() == null ? null : Date.valueOf(req.dateFin()));
            ps.setBigDecimal(5, req.tarifConvenu()); ps.setInt(6, req.idPrestataire());
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Prestataire absent ou inactif.");
            return Map.of("idContratPrestataire", generatedId(ps));
        }
    }

    @PostMapping(value = "/contrats/{id}/piece", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> joindreContrat(@PathVariable int id, @RequestParam("file") MultipartFile file) throws Exception {
        byte[] bytes = pdfBytes(file);
        String reference;
        LocalDate dateDebut;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT reference,date_debut FROM prestataire_contrat WHERE id_contrat_prestataire=? AND chemin_piece IS NULL")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Contrat absent ou pièce déjà jointe.");
                reference = rs.getString("reference");
                dateDebut = rs.getDate("date_debut") == null ? null : rs.getDate("date_debut").toLocalDate();
            }
        }
        Path directory = Path.of(documentDirectory).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String filename = nomFichierStockage("CONTRAT_PRESTATION", reference, dateDebut);
        Path path = directory.resolve(filename);
        Files.write(path, bytes);
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE prestataire_contrat SET chemin_piece=? WHERE id_contrat_prestataire=? AND chemin_piece IS NULL")) {
            ps.setString(1, filename); ps.setInt(2, id);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Contrat absent ou pièce déjà jointe.");
        } catch (Exception e) {
            Files.deleteIfExists(path);
            throw e;
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/contrats/{id}/piece")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> telechargerContrat(@PathVariable int id) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT chemin_piece,reference,date_debut FROM prestataire_contrat WHERE id_contrat_prestataire=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getString(1) == null) return ResponseEntity.notFound().build();
                LocalDate dateDebut = rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate();
                String downloadName = nomFichierTelechargement("contrat-prestation", rs.getString(2), dateDebut);
                return envoyerPdf(rs.getString(1), downloadName);
            }
        }
    }

    @GetMapping("/factures")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public List<Map<String, Object>> listerFactures() throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT pf.id_facture_prestataire,pf.id_contrat_prestataire,pi.nom AS prestataire,pc.reference AS contrat_reference,"
                + "pf.reference,pf.date_facture,pf.montant,pf.statut,pf.chemin_piece,pf.date_validation,pf.date_paiement,pf.reference_paiement,pf.motif_annulation,pf.date_annulation "
                + "FROM prestataire_facture pf JOIN prestataire_contrat pc ON pc.id_contrat_prestataire=pf.id_contrat_prestataire "
                + "JOIN prestataire_independant pi ON pi.id_prestataire=pc.id_prestataire ORDER BY pf.id_facture_prestataire DESC");
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> result = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String key : List.of("id_facture_prestataire", "id_contrat_prestataire", "prestataire", "contrat_reference", "reference", "date_facture", "montant", "statut", "chemin_piece", "date_validation", "date_paiement", "reference_paiement", "motif_annulation", "date_annulation"))
                    row.put(key, rs.getObject(key));
                result.add(row);
            }
            return result;
        }
    }

    @PostMapping("/factures")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public Map<String, Integer> creerFacture(@RequestBody FactureRequest req) throws Exception {
        if (req == null || req.idContratPrestataire() == null || blank(req.reference())
                || req.dateFacture() == null || req.montant() == null || req.montant().signum() <= 0)
            throw new IllegalArgumentException("Contrat, référence, date et montant positif sont requis.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO prestataire_facture (id_contrat_prestataire,reference,date_facture,montant) "
                + "SELECT pc.id_contrat_prestataire,?,?,? FROM prestataire_contrat pc "
                + "JOIN prestataire_independant pi ON pi.id_prestataire=pc.id_prestataire "
                + "WHERE pc.id_contrat_prestataire=? AND pi.est_actif=TRUE", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, req.reference().trim()); ps.setDate(2, Date.valueOf(req.dateFacture()));
            ps.setBigDecimal(3, req.montant()); ps.setInt(4, req.idContratPrestataire());
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Contrat absent ou prestataire inactif.");
            return Map.of("idFacturePrestataire", generatedId(ps));
        }
    }

    @PostMapping("/factures/{id}/annuler")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> annulerBrouillon(@PathVariable int id, @RequestBody AnnulationRequest req,
            Authentication auth) throws Exception {
        if (req == null || blank(req.motif()))
            throw new IllegalArgumentException("Le motif d'annulation est obligatoire.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE prestataire_facture SET statut='ANNULEE', motif_annulation=?, "
                + "annule_par=?, date_annulation=CURRENT_TIMESTAMP "
                + "WHERE id_facture_prestataire=? AND statut='BROUILLON'")) {
            ps.setString(1, req.motif().trim()); ps.setInt(2, userId(auth)); ps.setInt(3, id);
            if (ps.executeUpdate() != 1)
                throw new IllegalStateException("Seul un brouillon de facture peut être annulé.");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/factures/{id}/piece", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> joindreFacture(@PathVariable int id, @RequestParam("file") MultipartFile file) throws Exception {
        byte[] bytes = pdfBytes(file);
        String reference;
        LocalDate dateFacture;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT reference,date_facture FROM prestataire_facture WHERE id_facture_prestataire=? AND statut='BROUILLON' AND chemin_piece IS NULL")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Facture absente, déjà validée ou pièce déjà jointe.");
                reference = rs.getString("reference");
                dateFacture = rs.getDate("date_facture") == null ? null : rs.getDate("date_facture").toLocalDate();
            }
        }
        Path directory = Path.of(documentDirectory).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String filename = nomFichierStockage("FACTURE_PRESTATAIRE", reference, dateFacture);
        Path path = directory.resolve(filename);
        Files.write(path, bytes);
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE prestataire_facture SET chemin_piece=? WHERE id_facture_prestataire=? AND statut='BROUILLON' AND chemin_piece IS NULL")) {
            ps.setString(1, filename); ps.setInt(2, id);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Facture absente, déjà validée ou pièce déjà jointe.");
        } catch (Exception e) {
            Files.deleteIfExists(path);
            throw e;
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/factures/{id}/piece")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> telechargerPiece(@PathVariable int id) throws Exception {
        String filename;
        String downloadName;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT chemin_piece,reference,date_facture FROM prestataire_facture WHERE id_facture_prestataire=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getString(1) == null) return ResponseEntity.notFound().build();
                filename = rs.getString(1);
                LocalDate dateFacture = rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate();
                downloadName = nomFichierTelechargement("facture-prestataire", rs.getString(2), dateFacture);
            }
        }
        return envoyerPdf(filename, downloadName);
    }

    @PostMapping("/factures/{id}/valider")
    @PreAuthorize("hasAuthority('PAIE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerFacture(@PathVariable int id, Authentication auth) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE prestataire_facture SET statut='VALIDEE', valide_par=?, date_validation=CURRENT_TIMESTAMP "
                + "WHERE id_facture_prestataire=? AND statut='BROUILLON' AND chemin_piece IS NOT NULL "
                + "AND EXISTS (SELECT 1 FROM prestataire_contrat pc JOIN prestataire_independant pi "
                + "ON pi.id_prestataire=pc.id_prestataire WHERE pc.id_contrat_prestataire=prestataire_facture.id_contrat_prestataire "
                + "AND pc.chemin_piece IS NOT NULL AND pi.est_actif=TRUE AND pi.nif IS NOT NULL AND pi.nif<>'')")) {
            ps.setInt(1, userId(auth)); ps.setInt(2, id);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Validation impossible : contrôlez le NIF, le contrat PDF et la facture PDF.");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/factures/{id}/payer")
    @PreAuthorize("hasAuthority('PAIE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> payerFacture(@PathVariable int id, @RequestBody PaiementRequest req, Authentication auth) throws Exception {
        if (req == null || req.datePaiement() == null || blank(req.referencePaiement())
                || req.datePaiement().isAfter(LocalDate.now(ZoneId.of("Indian/Antananarivo"))))
            throw new IllegalArgumentException("Date réelle et référence du paiement obligatoires.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE prestataire_facture SET statut='PAYEE', date_paiement=?, reference_paiement=?, paye_par=? "
                + "WHERE id_facture_prestataire=? AND statut='VALIDEE' AND date_facture<=?")) {
            ps.setDate(1, Date.valueOf(req.datePaiement())); ps.setString(2, req.referencePaiement().trim());
            ps.setInt(3, userId(auth)); ps.setInt(4, id); ps.setDate(5, Date.valueOf(req.datePaiement()));
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Facture absente, non validée ou paiement antérieur à la facture.");
        }
        return ResponseEntity.noContent().build();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String nomFichierTelechargement(String type, String reference, LocalDate date) {
        String normalizedReference = Normalizer.normalize(reference == null ? "" : reference.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalizedReference.isBlank()) normalizedReference = "sans-reference";
        if (normalizedReference.length() > 80) normalizedReference = normalizedReference.substring(0, 80).replaceAll("-$", "");
        return type + "-" + normalizedReference + (date == null ? "" : "-" + date) + ".pdf";
    }
    private static String nomFichierStockage(String type, String reference, LocalDate date) {
        String normalizedReference = Normalizer.normalize(reference == null ? "" : reference.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (normalizedReference.isBlank()) normalizedReference = "SANS_REFERENCE";
        if (normalizedReference.length() > 60) normalizedReference = normalizedReference.substring(0, 60).replaceAll("_$", "");
        String datePart = (date == null ? LocalDate.now(ZoneId.of("Indian/Antananarivo")) : date)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return type + "_" + normalizedReference + "_" + datePart + "_" + shortId + ".pdf";
    }
    private static byte[] pdfBytes(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 10_000_000)
            throw new IllegalArgumentException("Un PDF de 10 Mo maximum est requis.");
        byte[] bytes = file.getBytes();
        if (bytes.length < 4 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F')
            throw new IllegalArgumentException("La pièce jointe doit être un PDF.");
        return bytes;
    }
    private ResponseEntity<byte[]> envoyerPdf(String filename, String downloadName) throws Exception {
        boolean newConvention = filename.matches("[A-Z0-9_-]+_[0-9]{8}_[a-f0-9]{8}\\.pdf");
        boolean legacyUuid = filename.matches("[a-f0-9-]{36}\\.pdf");
        if (!newConvention && !legacyUuid) throw new IllegalStateException("Référence de pièce invalide.");
        Path path = Path.of(documentDirectory).toAbsolutePath().normalize().resolve(filename);
        if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .body(Files.readAllBytes(path));
    }
    private static int generatedId(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) throw new IllegalStateException("Identifiant non généré.");
            return rs.getInt(1);
        }
    }
    private static int userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) return user.getIdUtilisateur();
        throw new IllegalStateException("Utilisateur authentifié requis.");
    }
}
