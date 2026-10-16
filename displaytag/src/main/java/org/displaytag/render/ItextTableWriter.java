/*
 * SPDX-License-Identifier: MIT
 * See LICENSE file for details.
 *
 * Copyright 2002-2026 Fabrizio Giustina, the Displaytag team
 */
package org.displaytag.render;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;
import java.io.File;
import java.util.Iterator;

import javax.servlet.jsp.JspException;

import org.apache.commons.lang3.StringUtils;
import org.displaytag.FontSettings;
import org.displaytag.decorator.TableDecorator;
import org.displaytag.exception.DecoratorException;
import org.displaytag.exception.ObjectLookupException;
import org.displaytag.model.Column;
import org.displaytag.model.ColumnIterator;
import org.displaytag.model.HeaderCell;
import org.displaytag.model.RowIterator;
import org.displaytag.model.TableModel;
import org.displaytag.properties.MediaTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A table writer that formats table as and writes it to an iText document.
 *
 * @see org.displaytag.render.TableWriterTemplate
 */
public class ItextTableWriter extends TableWriterAdapter {

    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(ItextTableWriter.class);

    /**
     * Number of rows inspected when calculating the column widths.
     */
    private static final int WIDTH_SAMPLE_ROWS = 1000;

    /**
     * Whether the full list (rather than the current page) is written.
     */
    private boolean fullList;

    /**
     * iText representation of the table.
     */
    private final PdfPTable table;

    /**
     * iText document to which the table is written.
     */
    private final Document document;

    /**
     * The default font used in the document.
     */
    private Font defaultFont;

    private BaseFont regularBaseFont;

    private BaseFont boldBaseFont;

    /**
     * This table writer uses an iText table and document to do its work.
     *
     * @param table
     *            iText representation of the table.
     * @param document
     *            iText document to which the table is written.
     */
    public ItextTableWriter(final PdfPTable table, final Document document) {
        this.table = table;
        this.document = document;
    }

    /**
     * Initialize the main info holder table, like the appropriate number of columns.
     *
     * @param model
     *            The table being represented as iText.
     *
     * @see org.displaytag.render.TableWriterTemplate#writeTableOpener(org.displaytag.model.TableModel)
     */
    @Override
    protected void writeTableOpener(final TableModel model) throws DocumentException {
        this.table.getDefaultCell().setVerticalAlignment(Element.ALIGN_TOP);
        this.table.setWidthPercentage(100);
        this.defaultFont = this.getTableFont();
        this.applyColumnWidths(model);
    }

    @Override
    public void writeTable(final TableModel model, final String id, final boolean full) throws JspException {
        // same rule as TableWriterTemplate.writeTableBody, so the widths are measured on the rows that are written
        this.fullList = full
                || (!MediaTypeEnum.HTML.equals(model.getMedia()) && model.getProperties().getExportFullList());
        super.writeTable(model, id, full);
    }

    private static volatile FontSettings fontSettings; // NOSONAR

    private static final String getSysProp(String name, String def) {
        String ret = System.getProperty(name);
        return ret == null ? def : ret;
    }

    public static synchronized FontSettings getFontSettings() {
        if (fontSettings == null) {
            String fontsFolder = getSysProp("FontsFolder", "${SystemRoot}\\Fonts");
            fontsFolder = fontsFolder.replace("${SystemRoot}", System.getenv("SystemRoot"));
            fontSettings = new FontSettings(fontsFolder, getSysProp("FontsFileRegular", "ARIALUNI.TTF"),
                    getSysProp("FontsFileBold", "ARIALUNI.TTF"), getSysProp("FontsFileRegularFallback", "arial.ttf"),
                    getSysProp("FontsFileBoldFallback", "arialbd.ttf"));
        }
        return fontSettings;
    }

