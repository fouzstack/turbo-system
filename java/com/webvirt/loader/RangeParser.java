package com.webvirt.loader;

/**
 * Parser de cabeceras HTTP Range.
 *
 * Soporta formatos:
 * - bytes=0-499 (primeros 500 bytes)
 * - bytes=500-999 (bytes 500 al 999)
 * - bytes=-500 (últimos 500 bytes)
 * - bytes=500- (desde el byte 500 hasta el final)
 *
 * @since 1.0.0
 */
public class RangeParser {

    private RangeParser() {
        throw new AssertionError("No instances");
    }

    /**
     * Parsea una cabecera Range.
     *
     * @param rangeHeader Valor de la cabecera (ej: "bytes=0-1023")
     * @param fileSize    Tamaño total del archivo
     * @return long[] {start, end} o null si el rango es inválido
     */
    public static long[] parse(String rangeHeader, long fileSize) {
        if (rangeHeader == null || fileSize <= 0) return null;

        if (!rangeHeader.startsWith("bytes=")) return null;

        String rangeValue = rangeHeader.substring(6).trim();
        int dashIndex = rangeValue.indexOf('-');

        if (dashIndex < 0) return null;

        try {
            String startStr = rangeValue.substring(0, dashIndex).trim();
            String endStr = rangeValue.substring(dashIndex + 1).trim();

            long start, end;

            if (startStr.isEmpty()) {
                // bytes=-500 → últimos 500 bytes
                long suffixLength = Long.parseLong(endStr);
                if (suffixLength <= 0) return null;
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else if (endStr.isEmpty()) {
                // bytes=500- → desde el byte 500 hasta el final
                start = Long.parseLong(startStr);
                if (start < 0) return null;
                end = fileSize - 1;
            } else {
                // bytes=0-499
                start = Long.parseLong(startStr);
                end = Long.parseLong(endStr);
                if (start < 0 || end < 0 || start > end) return null;
            }

            // Validar límites contra fileSize
            if (start >= fileSize) return null;
            if (end >= fileSize) end = fileSize - 1;

            return new long[] { start, end };

        } catch (NumberFormatException e) {
            return null;
        }
    }
}