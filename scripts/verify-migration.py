"""Compare a complete ZIP before and after restoration into the release APK."""

import hashlib
import json
import sys
import zipfile


def read(path):
    with zipfile.ZipFile(path) as archive:
        root = json.loads(archive.read("snapshot.json"))
        for photo in root["photos"]:
            data = archive.read(photo["relativePath"])
            assert hashlib.sha256(data).hexdigest() == photo["sha256"], "photo hash"
            assert len(data) == photo["sizeBytes"], "photo size"
        return root


def verify(source, restored):
    for field in (
        "schemaVersion", "dataSetId", "recordTypes", "categories", "tags",
        "ratingTagIds", "templates", "weeklyReviews", "trials", "businessSettings",
        "recordTypeCategories", "recordTypeTags", "defaultRecordTypeTags", "settingShortcuts",
    ):
        assert source[field] == restored[field], field
    assert not restored["runningRecords"], "a restored timer must not resume"
    records = {row["id"]: row for row in restored["records"]}
    for row in source["records"]:
        assert records[row["id"]] == row, f"record {row['id']}"
    ongoing_to_ended = {}
    original_ids = {row["id"] for row in source["records"]}
    new_rows = [row for row in restored["records"] if row["id"] not in original_ids]
    assert len(new_rows) == len(source["runningRecords"]), "ended timer count"
    for ongoing in source["runningRecords"]:
        matches = [row for row in new_rows if all(
            row.get(key) == ongoing.get(key)
            for key in ("timeStarted", "summary", "comment", "zoneId", "templateId", "tags")
        ) and row["typeId"] == ongoing["id"]]
        assert len(matches) == 1, "ended timer identity and details"
        ended = matches[0]
        assert ended["timeEnded"] == source["createdAt"], "timer cutoff"
        assert ended["reviewState"] == "PENDING", "ended timer review state"
        ongoing_to_ended[ongoing["id"]] = ended["id"]
    photos = {row["id"]: row for row in restored["photos"]}
    assert len(photos) == len(source["photos"]), "photo count"
    for photo in source["photos"]:
        other = photos[photo["id"]]
        for key in ("sha256", "sizeBytes", "mimeType", "position"):
            assert photo.get(key) == other.get(key), f"photo {key}"
        owner = ongoing_to_ended[photo["runningRecordId"]] if photo.get("runningRecordId") else photo["recordId"]
        assert other["recordId"] == owner and not other.get("runningRecordId"), "photo transfer"
    changed_tables = {"records", "runningRecords", "recordPhotos", "recordToRecordTag", "runningRecordToRecordTag"}
    assert source["upstreamData"]["databaseVersion"] == restored["upstreamData"]["databaseVersion"]
    for table, rows in source["upstreamData"]["tables"].items():
        if table not in changed_tables:
            assert rows == restored["upstreamData"]["tables"][table], f"native {table}"
    print(json.dumps({
        "migrationVerified": True,
        "records": len(records), "photos": len(photos),
        "templates": len(restored["templates"]),
        "reviews": len(restored["weeklyReviews"]), "trials": len(restored["trials"]),
        "runningRecords": 0,
    }))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: verify-migration.py source.zip restored.zip")
    verify(read(sys.argv[1]), read(sys.argv[2]))