    public static File getFontFile(boolean bold) {
        FontSettings fontSettings = getFontSettings();
        File fontFile = java.nio.file.Path.of(fontSettings.getFontsFolder(),
                bold ? fontSettings.getFontsFileBold() : fontSettings.getFontsFileRegular()).toFile();
        if (!fontFile.exists()) {
            fontFile = java.nio.file.Path
                    .of(fontSettings.getFontsFolder(),
                            bold ? fontSettings.getFontsFileBoldFallback() : fontSettings.getFontsFileRegularFallback())
                    .toFile();
        }
        return fontFile;
    }

    public static BaseFont createBaseFont(boolean bold) {
        try {
            File fontFile = getFontFile(bold);
            return BaseFont.createFont(fontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) {
            log.warn("Unable to load the configured PDF font, falling back to Helvetica", e);
            return FontFactory
                    .getFont(FontFactory.HELVETICA, 10, bold ? Font.BOLD : Font.NORMAL, new Color(0x00, 0x00, 0x00))
                    .getBaseFont();
        }
    }

    public static Font createFont(boolean bold, float size) {
        return new Font(createBaseFont(bold), size);
    }

    /**
     * Gets the base font for this writer, loading it only once per writer. Embedded fonts keep per-document state, so
     * the instance is deliberately not shared between writers.
     */
    private BaseFont getBaseFont(final boolean bold) {
        if (bold) {
            if (this.boldBaseFont == null) {
                this.boldBaseFont = createBaseFont(true);
            }
            return this.boldBaseFont;
        }
        if (this.regularBaseFont == null) {
            this.regularBaseFont = createBaseFont(false);
        }
        return this.regularBaseFont;
    }

    private Font getFont(final boolean bold, final float size) {
        return new Font(this.getBaseFont(bold), size);
    }

    /**
     * Obtain the font used to render text in the table; Meant to be overriden if a different font is desired.
     *
     * @return The font used to render text in the table.
     */
    protected Font getTableFont() {
        return this.getFont(false, 10);
    }

    /**
     * Write the table's caption to a iText document.
     *
     * @param model
     *            the model
     *
     * @throws Exception
     *             the exception
     *
     * @see org.displaytag.render.TableWriterTemplate#writeCaption(org.displaytag.model.TableModel)
     */
    @Override
    protected void writeCaption(final TableModel model) throws Exception {
        this.decorateCaption(model);
    }

    /**
     * Writes the table caption according to a set style.
     *
     * @param model
     *            The table model containing the caption.
     *
     * @throws DocumentException
     *             If an error occurrs while decorating the caption.
     */
    private void decorateCaption(final TableModel model) throws DocumentException {
        final Paragraph caption = new Paragraph(new Chunk(model.getCaption(), this.getCaptionFont()));
        caption.setAlignment(this.getCaptionHorizontalAlignment());
        this.document.add(caption);
        // only PDF honours this; RTF already separates the caption from the table
        this.table.setSpacingBefore(this.getCaptionSpacingAfter());
    }

    /**
     * Obtain the vertical space between the caption and the table; Meant to be overriden if a different style is
     * desired.
     *
     * @return The space in points.
     */
    protected float getCaptionSpacingAfter() {
        return 10f;
    }

    /**
     * Obtain the caption font; Meant to be overriden if a different style is desired.
     *
     * @return The caption font.
     */
    protected Font getCaptionFont() {
        return this.getFont(true, 17);
    }

    /**
     * Obtain the caption horizontal alignment; Meant to be overriden if a different style is desired.
     *
     * @return The caption horizontal alignment.
     */
    protected int getCaptionHorizontalAlignment() {
        return Element.ALIGN_CENTER;
    }

    /**
     * Write the table's header columns to an iText document.
     *
     * @param model
     *            the model
     *
     * @throws DocumentException
     *             if an error occurs while writing header.
     *
     * @see org.displaytag.render.TableWriterTemplate#writeTableHeader(org.displaytag.model.TableModel)
     */
    @Override
    protected void writeTableHeader(final TableModel model) throws DocumentException {
        final Iterator<HeaderCell> iterator = model.getHeaderCellList().iterator();

        while (iterator.hasNext()) {
            final HeaderCell headerCell = iterator.next();
            final PdfPCell hdrCell = this.getHeaderCell(ItextTableWriter.getHeaderTitle(headerCell));
            this.table.addCell(hdrCell);
        }
        this.table.setHeaderRows(1);
    }

    /**
     * Gets the text shown in the header of a column.
     *
     * @param headerCell
     *            the header cell
     *
     * @return the title, or the capitalized property name if there is no title
     */
    private static String getHeaderTitle(final HeaderCell headerCell) {
        final String title = headerCell.getTitle();
        return title != null ? title : StringUtils.capitalize(headerCell.getBeanPropertyName());
    }

    /**
     * Sets the column widths of the table, based on the content of the first {@value #WIDTH_SAMPLE_ROWS} rows.
     *
     * @param model
     *            the table model
     *
     * @throws DocumentException
     *             if the widths cannot be applied to the table
     */
    private void applyColumnWidths(final TableModel model) throws DocumentException {
        float[] longest;
        try {
            longest = this.measureLongestValues(model);
        } catch (ObjectLookupException | DecoratorException e) {
            ItextTableWriter.log.warn("Unable to measure column values, using equal column widths", e);
            longest = new float[model.getNumberOfColumns()];
        }
        this.table.setWidths(ItextTableWriter.computeColumnWidths(longest, this.measureHeaderMinimums(model)));
        // OpenRTF reads the absolute widths as percentages, so make them add up to 100. PDF ignores this, because the
        // width is not locked: the table is laid out using the width percentage when it is added to the document.
        this.table.setTotalWidth(100f);
    }

    /**
     * Measures the rendered width of the longest value (or line) of each column in the first
     * {@value #WIDTH_SAMPLE_ROWS} rows that will be exported.
     *
     * @param model
     *            the table model
     *
     * @return the widest value in points per column; 0 if the column is empty in all sampled rows
     *
     * @throws ObjectLookupException
     *             if a value cannot be read
     * @throws DecoratorException
     *             if a value cannot be decorated
     */
    private float[] measureLongestValues(final TableModel model) throws ObjectLookupException, DecoratorException {
        final int columns = model.getNumberOfColumns();
        final float[] longest = new float[columns];
        final BaseFont baseFont = this.defaultFont.getCalculatedBaseFont(false);
        final float fontSize = this.defaultFont.getCalculatedSize();

        final RowIterator rowIterator = model.getRowIterator(this.fullList);
        for (int r = 0; r < ItextTableWriter.WIDTH_SAMPLE_ROWS && rowIterator.hasNext(); r++) {
            final ColumnIterator columnIterator = rowIterator.next().getColumnIterator(model.getHeaderCellList());
            for (int c = 0; c < columns && columnIterator.hasNext(); c++) {
                final Column column = columnIterator.nextColumn();
                column.initialize();
                final Object value = column.getValue(true);
                final String text = StringUtils.trim(value != null ? value.toString() : null);
                if (StringUtils.isEmpty(text)) {
                    continue;
                }
                for (final String line : text.split("\\R")) {
                    final String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        // a value never counts as empty, even if the font has no glyphs for it
                        longest[c] = Math.max(longest[c], Math.max(1f, baseFont.getWidthPoint(trimmed, fontSize)));
                    }
                }
            }
        }
        return longest;
    }

