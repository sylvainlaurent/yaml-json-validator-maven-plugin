package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.SchemaLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ValidationService {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Schema schema;

    private final boolean isEmptyFileAllowed;

    public ValidationService(final InputStream schemaFile,
                             final boolean isEmptyFileAllowed,
                             final boolean detectDuplicateKeys,
                             final boolean allowJsonComments,
                             final boolean allowTrailingComma) {
        schema = getJsonSchema(schemaFile);
        this.isEmptyFileAllowed = isEmptyFileAllowed;
        if (detectDuplicateKeys) {
            this.jsonMapper.enable(Feature.STRICT_DUPLICATE_DETECTION);
            this.yamlMapper.enable(Feature.STRICT_DUPLICATE_DETECTION);
        }
        if (allowJsonComments) {
            this.jsonMapper.enable(Feature.ALLOW_COMMENTS);
        }
        if (allowTrailingComma) {
            this.jsonMapper.enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
            this.yamlMapper.enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
        }
    }

    public ValidationService(final InputStream schemaFile) {
        this(schemaFile, false, true, false, false);
    }

    public ValidationResult validate(final File file) {
        final ValidationResult validationResult = new ValidationResult();

        if (isEmptyFileAllowed && isFileEmpty(file)) {
            return validationResult;
        } else {
            JsonNode spec;
            try {
                spec = readFileContent(file);
                if ((spec == null || spec instanceof MissingNode) && !isEmptyFileAllowed) {
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
            final List<Error> errors = schema.validate(spec);
            if (!errors.isEmpty()) {
                validationResult.encounteredError();
            }
            for (final Error error : errors) {
                validationResult.addMessage(error.toString());
            }
        } catch (final SchemaException e) {
            validationResult.addMessage(e.getMessage());
            validationResult.encounteredError();
        }
    }

    private Schema getJsonSchema(final InputStream schemaFile) {
        if (schemaFile == null) {
            return null;
        }
        try {
            // Preserve Draft 4 as the default and support explicit file/URL references.
            // NetworkNT resolves bundled meta-schemas and classpath references locally.
            final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4,
                    builder -> builder.schemaLoader(SchemaLoader.getRemoteFetcher()));
            final JsonNode schemaObject = jsonMapper.readTree(schemaFile);
            return registry.getSchema(schemaObject);
        } catch (IOException | SchemaException e) {
            throw new RuntimeException(e);
        }
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
