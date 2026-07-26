from __future__ import annotations

import json
import re
from functools import lru_cache
from pathlib import Path
from typing import Any, Mapping


SCHEMA_VERSION_V1 = "agent-schema-v1"


class SchemaRegistryError(ValueError):
    """Raised when a registry artifact is missing or internally inconsistent."""


def default_registry_root() -> Path:
    return Path(__file__).resolve().parents[2] / "schemas" / "agent"


@lru_cache(maxsize=8)
def load_agent_schemas(
    version: str = SCHEMA_VERSION_V1,
    registry_root: str | Path | None = None,
) -> dict[str, dict[str, Any]]:
    root = Path(registry_root) if registry_root is not None else default_registry_root()
    version_dir = root / _version_dir(version)
    if not version_dir.is_dir():
        raise SchemaRegistryError(f"agent schema version not found: {version}")
    schemas: dict[str, dict[str, Any]] = {}
    for path in sorted(version_dir.glob("*.schema.json")):
        schema = json.loads(path.read_text(encoding="utf-8"))
        agent = str(schema.get("title") or "")
        if not agent:
            raise SchemaRegistryError(f"schema title missing: {path}")
        if agent in schemas:
            raise SchemaRegistryError(f"duplicate agent schema: {agent}")
        if schema.get("type") != "object":
            raise SchemaRegistryError(f"agent schema must be an object: {path}")
        required = schema.get("required")
        properties = schema.get("properties")
        if not isinstance(required, list) or not isinstance(properties, dict):
            raise SchemaRegistryError(f"invalid required/properties contract: {path}")
        missing_properties = [key for key in required if key not in properties]
        if missing_properties:
            raise SchemaRegistryError(
                f"required keys missing properties in {path}: {missing_properties}"
            )
        schemas[agent] = schema
    if not schemas:
        raise SchemaRegistryError(f"no agent schemas found for version: {version}")
    return schemas


def schema_for_agent(
    agent: str,
    version: str = SCHEMA_VERSION_V1,
    registry_root: str | Path | None = None,
) -> dict[str, Any]:
    schemas = load_agent_schemas(version, registry_root)
    try:
        return schemas[agent]
    except KeyError as exc:
        raise SchemaRegistryError(f"unknown agent schema: {version}/{agent}") from exc


def validate_object(
    value: Mapping[str, Any] | None,
    schema: Mapping[str, Any],
) -> tuple[bool, tuple[str, ...], tuple[str, ...]]:
    required = [str(key) for key in schema.get("required", [])]
    if value is None:
        return False, tuple(required), ()
    missing, invalid = _validation_errors(value, schema, schema, "")
    return not missing and not invalid, tuple(missing), tuple(invalid)


def legacy_type_map(
    version: str = SCHEMA_VERSION_V1,
    registry_root: str | Path | None = None,
) -> dict[str, dict[str, dict[str, type | tuple[type, ...]]]]:
    result: dict[str, dict[str, dict[str, type | tuple[type, ...]]]] = {}
    for agent, schema in load_agent_schemas(version, registry_root).items():
        properties = schema["properties"]
        result[agent] = {
            "required": {
                key: _python_type(properties[key]) for key in schema["required"]
            }
        }
    return result


def _version_dir(version: str) -> str:
    prefix = "agent-schema-"
    return version[len(prefix) :] if version.startswith(prefix) else version


def _matches_schema(value: Any, schema: Any, root: Mapping[str, Any] | None = None) -> bool:
    if root is None and isinstance(schema, Mapping):
        root = schema
    missing, invalid = _validation_errors(value, schema, root or {}, "")
    return not missing and not invalid


