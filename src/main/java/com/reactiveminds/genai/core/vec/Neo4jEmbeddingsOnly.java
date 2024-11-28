package com.reactiveminds.genai.core.vec;

import java.util.List;
import java.util.function.Function;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.reactiveminds.genai.core.AbstractEmbeddings;
import com.reactiveminds.genai.core.LanguageModel;
import com.reactiveminds.genai.core.RetrievalAugmentationSupport;
import com.reactiveminds.genai.core.llm.CrossEncoderScoring;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingStore;
/**
 * @deprecated only for comparison display *DO NOT USE*
 */
@Component
@ConditionalOnProperty(name = "vector.db", havingValue = "neo4j", matchIfMissing = true)
@Order(1)
public class Neo4jEmbeddingsOnly extends AbstractEmbeddings implements RetrievalAugmentationSupport{
	public Neo4jEmbeddingsOnly(@Autowired
			LanguageModel chatModel) {
		super(chatModel.getEmbeddingModel());
		this.chatModel = chatModel;
	}
	private Driver driver;
	final String INDEX_NAME = "embeddings";
	final LanguageModel chatModel;
	@Autowired
	Environment env;
	
	@Override
	protected void initStore() {
		
		driver = GraphDatabase.driver(env.getProperty("vector.db.url"), AuthTokens.basic(env.getProperty("vector.db.auth_user"), env.getProperty("vector.db.auth_password")));
		log.debug("Connecting to Neo4j ..");
		//reset();
		embeddingStore = Neo4jEmbeddingStore.builder()
				.driver(driver)
				.dimension(384)
				.indexName(INDEX_NAME)
				.label(INDEX_NAME.concat("-Document"))
				.build();		
				configureOnnx();
	}
	private OnnxScoringModel scoringModel;
	private void configureOnnx() {
		scoringModel = new CrossEncoderScoring();
		Response<Double> score = scoringModel.score("Football is the most popular sport on earth.", "What is football?");
		log.debug("cross encoder scoring: {}", score.content());
	}

	@Override
	public RetrievalAugmentor createRetrievalAugmentor(Function<String, Prompt> promptGenerator, int maxResults,
			double minScore) {
		QueryRouter query = new DefaultQueryRouter(EmbeddingStoreContentRetriever.builder()
				.embeddingModel(getEmbeddingModel())
				.embeddingStore(getEmbeddingStore())
				.maxResults(maxResults)
				.minScore(minScore)
				.build());
		
		return 
				DefaultRetrievalAugmentor.builder()
		.queryRouter(query)
		.contentAggregator(new ReRankingContentAggregator(scoringModel))
		.contentInjector(new DefaultContentInjector() {
			@Override
			protected Prompt createPrompt(ChatMessage chatMessage, List<Content> contents) {
		        return promptGenerator.apply(format(contents));
			}
		}) // system prompts?
		.build();
	}

	

}
