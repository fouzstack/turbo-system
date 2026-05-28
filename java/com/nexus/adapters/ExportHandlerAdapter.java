package com.nexus.adapters;

import androidx.annotation.NonNull;

import com.nexus.handlers.ExportHandler;

import com.nexus.NexusException;
import com.nexus.NexusHandler;
import java.util.HashMap;
import java.util.Map;

/**
* Adapter that exposes ExportHandler through Nexus.
*
* JavaScript usage:
* Nexus.call('export', { data: jsonString })
*/
public class ExportHandlerAdapter implements NexusHandler {
	
	private final ExportHandler exportHandler;
	
	public ExportHandlerAdapter(@NonNull ExportHandler exportHandler) {
		this.exportHandler = exportHandler;
	}
	
	@NonNull
	@Override
	public String getName() {
		return "export";
	}
	
	@NonNull
	@Override
	public Object handle(@NonNull Map<String, Object> params) throws Exception {
		String data = (String) params.get("data");
		if (data == null || data.isEmpty()) {
			throw new NexusException("INVALID_PARAMS", "The 'data' parameter is required");
		}
		
		exportHandler.exportJsonToFile(data);
		
		Map<String, Object> result = new HashMap<>();
		result.put("status", "ok");
		result.put("message", "File exported successfully");
		return result;
	}
}