package com.reactiveminds.genai.utils;

import java.util.function.BiFunction;

import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;

import dev.langchain4j.store.embedding.CosineSimilarity;

public interface TextSimilarityFunction extends BiFunction<String, String, Double>{
	public static final TextSimilarityFunction COSINE = (left, right) -> CosineSimilarity.between(RelevanceMap.embeddingModel.embed(left).content(), RelevanceMap.embeddingModel.embed(right).content());
	public static final TextSimilarityFunction JAROWINKLER = (left, right) -> new JaroWinklerSimilarity().apply(left, right);
	public static final TextSimilarityFunction JACCARD = (left, right) -> new JaccardSimilarity().apply(left, right);
	public static final TextSimilarityFunction SOUNDEX = new TextSimilarityFunction() {
		
		@Override
		public Double apply(String left, String right) {
			Soundex soundex = new Soundex();
			return LEVENSHTEIN.apply(soundex.encode(left), soundex.encode(right));
		}
	};
	public static final TextSimilarityFunction LEVENSHTEIN = new TextSimilarityFunction() {
		
		@Override
		public Double apply(String left, String right) {
			LevenshteinDistance ld = new LevenshteinDistance();
			double d = ld.apply(left, right) / (double)left.length();
			return 1.0-d;
		}
		
	};
	
	
}