package com.reactiveminds.genai.play;

import com.reactiveminds.genai.play.PromptService.SummarizationStrategy;

// naive class!! TODO: make the instance immutable
public class SummarizationOpts {
	
	public SummarizationStrategy STRATEGY = SummarizationStrategy.PROMPT_REDUCTION;
	public Boolean USE_SEMANTIC_SPLIT = false;
	public Boolean OUTPUT_BULLET_POINTS = false;
}
