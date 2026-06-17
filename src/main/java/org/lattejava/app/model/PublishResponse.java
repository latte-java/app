/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module org.lattejava.json;

import org.lattejava.app.model.internal.*;

/**
 * Successful JSON response of the publish endpoint.
 *
 * @param url The presigned PUT URL the client uploads the artifact to.
 */
@JSON
public record PublishResponse(String url) {
  public String toJSON() {
    return PublishResponseJSON.toJSON(this);
  }
}
