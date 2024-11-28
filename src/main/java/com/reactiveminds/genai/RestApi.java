package com.reactiveminds.genai;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactiveminds.genai.play.KnowledgeGraphModel;
import com.reactiveminds.genai.play.KnowledgeGraphModelV2;
import com.reactiveminds.genai.play.PromptPlayground;
import com.reactiveminds.genai.play.PromptService;
import com.reactiveminds.genai.play.PromptService.SummarizationStrategy;
import com.reactiveminds.genai.play.RagPlayground;
import com.reactiveminds.genai.play.SummarizationOpts;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController
@Api( tags = "Text Summarization Services")
public class RestApi {
	
	
	@ApiModel(description = "Summary request model")
	public static class SummaryRequest{
		@ApiModelProperty(required = true)
		String kbEndpointUrl;
		@ApiModelProperty(required = false, notes = "using OpenAI tokenizer for chunking the document to be sent to LLM. default 200")
		Integer maxTokensForSplit;
		@ApiModelProperty(required = false, notes = "using max overlapping tokens while splitting into chunks to preserve semantic continuity. default 20")
		Integer maxTokensOverlap;
		@ApiModelProperty(required = false, notes = "Known concepts that can be passed to build a knowledge graph from the document (which is used in the summarization) - "
				+ "name/person/location/service are some example concepts")
		List<String> namedEntityConcepts;
		@ApiModelProperty(required = false)
		Map<String, String> additionalHeaders;
		public String getKbEndpointUrl() {
			return kbEndpointUrl;
		}
		public void setKbEndpointUrl(String kbEndpointUrl) {
			this.kbEndpointUrl = kbEndpointUrl;
		}
		public int getMaxTokensForSplit() {
			return maxTokensForSplit;
		}
		public void setMaxTokensForSplit(int maxTokensForSplit) {
			this.maxTokensForSplit = maxTokensForSplit;
		}
		public int getMaxTokensOverlap() {
			return maxTokensOverlap;
		}
		public void setMaxTokensOverlap(int maxTokensOverlap) {
			this.maxTokensOverlap = maxTokensOverlap;
		}
		public List<String> getNamedEntityConcepts() {
			return namedEntityConcepts;
		}
		public void setNamedEntityConcepts(List<String> namedEntityConcepts) {
			this.namedEntityConcepts = namedEntityConcepts;
		}
		public Map<String, String> getAdditionalHeaders() {
			return additionalHeaders;
		}
		public void setAdditionalHeaders(Map<String, String> additionalHeaders) {
			this.additionalHeaders = additionalHeaders;
		}
		
		
	}
	@Autowired
	PromptPlayground promptPlayground;
	@Autowired
	PromptService promptService;
	@Autowired
	RagPlayground ragPlayground;
	/*
	@ApiOperation(value = "Summarize a KB by passing the endpoint url")
	@PostMapping(path = "/summarize", consumes = "application/json", produces = "text/plain")
	public ResponseEntity<String> summarizeKB(@RequestBody SummaryRequest request) {
		try {
			String summary = promptPlayground.summarize(request.getKbEndpointUrl(), 
					MapUtils.isEmpty(request.getAdditionalHeaders()) ? Map.of() : request.getAdditionalHeaders(), 
							request.maxTokensForSplit == null ? 200 : request.getMaxTokensForSplit(), 
							request.maxTokensOverlap == null ? 20 : request.getMaxTokensOverlap(), 
							null);
			return ResponseEntity.ok(summary);
		} 
		catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}*/
	
