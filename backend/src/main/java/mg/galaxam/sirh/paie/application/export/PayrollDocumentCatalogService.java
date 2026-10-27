package mg.galaxam.sirh.paie.application.export;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.dto.ActionCapability;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import org.springframework.stereotype.Service;

/** Disponibilité des documents calculée par les mêmes propriétaires que leurs commandes. */
@Service
public class PayrollDocumentCatalogService {
    public record Document(String id, String name, String detail, String format,
                           boolean available, String label, List<String> reasonCodes) {}
    public record Catalog(String scope, int coveredMonths, int totalMonths, List<Document> documents) {}

    private final DataSource dataSource;
    private final PayrollPeriodWorkflowService workflow;
    private final QuarterlySocialDataService quarterly;
    private final AnnualPayrollExportService annual;

    public PayrollDocumentCatalogService(DataSource dataSource, PayrollPeriodWorkflowService workflow,
            QuarterlySocialDataService quarterly, AnnualPayrollExportService annual) {
        this.dataSource = dataSource;
        this.workflow = workflow;
        this.quarterly = quarterly;
        this.annual = annual;
    }

    public Catalog monthly(Integer periodId, PayrollPeriodUserContext user) throws Exception {
        ActionCapability capability = periodId == null ? ActionCapability.denied("PAYROLL_PERIOD_CONTEXT_REQUIRED")
                : workflow.describeContext(periodId, null, user).availableActions().getOrDefault(
                        "GENERATE_OUTPUT", ActionCapability.denied("PAYROLL_OUTPUT_UNAVAILABLE"));
        boolean ready = capability.allowed();
        String label = ready ? "Disponible" : reason(capability.reasonCodes());
        boolean irsaReady = ready;
        String irsaLabel = label;
        List<String> irsaReasons = capability.reasonCodes();
        if (ready) {
            try (Connection connection = dataSource.getConnection()) {
                String sql = "SELECT EXISTS (SELECT 1 FROM bulletin_paie WHERE id_periode_paie = ? "
                        + "AND UPPER(regime_intervenant) NOT IN ('CONSULTANT','TEMPORAIRE','ISI_5') "
                        + "AND COALESCE(retenue_isi_5, 0) <= 0)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, periodId);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        irsaReady = result.getBoolean(1);
                    }
                }
            }
            if (!irsaReady) {
                irsaLabel = "Aucun salarié concerné par l'IRSA";
                irsaReasons = List.of("NO_IRSA_EMPLOYEE");
            }
        }
        List<Document> documents = List.of(
                document("bulletins", "Bulletins individuels", "Un bulletin par salarié, depuis la campagne.", "PDF · Excel", ready, label, capability.reasonCodes()),
                document("grand-livre", "Grand livre de paie", "État nominatif et détail des rubriques.", "Excel (.xlsx)", ready, label, capability.reasonCodes()),
                document("irsa", "État IRSA", "Contrôle fiscal nominatif à vérifier par la DAF.", "Excel (.xls)", irsaReady, irsaLabel, irsaReasons),
                document("social", "État social mensuel", "Cotisations par organisme et salarié.", "Excel (.xlsx)", ready, label, capability.reasonCodes()));
        return new Catalog("mensuel", ready ? 1 : 0, 1, documents);
    }

    public Catalog quarterly(int year, int quarter) throws Exception {
        int count = 0;
        String failure = null;
        try (Connection connection = dataSource.getConnection()) {
            var byFiliale = quarterly.loadAll(year, quarter, connection);
            count = byFiliale.values().stream().mapToInt(d -> d.people().size()).sum();
        } catch (IllegalStateException error) {
            failure = error.getMessage();
        }
        boolean available = count > 0 && failure == null;
        String label = failure != null ? failure : available ? "Disponible" : "Aucune donnée sociale";
        Document document = document("consolidation", "Synthèse sociale trimestrielle",
                "Synthèse et détail des trois mois pour contrôle interne.", "Excel (.xlsx)", available, label,
                failure != null ? List.of("PAYROLL_QUARTER_NOT_READY")
                        : available ? List.of() : List.of("NO_SOCIAL_DATA"));
        return new Catalog("trimestriel", count == 0 ? 0 : 3, 3, List.of(document));
    }

    public Catalog annual(int year) throws Exception {
        int months = 0;
        String failure = null;
        try (Connection connection = dataSource.getConnection()) {
            months = annual.coverage(year, connection).validatedMonths();
        } catch (IllegalStateException error) {
            failure = error.getMessage();
        }
        boolean available = failure == null;
        String label = available ? months == 12 ? "Année complète" : "Partiel · " + months + "/12 mois" : failure;
        return new Catalog("annuel", months, 12, List.of(document("recap-paie", "Récapitulatif annuel de paie",
                "Cumuls par salarié et détail mensuel des bulletins validés.", "Excel (.xlsx)", available, label,
                available ? List.of() : List.of("PAYROLL_YEAR_NOT_READY"))));
    }

    private static Document document(String id, String name, String detail, String format,
            boolean available, String label, List<String> reasons) {
        return new Document(id, name, detail, format, available, label, reasons);
    }

    private static String reason(List<String> codes) {
        if (codes.contains("PAYROLL_PERIOD_CONTEXT_REQUIRED")) return "Choisir une période";
        if (codes.contains("PAYROLL_CALCULATION_STALE")) return "Recalcul requis";
        if (codes.contains("PAYROLL_POPULATION_INCOMPLETE")) return "Bulletins incomplets";
        if (codes.contains("FORBIDDEN")) return "Accès non autorisé";
        return "Après validation de la paie";
    }
}
