package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class AgentRecoveryTest {
 @Test fun unknownRequiresVerification(){assertTrue(AgentRecovery.guidance("EXECUTION_UNKNOWN",2)!!.contains("manual"))}
 @Test fun completedNeedsNoRecovery(){assertNull(AgentRecovery.guidance("COMPLETED",0))}
 @Test fun confirmationNotBypassed(){assertNull(AgentRecovery.guidance("REQUIRES_CONFIRMATION",1))}
 @Test fun stoppedWithoutRemaining(){assertNull(AgentRecovery.guidance("STOPPED",0))}
}
