package com.webvirt.loader.policies;

/**
 * Estrategia para determinar qué assets son críticos para precarga.
 * Permite diferentes criterios según bundler o tipo de aplicación.
 * 
 * @since 3.6.0
 */
public interface PrecacheStrategy {

    /**
     * Determina si un asset debe ser precargado asíncronamente.
     *
     * @param path Path del asset (prefijo del handler)
     * @return true si el asset es crítico y debe precargarse
     */
    boolean isCriticalAsset(String path);
}