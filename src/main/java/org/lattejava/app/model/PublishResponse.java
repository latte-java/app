/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

/**
 * Successful JSON response of the publish endpoint.
 *
 * @param url The presigned PUT URL the client uploads the artifact to.
 */
public record PublishResponse(String url) {
}
