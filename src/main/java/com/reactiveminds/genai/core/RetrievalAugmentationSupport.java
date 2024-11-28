package com.reactiveminds.genai.core;

import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
/**
 * a template mixin for {@link AbstractEmbeddings} which can support augmenting retrieval
 */
public interface RetrievalAugmentationSupport {
	static PromptTemplate PROMPT_WITH_RAG = PromptTemplate.from(
            "Answer the following question to the best of your ability. If you are unsure, say don't know:\n"
                    + "\n"
                    + "Question:\n"
                    + "{{question}}\n"
                    + "\n"
                    + "Base your answer on the following information:\n"
                    + "{{information}}");
	/**
	 * get a new RAG pipeline for non-conversational query inferencing
	 * @param promptGenerator prompt generation function with augmented context
	 * @param maxResults max results to consider from embedding search result
	 * @param minScore results to consider for embedding search
	 * @return
	 */
	RetrievalAugmentor createRetrievalAugmentor(Function<String, Prompt> promptGenerator, int maxResults, double minScore);
	/**
	 * Create an augmented user query with RAG
	 * @param userQuery input query
	 * @param maxRelevantItems max relevant embeddings
	 * @param minRelevanceScore min relevance score
	 * @return augmented prompt
	 */
	default ChatMessage augmentRequest(String userQuery, int maxRelevantItems, double minRelevanceScore) {
		return createRetrievalAugmentor(context -> {
        	
    		return PROMPT_WITH_RAG.apply(Map.of("question", userQuery,
    											"information", context
    									));
        }, maxRelevantItems, minRelevanceScore)
		.augment(new AugmentationRequest(UserMessage.from(userQuery), new Metadata(UserMessage.from(userQuery), null, null)))
		.chatMessage()
		;
	}
}
