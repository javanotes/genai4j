package com.reactiveminds.genai.graphrag;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.reactiveminds.genai.core.LanguageModel;
import com.reactiveminds.genai.core.RetrievalAugmentationService;
import com.reactiveminds.genai.core.llm.CrossEncoderScoring;
import com.reactiveminds.genai.core.vec.DocumentVector;
import com.reactiveminds.genai.core.vec.Neo4jEmbeddings;
import com.reactiveminds.genai.play.KnowledgeGraphModelV2;
import com.reactiveminds.genai.play.PromptService;
import com.reactiveminds.genai.utils.TextSimilarityFunction;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.mapping.DateProperty;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.knn_search.KnnSearchQuery;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;

// For indexing news articles with title and date in Elastic. Not a generic class.
@Service("elastic")
public class NewsArticleRAService implements RetrievalAugmentationService{
	private static final Logger log = LoggerFactory.getLogger(NewsArticleRAService.class);
	@Autowired
	PromptService promptService;
	private final Neo4jEmbeddings neo4jDriver;
	private final LanguageModel languageModel;
	
	
	private ElasticsearchClient client;
	public NewsArticleRAService(@Autowired LanguageModel chatModel, @Autowired
			Neo4jEmbeddings neo4jEmbeddings) {
		this.languageModel = chatModel;
		neo4jDriver = neo4jEmbeddings;
	}
	

	private Duration relevantHistoryDuration = Duration.ofDays(90);
	
	//private MatchQuery matchQuery = new MatchQuery.Builder().field("title").query("some text").build();
	private CrossEncoderScoring scoring = new CrossEncoderScoring();
	
	private static DateTimeFormatter format = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static Query rangeQuery(LocalDate from, LocalDate to) {
		RangeQuery rangeQuery = new RangeQuery.Builder().field("date").from(from.format(format)).to(to.format(format)).format("dd.MM.yyyy").build();
		return new BoolQuery.Builder().must(List.of(rangeQuery._toQuery())).build()._toQuery();
	}
	@SuppressWarnings("unused")
	@Deprecated
	private static KnnSearchQuery buildQuery(Embedding vector, int k, LocalDate rangeFrom, LocalDate rangeTill) {     
		/*
		 * KnnSearchResponse<DocumentVector> resp = client.knnSearch(new
		 * KnnSearchRequest.Builder() .knn(buildQuery(vector, k))
		 * .filter(List.of(rangeQuery(rangeFrom, rangeTill))) .index("doc_vector")
		 * .build(), DocumentVector.class);
		 */
		return new KnnSearchQuery.Builder()
				.field("vector")
				.queryVector(vector.vectorAsList())
				.numCandidates(100)
				.k(k)
				.build();
	}
	
	private static KnnQuery buildKnnQuery(Embedding vector, int k, LocalDate rangeFrom, LocalDate rangeTill) {
		KnnQuery.Builder krb = new KnnQuery.Builder()
                .field("vector")
                .queryVector(vector.vectorAsList());

        krb.filter(rangeQuery(rangeFrom, rangeTill));
        krb.numCandidates(100);
        krb.k(k);
        return krb.build();
        
		
	}
	/**
	 * Search cosine similarity, re-rank by title relevance.
	 * @param text
	 * @param rangeFrom
	 * @param rangeTill
	 * @param maxResults
	 * @return
	 * @throws ElasticsearchException
	 * @throws IOException
	 */
	public List<DocumentVector> knnQuery(String text, LocalDate rangeFrom, LocalDate rangeTill, int maxResults) throws ElasticsearchException, IOException {
		Embedding vector = languageModel.getEmbeddingModel().embed(text).content();
		HitsMetadata<DocumentVector> hits = knnQuery(vector, rangeFrom, rangeTill, maxResults);
		
		if(hits.hits().size() == 0) {
			return null;
		}
		//rerank by title?
		List<DocumentVector> docs = hits.hits().stream().map(h -> h.source()).toList();
		List<Double> scores = scoring.scoreAll(docs.stream().map(d -> TextSegment.from(d.getTitle())).toList(), text).content();
		
		return IntStream.range(0, scores.size()).mapToObj(i -> Pair.of(i, scores.get(i)))
		.sorted(Collections.reverseOrder( (p1,p2) -> Double.compare(p1.getRight(), p2.getRight())))
		.map(p -> docs.get(p.getLeft())).toList();
		
	}
	private HitsMetadata<DocumentVector> knnQuery(Embedding vector, LocalDate rangeFrom, LocalDate rangeTill, int maxResults) throws ElasticsearchException, IOException {
		
		SearchResponse<DocumentVector> resp = client.search(new SearchRequest.Builder()
				.index("doc_vector")
				.knn(buildKnnQuery(vector, maxResults, rangeFrom, rangeTill))
				.build(), DocumentVector.class);
		log.info("found {} hits", resp.hits().hits().size());
		return resp.hits();
		
	}
	private void createGraph(String document) {	
		List<KnowledgeGraphModelV2> modelList = promptService.knowledgeGraph(document, 1024, 64, null, false);
		log.info("Saving {} entity relations", modelList.size());
		List<String> cyphers = GraphCypherUtil.createRelationships(modelList, TextSimilarityFunction.JAROWINKLER);
		neo4jDriver.saveGraph(cyphers);		
		log.info("saved knowledge graph");				
	}
	private void createEmbeddings(String title, String document, LocalDate date) {
		String summary = promptService.summarizePrompt(document);
		DocumentVector docVec = new DocumentVector();
		docVec.setDoc(summary);
		docVec.setTitle(title);
		docVec.setVector(languageModel.getEmbeddingModel().embed(summary).content().vector());
		docVec.setDate(format.format(date));
		try {
			bulkIndex(List.of(docVec));
			log.info("doc ingested. size={}, summary={} ",document.length(), summary.length());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	@Override
	public void ingestDocument(ADocument document) {
		createGraph(document.getText());
		createEmbeddings(document.getTitle(), document.getText(), document.getDate());
	}
	private class PartialEmbeddingStore extends InMemoryEmbeddingStore<TextSegment>{
		@Override
		public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
			LocalDate today = LocalDate.now();
	        try {
				HitsMetadata<DocumentVector> docs = knnQuery(referenceEmbedding, today.minusDays(relevantHistoryDuration.toDays()), today, maxResults);
				
				List<EmbeddingMatch<TextSegment>> relevant = docs.hits().stream().filter(h -> h.score() >= minScore)				
				.map(h -> {
					TextSegment text = TextSegment.from(h.source().getDoc());
					return new EmbeddingMatch<>(h.score(), h.id(), Embedding.from(h.source().getVector()), text);
				}).toList();
				log.info("relevant hits: {}", relevant.size());
				return relevant;
			} 
	        catch (ElasticsearchException | IOException e) {
				log.error("knn search error!", e);
			}
	        return List.of();
	    }
		
	}
    
	public List<String> bulkIndex(List<DocumentVector> documents) throws IOException {
        
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (int i = 0; i < documents.size(); i++) {
        	DocumentVector document = documents.get(i);           
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index("doc_vector")
                    .document(document)));
        }

