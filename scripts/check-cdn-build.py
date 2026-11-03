#!/usr/bin/env python3
"""Validate a CDN frontend build without contacting private APIs."""
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'dist')
base = sys.argv[2] if len(sys.argv) > 2 else 'https://static.sparkden.cn/shimo/releases/cdn-20261026/'
html = (root / 'index.html').read_text()
assert 'src="/shimo/theme-init.js"' in html
assert f'src="{base}assets/index-' in html
assert f'href="{base}assets/index-' in html
urls = set()
for file in [root / 'index.html', *root.glob('assets/*.css')]:
    text = file.read_text()
    urls.update(re.findall(re.escape(base) + r'[^\s"\'<>\)]+', text))
    assert '/shimo/api/' not in text
for url in urls:
    relative = url.removeprefix(base)
    assert relative.startswith('assets/'), url
    assert (root / relative).is_file(), url
for file in root.glob('assets/*.js'):
    text = file.read_text()
    assert base + 'api/' not in text
    assert base + 'share/' not in text
    assert base + 'introduction' not in text
    assert base + 'assets/brand/shimo-logo.png' in text
assert len(urls) > 250, len(urls)
print(f'CDN build verified: {len(urls)} HTML/CSS URLs exist; app routes remain same-origin')
