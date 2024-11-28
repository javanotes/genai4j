package com.reactiveminds.genai.play;

import com.fasterxml.jackson.annotation.JsonProperty;

public class KnowledgeGraphModelV2 {

	String head;
	@JsonProperty("head_type")
	String headType;
	String relation;
	String tail;
	@JsonProperty("tail_type")
	String tailType;
	public String getHead() {
		return head;
	}
	public void setHead(String head) {
		this.head = head;
	}
	public String getHeadType() {
		return headType;
	}
	public void setHeadType(String headType) {
		this.headType = headType;
	}
	public String getRelation() {
		return relation;
	}
	public void setRelation(String relation) {
		this.relation = relation;
	}
	public String getTail() {
		return tail;
	}
	public void setTail(String tail) {
		this.tail = tail;
	}
	public String getTailType() {
		return tailType;
	}
	public void setTailType(String tailType) {
		this.tailType = tailType;
	}
	
	
}
