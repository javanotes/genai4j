package com.reactiveminds.genai.graphrag;

import java.time.LocalDate;
/**
 * retrieval augmentation document
 */
public class ADocument {
	
	public ADocument(String title, String text, LocalDate date) {
		super();
		this.title = title;
		this.text = text;
		this.date = date;
	}
	private String title;
	private String text;
	private LocalDate date;
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public LocalDate getDate() {
		return date;
	}
	public void setDate(LocalDate date) {
		this.date = date;
	}
	
}
