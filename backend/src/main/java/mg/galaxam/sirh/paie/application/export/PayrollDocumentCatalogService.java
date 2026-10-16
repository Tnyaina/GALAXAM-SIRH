package mg.galaxam.sirh.paie.application.export;

import java.sql.Connection;
import java.util.ArrayList;
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
        List<Document> documents = List.of(
                document("bulletins", "Bulletins individuels", "Un bulletin par salarié, depuis la campagne.", "PDF · Excel", ready, label, capability.reasonCodes()),
                document("grand-livre", "Grand livre de paie", "État nominatif et détail des rubriques.", "Excel (.xlsx)", ready, label, capability.reasonCodes()),
                document("irsa", "État IRSA", "Modèle fiscal à contrôler par la DAF.", "Excel (.xls)", ready, label, capability.reasonCodes()),
                document("social", "État social mensuel", "Cotisations par organisme et salarié.", "Excel (.xlsx)", ready, label, capability.reasonCodes()),
                document("safi-csv", "État 301 SAFI", "Données fiscales nominatives à contrôler.", "CSV", ready, label, capability.reasonCodes()),
                document("safi-xls", "État 301 SAFI", "Déclaration IRSA au format Excel.", "Excel (.xls)", ready, label, capability.reasonCodes()),
                document("dnts-csv", "DNTS CNaPS / FMFP", "Données sociales nominatives à contrôler.", "CSV", ready, label, capability.reasonCodes()),
                document("virement-csv", "Ordre de virement", "Montants nets et coordonnées bancaires à vérifier.", "CSV", ready, label, capability.reasonCodes()));
        return new Catalog("mensuel", ready ? 1 : 0, 1, documents);
    }

    public Catalog quarterly(int year, int quarter) throws Exception {
        List<Document> documents = new ArrayList<>();
        String[] ids = {"consolidation", "cnaps", "fmfp", "ostie", "omsi"};
        String[] names = {"Consolidation sociale", "CNaPS · DNWEB", "CNaPS / FMFP", "OSTIE", "OMSI"};
        String[] details = {"Synthèse et détail des trois mois.", "Modèle nominatif sur trois mois.",
                "Modèle combiné et feuilles mensuelles.", "Modèle nominatif trimestriel.", "Modèle nominatif trimestriel."};
        int[] counts = null;
        String failure = null;
        try (Connection connection = dataSource.getConnection()) {
            var data = quarterly.load(year, quarter, connection);
            counts = new int[] {data.people().size(), data.cnaps().size(),
                    (int) data.people().stream().filter(p -> p.hasCnaps() || p.hasFmfp()).count(),
                    data.ostie().size(), data.omsi().size()};
        } catch (IllegalStateException error) {
            failure = error.getMessage();
        }
        for (int index = 0; index < ids.length; index++) {
            boolean available = counts != null && counts[index] > 0;
            String label = failure != null ? failure : available ? "À contrôler par la DAF" : "Aucune cotisation à déclarer";
            documents.add(document(ids[index], names[index], details[index], index == 2 ? "Excel (.xls)" : "Excel (.xlsx)",
                    available, label, failure != null ? List.of("PAYROLL_QUARTER_NOT_READY")
                            : available ? List.of() : List.of("NO_DECLARABLE_DATA")));
        }
        return new Catalog("trimestriel", counts == null ? 0 : 3, 3, documents);
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
