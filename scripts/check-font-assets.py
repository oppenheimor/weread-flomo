"""Validate generated fonts without installing fonttools.
Optional: python3 scripts/check-font-assets.py --text '需要展示的书名'
"""
import argparse
import hashlib
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--text', default='拾墨我的书架自律学习投资经济世界心理人生')
args = parser.parse_args()
css = (root / 'web/fonts.css').read_text()
covered = set()
needed = []
count = 0
for rule in css.split('@font-face')[1:]:
    filename = re.search(r'quanzi-kai/([^"\)]+)', rule)[1]
    match = re.fullmatch(r'(core|common-\d+|rare-\d+)\.([a-f0-9]{12})\.woff2', filename)
    assert match, f'Not content-addressed WOFF2: {filename}'
    data = (root / 'public/assets/fonts/quanzi-kai' / filename).read_bytes()
    assert data[:4] == b'wOF2', f'Invalid WOFF2: {filename}'
    assert hashlib.sha256(data).hexdigest().startswith(match[2]), f'Hash mismatch: {filename}'
    points = set()
    for item in re.search(r'unicode-range:\s*([^;]+)', rule)[1].split(','):
        span = item.strip().removeprefix('U+').split('-')
        points.update(range(int(span[0], 16), int(span[-1], 16) + 1))
    assert not points.intersection(covered), f'Overlapping ranges: {filename}'
    covered.update(points)
    if match[1] == 'core':
        assert len(data) < 20_000, 'Core font exceeds 20KB budget'
    elif match[1].startswith('common'):
        assert len(points) <= 64 and len(data) < 65_536, f'Common subset too large: {filename}'
    else:
        assert len(points) <= 128, f'Rare subset too large: {filename}'
    if points.intersection(map(ord, args.text)):
        needed.append(len(data))
    count += 1
assert len(covered) == 27547, f'Font coverage changed: {len(covered)}'
print(f'PASS: {count} WOFF2 subsets, {len(covered)} characters, valid hashes and size budgets')
print(f'Sample text: {len(needed)} requests, {sum(needed):,} font bytes')
