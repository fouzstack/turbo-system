package com.nexus.builtin.pdf;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.widget.Toast;

import com.nexus.utils.InventoryCalculations;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JsonToPdfHelper - Versión FINAL 2026
 * Diseño moderno profesional inspirado en reportes financieros de oficina:
 * - Espaciado generoso y legible
 * - Alineación perfecta de números
 * - Decimales SIEMPRE con 2 dígitos (13.00, 27.35, etc.)
 * - Total general SOLO en la última página
 * - Encabezados con ancho suficiente para mostrar nombres completos
 * - Formato limpio, elegante y 100% compatible Android 10+
 * - Precio Unitario muestra el precio real de cada venta específica (sale.price)
 */
public class JsonToPdfHelper {

    // ====================== FORMATOS NUMÉRICOS (FORZADOS A 2 DECIMALES) ======================
    private static final DecimalFormat DECIMAL_FORMAT;
    private static final DecimalFormat CURRENCY_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "MX"));
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');

        DECIMAL_FORMAT = new DecimalFormat("#,##0.00", symbols);
        DECIMAL_FORMAT.setMinimumFractionDigits(2);
        DECIMAL_FORMAT.setMaximumFractionDigits(2);

        CURRENCY_FORMAT = new DecimalFormat("$#,##0.00", symbols);
        CURRENCY_FORMAT.setMinimumFractionDigits(2);
        CURRENCY_FORMAT.setMaximumFractionDigits(2);
    }

    // ====================== COLORES PROFESIONALES MODERNOS ======================
    private static final int COLOR_PRIMARY      = Color.parseColor("#1E2A44");
    private static final int COLOR_SECONDARY    = Color.parseColor("#3498DB");
    private static final int COLOR_HEADER_BG    = Color.parseColor("#2C3E50");
    private static final int COLOR_HEADER_TEXT  = Color.parseColor("#ECF0F1");
    private static final int COLOR_ROW_EVEN     = Color.parseColor("#F8FAFC");
    private static final int COLOR_ROW_ODD      = Color.WHITE;
    private static final int COLOR_BORDER       = Color.parseColor("#D1D9E6");
    private static final int COLOR_TOTAL_BG     = Color.parseColor("#E8EEF5");
    private static final int COLOR_SUBTITLE     = Color.parseColor("#64748B");
    private static final int COLOR_ACCENT       = Color.parseColor("#10B981");

    // ====================== DIMENSIONES MODERNAS ======================
    private static final int PAGE_WIDTH         = 595;
    private static final int PAGE_HEIGHT        = 842;
    private static final int MARGIN             = 42;
    private static final int ROW_HEIGHT         = 22;
    private static final int HEADER_HEIGHT      = 32;
    private static final int DATE_AREA_HEIGHT   = 25;
    private static final int FOOTER_HEIGHT      = 55;

    // ====================== FORMATOS DE FECHA ======================
    private static final SimpleDateFormat REPORT_DATE_FORMAT =
            new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));

    // ====================== MÉTODO PRINCIPAL ======================
    public static void generarPdfDesdeJson(String jsonString, Context context,
                                           Handler mainHandler,
                                           PdfLogCallback logCallback) {
        mainHandler.post(() -> {
            try {
                Toast.makeText(context, "Generando reportes PDF profesionales...", Toast.LENGTH_SHORT).show();

                JSONObject invJson = new JSONObject(jsonString);

                List<InventoryCalculations.Product> products = InventoryCalculations.parseProducts(invJson.getJSONArray("products"));
                Collections.sort(products, (p1, p2) -> p1.name.toLowerCase().compareTo(p2.name.toLowerCase()));

                List<InventoryCalculations.Sale> sales = new ArrayList<>();
                JSONArray salesJson = invJson.optJSONArray("sales");
                if (salesJson != null) {
                    for (int i = 0; i < salesJson.length(); i++) {
                        sales.add(new InventoryCalculations.Sale(salesJson.getJSONObject(i)));
                    }
                }

                generarPdfInventario(products, context, logCallback);
                if (!sales.isEmpty()) {
                    generarPdfVentas(products, sales, context, logCallback);
                } else {
                    logCallback.log("No hay ventas registradas");
                }

                Toast.makeText(context, "Reportes PDF generados con éxito", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(context, "Error al generar PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                logCallback.log("Error PDF: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // ====================== PDF INVENTARIO ======================
    private static void generarPdfInventario(List<InventoryCalculations.Product> products,
                                             Context context,
                                             PdfLogCallback logCallback) throws Exception {
        if (products.isEmpty()) return;

        PdfDocument pdf = new PdfDocument();
        InventoryTableRenderer inventoryTable = new InventoryTableRenderer();

        double sumInitial = 0, sumIncoming = 0, sumLosses = 0, sumAvailable = 0;
        double sumFinal = 0, sumSold = 0, sumCash = 0;
        int countFinalNotNull = 0;

        for (InventoryCalculations.Product p : products) {
            sumInitial += p.initial_products;
            sumIncoming += p.incoming_products;
            sumLosses += p.losses;
            sumAvailable += p.available_products;
            sumSold += p.sold_products;
            sumCash += p.total_cash;
            if (p.final_products != null) {
                sumFinal += p.final_products;
                countFinalNotNull++;
            }
        }

        int maxRowsPerPage = calculateMaxRowsPerPage();
        int totalPages = (int) Math.ceil((double) products.size() / maxRowsPerPage);

        for (int page = 0; page < totalPages; page++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, page + 1).create();
            PdfDocument.Page pageDoc = pdf.startPage(pageInfo);
            Canvas canvas = pageDoc.getCanvas();

            Paint[] paints = createPaints();

            drawDateHeader(canvas, paints);

            int y = MARGIN + DATE_AREA_HEIGHT;
            inventoryTable.drawTableHeader(canvas, y, paints);

            y += HEADER_HEIGHT;
            Paint rightAlignPaint = new Paint(paints[1]);
            rightAlignPaint.setTextAlign(Paint.Align.RIGHT);

            int start = page * maxRowsPerPage;
            int end = Math.min(start + maxRowsPerPage, products.size());

            for (int i = start; i < end; i++) {
                InventoryCalculations.Product p = products.get(i);
                drawRowBackground(canvas, y, (i - start) % 2 == 0);
                inventoryTable.drawRow(canvas, p, y, paints[1], rightAlignPaint);
                drawHorizontalLine(canvas, y + ROW_HEIGHT - 1, inventoryTable.getColWidths());
                y += ROW_HEIGHT;
            }

            if (page == totalPages - 1) {
                inventoryTable.drawGrandTotal(canvas, y, paints,
                        sumInitial, sumIncoming, sumLosses, sumAvailable,
                        countFinalNotNull > 0 ? sumFinal : null, sumSold, sumCash);
            }

            drawPageNumber(canvas, page + 1, totalPages, paints);

            pdf.finishPage(pageDoc);
        }

        savePdf(pdf, "INVENTARIO", context, logCallback);
    }

    // ====================== PDF VENTAS ======================
    private static void generarPdfVentas(List<InventoryCalculations.Product> products,
                                         List<InventoryCalculations.Sale> sales,
                                         Context context,
                                         PdfLogCallback logCallback) throws Exception {
        if (sales.isEmpty()) return;

        Map<Integer, Double> productAveragePriceMap = new HashMap<>();
        for (InventoryCalculations.Product p : products) {
            productAveragePriceMap.put(p.code != null ? p.code.hashCode() : p.name.hashCode(), p.averageSellingPrice);
        }

        List<EnhancedSale> enhancedSales = new ArrayList<>();
        for (InventoryCalculations.Sale sale : sales) {
            EnhancedSale enhanced = new EnhancedSale();
            enhanced.sale = sale;

            int key = sale.productCode != null ? sale.productCode.hashCode() : sale.productName.hashCode();
            enhanced.averageSellingPrice = productAveragePriceMap.getOrDefault(key, sale.price);
            enhanced.unitPrice = sale.price;

            enhancedSales.add(enhanced);
        }

        PdfDocument pdf = new PdfDocument();
        SalesTableRenderer salesTable = new SalesTableRenderer();

        double grandQty = 0;
        double grandAmount = 0;
        for (EnhancedSale es : enhancedSales) {
            grandQty += es.sale.quantity;
            grandAmount += es.sale.subtotal;
        }

        int maxRowsPerPage = calculateMaxRowsPerPage();
        int totalPages = (int) Math.ceil((double) enhancedSales.size() / maxRowsPerPage);

        for (int page = 0; page < totalPages; page++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, page + 1).create();
            PdfDocument.Page pageDoc = pdf.startPage(pageInfo);
            Canvas canvas = pageDoc.getCanvas();

            Paint[] paints = createPaints();

            drawDateHeader(canvas, paints);

            int y = MARGIN + DATE_AREA_HEIGHT;
            salesTable.drawTableHeader(canvas, y, paints);

            y += HEADER_HEIGHT;
            Paint rightAlignPaint = new Paint(paints[1]);
            rightAlignPaint.setTextAlign(Paint.Align.RIGHT);

            int start = page * maxRowsPerPage;
            int end = Math.min(start + maxRowsPerPage, enhancedSales.size());

            for (int i = start; i < end; i++) {
                EnhancedSale es = enhancedSales.get(i);
                drawRowBackground(canvas, y, (i - start) % 2 == 0);
                salesTable.drawRow(canvas, es, y, paints[1], rightAlignPaint);
                drawHorizontalLine(canvas, y + ROW_HEIGHT - 1, salesTable.getColWidths());
                y += ROW_HEIGHT;
            }

            if (page == totalPages - 1) {
                salesTable.drawGrandTotal(canvas, y, paints, grandQty, grandAmount);
            }

            drawPageNumber(canvas, page + 1, totalPages, paints);

            pdf.finishPage(pageDoc);
        }

        savePdf(pdf, "VENTAS", context, logCallback);
    }

    // ====================== CLASE AUXILIAR ======================
    public static class EnhancedSale {
        public InventoryCalculations.Sale sale;
        public double averageSellingPrice;
        public double unitPrice;
    }

    // ====================== MÉTODOS AUXILIARES PÚBLICOS PARA RENDERERS ======================
    public static DecimalFormat getDecimalFormat() { return DECIMAL_FORMAT; }
    public static DecimalFormat getCurrencyFormat() { return CURRENCY_FORMAT; }

    public static int getColorHeaderBg() { return COLOR_HEADER_BG; }
    public static int getColorHeaderText() { return COLOR_HEADER_TEXT; }
    public static int getColorRowEven() { return COLOR_ROW_EVEN; }
    public static int getColorRowOdd() { return COLOR_ROW_ODD; }
    public static int getColorBorder() { return COLOR_BORDER; }
    public static int getColorTotalBg() { return COLOR_TOTAL_BG; }
    public static int getColorAccent() { return COLOR_ACCENT; }

    public static int getMargin() { return MARGIN; }
    public static int getRowHeight() { return ROW_HEIGHT; }
    public static int getHeaderHeight() { return HEADER_HEIGHT; }
    public static int getPageWidth() { return PAGE_WIDTH; }

    public static int[] calculateColumnWidths(float[] weights, int totalWidth) {
        int[] result = new int[weights.length];
        float totalWeight = 0;
        for (float w : weights) totalWeight += w;
        int assigned = 0;
        for (int i = 0; i < weights.length - 1; i++) {
            result[i] = Math.round((weights[i] / totalWeight) * totalWidth);
            assigned += result[i];
        }
        result[weights.length - 1] = totalWidth - assigned;
        return result;
    }

    public static void drawFullText(Canvas canvas, String text, float x, float y,
                                    Paint paint, float maxWidth) {
        if (text == null) text = "";
        float textWidth = paint.measureText(text);

        if (textWidth <= maxWidth) {
            canvas.drawText(text, x, y, paint);
        } else {
            float originalSize = paint.getTextSize();
            float newSize = originalSize * (maxWidth / textWidth);
            paint.setTextSize(Math.max(7.0f, newSize));
            canvas.drawText(text, x, y, paint);
            paint.setTextSize(originalSize);
        }
    }

    public static Date parseSaleDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd", "dd/MM/yyyy"
        };

        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern, Locale.getDefault()).parse(dateStr);
            } catch (ParseException ignored) {}
        }
        return null;
    }

    // ====================== MÉTODOS PRIVADOS ======================
    private static int calculateMaxRowsPerPage() {
        int usableHeight = PAGE_HEIGHT - (MARGIN * 2) - DATE_AREA_HEIGHT - FOOTER_HEIGHT;
        return usableHeight / ROW_HEIGHT;
    }

    private static Paint[] createPaints() {
        Paint titlePaint = new Paint();
        titlePaint.setColor(COLOR_PRIMARY);
        titlePaint.setTextSize(17);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setAntiAlias(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        Paint contentPaint = new Paint();
        contentPaint.setColor(COLOR_PRIMARY);
        contentPaint.setTextSize(9.5f);
        contentPaint.setAntiAlias(true);

        Paint subtitlePaint = new Paint();
        subtitlePaint.setColor(COLOR_SUBTITLE);
        subtitlePaint.setTextSize(10);
        subtitlePaint.setAntiAlias(true);

        return new Paint[]{titlePaint, contentPaint, subtitlePaint};
    }

    private static void drawDateHeader(Canvas canvas, Paint[] paints) {
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, bgPaint);

        canvas.drawText("Generado el " + REPORT_DATE_FORMAT.format(new Date()),
                MARGIN, MARGIN + 15, paints[2]);
    }

    private static void drawPageNumber(Canvas canvas, int currentPage, int totalPages, Paint[] paints) {
        Paint pagePaint = new Paint(paints[2]);
        pagePaint.setTextSize(9);
        pagePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Página " + currentPage + " de " + totalPages,
                PAGE_WIDTH / 2f, PAGE_HEIGHT - 22, pagePaint);
    }

    private static void drawRowBackground(Canvas canvas, int y, boolean isEven) {
        Paint bg = new Paint();
        bg.setStyle(Paint.Style.FILL);
        bg.setColor(isEven ? COLOR_ROW_EVEN : COLOR_ROW_ODD);
        canvas.drawRect(MARGIN, y - ROW_HEIGHT + 4, PAGE_WIDTH - MARGIN, y + 4, bg);
    }

    private static void drawHorizontalLine(Canvas canvas, float y, int[] colWidths) {
        Paint line = new Paint();
        line.setColor(COLOR_BORDER);
        line.setStrokeWidth(0.6f);
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line);

        int x = MARGIN;
        for (int width : colWidths) {
            x += width;
            canvas.drawLine(x, y - ROW_HEIGHT + 3, x, y + 3, line);
        }
    }

    private static void savePdf(PdfDocument pdf, String type, Context context,
                                PdfLogCallback logCallback) throws Exception {
        String filename = type + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".pdf";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/");

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);

        if (uri == null) throw new Exception("No se pudo crear el archivo PDF");

        try (OutputStream os = resolver.openOutputStream(uri)) {
            if (os != null) {
                pdf.writeTo(os);
                logCallback.log("PDF guardado: " + filename);
            }
        }
        pdf.close();
    }
}