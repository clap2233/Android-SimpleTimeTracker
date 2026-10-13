"""Generate deterministic development fixtures; never seed application data."""

import hashlib
import json
import struct
import zlib
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def timestamp(value):
    return int(datetime.fromisoformat(value).timestamp() * 1000)


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


png = b"\x89PNG\r\n\x1a\n"
png += png_chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
png += png_chunk(b"IDAT", zlib.compress(b"\x00\xff\xff\xff\xff"))
png += png_chunk(b"IEND", b"")
digest = hashlib.sha256(png).hexdigest()
(ROOT / "photos").mkdir(exist_ok=True)
(ROOT / "photos" / f"{digest}.png").write_bytes(png)

palette = [
    ("日常必需", "D9DDE2"), ("照顾自己", "285C46"), ("健康", "B6D8A8"),
    ("学习", "6DB5AC"), ("PKR复习", "CEBEE8"), ("创作／发挥", "B8DCF2"),
    ("用脑／事务", "44658D"), ("娱乐", "E7A09A"), ("熟悉关系", "EDBD72"), ("新关系", "F1DEA0"),
]
record_types = [
    {"id": str(index), "name": name, "icon": "", "color": {"colorId": 0, "colorInt": str(int("FF" + color, 16) - 2**32)},
     "defaultDuration": 0, "note": "", "hidden": False}
    for index, (name, color) in enumerate(palette, 1)
]


def tag(identifier, name, purpose="GENERAL", value_type="NONE"):
    return {"id": identifier, "name": name, "icon": "", "color": {"colorId": 0, "colorInt": ""},
            "iconColorSource": "0", "note": "", "archived": False, "valueType": value_type,
            "valueSuffix": "", "purpose": purpose}


def ratings(happiness, meaning, at):
    return [{"tagId": identifier, "numericValue": value, "ratedAt": timestamp(at)}
            for identifier, value in [("100", happiness), ("101", meaning)]]


def plain_tag(identifier):
    return {"tagId": identifier, "numericValue": None, "ratedAt": None}


def record(identifier, type_id, start, end, summary, tags=None, state="CONFIRMED", template_id=None):
    return {"id": identifier, "typeId": type_id, "timeStarted": timestamp(start), "timeEnded": timestamp(end),
            "comment": "", "tags": tags or [], "reviewState": state, "summary": summary,
            "zoneId": "Asia/Shanghai", "templateId": template_id}


snapshot = {
    "schemaVersion": 1, "dataSetId": "10000000-0000-4000-8000-000000000001",
    "snapshotId": "20000000-0000-4000-8000-000000000001", "createdAt": timestamp("2026-10-06T10:00:00+08:00"),
    "recordTypes": record_types, "categories": [], "recordTypeCategories": [],
    "recordTypeTags": [], "defaultRecordTypeTags": [], "settingShortcuts": [],
    "tags": [tag("100", "快乐", value_type="NUMERIC"), tag("101", "意义", value_type="NUMERIC"),
             tag("201", "播客"), tag("202", "学习内容"), tag("203", "家中", "ENVIRONMENT"), tag("205", "公园", "ENVIRONMENT")],
    "ratingTagIds": {"happinessTagId": "100", "meaningTagId": "101"},
    "records": [
        record("1", "1", "2026-10-05T23:45:00+08:00", "2026-10-06T00:15:00+08:00", "睡眠"),
        record("2", "2", "2026-10-06T08:00:00+08:00", "2026-10-06T08:30:00+08:00", "洗碗＋听播客",
               ratings(4, 3, "2026-10-06T08:40:00+08:00") + [plain_tag("201"), plain_tag("202")], template_id="7"),
        record("3", "0", "2026-10-06T08:30:00+08:00", "2026-10-06T08:45:00+08:00", "未分类"),
        record("4", "4", "2026-10-06T09:00:00+08:00", "2026-10-06T09:12:40+08:00", "家中阅读",
               ratings(2, 5, "2026-10-06T09:13:00+08:00") + [plain_tag("203")], state="PENDING", template_id="8"),
        record("5", "4", "2026-10-06T09:15:00+08:00", "2026-10-06T09:30:00+08:00", "公园阅读",
               ratings(4, 5, "2026-10-06T09:31:00+08:00") + [plain_tag("205")], template_id="8"),
    ],
    "runningRecords": [{"id": "4", "timeStarted": timestamp("2026-10-06T09:45:00+08:00"), "comment": "", "tags": [],
                        "summary": "继续阅读", "zoneId": "Asia/Shanghai", "templateId": "8"}],
    "photos": [{"id": "1", "recordId": "2", "runningRecordId": None, "relativePath": f"photos/{digest}.png",
                "sha256": digest, "position": 0, "mimeType": "image/png", "sizeBytes": len(png)}],
    "templates": [{"id": "7", "typeId": "2", "name": "洗碗时听播客", "summary": "洗碗＋听播客", "comment": "",
                   "tags": [plain_tag("201"), plain_tag("202")]},
                  {"id": "8", "typeId": "4", "name": "阅读", "summary": "阅读", "comment": "", "tags": []}],
    "weeklyReviews": [{"id": "1", "weekStart": "2026-10-05", "zoneId": "Asia/Shanghai",
                       "timeAbundance": "这周留出了阅读的时间", "pleasantRecords": [{"recordId": "5", "caption": "公园阅读"}],
                       "unpleasantRecords": [{"recordId": "4", "caption": "家中阅读"}],
                       "keep": {"text": "保留户外阅读", "recordId": "5", "templateId": None},
                       "adjust": {"text": "换一种环境试阅读", "recordId": "4", "templateId": None},
                       "attentionRecordIds": ["4"], "pinnedTemplateIds": ["7", "8"]}],
    "trials": [{"id": "1", "reviewId": "1", "source": {"recordId": "4", "caption": "家中阅读"},
                "description": "试一次在公园阅读", "relatedRecordIds": ["5"]}],
    "businessSettings": {"firstDayOfWeek": 2, "startOfDayShift": 0, "activityReminderDuration": 900, "activityReminderRecurrent": True},
}
expected = {
    "weekStart": timestamp("2026-10-05T00:00:00+08:00"), "cutoff": snapshot["createdAt"],
    "elapsedMs": 122400000, "recordedUnionMs": 7060000, "unrecordedMs": 115340000,
    "confirmedMs": 5400000, "pendingMs": 760000, "runningMs": 900000, "confirmedEventCount": 4,
    "confirmedByType": {"1": 1800000, "2": 1800000, "0": 900000, "4": 900000},
    "happiness": {"distribution": [0, 0, 0, 2, 0], "ratedCount": 2, "unratedCount": 2},
    "meaning": {"distribution": [0, 0, 1, 0, 1], "ratedCount": 2, "unratedCount": 2},
    "dailyConfirmedMs": [900000, 4500000], "combinedTagFilterIds": ["2"], "combinedTagFilterMs": 1800000,
}
for filename, data in [("snapshot-v1.json", snapshot), ("expected-v1.json", expected)]:
    (ROOT / filename).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
