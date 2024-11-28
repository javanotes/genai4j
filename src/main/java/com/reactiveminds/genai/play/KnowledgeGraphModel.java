package com.reactiveminds.genai.play;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class KnowledgeGraphModel {
	public static class Relationship{
		String source;				
		String target;
		String type;
		public String getSource() {
			return source;
		}
		public void setSource(String source) {
			this.source = source;
		}
		public String getTarget() {
			return target;
		}
		public void setTarget(String target) {
			this.target = target;
		}
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
	}
	public static class Node{
		String id;
		String label;
		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}
		public String getLabel() {
			return label;
		}
		public void setLabel(String label) {
			this.label = label;
		}
		public Map<String, Object> getProperties() {
			return properties;
		}
		public void setProperties(Map<String, Object> properties) {
			this.properties = properties;
		}
		Map<String, Object> properties;
	}
	List<Node> nodes;
	List<Relationship> relationships;
	public List<Node> getNodes() {
		return nodes;
	}
	public void setNodes(List<Node> nodes) {
		this.nodes = nodes;
	}
	public List<Relationship> getRelationships() {
		return relationships;
	}
	public void setRelationships(List<Relationship> relationships) {
		this.relationships = relationships;
	}

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			;
	
	public static ObjectMapper getMapper() {
		return MAPPER;
	}
	public static KnowledgeGraphModel toModel(String json) {
		try {
			return MAPPER.readerFor(KnowledgeGraphModel.class).readValue(json);
		} 
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}
	public static List<Relationship> toRelations(String json) {
		try {
			return MAPPER.convertValue(MAPPER.readTree(json), new TypeReference<>() {
			});
		} 
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}
	public static <T>T toTypedObject(String json, TypeReference<T> typeRef) {
		try {
			return MAPPER.convertValue(MAPPER.readTree(json), typeRef);
		} 
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}
	public static <T>T toObject(String json, Class<T> typeRef) {
		try {
			return MAPPER.convertValue(MAPPER.readTree(json), typeRef);
		} 
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}
	public static JsonNode toJackson(String json) {
		try {
			return MAPPER.readTree(json);
		} 
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}
	public static<T> JsonNode objectToJackson(T json) {
		return MAPPER.convertValue(json, JsonNode.class);
	}
	public static <T> String toJson(T json) {
		try {
			return MAPPER.writeValueAsString(json);
		} 
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}
	public static <T> String toPrettyJson(T json) {
		try {
			return MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(json);
		} 
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}
	public static String joinJsonArray(String json) {
		List<String> lines;
		try {
			lines = MAPPER.convertValue(MAPPER.readTree(json), new TypeReference<List<String>>() {});
		} 
		catch (JsonProcessingException | IllegalArgumentException e) {
			throw new RuntimeException(e);
		}
		return lines.stream().collect(Collectors.joining());
	}
}