def _validation_errors(
    value: Any,
    schema: Any,
    root: Mapping[str, Any],
    path: str,
) -> tuple[list[str], list[str]]:
    if not isinstance(schema, Mapping):
        return [], []
    if "$ref" in schema:
        target = _resolve_local_ref(root, str(schema["$ref"]))
        return _validation_errors(value, target, root, path)
    for keyword in ("allOf",):
        branches = schema.get(keyword)
        if isinstance(branches, list):
            missing: list[str] = []
            invalid: list[str] = []
            for branch in branches:
                branch_missing, branch_invalid = _validation_errors(value, branch, root, path)
                missing.extend(branch_missing)
                invalid.extend(branch_invalid)
            if missing or invalid:
                return missing, invalid
    for keyword in ("anyOf", "oneOf"):
        branches = schema.get(keyword)
        if isinstance(branches, list):
            results = [_validation_errors(value, branch, root, path) for branch in branches]
            passing = [result for result in results if not result[0] and not result[1]]
            if (keyword == "anyOf" and passing) or (keyword == "oneOf" and len(passing) == 1):
                break
            return [], [_display_path(path)]
    expected = schema.get("type")
    if isinstance(expected, list):
        if not any(_type_matches(value, item) for item in expected):
            return [], [_display_path(path)]
    elif expected and not _type_matches(value, expected):
        return [], [_display_path(path)]

    if "const" in schema and value != schema["const"]:
        return [], [_display_path(path)]
    enum = schema.get("enum")
    if isinstance(enum, list) and value not in enum:
        return [], [_display_path(path)]

    missing: list[str] = []
    invalid: list[str] = []
    if isinstance(value, dict):
        properties = schema.get("properties", {})
        required_keys = schema.get("required", [])
        if isinstance(required_keys, list):
            missing.extend(
                _join_path(path, str(key)) for key in required_keys if key not in value
            )
        if isinstance(properties, Mapping):
            for key, child_value in value.items():
                if key in properties:
                    child_missing, child_invalid = _validation_errors(
                        child_value, properties[key], root, _join_path(path, str(key))
                    )
                    missing.extend(child_missing)
                    invalid.extend(child_invalid)
                elif schema.get("additionalProperties") is False:
                    invalid.append(_join_path(path, str(key)))
    elif isinstance(value, list):
        if isinstance(schema.get("minItems"), int) and len(value) < schema["minItems"]:
            invalid.append(_display_path(path))
        if isinstance(schema.get("maxItems"), int) and len(value) > schema["maxItems"]:
            invalid.append(_display_path(path))
        if schema.get("uniqueItems") is True and len({json.dumps(item, sort_keys=True, ensure_ascii=False) for item in value}) != len(value):
            invalid.append(_display_path(path))
        items = schema.get("items")
        if isinstance(items, Mapping):
            for index, child_value in enumerate(value):
                child_missing, child_invalid = _validation_errors(
                    child_value, items, root, f"{path}[{index}]"
                )
                missing.extend(child_missing)
                invalid.extend(child_invalid)
    elif isinstance(value, str):
        if isinstance(schema.get("minLength"), int) and len(value) < schema["minLength"]:
            invalid.append(_display_path(path))
        if isinstance(schema.get("maxLength"), int) and len(value) > schema["maxLength"]:
            invalid.append(_display_path(path))
        pattern = schema.get("pattern")
        if isinstance(pattern, str) and re.search(pattern, value) is None:
            invalid.append(_display_path(path))
    elif isinstance(value, (int, float)) and not isinstance(value, bool):
        if isinstance(schema.get("minimum"), int | float) and value < schema["minimum"]:
            invalid.append(_display_path(path))
        if isinstance(schema.get("maximum"), int | float) and value > schema["maximum"]:
            invalid.append(_display_path(path))
    return _dedupe(missing), _dedupe(invalid)


def _type_matches(value: Any, expected: Any) -> bool:
    if expected == "string":
        return isinstance(value, str)
    if expected == "boolean":
        return isinstance(value, bool)
    if expected == "array":
        return isinstance(value, list)
    if expected == "object":
        return isinstance(value, dict)
    if expected == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if expected == "number":
        return isinstance(value, int | float) and not isinstance(value, bool)
    if expected == "null":
        return value is None
    return True


def _resolve_local_ref(root: Mapping[str, Any], ref: str) -> Any:
    if not ref.startswith("#/"):
        raise SchemaRegistryError(f"only local schema refs are supported: {ref}")
    current: Any = root
    for raw_part in ref[2:].split("/"):
        part = raw_part.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, Mapping) or part not in current:
            raise SchemaRegistryError(f"unresolved local schema ref: {ref}")
        current = current[part]
    return current


def _join_path(parent: str, key: str) -> str:
    return f"{parent}.{key}" if parent else key


def _display_path(path: str) -> str:
    return path or "$"


def _dedupe(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))


def _python_type(schema: Mapping[str, Any]) -> type | tuple[type, ...]:
    expected = schema.get("type")
    if expected == "string":
        return str
    if expected == "boolean":
        return bool
    if expected == "array":
        return list
    if expected == "object":
        return dict
    if expected == "integer":
        return int
    if expected == "number":
        return (int, float)
    return object
