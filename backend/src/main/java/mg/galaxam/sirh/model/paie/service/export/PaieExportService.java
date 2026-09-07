package mg.galaxam.sirh.model.paie.service.export;

import mg.galaxam.sirh.model.paie.dto.export.DntsRowDto;
import mg.galaxam.sirh.model.paie.dto.export.SafiRowDto;
import mg.galaxam.sirh.model.paie.dto.export.VirementRowDto;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * Service centralisant l'ensemble des exports légaux et bancaires du module Paie.
 */
@Service
public class PaieExportService {

    private final DataSource dataSource;
    private final SafiExportService safiExportService;
    private final SafiOdsExportService safiOdsExportService;
    private final EdiIsiExportService ediIsiExportService;
    private final DntsExportService dntsExportService;
    private final VirementBancaireExportService virementBancaireExportService;

    public PaieExportService(DataSource dataSource,
                             SafiExportService safiExportService,
                             SafiOdsExportService safiOdsExportService,
                             EdiIsiExportService ediIsiExportService,
                             DntsExportService dntsExportService,
                             VirementBancaireExportService virementBancaireExportService) {
        this.dataSource = dataSource;
        this.safiExportService = safiExportService;
        this.safiOdsExportService = safiOdsExportService;
        this.ediIsiExportService = ediIsiExportService;
        this.dntsExportService = dntsExportService;
        this.virementBancaireExportService = virementBancaireExportService;
    }

    public byte[] exporterSafiOds(Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return safiOdsExportService.genererSafiOds(idPeriode, c);
        }
    }

    public List<SafiRowDto> obtenirDonneesSafi(Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return safiExportService.genererDonneesSafi(idPeriode, c);
        }
    }

    public String exporterSafiCsv(Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return safiExportService.exporterSafiCsv(idPeriode, c);
        }
    }

    public String exporterEdiXml(Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return ediIsiExportService.exporterEdiXml(idPeriode, c);
        }
    }

    public List<DntsRowDto> obtenirDonneesDnts(Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return dntsExportService.genererDonneesDnts(idPeriode, c);
        }
    }

    public String exporterDntsCsv(Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return dntsExportService.exporterDntsCsv(idPeriode, c);
        }
    }

    public List<VirementRowDto> obtenirDonneesVirement(Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return virementBancaireExportService.genererDonneesVirement(idPeriode, c);
        }
    }

    public String exporterVirementCsv(Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return virementBancaireExportService.exporterVirementCsv(idPeriode, c);
        }
    }
}
