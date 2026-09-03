package com.romanopetroli.rpfidelity.data.model

import org.json.JSONObject

/**
 * org.json's optString() returns the literal string "null" for a JSON null value
 * instead of an actual null, so a plain .ifBlank { null } never catches it.
 */
fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }
