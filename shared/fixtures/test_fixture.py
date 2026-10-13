"""Verify reference fixture integrity and independently specified expected values."""

import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SNAPSHOT = json.loads((ROOT / "snapshot-v1.json").read_text(encoding="utf-8"))
EXPECTED = json.loads((ROOT / "expected-v1.json").read_text(encoding="utf-8"))


def duration(record, start=EXPECTED["weekStart"], end=EXPECTED["cutoff"]):
    return max(0, min(record["timeEnded"], end) - max(record["timeStarted"], start))


class SnapshotFixtureTest(unittest.TestCase):
    def test_references_and_ratings(self):
        type_ids = {item["id"] for item in SNAPSHOT["recordTypes"]}
        tags = {item["id"]: item for item in SNAPSHOT["tags"]}
        rating_ids = set(SNAPSHOT["ratingTagIds"].values())
        self.assertEqual(len(rating_ids), 2)
        for identifier in rating_ids:
            self.assertEqual(tags[identifier]["valueType"], "NUMERIC")
        for item in SNAPSHOT["records"] + SNAPSHOT["runningRecords"]:
            self.assertIn(item.get("typeId", item["id"]), type_ids | {"0"})
            self.assertEqual(len(item["tags"]), len({tag["tagId"] for tag in item["tags"]}))
            for tag in item["tags"]:
                self.assertIn(tag["tagId"], tags)
                if tag["tagId"] in rating_ids and tag["numericValue"] is not None:
                    self.assertIn(tag["numericValue"], range(1, 6))
        for template in SNAPSHOT["templates"]:
            self.assertFalse(rating_ids & {tag["tagId"] for tag in template["tags"]})

    def test_union_is_bounded_by_snapshot_time(self):
        intervals = [(r["timeStarted"], r["timeEnded"]) for r in SNAPSHOT["records"]]
        intervals += [(r["timeStarted"], SNAPSHOT["createdAt"]) for r in SNAPSHOT["runningRecords"]]
        merged = []
        for start, end in sorted(intervals):
            start, end = max(start, EXPECTED["weekStart"]), min(end, EXPECTED["cutoff"])
            if merged and start <= merged[-1][1]:
                merged[-1][1] = max(end, merged[-1][1])
            elif end > start:
                merged.append([start, end])
        total = sum(end - start for start, end in merged)
        self.assertEqual(EXPECTED["recordedUnionMs"], total)
        elapsed = EXPECTED["cutoff"] - EXPECTED["weekStart"]
        self.assertEqual(EXPECTED["elapsedMs"], elapsed)
        self.assertEqual(EXPECTED["unrecordedMs"], elapsed - total)
        self.assertEqual(EXPECTED["runningMs"], sum(SNAPSHOT["createdAt"] - r["timeStarted"] for r in SNAPSHOT["runningRecords"]))

    def test_state_and_category_totals(self):
        confirmed = [r for r in SNAPSHOT["records"] if r["reviewState"] == "CONFIRMED"]
        totals = {}
        for record in confirmed:
            self.assertEqual(duration(record) % 900000, 0)
            totals[record["typeId"]] = totals.get(record["typeId"], 0) + duration(record)
        self.assertEqual(EXPECTED["confirmedEventCount"], len(confirmed))
        self.assertEqual(EXPECTED["confirmedMs"], sum(totals.values()))
        self.assertEqual(EXPECTED["confirmedByType"], totals)
        self.assertEqual(EXPECTED["pendingMs"], sum(duration(r) for r in SNAPSHOT["records"] if r["reviewState"] == "PENDING"))
        for day, total in enumerate(EXPECTED["dailyConfirmedMs"]):
            start = EXPECTED["weekStart"] + day * 86400000
            self.assertEqual(total, sum(duration(r, start, min(start + 86400000, EXPECTED["cutoff"])) for r in confirmed))

    def test_scores_and_combined_activity_are_not_duplicated(self):
        confirmed = {r["id"]: r for r in SNAPSHOT["records"] if r["reviewState"] == "CONFIRMED"}
        for dimension in ["happiness", "meaning"]:
            identifier = SNAPSHOT["ratingTagIds"][dimension + "TagId"]
            values = [t["numericValue"] for r in confirmed.values() for t in r["tags"]
                      if t["tagId"] == identifier and t["numericValue"] is not None]
            self.assertEqual(EXPECTED[dimension]["distribution"], [values.count(score) for score in range(1, 6)])
            self.assertEqual(EXPECTED[dimension]["ratedCount"], len(values))
            self.assertEqual(EXPECTED[dimension]["unratedCount"], len(confirmed) - len(values))
        matches = {r["id"]: r for r in SNAPSHOT["records"] for t in r["tags"] if t["tagId"] in {"201", "202"}}
        self.assertEqual(EXPECTED["combinedTagFilterIds"], list(matches))
        self.assertEqual(EXPECTED["combinedTagFilterMs"], sum(duration(r) for r in matches.values()))

    def test_reference_photo_is_complete(self):
        ids = {r["id"] for r in SNAPSHOT["records"]}
        for photo in SNAPSHOT["photos"]:
            self.assertIn(photo["recordId"], ids)
            self.assertIsNone(photo["runningRecordId"])
            data = (ROOT / photo["relativePath"]).read_bytes()
            self.assertEqual(photo["sizeBytes"], len(data))
            self.assertEqual(photo["sha256"], hashlib.sha256(data).hexdigest())
            self.assertTrue(data.startswith(b"\x89PNG\r\n\x1a\n"))


if __name__ == "__main__":
    unittest.main()
