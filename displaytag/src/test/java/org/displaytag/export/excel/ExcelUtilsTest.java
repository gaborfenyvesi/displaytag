/*
 * SPDX-License-Identifier: MIT
 * See LICENSE file for details.
 *
 * Copyright 2002-2026 Fabrizio Giustina, the Displaytag team
 */
package org.displaytag.export.excel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExcelUtils}.
 */
class ExcelUtilsTest {

    /**
     * Values starting with a formula character must be prefixed with a quote.
     */
    @Test
    void beforeWriteXlsEscapesFormulaPrefixes() {
        Assertions.assertEquals("'=1+1", ExcelUtils.beforeWriteXls("=1+1"));
        Assertions.assertEquals("'+1", ExcelUtils.beforeWriteXls("+1"));
        Assertions.assertEquals("'-1", ExcelUtils.beforeWriteXls("-1"));
        Assertions.assertEquals("'@SUM(A1)", ExcelUtils.beforeWriteXls("@SUM(A1)"));
        Assertions.assertEquals("'*a", ExcelUtils.beforeWriteXls("*a"));
    }

    /**
     * Other values must be returned unchanged.
     */
    @Test
    void beforeWriteXlsKeepsOtherValues() {
        Assertions.assertNull(ExcelUtils.beforeWriteXls(null));
        Assertions.assertEquals("", ExcelUtils.beforeWriteXls(""));
        Assertions.assertEquals("abc", ExcelUtils.beforeWriteXls("abc"));
    }

}
