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
