package com.projectcat.jarvis.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCompletionsEndpointTest {
    @Test fun appendsChatCompletionsToVersionedBaseUrl() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ChatCompletionsEndpoint.from("https://api.example.com/v1/")
        )
    }

    @Test fun acceptsFullEndpointWithoutDuplicatingPath() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ChatCompletionsEndpoint.from("https://api.example.com/v1/chat/completions")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUrlsWithoutHttpScheme() {
        ChatCompletionsEndpoint.from("api.example.com/v1")
    }
}
