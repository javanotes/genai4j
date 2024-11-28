package com.reactiveminds.genai.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.StringEncoder;
import org.apache.commons.codec.language.RefinedSoundex;
import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.WordUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.reactiveminds.genai.core.EnvUils;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;

public class HtmlExtractor {
	
	public static void googleSearch(String query, int maxResults) {
		Map<String, Object> optionalParameters = new HashMap<>();
        optionalParameters.put("gl", "us");
        optionalParameters.put("hl", "en");
        optionalParameters.put("google_domain", "google.com");
        
        WebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(EnvUils.SEARCHAPI_API_KEY)
                .engine("google")
                .optionalParameters(optionalParameters)
                .build();
        
        WebSearchResults results = searchEngine.search(WebSearchRequest.builder()
        		.safeSearch(true)
        		.maxResults(maxResults)
        		.searchTerms(query)
        		.additionalParams(optionalParameters)
        		.build());
        
        List<WebSearchOrganicResult> allItems = results.results();
        System.out.println(results);
        
        //insufficient privileges to complete the operation
        //https://support.servicenow.com/kb?id=kb_article_view&sysparm_article=KB0753146
	}
	
	public static void extractText(String getUrl, Map<String, String> headers) {
		try {
			Document doc = Jsoup.connect(getUrl).userAgent(
					"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36")
					.headers(headers).ignoreHttpErrors(true).get();
			//Element body = doc.body();
			doc.select("script").forEach(Element::remove);
			doc.select("style").forEach(Element::remove);
			doc.select("img").forEach(Element::remove);
			doc.select("input").forEach(Element::remove);
			
			System.out.println(WordUtils.wrap(doc.body().text(), 80));
		} 
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	public static void main(String[] args) throws EncoderException {
		//extractText("http://www.fipa.org/specs/fipa00061/SC00061G.html", Map.of());
		//https://searchengineland.com/search-engine-traffic-2026-prediction-437650
		//extractText("https://searchengineland.com/search-engine-traffic-2026-prediction-437650", Map.of());
		
		StringEncoder soundex = new Soundex();
		System.out.println(soundex.encode("Mel Gibson")); 
		System.out.println(soundex.encode("Mel Gybson")); 
		 
		
		//googleSearch("insufficient privileges to complete the operation", 10);
	}

}
