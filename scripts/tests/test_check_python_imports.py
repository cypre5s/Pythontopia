"""Synthetic dependency graphs cover hidden imports and legal assembly."""

import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "check_python_imports", Path(__file__).parents[1] / "check-python-imports.py"
)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class PythonImportChecks(unittest.TestCase):
    def test_function_local_relative_import_participates_in_cycles(self):
        graph, findings = checker.analyze_sources(
            {
                "app.feature.api": "from . import rules",
                "app.feature.rules": "def run():\n    from .api import call\n",
            }
        )
        self.assertEqual(graph["app.feature.rules"], {"app.feature.api"})
        self.assertTrue(any("循环依赖" in item.message for item in findings))

    def test_initializer_can_assemble_child_modules(self):
        _, findings = checker.analyze_sources(
            {
                "app.feature": "from .api import router",
                "app.feature.api": "from . import rules",
                "app.feature.rules": "VALUE = 1",
            },
            {"app.feature"},
        )
        self.assertEqual(findings, [])

    def test_private_import_and_module_alias_attribute_are_detected(self):
        _, findings = checker.analyze_sources(
            {
                "app.rules": "_secret = 1",
                "app.api": "from .rules import _secret\nfrom . import rules as domain\ndef run():\n    return domain._secret",
            }
        )
        self.assertEqual(len(findings), 2)
        self.assertTrue(any("私有导入" in item.message for item in findings))
        self.assertTrue(any("私有访问" in item.message for item in findings))

    def test_dynamic_import_is_counted_and_private_access_is_detected(self):
        graph, findings = checker.analyze_sources(
            {
                "app.rules": "from .api import run",
                "app.api": "from importlib import import_module as load\ndef run():\n    module = load('.rules', package='app')\n    return module._secret",
            }
        )
        self.assertEqual(graph["app.api"], {"app.rules"})
        self.assertTrue(any("循环依赖" in item.message for item in findings))
        self.assertTrue(any("私有访问" in item.message for item in findings))

    def test_framework_objects_and_shadowed_aliases_are_not_module_access(self):
        _, findings = checker.analyze_sources(
            {
                "app.rules": "class Model: pass",
                "app.api": "import sqlalchemy as orm\nfrom .rules import Model\nfrom . import rules\ndef run(rules):\n    return orm._internal, Model._metadata, rules._value",
            }
        )
        self.assertEqual(findings, [])

    def test_constant_keyword_import_module_call_participates_in_cycles(self):
        graph, findings = checker.analyze_sources(
            {
                "app.rules": "from .api import run",
                "app.api": "import importlib\ndef run():\n    return importlib.import_module(name='app.rules')",
            }
        )
        self.assertEqual(graph["app.api"], {"app.rules"})
        self.assertTrue(any("循环依赖" in item.message for item in findings))

    def test_reassignment_stops_treating_an_object_as_a_module(self):
        _, findings = checker.analyze_sources(
            {
                "app.rules": "_secret = 1",
                "app.api": "from . import rules\nrules = object()\nrules._field\ndef run():\n    from . import rules\n    rules = object()\n    return rules._field",
            }
        )
        self.assertEqual(findings, [])

    def test_assigned_alias_preserves_private_module_boundary(self):
        _, findings = checker.analyze_sources(
            {
                "app.rules": "_secret = 1",
                "app.api": "from . import rules\nalias = rules\nalias._secret",
            }
        )
        self.assertEqual(len(findings), 1)
        self.assertIn("私有访问：app.rules._secret", findings[0].message)


if __name__ == "__main__":
    unittest.main()
