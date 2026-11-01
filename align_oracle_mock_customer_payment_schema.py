#!/usr/bin/env python3
"""Align Oracle mock bootstrap scripts (TEST-ONLY); no Git, Docker, or test commands.

Run at repository root:
    python align_oracle_mock_customer_payment_schema.py --dry-run
    python align_oracle_mock_customer_payment_schema.py
"""
from __future__ import annotations
import argparse
from pathlib import Path
import re
import sys

CUSTOMER = Path('docker/customer-oracle/001_customer_schema.sql')
PAYMENT = Path('docker/customer-oracle/002_payment_schema.sql')


def replace_once(s: str, old: str, new: str, label: str) -> str:
    count = s.count(old)
    if count == 0 and new in s:
        return s  # already applied
    if count != 1:
        raise ValueError(f'{label}: expected exactly one match; got {count}')
    return s.replace(old, new, 1)


def replace_regex_once(s: str, pattern: str, replacement: str, label: str) -> str:
    new, count = re.subn(pattern, replacement, s, count=0, flags=re.IGNORECASE | re.MULTILINE)
    if count != 1:
        raise ValueError(f'{label}: expected exactly one match; got {count}')
    return new


def transform_customer(s: str) -> str:
    s = replace_once(s, 'ETA   VARCHAR2(2 CHAR),', 'ETA   VARCHAR2(4 CHAR),', 'BKAUTC.ETA')
    s = replace_once(
        s,
        'CREATE TABLE REGIONAL_MOCK.BKNOM (\n    CTAB VARCHAR2(10 CHAR),\n    CACC VARCHAR2(30 CHAR),\n    MNT4 NUMBER(10)\n);',
        'CREATE TABLE REGIONAL_MOCK.BKNOM (\n    CTAB VARCHAR2(10 CHAR),\n    CACC VARCHAR2(30 CHAR),\n    LIB1 VARCHAR2(100 CHAR),\n    MNT1 VARCHAR2(50 CHAR),\n    MNT2 VARCHAR2(50 CHAR),\n    MNT4 NUMBER\n);',
        'BKNOM shared definition'
    )
    marker = "INSERT INTO REGIONAL_MOCK.BKNOM (CTAB, CACC, MNT4)\nVALUES ('098', 'SITE-CENT', 0);"
    s = replace_once(s, marker,
        marker + "\n\n-- Payment reference fixture previously inserted by 002_payment_schema.sql.\n"
        "INSERT INTO REGIONAL_MOCK.BKNOM (CTAB, CACC, MNT2)\nVALUES ('001', '00099', '19102026');",
        'BKNOM payment fixture')
    # Scope correction to the second customer block only.
    start = s.index('-- second customer')
    end = s.index('-- third customer', start)
    block = s[start:end]
    block = replace_once(block,
        "INSERT INTO REGIONAL_MOCK.BKTELCLI (CLI, NUM)\nVALUES ('0494223', '+237671063172');",
        "INSERT INTO REGIONAL_MOCK.BKTELCLI (CLI, NUM)\nVALUES ('0604667', '+237671063172');", 'second customer phone')
    block = replace_once(block,
        "INSERT INTO REGIONAL_MOCK.BKEMACLI (CLI, EMAIL)\nVALUES ('0494223', 'clarisse.pierre@gmail.com');",
        "INSERT INTO REGIONAL_MOCK.BKEMACLI (CLI, EMAIL)\nVALUES ('0604667', 'clarisse.pierre@gmail.com');", 'second customer email')
    s = s[:start] + block + s[end:]
    return s


def transform_payment(s: str) -> str:
    # These four statements belong to the shared customer schema, executed first.
    patterns = [
        (r'^CREATE TABLE REGIONAL_MOCK\.BKNOM\s*\([^;]*\);\s*\n', 'BKNOM duplicate'),
        (r"^INSERT INTO REGIONAL_MOCK\.BKNOM\s*\(CTAB\s*,\s*CACC\s*,\s*MNT2\)\s*VALUES\s*\('001'\s*,\s*'00099'\s*,\s*'19102026'\)\s*;\s*\n", 'BKNOM payment fixture duplicate'),
        (r"^INSERT INTO REGIONAL_MOCK\.BKNOM\s*\(CTAB\s*,\s*CACC\s*,\s*MNT4\)\s*VALUES\s*\('098'\s*,\s*'SITE-CENT'\s*,\s*0\)\s*;\s*\n", 'BKNOM mode fixture duplicate'),
        (r'^CREATE TABLE REGIONAL_MOCK\.BKAUTC\s*\([^;]*\);\s*\n', 'BKAUTC duplicate'),
    ]
    for pattern, label in patterns:
        if label == 'BKNOM duplicate' and not re.search(pattern, s, re.IGNORECASE | re.MULTILINE):
            # Already migrated: all four statements should be absent.
            if all(not re.search(p, s, re.IGNORECASE | re.MULTILINE) for p, _ in patterns):
                return s
        s = replace_regex_once(s, pattern, '', label)
    anchor = '-- R6.6.10 TEST-ONLY / REFERENCE_ONLY. Not a La Regionale Amplitude schema.'
    if anchor in s:
        s = s.replace(anchor, anchor + '\n-- BKNOM and BKAUTC are created and seeded by 001_customer_schema.sql.', 1)
    return s


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--dry-run', action='store_true', help='show planned changes; do not write')
    ap.add_argument('--root', type=Path, default=Path.cwd(), help='repository root')
    args = ap.parse_args()
    paths = [args.root / CUSTOMER, args.root / PAYMENT]
    for p in paths:
        if not p.is_file():
            raise FileNotFoundError(f'Missing {p}; run from the repository root or pass --root')
    originals = [p.read_text(encoding='utf-8-sig') for p in paths]
    transformed = [transform_customer(originals[0]), transform_payment(originals[1])]
    for p, before, after in zip(paths, originals, transformed):
        print(f'{"CHANGE" if before != after else "UNCHANGED"}: {p}')
    if args.dry_run:
        print('Dry run: no files written.')
        return 0
    # All transformations complete before writing either file.
    for p, before, after in zip(paths, originals, transformed):
        if before != after:
            p.write_text(after, encoding='utf-8', newline='\n')
    print('Completed. No Git, Docker, tests, or gates executed.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, FileNotFoundError) as e:
        print(f'ERROR: {e}', file=sys.stderr)
        sys.exit(1)
