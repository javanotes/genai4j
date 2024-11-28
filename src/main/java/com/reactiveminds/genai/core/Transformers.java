package com.reactiveminds.genai.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.springframework.util.ResourceUtils;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.callback.IV8ModuleResolver;
import com.caoccao.javet.interop.executors.IV8Executor;
import com.caoccao.javet.values.reference.IV8Module;
import com.caoccao.javet.values.reference.V8Module;

public class Transformers {
	
	static {
		List<ScriptEngineFactory> engines = (new ScriptEngineManager()).getEngineFactories();
		for (ScriptEngineFactory f: engines) {
		    System.out.println(f.getLanguageName()+" "+f.getEngineName()+" "+f.getNames().toString());
		}
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
		// read script file
		try {
			//engine.eval(Files.newBufferedReader(ResourceUtils.getFile("classpath:transformers-js-3.0.2/src/transformers.js").toPath()));
			
			
			/*
			 * try (Context context = Context.newBuilder("js") .allowIO(true)
			 * .allowEnvironmentAccess(EnvironmentAccess.INHERIT)
			 * .allowExperimentalOptions(true) .option("js.esm-eval-returns-exports",
			 * "true") .logHandler(System.out) .build()) {
			 * context.eval(Source.newBuilder("js", Files.readString(ResourceUtils.getFile(
			 * "classpath:transformers-js-3.0.2/src/transformers.js").toPath()), "src.mjs")
			 * .mimeType("application/javascript+module") .build());
			 * 
			 * }
			 */
			
			// Step 1: Create a V8 runtime from V8 host in try-with-resource.
			try (V8Runtime v8Runtime = V8Host.getV8Instance().createV8Runtime()) {
				//v8Runtime.com
			    // Step 2: Execute a string as JavaScript code and print the result to console.
				Files.walkFileTree(ResourceUtils.getFile("classpath:transformers-js-3.0.2/src").toPath(), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {					
						
						try {
							V8Module module = v8Runtime.getExecutor(file.toFile())
							.compileV8Module();
							module.evaluate();
						} 
						catch (JavetException e) {
							System.err.println(file);
							e.printStackTrace();
						}
						return FileVisitResult.CONTINUE;
					};
				});
				//V8Host.getNodeInstance().cre
				//IV8Executor executor = v8Runtime.getExecutor(ResourceUtils.getFile("classpath:transformers-3-0-2.js"));
				//executor.executeString();
				//V8Module module = executor.compileV8Module();;
				/*
				v8Runtime.setV8ModuleResolver(new IV8ModuleResolver() {
					
					@Override
					public IV8Module resolve(V8Runtime v8Runtime, String resourceName, IV8Module v8ModuleReferrer)
							throws JavetException {
						System.out.println("callign resolve for resource: "+resourceName);
						return module;
					}
				});
				*/
				
				//module.asString();
				Object result = v8Runtime.getExecutor("softmax1(0.1,0.22,-0.3)")
				.setModule(true)
				.setResourceName(ResourceUtils.getFile("classpath:transformers-3-0-2.js").getPath())
				.executeObject();
				
			    System.out.println(result); // Hello Javet
			    // Step 3: Resource is recycled automatically at the end of the try-with-resource block.
			}
			 
		} 
		catch (Exception e) {
			//e.printStackTrace();
			throw new RuntimeException("unable to load javascript library! ", e);
		}

	}
	
	public static void main(String[] args) {
		new Transformers();
	}

}
