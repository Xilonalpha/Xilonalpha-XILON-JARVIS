import json

def dispatch(model, registry, question, confirm=None):
    prompt = (
        "Return only a JSON object with either "
        '{"answer":"text"} or {"tool":"name","args":{}}. '
        "Never claim a tool has executed. Available tools: "
        + json.dumps(registry.describe(), ensure_ascii=False)
        + "\nUser request: " + json.dumps(question, ensure_ascii=False)
    )
    raw = model(prompt)
    try:
        decision = json.loads(raw)
    except (ValueError, TypeError):
        raise ValueError("Model did not return a valid structured decision")
    if not isinstance(decision, dict):
        raise ValueError("Invalid decision")
    if set(decision) == {"answer"} and isinstance(decision["answer"], str):
        return {"kind": "model_answer", "answer": decision["answer"]}
    if set(decision) != {"tool", "args"} or not isinstance(decision["tool"], str) or not isinstance(decision["args"], dict):
        raise ValueError("Invalid tool decision")
    result = registry.execute(decision["tool"], decision["args"], confirm)
    return {"kind": "tool_result", "tool": decision["tool"], "result": result}

def run_task(model, registry, question, confirm=None, max_steps=4):
    if type(max_steps) is not int or not 1 <= max_steps <= 8:
        raise ValueError("Invalid action budget")
    events = []
    seen = set()
    for _ in range(max_steps):
        context = json.dumps({"goal": question, "observations": events}, ensure_ascii=False)
        def guarded(name, args):
            signature = json.dumps([name, args], sort_keys=True)
            if signature in seen:
                raise ValueError("Repeated action")
            seen.add(signature)
            return registry.execute(name, args, confirm)
        class GuardedRegistry:
            def describe(self):
                return registry.describe()
            def execute(self, name, args, ignored=None):
                return guarded(name, args)
        try:
            decision = dispatch(model, GuardedRegistry(), context)
        except (ValueError, TypeError, PermissionError, RuntimeError, OSError) as error:
            return {"status": "blocked", "observations": events, "error": str(error)}
        if decision["kind"] == "model_answer":
            return {"status": "model_answer_unverified", "observations": events, "answer": decision["answer"]}
        events.append(decision)
    return {"status": "budget_exhausted", "observations": events}
