package com.reactiveminds.genai.graphrag;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.JaccardDistance;
import org.springframework.util.ResourceUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.EnumNamingStrategies.CamelCaseStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.reactiveminds.genai.play.KnowledgeGraphModel;
import com.reactiveminds.genai.play.KnowledgeGraphModelV2;
import com.reactiveminds.genai.utils.RelevanceMap;
import com.reactiveminds.genai.utils.TextSimilarityFunction;
/**
 * An opinionated utility to generate cypher queries given a knowledge graph in specific format. Uses a best effort case of
 * named entity linking. by default expects a JSON array in the format:
 * <pre>
[
	{
		"head": "SAML2 configuration",
		"head_type": "Concept",
		"relation": "UNABLE_TO_CREATE_METADATA_FOR",
		"tail": "IE8",
		"tail_type": "Browser"
	},
	{
		"head": "IE8",
		"head_type": "Browser",
		"relation": "AFFECTS",
		"tail": "Admin users and below",
		"tail_type": "UserRole"
	}
]
 * </pre>
 */
public class GraphCypherUtil {
	
	static final String DEFAULT_CYPHER_CREATE = "MERGE (head:#{head_type} {name: \"#{head}\"}) -[:#{relation}]-> (tail:#{tail_type} {name: \"#{tail}\"})";
	public static final String OPEN = "#{";
	public static final String CLOSE = "}";
	/**
	 * 
	 * @param json
	 * @return
	 */
	public static List<String> createRelationships(final JsonNode json){
		return createRelationships(json, DEFAULT_CYPHER_CREATE);
	}
	/**
	 * create cypher queries with specific model and having entity linking with similarity distance.
	 * @param entities
	 * @param similarityFunc 
	 * @return
	 */
	public static List<String> createRelationships(List<KnowledgeGraphModelV2> entities, TextSimilarityFunction similarityFunc){
		LinkedList<String> result = new LinkedList<>();
		List<KnowledgeGraphModelV2> applied = applyNELinkings(entities, similarityFunc);
		List<Map<String, Object>> linkedNames = KnowledgeGraphModel.getMapper().convertValue(applied, new TypeReference<>() {});
		createRelationships0(linkedNames, result, DEFAULT_CYPHER_CREATE);
		
		return result;
	}
	/**
	 * create relationships
	 * @param json
	 * @param mergeTemplate
	 * @return
	 */
	public static List<String> createRelationships(final JsonNode json, final String mergeTemplate){
		LinkedList<String> result = new LinkedList<>();
		if(json.isArray()) {
			List<Map<String, Object>> linkedNames = linkEntityNames(json);
			createRelationships0(linkedNames, result, mergeTemplate);									
		}
		
		return result;		
	}
	private static void createRelationships0(final List<Map<String, Object>> linkedNames, final LinkedList<String> result, String mergeTemplate) {
		for (int i = 0; i < linkedNames.size(); i++) {
			Map<String, Object> n = linkedNames.get(i);
			String replaced = mergeTemplate;
			String[] replaceMe = StringUtils.substringsBetween(replaced, OPEN, CLOSE);
			for (int j = 0; j < replaceMe.length; j++) {
				String token = replaceMe[j];
				if(n.containsKey(token)) {
					String label = n.get(token).toString();
					if(token.equals("head_type") || token.equals("tail_type")) {
						label = toCameCase(label);
					}
					replaced = replaced.replace(OPEN.concat(token).concat(CLOSE), label);						
				}
			}
			result.add(replaced);
		}
	}
	private static String toCameCase(String input) {
		String underscrd = input.strip().trim(); //symbol '\u0000' is not deleted by strip, but deleted by trim
		if(!StringUtils.containsWhitespace(underscrd)) {
			return underscrd;
		}
		underscrd = StringUtils.replaceChars(underscrd, ' ', '_'); 
		underscrd = CamelCaseStrategy.INSTANCE.convertEnumToExternalName(underscrd);
		return String.valueOf(underscrd.charAt(0)).toUpperCase().concat(underscrd.substring(1));
	}
	public static void main(String[] args) {
		System.out.println(toCameCase("News Outlet"));
	}
	/**
	 * resolve named entity linking
	 * @param entities
	 * @return 
	 */
	public static List<KnowledgeGraphModelV2> applyNELinkings(List<KnowledgeGraphModelV2> entities, TextSimilarityFunction similarityFunc) {
		Map<String, List<KnowledgeGraphModelV2>> byHeadType = entities.stream().collect(Collectors.groupingBy(k -> Optional.ofNullable(k.getHeadType()).orElse("-")));
		Map<String, List<KnowledgeGraphModelV2>> byTailType = entities.stream().collect(Collectors.groupingBy(k -> Optional.ofNullable(k.getTailType()).orElse("-")));
		
		Map<String, List<KnowledgeGraphModelV2>> mappedByType = byHeadType.entrySet().stream()
		.map(e -> {
			List<KnowledgeGraphModelV2> merged = new ArrayList<>(e.getValue());
			List<KnowledgeGraphModelV2> sametyp = byTailType.remove(e.getKey());
			if(sametyp != null) {
				merged.addAll(sametyp);
			}
			return new SimpleEntry<>(e.getKey(), merged);
		}).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
		
		mappedByType.putAll(byTailType); // all entities by their type
		
		return mappedByType.entrySet().parallelStream()
		.flatMap(e -> {
			System.out.println("processing type: ".concat(e.getKey()));
			Set<String> entityNames = e.getValue().stream().flatMap(model -> List.of(model.getHead(), model.getTail()).stream()).collect(Collectors.toSet());
			
			List<KnowledgeGraphModelV2> modified = new ArrayList<>(e.getValue());
			RelevanceMap relevanceMap = new RelevanceMap();
			relevanceMap.setRelevanceScore(0.74);
			relevanceMap.setSimilarityFunction(similarityFunc);
			entityNames.forEach(name -> relevanceMap.addSentence(name));
			List<Pair<String, Set<String>>> links = relevanceMap.namedEntityLinking(4, () -> new JaccardDistance());
			
			for (Iterator<Pair<String, Set<String>>> iterator = links.iterator(); iterator.hasNext();) {
				Pair<String, Set<String>> pair = iterator.next();
				String link = pair.getLeft();
				modified.forEach(model -> {
					if(pair.getRight().contains(model.getHead())) {
						model.setHead(link);
					}
					if(pair.getRight().contains(model.getTail())) {
						model.setTail(link);
					}
				});
			}
			
			return modified.stream();
		}).toList();
	}
	private static List<Map<String,Object>> linkEntityNames(JsonNode json) {
		RelevanceMap relevanceMap = new RelevanceMap();
		relevanceMap.setRelevanceScore(0.74);
		relevanceMap.setSimilarityFunction(TextSimilarityFunction.COSINE);
		
		List<Map<String,Object>> linkedNodes = new ArrayList<>(json.size());
		for (int i = 0; i < json.size(); i++) {
			JsonNode n = json.get(i);
			if(n.has("head")) {
				relevanceMap.addSentence((n.get("head").textValue()));
			}
			if(n.has("tail")) {
				relevanceMap.addSentence((n.get("tail").textValue()));
			}
			linkedNodes.add(KnowledgeGraphModel.getMapper().convertValue(n, new TypeReference<>() {}));
		}
		List<Pair<String, Set<String>>> links = relevanceMap.namedEntityLinking(4, () -> new JaccardDistance());
		
		for (Iterator<Pair<String, Set<String>>> iterator = links.iterator(); iterator.hasNext();) {
			Pair<String, Set<String>> pair = iterator.next();
			String link = pair.getLeft();
			pair.getRight();
			linkedNodes = linkedNodes
			.stream()
			.map(map -> map.entrySet().stream().map(entry -> pair.getValue().contains(entry.getValue()) ? new SimpleEntry<String, Object>(entry.getKey(), link) : entry )
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())))
			.collect(Collectors.toList());
			;
		}
		return linkedNodes;
	}
	
	public static List<String> tryCypher() {
		File f;
		try {
			f = ResourceUtils.getFile("classpath:doc/llm-graph.json");
			JsonNode graph = KnowledgeGraphModel.getMapper().readTree(f);
			return createRelationships(graph);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
	}
	
}
