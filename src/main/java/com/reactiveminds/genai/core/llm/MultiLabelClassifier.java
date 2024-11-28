package com.reactiveminds.genai.core.llm;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.ResourceUtils;

import ai.djl.util.PairList;
import ai.onnxruntime.OrtSession.SessionOptions;

class MultiLabelClassifier extends HFTransformer implements BiFunction<String, List<String>, String>{
	static File model,tokenizer;
	static {
		/**
		 * "id2label": {
			    "0": "ENTAILMENT",
			    "1": "NEUTRAL",
			    "2": "CONTRADICTION"
			},
		 */
		try {
			model = ResourceUtils.getFile("classpath:distilbert-base-uncased-mnli/model_quantized.onnx");
			tokenizer = ResourceUtils.getFile("classpath:distilbert-base-uncased-mnli/tokenizer.json");
		} 
		catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	public MultiLabelClassifier() {
		super(model.getPath(), tokenizer.getPath(), new SessionOptions());
	}
	
	public static void main(String[] args) {
		MultiLabelClassifier classif = new MultiLabelClassifier();
		String clas = classif.apply("I really don't know what you want, or maybe I don't want to know", List.of("confused", "confident", "indifferent", "happy"));
		System.out.println(clas);
		clas = classif.apply("Its so good to see you after so many days", List.of("confused", "confident", "indifferent", "happy"));
		System.out.println(clas);
		classif.close();
	}

	@Override
	public String apply(String query, List<String> labels) {
		PairList<String, String> pairs = new PairList<>(labels.size());
		labels.forEach(label -> pairs.add(query, label));
		List<Double> scores = inferPairs(pairs, new ProbabilityDistributionResult());
		
		Pair<Integer, Double> classified = IntStream.range(0, scores.size())
				.mapToObj(i -> Pair.of(i, scores.get(i)))
				.sorted(Collections.reverseOrder(new Comparator<Pair<Integer,Double>>() {

					@Override
					public int compare(Pair<Integer,Double> o1, Pair<Integer,Double> o2) {
						return Double.compare(o1.getRight(), o2.getRight());
					}
				}))
				.findFirst().get()
				;
				return labels.get(classified.getKey());
	}

}
