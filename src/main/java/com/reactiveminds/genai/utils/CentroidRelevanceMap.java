package com.reactiveminds.genai.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.ResourceUtils;

import com.google.common.collect.Sets;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;

//@WIP concept not proved yet!
class CentroidRelevanceMap extends RelevanceMap{
		private final Map<SentenceCentroid, Set<String>> centroidCluster = new ConcurrentHashMap<>();
	
		
		private static class SentenceCentroid{//object equality considered
			private Embedding centroidVec;
			
			public SentenceCentroid(Embedding seedVec) {
				super();
				this.centroidVec = seedVec;
			}
			double similarity(Embedding other) {
				return CosineSimilarity.between(centroidVec, other);
			}
			public void recenter(Set<String> value) {
				List<Embedding> vectors = embeddingModel.embedAll(value.stream().map(s->TextSegment.from(s)).collect(Collectors.toList())).content();
				
				float[] centroid = new float[RelevanceMap.embeddingModel.dimension()];
				for (int i = 0; i < centroid.length; i++) {
					centroid[i] = 0.0f;
				}
				for (int i = 0; i < vectors.size(); i++) {
					Embedding em = vectors.get(i);
					float[] v = em.vector();
					for (int j = 0; j < centroid.length; j++) {
						centroid[j] += v[j];
					}
				}
				for (int i = 0; i < centroid.length; i++) {
					centroid[i] /= vectors.size();
				}
				centroidVec = Embedding.from(centroid);
			}
		}
	
	
	public CentroidRelevanceMap addSentence(String text) {
		Embedding textVec = embeddingModel.embed(text).content();
		if(centroidCluster.isEmpty()) {
			centroidCluster.computeIfAbsent(new SentenceCentroid(textVec), k -> new HashSet<>()).add(text);
			return this;
		}
		HashSet<Entry<SentenceCentroid,Set<String>>> keys = Sets.newHashSet(centroidCluster.entrySet());
		
		Pair<Entry<SentenceCentroid,Set<String>>, Double> similar = keys.parallelStream()
		.map(key -> Pair.of(key, key.getKey().similarity(textVec)))
		.sorted(Collections.reverseOrder(new Comparator<Pair<Entry<SentenceCentroid,Set<String>>, Double>>() {

			@Override
			public int compare(Pair<Entry<SentenceCentroid,Set<String>>, Double> o1, Pair<Entry<SentenceCentroid,Set<String>>, Double> o2) {
				return Double.compare(o1.getRight(), o2.getRight());
			}
		})).findFirst().get();
		
		if(similar.getRight() >= getRelevanceScore()) {
			centroidCluster.get(similar.getLeft().getKey()).add(text);
			similar.getKey().getKey().recenter(similar.getKey().getValue());
		}
		else {
			centroidCluster.computeIfAbsent(new SentenceCentroid(textVec), k -> new HashSet<>()).add(text);
		}
		
		return this;
	}
	@Override
	public int clusterSize() {
		return centroidCluster.size();
	}
	
	public static void main(String[] args) throws IOException {
		File f = ResourceUtils.getFile("classpath:cluster.txt");
		List<String> lines = Files.readAllLines(f.toPath());
		System.out.println(String.format("read %d lines from file", lines.size()));
		
		RelevanceMap relevanceMap = new CentroidRelevanceMap();
		relevanceMap.setRelevanceScore(0.6);
		lines.forEach(line -> relevanceMap.addSentence(line));
		System.out.println(String.format("loaded relevance map. cluster size=%d. now running relevance ..", relevanceMap.clusterSize()));
	}
	
}
