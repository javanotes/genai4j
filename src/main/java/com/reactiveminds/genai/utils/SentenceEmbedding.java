package com.reactiveminds.genai.utils;

import java.util.Objects;

import dev.langchain4j.data.embedding.Embedding;

class SentenceEmbedding{
	public String getSentence() {
		return sentence;
	}
	static SentenceEmbedding of(String text) {
		return new SentenceEmbedding(text, null);
	}
	@Override
	public String toString() {
		return sentence;
	}
	final String sentence;
	@Override
	public int hashCode() {
		return Objects.hash(sentence);
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SentenceEmbedding other = (SentenceEmbedding) obj;
		return Objects.equals(sentence, other.sentence);
	}
	SentenceEmbedding(String sentence, Embedding embedding) {
		super();
		this.sentence = sentence;
		this.embedding = embedding;
	}
	final Embedding embedding;
}