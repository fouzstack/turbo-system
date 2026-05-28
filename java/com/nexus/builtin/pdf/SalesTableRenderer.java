package com.nexus.builtin.pdf;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.nexus.utils.InventoryCalculations;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Clase especializada en renderizar la tabla de ventas en PDF
 * Responsabilidad: Dibujar la tabla de ventas con sus filas, encabezados y totales
 */
public class SalesTableRenderer {
    
    private static final DecimalFormat DECIMAL_FORMAT = JsonToPdfHelper.getDecimalFormat();
    private static final DecimalFormat CURRENCY_FORMAT = JsonToPdfHelper.getCurrencyFormat();
    
    private static final int COLOR_HEADER_BG = JsonToPdfHelper.getColorHeaderBg();
    private static final int COLOR_HEADER_TEXT = JsonToPdfHelper.getColorHeaderText();
    private static final int COLOR_ROW_EVEN = JsonToPdfHelper.getColorRowEven();
    private static final int COLOR_ROW_ODD = JsonToPdfHelper.getColorRowOdd();
    private static final int COLOR_BORDER = JsonToPdfHelper.getColorBorder();
    private static final int COLOR_TOTAL_BG = JsonToPdfHelper.getColorTotalBg();
    private static final int COLOR_ACCENT = JsonToPdfHelper.getColorAccent();
    
    private static final int MARGIN = JsonToPdfHelper.getMargin();
    private static final int ROW_HEIGHT = JsonToPdfHelper.getRowHeight();
    private static final int HEADER_HEIGHT = JsonToPdfHelper.getHeaderHeight();
    private static final int PAGE_WIDTH = JsonToPdfHelper.getPageWidth();
    
    private static final SimpleDateFormat DATE_FORMAT_SHORT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT_SHORT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    private final String[] headers = {"Fecha", "Hora", "Producto", "Cantidad", "Precio Unitario", "Importe Total"};
    private final float[] weights = {1.4f, 1.1f, 4.0f, 1.2f, 2.0f, 2.0f};
    private int[] colWidths;
    
    public SalesTableRenderer() {
        this.colWidths = JsonToPdfHelper.calculateColumnWidths(weights, PAGE_WIDTH - 2 * MARGIN);
    }
    
    public void drawTableHeader(Canvas canvas, int y, Paint[] paints) {
        Paint headerBg = new Paint();
        headerBg.setColor(COLOR_HEADER_BG);
        canvas.drawRect(MARGIN, y - HEADER_HEIGHT + 4, PAGE_WIDTH - MARGIN, y + 6, headerBg);
        
        Paint headerTextPaint = new Paint(paints[1]);
        headerTextPaint.setColor(COLOR_HEADER_TEXT);
        headerTextPaint.setTextSize(10.5f);
        headerTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerTextPaint.setAntiAlias(true);
        
        int x = MARGIN;
        for (int i = 0; i < headers.length; i++) {
            float maxTextWidth = colWidths[i] - 12;
            String headerText = headers[i];
            float textWidth = headerTextPaint.measureText(headerText);
            
            if (textWidth <= maxTextWidth) {
                canvas.drawText(headerText, x + (colWidths[i] / 2f) - (textWidth / 2f), y - 9, headerTextPaint);
            } else {
                float originalSize = headerTextPaint.getTextSize();
                float newSize = originalSize * (maxTextWidth / textWidth);
                headerTextPaint.setTextSize(Math.max(8.5f, Math.min(originalSize, newSize)));
                canvas.drawText(headerText, x + (colWidths[i] / 2f) - (headerTextPaint.measureText(headerText) / 2f), y - 9, headerTextPaint);
                headerTextPaint.setTextSize(originalSize);
            }
            
            x += colWidths[i];
            
            Paint vLine = new Paint();
            vLine.setColor(COLOR_BORDER);
            vLine.setStrokeWidth(0.7f);
            canvas.drawLine(x, y - HEADER_HEIGHT + 4, x, y + ROW_HEIGHT * 2, vLine);
        }
        
        Paint hLine = new Paint();
        hLine.setColor(COLOR_BORDER);
        hLine.setStrokeWidth(1f);
        canvas.drawLine(MARGIN, y + 5, PAGE_WIDTH - MARGIN, y + 5, hLine);
    }
    
    // MODIFICADO: Ahora usa unitPrice en lugar de averageSellingPrice
    public void drawRow(Canvas canvas, JsonToPdfHelper.EnhancedSale es, int y,
                        Paint leftPaint, Paint rightAlignPaint) {
        InventoryCalculations.Sale s = es.sale;
        float baseline = y - 8;
        int x = MARGIN;
        
        Date saleDate = JsonToPdfHelper.parseSaleDate(s.saleDate);
        if (saleDate != null) {
            canvas.drawText(DATE_FORMAT_SHORT.format(saleDate), x + 7, baseline, leftPaint);
            x += colWidths[0];
            canvas.drawText(TIME_FORMAT_SHORT.format(saleDate), x + 7, baseline, leftPaint);
        } else {
            canvas.drawText("—", x + 7, baseline, leftPaint);
            x += colWidths[0];
            canvas.drawText("—", x + 7, baseline, leftPaint);
        }
        x += colWidths[1];
        
        JsonToPdfHelper.drawFullText(canvas, s.productName != null ? s.productName : "",
                x + 7, baseline, leftPaint, colWidths[2] - 14);
        x += colWidths[2];
        
        canvas.drawText(DECIMAL_FORMAT.format(s.quantity),  x + colWidths[3] - 9, baseline, rightAlignPaint);
        x += colWidths[3];
        
        // MODIFICACIÓN CLAVE: Usar unitPrice (precio real de la venta) en lugar de averageSellingPrice
        canvas.drawText(CURRENCY_FORMAT.format(es.unitPrice), x + colWidths[4] - 9, baseline, rightAlignPaint);
        x += colWidths[4];
        
        canvas.drawText(CURRENCY_FORMAT.format(s.subtotal), x + colWidths[5] - 9, baseline, rightAlignPaint);
    }
    
    public void drawGrandTotal(Canvas canvas, int y, Paint[] paints,
                               double totalQty, double totalAmount) {
        Paint totalBg = new Paint();
        totalBg.setColor(COLOR_TOTAL_BG);
        canvas.drawRect(MARGIN, y - ROW_HEIGHT + 4, PAGE_WIDTH - MARGIN, y + 10, totalBg);
        
        float baseline = y - 6;
        Paint totalPaint = new Paint(paints[1]);
        totalPaint.setTextSize(10.5f);
        totalPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        totalPaint.setColor(COLOR_ACCENT);
        
        Paint rightTotal = new Paint(totalPaint);
        rightTotal.setTextAlign(Paint.Align.RIGHT);
        
        int x = MARGIN;
        canvas.drawText("TOTAL GENERAL", x + 7, baseline, totalPaint);
        x += colWidths[0] + colWidths[1] + colWidths[2];
        
        canvas.drawText(DECIMAL_FORMAT.format(totalQty), x + colWidths[3] - 9, baseline, rightTotal);
        x += colWidths[3];
        x += colWidths[4];
        canvas.drawText(CURRENCY_FORMAT.format(totalAmount), x + colWidths[5] - 9, baseline, rightTotal);
    }
    
    public int[] getColWidths() {
        return colWidths;
    }
}