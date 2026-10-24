"""Turns docs/user-guide.md into one HTML page, for the phone and for the release PDF.

    python3 docs/build_guide.py docs/user-guide.md build/guide/index.html [version]

Images stay relative (screenshots/...), so copy docs/screenshots next to the output. The
release workflow prints the page to PDF with headless Chrome.
"""
import re, sys, markdown

src, out = sys.argv[1], sys.argv[2]
version = sys.argv[3] if len(sys.argv) > 3 else None
md = open(src).read()
if version is None:
    version = re.search(r"covers \*\*version ([0-9.]+)\*\*", md).group(1)
# Title and intro come from the page header.
md = md.split("\n", 1)[1]
body = markdown.markdown(md, extensions=["tables", "toc"])
# Tables scroll inside their own box; screenshot tables become image grids.
def table(m):
    t = m.group(0)
    cls = "shots" if "<img" in t else "data"
    return f'<div class="tbl {cls}">{t}</div>'
body = re.sub(r"<table>.*?</table>", table, body, flags=re.S)
body = body.replace("<img ", '<img loading="lazy" ')

page = f"""<title>Office Tracker Guide</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,600&family=Source+Sans+3:wght@400;600;700&family=JetBrains+Mono:wght@500&display=swap">
<style>
/* Layout: one reading column like a phone manual; teal from the app icon, amber for breaks. */
:root {{
  --bg: #f5f9f8; --surface: #ffffff; --fg: #142021; --muted: #4c5d5e; --line: #d5e2e1;
  --accent: #00696e; --accent-soft: #d9efee; --amber: #8b5000;
  --display: "Fraunces", Georgia, serif; --body: "Source Sans 3", "Segoe UI", Roboto, sans-serif;
  --mono: "JetBrains Mono", ui-monospace, monospace;
}}
@media (prefers-color-scheme: dark) {{ :root:not([data-theme="light"]) {{
  --bg: #0d1515; --surface: #152020; --fg: #dde6e5; --muted: #a3b5b4; --line: #2a3a3a;
  --accent: #4fd8de; --accent-soft: #163536; --amber: #ffb86e; color-scheme: dark; }} }}
:root[data-theme="dark"] {{
  --bg: #0d1515; --surface: #152020; --fg: #dde6e5; --muted: #a3b5b4; --line: #2a3a3a;
  --accent: #4fd8de; --accent-soft: #163536; --amber: #ffb86e; color-scheme: dark; }}
body {{ background: var(--bg); color: var(--fg); font: 17px/1.6 var(--body); }}
.wrap {{ max-width: 720px; margin: 0 auto; padding-block: 24px 64px;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); }}
header {{ padding-block: 8px 20px; border-bottom: 1px solid var(--line); margin-bottom: 8px; }}
.eyebrow {{ font: 600 12px/1 var(--body); letter-spacing: .12em; text-transform: uppercase; color: var(--accent); }}
h1 {{ font: 600 clamp(32px, 8vw, 46px)/1.1 var(--display); margin: 10px 0 12px; text-wrap: balance; }}
h2 {{ font: 600 26px/1.25 var(--display); margin: 48px 0 12px; padding-top: 16px; border-top: 1px solid var(--line); text-wrap: balance; scroll-margin-top: 16px; }}
p, li {{ max-width: 65ch; }}
a {{ color: var(--accent); }}
a:focus-visible {{ outline: 2px solid var(--accent); outline-offset: 2px; }}
strong {{ font-weight: 700; }}
code {{ font: 500 .88em var(--mono); background: var(--accent-soft); padding: 1px 5px; border-radius: 4px; overflow-wrap: anywhere; }}
hr {{ border: 0; border-top: 1px solid var(--line); margin: 24px 0; }}
ol, ul {{ padding-left: 1.3em; }}
li + li {{ margin-top: 4px; }}
/* Contents list as a two-column index on wider screens. */
h2#contents + ol, p:has(> strong:only-child) + ol {{ columns: 2 260px; column-gap: 24px; }}
.tbl {{ overflow-x: auto; margin: 16px 0; }}
.tbl table {{ border-collapse: collapse; width: 100%; font-size: 15px; }}
.data th, .data td {{ text-align: left; vertical-align: top; padding: 8px 10px; border-bottom: 1px solid var(--line); }}
.data th {{ font-weight: 700; color: var(--muted); font-size: 13px; letter-spacing: .04em; text-transform: uppercase; }}
.data td {{ min-width: 120px; }}
.shots table {{ table-layout: fixed; }}
.shots th {{ font-size: 13px; font-weight: 600; color: var(--muted); padding: 0 6px 6px; text-align: left; }}
.shots td {{ padding: 0 6px; vertical-align: top; }}
img {{ display: block; max-width: 100%; height: auto; border-radius: 14px; border: 1px solid var(--line); background: var(--surface); }}
p > img {{ max-width: min(100%, 340px); margin: 8px 0; }}
.shots img {{ width: 100%; }}
footer {{ margin-top: 56px; color: var(--muted); font-size: 14px; }}
@media print {{
  @page {{ size: A4; margin: 14mm 14mm 16mm; }}
  * {{ print-color-adjust: exact; -webkit-print-color-adjust: exact; }}
  :root {{ --bg: #ffffff; --surface: #ffffff; --fg: #142021; --muted: #4c5d5e; --line: #d5e2e1;
    --accent: #00696e; --accent-soft: #e6f3f2; color-scheme: light; }}
  body {{ font-size: 10.5pt; line-height: 1.45; }}
  .wrap {{ max-width: none; padding: 0; }}
  h1 {{ font-size: 28pt; }}
  h2 {{ font-size: 16pt; margin-top: 18pt; break-after: avoid; }}
  h2 + * {{ break-before: avoid; }}
  p > img {{ max-width: 52mm; }}
  .shots table {{ max-width: 150mm; }}
  img, tr, .tbl.data {{ break-inside: avoid; }}
  a {{ color: inherit; text-decoration: none; }}
}}
</style>
<div class="wrap">
<header>
  <div class="eyebrow">Version {version} · How to use</div>
  <h1>Office Tracker user guide</h1>
</header>
{body}
<footer>Office Tracker {version}. Screenshots: the real app on an Android 14 emulator with demo data.</footer>
</div>
"""
open(out, "w").write(page)
