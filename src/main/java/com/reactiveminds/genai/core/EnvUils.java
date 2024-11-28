package com.reactiveminds.genai.core;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvUils {
	
	public static final String OPENAI_API_KEY = System.getProperty("OPENAI_API_KEY", "demo");
			//"");
	
	public static final String GITHUB_TOKEN = System.getProperty("GITHUB_TOKEN", 
			"");
	
	public static final String OPENAI_ORG_ID = "";
	
	public static final String HF_API_KEY = System.getProperty("HF_API_KEY", 
			"");
	
	public static final String SEARCHAPI_API_KEY = System.getProperty("SEARCHAPI_API_KEY", 
			"");//;
	

    public static void startConversationWith(Assistant assistant) {
        Logger log = LoggerFactory.getLogger(Assistant.class);
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                log.info("==================================================");
                log.info("User: ");
                String userQuery = scanner.nextLine();
                log.info("==================================================");

                if ("exit".equalsIgnoreCase(userQuery)) {
                    break;
                }

                String agentAnswer = assistant.answer(userQuery);
                log.info("==================================================");
                log.info("Assistant: " + agentAnswer);
            }
        }
    }

    public static PathMatcher glob(String glob) {
        return FileSystems.getDefault().getPathMatcher("glob:" + glob);
    }

    public static Path toPath(String relativePath) {
        try {
            URL fileUrl = EnvUils.class.getClassLoader().getResource(relativePath);
            return Paths.get(fileUrl.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
