package com.reactiveminds.genai.core;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import com.reactiveminds.genai.core.vec.ChromaEmbeddings;
import com.reactiveminds.genai.core.vec.Neo4jEmbeddings;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

public abstract class AbstractEmbeddings implements EmbeddingsRunner,ApplicationRunner{
	protected Logger log = LoggerFactory.getLogger(getClass());
	/**
	 * Instantiate from subclass constructor, if required. If not, BgeSmallEnV15QuantizedEmbeddingModel
	 * will be used.
	 */
	protected final EmbeddingModel embeddingModel;
	protected AbstractEmbeddings(EmbeddingModel embeddingModel) {
		super();
		this.embeddingModel = embeddingModel;
	}
	protected EmbeddingStore<TextSegment> embeddingStore;
	/**
	 * Build the specific embedding store provider.
	 * @see ChromaEmbeddings
	 * @see Neo4jEmbeddings
	 */
	protected abstract void initStore();
	private String relevanceQueryPrepend;
	
	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.info("Initializing {} ..", getClass().getSimpleName());
				
		if(embeddingModel instanceof BgeSmallEnV15QuantizedEmbeddingModel) {
			//TODO : the model selection will be delegated
			relevanceQueryPrepend = "Represent this sentence for searching relevant passages:";
		}
		initStore();
		runRelevance();
		log.info("Vector db ready; using embedding model: {}", getEmbeddingModel().getClass().getSimpleName());
	}
	@Override
	public void embedSegments(List<TextSegment> segments) {
		List<Embedding> embeddings = getEmbeddingModel().embedAll(segments).content();
		getEmbeddingStore().addAll(embeddings, segments);
	}
	public Embedding createEmbedding(TextSegment text) {
		return getEmbeddingModel().embed(text).content();
	}
	
	@Override
	public EmbeddingSearchResult<TextSegment> findRelevant(String question, int maxResults, double minScore) {

		// Embed the question
		Embedding questionEmbedding = getEmbeddingModel().embed(relevanceQueryPrepend != null ? relevanceQueryPrepend.concat(question) : question).content();
		EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder().maxResults(maxResults)
				.minScore(minScore).queryEmbedding(questionEmbedding).build();
		return getEmbeddingStore().search(searchRequest);
	}
	protected void runRelevance() {

		TextSegment segment1 = TextSegment.from("I like football.");
		Embedding embedding1 = getEmbeddingModel().embed(segment1).content();
		getEmbeddingStore().add(embedding1, segment1);

		TextSegment segment2 = TextSegment.from("The weather is good today.");
		Embedding embedding2 = getEmbeddingModel().embed(segment2).content();
		getEmbeddingStore().add(embedding2, segment2);

		EmbeddingSearchResult<TextSegment> relevant = findRelevant("What is your favourite sport?", 2, 0.7);		
		EmbeddingMatch<TextSegment> embeddingMatch = relevant.matches().get(0);

		log.info("test relevance score .. " + embeddingMatch.score());
		log.info(embeddingMatch.embedded().text()); // I like football.
		
		
	}

	public EmbeddingStore<TextSegment> getEmbeddingStore() {
		return embeddingStore;
	}

	public EmbeddingModel getEmbeddingModel() {
		return embeddingModel;
	}

}
