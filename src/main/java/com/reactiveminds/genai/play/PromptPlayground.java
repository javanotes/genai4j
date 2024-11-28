package com.reactiveminds.genai.play;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.htmlunit.BrowserVersion;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import com.reactiveminds.genai.core.SemanticSplitter;
import com.reactiveminds.genai.core.vec.Neo4jEmbeddings;
import com.reactiveminds.genai.graphrag.GraphCypherUtil;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;

@Component
@Order(100)
public class PromptPlayground implements ApplicationRunner {
	private static Logger log = LoggerFactory.getLogger(PromptPlayground.class);
	@Autowired
	PromptService promptService;
	@Autowired
	SemanticSplitter splitter;
	@Autowired
	Neo4jEmbeddings neo4j;

	private void trySemanticSplitter() throws FileNotFoundException, IOException {
		DocumentParser documentParser = new ApacheTikaDocumentParser();
        Document document;
        try(InputStream in = ResourceUtils.getURL("classpath:doc/knowledge-base.txt").openStream()){
        	document = documentParser.parse(in);
        	List<TextSegment> chunks = splitter.split(document);
        	for (int i = 0; i < chunks.size(); i++) {
				log.info("chunk {} of {} ", i+1, chunks.size());
				log.info(chunks.get(i).text());log.info("^^^\n");
			}
        }
	}
	@Override
	public void run(ApplicationArguments args) throws Exception {
		//trySemanticSplitter();
		//tryGraphQuery();
	}
	private void tryGraphQuery() {
		log.info("starting tryGraphQuery ..");
		List<String> cypher = GraphCypherUtil.tryCypher();
		cypher.stream().forEach(s -> log.info(s));
		neo4j.writeAll(cypher);
		log.info("neo4j: saved knowledge graph. querying => Which users are affected with Internet Explorer?");
		Set<String> answer = neo4j.askGraph("Which users are affected with Internet Explorer?");
		log.info("neo4j: got {} answer", answer.size());
		answer.forEach(c -> log.info(c.toString()));
		log.info("end tryGraphQuery ");
	}
	
	private static void getPageContent(String url) {
		WebClient webClient = new WebClient(BrowserVersion.EDGE);
		webClient.getOptions().setJavaScriptEnabled(true);
		webClient.getOptions().setThrowExceptionOnScriptError(false);
		try {
			/*
			 * driver.get(url);
			 * driver.manage().timeouts().implicitlyWait(Duration.ofMillis(1000)); String
			 * html = driver.getPageSource(); log.info(html);
			 */
			HtmlPage page = webClient.getPage(url);
			webClient.waitForBackgroundJavaScript(30 * 1000);
			
			log.info(page.asNormalizedText());
		} 
		catch (FailingHttpStatusCodeException | IOException e) {
			e.printStackTrace();
		}
		finally {
			webClient.close();
		}
		
        

        
	}

	public String summarize(String getUrl, Map<String, String> headers, int maxTokens, int maxOverlap, String concepts, SummarizationOpts strategy)
			throws IOException {
		log.info("extracting document at: {}, with maxTokes={}, maxOverlap={} building NER on: {}", getUrl, maxTokens,
				maxOverlap, concepts);
		getPageContent(getUrl);
		
		String doc = Jsoup.connect(getUrl).userAgent(
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36")
				.headers(headers).ignoreHttpErrors(true).get().body().text();

		return summarizeContent(doc, maxTokens, maxOverlap, concepts, strategy);
	}

	public String summarizeContent(String content, int maxTokens, int maxOverlap, String concepts, SummarizationOpts strategy) throws IOException {
		log.debug("content: {}", content);
		byte[] docBytes = content.getBytes(StandardCharsets.UTF_8);
		log.info("doc bytes={}", docBytes.length);

		String summary = promptService.summarization(new BufferedInputStream(new ByteArrayInputStream(docBytes)),
				maxTokens, maxOverlap, concepts, strategy);
		return summary; 
	}

	public List<KnowledgeGraphModelV2> knowledgeGraph(String content, int maxTokens, int maxOverlap, String concepts)
			throws IOException {

		List<KnowledgeGraphModelV2> summary = promptService.knowledgeGraph(content, maxTokens, maxOverlap, concepts);
		return summary;
	}

}
