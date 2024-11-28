package com.reactiveminds.genai.play;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.reactiveminds.genai.core.LanguageModel;
import com.reactiveminds.genai.core.vec.Neo4jEmbeddingsOnly;
import com.reactiveminds.genai.play.KnowledgeGraphModel.Relationship;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.input.PromptTemplate;
@Component
public class PromptService extends AbstractFlow{
	@Autowired
	LanguageModel chatModel;
	
	@Autowired
	Neo4jEmbeddingsOnly embedOnly;
	
	private static final Logger log = LoggerFactory.getLogger(PromptService.class);
	
	public static enum SummarizationStrategy{
		GRAPH_REDUCTION,PROMPT_REDUCTION
	}
	
	// https://github.com/rahulnyk/knowledge_graph/blob/main/helpers/prompts.py	
	static final String KNOWLEDGE_GRAPH_SYS_PROMPT = "\"You are a top-tier algorithm designed for extracting terms and their relations from a given context to build a knowledge graph. \"\n"
			+ "    \"You are provided with a context chunk (delimited by ```) and your task is to extract the ontology \"\n"
			+ "    \"of terms mentioned in the given context. These terms should represent the key concepts as per the context. Ensure you use basic or elementary types for term labels."
			+ "\\n\"\n"
			+ "    \"Thought 1: While traversing through each sentence, Think about the key terms mentioned in it.\\n\"\n"
			+ "        \"\\tTerms may include {{concepts}}\\n\"\n"
			+ "        \"\\tTerms should be as atomistic as possible\\n\\n\"\n"
			+ "    \"Thought 2: Think about how these terms can have one on one relation with other terms.\\n\"\n"
			+ "        \"\\tTerms that are mentioned in the same sentence or the same paragraph are typically related to each other.\\n\"\n"
			+ "        \"\\tTerms can be related to many other terms\\n\\n\"\n"
			+ "    \"Thought 3: Find out the relation between each such related pair of terms. \\n\\n\"\n"
			+ "    \"Thought 4: When extracting terms, it is vital to ensure coreference resolution. If a term, such as \\\"John Doe\\\", is mentioned multiple times in the text but is referred to by different names or pronouns (e.g., \\\"Joe\\\", \\\"he\\\"), "
			+ "			+ \"always use the most complete identifier for that term throughout the knowledge graph. In this example, use \\\"John Doe\\\" as the term. \\n\\n\"\n"
			+ "\\n\\n"
			
			+ "    \"Format your output as a list of json. Each element of the list contains a pair of terms\"\n"
			+ "    \"and the relation between them, like the following: \\n\"\n"
			+ "    \"[\\n\"\n"
			+ "    \"   {\\n\"\n"
			+ "    '       \"source\": \"A term from extracted ontology\",\\n'\n"
			+ "    '       \"target\": \"A related term from extracted ontology\",\\n'\n"
			+ "    '       \"type\": \"relationship between the two terms, `source` and `target` in one or two words\"\\n'\n"
			+ "    \"   }, {...}\\n\"\n"
			+ "    \"]\"\n Respond with valid JSON only. Do not write an introduction or summary.";
	
	static final String deafultNamedEntities = "concepts,entities,person,location,organization,service";
	
	static final String KNOWLEDGE_GRAPH_SUMMARY_SYS_PROMPT = "\"As a top tier algorithm, your task is to generate a summary \"\n"
			+ "    \"of the provided context (delimited by ```) from the knowledge graph ontology of the context. \"\n"
			+ "Ensure you use basic or elementary types for term labels and ignore any code snippets, if present."
			+ "\\n\"\n"
			+ "    \"Thought 1: While traversing through each sentence, Think about the key terms mentioned in it.\\n\"\n"
			+ "        \"\\tTerms may include {{concepts}} etc.\\n\"\n"
			+ "        \"\\tTerms should be as atomistic as possible\\n\\n\"\n"
			+ "    \"Thought 2: Think about how these terms can have one on one relation with other terms.\\n\"\n"
			+ "        \"\\tTerms that are mentioned in the same sentence or the same paragraph are typically related to each other.\\n\"\n"
			+ "        \"\\tTerms can be related to many other terms\\n\\n\"\n"
			+ "    \"Thought 3: Find out the relation between each such related pair of terms.\\n\\n\"\n"			
			+ "    \"Thought 4: Describe in a sentence to coherently connect a pair of related terms with their relationship. \\n\\n"
			
			+ "\\n\\n Do not write an introduction or summary or steps.";
	
	static final String INPUT_DATA_PROMPT = "context: ```{{input}}``` \\n\\n output: ";
	