	@ApiOperation(value = "Generate a knowledge graph by custom prompting to a LLM")
	@PostMapping(path = "/knowledge-graph", consumes = "text/plain", produces = "application/json")
	public ResponseEntity<?> knowledgeGraph(@RequestBody String request, 
			@RequestParam(name = "maxTokensForSplit", required = false, defaultValue = "200") int maxTokensForSplit,
			@RequestParam(name = "maxTokensOverlap", required = false, defaultValue = "20") int maxTokensOverlap,
			@RequestParam(name = "namedEntityConcepts", required = false, defaultValue = "") String namedEntityConcepts) {
		try {
			List<KnowledgeGraphModelV2> summary = promptPlayground.knowledgeGraph(request, 
					maxTokensForSplit, 
					maxTokensOverlap, 
					namedEntityConcepts);
			
			return ResponseEntity.ok(summary);
		} 
		catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}
	@ApiOperation(value = "Search knowledge base and present an augmented query (neo4j graph+embeddings) to a LLM")
	@GetMapping(path = "/knowledge-graph/search", produces = "text/plain")
	public ResponseEntity<?> knowledgeGraphSearch( 
			@RequestParam(name = "searchQuery") String searchQuery) {
		try {
			String response = ragPlayground.search(searchQuery);
			
			return ResponseEntity.ok(response);
		} 
		catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}
	@ApiOperation(value = "Search news articles knowledge base and present an augmented query (neo4j graph + elastic embeddings) to a LLM")
	@GetMapping(path = "/knowledge-graph/searchNews", produces = "text/plain")
	public ResponseEntity<?> newsSearch( 
			@RequestParam(name = "searchQuery") String searchQuery) {
		try {
			String response = ragPlayground.searchNews(searchQuery);
			
			return ResponseEntity.ok(response);
		} 
		catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}
	@ApiOperation(value = "Summarize a KB by providing the full text")
	@PostMapping(path = "/summarize-content", consumes = "text/plain", produces = "text/plain")
	public ResponseEntity<String> summarizeKBText(@RequestBody String request, 
			@RequestParam(name = "maxTokensForSplit", required = false, defaultValue = "200") int maxTokensForSplit,
			@RequestParam(name = "maxTokensOverlap", required = false, defaultValue = "20") int maxTokensOverlap,
			@RequestParam(name = "namedEntityConcepts", required = false, defaultValue = "") String namedEntityConcepts,
			@RequestParam(name = "useSemanticSplit", defaultValue = "false") boolean useSemanticSplit,
			@RequestParam(name = "outputBulletPoints", defaultValue = "false") boolean outputBulletPoints,
			@RequestParam(name = "strategy") SummarizationStrategy strategy
			) {
		try {
			SummarizationOpts options = new SummarizationOpts();
			options.STRATEGY = strategy;
			options.USE_SEMANTIC_SPLIT = useSemanticSplit;
			options.OUTPUT_BULLET_POINTS = outputBulletPoints;
			
			String summary = promptPlayground.summarizeContent(request, 
							maxTokensForSplit, 
							maxTokensOverlap, 
							namedEntityConcepts,options );
			return ResponseEntity.ok(summary);
		} 
		catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}
	@ApiOperation(value = "Rewrite a technical content in a more SEO optimized manner and extract keywords")
	@PostMapping(path = "/enhance-content", consumes = "text/plain", produces = "application/json")
	public ResponseEntity<?> testPrompt(@RequestBody String request, 
			@RequestParam(name = "useSemanticSplit", defaultValue = "false") boolean useSemanticSplit,
			@RequestParam(name = "maxKeywords", required = false, defaultValue = "5") int maxKeywords
			) {
		try {
			Pair<List<String>, String> pair = promptService.rewriteAndExtract(request, maxKeywords, useSemanticSplit);
			ObjectNode resp = KnowledgeGraphModel.getMapper().createObjectNode()
			.put("document", pair.getRight());
			resp.putArray("keywords").addAll(KnowledgeGraphModel.getMapper().convertValue(pair.getLeft(), ArrayNode.class));
			return ResponseEntity.ok(resp)
					
					;
		} 
		catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}
	
	@PostMapping(path = "/summarize-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "text/plain")
    @ApiOperation(value = "Summarize a text/pdf document content")    
    public ResponseEntity<String> summarizeDocument(
    		@RequestParam(name = "maxTokensForSplit", required = false, defaultValue = "400") int maxTokensForSplit,
			@RequestParam(name = "maxTokensOverlap", required = false, defaultValue = "40") int maxTokensOverlap,
			@RequestParam(name = "namedEntityConcepts", required = false, defaultValue = "") String namedEntityConcepts,
			@RequestParam(name = "useSemanticSplit", defaultValue = "true") boolean useSemanticSplit,
			@RequestParam(name = "outputBulletPoints", defaultValue = "false") boolean outputBulletPoints,
			@RequestParam(name = "strategy") SummarizationStrategy strategy,
            @ApiParam(name = "file", value = "Select the file to summarize", required = true)
            @RequestPart("file") MultipartFile file) {

        try {
            
            SummarizationOpts options = new SummarizationOpts();
			options.STRATEGY = strategy;
			options.USE_SEMANTIC_SPLIT = useSemanticSplit;
			options.OUTPUT_BULLET_POINTS = outputBulletPoints;
			
			String summary = promptService.summarization(file.getInputStream(), 
							maxTokensForSplit, 
							maxTokensOverlap, 
							namedEntityConcepts,options );
			
			return ResponseEntity.ok(summary);
        } 
        catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<String>("Failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
