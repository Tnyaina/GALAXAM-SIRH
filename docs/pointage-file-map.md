# Carte des fichiers du pointage

Inventaire du code directement utilisé par les écrans de pointage et de leurs principales intégrations. Les captures reçues montrent le journal de la fiche employé, la saisie collective, la saisie par collaborateur, le suivi et le contrôle. Elles ne montrent pas toutes les vues annexes (aperçu d’import, Gantt, récapitulatifs, configuration).

## Écrans et routes

- [Router.jsx](../frontend/src/routes/Router.jsx)
- [ArbitrageDafPage.jsx](../frontend/src/features/attendance/pages/ArbitrageDafPage.jsx)
- [GestionPointagesPage.jsx](../frontend/src/features/attendance/pages/GestionPointagesPage.jsx)
- [SaisiePeriodeEmployePage.jsx](../frontend/src/features/attendance/pages/SaisiePeriodeEmployePage.jsx)
- [SaisieQuotidiennePage.jsx](../frontend/src/features/attendance/pages/SaisieQuotidiennePage.jsx)
- [SuiviAnomaliesPage.jsx](../frontend/src/features/attendance/pages/SuiviAnomaliesPage.jsx)
- [EmployeFicheDetailPage.jsx](../frontend/src/features/employees/pages/EmployeFicheDetailPage.jsx)
- [PointageTab.jsx](../frontend/src/features/employees/components/tabs/PointageTab.jsx)

## Composants du pointage

- [WeeklyPointageCalculationTable.jsx](../frontend/src/features/attendance/components/WeeklyPointageCalculationTable.jsx)
- [PointageAllowanceSelector.jsx](../frontend/src/features/attendance/components/controls/PointageAllowanceSelector.jsx)
- [PointageToolbar.jsx](../frontend/src/features/attendance/components/controls/PointageToolbar.jsx)
- [StatusPickerPopover.jsx](../frontend/src/features/attendance/components/controls/StatusPickerPopover.jsx)
- [AnomalieRegularisationModal.jsx](../frontend/src/features/attendance/components/modals/AnomalieRegularisationModal.jsx)
- [DafAjustementPointageModal.jsx](../frontend/src/features/attendance/components/modals/DafAjustementPointageModal.jsx)
- [PeriodeRecapModal.jsx](../frontend/src/features/attendance/components/modals/PeriodeRecapModal.jsx)
- [PointageDetailHebdoModal.jsx](../frontend/src/features/attendance/components/modals/PointageDetailHebdoModal.jsx)
- [PointageRecapModal.jsx](../frontend/src/features/attendance/components/modals/PointageRecapModal.jsx)
- [PointageSoldePeriodeModal.jsx](../frontend/src/features/attendance/components/modals/PointageSoldePeriodeModal.jsx)
- [PeriodeEmployeeEmptyState.jsx](../frontend/src/features/attendance/components/views/PeriodeEmployeeEmptyState.jsx)
- [PeriodeEmployeeHeader.jsx](../frontend/src/features/attendance/components/views/PeriodeEmployeeHeader.jsx)
- [PeriodeEmployeeImportPreview.jsx](../frontend/src/features/attendance/components/views/PeriodeEmployeeImportPreview.jsx)
- [PeriodeEmployeeTable.jsx](../frontend/src/features/attendance/components/views/PeriodeEmployeeTable.jsx)
- [PointageGanttView.jsx](../frontend/src/features/attendance/components/views/PointageGanttView.jsx)
- [PointageGridTable.jsx](../frontend/src/features/attendance/components/views/PointageGridTable.jsx)

## API, hooks et règles front

