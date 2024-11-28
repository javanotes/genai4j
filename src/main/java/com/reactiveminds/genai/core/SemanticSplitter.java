package com.reactiveminds.genai.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.random.EmpiricalDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ResourceUtils;

import com.reactiveminds.genai.play.KnowledgeGraphModel;
import com.reactiveminds.genai.utils.RelevanceMap;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

/**
 * credits:
 * @see https://github.com/FullStackRetrieval-com/RetrievalTutorials/blob/main/tutorials/LevelsOfTextSplitting/5_Levels_Of_Text_Splitting.ipynb
 */
public class SemanticSplitter extends DocumentBySentenceSplitter{
	private final double breakpointPercentile;
	/**
	 * 
	 * @param maxTokens
	 * @param embeddings
	 * @param languageModel
	 * @param breakpointPercentile
	 */
	public SemanticSplitter(int maxTokens, AbstractEmbeddings embeddings,
			LanguageModel languageModel, double breakpointPercentile) {
		super(maxTokens, 0, languageModel.getTokenizer());
		this.breakpointPercentile = breakpointPercentile;
		this.embeddings = embeddings;
	}
	/**
	 * 
	 * @param maxSegmentSizeInChars
	 * @param embeddings
	 * @param languageModel
	 */
	public SemanticSplitter(int maxSegmentSizeInChars, AbstractEmbeddings embeddings,
			LanguageModel languageModel) {
		this(maxSegmentSizeInChars, embeddings, languageModel, 0.95);
	}
	private static final Logger log = LoggerFactory.getLogger(SemanticSplitter.class);
	
	final AbstractEmbeddings embeddings;
	