    /**
     * Measures the width each column needs to show the longest word of its header without breaking it.
     *
     * @param model
     *            the table model
     *
     * @return the minimum width of each column in percent of the table width; 0 if there is no header
     */
    private float[] measureHeaderMinimums(final TableModel model) {
        final float[] minimums = new float[model.getNumberOfColumns()];
        final float tableWidth = this.document.right() - this.document.left();
        if (!model.getProperties().getShowHeader() || tableWidth <= 0) {
            return minimums;
        }
        final BaseFont baseFont = this.getFont(true, this.defaultFont.getSize()).getCalculatedBaseFont(false);
        final float fontSize = this.defaultFont.getCalculatedSize();
        final PdfPCell sample = new PdfPCell();
        // cell padding plus a little slack for the borders and rounding
        final float padding = sample.getPaddingLeft() + sample.getPaddingRight() + 2f;

        final Iterator<HeaderCell> iterator = model.getHeaderCellList().iterator();
        for (int c = 0; c < minimums.length && iterator.hasNext(); c++) {
            final String title = ItextTableWriter.getHeaderTitle(iterator.next());
            if (title == null) {
                continue;
            }
            float widest = 0;
            for (final String word : StringUtils.split(title)) {
                widest = Math.max(widest, baseFont.getWidthPoint(word, fontSize));
            }
            if (widest > 0) {
                minimums[c] = (widest + padding) * 100f / tableWidth;
            }
        }
        return minimums;
    }

