package com.reactiveminds.genai.core.vec;

public class DocumentVector{
	String title; 
	String date; 
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public String getDoc() {
		return doc;
	}
	public void setDoc(String doc) {
		this.doc = doc;
	}
	public float[] getVector() {
		return vector;
	}
	public void setVector(float[] vector) {
		this.vector = vector;
	}
	String doc;
	float[] vector;
}