	static final String REDUCTIVE_SUMMARY_SYS_PROMPT = "As a professional summarizer, create a concise and comprehensive summary of the provided context (delimited by ```), "
			+ "be it an article, post, conversation, or passage, while adhering to these guidelines:\n\n"
			+ "Thought 1. Craft a summary that is thorough and in-depth, while avoiding any unnecessary information or repetition.\n"
			+ "Thought 2. Please ensure that the summary includes relevant details and examples that support the main ideas.\n"
			+ "Thought 3. To ensure accuracy, please read the text carefully and pay attention to any nuances or complexities in the language.\n"
			+ "Thought 4. Rely strictly on the provided text, without including external information.\n\n\\n"
			+ "Format the content as list of sentences."
			;
	
	static final String SUMMARY_OF_SUMMARY_USR_PROMPT = "Write a comprehensive summary of the following text delimited by triple backquotes.\n"
			+ "Return your response in bullet points which covers the key points of the text.\n"
			+ "\n\n\n"
			+ "    ```{{text}}```"
			;
	static final String ONESHOT_SUMMARY_USR_PROMPT = "Write a comprehensive summary of the following text delimited by triple backquotes.\n"
			+ "\n\n\n"
			+ "    ```{{text}}```"
			;
	
	static final String MISTRAL_KG_SYS_PROMPT = "You are a top-tier algorithm designed for extracting structured information to build a knowledge graph, while "
			+ "adhering to the below guidelines: \n\n"
			+ "Step 1: Entity Recognition\n"
			+ "a. Entities will be one of the types from the following: {{concepts}}\n"
			+ "b. Identify core entities, relationships, and attributes within the text\n"
			+ "c. Ensure you use basic or elementary types for entity labels"
			+ "d. Create a list of key entities for ontology generation\n"
			+ "\n"
			+ "Step 2: Relationship Extraction\n"
			+ "a. Utilize Relation Extraction techniques to identify relationships between entities in the text\n"
			+ "b. Determine relationships such as \"is-a\", \"part-of\", \"has-a\", \"located-in\" etc.\n"
			+ "c. Create a list of key relationships for ontology generation\n"
			+ "\n"
			+ "Step 3: Ontology Generation\n"
			+ "a. Develop an initial ontology based on the core entities and relationships identified\n"
			+ "b. Iteratively refine the ontology by integrating new entities, relationships, and attributes as they are \n"
			+ "discovered in the text\n"
			+ "c. Ensure that the final ontology is coherent and logically consistent \n"
			+ "d. If an entity, such as \"John Doe\", is mentioned multiple times in the text "
			+ "but is referred to by different names or pronouns (e.g., \"John\", \"he\"), always use the most complete identifier (\"John Doe\" in this example) for that entity throughout the knowledge graph \n\n"
			
			+ "You must generate the output in a JSON format containing a list with JSON objects. Each object should have the keys: \"head\","
			+ "\"head_type\", \"relation\", \"tail\", and \"tail_type\". \n"
			+ "- The \"head\" key must contain the text of the extracted entity.\n"
			+ "- The \"head_type\" key must contain the type of the extracted head entity which must be one of the types from {{concepts}}.\n"
			+ "- The \"relation\" key must contain the type of relation between the \"head\" and the \"tail\".\n"
			+ "- The \"tail\" key must represent the text of an extracted entity which is the tail of the relation.\n"
			+ "- The \"tail_type\" key must contain the type of the extracted tail entity, which must be one of the types from {{concepts}}.\n "
			+ "\n\n Attempt to extract as many entities and relations as you can.\n\n"

			;
	
	
	// https://bratanic-tomaz.medium.com/constructing-knowledge-graphs-from-text-using-openai-functions-096a6d010c17
	static final String KNOWLEDGE_GRAPH_SYS_PROMPT_WITH_EX = 
			
			"You are a top-tier algorithm designed for extracting structured information to build a knowledge graph. Your task is to identify the entities and relations from a given text."
			+ "Try to capture as much information from the text as possible without sacrificing accuracy. Do not add any information that is not explicitly mentioned in the text."
			+ "You must generate the output in a JSON format containing a list with JSON objects. Each object should have the keys: \"head\","
			+ "\"head_type\", \"relation\", \"tail\", and \"tail_type\". \n"
			+ "- The \"head\" key must contain the text of exactly one extracted entity.\n"
			+ "- The \"head_type\" key must contain the type of the extracted head entity which must be one of the types from {{concepts}}.\n"
			+ "- The \"relation\" key must contain the type of relation between the \"head\" and the \"tail\".\n"
			+ "- The \"tail\" key must represent the text of exactly one extracted entity, which is the tail of the relation.\n"
			+ "- The \"tail_type\" key must contain the type of the extracted tail entity, which must be one of the types from {{concepts}}.\n "
						
