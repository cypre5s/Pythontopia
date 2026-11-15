"""Pure task graph readiness and completion calculations."""


def node_statuses(definition: dict, states: dict) -> dict:
    nodes = {node["id"]: node for node in definition["nodes"]}
    memo = {}

    def evaluate(node_id):
        if node_id in memo:
            return memo[node_id]
        if node_id in states:
            memo[node_id] = "complete"
            return "complete"
        node = nodes[node_id]
        condition = node.get("condition")
        if condition:
            parent = states.get(condition["node"])
            if parent and parent.choice != condition["choice"]:
                memo[node_id] = "skipped"
                return "skipped"
        deps = [evaluate(dep) for dep in node.get("dependencies", [])]
        if node.get("aggregation", "ALL") == "ANY" and deps:
            status = "ready" if "complete" in deps else "skipped" if all(x == "skipped" for x in deps) else "locked"
        else:
            if deps and all(status == "skipped" for status in deps):
                status = "skipped"
            elif all(status in {"complete", "skipped"} for status in deps):
                status = "ready"
            else:
                status = "locked"
        if condition and not states.get(condition["node"]):
            status = "locked"
        memo[node_id] = status
        return status

    return {node_id: evaluate(node_id) for node_id in nodes}
