package com.reactiveminds.genai.core.llm;

import java.time.Duration;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.reactiveminds.genai.core.LanguageModel;
import com.reactiveminds.genai.core.EnvUils;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;

@Component("openai")
@ConditionalOnProperty(name = "llm", havingValue = "openai", matchIfMissing = true)
public class OpenAiModel implements LanguageModel{
	private Logger log = LoggerFactory.getLogger(getClass());
	@Autowired
	ModelConfig config;
	@PostConstruct
	void init() {
		log.info("LLM: {} will be used", config.getModel());
	}
	@Override
	public ChatLanguageModel getLLM() {
		return OpenAiChatModel.builder()
        .apiKey(EnvUils.OPENAI_API_KEY)
        .organizationId(EnvUils.OPENAI_ORG_ID)
        .modelName(OpenAiChatModelName.GPT_4_O_MINI)
        .timeout(Duration.ofSeconds(60))
        .temperature(config.getTemperature())
        .build();
	}
	
}
/*
 * @Component("github")
 * 
 * @ConditionalOnProperty(name = "llm", havingValue = "github") public class
 * GithubModel implements LanguageModel{
 * 
 * @Override public ChatLanguageModel getLLM() { return
 * GitHubModelsChatModel.builder() .gitHubToken(Utils.GITHUB_TOKEN)
 * .modelName("gpt-4o-mini") .build(); }
 * 
 * }
 */