			+ "If an entity, such as \"John Doe\", is mentioned multiple times in the text but is referred to by different names or pronouns (e.g., \"Joe\", \"he\"), "
			+ "always use the most complete identifier for that entity throughout the knowledge graph. In this example, use \"John Doe\" as the \"head\" or \"tail\". "
			+ "Remember, the knowledge graph should be coherent and easily understandable, so maintaining consistency in entity references is crucial. \n"

			;
		
	@Deprecated
	static final String KNOWLEDGE_GRAPH_SYS_PROMPT_V3 = "\"# Knowledge Graph Instructions\\n\"\n"
			+ "    \"## 1. Overview\\n\"\n"
			+ "    \"You are a top-tier algorithm designed for extracting information in structured \"\n"
			+ "    \"formats to build a knowledge graph.\\n\"\n"
			+ "    \"Try to capture as much information from the text as possible without \"\n"
			+ "    \"sacrificing accuracy. Do not add any information that is not explicitly \"\n"
			+ "    \"mentioned in the text.\\n\"\n"
			+ "    \"- **Nodes** represent entities and concepts.\\n\"\n"
			+ "    \"- The aim is to achieve simplicity and clarity in the knowledge graph, making it\\n\"\n"
			+ "    \"accessible for a vast audience.\\n\"\n"
			+ "    \"## 2. Labeling Nodes\\n\"\n"
			+ "    \"- **Consistency**: Ensure you use available types for node labels.\\n\"\n"
			+ "    \"Ensure you use basic or elementary types for node labels.\\n\"\n"
			+ "    \"- For example, when you identify an entity representing a person, \"\n"
			+ "    \"always label it as **'person'**. Avoid using more specific terms \"\n"
			+ "    \"like 'mathematician' or 'scientist'.\"\n"
			+ "    \"- **Node IDs**: Never utilize integers as node IDs. Node IDs should be \"\n"
			+ "    \"names or human-readable identifiers found in the text.\\n\"\n"
			+ "    \"- **Relationships** represent connections between entities or concepts.\\n\"\n"
			+ "    \"Ensure consistency and generality in relationship types when constructing \"\n"
			+ "    \"knowledge graphs. Instead of using specific and momentary types \"\n"
			+ "    \"such as 'BECAME_PROFESSOR', use more general and timeless relationship types \"\n"
			+ "    \"like 'PROFESSOR'. Make sure to use general and timeless relationship types!\\n\"\n"
			+ "    \"## 3. Coreference Resolution\\n\"\n"
			+ "    \"- **Maintain Entity Consistency**: When extracting entities, it's vital to \"\n"
			+ "    \"ensure consistency.\\n\"\n"
			+ "    'If an entity, such as \"John Doe\", is mentioned multiple times in the text '\n"
			+ "    'but is referred to by different names or pronouns (e.g., \"Joe\", \"he\"),'\n"
			+ "    \"always use the most complete identifier for that entity throughout the \"\n"
			+ "    'knowledge graph. In this example, use \"John Doe\" as the entity ID.\\n'\n"
			+ "    \"Remember, the knowledge graph should be coherent and easily understandable, \"\n"
			+ "    \"so maintaining consistency in entity references is crucial.\\n\"\n"
			+ "    \"## 4. Strict Compliance\\n\"\n"
			+ "    \"Adhere to the rules strictly. Non-compliance will result in termination.\"";
	
	static final String KNOWLEDGE_GRAPH_USR_PROMPT_V3 = "Make sure to answer in strict JSON format and do not provide any explanations or steps. "
			+ "For the following text, build a knowledge graph ontology. \nText: {{input}}";
			
	static final String KNOWLEDGE_GRAPH_USR_PROMPT_WITH_EX = 
			"Based on the following example, attempt to extract as many entities and relations as you can.\n"
			+ "Below are a number of examples of text and their extracted entities and relationships.\n"
			+ "        {{examples}}\n"
			+ "For the following text, extract entities and relations as in the provided example. "
			+ "Make sure to respond in strict JSON format only, as provided in the example. Do not provide any introduction or steps.\nText: {{input}}";
	
