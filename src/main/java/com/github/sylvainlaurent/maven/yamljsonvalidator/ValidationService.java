package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.Dereferencing;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import java.io.File;
import java.io.IOException;

public class ValidationService {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final JsonSchema schema;

    private final boolean isEmptyFileAllowed;

    public ValidationService(final File schemaFile,
                             final boolean isEmptyFileAllowed,
                             final boolean detectDuplicateKeys,
                             final boolean allowJsonComments) {
        schema = getJsonSchema(schemaFile);
        this.isEmptyFileAllowed = isEmptyFileAllowed;
        if (detectDuplicateKeys) {
            this.jsonMapper.enable(Feature.STRICT_DUPLICATE_DETECTION);
            this.yamlMapper.enable(Feature.STRICT_DUPLICATE_DETECTION);
        }
        if (allowJsonComments) {
            this.jsonMapper.enable(Feature.ALLOW_COMMENTS);
        }
    }

    public ValidationService(final File schemaFile) {
        this(schemaFile, false, true, false);
    }

    public ValidationResult validate(final File file) {
        final ValidationResult validationResult = new ValidationResult();

        if (isEmptyFileAllowed && isFileEmpty(file)) {
            return validationResult;
        } else {
            JsonNode spec;
            try {
                spec = readFileContent(file);
                if (spec == null && !isEmptyFileAllowed) {
                    validationResult.addMessage("Empty file is not valid: " + file);
                    validationResult.encounteredError();
                    return validationResult;
                }
            } catch (final Exception e) {
                validationResult.addMessage("Error while parsing file " + file + ": " + e.getMessage());
                validationResult.encounteredError();
                return validationResult;
            }

            validateAgainstSchema(spec, validationResult);
        }
        return validationResult;
    }

    private boolean isFileEmpty(final File file) {
        return file.length() == 0L;
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
            // using INLINE dereferencing to avoid internet access while validating
            final LoadingConfiguration loadingConfiguration = LoadingConfiguration.newBuilder()
                    .dereferencing(Dereferencing.INLINE).freeze();
            final JsonSchemaFactory factory = JsonSchemaFactory.newBuilder()
                    .setLoadingConfiguration(loadingConfiguration).freeze();

            final JsonNode schemaObject = jsonMapper.readTree(schemaFile);
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
