package com.reactiveminds.genai.core.llm;

import java.io.File;
import java.io.FileNotFoundException;

import org.springframework.util.ResourceUtils;

import ai.onnxruntime.OrtSession.SessionOptions;

class CausalTextGenerator extends HFTransformer{
	static File model,tokenizer;
	static {
		try {
			model = ResourceUtils.getFile("classpath:distilgpt2_onnx-quantized/decoder_model.onnx");
			tokenizer = ResourceUtils.getFile("classpath:distilgpt2_onnx-quantized/tokenizer.json");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	public CausalTextGenerator() {
		super(model.getPath(), tokenizer.getPath(), new SessionOptions());
	}
	
	public static void main(String[] args) {
		CausalTextGenerator textgen = new CausalTextGenerator();
		textgen.inferLine("Hey there, how are you?", new GeneratedTextResult());
		textgen.close();
	}

}
