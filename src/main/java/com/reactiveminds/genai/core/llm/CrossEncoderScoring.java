package com.reactiveminds.genai.core.llm;

import java.io.File;
import java.io.FileNotFoundException;

import org.springframework.util.ResourceUtils;

import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;

public class CrossEncoderScoring extends OnnxScoringModel{
	static File model,tokenizer;
	static {
		try {
			model = ResourceUtils.getFile("classpath:ms-marco-MiniLM-L-6-v2/model_quantized.onnx");
			tokenizer = ResourceUtils.getFile("classpath:ms-marco-MiniLM-L-6-v2/tokenizer.json");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	public CrossEncoderScoring() {
		super(model.getPath(), tokenizer.getPath());
	}

}
