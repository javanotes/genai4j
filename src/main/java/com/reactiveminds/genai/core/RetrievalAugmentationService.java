package com.reactiveminds.genai.core;

import java.time.Duration;

import com.reactiveminds.genai.core.vec.DocumentVector;
import com.reactiveminds.genai.graphrag.ADocument;
/**
 * service for providing capabilities to a RAG based inferencing
 */
public interface RetrievalAugmentationService extends RetrievalAugmentationSupport{
	
	/**
	 * The time period for which historical matched documents may be retrieved. This is provided as a hint to the RetrievalAugmentationService
	 * to provide a purging policy if applicable.
	 * @param relevantHistoryDuration
	 * @see DocumentVector#getDate()
	 */
	void setRelevantHistoryDuration(Duration relevantHistoryDuration);
	/**
	 * index document and vector, as applicable to the underlying embedding store provider
	 * @param document
	 */
	void ingestDocument(ADocument document);
}
