package com.reactiveminds.genai.play;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.reactiveminds.genai.core.RetrievalAugmentationService;
import com.reactiveminds.genai.core.RetrievalAugmentationSupport;
import com.reactiveminds.genai.core.SemanticSplitter;
import com.reactiveminds.genai.core.vec.Neo4jEmbeddingsOnly;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
@Component
@Order(100)
public class RagPlayground extends AbstractFlow implements ApplicationRunner{
	@Autowired
	SemanticSplitter semanticChunker;
	@Autowired
	Neo4jEmbeddingsOnly embedOnly;
	@Autowired
	RetrievalAugmentationService newsService;
	
	public static final int DEFAULT_MAX_RELEVANT_ITEMS_SEARCH = 3;
	public static final double DEFAULT_MIN_RELEVANCE_SCOR_SEARCH = 0.7;
	
	/**
	 * Prompt and use a neo4j backed rag pipeline, queries knowledge graph and semantic embeddings.
	 * @param question only the query. do not give instructions!
	 * @param maxRelevantItems max relevant embeddings to include
	 * @param minRelevanceScore min relevance score to use
	 * @return
	 */
	private String search(String question, int maxRelevantItems, double minRelevanceScore, RetrievalAugmentationSupport augmentor) {
		
		
        // todo - this api design needs improvement?
        ChatMessage augmented = augmentor.augmentRequest(question, maxRelevantItems, minRelevanceScore);
        
        AiMessage aiMessage = chatModel.getLLM(). generate(augmented).content();

        /*
        log.info("** Only Embedding search **");
        AiMessage toCompare = chatModel.getLLM(). generate(((RetrievalAugmentationSupport)embedOnly).augmentRequest(question, maxRelevantItems, minRelevanceScore) ).content();
        log.info(toCompare.text());
        log.info("****************************");
        */
        
        // See an answer from the model
        return aiMessage.text();
	}
	public String search(String question) {
		Assert.isTrue(vectordb instanceof RetrievalAugmentationSupport, "only neo4j backend supported!");
		return search(question, DEFAULT_MAX_RELEVANT_ITEMS_SEARCH, DEFAULT_MIN_RELEVANCE_SCOR_SEARCH, (RetrievalAugmentationSupport) vectordb);
	}
	public String searchNews(String question) {
		return search(question, 1, DEFAULT_MIN_RELEVANCE_SCOR_SEARCH, newsService);
	}
	
	@Override
	public void run(ApplicationArguments args) throws Exception {		
		//embedSegments("classpath:doc/SutanuDalui-Resume-0924.pdf");
		//embedSegments("classpath:doc/Shobit_Shrivastav_Resume.pdf");
		//withRag();
		//withoutRag();
		
		
		/*
		String chunks = semanticChunker.loadChunks("classpath:doc/kown-err1.txt", 50, 10, 1).stream()
		.map(TextSegment::text).collect(Collectors.joining());
		System.out.println(chunks);
		*/
	}
	private void withRag() {
		log.info("=== Testing PROMPT_WITH_RAG ===");
		//String question = "When did Sutanu work in Ericsson?";
		//String question = "Does Sutanu has experience in Java based embedded systems?";
		//String question = "Does Sutanu has experience in Java and microservices ?";
		//String question = "Does Sutanu has experience in Java?";
		
		String question = "When did Sutanu work in Ericsson?";
	
		log.info("Question :: ".concat(question));
		String answer = ask(RetrievalAugmentationSupport.PROMPT_WITH_RAG, question);
		log.info("Response :: ".concat(answer));
	}
	private void withoutRag() {
		log.info("=== Testing PROMPT_WITHOUT_RAG ===");
		String question = "When did Sutanu work in Ericsson?";
		
		log.info("Question :: ".concat(question));
		String answer = ask(PROMPT_WITHOUT_RAG, question);
		log.info("Response :: ".concat(answer));
	}

}
