/*
 * SPDX-License-Identifier: MIT
 * See LICENSE file for details.
 *
 * Copyright 2002-2026 Fabrizio Giustina, the Displaytag team
 */
package org.displaytag.render;

import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.displaytag.export.BinaryExportView;
import org.displaytag.export.DefaultItextExportView;
import org.displaytag.export.DefaultPdfExportView;
import org.displaytag.export.DefaultRtfExportView;
import org.displaytag.model.Cell;
import org.displaytag.model.HeaderCell;
import org.displaytag.model.Row;
import org.displaytag.model.TableModel;
import org.displaytag.properties.MediaTypeEnum;
import org.displaytag.properties.TableProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the column widths and page orientation of the iText (PDF/RTF) exports.
 */
class ItextTableWriterTest {

    /** Tolerance for percentage comparisons. */
    private static final float DELTA = 0.001f;

    /**
     * 8 columns, 2 of them empty: the empty ones get 12.5% each, the others share 75%.
     */
    @Test
    void emptyColumnsGetAverageWidth() {
        final float[] widths = ItextTableWriter.computeColumnWidths(new float[] { 10, 0, 20, 30, 0, 40, 50, 60 });

        Assertions.assertEquals(12.5f, widths[1], DELTA);
        Assertions.assertEquals(12.5f, widths[4], DELTA);
        // 75% shared in proportion to 10..60 (total 210)
        Assertions.assertEquals(75f * 10 / 210, widths[0], DELTA);
        Assertions.assertEquals(75f * 60 / 210, widths[7], DELTA);
        Assertions.assertEquals(100f, sum(widths), DELTA);
    }

    /**
     * Columns under a quarter of the average width stay exactly at it; only the other columns are scaled down.
     */
    @Test
    void narrowColumnsStayAtMinimum() {
        final float[] widths = ItextTableWriter.computeColumnWidths(new float[] { 1000, 1, 1, 1, 1, 1, 0, 0 });

        // five columns fixed at 12.5 / 4 = 3.125; the rest (75 * 1000 / 1005 and two empty at 12.5) fill 84.375%
        final float scale = (100f - 5 * 3.125f) / (75f * 1000 / 1005 + 2 * 12.5f);
        Assertions.assertEquals(75f * 1000 / 1005 * scale, widths[0], DELTA);
        for (int i = 1; i <= 5; i++) {
            Assertions.assertEquals(3.125f, widths[i], DELTA);
        }
        Assertions.assertEquals(12.5f * scale, widths[6], DELTA);
        Assertions.assertEquals(12.5f * scale, widths[7], DELTA);
        Assertions.assertEquals(100f, sum(widths), DELTA);
    }

    /**
     * A header minimum larger than a quarter of the average is kept exactly.
     */
    @Test
    void headerMinimumIsKept() {
        final float[] widths = ItextTableWriter.computeColumnWidths(new float[] { 100, 1, 100, 100 },
                new float[] { 0, 15, 0, 0 });

        Assertions.assertEquals(15f, widths[1], DELTA);
        Assertions.assertEquals(85f / 3, widths[0], DELTA);
        Assertions.assertEquals(100f, sum(widths), DELTA);
    }

    /**
     * Minimums that add up to more than 100% are scaled down proportionally.
     */
    @Test
    void tooLargeMinimumsAreScaled() {
        final float[] widths = ItextTableWriter.computeColumnWidths(new float[] { 1, 1 }, new float[] { 90, 60 });

        Assertions.assertEquals(60f, widths[0], DELTA);
        Assertions.assertEquals(40f, widths[1], DELTA);
    }

    /**
     * Without any data all columns are equal.
     */
    @Test
    void allEmptyColumnsAreEqual() {
        final float[] widths = ItextTableWriter.computeColumnWidths(new float[] { 0, 0, 0, 0 });
        for (final float width : widths) {
            Assertions.assertEquals(25f, width, DELTA);
        }
        Assertions.assertEquals(0, ItextTableWriter.computeColumnWidths(new float[0]).length);
    }

