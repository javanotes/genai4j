package com.reactiveminds.genai.core.llm;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import com.reactiveminds.genai.core.EnvUils;
import com.reactiveminds.genai.core.LanguageModel;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenizer;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceModelName;

@Component("hugging")
@ConditionalOnProperty(name = "llm", havingValue = "hugging")
public class HuggingFaceModel implements LanguageModel{

	private Logger log = LoggerFactory.getLogger(getClass());
	@Autowired
	ModelConfig config;
	@PostConstruct
	void init() {
		log.info("LLM: {} will be used", config.getModel());
	}
	@Override
	public ChatLanguageModel getLLM() {
		return HuggingFaceChatModel
				.builder()
                .accessToken(EnvUils.HF_API_KEY)
                .modelId(Optional.ofNullable(config.getModel()).orElse(HuggingFaceModelName.TII_UAE_FALCON_7B_INSTRUCT))
                .timeout(Duration.ofSeconds(15))
                .temperature(0.2)
                .maxNewTokens(20)
                .waitForModel(true)
                .build();
	}
	
	@Override
	public Tokenizer getTokenizer() {
		try {
			return StringUtils.isNotEmpty(config.getTokenizer()) ? 
					new HuggingFaceTokenizer(ResourceUtils.getFile(config.getTokenizer()).toPath()) : 
						new HuggingFaceTokenizer();
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}
	@Override
	public EmbeddingModel getEmbeddingModel() {
		return new HuggingFaceEmbeddingModel.HuggingFaceEmbeddingModelBuilder()
				.accessToken(EnvUils.HF_API_KEY)
				.build();
	}
}