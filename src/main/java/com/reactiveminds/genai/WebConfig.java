package com.reactiveminds.genai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.reactiveminds.genai.core.AbstractEmbeddings;
import com.reactiveminds.genai.core.LanguageModel;
import com.reactiveminds.genai.core.SemanticSplitter;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableWebMvc
@EnableSwagger2
//@Import({ SpringDataRestConfiguration.class, BeanValidatorPluginsConfiguration.class })
public class WebConfig implements WebMvcConfigurer {
	@Bean
	public Docket api() {
		return new Docket(DocumentationType.SWAGGER_2)
				.select()
				.apis(RequestHandlerSelectors.any())
				.paths(PathSelectors.any()).build()
				.apiInfo(metaData());
	}
	private ApiInfo metaData() {
        return new ApiInfoBuilder()
                .title("Langchain4j Playground")
                .version("1.0.0")
                .build();
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
		registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
	}
	@Bean
	SemanticSplitter defaultSemanticSplitter(@Autowired @Qualifier("neo4j") AbstractEmbeddings embeddings, @Autowired LanguageModel languageModel) {
		return new SemanticSplitter(256, embeddings, languageModel);
	}

}