	static final String KNOWLEDGE_GRAPH_SAMPLE_SPLIT = ""
			+ "Text: \"Adam is a software engineer in Microsoft since 2009, and last year he got an award as the Best Talent\" \n"
			+ "Output: [\n"
			+ "		{\n"
			+ "        \n"
			+ "        \"head\": \"Adam\",\n"
			+ "        \"head_type\": \"Person\",\n"
			+ "        \"relation\": \"WORKS_FOR\",\n"
			+ "        \"tail\": \"Microsoft\",\n"
			+ "        \"tail_type\": \"Company\",\n"
			+ "    },\n"
			+ "    {\n"
			+ "        \n"
			+ "        \"head\": \"Adam\",\n"
			+ "        \"head_type\": \"Person\",\n"
			+ "        \"relation\": \"HAS_AWARD\",\n"
			+ "        \"tail\": \"Best Talent\",\n"
			+ "        \"tail_type\": \"Award\",\n"
			+ "    }"
			+ "\n] \n\n"
			
			+ "Text: \"Microsoft is a tech company that provide several products such as Microsoft Word\" \n"
			+ "Output: [\n"
			+ "		{\n"
			+ "        \n"
			+ "        \"head\": \"Microsoft Word\",\n"
			+ "        \"head_type\": \"Product\",\n"
			+ "        \"relation\": \"PRODUCED_BY\",\n"
			+ "        \"tail\": \"Microsoft\",\n"
			+ "        \"tail_type\": \"Company\",\n"
			+ "    } \n] \n\n"
			
			+ "Text: \"Microsoft Word is a lightweight app that is accessible offline\" \n"
			+ "Output: [\n"
			+ "		{\n"
			+ "        \"head\": \"Microsoft Word\",\n"
			+ "        \"head_type\": \"Product\",\n"
			+ "        \"relation\": \"HAS_CHARACTERISTIC\",\n"
			+ "        \"tail\": \"lightweight app\",\n"
			+ "        \"tail_type\": \"Characteristic\",\n"
			+ "    },\n"
			+ "    {\n"
			+ "        \"head\": \"Microsoft Word\",\n"
			+ "        \"head_type\": \"Product\",\n"
			+ "        \"relation\": \"HAS_CHARACTERISTIC\",\n"
			+ "        \"tail\": \"accessible offline\",\n"
			+ "        \"tail_type\": \"Characteristic\",\n"
			+ "    }\n]"
			;
	
	
	static final String KNOWLEDGE_GRAPH_SAMPLE = "[\n"
			+ "    {\n"
			+ "        \"text\": "
			+ "            \"Adam is a software engineer in Microsoft since 2009, \"\n"
			+ "            \"and last year he got an award as the Best Talent\"\n"
			+ "        ,\n"
			+ "        \"head\": \"Adam\",\n"
			+ "        \"head_type\": \"Person\",\n"
			+ "        \"relation\": \"WORKS_FOR\",\n"
			+ "        \"tail\": \"Microsoft\",\n"
			+ "        \"tail_type\": \"Company\",\n"
			+ "    },\n"
			+ "    {\n"
			+ "        \"text\": "
			+ "            \"Adam is a software engineer in Microsoft since 2009, \"\n"
			+ "            \"and last year he got an award as the Best Talent\"\n"
			+ "        ,\n"
			+ "        \"head\": \"Adam\",\n"
			+ "        \"head_type\": \"Person\",\n"
			+ "        \"relation\": \"HAS_AWARD\",\n"
			+ "        \"tail\": \"Best Talent\",\n"
			+ "        \"tail_type\": \"Award\",\n"
			+ "    },\n"
			
			+ "    {\n"
			+ "        \"text\": "
			+ "            \"Microsoft is a tech company that provide \"\n"
			+ "            \"several products such as Microsoft Word\"\n"
			+ "        ,\n"
			+ "        \"head\": \"Microsoft Word\",\n"
			+ "        \"head_type\": \"Product\",\n"
			+ "        \"relation\": \"PRODUCED_BY\",\n"
			+ "        \"tail\": \"Microsoft\",\n"
			+ "        \"tail_type\": \"Company\",\n"
			+ "    },\n"
			+ "    {\n"
			+ "        \"text\": \"Microsoft Word is a lightweight app that accessible offline\",\n"
			+ "        \"head\": \"Microsoft Word\",\n"
			+ "        \"head_type\": \"Product\",\n"
			+ "        \"relation\": \"HAS_CHARACTERISTIC\",\n"
			+ "        \"tail\": \"lightweight app\",\n"
			+ "        \"tail_type\": \"Characteristic\",\n"
			+ "    },\n"
			+ "    {\n"
			+ "        \"text\": \"Microsoft Word is a lightweight app that accessible offline\",\n"
			+ "        \"head\": \"Microsoft Word\",\n"
			+ "        \"head_type\": \"Product\",\n"
			+ "        \"relation\": \"HAS_CHARACTERISTIC\",\n"
			+ "        \"tail\": \"accessible offline\",\n"
			+ "        \"tail_type\": \"Characteristic\",\n"
			+ "    },\n {...}"
			+ "]"
			
