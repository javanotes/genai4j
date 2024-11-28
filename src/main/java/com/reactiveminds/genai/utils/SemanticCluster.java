package com.reactiveminds.genai.utils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

class SemanticCluster{
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(SemanticCluster.class);
	private double similarityIndex = 0.95;
	SemanticCluster(List<TextSegment> chunks, EmbeddingModel embeddingModel) {
		this.chunks = chunks;
		this.embeddingModel = embeddingModel;
	}
	private final EmbeddingModel embeddingModel;
	private Embedding centroid;
	private List<SentenceEmbedding> embeddings;
	private LinkedHashMap<SentenceEmbedding, Double> relevanceScore;
	private Map<SentenceEmbedding, Double> noveltyScore = new HashMap<SentenceEmbedding, Double>();
	private List<TextSegment> chunks;
	
	//order similar texts by relevance
	public List<String> getRelevantChunks() {
		deriveCentroid();
		sentenceRelevance();
		log.debug("relevanceScore map size={}; emebddings sze={}",relevanceScore.size(), embeddings.size());
		log.trace(printRelevanceMap());
		return relevanceScore.entrySet().stream().map(Entry::getKey).map(SentenceEmbedding::toString).collect(Collectors.toList());
	}
	public String printRelevanceMap() {
		StringBuilder str = new StringBuilder();
		if(relevanceScore != null) {
			relevanceScore.forEach((e,s) -> {
				str.append("\n```").append(e.sentence)
				.append("``` -> ").append(s);
			});
		}
		return str.toString();
	}
	
	void deriveCentroid() {
		List<Embedding> vectors = embeddingModel.embedAll(chunks).content();
		embeddings = new ArrayList<>(vectors.size());
		
		float[] centroidVec = new float[embeddingModel.dimension()];
		for (int i = 0; i < centroidVec.length; i++) {
			centroidVec[i] = 0.0f;
		}
		for (int i = 0; i < vectors.size(); i++) {
			Embedding em = vectors.get(i);
			float[] v = em.vector();
			for (int j = 0; j < centroidVec.length; j++) {
				centroidVec[j] += v[j];
			}
			embeddings.add(new SentenceEmbedding(chunks.get(i).text(), vectors.get(i)));
		}
		for (int i = 0; i < centroidVec.length; i++) {
			centroidVec[i] /= chunks.size();
		}
		centroid = Embedding.from(centroidVec);
		log.trace("centroid: {}", centroidVec);
	}
	
	private void sentenceRelevance() {
		relevanceScore = embeddings.stream()
		.map(em -> Pair.of( CosineSimilarity.between(getCentroid(), em.embedding), em))
		.sorted(Collections.reverseOrder( new Comparator<Pair<Double,SentenceEmbedding>>() {

			@Override
			public int compare(Pair<Double, SentenceEmbedding> o1, Pair<Double, SentenceEmbedding> o2) {
				return Double.compare(o1.getLeft(), o2.getLeft());
			}
		}))
		.map(pair -> new AbstractMap.SimpleEntry<SentenceEmbedding, Double>(pair.getRight(), pair.getLeft()))
		.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x,y)->y, LinkedHashMap::new));
	}
	
	
	private void sentenceNovelty() {
		noveltyScore.clear();
		Sets.newHashSet();
		for (int i = 0; i < embeddings.size(); i++) {
			double maxSimilar = 0.0;
			for (int j = i+1; j < embeddings.size(); j++) {
				double theta = CosineSimilarity.between(embeddings.get(i).embedding, embeddings.get(j).embedding);
				if(theta > maxSimilar) {
					maxSimilar = theta;
					Double iThScore = relevanceScore.get(embeddings.get(i));
					Double jThScore = relevanceScore.get(embeddings.get(j));
					if(maxSimilar > similarityIndex) {
						//these 2 sentences are very similar - consider the one with higher relevance
						if(iThScore > jThScore) {
							noveltyScore.put(embeddings.get(i), 1.0);
						}
						else {
							noveltyScore.put(embeddings.get(j), 1.0);
						}
					}
					else {
						//probably a repetition
						noveltyScore.put(embeddings.get(i), 1-iThScore);
					}
				}
			}
		}
	}
	public double getSimilarityIndex() {
		return similarityIndex;
	}
	public void setSimilarityIndex(double similarityIndex) {
		this.similarityIndex = similarityIndex;
	}
	public Embedding getCentroid() {
		return centroid;
	}
}