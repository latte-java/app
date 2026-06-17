/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.error;

import module java.base;
import module org.lattejava.json;

import org.lattejava.app.error.internal.ErrorsJSON;

/**
 * Standard error collection. Holds field-keyed errors and general errors, suitable for surfacing back to a form view or
 * as an API response payload.
 */
@JSON
public class Errors {
  public final Map<String, List<Error>> fieldErrors;
  public final List<Error> generalErrors;

  public Errors() {
    this(new LinkedList<>(), new HashMap<>());
  }

  @JSONConstructor
  public Errors(List<Error> generalErrors,Map<String, List<Error>> fieldErrors) {
    this.generalErrors = generalErrors;
    this.fieldErrors = fieldErrors;
  }

  public static Errors fromJSON(String json) {
    return ErrorsJSON.fromJSON(json);
  }

  public Errors add(Errors otherErrors) {
    if (otherErrors != null) {
      fieldErrors.putAll(otherErrors.fieldErrors);
      generalErrors.addAll(otherErrors.generalErrors);
    }
    return this;
  }

  public Errors addFieldError(String field, String code, String message, Object... values) {
    String formatted = values.length == 0 ? message : String.format(message, values);
    fieldErrors.computeIfAbsent(field, _ -> new LinkedList<>()).add(new Error(code, formatted));
    return this;
  }

  public Errors addGeneralError(String code, String message, Object... values) {
    String formatted = values.length == 0 ? message : String.format(message, values);
    generalErrors.add(new Error(code, formatted));
    return this;
  }

  public boolean containsError(String codePrefix) {
    for (Error error : generalErrors) {
      if (error.code().startsWith(codePrefix)) {
        return true;
      }
    }
    for (List<Error> errors : fieldErrors.values()) {
      for (Error error : errors) {
        if (error.code().startsWith(codePrefix)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean empty() {
    return generalErrors.isEmpty() && fieldErrors.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Errors errors = (Errors) o;
    return fieldErrors.equals(errors.fieldErrors) && generalErrors.equals(errors.generalErrors);
  }

  public Error getFieldError(String field, String code) {
    List<Error> errors = fieldErrors.get(field);
    if (errors == null) {
      return null;
    }
    for (Error fieldError : errors) {
      if (fieldError.code().equals(code)) {
        return fieldError;
      }
    }
    return null;
  }

  @Override
  public int hashCode() {
    int result = generalErrors.hashCode();
    result = 31 * result + fieldErrors.hashCode();
    return result;
  }

  public int size() {
    return generalErrors.size() + fieldErrors.values().stream().mapToInt(List::size).sum();
  }

  public String toJSON() {
    return ErrorsJSON.toJSON(this);
  }

  @Override
  public String toString() {
    return "Errors[fieldErrors=[" + fieldErrors + "], generalErrors=[" + generalErrors + "]]";
  }
}