    /**
     * Calculates the column widths, in percent of the table width, without header minimums.
     *
     * @param longest
     *            the width of the longest value of each column; 0 (or less) means the column has no data
     *
     * @return the column widths in percent, adding up to 100
     *
     * @see #computeColumnWidths(float[], float[])
     */
    public static float[] computeColumnWidths(final float[] longest) {
        return ItextTableWriter.computeColumnWidths(longest, new float[longest.length]);
    }

    /**
     * Calculates the column widths, in percent of the table width.
     * <ul>
     * <li>Each column that has no data gets the average width (100 / number of columns).</li>
     * <li>The columns with data share the rest, in proportion to their longest value.</li>
     * <li>No column is narrower than its minimum: a quarter of the average width, or the given header minimum if that
     * is larger. Such columns stay exactly at their minimum, and only the other columns are scaled down so that the
     * widths add up to 100 again.</li>
     * </ul>
     * If no column has data, all columns get the same width (still respecting the minimums). If the minimums alone
     * exceed 100, they are scaled down proportionally.
     *
     * @param longest
     *            the width of the longest value of each column; 0 (or less) means the column has no data
     * @param headerMinimums
     *            the minimum width of each column in percent, needed by its header; 0 for none
     *
     * @return the column widths in percent, adding up to 100
     */
    public static float[] computeColumnWidths(final float[] longest, final float[] headerMinimums) {
        final int columns = longest.length;
        final float[] widths = new float[columns];
        if (columns == 0) {
            return widths;
        }
        final float average = 100f / columns;

        float dataTotal = 0;
        int dataColumns = 0;
        for (final float value : longest) {
            if (value > 0) {
                dataTotal += value;
                dataColumns++;
            }
        }

        final float dataShare = average * dataColumns;
        final float[] minimums = new float[columns];
        float minimumTotal = 0;
        for (int i = 0; i < columns; i++) {
            widths[i] = longest[i] > 0 ? dataShare * longest[i] / dataTotal : average;
            minimums[i] = Math.max(average / 4, i < headerMinimums.length ? headerMinimums[i] : 0);
            minimumTotal += minimums[i];
        }
        if (minimumTotal >= 100f) {
            for (int i = 0; i < columns; i++) {
                widths[i] = minimums[i] * 100f / minimumTotal;
            }
            return widths;
        }

        // fix the columns that would fall below their minimum and scale the others to fill the rest; fixing a column
        // shrinks the others further, so repeat until nothing changes
        final boolean[] fixed = new boolean[columns];
        boolean changed = true;
        float scale = 1f;
        while (changed) {
            changed = false;
            float fixedTotal = 0;
            float freeTotal = 0;
            for (int i = 0; i < columns; i++) {
                if (fixed[i]) {
                    fixedTotal += minimums[i];
                } else {
                    freeTotal += widths[i];
                }
            }
            scale = (100f - fixedTotal) / freeTotal;
            for (int i = 0; i < columns; i++) {
                if (!fixed[i] && widths[i] * scale < minimums[i]) {
                    fixed[i] = true;
                    changed = true;
                }
            }
        }
        for (int i = 0; i < columns; i++) {
            widths[i] = fixed[i] ? minimums[i] : widths[i] * scale;
        }
        return widths;
    }

