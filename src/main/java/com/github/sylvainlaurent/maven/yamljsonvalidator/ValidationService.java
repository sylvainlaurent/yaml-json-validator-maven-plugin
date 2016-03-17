package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

public class ValidationService {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final JsonSchema schema;

    public ValidationService(final File schemaFile) {
        schema = getJsonSchema(schemaFile);
    }

    public ValidationResult validate(final File file) {
        final ValidationResult validationResult = new ValidationResult();

        JsonNode spec;
        try {
            spec = readFileContent(file);
        } catch (final Exception e) {
            validationResult.addMessage("Error while parsing file " + file + ": " + e.getMessage());
            validationResult.encounteredError();
            return validationResult;
        }

        validateAgainstSchema(spec, validationResult);

        return validationResult;
    }

    private void validateAgainstSchema(final JsonNode spec, final ValidationResult validationResult) {
        if (schema == null) {
            return;
        }
        try {
            final ProcessingReport report = schema.validate(spec);
            if (!report.isSuccess()) {
                validationResult.encounteredError();
            }
            for (final ProcessingMessage processingMessage : report) {
                validationResult.addMessage(processingMessage.toString());
            }
        } catch (final ProcessingException e) {
            validationResult.addMessage(e.getMessage());
            validationResult.encounteredError();
        }
    }

    private JsonSchema getJsonSchema(final File schemaFile) {
        if (schemaFile == null) {
            return null;
        }
        JsonSchema jsonSchema;
        try {
            final JsonNode schemaObject = jsonMapper.readTree(schemaFile);
            final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
            jsonSchema = factory.getJsonSchema(schemaObject);
        } catch (IOException | ProcessingException e) {
            throw new RuntimeException(e);
        }
        return jsonSchema;
    }

    private JsonNode readFileContent(final File file) throws IOException {
        final String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            return yamlMapper.readTree(file);
        } else {
            return jsonMapper.readTree(file);
        }
    }

}
