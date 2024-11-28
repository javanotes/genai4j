package com.reactiveminds.genai.play;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.reactiveminds.genai.core.RetrievalAugmentationService;
import com.reactiveminds.genai.graphrag.ADocument;

@Component
public class NewsArticleIngestor implements ApplicationRunner{
	private static Logger log = LoggerFactory.getLogger(NewsArticleIngestor.class);
	
	@Autowired
	RetrievalAugmentationService ragService;
	
	public void pipeline() throws IOException {
		JsonNode articles = KnowledgeGraphModel.getMapper().readTree( ResourceUtils.getFile("classpath:doc/news-articles.json"));
		log.info("processing {} articles ", articles.size());
		Random r = new Random();
		AtomicInteger i = new AtomicInteger();
		for (; i.get() < 2; i.getAndIncrement()) {
			JsonNode article = articles.get(r.nextInt(articles.size()));
			String title = article.get("title").asText();
			String text = article.get("text").asText();
			log.info("{}. graph for : {}", i, title);
			
			ragService.ingestDocument(new ADocument(title, text, LocalDate.now()));
		}
		
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.info("* --------------------------------------- *");
		log.info("* Testing information extraction pipeline *");
		log.info("* --------------------------------------- *");
		//pipeline();
		
		//Set<String> answer = neo4j.askGraph("Who are the members of United Nations?");
		//log.info("Who are the members of United Nations? answer : {}", answer);
		log.info("** --------- complete ----------- **");
	}

}
