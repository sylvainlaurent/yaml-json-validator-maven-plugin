package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

public class ValidationService {

	private ObjectMapper jsonMapper = new ObjectMapper();
	private ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

	public ValidationResult validate(File file) {
		ValidationResult validationResult = new ValidationResult();

		JsonNode spec;
		try {
			spec = readFileContent(file);
		} catch (Exception e) {
			validationResult.addMessage("Error while parsing file " + file + ": " + e.getMessage());
			validationResult.encounteredError();
			return validationResult;
		}

		//TODO json schema
		// JsonSchema schema = getJsonSchema();
		// validateAgainstSchema(spec, schema, validationResult);

		return validationResult;
	}

	private void validateAgainstSchema(JsonNode spec, JsonSchema schema, ValidationResult validationResult) {

		try {
			ProcessingReport report = schema.validate(spec);
			if (!report.isSuccess()) {
				validationResult.encounteredError();
			}
			for (ProcessingMessage processingMessage : report) {
				validationResult.addMessage(processingMessage.toString());
			}
		} catch (ProcessingException e) {
			validationResult.addMessage(e.getMessage());
			validationResult.encounteredError();
		}
	}

	private JsonSchema getJsonSchema() {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream("TODO");
		JsonSchema schema;
		try {
			JsonNode schemaObject = jsonMapper.readTree(is);
			JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
			schema = factory.getJsonSchema(schemaObject);
		} catch (IOException | ProcessingException e) {
			throw new RuntimeException(e);
		}
		return schema;
	}

	private JsonNode readFileContent(File file) throws IOException {
		String fileName = file.getName().toLowerCase();
		if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
			return yamlMapper.readTree(file);
		} else {
			return jsonMapper.readTree(file);
		}
	}

}
