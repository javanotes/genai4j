package com.reactiveminds.genai.core;

import java.util.List;

import com.reactiveminds.genai.utils.RelevanceMap;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;

public interface LanguageModel {
	
	ChatLanguageModel getLLM();
	// The choice of tokenizer is only to get an estimate of token counts. Since from Java we are always using the inference apis, 
	// which use complete string content
	default Tokenizer getTokenizer() {
		return new OpenAiTokenizer();
	}
	default EmbeddingModel getEmbeddingModel() {
		return new BgeSmallEnV15QuantizedEmbeddingModel();
	}
	default List<String> getRelevantKeywords(String document, List<String> keywords) {
		return RelevanceMap.orderByRelevance(getEmbeddingModel(), 
				DocumentSplitters.recursive(256, 20, getTokenizer()).split(Document.document(document)), keywords);
	}

}
