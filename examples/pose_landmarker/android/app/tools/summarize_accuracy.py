"""Summarize labeled debug telemetry; never treat unavailable samples as correct.

Input CSV columns: expected,actual,latency_ms (optional).
expected: SAFE or DANGER. actual: SAFE, WARNING, DANGER or UNAVAILABLE.
Label ground truth independently of the app output. Do not publish personal recordings.
"""
import argparse
import csv
import json


def summarize(rows):
    total = unavailable = false_alarm = missed = correct = 0
    safe = danger = 0
    for row in rows:
        expected, actual = row["expected"], row["actual"]
        if expected not in ("SAFE", "DANGER"):
            raise ValueError("Expected labels must be SAFE or DANGER")
        if actual not in ("SAFE", "WARNING", "DANGER", "UNAVAILABLE"):
            raise ValueError("Invalid actual state")
        total += 1
        safe += expected == "SAFE"
        danger += expected == "DANGER"
        unavailable += actual == "UNAVAILABLE"
        false_alarm += expected == "SAFE" and actual in ("WARNING", "DANGER")
        # WARNING is not enough to trigger the existing audible reminder policy.
        missed += expected == "DANGER" and actual != "DANGER"
        correct += expected == actual
    def rate(n, d):
        return round(n / d, 4) if d else None
    return dict(samples=total, exact_accuracy=rate(correct, total),
                unavailable_rate=rate(unavailable, total),
                false_alarm_rate=rate(false_alarm, safe),
                missed_reminder_rate=rate(missed, danger))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("csv")
    args = parser.parse_args()
    with open(args.csv, encoding="utf-8-sig", newline="") as source:
        print(json.dumps(summarize(csv.DictReader(source)), indent=2))
