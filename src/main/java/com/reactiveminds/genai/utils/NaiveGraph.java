package com.reactiveminds.genai.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class NaiveGraph {
	public static class GraphElement {
	    public final String label;
	    public final Map<String, Object> properties = new HashMap<>();

	    public GraphElement(String label) {
	        this.label = label;
	    }
	}

	// A Node is a GraphElement with incoming and outgoing edges
	public static class Node extends GraphElement {
	    public final String id;
	    public final List<Edge> outgoingEdges = new ArrayList<>();
	    public final List<Edge> incomingEdges = new ArrayList<>();

	    public Node(String label, String id) {
	        super(label);
	        this.id = id;
	    }
	    
	    void addOutgoingEdge(final Edge r) {
	        outgoingEdges.add(r);
	    }

	    void addIncomingEdge(final Edge r) {
	        incomingEdges.add(r);
	    }

	    Stream<Edge> outgoingEdges() {
	        return outgoingEdges.stream();
	    }

	    Stream<Edge> outgoingEdges(final String edgeLabel) {
	        return outgoingEdges.stream().filter(e -> e.label.equals(edgeLabel));
	    }

	    Stream<Edge> incomingEdges() {
	        return incomingEdges.stream();
	    }

	    Stream<Edge> incomingEdges(final String edgeLabel) {
	        return incomingEdges.stream().filter(e -> e.label.equals(edgeLabel));
	    }
	}

	// An Edge is a GraphElement with a source and a target node
	public static class Edge extends GraphElement {
	    public final Node source;
	    public final Node target;

	    public Edge(String label, Node source, Node target) {
	        super(label);
	        this.source = source;
	        this.target = target;
	    }
	}
	
	public static class Graph {

	    private final Map<String, Node> nodeIdToNode = new HashMap<>();

	    private final Map<String, Set<Node>> nodeLabelToNodes = new HashMap<>();

	    public Node addNode(
	            final String id,
	            final String label) {

	        if (nodeIdToNode.containsKey(id)) {
	            throw new IllegalArgumentException("duplicate id: ".concat(id));
	        }

	        final Node n = new Node(id, label);
	        nodeIdToNode.put(id, n);
	        nodeLabelToNodes.computeIfAbsent(label, k -> new HashSet<>()).add(n);
	        return n;
	    }

	    public Node addNodeIfAbsent(
	            final String id,
	            final String label) {
	        Node n = nodeIdToNode.get(id);

	        if (n == null) {
	            n = new Node(id, label);
	            nodeIdToNode.put(id, n);
	            nodeLabelToNodes.computeIfAbsent(label, k -> new HashSet<>()).add(n);
	        }

	        return n;
	    }

	    public Edge addEdge(
	            final String label,
	            final String fromNodeId,
	            final String toNodeId) {

	        final Node fromNode = getNodeOrThrow(fromNodeId);
	        final Node toNode = getNodeOrThrow(toNodeId);

	        final Edge e = new Edge(label, fromNode, toNode);
	        fromNode.addOutgoingEdge(e);
	        toNode.addIncomingEdge(e);

	        return e;
	    }

	    public Node getNode(final String id) {
	        return nodeIdToNode.get(id);
	    }

	    public Optional<Node> getOptionalNode(final String id) {
	        return Optional.ofNullable(nodeIdToNode.get(id));
	    }

	    public Node getNodeOrThrow(final String id) {
	        final Node node = nodeIdToNode.get(id);
	        if (node == null) {
	            throw new IllegalArgumentException("node not found: ".concat(id));
	        }
	        return node;
	    }

	    public Set<Node> getNodesByLabel(final String label) {
	        return nodeLabelToNodes.get(label);
	    }

	}

}