    /**
     * RTF export gets real column widths: wider for longer values, equal for the empty columns, spanning the page.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    void rtfColumnWidths() throws Exception {
        // column 2 and 5 are empty; only the first 1000 rows count, so the long value in row 1001 is ignored
        final String rtf = new String(export(new DefaultRtfExportView(), 8, 1002), StandardCharsets.ISO_8859_1);

        final Matcher row = Pattern.compile("\\\\trowd.*?\\\\pard", Pattern.DOTALL).matcher(rtf);
        Assertions.assertTrue(row.find(), "No table row found");
        final List<Integer> rights = new ArrayList<>();
        final Matcher cellx = Pattern.compile("\\\\cellx(\\d+)").matcher(row.group());
        while (cellx.find()) {
            rights.add(Integer.valueOf(cellx.group(1)));
        }
        Assertions.assertEquals(8, rights.size());

        final float[] widths = new float[8];
        for (int i = 0; i < 8; i++) {
            widths[i] = (float) rights.get(i) - (i == 0 ? 0 : rights.get(i - 1));
            Assertions.assertTrue(widths[i] > 0, "Column " + i + " has no width: " + rights);
        }
        Assertions.assertEquals(widths[2], widths[5], 1, "Empty columns must have the same width");
        Assertions.assertTrue(widths[7] > widths[0], "Longer values must get a wider column");

        // landscape A4 minus the left and right margins, in twips (1pt = 20 twips)
        final float pageWidth = (PageSize.A4.rotate().getWidth() - DefaultItextExportView.MARGIN_LEFT
                - DefaultItextExportView.MARGIN_RIGHT) * 20;
        Assertions.assertEquals(pageWidth, rights.get(7), 10, "The table must span the page width");
        Assertions.assertTrue(rtf.contains("\\lndscpsxn"), "More than 5 columns must be landscape");
    }

    /**
     * RTF export with 5 columns is portrait.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    void rtfPortraitUpToFiveColumns() throws Exception {
        final String rtf = new String(export(new DefaultRtfExportView(), 5, 3), StandardCharsets.ISO_8859_1);
        Assertions.assertFalse(rtf.contains("\\lndscpsxn"), "5 columns must be portrait");
    }

    /**
     * PDF orientation depends on the number of columns.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    void pdfOrientation() throws Exception {
        try (PdfReader reader5 = new PdfReader(export(new DefaultPdfExportView(), 5, 3))) {
            final Rectangle portrait = reader5.getPageSizeWithRotation(1);
            Assertions.assertTrue(portrait.getHeight() > portrait.getWidth(), "5 columns must be portrait");
        }
        try (PdfReader reader6 = new PdfReader(export(new DefaultPdfExportView(), 6, 3))) {
            final Rectangle landscape = reader6.getPageSizeWithRotation(1);
            Assertions.assertTrue(landscape.getWidth() > landscape.getHeight(), "6 columns must be landscape");
        }
    }

    /**
     * Exports a table where column 2 and 5 are empty, and column i has values of length (i + 1) * 3. Row 1001 contains
     * a very long value in column 0, which must not influence the widths.
     */
    private static byte[] export(final BinaryExportView view, final int columns, final int rows) throws Exception {
        final TableModel model = new TableModel(TableProperties.getInstance(null), "UTF-8", null);
        model.setMedia(MediaTypeEnum.PDF);
        for (int c = 0; c < columns; c++) {
            final HeaderCell header = new HeaderCell();
            header.setTitle("Column " + c);
            model.addColumnHeader(header);
        }
        for (int r = 0; r < rows; r++) {
            final Row row = new Row(new Object(), r);
            for (int c = 0; c < columns; c++) {
                String value = c == 2 || c == 5 ? "" : "x".repeat((c + 1) * 3);
                if (r == 1000 && c == 0) {
                    value = "w".repeat(500);
                }
                row.addCell(new Cell(value));
            }
            model.addRow(row);
        }
        model.setRowListPage(model.getRowListFull());
        view.setParameters(model, true, true, false);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        view.doExport(out);
        return out.toByteArray();
    }

    private static float sum(final float[] values) {
        float sum = 0;
        for (final float value : values) {
            sum += value;
        }
        return sum;
    }
}