- [attendanceApi.js](../frontend/src/features/attendance/api/attendanceApi.js)
- [useDerogationIndemnites.js](../frontend/src/features/attendance/hooks/useDerogationIndemnites.js)
- [usePeriodePaie.js](../frontend/src/features/attendance/hooks/usePeriodePaie.js)
- [usePointageAnomalies.js](../frontend/src/features/attendance/hooks/usePointageAnomalies.js)
- [usePointageBatchPreview.js](../frontend/src/features/attendance/hooks/usePointageBatchPreview.js)
- [usePointagePeriodeEmploye.js](../frontend/src/features/attendance/hooks/usePointagePeriodeEmploye.js)
- [usePointageSaisie.js](../frontend/src/features/attendance/hooks/usePointageSaisie.js)
- [attendanceStatus.js](../frontend/src/features/attendance/utils/attendanceStatus.js)
- [pointageBatchPayload.js](../frontend/src/features/attendance/utils/pointageBatchPayload.js)
- [pointageDayContext.js](../frontend/src/features/attendance/utils/pointageDayContext.js)
- [pointageDayFilter.js](../frontend/src/features/attendance/utils/pointageDayFilter.js)
- [pointageImport.js](../frontend/src/features/attendance/utils/pointageImport.js)
- [pointageRow.js](../frontend/src/features/attendance/utils/pointageRow.js)
- [attendanceDisplayUtils.jsx](../frontend/src/features/attendance/utils/attendanceDisplayUtils.jsx)
- [ActionMenu.jsx](../frontend/src/components/ui/overlays/ActionMenu.jsx)

## API et DTO backend

- [PointageController.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/PointageController.java)
- [PointagePeriodeController.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/PointagePeriodeController.java)
- [FichePointageEmployeDTO.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/dto/FichePointageEmployeDTO.java)
- [PeriodePaieDTO.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/dto/PeriodePaieDTO.java)
- [PointageAnomalieDTO.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/dto/PointageAnomalieDTO.java)
- [PointageJourDTO.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/dto/PointageJourDTO.java)
- [PointageSyntheseMensuelleDTO.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/dto/PointageSyntheseMensuelleDTO.java)
- [PointageTronconSemaineDTO.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/dto/PointageTronconSemaineDTO.java)
- [SaisiePointageJourDTO.java](../backend/src/main/java/mg/galaxam/sirh/pointage/api/dto/SaisiePointageJourDTO.java)

## Services backend

- [AttendanceStatusPolicy.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/AttendanceStatusPolicy.java)
- [PointageAjustementPeriodeService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageAjustementPeriodeService.java)
- [PointageAnomalieService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageAnomalieService.java)
- [PointageDocumentData.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageDocumentData.java)
- [PointageDocumentService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageDocumentService.java)
- [PointageEmployeService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageEmployeService.java)
- [PointageExcelExportService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageExcelExportService.java)
- [PointageImportService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageImportService.java)
- [PointagePdfExportService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointagePdfExportService.java)
- [PointageService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageService.java)
- [PointageSyntheseService.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageSyntheseService.java)
- [PointageTemporalConfigurationGuard.java](../backend/src/main/java/mg/galaxam/sirh/pointage/application/PointageTemporalConfigurationGuard.java)

## Calcul et données backend

- [AttendanceEngine.java](../backend/src/main/java/mg/galaxam/sirh/pointage/domain/calculation/AttendanceEngine.java)
- [PointageAllowancePolicy.java](../backend/src/main/java/mg/galaxam/sirh/pointage/domain/calculation/PointageAllowancePolicy.java)
- [QualifiedTimeBucket.java](../backend/src/main/java/mg/galaxam/sirh/pointage/domain/calculation/QualifiedTimeBucket.java)
- [TemporalAttendanceEngine.java](../backend/src/main/java/mg/galaxam/sirh/pointage/domain/calculation/TemporalAttendanceEngine.java)
- [CalendrierOuvrableEngine.java](../backend/src/main/java/mg/galaxam/sirh/pointage/domain/engine/CalendrierOuvrableEngine.java)
- [EmployeIndemniteDerogation.java](../backend/src/main/java/mg/galaxam/sirh/pointage/persistence/EmployeIndemniteDerogation.java)

## Intégrations proches

- [CalendrierFerieTab.jsx](../frontend/src/features/administration/components/payroll/CalendrierFerieTab.jsx)
- [IndemnitesSitesTab.jsx](../frontend/src/features/administration/components/payroll/IndemnitesSitesTab.jsx)
- [ParametresGlobauxTab.jsx](../frontend/src/features/administration/components/payroll/ParametresGlobauxTab.jsx)
- [pointageParametreService.js](../frontend/src/features/administration/api/pointageParametreService.js)
- [DemandeDetailsModal.jsx](../frontend/src/features/leave/components/DemandeDetailsModal.jsx)
- [CongeService.java](../backend/src/main/java/mg/galaxam/sirh/conge/application/CongeService.java)
- [PayrollAttendanceInput.java](../backend/src/main/java/mg/galaxam/sirh/paie/domain/dto/PayrollAttendanceInput.java)
## Tests ciblés

