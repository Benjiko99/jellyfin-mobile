package org.jellyfin.mobile.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.network.testApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A realistic file: the links we want, buried in web-client settings that mean nothing to us. */
private const val WEB_CONFIG = """
    {
      "includeCorsCredentials": false,
      "multiserver": false,
      "themes": [{ "name": "Dark", "id": "dark", "default": true }],
      "menuLinks": [
        { "name": "Requests", "icon": "live_tv", "url": "https://requests.example.invalid/" },
        { "name": "Wiki", "url": "https://wiki.example.invalid/" }
      ],
      "servers": [],
      "plugins": ["htmlVideoPlayer", "photoPlayer"]
    }
"""

class MenuLinksRepositoryTest {
    private fun repositoryWith(engine: MockEngine) = MenuLinksRepository(testApi(engine))

    private fun jsonEngine(body: String, contentType: ContentType = ContentType.Application.Json) =
        MockEngine {
            respond(content = body, headers = headersOf(HttpHeaders.ContentType, contentType.toString()))
        }

    @Test
    fun `reads the links out of the web client config`() = runTest {
        val links = repositoryWith(jsonEngine(WEB_CONFIG)).loadMenuLinks()

        assertEquals(listOf("Requests", "Wiki"), links.map { it.name })
        assertEquals("https://requests.example.invalid/", links.first().url)
    }

    @Test
    fun `reads the config from the web client, not from an API route`() = runTest {
        val engine = jsonEngine(WEB_CONFIG)

        repositoryWith(engine).loadMenuLinks()

        assertEquals(
            "http://jellyfin.test/web/config.json",
            engine.requestHistory.single().url.toString(),
        )
    }

    /**
     * Static files are served with whatever content type the deployment decided on, and we ask for
     * the body as text precisely so that a `text/plain` config still parses.
     */
    @Test
    fun `decodes regardless of the content type the server serves it with`() = runTest {
        val links = repositoryWith(jsonEngine(WEB_CONFIG, ContentType.Text.Plain)).loadMenuLinks()

        assertEquals(listOf("Requests", "Wiki"), links.map { it.name })
    }

    /** Hand-edited JSON. A nameless row would be blank and a URL-less one would do nothing. */
    @Test
    fun `drops entries missing a name or a url`() = runTest {
        val config = """
            {
              "menuLinks": [
                { "name": "Requests", "url": "https://requests.example.invalid/" },
                { "name": "No URL" },
                { "url": "https://no-name.example.invalid/" },
                { "name": "  ", "url": "https://blank.example.invalid/" }
              ]
            }
        """.trimIndent()

        val links = repositoryWith(jsonEngine(config)).loadMenuLinks()

        assertEquals(listOf("Requests"), links.map { it.name })
    }

    @Test
    fun `a server without the web client simply has no links`() = runTest {
        // `--nowebclient`, or a proxy that exposes the API but not /web/.
        val links = repositoryWith(MockEngine { respondError(HttpStatusCode.NotFound) }).loadMenuLinks()

        assertTrue(links.isEmpty())
    }

    /**
     * The one failure that would do real damage. `config.json` is static content, so a proxy with
     * its own authentication in front of `/web/` answers 401 — and a
     * [org.jellyfin.mobile.network.SessionExpiredException] escaping from here would sign the user
     * out of a Jellyfin session that is working perfectly well.
     */
    @Test
    fun `an authenticating proxy in front of the web client does not sign the user out`() = runTest {
        val links = repositoryWith(
            MockEngine { respondError(HttpStatusCode.Unauthorized) },
        ).loadMenuLinks()

        assertTrue(links.isEmpty())
    }

    @Test
    fun `a config that is not JSON at all has no links`() = runTest {
        // A proxy or a captive portal answering /web/ with an HTML page.
        val links = repositoryWith(jsonEngine("<!DOCTYPE html><html></html>")).loadMenuLinks()

        assertTrue(links.isEmpty())
    }
}