    /**
     * Write post body footer.
     *
     * @param model
     *            the model
     *
     * @throws DocumentException
     *             if an error occurs while writing post-body footer.
     *
     * @see org.displaytag.render.TableWriterTemplate#writePostBodyFooter(org.displaytag.model.TableModel)
     */
    @Override
    protected void writePostBodyFooter(final TableModel model) throws DocumentException {
        final Chunk cellContent = new Chunk(model.getFooter(), this.getFooterFont());
        this.setFooterFontStyle(cellContent);
        final PdfPCell cell = new PdfPCell(new Phrase(cellContent));
        cell.setBackgroundColor(this.getFooterBackgroundColor());
        cell.setHorizontalAlignment(this.getFooterHorizontalAlignment());
        cell.setColspan(model.getNumberOfColumns());
        this.table.addCell(cell);
    }

    /**
     * Obtain the footer background color; Meant to be overriden if a different style is desired.
     *
     * @return The footer background color.
     */
    protected Color getFooterBackgroundColor() {
        return new Color(0xce, 0xcf, 0xce);
    }

    /**
     * Obtain the footer horizontal alignment; Meant to be overriden if a different style is desired.
     *
     * @return The footer horizontal alignment.
     */
    protected int getFooterHorizontalAlignment() {
        return Element.ALIGN_LEFT;
    }

    /**
     * Set the font style used to render the header text; Meant to be overridden if a different header style is desired.
     *
     * @param cellContent
     *            The header content whose font will be modified.
     */
    protected void setFooterFontStyle(final Chunk cellContent) {
        this.setBoldStyle(cellContent, this.getFooterFontColor());
    }

    /**
     * Obtain the footer font color; Meant to be overriden if a different style is desired.
     *
     * @return The footer font color.
     */
    protected Color getFooterFontColor() {
        return new Color(0x00, 0x00, 0x00);
    }

    /**
     * Obtain the footer font; Meant to be overriden if a different style is desired.
     *
     * @return The footer font.
     */
    protected Font getFooterFont() {
        return this.getFont(false, 10);
    }

    /**
     * Decorators that help render the table to an iText document must implement ItextDecorator.
     *
     * @param model
     *            the model
     *
     * @see org.displaytag.render.TableWriterTemplate#writeDecoratedRowStart(org.displaytag.model.TableModel)
     */
    @Override
    protected void writeDecoratedRowStart(final TableModel model) {
        final TableDecorator decorator = model.getTableDecorator();
        if (decorator instanceof ItextDecorator) {
            final ItextDecorator idecorator = (ItextDecorator) decorator;
            idecorator.setTable(this.table);
            idecorator.setFont(this.defaultFont);
        }
        decorator.startRow();
    }

    /**
     * Write decorated row finish.
     *
     * @param model
     *            the model
     *
     * @throws Exception
     *             the exception
     *
     * @see org.displaytag.render.TableWriterTemplate#writeDecoratedRowFinish(org.displaytag.model.TableModel)
     */
    @Override
    protected void writeDecoratedRowFinish(final TableModel model) throws Exception {
        model.getTableDecorator().finishRow();
    }

    /**
     * Write a column's opening structure to an iText document.
     *
     * @param column
     *            the column
     *
     * @throws ObjectLookupException
     *             the object lookup exception
     * @throws DecoratorException
     *             the decorator exception
     *
     * @see org.displaytag.render.TableWriterTemplate#writeColumnOpener(org.displaytag.model.Column)
     */
    @Override
    protected void writeColumnOpener(final Column column) throws ObjectLookupException, DecoratorException {
        column.initialize(); // has side effect, setting its stringValue, which affects grouping logic.
    }

