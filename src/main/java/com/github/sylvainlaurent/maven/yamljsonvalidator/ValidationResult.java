package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
	private final Throwable exc;
	private final List<String> messages = new ArrayList<>();
	private boolean hasError;

	public ValidationResult() {
		exc = null;
	}

	public ValidationResult(Throwable exc) {
		this.exc = exc;
	}

	public static ValidationResult fromException(Throwable exc) {
		return new ValidationResult(exc);
	}

	public boolean hasError() {
		return hasError || exc != null;
	}

	public void encounteredError() {
		hasError = true;
	}

	public Throwable getExc() {
		return exc;
	}

	public void addMessage(String msg) {
		this.messages.add(msg);
	}

	public void addMessages(List<String> msgs) {
		this.messages.addAll(msgs);
	}

	public List<String> getMessages() {
		return messages;
	}

}
