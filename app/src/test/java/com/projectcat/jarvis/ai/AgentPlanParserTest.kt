package com.projectcat.jarvis.ai

import com.projectcat.jarvis.tools.LocalCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPlanParserTest {
    @Test
    fun parsesSingleWebSearchProposal() {
        val proposal = AgentPlanParser.parse(
            "Find information about Android local AI",
            "TOOL: web_search\nINPUT: Android local AI\nREASON: Search for current implementation references."
        )
        assertTrue(proposal?.action is LocalCommand.SearchWeb)
        assertEquals("Android local AI", (proposal?.action as LocalCommand.SearchWeb).query)
    }

    @Test
    fun parsesSafeMapsProposal() {
        val proposal = AgentPlanParser.parse(
            "Find the city railway station",
            "TOOL: maps_search\nINPUT: city railway station\nREASON: Open a map search for the requested place."
        )
        assertTrue(proposal?.action is LocalCommand.MapsSearch)
    }

    @Test
    fun parsesHttpsOpenAndDialerProposals() {
        val open = AgentPlanParser.parse(
            "Open the official site",
            "TOOL: open_url\nINPUT: https://example.com\nREASON: Open the secure site requested by the user."
        )
        assertTrue(open?.action is LocalCommand.OpenUrl)

        val dial = AgentPlanParser.parse(
            "Prepare a call",
            "TOOL: dial_number\nINPUT: +919876543210\nREASON: Open the dialer with this number for user review."
        )
        assertTrue(dial?.action is LocalCommand.DialNumber)
    }

    @Test
    fun rejectsUnsafeUrlsAndUnknownTools() {
        assertNull(
            AgentPlanParser.parse(
                "Open this link",
                "TOOL: open_url\nINPUT: http://bad.example\nREASON: Open the supplied destination."
            )
        )
        assertNull(
            AgentPlanParser.parse(
                "Run a command",
                "TOOL: shell\nINPUT: rm -rf /\nREASON: This is not an allowed action."
            )
        )
    }

    @Test
    fun rejectsMultipleActionsOrExtraModelText() {
        assertNull(
            AgentPlanParser.parse(
                "Do two things",
                "TOOL: web_search\nINPUT: query\nREASON: First action.\nTOOL: dial_number"
            )
        )
        assertNull(
            AgentPlanParser.parse(
                "Find something",
                "Here is the plan:\nTOOL: web_search\nINPUT: query\nREASON: Search."
            )
        )
    }

    @Test
    fun rejectsEmptyOrUnstructuredOutput() {
        assertNull(AgentPlanParser.parse("Find something", "NO_ACTION"))
        assertNull(AgentPlanParser.parse("Find something", ""))
        assertNull(AgentPlanParser.parse("Find something", "TOOL: web_search\nINPUT: x"))
    }

    @Test
    fun parsesAgentRequestOnlyForExactCommandToken() {
        assertEquals(
            AgentRequest.Task("Find the official website"),
            AgentRequestPolicy.parse("/agent Find the official website")
        )
        assertTrue(AgentRequestPolicy.parse("/agent") is AgentRequest.Invalid)
        assertNull(AgentRequestPolicy.parse("/agents do something"))
    }
}
