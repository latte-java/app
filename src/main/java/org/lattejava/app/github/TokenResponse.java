/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

import module org.lattejava.json;

/**
 * The JSON body GitHub returns from {@code POST https://github.com/login/oauth/access_token} when the request carries
 * {@code Accept: application/json}. GitHub answers HTTP 200 for both outcomes: a successful exchange carries
 * {@code access_token} (plus {@code token_type} and {@code scope}, which we ignore); a failed one carries
 * {@code error} (plus {@code error_description} and {@code error_uri}, which we ignore) and no {@code access_token}.
 *
 * @param accessToken The OAuth access token, or {@code null} when the exchange failed.
 * @param error The error code (for example {@code bad_verification_code}), or {@code null} when the exchange succeeded.
 */
@JSON(naming = NamingStrategy.SNAKE_CASE)
public record TokenResponse(String accessToken, String error) {
}
