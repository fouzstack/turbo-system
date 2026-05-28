package com.nexus.builtin.pdf;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.nexus.utils.InventoryCalculations;

import java.text.DecimalFormat;
import java.util.List;

/**
* Clase especializada en renderizar la tabla de inventario en PDF
* Responsabilidad: Dibujar la tabla de inventario con sus filas, encabezados y totales
*/
public class InventoryTableRenderer {
	
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
	
	private final String[] headers = {"Producto", "Unidad", "Inicial", "Entrada", "Ajuste", "A la venta", "Finales", "Vendido", "Precio Estimado", "Importe Total"};
	private final float[] weights = {4.0f, 1.2f, 1.1f, 1.1f, 1.1f, 1.4f, 1.1f, 1.25f, 2.2f, 2.0f};
	private int[] colWidths;
	
	public InventoryTableRenderer() {
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
				// Centrado con espacio a izquierda y derecha
				canvas.drawText(headerText, x + (colWidths[i] / 2f) - (textWidth / 2f), y - 9, headerTextPaint);
				} else {
				float originalSize = headerTextPaint.getTextSize();
				float newSize = originalSize * (maxTextWidth / textWidth);
				headerTextPaint.setTextSize(Math.max(8.5f, Math.min(originalSize, newSize)));
				// Centrado con espacio a izquierda y derecha incluso cuando se reduce el tamaño
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
	
	public void drawRow(Canvas canvas, InventoryCalculations.Product p, int y,
	Paint leftPaint, Paint rightAlignPaint) {
		float baseline = y - 8;
		int x = MARGIN;
		
		JsonToPdfHelper.drawFullText(canvas, p.name, x + 7, baseline, leftPaint, colWidths[0] - 14);
		x += colWidths[0];
		
		canvas.drawText(p.unit != null ? p.unit : "-", x + 7, baseline, leftPaint);
		x += colWidths[1];
		
		canvas.drawText(DECIMAL_FORMAT.format(p.initial_products),   x + colWidths[2] - 9, baseline, rightAlignPaint); x += colWidths[2];
		canvas.drawText(DECIMAL_FORMAT.format(p.incoming_products),  x + colWidths[3] - 9, baseline, rightAlignPaint); x += colWidths[3];
		canvas.drawText(DECIMAL_FORMAT.format(p.losses),             x + colWidths[4] - 9, baseline, rightAlignPaint); x += colWidths[4];
		canvas.drawText(DECIMAL_FORMAT.format(p.available_products), x + colWidths[5] - 9, baseline, rightAlignPaint); x += colWidths[5];
		canvas.drawText(p.final_products == null ? "-" : DECIMAL_FORMAT.format(p.final_products),
		x + colWidths[6] - 9, baseline, rightAlignPaint); x += colWidths[6];
		canvas.drawText(DECIMAL_FORMAT.format(p.sold_products),      x + colWidths[7] - 9, baseline, rightAlignPaint); x += colWidths[7];
		canvas.drawText(CURRENCY_FORMAT.format(p.averageSellingPrice), x + colWidths[8] - 9, baseline, rightAlignPaint); x += colWidths[8];
		canvas.drawText(CURRENCY_FORMAT.format(p.total_cash),        x + colWidths[9] - 9, baseline, rightAlignPaint);
	}
	
	public void drawGrandTotal(Canvas canvas, int y, Paint[] paints,
	double ini, double ent, double aju, double ava,
	Double fin, double ven, double imp) {
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
		x += colWidths[0] + colWidths[1];
		
		canvas.drawText(DECIMAL_FORMAT.format(ini), x + colWidths[2] - 9, baseline, rightTotal); x += colWidths[2];
		canvas.drawText(DECIMAL_FORMAT.format(ent), x + colWidths[3] - 9, baseline, rightTotal); x += colWidths[3];
		canvas.drawText(DECIMAL_FORMAT.format(aju), x + colWidths[4] - 9, baseline, rightTotal); x += colWidths[4];
		canvas.drawText(DECIMAL_FORMAT.format(ava), x + colWidths[5] - 9, baseline, rightTotal); x += colWidths[5];
		
		String finalStr = (fin != null) ? DECIMAL_FORMAT.format(fin) : "-";
		canvas.drawText(finalStr, x + colWidths[6] - 9, baseline, rightTotal); x += colWidths[6];
		
		canvas.drawText(DECIMAL_FORMAT.format(ven), x + colWidths[7] - 9, baseline, rightTotal); x += colWidths[7];
		x += colWidths[8];
		canvas.drawText(CURRENCY_FORMAT.format(imp), x + colWidths[9] - 9, baseline, rightTotal);
	}
	
	public int[] getColWidths() {
		return colWidths;
	}
}