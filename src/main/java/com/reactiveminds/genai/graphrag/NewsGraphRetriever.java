package com.reactiveminds.genai.graphrag;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.apache.commons.collections4.CollectionUtils;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.TypeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.reactiveminds.genai.play.KnowledgeGraphModel;
import com.reactiveminds.genai.play.PromptService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.graph.neo4j.Neo4jException;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;

class NewsGraphRetriever implements ContentRetriever{
	private static final Logger log = LoggerFactory.getLogger(NewsGraphRetriever.class);
    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Type NODE = TypeSystem.getDefault().NODE();

    private final Neo4jGraph graph;
    private Driver driver;

    private final ChatLanguageModel chatLanguageModel;
    
    static final String FIND_LABEL_BY_NAME = "MATCH (a) where a.name=$name RETURN labels(a) limit 1";
    //not using fuzzy search. the entity names should already be standardized during ingestion. The question has to be 'correct'
    static final String FIND_INRELATIONS_FOR_LABEL = "MATCH (a)<-[r]-(b) WHERE a.name=$name RETURN b.name, type(r), a.name";
    static final String FIND_OUTRELATIONS_FOR_LABEL = "MATCH (a)-[r]->(b) WHERE a.name=$name RETURN a.name, type(r), b.name";

    @Autowired
    public NewsGraphRetriever(Neo4jGraph graph, Driver driver, ChatLanguageModel chatLanguageModel) {
    	this.driver = driver;
        this.graph = ensureNotNull(graph, "graph");
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
    }

    private List<Content> retrieve0(Query query){
    	String question = query.text();
        String schema = graph.getSchema();
        log.debug(schema);
        String cypherQuery = generateCypherQuery(schema, question);
        List<String> response = List.of();
		try {
			response = executeQuery(cypherQuery);
		} 
		catch (Neo4jException e) {
			log.warn("retrying on error: {}", e.getMessage());
			cypherQuery = chatLanguageModel.generate(
					AiMessage.aiMessage(cypherQuery), 
					UserMessage.from(String.format("This query returns an error: %s \n"
							+ "Give me a improved query that works without any explanations or apologies", e.getMessage()) )).content().text()
			;
			response = executeQuery(cypherQuery);
		}
        return response.stream().map(Content::from).toList();
    }
    @Override
    public List<Content> retrieve(Query query) {
    	 List<Content> resutl = retrieveByIntrospection(query);   
    	 if(CollectionUtils.isEmpty(resutl)) {
    		 log.warn("trying again with dynamic query ..");
    		 resutl = retrieveByDynamicCypher(query);
     	}
    	 return resutl;
    }
    private List<Content> retrieveByDynamicCypher(Query query) {
    	List<Content> graph=List.of();
		try {
			graph = retrieve0(query);
		} catch (Exception e) {
			log.error("cypher execution failed!", e);
		}
		List<TextSegment> texts = graph.stream().map(Content::textSegment).toList();
		log.debug("graph responded {} items: {}", graph.size(), texts);
		return graph;
    }
    private List<Content> retrieveByIntrospection(Query query) {
    	List<String> responses = getEntityRelations(query.text());
    	log.debug("graph responded {} items: {}", responses.size(), responses);
    	return responses.stream().map(s -> Content.from(s)).toList();
    }
    static PromptTemplate NAMED_ENTITY = PromptTemplate.from("Identify named entities from the given text delimited by triple backquotes: ```{{text}}``` \n\n Return entity names only, strictly as JSON array.");
    
