
package com.github.sylvainlaurent.maven.yamljsonvalidator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;

import com.github.sylvainlaurent.maven.yamljsonvalidator.ValidationResult;
import com.github.sylvainlaurent.maven.yamljsonvalidator.ValidationService;

public class ValidationServiceTest {
	private ValidationService service = new ValidationService();

	@Test
	public void test_empty_file_yml() {
		ValidationResult result = service.validate(new File("src/test/resources/empty.yml"));
		assertTrue(result.hasError());
		assertEquals(1, result.getMessages().size());
	}

	@Test
	public void test_empty_file_json() {
		ValidationResult result = service.validate(new File("src/test/resources/empty.json"));
		assertTrue(result.hasError());
		assertEquals(1, result.getMessages().size());
	}

	@Test
	public void test_malformed_file_yml() {
		ValidationResult result = service.validate(new File("src/test/resources/malformed.yml"));
		assertTrue(result.hasError());
		assertEquals(1, result.getMessages().size());
	}

	@Test
	public void test_malformed_file_json() {
		ValidationResult result = service.validate(new File("src/test/resources/malformed.json"));
		assertTrue(result.hasError());
		assertEquals(1, result.getMessages().size());
	}

	@Test
	@Ignore
	public void test_not_valid_swagger_yml() {
		ValidationResult result = service.validate(new File("src/test/resources/not-valid.yml"));
		assertTrue(result.hasError());
		assertEquals(1, result.getMessages().size());
	}

	@Test
	@Ignore
	public void test_not_valid_swagger_json() {
		ValidationResult result = service.validate(new File("src/test/resources/not-valid.json"));
		assertTrue(result.hasError());
		assertEquals(1, result.getMessages().size());
	}

	@Test
	public void test_valid_yml() {
		ValidationResult result = service.validate(new File("src/test/resources/swagger-editor-example.yml"));
		assertFalse(result.hasError());
		assertTrue(result.getMessages().isEmpty());
	}

	@Test
	public void test_valid_json() {
		ValidationResult result = service.validate(new File("src/test/resources/swagger-editor-example.json"));
		assertFalse(result.hasError());
		assertTrue(result.getMessages().isEmpty());
	}
}
