package com.xilon.jarvis

import org.junit.Test
import org.junit.Assert.assertTrue

class RegistryPlannerTest {
 private fun rejects(raw:String){
  assertTrue("Invalid plan accepted",runCatching{RegistryPlanner.parse(raw)}.isFailure)
 }
 @Test fun rejectsProse(){rejects("I have executed your task")}
 @Test fun rejectsEmptyPlan(){rejects("[]")}
 @Test fun rejectsObjectInsteadOfArray(){rejects("{}")}
 @Test fun rejectsMissingArguments(){rejects("""[{"tool":"open_app"}]""")}
 @Test fun rejectsExtraKeys(){rejects("""[{"tool":"open_app","args":{},"verified":true}]""")}
 @Test fun rejectsUnknownTool(){rejects("""[{"tool":"nonexistent_test_tool_123","args":{}}]""")}
 @Test fun rejectsOversizedPlan(){rejects(List(21){"{}"}.joinToString(",","[","]"))}
 @Test fun rejectsTruncatedJson(){rejects("""[{"tool":"open_app","args":""")}
}
