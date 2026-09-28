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

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Validates files against a schema owned by the execution's resolver. */
final class ValidationService {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Schema schema;
    private final boolean isEmptyFileAllowed;

    ValidationService(final Schema schema,
                      final boolean isEmptyFileAllowed,
                      final boolean detectDuplicateKeys,
                      final boolean allowJsonComments,
                      final boolean allowTrailingComma) {
        this.schema = schema;
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

    ValidationResult validate(final File file) {
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

    private JsonNode readFileContent(final File file) throws IOException {
        final String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            return yamlMapper.readTree(file);
        } else {
            return jsonMapper.readTree(file);
        }
    }

}
