package com.rinwave.sakuro.engine.media3

/**
 * Cache keys for network media that survive URL re-signing.
 *
 * Media3 keys the cache by the full URL. Signed CDN links (CloudFront, Akamai, S3/GCS, Azure
 * SAS, tokenized IPTV) put a fresh signature or expiry into the query on every request or
 * session, so the same segment would never hit the cache. Following Reddit's ExoPlayer
 * advice, the key drops only parameters that sign or expire a URL and keeps everything that
 * can select content; the remaining parameters are sorted so their order does not matter.
 */
internal object StreamCacheKey {

    fun of(url: String): String {
        val withoutFragment = url.substringBefore('#')
        val base = withoutFragment.substringBefore('?')
        val query = withoutFragment.substringAfter('?', "")
        if (query.isEmpty()) return base
        val kept = query.split('&')
            .filter { it.isNotEmpty() && !isSigningParameter(it.substringBefore('=')) }
            .sorted()
        return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
    }

    internal fun isSigningParameter(name: String): Boolean {
        val lower = name.lowercase()
        return lower in signingNames || signingPrefixes.any { lower.startsWith(it) }
    }

    private val signingNames = setOf(
        "token", "access_token", "auth", "auth_key", "authorization",
        "sig", "signature", "hmac", "md5", "hash",
        "expires", "expire", "expiry", "exp", "validfrom", "validto",
        // CloudFront signed URLs
        "policy", "key-pair-id",
        // Akamai token auth
        "hdnts", "hdnea", "acl",
        // Google Cloud Storage (legacy signing)
        "googleaccessid",
        // Azure SAS (content-selecting parameters are not part of SAS)
        "se", "st", "sp", "sv", "sr", "spr", "skoid", "sktid", "skt", "ske", "sks", "skv",
    )

    // AWS SigV4 and GCS V4 signatures.
    private val signingPrefixes = listOf("x-amz-", "x-goog-")
}
