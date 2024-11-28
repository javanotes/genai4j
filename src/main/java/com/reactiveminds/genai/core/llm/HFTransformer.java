package com.reactiveminds.genai.core.llm;

import java.io.Closeable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.PairList;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import ai.onnxruntime.OrtSession.SessionOptions;

/**
 * base class for hugging face transformers
 * 
 * @param <T>
 */
public abstract class HFTransformer implements Closeable {
	protected Logger log = LoggerFactory.getLogger(getClass());
	private final OrtEnvironment environment;
	private final OrtSession session;
	private final HuggingFaceTokenizer tokenizer;

	/**
	 * Handling of the output tensors can be different
	 * 
	 * @param <T>
	 */
	public static interface ResultHandler<T> extends BiFunction<OrtSession.Result, HuggingFaceTokenizer, T> {
		static int sizeofTensor(Object resultTensor) {
			Class<?> maybeArray = resultTensor.getClass();
			if (maybeArray.isArray()) {
				// will return something like float[][]
				String type = maybeArray.getTypeName();
				return StringUtils.substringsBetween(type, "[", "]").length;
			}
			return -1;
		}

		@Override
		default T apply(Result t, HuggingFaceTokenizer tokenizer) {
			System.out.println("=== Output ===");
			try (OrtSession.Result results = t) {

				for (Map.Entry<String, OnnxValue> r : results) {
					OnnxValue resultValue = r.getValue();
					OnnxTensor resultTensor = (OnnxTensor) resultValue;
					Object result = resultTensor.getValue();
					System.out.println(String.format("[%s] => shape: %s, type: %s, instanceof: %s, dimension: %d",
							r.getKey(), Arrays.toString(resultTensor.getInfo().getShape()),
							resultTensor.getType().name(), result.getClass().getTypeName(), sizeofTensor(result)));
					;

				}
				
			} catch (OrtException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	public static class ProbabilityDistributionResult implements ResultHandler<List<Double>> {
		@Override
		public List<Double> apply(Result t, HuggingFaceTokenizer tokenizer) {
			//ResultHandler.super.apply(t);
			try (OrtSession.Result results = t) {

				for (Map.Entry<String, OnnxValue> r : results) {
					
					OnnxTensor resultTensor = (OnnxTensor) r.getValue();
					Object result = resultTensor.getValue();
					int sizeof = ResultHandler.sizeofTensor(result);
					
					if(sizeof == 2) {
						System.out.println(String.format("[%s] => shape: %s, type: %s, instanceof: %s, dimension: %d",
								r.getKey(), Arrays.toString(resultTensor.getInfo().getShape()),
								resultTensor.getType().name(), result.getClass().getTypeName(), sizeof));
						
						float[][] outputData = (float[][]) result;
						List<Double> maxProbabilities = new ArrayList<>(outputData.length);
			            for (float[] row : outputData) {
			            	/*
			            	 * "id2label": {
								    "0": "ENTAILMENT",
								    "1": "NEUTRAL",
								    "2": "CONTRADICTION"
								}
			            	 */
			                double[] probabilities = softmax(row);			                
			                double max = Double.MIN_VALUE;
			                int index = -1;
			                for (int i = 0; i < probabilities.length; i++) {
								double value = probabilities[i];
								if(value > max) {
			                    	max = value;
			                    	index = i;
								}
							}
			                if(index == 0) {
			                	maxProbabilities.add(max);
			                }
			                else {
			                	maxProbabilities.add(0.0); //not entailed
			                }
			            }
			            return maxProbabilities;
					}
					
				}
				
			} catch (OrtException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
	
	public static class GeneratedTextResult implements ResultHandler<String> {
		@Override
		public String apply(Result t, HuggingFaceTokenizer tokenizer) {
			//ResultHandler.super.apply(t);
			try (OrtSession.Result results = t) {

				for (Map.Entry<String, OnnxValue> r : results) {
					
					OnnxTensor resultTensor = (OnnxTensor) r.getValue();
					Object result = resultTensor.getValue();
					int sizeof = ResultHandler.sizeofTensor(result);
										
					if(sizeof == 3) {
						System.out.println(String.format("[%s] => shape: %s, type: %s, instanceof: %s, dimension: %d",
								r.getKey(), Arrays.toString(resultTensor.getInfo().getShape()),
								resultTensor.getType().name(), result.getClass().getTypeName(), sizeof));
						;
						
						float[][][] outputData = (float[][][]) result;
						int order2 = outputData[0].length;
						System.out.println("order2: "+order2);
						order2 = outputData[0][0].length;
						System.out.println("order3: "+order2);
					}
					

				}
				
			} catch (OrtException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	public static void main(String[] args) {
		try (HFTransformer transformer = new HFTransformer(ZeroShotClassifier.model.getPath(),
				ZeroShotClassifier.tokenizer.getPath(), new SessionOptions()) {
		}) {
			List<Double> result = transformer.inferLine("what should I do now?", new ProbabilityDistributionResult());
			System.out.println("result : ".concat( result.toString()));
		}

	}

	/**
	 * 
	 * @param modelPath
	 * @param options
	 * @param pathToTokenizer
	 */
	public HFTransformer(String modelPath, String pathToTokenizer, OrtSession.SessionOptions options) {
		try {
			this.environment = OrtEnvironment.getEnvironment();
			this.session = this.environment.createSession(modelPath, options);
			Map<String, String> tokenizerOptions = new HashMap<>() {
				{
					put("padding", "true");
					put("truncation", "LONGEST_FIRST"); // Default maximum length limit, LONGEST-FIRST prioritizes
														// truncating the longest part
					// put("modelMaxLength", String.valueOf(modelMaxLength - 2));
				}
			};
			this.tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(pathToTokenizer), tokenizerOptions);
			log.info("in tensor: {}, out tensor:{}", session.getInputNames(), session.getOutputNames());
			log.info("out tensor: {}", getOutputInfo());
			session.getMetadata();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected Map<String, NodeInfo> getOutputInfo() throws OrtException {
		return session.getOutputInfo();
	}

	public <T> T inferLine(String sentence, ResultHandler<T> handler) {
		Encoding encoding = tokenizer.encode(sentence);
		return inference(extractTokenIds(Stream.of(encoding)), handler);
	}

	public <T> T inferPair(String sentence, String pair, ResultHandler<T> handler) {
		Encoding encoding = tokenizer.encode(sentence, pair);
		return inference(extractTokenIds(Stream.of(encoding)), handler);
	}

	public <T> T inferLines(List<String> sentences, ResultHandler<T> handler) {
		Encoding[] encoding = tokenizer.batchEncode(sentences);
		return inference(extractTokenIds(Stream.of(encoding)), handler);
	}

	public <T> T inferPairs(PairList<String, String> pairs, ResultHandler<T> handler) {
		Encoding[] encoding = tokenizer.batchEncode(pairs);
		return inference(extractTokenIds(Stream.of(encoding)), handler);
	}

	private Triple<long[][], long[][], long[][]> extractTokenIds(Stream<Encoding> encodingStream) {
		List<Encoding> asList = encodingStream.collect(Collectors.toList());
		long[][] inputIdsData = asList.stream().map(Encoding::getIds).toArray(long[][]::new);
		long[][] attentionMaskData = asList.stream().map(Encoding::getAttentionMask).toArray(long[][]::new);
		long[][] tokenTypeIds = asList.stream().map(Encoding::getTypeIds).toArray(long[][]::new);

		return ImmutableTriple.of(inputIdsData, attentionMaskData, tokenTypeIds);
	}

	private <T> T inference(Triple<long[][], long[][], long[][]> input, ResultHandler<T> callback) {

		// Perform inference
		try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, input.getLeft());
				OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(environment, input.getMiddle());
				OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(environment, input.getRight())) {

			Map<String, OnnxTensor> inputTensors = session.getInputNames().contains("token_type_ids")
					? Map.of("input_ids", inputIdsTensor, "attention_mask", attentionMaskTensor, "token_type_ids",
							tokenTypeIdsTensor)
					: Map.of("input_ids", inputIdsTensor, "attention_mask", attentionMaskTensor);

			try (OrtSession.Result result = session.run(inputTensors, session.getOutputNames())) {

				// Get the predictions for the masked token
				// Optional<OnnxValue> optionalValue = result.get("logits");
				List<String> outputKeys = StreamSupport.stream(result.spliterator(), false).map(Entry::getKey).toList();
				log.info("output keys: {}", outputKeys);
				return callback.apply(result, tokenizer);

			} catch (Exception e) {
				log.error("uncaught exception from handler", e);
			}

			return null;
		} catch (OrtException e) {
			throw new IllegalArgumentException(e);
		}

	}

	protected static double[] softmax(float[] logits) {
		// from Apache openNLP library

		final double[] t = new double[logits.length];
		double sum = 0.0;

		for (int x = 0; x < logits.length; x++) {
			double val = Math.exp(logits[x]);
			sum += val;
			t[x] = val;
		}

		final double[] output = new double[logits.length];

		for (int x = 0; x < output.length; x++) {
			output[x] = (float) (t[x] / sum);
		}

		return output;
	}

	@Override
	public void close() {
		try {
			session.close();
		} catch (OrtException e) {
			e.printStackTrace();
		}
	}

}
