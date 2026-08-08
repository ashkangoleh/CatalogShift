import unittest

from scripts.state_model import canonical_json, compare_states, fingerprint


class StateModelTests(unittest.TestCase):
    def test_canonical_json_is_order_independent(self):
        self.assertEqual(canonical_json({"b": 2, "a": 1}), canonical_json({"a": 1, "b": 2}))

    def test_fingerprint_changes_with_content(self):
        self.assertNotEqual(fingerprint([1, 2]), fingerprint([1, 3]))

    def test_equal_states_have_no_failures(self):
        state = {"tables": {"demo.orders": {"row_count": 5}}}
        self.assertEqual(compare_states(state, state), [])

    def test_comparison_reports_table_and_field_differences(self):
        expected = {"tables": {"demo.orders": {"row_count": 5}}}
        actual = {"tables": {"demo.orders": {"row_count": 4}, "demo.extra": {}}}
        failures = compare_states(expected, actual)
        self.assertEqual(failures[0]["field"], "tables")
        self.assertEqual(failures[1]["field"], "row_count")


if __name__ == "__main__":
    unittest.main()
