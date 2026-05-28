package com.nexus.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class InventoryCalculations {
	
	public static class Sale {
		public int id;
		public int productId;
		public double cost;
		public double price;
		public String productName;
		public String productCode;
		public String productUnit;
		public double quantity;  // CAMBIADO: int → double
		public double subtotal;
		public String seller;
		public String saleDate;
		public int inventoryId;
		public String createdAt;
		
		public Sale(JSONObject json) throws JSONException {
			id = json.optInt("id", 0);
			productId = json.optInt("productId", 0);
			cost = json.optDouble("cost", 0);
			price = json.optDouble("price", 0);
			productName = json.optString("productName");
			productCode = json.optString("productCode");
			productUnit = json.optString("productUnit");
			quantity = json.optDouble("quantity", 0);  // CAMBIADO: optInt → optDouble
			subtotal = json.optDouble("subtotal", 0);
			seller = json.optString("seller");
			saleDate = json.optString("saleDate");
			inventoryId = json.optInt("inventoryId", 0);
			createdAt = json.optString("createdAt");
		}
	}
	
	public static class Product {
		public String code;
		public String name;
		public String unit;
		public double initial_products;
		public double incoming_products;
		public double losses;
		public Double final_products;
		public double price;
		public double available_products; // CAMBIADO: int → double
		public double sold_products; // CAMBIADO: int → double
		public double total_cash; // derived
		public double salesPercentage; // derived
		public double averageSellingPrice; // derived
		public List<Sale> sales; // Ventas del producto
		
		public Product(JSONObject json) throws JSONException {
			code = json.optString("code");
			name = json.optString("name");
			unit = json.optString("unit");
			initial_products = json.optDouble("initial_products", 0);
			incoming_products = json.optDouble("incoming_products", 0);
			losses = json.optDouble("losses", 0);
			final_products = json.isNull("final_products") ? null : json.optDouble("final_products");
			price = json.optDouble("price", 0);
			
			// Parsear ventas si existen
			sales = new ArrayList<>();
			if (json.has("sales") && !json.isNull("sales")) {
				JSONArray salesArray = json.getJSONArray("sales");
				for (int i = 0; i < salesArray.length(); i++) {
					sales.add(new Sale(salesArray.getJSONObject(i)));
				}
			}
			
			// Cálculos derivados basados en ventas
			available_products = initial_products + incoming_products - losses;  // CAMBIADO: sin casting a int
			
			// Calcular vendidos y total_cash desde ventas
			double soldFromSales = 0;  // CAMBIADO: int → double
			double totalCashFromSales = 0;
			for (Sale sale : sales) {
				soldFromSales += sale.quantity;
				totalCashFromSales += sale.subtotal;
			}
			
			sold_products = soldFromSales;
			total_cash = totalCashFromSales;
			
			// Calcular final_products basado en ventas si no está definido
			if (final_products == null) {
				final_products = Math.max(0, available_products - sold_products);
			}
			
			// Calcular porcentaje de ventas y precio promedio
			if (available_products > 0) {
				salesPercentage = (sold_products * 100.0 / available_products);
				} else {
				salesPercentage = 0;
			}
			
			if (sold_products > 0) {
				averageSellingPrice = total_cash / sold_products;
				} else {
				averageSellingPrice = price;
			}
			
			// Redondear a 2 decimales
			total_cash = Math.round(total_cash * 100.0) / 100.0;
			averageSellingPrice = Math.round(averageSellingPrice * 100.0) / 100.0;
			salesPercentage = Math.round(salesPercentage * 100.0) / 100.0;
		}
	}
	
	public static class Totals {
		public double initial_products = 0;
		public double incoming_products = 0;
		public double losses = 0;
		public double available_products = 0;  // CAMBIADO: int → double
		public double final_products = 0;
		public double sold_products = 0;  // CAMBIADO: int → double
		public double total_cash = 0;
		public double salesPercentage = 0;
		public double averageSellingPrice = 0;
	}
	
	// Parsea JSONArray a List<Product>
	public static List<Product> parseProducts(JSONArray productsJson) throws JSONException {
		List<Product> products = new ArrayList<>();
		for (int i = 0; i < productsJson.length(); i++) {
			products.add(new Product(productsJson.getJSONObject(i)));
		}
		return products;
	}
	
	// Calcula los totales sumando productos derivados
	public static Totals calculateTotals(List<Product> products) {
		Totals totals = new Totals();
		for (Product p : products) {
			totals.initial_products += p.initial_products;
			totals.incoming_products += p.incoming_products;
			totals.losses += p.losses;
			totals.available_products += p.available_products;
			totals.final_products += (p.final_products == null) ? 0 : p.final_products;
			totals.sold_products += p.sold_products;
			totals.total_cash += p.total_cash;
		}
		
		// Calcular promedios totales
		if (totals.available_products > 0) {
			totals.salesPercentage = Math.round((totals.sold_products * 100.0 / totals.available_products) * 100.0) / 100.0;
		}
		
		if (totals.sold_products > 0) {
			totals.averageSellingPrice = Math.round((totals.total_cash / totals.sold_products) * 100.0) / 100.0;
		}
		
		return totals;
	}
}
