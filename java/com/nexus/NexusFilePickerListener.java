package com.nexus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Listener for when a handler needs to open the file picker.
 * The developer implements this interface in their Activity/Fragment
 * to manage file selection.
 */
public interface NexusFilePickerListener {

    /**
     * Called when a handler needs the user to select a file.
     *
     * @param callbackId ID of the callback that must be resolved with the result
     * @param mimeTypes  Accepted MIME types (may be null for any type)
     */
    void onFilePickerNeeded(@NonNull String callbackId, @Nullable String[] mimeTypes);
}