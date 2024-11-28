package com.reactiveminds.genai.core.vec;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.reactiveminds.genai.core.AbstractEmbeddings;
import com.reactiveminds.genai.core.LanguageModel;
import com.reactiveminds.genai.core.RetrievalAugmentationSupport;
import com.reactiveminds.genai.core.llm.CrossEncoderScoring;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.neo4j.Neo4jContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;

@ConditionalOnProperty(name = "vector.db", havingValue = "neo4j", matchIfMissing = true)
@Component("neo4j")
@Order(1)
public class Neo4jEmbeddings extends AbstractEmbeddings implements RetrievalAugmentationSupport{
	public Neo4jEmbeddings(@Autowired
			LanguageModel chatModel) {
		super(chatModel.getEmbeddingModel());
		this.chatModel = chatModel;
	}
	
	private Driver driver;
	public Driver getDriver() {
		Assert.notNull(driver, "neo4j not instantiated!");
		return driver;
	}

	final String INDEX_NAME = "graph_embeddings";
	final LanguageModel chatModel;
	@Autowired
	Environment env;
	
	// underneath uses LLM to `generate` a cypher query!
	public ContentRetriever graphContentRetriever(PromptTemplate template) {		
		return new Neo4jContentRetriever(new Neo4jGraph(driver), chatModel.getLLM(), template) {
			@Override
			public List<Content> retrieve(Query query) {
				List<Content> graph=List.of();
				try {
					graph = super.retrieve(query);
				} catch (Exception e) {
					log.error("cypher execution failed!", e);
				}
				List<TextSegment> texts = graph.stream().map(Content::textSegment).toList();
				log.info("graph responded {} items: {}", graph.size(), texts);
				return graph;
			}
		};
	}
	
	/**
	 * Get relevant result from knowledge graph
	 * @param question
	 * @return
	 */
	private List<TextSegment> findRelevant(String question){
		List<Content> contents = graphContentRetriever(null).retrieve(Query.from(question));
		log.debug(contents.toString());
		return contents.stream()
		.map(Content::textSegment)
		.collect(Collectors.toList());
	}
	
	public Set<String> askGraph(String question) {	
		return findRelevant(question).stream()
		.map(TextSegment::text).collect(Collectors.toSet());
	}
	public void saveGraph(List<String> cyphers) {
		//List<String> cyphers = GraphCypherUtil.createRelationships(KnowledgeGraphModel.objectToJackson(elements));
		cyphers.stream().forEach(c -> log.info(c));
		writeAll(cyphers);
	}
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
		log.info("cross encoder scoring: {}", score.content());
	}
	public void write(String cypher, Map<String, Object> params) {
		try ( Session session = driver.session() )
		{
			
			try(Transaction tx = session.beginTransaction()){
				tx.run(cypher, params);
				tx.commit();
			}
			
		}
	}
	public Result read(String cypher, Map<String, Object> params) {
		try ( Session session = driver.session() )
		{
			
			try(Transaction tx = session.beginTransaction()){
				return tx.run(cypher, params);
			}
			
		}
	}
	public Record readOne(String cypher, Map<String, Object> params) {
		try ( Session session = driver.session() )
		{
			
			try(Transaction tx = session.beginTransaction()){
				Result res = tx.run(cypher, params);
				if(res.hasNext()) {
					return res.next();
				}
			}
			
		}
		return null;
	}
	public void writeAll(List<String> cypher) {
		try ( Session session = driver.session() )
		{
			Transaction tx = session.beginTransaction();
			try {
				cypher.forEach(each -> tx.run(each));
				tx.commit();
			} 
			finally {
				tx.close();
			}
		}
	}
	void reset() {
		try ( Session session = driver.session() )
		{
			
			Map<String, Object> params = Map.of("name", INDEX_NAME);
            session.run("DROP INDEX $name IF EXISTS", params);
            var res = session.run("CALL db.awaitIndexes($timeout)", 
                    Map.of("timeout", 60L)
            );
            log.warn("reset done: {}", res.consume());
		}
	}
	@Override
	public RetrievalAugmentor createRetrievalAugmentor(Function<String, Prompt> promptGenerator, int maxResults,
			double minScore) {
		QueryRouter query = new DefaultQueryRouter(graphContentRetriever(null), EmbeddingStoreContentRetriever.builder()
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
