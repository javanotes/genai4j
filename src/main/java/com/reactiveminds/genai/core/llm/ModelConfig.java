package com.reactiveminds.genai.core.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm")
public class ModelConfig {
	String apiUrl;
	String apiKey;
	String model;
	String tokenizer;
	public String getTokenizer() {
		return tokenizer;
	}
	public void setTokenizer(String tokenizer) {
		this.tokenizer = tokenizer;
	}
	Double temperature;
	public String getApiUrl() {
		return apiUrl;
	}
	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}
	public String getApiKey() {
		return apiKey;
	}
	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
	public String getModel() {
		return model;
	}
	public void setModel(String model) {
		this.model = model;
	}
	public Double getTemperature() {
		return temperature;
	}
	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}
	
}
