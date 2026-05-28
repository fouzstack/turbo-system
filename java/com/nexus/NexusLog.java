package com.nexus;

import android.util.Log;

import androidx.annotation.NonNull;

/**
* Logging utility for Nexus.
*
* Provides leveled logging with tag prefixing.
* All methods are static and thread-safe.
*/
public final class NexusLog {
	
	private static final String TAG_PREFIX = "Nexus";
	private static volatile NexusLogLevel globalLevel = NexusLogLevel.INFO;
	
	private NexusLog() {
		// Utility class - no instances
	}
	
	/**
	* Sets the global log level for all Nexus components.
	*
	* @param level Minimum level to log
	*/
	public static void setLevel(@NonNull NexusLogLevel level) {
		globalLevel = level;
	}
	
	/**
	* @return Current global log level
	*/
	@NonNull
	public static NexusLogLevel getLevel() {
		return globalLevel;
	}
	
	/**
	* Debug log - only shown if level >= DEBUG
	*/
	public static void d(@NonNull String tag, @NonNull String message) {
		if (globalLevel.ordinal() >= NexusLogLevel.DEBUG.ordinal()) {
			Log.d(TAG_PREFIX + ":" + tag, message);
		}
	}
	
	/**
	* Info log - only shown if level >= INFO
	*/
	public static void i(@NonNull String tag, @NonNull String message) {
		if (globalLevel.ordinal() >= NexusLogLevel.INFO.ordinal()) {
			Log.i(TAG_PREFIX + ":" + tag, message);
		}
	}
	
	/**
	* Warning log - only shown if level >= WARN
	*/
	public static void w(@NonNull String tag, @NonNull String message) {
		if (globalLevel.ordinal() >= NexusLogLevel.WARN.ordinal()) {
			Log.w(TAG_PREFIX + ":" + tag, message);
		}
	}
	
	/**
	* Error log - always shown
	*/
	public static void e(@NonNull String tag, @NonNull String message) {
		Log.e(TAG_PREFIX + ":" + tag, message);
	}
	
	/**
	* Error log with exception - always shown
	*/
	public static void e(@NonNull String tag, @NonNull String message, @NonNull Throwable t) {
		Log.e(TAG_PREFIX + ":" + tag, message, t);
	}
	
	/**
	* Verbose log - only shown if level >= VERBOSE
	*/
	public static void v(@NonNull String tag, @NonNull String message) {
		if (globalLevel.ordinal() >= NexusLogLevel.VERBOSE.ordinal()) {
			Log.v(TAG_PREFIX + ":" + tag, message);
		}
	}
}