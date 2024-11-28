package com.reactiveminds.genai.core;

import java.util.List;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

public interface EmbeddingsRunner{

	void embedSegments(List<TextSegment> segments);
	EmbeddingSearchResult<TextSegment> findRelevant(String question, int maxResults, double minScore);

}