/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module org.lattejava.json;

import org.lattejava.app.model.internal.*;

/**
 * Inbound JSON body of {@code POST /api/v1/publish/{groupName}}. The group name is carried in the URL path, not here.
 *
 * @param fileName The complete R2 object key the client intends to upload to.
 */
@JSON
public record PublishRequest(String fileName) {
  public static PublishRequest fromJSON(byte[] json) {
    return PublishRequestJSON.fromJSON(json);
  }

  public String toJSON() {
    return PublishRequestJSON.toJSON(this);
  }
}