    private List<String> getEntityRelations(String question) {
    	String ner = PromptService.sanitize(chatLanguageModel.generate(NAMED_ENTITY.apply(Map.of("text", question)).toUserMessage()).content());
    	log.debug(ner);
    	ArrayNode array = (ArrayNode) KnowledgeGraphModel.toJackson(ner);
    	
    	return StreamSupport.stream(array.spliterator(), false)
    	.map(n -> n.asText())
    	.flatMap(n -> {
    		CompletableFuture<Set<String>> future = alsoApply(
    			    CompletableFuture.supplyAsync(() -> getDirectedRelations(FIND_INRELATIONS_FOR_LABEL, n)),
    			    CompletableFuture.supplyAsync(() -> getDirectedRelations(FIND_OUTRELATIONS_FOR_LABEL, n))
    			.thenApply(b -> a -> {
    				Set<String> set = new HashSet<>();
    				set.addAll(b);
    				set.addAll(a);
    				return set;
    			}));
    		
    		try {
				return future.get().stream();
			} 
    		catch (InterruptedException | ExecutionException e) {
				log.error(e.getMessage());
				log.debug("", e);
				return List.<String>of().stream();
			}
    	}).toList();
    }
    //monadic pattern! don't understand how this works :(
    //https://stackoverflow.com/questions/70724490/completablefuture-is-a-monad-but-where-is-the-applicative
    static <T, R> CompletableFuture<R> alsoApply(CompletableFuture<T> future, CompletableFuture<Function<T, R>> f) {
        return f.thenCompose(future::thenApply);
    }
    private List<String> getDirectedRelations(String query, String name) {

        try (Session session = this.driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(query, Map.of("name", name));
                return result.list().stream().map(r -> r.get(0).asString()
                		.concat(" ")
                		.concat(r.get(1).asString())
                		.concat(" ")
                		.concat(r.get(2).asString()))
                		.toList();
            });
        } 
        catch (ClientException e) {
            throw new Neo4jException("Error executing query: " , e);
        }
    }
    
    private String generateCypherQuery(String schema, String question) {
    	
        String cypherQuery = chatLanguageModel.generate(
        		PromptTemplate.from("You are a Neo4j Cypher query expert. Given below is a Neo4j graph database schema: \n"
        				+ "{{schema}} \n\n"
        				+ "Based on the above schema, your task would be to generate a cypher query that can answer a user question. Think in steps as follows:"
        				+ "1. Identify named entities like name,place,organization,concept from the user question \n"
        				+ "2. Identify relations associated with the entity as mentioned in the question \n"
        				+ "3. Form a cypher query on the entity type and relationship, as present in database schema \n"
        				+ "4. If no specific entity name is identified in the user question, use the entity type to query \n"
        				//+ "5. If no specific relationship name is identified in the user question, get all information for the named entity \n"
        				+ "Give the cypher query only, without any explanation or apologies. Avoid double quotes while using alias in cypher query. \n\n"
        				+ "Below given are examples of valid cypher query:\n"
			        		+ "Question: Find all people who have a relationship with someone named Jane\n"
							+ "Cypher: MATCH (p)-[r]->(j:Person {name:\"Jane\"}) RETURN p \n"
							+ "Question: What is the city with the most airports \nCypher: MATCH (a:Airport)-[:IN_CITY]->(c:City) "
							+ "RETURN c.name AS City, COUNT(a) AS NumberOfAirports "
							+ "ORDER BY NumberOfAirports DESC "
							+ "LIMIT 1 \n"
							+ "Question: Find all friends of a person named John \nCypher: MATCH (p:Person {name:\"John\"})-[:FRIEND_OF]->(f) RETURN f \n "
							+ "Question: Does ISS satellite orbits the Sun \nCypher: MATCH (s:Satellite)-[:ORBITS]->(a:AstronomicalObject) "
							+ "WHERE s.name = \"ISS\" AND a.name = \"Sun\" "
							+ "RETURN s, a \n\n "
				)
        		.apply(Map.of("schema", schema)).toSystemMessage(), 
        		PromptTemplate.from("Generate a Neo4j cypher query for the user question delimited by triple backquotes: ```{{question}}``` ")
        		.apply(Map.of(
        				"question", question
        				
        				)).toUserMessage()
        )
        .content().text();
        
        Matcher matcher = BACKTICKS_PATTERN.matcher(cypherQuery);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return cypherQuery;
    }

    private List<String> executeQuery(String cypherQuery) {
    	log.info("running cypher: {}", cypherQuery);
        List<Record> records = graph.executeRead(cypherQuery);
        return records.stream()
                .flatMap(r -> r.values().stream())
                .map(value -> NODE.isTypeOf(value) ? value.asMap().toString() : value.toString())
                .toList();
    }

}
