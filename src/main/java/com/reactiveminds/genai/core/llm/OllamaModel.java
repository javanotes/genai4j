package com.reactiveminds.genai.core.llm;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.time.Duration;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import com.reactiveminds.genai.core.LanguageModel;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenizer;
import dev.langchain4j.model.ollama.OllamaChatModel;

@Component("ollama")
@ConditionalOnProperty(name = "llm", havingValue = "ollama")
public class OllamaModel implements LanguageModel{
	@Autowired
	ModelConfig config;
	private Logger log = LoggerFactory.getLogger(getClass());
	@PostConstruct
	void init() {
		log.info("LLM: {} will be used", config.getModel());
	}
	public Tokenizer getTokenizer() {
		try {
			return new HuggingFaceTokenizer(ResourceUtils.getFile("classpath:tokenizers/mistral/tokenizer.json").toPath());
		} 
		catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}
	@Override
	public ChatLanguageModel getLLM() {
		//StreamingChatLanguageModel
		return OllamaChatModel.builder()
	    .baseUrl(config.getApiUrl())
	    .modelName(config.getModel())
	    .temperature(config.getTemperature())
	    .timeout(Duration.ofSeconds(300))
	    .build();
	}
	
}