- [AnomalieRegularisationKeyboard.test.jsx](../frontend/src/features/attendance/__tests__/AnomalieRegularisationKeyboard.test.jsx)
- [ArbitrageSilentUpdate.test.jsx](../frontend/src/features/attendance/__tests__/ArbitrageSilentUpdate.test.jsx)
- [AttendanceSheetCache.test.js](../frontend/src/features/attendance/__tests__/AttendanceSheetCache.test.js)
- [AttendanceUxAccessibility.test.jsx](../frontend/src/features/attendance/__tests__/AttendanceUxAccessibility.test.jsx)
- [PeriodeEmployeeTableWorkflow.test.jsx](../frontend/src/features/attendance/__tests__/PeriodeEmployeeTableWorkflow.test.jsx)
- [PeriodeEmployeeToolsMenu.test.jsx](../frontend/src/features/attendance/__tests__/PeriodeEmployeeToolsMenu.test.jsx)
- [PointageBatchPayload.test.js](../frontend/src/features/attendance/__tests__/PointageBatchPayload.test.js)
- [PointageDayContext.test.js](../frontend/src/features/attendance/__tests__/PointageDayContext.test.js)
- [PointageDayFilter.test.js](../frontend/src/features/attendance/__tests__/PointageDayFilter.test.js)
- [PointageDocumentApi.test.js](../frontend/src/features/attendance/__tests__/PointageDocumentApi.test.js)
- [PointageEmployeeUi.test.jsx](../frontend/src/features/attendance/__tests__/PointageEmployeeUi.test.jsx)
- [PointageImportPreview.test.jsx](../frontend/src/features/attendance/__tests__/PointageImportPreview.test.jsx)
- [PointageInitialLoading.test.jsx](../frontend/src/features/attendance/__tests__/PointageInitialLoading.test.jsx)
- [PointagePreflightApi.test.js](../frontend/src/features/attendance/__tests__/PointagePreflightApi.test.js)
- [PointageSharedWorkflow.test.jsx](../frontend/src/features/attendance/__tests__/PointageSharedWorkflow.test.jsx)
- [PointageStatusResolution.test.js](../frontend/src/features/attendance/__tests__/PointageStatusResolution.test.js)
- [PointageUnenteredDay.test.js](../frontend/src/features/attendance/__tests__/PointageUnenteredDay.test.js)
- [WeeklyPointageCalculationTable.test.jsx](../frontend/src/features/attendance/__tests__/WeeklyPointageCalculationTable.test.jsx)
- [AttendanceEngineTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/AttendanceEngineTest.java)
- [PeriodBalanceTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/PeriodBalanceTest.java)
- [TemporalAttendanceEngineTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/TemporalAttendanceEngineTest.java)
- [PointageEmployeeCapabilitiesTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/api/PointageEmployeeCapabilitiesTest.java)
- [PointagePeriodOptionsTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/api/PointagePeriodOptionsTest.java)
- [PointagePeriodOverlapIntegrationTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/api/PointagePeriodOverlapIntegrationTest.java)
- [PointageComprehensiveFeaturesExportTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointageComprehensiveFeaturesExportTest.java)
- [PointageConfigurationResolutionTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointageConfigurationResolutionTest.java)
- [PointageDayStatusTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointageDayStatusTest.java)
- [PointageDocumentServiceTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointageDocumentServiceTest.java)
- [PointageExcelExportServiceTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointageExcelExportServiceTest.java)
- [PointageFixedMonthlySnapshotTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointageFixedMonthlySnapshotTest.java)
- [PointageGridAssignmentTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointageGridAssignmentTest.java)
- [PointagePdfExportServiceTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointagePdfExportServiceTest.java)
- [PointagePhase6IntegrationTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/application/PointagePhase6IntegrationTest.java)
- [PointageAllowancePolicyTest.java](../backend/src/test/java/mg/galaxam/sirh/pointage/domain/calculation/PointageAllowancePolicyTest.java)

