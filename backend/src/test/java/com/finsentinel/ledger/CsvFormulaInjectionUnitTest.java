package com.finsentinel.ledger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvFormulaInjectionUnitTest {

    @Test
    void neutralizesDangerousSpreadsheetFormulaPrefixes() {
        assertThat(CsvImportExportService.sanitizeCsvCell("=CMD|' /C calc'!A0")).startsWith("'");
        assertThat(CsvImportExportService.sanitizeCsvCell("+SUM(A1:A2)")).startsWith("'");
        assertThat(CsvImportExportService.sanitizeCsvCell("-100+200")).startsWith("'");
        assertThat(CsvImportExportService.sanitizeCsvCell("@SUM(1,2)")).startsWith("'");
        assertThat(CsvImportExportService.sanitizeCsvCell("BigBasket Groceries")).isEqualTo("BigBasket Groceries");
    }
}
