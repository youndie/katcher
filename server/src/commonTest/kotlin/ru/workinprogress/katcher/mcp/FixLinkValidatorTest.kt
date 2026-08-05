package ru.workinprogress.katcher.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The fix URL is the only value an agent can write into Katcher, and it renders as a link
 * in the dashboard. These cases are the ways a compromised agent could turn that into
 * something worse than a wrong link.
 */
class FixLinkValidatorTest {
    private fun validate(url: String) = FixLinkValidator.validate(url)

    @Test
    fun `accepts a normal pull request url`() {
        val valid = assertIs<FixLinkResult.Valid>(validate("https://github.com/acme/app/pull/42"))
        assertEquals("https://github.com/acme/app/pull/42", valid.url)
    }

    @Test
    fun `accepts a self-hosted forge on a port`() {
        // Katcher holds no repo config, so self-hosted GitLab or Gitea must still work.
        assertIs<FixLinkResult.Valid>(validate("https://git.internal.example:8443/team/app/-/merge_requests/7"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        val valid = assertIs<FixLinkResult.Valid>(validate("  https://github.com/acme/app/pull/1  "))
        assertEquals("https://github.com/acme/app/pull/1", valid.url)
    }

    @Test
    fun `rejects javascript scheme`() {
        // Would execute in the dashboard when someone clicks it.
        assertIs<FixLinkResult.Rejected>(validate("javascript:alert(document.cookie)"))
    }

    @Test
    fun `rejects data scheme`() {
        assertIs<FixLinkResult.Rejected>(validate("data:text/html;base64,PHNjcmlwdD4="))
    }

    @Test
    fun `rejects plain http`() {
        assertIs<FixLinkResult.Rejected>(validate("http://github.com/acme/app/pull/1"))
    }

    @Test
    fun `rejects embedded credentials`() {
        // https://github.com@evil.test/... reads as github.com at a glance.
        assertIs<FixLinkResult.Rejected>(validate("https://github.com@evil.test/acme/app/pull/1"))
    }

    @Test
    fun `rejects non-ascii characters used for homographs`() {
        // Cyrillic "о" in githуb — indistinguishable in the dashboard.
        assertIs<FixLinkResult.Rejected>(validate("https://githоb.com/acme/app/pull/1"))
    }

    @Test
    fun `rejects control characters that hide part of the url`() {
        assertIs<FixLinkResult.Rejected>(validate("https://github.com/acme/app/pull/1evil"))
    }

    @Test
    fun `rejects a bare host with no page`() {
        assertIs<FixLinkResult.Rejected>(validate("https://github.com"))
        assertIs<FixLinkResult.Rejected>(validate("https://github.com/"))
    }

    @Test
    fun `rejects a host without a dot`() {
        assertIs<FixLinkResult.Rejected>(validate("https://localhost/acme/app/pull/1"))
    }

    @Test
    fun `rejects an invalid port`() {
        assertIs<FixLinkResult.Rejected>(validate("https://git.example:99999/a/b/pull/1"))
        assertIs<FixLinkResult.Rejected>(validate("https://git.example:abc/a/b/pull/1"))
    }

    @Test
    fun `rejects empty and oversized urls`() {
        assertIs<FixLinkResult.Rejected>(validate("   "))
        assertIs<FixLinkResult.Rejected>(validate("https://git.example/" + "a".repeat(3000)))
    }

    @Test
    fun `rejects characters that must be percent-encoded`() {
        // kotlinx.html escapes attribute values, so these would not actually break out of
        // the href. Rejected anyway so the guarantee does not rest on the templating engine.
        assertIs<FixLinkResult.Rejected>(validate("""https://ok.example/a/b"onmouseover=alert(1)"""))
        assertIs<FixLinkResult.Rejected>(validate("https://ok.example/a/<script>"))
        assertIs<FixLinkResult.Rejected>(validate("https://ok.example/a b/pull/1"))
    }

    @Test
    fun `rejection reasons never echo the url back`() {
        // Reasons surface to the same agent; repeating the payload would undo the point.
        val rejected = assertIs<FixLinkResult.Rejected>(validate("javascript:alert(1)"))
        assertEquals(false, "alert" in rejected.reason)
    }
}