	static class SemanticEmbedding{
		int index;
		String sentence;
		String combined;
		Embedding sentenceVector;
		Embedding combinedVector;
	}
	private List<SemanticEmbedding> createEmbeddings(CombinedMap combinedMap) {
		return combinedMap._map.entrySet()
		.parallelStream()
		.map(e -> {
			SemanticEmbedding embedding = new SemanticEmbedding();
			embedding.index = e.getKey();
			embedding.sentence = e.getValue().getLeft();
			embedding.combined = e.getValue().getRight();
			//embedding.sentenceVector = embeddings.createEmbedding(TextSegment.from(embedding.sentence));
			embedding.combinedVector = embeddings.createEmbedding(TextSegment.from(embedding.combined));
			return embedding;
		}).collect(Collectors.toList());
	}
	
	
	private static List<Pair<Double,SemanticEmbedding>> calculatePercentile(List<SemanticEmbedding> embeddings) {
		final double[] distances = new double[embeddings.size()];
		for (int i = 0; i < embeddings.size()-1; i++) {
			Float[] nthVec = embeddings.get(i).combinedVector.vectorAsList().toArray(new Float[] {});
			Float[] nPlusOneVec = embeddings.get(i+1).combinedVector.vectorAsList().toArray(new Float[] {});
			double theta = RelevanceMap.cosineSimilarity(nthVec, nPlusOneVec);
			distances[i] = (1.0-theta);
		}
		
		EmpiricalDistribution distribution = new EmpiricalDistribution(distances.length);
	    distribution.load(distances);
	    
		AtomicInteger i = new AtomicInteger();
		return embeddings.stream()
		.map(em -> Pair.of(distribution.cumulativeProbability(distances[i.getAndIncrement()]), em))
		.collect(Collectors.toList());
	}
	@Deprecated
	public List<TextSegment> loadChunks(String resourcePath, int maxSegments, int maxOverlap, int topK) throws IOException {
		// Load the document that includes the information you'd like to "chat" about with the model.
		
        DocumentParser documentParser = new ApacheTikaDocumentParser();
        Document document;
        try(InputStream in = ResourceUtils.getURL(resourcePath).openStream()){
        	document = documentParser.parse(in);
        }       

        // Split document into segments 100 tokens each
        DocumentSplitter splitter = new DocumentBySentenceSplitter(256, 0);
        List<TextSegment> splits = splitter.split(document);
        List<Vec> vectors = IntStream.range(0, splits.size()).mapToObj(i -> new Vec(i, splits.get(i))).collect(Collectors.toList());
        
        // convert text to vector 
        // todo - concurrency ?
        vectors.parallelStream()
        .forEach(vector -> {
        	List<Float> v = embeddings.createEmbedding(vector.text).vectorAsList();
        	synchronized(Float.class) {
        		vector.embeddings = v;
        	}
        } )
        ;
        
        List<TextSegment> segments = new LinkedList<>();
        // cosine similarity - all to all?
        for (int i = 0; i < vectors.size(); i++) {
        	List<Vec> embeddings = new ArrayList<>(vectors.size()-1);
        	Vec embedding = vectors.get(i);
			for (int j = 0; j < vectors.size(); j++) {
				if(j == i)
					continue;
				embeddings.add(vectors.get(j));
			}
			ArrayList<Vec> annList = compareTheta(embedding, embeddings);
			List<Vec> annListTopK = annList.subList(0, Math.min(topK, annList.size()));
			annListTopK.add(embedding);
			StringBuilder text = new StringBuilder();
			annListTopK.stream().sorted(Comparator.comparingInt(Vec::getPosition)).distinct().forEach(vec -> text.append(vec.text.text()));
			
			segments.add( TextSegment.from(text.toString()));
			
			log.debug("Root chunk : {} ", embedding);
			log.debug("Semantic chunks : {} ", annList);
		}
        
        return segments;
	}
	public static void main(String[] args) {
		CombinedMap map = new CombinedMap();
		map.apply(List.of(
				"this is sentence 0.",
				"this is sentence 1.",
				"this is sentence 2."
				), 1);
		log.info(map.toString());
	}
	/**
	 * Creates and hold a sliding window map for a collection of sentences, in order.
	 */
	private static class CombinedMap{
		@Override
		public String toString() {
			return KnowledgeGraphModel.toPrettyJson(_map);
		}
		private final Map<Integer, Pair<String, String>> _map = new LinkedHashMap<>();
		void apply(List<String> sentences, int window) {
			_map.clear();
			int len = sentences.size();
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < sentences.size(); i++) {
				String left = sentences.get(i);
				for (int j = Math.max(0, i-window); j < i; j++) {
					str.append(sentences.get(j));
				}
				str.append(left);
				for (int j = i+1; j < Math.min(len, i+1+window); j++) {
					str.append(sentences.get(j));
				}
				_map.put(i, Pair.of(left, str.toString()));
				str.setLength(0);
			}
		}
	}
	private static class Vec{
		private final TextSegment text;
		private List<Float> embeddings;
		@Override
		public int hashCode() {
			return Objects.hash(text.text());
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Vec other = (Vec) obj;
			return Objects.equals(text.text(), other.text.text());
		}
		private final int position;
		public int getPosition() {
			return position;
		}
		public Vec(Integer position, TextSegment text) {
			super();
			this.position = position;
			this.text = text;
		}
		@Override
		public String toString() {
			return "Vec [position=" + position + ", text=" + text + "]";
		}
	}
	static ArrayList<Vec> compareTheta(Vec embedding, List<Vec> embeddings) {
		
		return embeddings.stream()
		.map(compareTo -> {
			Double theta = RelevanceMap.cosineSimilarity(embedding.embeddings.toArray(new Float[0]), compareTo.embeddings.toArray(new Float[0]));
			return Pair.of(compareTo, theta);
		})
		.sorted(Collections.reverseOrder( new Comparator<>() {

			@Override
			public int compare(Pair<Vec, Double> o1, Pair<Vec, Double> o2) {
				return Double.compare(o1.getRight(), o2.getRight());
			}
		}) )
		.map(Pair::getLeft)
		.collect(Collectors.toCollection(ArrayList::new))
		;
	}
	
	@Override
	public List<TextSegment> split(Document document) {
		return split0(document, d -> super.split(d));
	}
	private List<TextSegment> split0(Document document, Function<Document, List<TextSegment>> baseSplitter) {
		List<TextSegment> lines = baseSplitter.apply(document);
		log.info("found {} sentences in doc", lines.size());
		//Arrays.asList(lines).forEach(line -> log.info(line))		
		List<TextSegment> chunks = toChunks(lines.stream().map(TextSegment::text).toList());
		
		log.info("breakpoint percentile={}; chunks={}", breakpointPercentile, chunks.size());
		return chunks;
	}
	
	private List<TextSegment> toChunks(List<String> lines) {
		CombinedMap combinedMap = new CombinedMap();
		combinedMap.apply(lines, 1);
		List<SemanticEmbedding> combinedEmbeddings = createEmbeddings(combinedMap);
		List<Pair<Double, SemanticEmbedding>> distances = calculatePercentile(combinedEmbeddings);
		
		StringBuilder str = new StringBuilder();
		str.setLength(0);
		
		if (log.isDebugEnabled()) {
			for (int i = 0; i < 3; i++) {
				Pair<Double, SemanticEmbedding> d = distances.get(i);
				str.append(d.getLeft()).append(" : ").append(d.getRight().sentence).append("\n");
			}
			str.append(". . . .").append("\n");
			for (int i = lines.size() - 3; i < lines.size(); i++) {
				Pair<Double, SemanticEmbedding> d = distances.get(i);
				str.append(d.getLeft()).append(" : ").append(d.getRight().sentence).append("\n");
			}
			log.debug(str.toString());
		}
		List<TextSegment> chunks = new LinkedList<>();
		
		str.setLength(0);
		distances.forEach(pair -> {
			str.append(pair.getRight().sentence);
			if(pair.getLeft() >= breakpointPercentile) {
				String nextChunk = str.toString();
				int tokens = super.tokenizer.estimateTokenCountInText(nextChunk);
				if(tokens > super.maxSegmentSize) {
					log.warn("tokens={}, maxSegmentSize={} more splits required. Please check context window of the model", tokens, super.maxSegmentSize);
				}
				chunks.add(TextSegment.from(nextChunk));
				/*
				if(tokens > super.maxSegmentSize) {
					log.info("tokens={}, maxSegmentSize={} more splits required", tokens, super.maxSegmentSize);
					//this implies a sentence is longer than the context window. not playing nice with sentence-splitter :(
					split0(Document.document(nextChunk), d -> DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize, tokenizer).split(d));
					List<TextSegment> moreChunks = split(Document.document(nextChunk));
					chunks.addAll(moreChunks);
				}
				else {
					chunks.add(TextSegment.from(nextChunk));
				}*/				
				str.setLength(0);
			}
		});
		
		return chunks;
	}

}