    /**
     * Write a column's value to a iText document.
     *
     * @param value
     *            the value
     * @param column
     *            the column
     *
     * @throws BadElementException
     *             the bad element exception
     *
     * @see org.displaytag.render.TableWriterTemplate#writeColumnValue(Object,org.displaytag.model.Column)
     */
    @Override
    protected void writeColumnValue(final Object value, final Column column) throws BadElementException {
        this.table.addCell(this.getCell(value));
    }

    /**
     * Write decorated table finish.
     *
     * @param model
     *            the model
     *
     * @see org.displaytag.render.TableWriterTemplate#writeDecoratedTableFinish(org.displaytag.model.TableModel)
     */
    @Override
    protected void writeDecoratedTableFinish(final TableModel model) {
        model.getTableDecorator().finish();
    }

    /**
     * Returns a formatted cell for the given value.
     *
     * @param value
     *            cell value
     *
     * @return Cell
     */
    private PdfPCell getCell(final Object value) {
        final PdfPCell cell = new PdfPCell(new Phrase(new Chunk(
                StringUtils.trimToEmpty(value != null ? value.toString() : StringUtils.EMPTY), this.defaultFont)));
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setLeading(8, 0);
        return cell;
    }

    /**
     * Obtain a header cell.
     *
     * @param value
     *            Cell content.
     *
     * @return A header cell with the given content.
     */
    private PdfPCell getHeaderCell(final String value) {
        final Chunk cellContent = new Chunk(value, this.getHeaderFont());
        this.setHeaderFontStyle(cellContent);
        final PdfPCell cell = new PdfPCell(new Phrase(cellContent));
        cell.setHorizontalAlignment(this.getHeaderHorizontalAlignment());
        cell.setBackgroundColor(this.getHeaderBackgroundColor());
        return cell;
    }

    /**
     * Obtain the font used to render the header text; Meant to be overridden if a different header font is desired.
     *
     * @return The font used to render the header text.
     */
    protected Font getHeaderFont() {
        return this.defaultFont;
    }

    /**
     * Obtain the background color used to render the header; Meant to be overridden if a different header background
     * color is desired.
     *
     * @return The backgrounc color used to render the header.
     */
    protected Color getHeaderBackgroundColor() {
        return new Color(0xee, 0xee, 0xee);
    }

    /**
     * Set the font style used to render the header text; Meant to be overridden if a different header style is desired.
     *
     * @param cellContent
     *            The header content whose font will be modified.
     */
    protected void setHeaderFontStyle(final Chunk cellContent) {
        this.setBoldStyle(cellContent, this.getHeaderFontColor());
    }

    /**
     * Set the font color used to render the header text; Meant to be overridden if a different header style is desired.
     *
     * @return The font color used to render the header text.
     */
    protected Color getHeaderFontColor() {
        return new Color(0x00, 0x00, 0x00);
    }

    /**
     * Obtain the horizontal alignment used to render header text; Meant to be overridden if a different alignment is
     * desired.
     *
     * @return The horizontal alignment used to render header text;
     */
    protected int getHeaderHorizontalAlignment() {
        return Element.ALIGN_CENTER;
    }

    /**
     * Makes chunk content bold.
     *
     * @param chunk
     *            The chunk whose content is to be rendered bold.
     * @param color
     *            The font color desired.
     */
    private void setBoldStyle(final Chunk chunk, final Color color) {
        Font font = chunk.getFont();
        Font newFont;
        try {
            newFont = this.getFont(true, font.getSize());
        } catch (Exception e) {
            newFont = FontFactory.getFont(font.getFamilyname(), font.getSize(), Font.BOLD, color);
        }
        chunk.setFont(newFont);
    }

    /**
     * An implementor of this interface decorates tables and columns appearing in iText documents.
     */
    public interface ItextDecorator {

        /**
         * Set the iText table used to render a table model.
         *
         * @param table
         *            The iText table used to render a table model.
         */
        void setTable(PdfPTable table);

        /**
         * Set the font used to render a table's content.
         *
         * @param font
         *            The font used to render a table's content.
         */
        void setFont(Font font);
    }

}
