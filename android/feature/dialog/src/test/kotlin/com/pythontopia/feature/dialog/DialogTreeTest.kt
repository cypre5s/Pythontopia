package com.pythontopia.feature.dialog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogTreeTest {

    @Test
    fun `root node exists and is named greet`() {
        val root = DemoDialogTree.nodes[DemoDialogTree.ROOT_NODE_ID]
        assertNotNull(root)
        assertEquals("greet", root!!.id)
    }

    @Test
    fun `every non-terminal next node id resolves to an existing node`() {
        DemoDialogTree.nodes.values.forEach { node ->
            node.choices.forEach { choice ->
                if (choice.nextNodeId != null) {
                    assertTrue(
                        DemoDialogTree.nodes.containsKey(choice.nextNodeId),
                        "Unknown nextNodeId=${choice.nextNodeId} from ${node.id}",
                    )
                }
            }
        }
    }

    @Test
    fun `greet has a terminal choice that ends the conversation`() {
        val greet = DemoDialogTree.nodes.getValue("greet")
        val terminals = greet.choices.filter { it.nextNodeId == null }
        assertEquals(1, terminals.size)
        assertNull(terminals.first().nextNodeId)
    }

    @Test
    fun `chat_progress includes a SET_FLAG choice for native hub progress`() {
        val node = DemoDialogTree.nodes.getValue("chat_progress")
        val flagged = node.choices.firstOrNull { it.setFlagKey == "android_native_hub_played" }
        assertNotNull(flagged)
        assertEquals("true", flagged!!.setFlagValue)
    }
}