			;
	
	private static String CONTENT_REWRITER = "You are a top level content writer. "
			+ "Given a context, your task is to rewrite the original content by adding relevant quotations and statistics, as appropriate for the theme. \n\n"
			+ "Remember to keep the facts and examples expressed intact. "
			+ "Do not add any new ideas or facts.";
	
	private static String KEYWORD_EXTRACTION = "I have the following document enclosed delimited by triple backquotes : ```{{input}}``` \n\n "
			+ "Based on the information above, extract the relevant keywords that best describe the topic of the text. Give response formatted as a JSON array";
	
	private String runSummaryPass(List<TextSegment> segments, String concepts) {
		List<String> epoch = summarizeChunk(segments, true, concepts);   
        return epoch.stream().collect(Collectors.joining());
	}
	public String tryHelloChat(String friendlyHi) {
		AiMessage aiMessage = chatModel.getLLM().generate(
    			SystemMessage.from("You are a good friend of mine, who likes to answer with jokes. Do not include instructions in response"),  
    			UserMessage.from(friendlyHi))
    			.content();
		return aiMessage.text();
	}
	private String rewriteContent(String content) {
		AiMessage aiMessage = chatModel.getLLM().generate(
    			SystemMessage.from(CONTENT_REWRITER),  
    			PromptTemplate.from(INPUT_DATA_PROMPT).apply(Map.of("input", content)).toUserMessage()
    			)
    			.content();
				
		return aiMessage.text();
	}
	public Pair<List<String>,String> rewriteAndExtract(String content, int maxKeywords, boolean splitDoc) {
		String rewritten = splitDoc ? rewriteSplittableContent(content) : rewriteContent(content);
		List<String> keywords = extractKeywords(content);
		log.info(WordUtils.wrap(rewritten, 80));
		return Pair.of(new ArrayList<>(keywords).subList(0, maxKeywords), rewritten);
	}
	private List<String> extractKeywords(String content) {
		String keywords = chatModel.getLLM().generate(
    			PromptTemplate.from(KEYWORD_EXTRACTION).apply(Map.of("input", content)).toUserMessage()
    			)
    			.content().text();
		List<String> asList = KnowledgeGraphModel.toTypedObject(keywords, new TypeReference<List<String>>() {});
		asList = chatModel.getRelevantKeywords(content, asList);
		log.info(keywords);
		return asList;
	}
	private String rewriteSplittableContent(String content) {
		
		try {
			List<TextSegment> chunks = splitDocument(new ByteArrayInputStream(content.getBytes()), 300, 0, true);
			return chunks.stream()
			.map(ts -> rewriteContent(ts.text()))
			.collect(Collectors.joining("\n\n"));
		} 
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
	}
	private List<String> summarizeChunk(List<TextSegment> segments, boolean returnJsonArray, String concepts) {
		AtomicInteger i = new AtomicInteger();
		int l = segments.size();
		ForkJoinPool fjPool = new ForkJoinPool(4);
		try {
			return fjPool.submit(() -> 
				segments.parallelStream()
			    .map(text -> {
			    	        	
			    	AiMessage aiMessage = chatModel.getLLM().generate(
			    			PromptTemplate.from(KNOWLEDGE_GRAPH_SUMMARY_SYS_PROMPT).apply(Map.of("concepts", concepts != null ? concepts : deafultNamedEntities)).toSystemMessage(),  
			    			PromptTemplate.from(INPUT_DATA_PROMPT).apply(Map.of("input", text.text())).toUserMessage())
			    			.content();
			        log.debug(aiMessage.text());
			        log.info("chunk {} of {}", i.getAndIncrement(), l);
			        String response = sanitize(aiMessage);
			        try {
			        	return response;
			        }
			        catch(Exception e) {
			        	log.error(e.getMessage(), e);
			        	log.error(aiMessage.text());
			        }
			        return null;
			        
			    }).filter(Objects::nonNull) .collect(Collectors.toList())
				
			).get();
		} 
		catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
		finally {
			fjPool.shutdown();
		}
		
	}
	public static String sanitize(AiMessage aiMessage) {
		String result = aiMessage.text().trim();
		if(result.startsWith("```")) {
			result = result.substring(3);
		}
		if(result.endsWith("```")) {
			result = result.substring(0, result.length()-3);
		}
		
		return result;
	}
	
