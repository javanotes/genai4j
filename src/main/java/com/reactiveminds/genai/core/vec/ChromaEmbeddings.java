package com.reactiveminds.genai.core.vec;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.reactiveminds.genai.core.AbstractEmbeddings;
import com.reactiveminds.genai.core.LanguageModel;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;

@ConditionalOnProperty(name = "vector.db", havingValue = "chroma")
@Component("chroma")
@Order(0)
public class ChromaEmbeddings extends AbstractEmbeddings {

	@Autowired
	Environment env;
	public ChromaEmbeddings(@Autowired
			LanguageModel chatModel) {
		super(chatModel.getEmbeddingModel());
	}
	@PreDestroy
	void shutdown() {

	}

	@Override
	protected void initStore() {
		log.debug("Connecting to Chroma DB ..");
		embeddingStore = ChromaEmbeddingStore.builder().baseUrl(env.getProperty("vector.db.url")).build();
	}

}
