package com.reactiveminds.genai.play;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.ResourceUtils;

import com.reactiveminds.genai.core.AbstractEmbeddings;
import com.reactiveminds.genai.core.Assistant;
import com.reactiveminds.genai.core.LanguageModel;
import com.reactiveminds.genai.core.SemanticSplitter;
import com.reactiveminds.genai.core.vec.Neo4jEmbeddings;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

abstract class AbstractFlow {
	protected Logger log = LoggerFactory.getLogger(getClass());
	@Autowired
	@Qualifier("neo4j")
	AbstractEmbeddings vectordb;
	@Autowired
	SemanticSplitter semantic;
	@Autowired
	LanguageModel chatModel;
	
	
	
	
	static PromptTemplate PROMPT_WITHOUT_RAG = PromptTemplate.from(
            "Answer the following question to the best of your ability. If you are unsure, say don't know:\n"
                    + "\n"
                    + "Question:\n"
                    + "{{question}}\n"
                    );
	private static final Pattern LINK_REGEX = Pattern.compile("((http:\\/\\/|https:\\/\\/)?(www.)?(([a-zA-Z0-9-]){2,2083}\\.){1,4}([a-zA-Z]){2,6}(\\/(([a-zA-Z-_\\/\\.0-9#:?=&;,]){0,2083})?){0,2083}?[^ \\n]*)");
	/**
	 * using semantic document splitting
	 * @param resource
	 * @param maxTokens
	 * @param maxOverlapTokens
	 * @return
	 * @throws IOException
	 */
	public List<TextSegment> splitDocument(InputStream resource, int maxTokens, int maxOverlapTokens, boolean useSemanticSplit) throws IOException {
		// Load the document that includes the information you'd like to "chat" about with the model.		
        DocumentParser documentParser = new ApacheTikaDocumentParser();
        Document document = documentParser.parse(resource);
        DocumentSplitter semanticSplitter = useSemanticSplit ? new SemanticSplitter(maxTokens, vectordb, chatModel) 
        		: DocumentSplitters.recursive(maxTokens, maxOverlapTokens, chatModel.getTokenizer());
        return semanticSplitter.split(document);
	}
	/**
	 * using recursive document splitting
	 * @param contents
	 * @param maxTokens
	 * @param maxOverlap
	 * @return
	 */
	protected List<TextSegment> splitContent(List<String> contents, int maxTokens, int maxOverlap) {		     
        DocumentSplitter splitter = DocumentSplitters.recursive(maxTokens, maxOverlap, chatModel.getTokenizer());
        return splitter.splitAll(contents.stream().map(Document::from).collect(Collectors.toList()));
	}
	
	protected List<String> extractLinks(List<TextSegment> segments) {
		
		List<String> urls = segments
				.parallelStream()
				.flatMap(text -> {
					String s = text.text();
					Matcher m = LINK_REGEX.matcher(s);
					List<String> links = new ArrayList<String>();
					while(m.find()) {
						links.add(m.group());
					}
					return links.stream();
				})
				.map(String::toLowerCase)
				.filter(link -> link.startsWith("http") || link.startsWith("www"))
				.map(url -> {
					try {
						org.jsoup.nodes.Document d = Jsoup.connect(url).get();
						log.info("url title : {}", d.title());
						log.debug("url content : {}", d.text());
						return d.text();
					}
					catch(Exception e) {
						log.warn("unable to extract url: {}. Error ==> {}", url, e.getMessage());
						log.debug("", e);
					}
					return "";
				})
				.collect(Collectors.toList());
				
				
		return urls;
	}
	

	
	public void embedSegments(String resourcePath) {
		//
		try(InputStream in = ResourceUtils.getURL(resourcePath).openStream()){
			
			List<TextSegment> segments = splitDocument(in, 100, 20, true);						
			//List<TextSegment> segments = semantic.loadChunks(resourcePath, 5);
			
			log.info("{} segments to embed for `{}`. Using vector store {}", segments.size(), resourcePath, vectordb.getClass().getSimpleName());
			vectordb.embedSegments(segments);
			
			//extractLinks(segments);
			/*
			log.info("logging {} semantic segments for `{}`. ", segments.size(), resourcePath);
			segments.forEach(t -> {
				log.info("+ ------------------ ++");
				log.info(t.text());
			}
			);*/
			
		}  
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	
	
	public String ask(PromptTemplate promptTemplate, String question) {
		EmbeddingSearchResult<TextSegment> relevantEmbeddings = vectordb.findRelevant(question, 3, 0.8);
		String information = relevantEmbeddings.matches().stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n"));
		log.info("found {} relevant embeddings", CollectionUtils.isEmpty(relevantEmbeddings.matches()) ? 0 : relevantEmbeddings.matches().size());
		//log.info("+++ RAG info +++ \\n {}", information);
		//log.info("+++++++++++++++++");
		
		// the placeholders are hardcoded here
		Prompt prompt = promptTemplate.apply(Map.of("question", question,
									"information", information
				));		
        
        log.debug(prompt.toString());
        
        AiMessage aiMessage = chatModel.getLLM(). generate(prompt.toUserMessage()).content();

        // See an answer from the model
        return aiMessage.text();
	}
	
	
	public String chat(PromptTemplate promptTemplate, String question, boolean queryCompression, int maxChatMemory) {
		EmbeddingSearchResult<TextSegment> relevantEmbeddings = vectordb.findRelevant(question, 3, 0.8);
		String information = relevantEmbeddings.matches().stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n"));
		
		log.info("found {} relevant embeddings", CollectionUtils.isEmpty(relevantEmbeddings.matches()) ? 0 : relevantEmbeddings.matches().size());
		
		// the placeholders are hardcoded here
		Prompt prompt = promptTemplate.apply(Map.of("question", question,
									"information", information
				));

        
        ContentRetriever embeddingStoreContentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(vectordb.getEmbeddingStore())
                .embeddingModel(vectordb.getEmbeddingModel())
                .maxResults(3)
                .minScore(0.8)
                .build();
        
        
        // query router that will route each query to both retrievers.
        QueryRouter queryRouter = new DefaultQueryRouter(embeddingStoreContentRetriever, // vector embeddings
        		((Neo4jEmbeddings) vectordb.getEmbeddingStore()).graphContentRetriever(null)) // knowledge graph
        		;
        
        log.debug(prompt.toString());
        
        Assistant assist = AiServices.builder(Assistant.class)
        .chatLanguageModel(chatModel.getLLM())
        .retrievalAugmentor(queryCompression ? DefaultRetrievalAugmentor.builder()
                	.queryRouter(queryRouter)
                	.queryTransformer(new CompressingQueryTransformer(chatModel.getLLM()))
                .build() : DefaultRetrievalAugmentor.builder()
            	.queryRouter(queryRouter)
            .build())
        
        .chatMemory(MessageWindowChatMemory.withMaxMessages(maxChatMemory))
        .build();
               
        return assist.answer(prompt.text());
        
	}
}