	public String summarization(InputStream in, int maxTokens, int maxOverlap, String concepts, SummarizationOpts options) throws IOException {
		List<TextSegment> segments = splitDocument(in, maxTokens, maxOverlap, options.USE_SEMANTIC_SPLIT);	
		log.info("*** EPOCH 0 *** segments={}", segments.size());
		// list size should be = segments size
        List<String> summarizedChunksByKG = summarizeChunk(segments, true, concepts);   
        String joinedSummary="";
        switch(options.STRATEGY) {
			case GRAPH_REDUCTION:
				joinedSummary = mergeKnowledgeGraphStrategy(summarizedChunksByKG, maxTokens, maxOverlap, concepts);
				break;
			case PROMPT_REDUCTION:
				joinedSummary =  mergeSummaryPromptStrategy(summarizedChunksByKG, maxTokens, maxOverlap, concepts);
				break;
			default:
				break;
        
        }
        log.debug("joinedSummary: {}", joinedSummary);
        joinedSummary = options.OUTPUT_BULLET_POINTS ? ask(SUMMARY_OF_SUMMARY_USR_PROMPT, Map.of("text", joinedSummary)) : paragraphize(joinedSummary);
        
        log.info("out doc bytes={}", joinedSummary.getBytes().length);
        return joinedSummary;
	}
	private String ask(String prompt, Map<String,Object> args) {
		return chatModel.getLLM().generate(
				PromptTemplate.from(prompt).apply(args).toUserMessage()
            	)
            	.content().text();
	}
	public String paragraphize(String merged) {
		//put paragraphs to semantically connected statements
        return Arrays.asList(semantic.split(merged))
        .stream()
        .map(s -> WordUtils.wrap(s, 80))
        .collect(Collectors.joining("\n\n"));
	}
	private String mergeKnowledgeGraphStrategy(final List<String> summarizedChunksByKGIn, int maxTokens, int maxOverlap, String concepts) {
		log.info("Using knowledge graph strategy");
		StringBuilder buff = new StringBuilder();       
        List<String> nextEpoch = new ArrayList<>();
        List<String> summarizedChunksByKG = new ArrayList<>(summarizedChunksByKGIn);
        int i = 0;
        
        for ( ;i < summarizedChunksByKG.size()-1; i+=2) {
			buff.append(summarizedChunksByKG.get(i));
			buff.append(summarizedChunksByKG.get(i+1));
			nextEpoch.add(buff.toString());
			buff.setLength(0);
		}
        if(i == summarizedChunksByKG.size()-1) {
        	buff.append(summarizedChunksByKG.get(i));
        }
        if(!buff.isEmpty()) {
        	nextEpoch.add(buff.toString());
        	buff.setLength(0);
        }
        
        // divide and conquer LogN - summary with KG
        AtomicInteger pass = new AtomicInteger(1);
        
        while (nextEpoch.size() > 1) {
        	log.info("*** EPOCH {} *** segments={}", pass.get(), nextEpoch.size());
        	summarizedChunksByKG = nextEpoch.stream()
        	.map(s -> splitContent(List.of(s), maxTokens, maxOverlap))
        	.map(nextSegments -> runSummaryPass(nextSegments, concepts))
        	.collect(Collectors.toList());
        	nextEpoch.clear();
        	
        	i=0;
        	for ( ;i < summarizedChunksByKG.size()-1; i+=2) {
    			buff.append(summarizedChunksByKG.get(i));
    			buff.append(summarizedChunksByKG.get(i+1));
    			nextEpoch.add(buff.toString());
    			buff.setLength(0);
    		}
            if(i == summarizedChunksByKG.size()-1) {
            	buff.append(summarizedChunksByKG.get(i));
            }
            if(!buff.isEmpty()) {
            	nextEpoch.add(buff.toString());
            	buff.setLength(0);
            }
	        
	        pass.incrementAndGet();
        }
        Assert.isTrue(nextEpoch.size() == 1, "Final summary not found!"); 
        //final pass after LogN KG summarize
        return nextEpoch.get(0);
	}
	
