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

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;

public class ZeroShotClassifier implements BiFunction<String, List<String>, String>{
	static File model,tokenizer;
	static {
		try {
			model = ResourceUtils.getFile("classpath:distilbert-base-uncased-mnli/model_quantized.onnx");
			tokenizer = ResourceUtils.getFile("classpath:distilbert-base-uncased-mnli/tokenizer.json");
		} 
		catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	private final OnnxScoringModel scoringModel;
	public ZeroShotClassifier() {
		scoringModel = new OnnxScoringModel(model.getPath(), tokenizer.getPath());
	}
	public static void main(String[] args) {
		ZeroShotClassifier classif = new ZeroShotClassifier();
		String label = classif.apply("I really don't know what you want, or maybe I don't want to know", List.of("confused", "confident", "indifferent", "happy"));
		System.out.println(label);
		label = classif.apply("I am sure we will win", List.of("confused", "confident", "indifferent", "happy"));
		System.out.println(label);
		label = classif.apply("Its so good to see you afetr so many days", List.of("confused", "confident", "indifferent", "happy"));
		System.out.println(label);
	}
	@Override
	public String apply(String query, List<String> labels) {
		Response<List<Double>> result = scoringModel.scoreAll(labels.stream().map(s -> TextSegment.from(s)).toList(), query);
		final List<Double> scores = result.content();
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
