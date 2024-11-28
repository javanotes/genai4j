package com.reactiveminds.genai.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.EditDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequence;
import org.springframework.util.ResourceUtils;

import com.google.common.collect.Sets;
import com.reactiveminds.genai.play.KnowledgeGraphModel;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

public class RelevanceMap{
	public int clusterSize() {
		return cluster.size();
	}
	
	@Override
	public String toString() {
		return KnowledgeGraphModel.toPrettyJson(cluster);
	}

	private final Map<String, Set<String>> cluster = new ConcurrentHashMap<>();
	
	private TextSimilarityFunction similarityFunction = TextSimilarityFunction.COSINE;
	public TextSimilarityFunction getSimilarityFunction() {
		return similarityFunction;
	}

	public void setSimilarityFunction(TextSimilarityFunction similarityFunction) {
		this.similarityFunction = similarityFunction;
	}

	private double relevanceScore = 0.51;
	public double getRelevanceScore() {
		return relevanceScore;
	}

	public void setRelevanceScore(double relevanceScore) {
		this.relevanceScore = relevanceScore;
	}

	protected static EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
	//assumes that the two vectors have the same length
	public static double cosineSimilarity(Float[] vectorA, Float[] vectorB) {  
	    return CosineSimilarity.between(Embedding.from(Arrays.asList(vectorA)), Embedding.from(Arrays.asList(vectorB)));
	}
	public RelevanceMap addSentence(String text) {
		if(cluster.isEmpty()) {
			cluster.computeIfAbsent(text, k -> new HashSet<>()).add(text);
		}
		HashSet<String> keys = Sets.newHashSet(cluster.keySet());
		
		Pair<String, Double> similar = keys.parallelStream()
		.map(key -> Pair.of(key, similarityFunction.apply(key, text)))
		.sorted(Collections.reverseOrder(new Comparator<Pair<String, Double>>() {

			@Override
			public int compare(Pair<String, Double> o1, Pair<String, Double> o2) {
				return Double.compare(o1.getRight(), o2.getRight());
			}
		})).findFirst().get();
		if(similar.getRight() >= relevanceScore) {
			cluster.get(similar.getLeft()).add(text);
		}
		else {
			cluster.computeIfAbsent(text, k -> new HashSet<>()).add(text);
		}
		
		return this;
	}
	public static List<String> orderByRelevance(EmbeddingModel embeddingModel, List<TextSegment> document, List<String> keywords) {
		SemanticCluster cluster = new SemanticCluster(document, embeddingModel);
		cluster.deriveCentroid();
		List<SentenceEmbedding> embeddings = 
				keywords.stream()
		.map(s -> new SentenceEmbedding(s, embeddingModel.embed(s).content()))
		.collect(Collectors.toList());
		
		embeddings.stream()
		.map(em -> Pair.of( CosineSimilarity.between(cluster.getCentroid(), em.embedding), em))
		.sorted(Collections.reverseOrder( new Comparator<Pair<Double,SentenceEmbedding>>() {

			@Override
			public int compare(Pair<Double, SentenceEmbedding> o1, Pair<Double, SentenceEmbedding> o2) {
				return Double.compare(o1.getLeft(), o2.getLeft());
			}
		}))
		.map(pair -> new AbstractMap.SimpleEntry<SentenceEmbedding, Double>(pair.getRight(), pair.getLeft()))
		.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x,y)->y, LinkedHashMap::new));
		
		return embeddings.stream().map(SentenceEmbedding::getSentence).collect(Collectors.toList());
	}
	public List<List<String>> getSentenceCluster() {
		return cluster.values()
		.parallelStream()
		.map(texts -> new SemanticCluster(texts.stream().map(s -> TextSegment.from(s)).collect(Collectors.toList()), embeddingModel))
		.map(semantic -> semantic.getRelevantChunks())
		.collect(Collectors.toList());
	}
	
	/**
	 * 
	 * @param minCharsName
	 * @param distanceFunction
	 * @return
	 */
	public List<Pair<String, Set<String>>> namedEntityLinking(int minCharsName, Supplier<EditDistance<? extends Number>> distanceFunction) {		
		List<List<String>> clusters = getSentenceCluster();
		return clusters.stream()
		.map(list -> {
			if(list.size() == 1) {
				return Pair.of(list.get(0), Set.copyOf(list));
			}
			LongestCommonSubsequence lcs = new LongestCommonSubsequence();
			CharSequence key = list.get(0);
			boolean found = true;
			// first check longest subsequence
			for (int i = 1; i < list.size(); i++) {
				key = lcs.longestCommonSubsequence(list.get(i), key);
				if(key.length() < minCharsName) {
					// require at least minCharsName character sequence
					// we short circuit here, if min chars not found
					found = false;
					break;
				}
			}
			if(found) {
				// check if the subsequence is meaningful - i.e. a substring
				//IE8, Internet Explorer 8
				String seq = key.toString();
				found = list.stream().anyMatch(s -> s.contains(seq));
			}
			
			if(!found) {
				//try edit distance - whichever has the highest common part, by edit distance
				
				EditDistance<? extends Number> editDist = distanceFunction.get();
				for (int i = 0; i < list.size()-1; i++) {
					Double i_to_i1 = editDist.apply(list.get(i), list.get(i+1)).doubleValue();
					Double i1_to_i = editDist.apply(list.get(i+1), list.get(i)).doubleValue();
					
					key = i_to_i1 < i1_to_i ? list.get(i) : list.get(i+1);
				}
			}
			return Pair.of(key.toString(), Set.copyOf(list));
			
		}).collect(Collectors.toList());
	}
	
	public static void main(String[] args) throws IOException {
		System.out.println(TextSimilarityFunction.JAROWINKLER.apply("sutanu", "sutanu dalui"));
		System.out.println(TextSimilarityFunction.LEVENSHTEIN.apply("sutanu", "sutanu dalui"));
		System.out.println(TextSimilarityFunction.JAROWINKLER.apply("24112017", "24/11/2017"));
		System.out.println(TextSimilarityFunction.LEVENSHTEIN.apply("24112017", "24/11/2017"));
		System.out.println(TextSimilarityFunction.JAROWINKLER.apply("I like football", "What is my favourite sport"));
		
		File f = ResourceUtils.getFile("classpath:cluster.txt");
		List<String> lines = Files.readAllLines(f.toPath());
		System.out.println(String.format("read %d lines from file", lines.size()));
		RelevanceMap relevanceMap = new RelevanceMap();
		relevanceMap.setRelevanceScore(0.6);
		lines.forEach(line -> relevanceMap.addSentence(line));
		System.out.println(String.format("loaded relevance map. cluster size=%d. now running relevance ..", relevanceMap.clusterSize()));
		
		List<List<String>> relevance = relevanceMap.getSentenceCluster();
		System.out.println(relevance);
	}

}