	static ChatMemory chatMemoryOf(int messages) {
		return MessageWindowChatMemory
				.builder()
				.id(UUID.randomUUID())
				.maxMessages(messages).build();
	}
	private String mergeSummaryPromptStrategy(final List<String> summarizedChunksByKG, int maxTokens, int maxOverlap, String concepts) {
		log.info("Using summary prompt strategy");
		ChatMemory chatMemory = chatMemoryOf(2);
				//1 user message, 1 ai message - sys message is always stored  
		
		List<TextSegment> segments = splitContent(summarizedChunksByKG, maxTokens, maxOverlap);
        log.info("Running summary prompt on {} segments..", segments.size());
        List<String> summarizedChunksByPrompt = segments.stream()
            	.map(s -> summarizePrompt(chatMemory, s.text(), concepts))
            	.collect(Collectors.toCollection(LinkedList::new));
        
        log.info("Summarization complete: {}");	        
        log.debug(summarizedChunksByPrompt.toString());
        
        return summarizedChunksByPrompt.stream().collect(Collectors.joining());
        
	}
	public String summarizePrompt(String content) {
		AiMessage aiMessage = chatModel.getLLM().generate(
				PromptTemplate.from("As an expert assistant, create a concise and comprehensive summary of the provided context (delimited by ```), \"\n"
						+ "			+ \"be it an article, post, conversation, or passage, while adhering to these guidelines:\\n\\n\"\n"
						+ "			+ \"Thought 1. Craft a summary that is thorough and in-depth, while avoiding any unnecessary information or repetition.\\n\"\n"
						+ "			+ \"Thought 2. Please ensure that the summary includes relevant details and examples that support the main ideas.\\n\"\n"
						+ "			+ \"Thought 3. To ensure accuracy, please read the text carefully and pay attention to any nuances or complexities in the language.\\n\"\n"
						+ "			+ \"Thought 4. Rely strictly on the provided text, without including external information.\\n\\n "
						+ "Only provide the summary without any explanations.").apply(Map.of()).toSystemMessage(),
				PromptTemplate.from(INPUT_DATA_PROMPT).apply(Map.of("input", content)).toUserMessage()
    	)
    	.content();
		
        return aiMessage.text();
	}
	private String summarizePrompt(ChatMemory chatMemory, String strToSummarize, String concepts) {
		chatMemory.add(PromptTemplate.from(REDUCTIVE_SUMMARY_SYS_PROMPT).apply(Map.of("concepts", concepts != null ? concepts : deafultNamedEntities)).toSystemMessage());
		chatMemory.add(PromptTemplate.from(INPUT_DATA_PROMPT).apply(Map.of("input", strToSummarize)).toUserMessage());
		AiMessage aiMessage = chatModel.getLLM().generate(
    			chatMemory.messages() 
    	)
    	.content();
		chatMemory.add(aiMessage);
		log.info("chat memory updated ..");
        return aiMessage.text();
	}
	public String summarization(String resourcePath, int maxTokens, int maxOverlap, String concepts, SummarizationOpts options) {
		try(InputStream in = ResourceUtils.getURL(resourcePath).openStream()){
			return summarization(in, maxTokens, maxOverlap, concepts, options);
		}  
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}		
		
	}
	
	static interface GraphLLMTransformer<R> extends Function<TextSegment, R>{
		
	}
	class V1LLMTransformer implements GraphLLMTransformer<List<Relationship>>{

		final String concepts;
		final AtomicInteger count;
		final int size;
		public V1LLMTransformer(String concepts, AtomicInteger count, int size) {
			super();
			this.concepts = concepts;
			this.count = count;
			this.size = size;
		}
		@Override
		public List<Relationship> apply(TextSegment text) {
			
	    	AiMessage aiMessage = chatModel.getLLM(). generate(
	    			PromptTemplate.from(KNOWLEDGE_GRAPH_SYS_PROMPT).apply(Map.of("concepts", concepts != null ? concepts : deafultNamedEntities)).toSystemMessage(),  
	    			PromptTemplate.from("Use the given format to extract information from the following input: {{input}}").apply(Map.of("input", text.text())).toUserMessage())
	    			
	    			.content();
	    	log.info(String.format("%d of %d segments:", count.incrementAndGet(), size));
	        log.debug(aiMessage.text());
	        try {
				String json = (aiMessage.text());
				json = sanitize(aiMessage);
				return KnowledgeGraphModel.toRelations(json);
			} 
	        catch (Exception e) {
				log.error(e.getMessage(), e);
				log.error(aiMessage.text());
			}
	        return List.of();
		}
		
	}
	class V2LLMTransformer implements GraphLLMTransformer<List<KnowledgeGraphModelV2>>{

		final String concepts;
		final AtomicInteger count;
		final int size;
		public V2LLMTransformer(String concepts, AtomicInteger count, int size) {
			super();
			this.concepts = concepts;
			this.count = count;
			this.size = size;
		}
		@Override
		public List<KnowledgeGraphModelV2> apply(TextSegment text) {
			List<ChatMessage> chatMsg = List.of(
					PromptTemplate.from(MISTRAL_KG_SYS_PROMPT).apply(Map.of("concepts", concepts != null ? concepts : deafultNamedEntities)).toSystemMessage(),
					PromptTemplate.from(KNOWLEDGE_GRAPH_USR_PROMPT_V3).apply(Map.of(
    						"input", text.text()
    						//,"examples", KNOWLEDGE_GRAPH_SAMPLE
    					)).toUserMessage()
					);
			//chatModel.getTokenizer().
			log.info(String.format("%d of %d segments, token size=%d:", count.incrementAndGet(), size, chatModel.getTokenizer().estimateTokenCountInMessages(chatMsg)));	    	
			AiMessage aiMessage = chatModel.getLLM(). generate(chatMsg).content();	    		    	
	        log.debug(aiMessage.type().toString());
	        try {
	        	String response = sanitize(aiMessage);
				return KnowledgeGraphModel.toTypedObject( response, new TypeReference<List<KnowledgeGraphModelV2>>() {});
			} 
	        catch (Exception e) {
				log.error("* ERROR parsing model response * ".concat(e.getMessage()));
				log.error(aiMessage.text());
			}
	        return List.of();
		}
		
	}
	//for wide context windows
	class V3LLMTransformer implements GraphLLMTransformer<List<KnowledgeGraphModelV2>>{

		final String concepts;
		final AtomicInteger count;
		final int size;
		public V3LLMTransformer(String concepts, AtomicInteger count, int size) {
			super();
			this.concepts = concepts;
			this.count = count;
			this.size = size;
		}
		@Override
		public List<KnowledgeGraphModelV2> apply(TextSegment text) {
			List<ChatMessage> chatMsg = List.of(
					PromptTemplate.from(KNOWLEDGE_GRAPH_SYS_PROMPT_WITH_EX).apply(Map.of("concepts", concepts != null ? concepts : deafultNamedEntities)).toSystemMessage(),
					PromptTemplate.from(KNOWLEDGE_GRAPH_USR_PROMPT_WITH_EX).apply(Map.of(
    						"input", text.text()
    						,"examples", KNOWLEDGE_GRAPH_SAMPLE_SPLIT
    					)).toUserMessage()
					);
			//chatModel.getTokenizer().
			log.info(String.format("%d of %d segments, token size=%d:", count.incrementAndGet(), size, chatModel.getTokenizer().estimateTokenCountInMessages(chatMsg)));	    	
			AiMessage aiMessage = chatModel.getLLM(). generate(chatMsg).content();	    		    	
	        log.debug(aiMessage.text());
	        try {
	        	String response = sanitize(aiMessage);
				return KnowledgeGraphModel.toTypedObject( response, new TypeReference<List<KnowledgeGraphModelV2>>() {});
			} 
	        catch (Exception e) {
				log.error("* ERROR parsing model response (below) * ".concat(e.getMessage()));
				log.error(aiMessage.text());
			}
	        return List.of();
		}
		
	}
	/**
	 * Generate a knowledge graph and save it to graph db (neo4j)
	 * @param content
	 * @param maxTokens
	 * @param maxOverlap
	 * @param concepts
	 * @param embed
	 * @return
	 */
	public List<KnowledgeGraphModelV2> knowledgeGraph(String content, int maxTokens, int maxOverlap, String concepts, boolean embed) {
		
		try(InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))){
			List<TextSegment> segments = splitDocument(in, maxTokens, maxOverlap, true);			
	        
	        
	        AtomicInteger count = new AtomicInteger();
	        ForkJoinPool fjPool = new ForkJoinPool(1);
	        List<KnowledgeGraphModelV2> modelList = null;
	        try {
				modelList = fjPool.submit(() -> 
					segments.parallelStream().
				    flatMap(text -> new V3LLMTransformer(concepts, count, segments.size()).apply(text).stream())
				    .filter(Objects::nonNull)
				    .collect(Collectors.toList())									
				).get();
			} 
	        catch (InterruptedException | ExecutionException e) {
				
				e.printStackTrace();
			}
	        finally {
	        	fjPool.shutdown();
	        }	        	            	        
	        
	        if(modelList != null) {	        	
	        	save(modelList, segments, embed);
	        }
	        
	        return modelList;
		}  
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}		
		
	}
	public List<KnowledgeGraphModelV2> knowledgeGraph(String content, int maxTokens, int maxOverlap, String concepts) {
		
		return knowledgeGraph(content, maxTokens, maxOverlap, concepts, true);		
		
	}
	
	static PropertyNamingStrategies.SnakeCaseStrategy caseUtil = new SnakeCaseStrategy();
	
	private void save(List<KnowledgeGraphModelV2> modelList, List<TextSegment> segments, boolean embed) {	
		
		if (embed) {
			embedOnly.embedSegments(segments);
		}
		
	}

}