        BulkResponse response = client.bulk(bulkBuilder.build());
        return handleBulkResponse(response);
    }

    private List<String> handleBulkResponse(BulkResponse response) {
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                throwIfError(item.error());
            }
        }
        else {
        	return response.items().stream()
        	.map(BulkResponseItem::id).toList();
        }
        return null;
    }

    private void throwIfError(ErrorCause errorCause) {
        if (errorCause != null) {
            throw new RuntimeException("ElasticSearchException type: " + errorCause.type() + ", reason: " + errorCause.reason());
        }
    }
	@PostConstruct
	protected void initStore() {
		RestClient restClient = RestClient
				  .builder(HttpHost.create("http://localhost:9200"))
				  .build();
		client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
		
		try {
			//client.indices().delete(new DeleteIndexRequest.Builder().index("doc_vector").build()).acknowledged();
			
			BooleanResponse bool = client.indices().exists(new ExistsRequest.Builder().index("doc_vector").build());
			if(!bool.value()) {
				DimensionAwareEmbeddingModel dimModel = (DimensionAwareEmbeddingModel) languageModel.getEmbeddingModel();
				CreateIndexRequest createIndex = new CreateIndexRequest.Builder()
						.index("doc_vector")
						.mappings(new TypeMapping.Builder()
								.properties("title", new Property(new TextProperty.Builder().index(true).build()))
								.properties("date", new Property(new DateProperty.Builder().index(true).format("dd.MM.yyyy").build()))
								.properties("doc", new Property(new TextProperty.Builder().index(false).build()))
								.properties("vector", new Property(new DenseVectorProperty.Builder().index(true).dims(dimModel.dimension()).similarity("cosine").build()))
								.build())
						.build();
				CreateIndexResponse done = client.indices().create(createIndex);
				if(done.acknowledged()) {
					log.info("doc_vector index created ..");
				}
			}
		} catch (ElasticsearchException | IOException e) {
			throw new BeanInitializationException("unable to create vector index", e);
		}		
		
	}
	public Duration getRelevantHistoryDuration() {
		return relevantHistoryDuration;
	}
	public void setRelevantHistoryDuration(Duration relevantHistoryDuration) {
		this.relevantHistoryDuration = relevantHistoryDuration;
	}
	@Override
	public RetrievalAugmentor createRetrievalAugmentor(Function<String, Prompt> promptGenerator, int maxResults,
			double minScore) {
		QueryRouter query = new DefaultQueryRouter(new NewsGraphRetriever(new Neo4jGraph(neo4jDriver.getDriver()), neo4jDriver.getDriver(), languageModel.getLLM()), new ContentRetriever() {
			
			@Override
			public List<Content> retrieve(dev.langchain4j.rag.query.Query query) {
				Embedding embeddedQuery = languageModel.getEmbeddingModel().embed(query.text()).content();

		        PartialEmbeddingStore elasticEmbeddings = new PartialEmbeddingStore();
		        List<EmbeddingMatch<TextSegment>> searchResult = elasticEmbeddings.findRelevant(embeddedQuery, maxResults, minScore);
		        DocumentBySentenceSplitter splitter = new DocumentBySentenceSplitter(1000, 0);
		        return searchResult.stream()
		                .map(EmbeddingMatch::embedded)
		                .flatMap(text -> Arrays.stream(splitter.split(text.text())))
		                .map(Content::from)
		                .collect(toList());
			}
		});
		
		return 
				DefaultRetrievalAugmentor.builder()
		.queryRouter(query)
		// also ranking on the document title (if more than 1 docs)
		.contentAggregator(new ReRankingContentAggregator(scoring))
		.contentInjector(new DefaultContentInjector(PromptTemplate.from(
	            "{{userMessage}}\n" +
	                    "\n" +
	                    "Answer using the following information. Do not provide explanations:\n" +
	                    "{{contents}}"
	    )) {
			@Override
			protected Prompt createPrompt(ChatMessage chatMessage, List<Content> contents) {
				Prompt prompt = promptGenerator.apply(format(contents));
				log.debug(prompt.text());
		        return prompt;
			}
		}) 
		.build();
	}

}
