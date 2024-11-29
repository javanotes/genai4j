## Bringing generative AI based NLP to the Java ecosystem
Yes, you heard that right! Thanks to the advent of LLM inference engines, it is now possible to include NLP based workflows directly into your Java applications. While, libraries like deeplearning4j,djl do exist for developing 
machine learning models, Python libraries like numpy/Tensorflow/Pytorch are the state of the art, really. 

However, my learnings are really on the model usage space, and how can robust enterprise Java ecosystems can directly benefit from the advent of generative AI. 
And without having to go polyglot or rely on counter ecosystem workflows.
#### Tools Used
- Java/Spring Boot
- `langchain4j` library (a limited version of `langchain` for Python)
- `Ollama` container for running LLM inferencing locally
- `Neo4j` v5.23.0 with `apoc` plugin, as a graph database (and embedding store)
- `Elasticsearch` v8.15.2 as embedding store (and document indexing)
- Docker for running neo4j and elastic containers
- Couple of tools used transitively, worth mentioning:
  - `deep java library (DJL)` for using Huggingface tokenizers. DJL is the java deep learning library used in Amazon Sagemaker
  - `onnx runtime` for running Open Neural Net eXchange formatted Huggingface embedding models
