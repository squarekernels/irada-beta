from pathlib import Path
from math import pow

from PIL import Image, ImageDraw
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / "design" / "brand" / "irada"
MOCKUPS = ROOT / "design" / "ui-mockups"
TMP = ROOT / "tmp" / "brand-manual"
OUT = BRAND / "manual"
TMP.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)

PURPLE = "4B32F4"
INK = "11121A"
MUTED = "56596B"
LIGHT_CANVAS = "F7F7FC"
LIGHT_SURFACE = "FFFFFF"
LIGHT_SUBTLE = "EFEEF8"
LIGHT_OUTLINE = "D8D7E5"
DARK_CANVAS = "0D1120"
DARK_SURFACE = "171C2E"
DARK_RAISED = "20263A"
DARK_TEXT = "F7F7FB"
DARK_MUTED = "BCC0CF"
DARK_OUTLINE = "3B425B"
LAVENDER = "A99EFF"


def set_cell_fill(cell, color):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), color)


def set_cell_margins(cell, top=80, start=120, bottom=80, end=120):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for margin, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{margin}"))
        if node is None:
            node = OxmlElement(f"w:{margin}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_cell_border(cell, color="D9D9D9", size="6"):
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = tc_pr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tc_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        element = borders.find(qn(f"w:{edge}"))
        if element is None:
            element = OxmlElement(f"w:{edge}")
            borders.append(element)
        element.set(qn("w:val"), "single")
        element.set(qn("w:sz"), size)
        element.set(qn("w:color"), color)


def set_repeat_table_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def shade_run(run, color, bold=False, size=None):
    run.font.color.rgb = RGBColor.from_string(color)
    run.bold = bold
    if size:
        run.font.size = Pt(size)


def add_hyperlink(paragraph, text, url, color=PURPLE):
    part = paragraph.part
    rel_id = part.relate_to(url, "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink", is_external=True)
    hyperlink = OxmlElement("w:hyperlink")
    hyperlink.set(qn("r:id"), rel_id)
    run = OxmlElement("w:r")
    r_pr = OxmlElement("w:rPr")
    c = OxmlElement("w:color")
    c.set(qn("w:val"), color)
    r_pr.append(c)
    u = OxmlElement("w:u")
    u.set(qn("w:val"), "single")
    r_pr.append(u)
    run.append(r_pr)
    t = OxmlElement("w:t")
    t.text = text
    run.append(t)
    hyperlink.append(run)
    paragraph._p.append(hyperlink)


def keep_with_next(paragraph):
    paragraph.paragraph_format.keep_with_next = True


def add_page_number(paragraph):
    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = paragraph.add_run()
    fld_char1 = OxmlElement("w:fldChar")
    fld_char1.set(qn("w:fldCharType"), "begin")
    instr_text = OxmlElement("w:instrText")
    instr_text.set(qn("xml:space"), "preserve")
    instr_text.text = " PAGE "
    fld_char2 = OxmlElement("w:fldChar")
    fld_char2.set(qn("w:fldCharType"), "end")
    run._r.append(fld_char1)
    run._r.append(instr_text)
    run._r.append(fld_char2)


def rgb_tuple(hex_value):
    return tuple(int(hex_value[i:i+2], 16) for i in (0, 2, 4))


def luminance(hex_value):
    values = []
    for component in rgb_tuple(hex_value):
        c = component / 255.0
        values.append(c / 12.92 if c <= 0.04045 else pow((c + 0.055) / 1.055, 2.4))
    return 0.2126 * values[0] + 0.7152 * values[1] + 0.0722 * values[2]


def contrast(fg, bg):
    a, b = luminance(fg), luminance(bg)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


def normalize_accent(source, destination, add_exact_icon=False):
    image = Image.open(source).convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            hi = max(r, g, b)
            lo = min(r, g, b)
            saturation = 0 if hi == 0 else (hi - lo) / hi
            blue_or_purple = b > r * 1.06 and b > g * 1.12
            if a and hi > 120 and saturation > 0.45 and blue_or_purple:
                pixels[x, y] = (*rgb_tuple(PURPLE), a)
    if add_exact_icon:
        launcher = Image.open(BRAND / "irada-android-launcher-1024.png").convert("RGBA")
        launcher = launcher.resize((64, 64), Image.Resampling.LANCZOS)
        image.alpha_composite(launcher, (324, 116))
    image.convert("RGB").save(destination, quality=96)


normalize_accent(MOCKUPS / "irada-android-screens.png", MOCKUPS / "irada-android-screens.png")
normalize_accent(MOCKUPS / "irada-ios-screens.png", MOCKUPS / "irada-ios-screens.png")
normalize_accent(MOCKUPS / "irada-system-states.png", MOCKUPS / "irada-system-states.png", add_exact_icon=True)


def make_logo_lockup():
    canvas = Image.new("RGB", (1600, 620), "#FFFFFF")
    draw = ImageDraw.Draw(canvas)
    symbol = Image.open(BRAND / "irada-symbol-1024.png").convert("RGBA")
    symbol.thumbnail((420, 420), Image.Resampling.LANCZOS)
    canvas.paste(symbol, (110, 95), symbol)
    draw.text((610, 205), "Irada", fill=rgb_tuple(INK), font=None)
    # The wordmark SVG remains the editable source. This bitmap is only a document preview.
    path = TMP / "logo-preview.png"
    canvas.save(path)
    return path


def make_clearspace():
    canvas = Image.new("RGB", (1200, 620), "#FFFFFF")
    draw = ImageDraw.Draw(canvas)
    symbol = Image.open(BRAND / "irada-symbol-1024.png").convert("RGBA")
    symbol.thumbnail((350, 350), Image.Resampling.LANCZOS)
    x, y = 425, 135
    canvas.paste(symbol, (x, y), symbol)
    draw.rounded_rectangle((340, 50, 860, 570), radius=20, outline=rgb_tuple(PURPLE), width=4)
    draw.text((350, 70), "1x recommended clear space", fill=rgb_tuple(MUTED))
    draw.line((340, 110, 425, 110), fill=rgb_tuple(PURPLE), width=4)
    draw.text((365, 88), "x", fill=rgb_tuple(PURPLE))
    path = TMP / "clearspace.png"
    canvas.save(path)
    return path


LOGO_PREVIEW = make_logo_lockup()
CLEARSPACE = make_clearspace()


doc = Document()
section = doc.sections[0]
section.page_width = Inches(8.5)
section.page_height = Inches(11)
section.top_margin = Inches(0.68)
section.bottom_margin = Inches(0.62)
section.left_margin = Inches(0.72)
section.right_margin = Inches(0.72)

styles = doc.styles
styles["Normal"].font.name = "Arial"
styles["Normal"]._element.rPr.rFonts.set(qn("w:eastAsia"), "Arial")
styles["Normal"].font.size = Pt(10.3)
styles["Normal"].font.color.rgb = RGBColor.from_string(INK)
styles["Normal"].paragraph_format.space_after = Pt(6)
styles["Normal"].paragraph_format.line_spacing = 1.12

title_style = styles["Title"]
title_style.font.name = "Arial"
title_style.font.size = Pt(30)
title_style.font.bold = True
title_style.font.color.rgb = RGBColor(0, 0, 0)
title_style.paragraph_format.space_after = Pt(12)

for style_name, size, after in (("Heading 1", 20, 10), ("Heading 2", 13, 6), ("Heading 3", 11, 4)):
    style = styles[style_name]
    style.font.name = "Arial"
    style.font.size = Pt(size)
    style.font.bold = True
    style.font.color.rgb = RGBColor(0, 0, 0)
    style.paragraph_format.space_before = Pt(10)
    style.paragraph_format.space_after = Pt(after)
    style.paragraph_format.keep_with_next = True

for sec in doc.sections:
    footer = sec.footer.paragraphs[0]
    footer.text = "Irada brand manual   |   November 2026   |   "
    footer.style = styles["Normal"]
    for run in footer.runs:
        run.font.size = Pt(8)
        run.font.color.rgb = RGBColor.from_string(MUTED)
    add_page_number(footer)


def add_title(text):
    return doc.add_paragraph(text, style="Title")


def add_heading(text, level=1):
    return doc.add_heading(text, level=level)


def add_body(text, bold_lead=None):
    p = doc.add_paragraph()
    if bold_lead and text.startswith(bold_lead):
        r = p.add_run(bold_lead)
        r.bold = True
        p.add_run(text[len(bold_lead):])
    else:
        p.add_run(text)
    return p


def add_bullets(items):
    for item in items:
        p = doc.add_paragraph(item, style="List Bullet")
        p.paragraph_format.space_after = Pt(3)


def add_table(headers, rows, widths=None, header_color=INK):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    header = table.rows[0]
    set_repeat_table_header(header)
    for index, value in enumerate(headers):
        cell = header.cells[index]
        cell.text = value
        set_cell_fill(cell, header_color)
        set_cell_margins(cell)
        set_cell_border(cell)
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        for p in cell.paragraphs:
            for run in p.runs:
                run.font.color.rgb = RGBColor(255, 255, 255)
                run.bold = True
                run.font.size = Pt(9)
    for row_index, values in enumerate(rows):
        cells = table.add_row().cells
        for index, value in enumerate(values):
            cell = cells[index]
            cell.text = str(value)
            set_cell_fill(cell, "FFFFFF" if row_index % 2 == 0 else "F7F7FC")
            set_cell_margins(cell)
            set_cell_border(cell)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            for p in cell.paragraphs:
                p.paragraph_format.space_after = Pt(0)
                for run in p.runs:
                    run.font.size = Pt(8.0)
    if widths:
        for row in table.rows:
            for index, width in enumerate(widths):
                row.cells[index].width = Inches(width)
    doc.add_paragraph().paragraph_format.space_after = Pt(1)
    return table


def page_break():
    doc.add_page_break()


# Cover
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(18)
p.add_run().add_picture(str(BRAND / "irada-android-launcher-1024.png"), width=Inches(1.55))
doc.add_paragraph().paragraph_format.space_after = Pt(12)
title = add_title("Irada Brand Manual")
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
subtitle = doc.add_paragraph("Identity and product interface guidance")
subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
subtitle.runs[0].font.size = Pt(15)
subtitle.runs[0].font.color.rgb = RGBColor.from_string(MUTED)
intro = doc.add_paragraph()
intro.alignment = WD_ALIGN_PARAGRAPH.CENTER
intro.paragraph_format.space_before = Pt(18)
intro.paragraph_format.left_indent = Inches(0.65)
intro.paragraph_format.right_indent = Inches(0.65)
intro.add_run(
    "Irada turns an unstructured brain dump into a plan the user reviews and approves. "
    "The identity should feel calm, deliberate, capable, and human. Purple #4B32F4 is the locked core color, "
    "and Irada is the only app name."
)
meta = doc.add_paragraph("Version 1.0   |   18 November 2026")
meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
meta.paragraph_format.space_before = Pt(26)
meta.runs[0].font.size = Pt(9)
meta.runs[0].font.color.rgb = RGBColor.from_string(MUTED)

page_break()

# Brand core
add_title("Brand Core")
add_heading("Purpose", 1)
add_body(
    "Irada helps people move from mental overload to a realistic task plan without taking control away from them. "
    "AI output is always a proposal. Nothing is stored or scheduled before explicit approval."
)
add_heading("Personality", 1)
add_table(
    ["Trait", "How it appears", "Avoid"],
    [
        ("Calm", "Unhurried spacing, direct language, stable layouts", "Urgency theatre, alarmist color, noisy motion"),
        ("Deliberate", "Review before save, visible reasons, clear actions", "Automatic commitments or hidden changes"),
        ("Capable", "Strong hierarchy, concise labels, useful defaults", "Cute-but-vague copy or decorative complexity"),
        ("Human", "Plain language, preserved user words, honest uncertainty", "AI jargon, confidence scores, robot imagery"),
        ("Native", "Platform controls and familiar navigation", "A single cross-platform skin that ignores conventions"),
    ],
    widths=[1.0, 2.8, 2.8],
)
add_heading("Voice", 1)
add_bullets([
    "Say what happened and what the user can do next.",
    "Use Suggested for AI-added values and explain the source in ordinary language.",
    "Use active, specific actions such as Approve 3 tasks, Edit input, and Undo deletion.",
    "Never imply a reminder will fire when notifications are disabled.",
])
add_heading("Naming", 1)
add_body(
    "Use Irada in the app, stores, support, documentation, and marketing. Do not use PocketBuddy, IradaApp, "
    "or an alternate product name. The main destination is Tasks; Inbox may describe the mental model in internal "
    "design discussions but should not become a second destination."
)

page_break()

# Logo
add_title("Logo System")
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.add_run().add_picture(str(BRAND / "irada-wordmark-horizontal-1024x400.png"), width=Inches(4.7))
add_body(
    "The continuous loop holds an unstructured thought and resolves into an upright terminal: thought becoming "
    "intent through review and approval. Use the symbol alone for the app icon and compact product moments; use the "
    "horizontal wordmark when the name needs to be introduced."
)
add_heading("Source and editability", 1)
add_table(
    ["Asset", "Use", "Status"],
    [
        ("irada-symbol.svg", "Primary purple symbol", "Genuine editable vector"),
        ("irada-symbol-mono.svg", "One-color production", "Genuine editable vector"),
        ("irada-wordmark-horizontal.svg", "Symbol with live-text wordmark", "Editable vector plus live text"),
        ("irada-android-launcher.svg", "Launcher composition reference", "Editable vector preview; adaptive layers still required"),
        ("PNG exports", "Documents, previews, raster delivery", "Raster only; never label as SVG or editable vector"),
    ],
    widths=[1.8, 2.5, 2.3],
)
add_heading("Clear space and minimum size", 1)
add_bullets([
    "Recommended clear space is one symbol stroke width on every side; never use less than half a stroke width.",
    "Digital symbol minimum: 24 px high; use 32 px or larger when the mark is not inside an app icon.",
    "Print symbol minimum: 6 mm high. Horizontal wordmark minimum: 120 px or 30 mm wide.",
    "App icons use the platform-required sizes, generated from a 1024 px master with the symbol optically centered.",
])

page_break()

# Color
add_title("Color System")
add_body(
    "Core brand purple is locked at #4B32F4. Use it as a flat primary action and identity color. Do not substitute "
    "the former bright blue. Dark-mode text and small icons use a lighter lavender where the core purple does not "
    "provide enough contrast against dark surfaces."
)
add_heading("Light and dark palettes", 1)
palette_rows = [
    ("Brand primary", "#4B32F4", "#4B32F4", "Filled actions and identity"),
    ("Accessible accent", "#4B32F4", "#A99EFF", "Links, small icons, focus"),
    ("Canvas", "#F7F7FC", "#0D1120", "Screen background"),
    ("Surface", "#FFFFFF", "#171C2E", "Cards and sheets"),
    ("Raised surface", "#EFEEF8", "#20263A", "Grouped controls"),
    ("Primary text", "#11121A", "#F7F7FB", "Headings and body"),
    ("Secondary text", "#56596B", "#BCC0CF", "Supporting copy"),
    ("Outline", "#D8D7E5", "#3B425B", "Dividers; pair with shape or spacing"),
    ("Danger", "#B42318", "#FF8A82", "Destructive and overdue"),
    ("Warning", "#8A4B00", "#FFC46B", "Permission and offline warnings"),
    ("Success", "#137A50", "#65D6A5", "Completion and success"),
]
table = add_table(["Token", "Light", "Dark", "Use"], palette_rows, widths=[1.35, 1.1, 1.1, 3.05], header_color=PURPLE)
for row, (_, light_value, dark_value, _) in zip(table.rows[1:], palette_rows):
    for cell, value in ((row.cells[1], light_value), (row.cells[2], dark_value)):
        set_cell_fill(cell, value[1:])
        fg = "FFFFFF" if contrast("FFFFFF", value[1:]) >= 3 else INK
        for run in cell.paragraphs[0].runs:
            run.font.color.rgb = RGBColor.from_string(fg)
            run.bold = True

page_break()

# Contrast
add_title("Contrast Checks")
add_body(
    "These ratios are mathematical checks for the exact foreground and background values shown. They do not, by "
    "themselves, establish accessibility compliance. Final evaluation must also cover text size and weight, focus and "
    "pressed states, non-text boundaries, color-independent meaning, screen-reader semantics, motion, zoom, device "
    "rendering, and implementation fidelity."
)
contrast_pairs = [
    ("White on brand purple", "FFFFFF", PURPLE, "Primary button labels"),
    ("Brand purple on white", PURPLE, "FFFFFF", "Links and selected text in light mode"),
    ("Primary text on light canvas", INK, LIGHT_CANVAS, "Default light-mode text"),
    ("Secondary text on white", MUTED, "FFFFFF", "Supporting light-mode text"),
    ("Brand purple on light tint", PURPLE, "ECE9FF", "Suggested badge text"),
    ("Dark primary text on canvas", DARK_TEXT, DARK_CANVAS, "Default dark-mode text"),
    ("Dark secondary text on surface", DARK_MUTED, DARK_SURFACE, "Supporting dark-mode text"),
    ("Brand purple on dark canvas", PURPLE, DARK_CANVAS, "Do not use for small text"),
    ("Light lavender on dark surface", LAVENDER, DARK_SURFACE, "Dark-mode links, icons, focus"),
    ("Danger on white", "B42318", "FFFFFF", "Light destructive text"),
    ("Dark danger on surface", "FF8A82", DARK_SURFACE, "Dark destructive text"),
    ("Warning on white", "8A4B00", "FFFFFF", "Light warning text"),
    ("Dark warning on surface", "FFC46B", DARK_SURFACE, "Dark warning text"),
    ("Success on white", "137A50", "FFFFFF", "Light success text"),
    ("Dark success on surface", "65D6A5", DARK_SURFACE, "Dark success text"),
]
rows = []
for name, fg, bg, usage in contrast_pairs:
    rows.append((name, f"#{fg} / #{bg}", f"{contrast(fg, bg):.2f}:1", usage))
add_table(["Pair", "Values", "Ratio", "Intended use"], rows, widths=[2.0, 1.6, 0.8, 2.2], header_color=INK)
add_body(
    "Boundary note: #D8D7E5 on white is 1.42:1, and #3B425B on #171C2E is 1.70:1. These subtle outlines "
    "must not be the sole cue for controls or grouping. Use shape, spacing, labels, native semantics, and visible state."
)

page_break()

# Typography
add_title("Typography")
add_body(
    "Keep the identity consistent without fighting native platform behavior. Inter carries the brand in the wordmark, "
    "marketing, and cross-platform documents. Product UI uses each platform's system typeface so text scaling, language "
    "support, and native metrics remain reliable."
)
add_table(
    ["Context", "Typeface", "Weights", "License and handling"],
    [
        ("Identity and communications", "Inter", "400, 500, 600, 700", "SIL Open Font License 1.1; may be embedded and redistributed under its terms"),
        ("iOS product UI", "SF Pro system font", "System text styles", "Apple license; use through system APIs and do not bundle the downloaded font"),
        ("Android product UI", "Roboto system font", "400, 500, 700", "Current Google Fonts distribution is SIL Open Font License 1.1"),
    ],
    widths=[1.6, 1.4, 1.2, 2.6],
)
add_heading("Sources and licenses", 1)
p = doc.add_paragraph("Inter source and license: ")
add_hyperlink(p, "rsms Inter repository", "https://github.com/rsms/inter")
p.add_run(" and ")
add_hyperlink(p, "OFL 1.1 text", "https://github.com/rsms/inter/blob/master/LICENSE.txt")
p = doc.add_paragraph("SF Pro source and Apple license: ")
add_hyperlink(p, "Apple Fonts", "https://developer.apple.com/fonts/")
p = doc.add_paragraph("Roboto source and license: ")
add_hyperlink(p, "Google Fonts Roboto", "https://fonts.google.com/specimen/Roboto")
p.add_run(" and ")
add_hyperlink(p, "current Google Fonts metadata", "https://github.com/google/fonts/blob/main/ofl/roboto/METADATA.pb")
add_heading("Product type roles", 1)
add_table(
    ["Role", "iOS", "Android", "Guidance"],
    [
        ("Screen title", "Large Title or Title 1", "Headline Large", "Semibold or platform equivalent"),
        ("Section title", "Headline", "Title Medium", "Short, task-oriented labels"),
        ("Body", "Body", "Body Large", "Default reading text; allow scaling"),
        ("Supporting", "Subheadline or Footnote", "Body Medium", "Never rely on low contrast or tiny type"),
        ("Button", "Headline", "Label Large", "Sentence case; concise verb-led action"),
    ],
    widths=[1.2, 1.4, 1.5, 2.7],
)
add_bullets([
    "Do not hard-code point sizes in the native app; use Dynamic Type and scalable Android text styles.",
    "Do not use italics for hierarchy. Reserve italic only for conventional editorial emphasis.",
    "Use tabular numerals where time or counts align, and test long localized strings before release.",
])

# Icon, art, spacing
icons_title = add_title("Icons Artwork and Spacing")
icons_title.paragraph_format.page_break_before = True
add_heading("Icon rules", 1)
add_bullets([
    "Soft Outline is the selected family: 24 by 24 viewBox, 1.8-unit stroke, rounded terminals and joins.",
    "Use currentColor so semantic tokens control light, dark, selected, warning, and destructive states.",
    "Keep glyphs at 24 dp and Android touch targets at least 48 dp; follow native minimum targets on iOS.",
    "Do not mix the Soft Outline family with the Precise or Bold explorations.",
    "Pair ambiguous actions with visible text. Expose selected state through native semantics and more than color alone.",
    "Decorative icons beside equivalent text should not receive duplicate screen-reader announcements.",
])
add_heading("Artwork direction", 1)
add_table(
    ["Use", "Avoid"],
    [
        ("Continuous paths, loops, ordered clusters, and calm card compositions", "Robots, brains, magic clouds, or chat-bubble-first imagery"),
        ("Flat color, restrained depth, readable hierarchy, native surfaces", "Glossy gradients, heavy glow, glass effects that reduce clarity"),
        ("Human-scale moments of review, choice, and completion", "Automation imagery that suggests the app acts without approval"),
        ("Purple as a focused signal with neutral space around it", "Flooding every surface with purple or using color as the only state cue"),
    ],
    widths=[3.25, 3.25],
)
add_heading("Spacing and shape", 1)
add_table(
    ["Token", "Value", "Typical use"],
    [
        ("Space 1", "4 pt", "Fine optical adjustment"),
        ("Space 2", "8 pt", "Icon to label; compact gaps"),
        ("Space 3", "12 pt", "Control interiors"),
        ("Space 4", "16 pt", "Default card padding and row gaps"),
        ("Space 6", "24 pt", "Section separation"),
        ("Space 8", "32 pt", "Screen-group separation"),
        ("Space 12", "48 pt", "Hero or focused-flow separation"),
        ("Radius small", "12 pt", "Fields and compact controls"),
        ("Radius medium", "16 pt", "Cards and primary controls"),
        ("Radius large", "24 pt", "Prominent sheets and large buttons"),
    ],
    widths=[1.5, 1.1, 4.0],
)
add_body(
    "Treat these as cross-platform design tokens, then map them to native components rather than forcing identical "
    "pixel geometry on both platforms."
)

page_break()

# Product application
add_title("Identity in the Product")
add_heading("Home and Inbox", 1)
add_body(
    "The product has one true home: Tasks. It performs the role of an inbox without adding a second destination. "
    "Use purple for the primary capture action and current filter; keep overdue red semantic and task content neutral."
)
add_heading("Swipe and Triage", 1)
add_body(
    "Treat triage as a contextual gesture, not a permanent tab. A swipe may reveal Reschedule and Delete, with text "
    "labels and native undo behavior. Purple may identify the constructive action; destructive red remains reserved for delete."
)
add_heading("AI suggestions", 1)
add_body(
    "Present AI output as a proposed batch behind a clear approval boundary. Use a Suggested badge and a human-readable "
    "reason such as Based on tomorrow. In light mode use #4B32F4 on #ECE9FF; in dark mode use #A99EFF on #20263A. "
    "Never communicate AI provenance by color alone."
)
add_heading("Focus mode", 1)
add_body(
    "If approved for a later release, Focus uses the same task title, timer, and pause controls with minimal chrome. "
    "Purple marks the active session; reduced-motion settings remove pulsing or animated path effects. Focus is not part "
    "of the current v1 scope and should not appear as a permanent destination yet."
)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.add_run().add_picture(str(MOCKUPS / "irada-system-states.png"), width=Inches(5.4))
caption = doc.add_paragraph("Irada system-state examples with the selected symbol and purple identity")
caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
caption.runs[0].italic = True
caption.runs[0].font.size = Pt(8)
caption.runs[0].font.color.rgb = RGBColor.from_string(MUTED)

page_break()

# Mockups
add_title("Platform Examples")
add_body(
    "These raster review sheets show the identity applied to current iOS and Android concepts. They are mockups, not "
    "component specifications. The original SVG logo files remain the editable vector sources; embedded screen previews "
    "and exported PNGs are raster images."
)
add_heading("Android", 1)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.add_run().add_picture(str(MOCKUPS / "irada-android-screens.png"), width=Inches(7.0))
add_heading("iOS", 1)
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.add_run().add_picture(str(MOCKUPS / "irada-ios-screens.png"), width=Inches(7.0))

page_break()

# Correct incorrect
add_title("Correct and Incorrect Use")
add_table(
    ["Correct", "Incorrect"],
    [
        ("Use Irada as the only product name.", "Use PocketBuddy, IradaApp, or alternate names."),
        ("Use #4B32F4 as the locked core purple.", "Restore the former blue or recolor the mark casually."),
        ("Use the supplied loop symbol without modification.", "Add a dot, arrowhead, gradient, shadow, or internal decoration."),
        ("Use white text on #4B32F4 for primary filled actions.", "Use #11121A text on #4B32F4; the pair measures only 2.72:1."),
        ("Use #A99EFF for small accent text on dark surfaces.", "Use #4B32F4 for small text on #0D1120; the pair measures 2.74:1."),
        ("Label ambiguous icons and expose native semantics.", "Use color-only or icon-only meaning for review, triage, or status."),
        ("Preserve native iOS and Android behavior.", "Force both platforms into identical navigation and control behavior."),
        ("Describe AI output as Suggested and require approval.", "Present AI recommendations as completed or automatically saved work."),
        ("Use flat color and restrained depth.", "Add glossy gradients, heavy glow, or low-contrast decoration."),
        ("Keep clear space around the symbol.", "Crop, stretch, rotate, outline, or place the symbol on a busy image."),
    ],
    widths=[3.25, 3.25],
    header_color=PURPLE,
)
add_heading("Production checklist", 1)
add_bullets([
    "Use the SVG source for vector production and the PNGs only when raster output is required.",
    "Verify light and dark states on real devices, including pressed, focused, selected, disabled, and error states.",
    "Test VoiceOver, TalkBack, large text, reduced motion, RTL navigation, and color-independent status cues.",
    "Recalculate contrast after implementation; screenshots and design-tool values can differ from rendered pixels.",
])

page_break()

# Approvals
add_title("Decisions Requiring Approval")
add_body(
    "The app name Irada, core purple #4B32F4, selected continuous-loop symbol, and replacement of the former blue and "
    "checkmark identity are locked by the product owner. The following implementation decisions remain proposed."
)
add_table(
    ["Decision", "Recommendation", "Why approval is needed"],
    [
        ("Dark-mode accent", "Approve #A99EFF for small text, icons, and focus rings", "Core purple is only 2.74:1 on #0D1120; the lighter token is 8.07:1"),
        ("Typography split", "Inter for identity; SF Pro and Roboto through native system APIs", "Confirms native typography takes priority over identical cross-platform metrics"),
        ("Clear space", "Make one stroke width the standard; half a stroke is the absolute floor", "Strengthens the existing asset guidance"),
        ("Minimum sizes", "24 px or 6 mm symbol; 120 px or 30 mm wordmark", "These production thresholds were not previously specified"),
        ("Home naming", "Use Tasks in the product, not Inbox", "Keeps one true home and avoids messaging expectations"),
        ("Swipe triage", "Contextual Reschedule and Delete actions only", "Confirms triage is a gesture pattern, not a destination"),
        ("Focus mode", "Keep outside v1 and treat as a later module", "Focus is exploratory in the current icon notes and absent from v1 scope"),
        ("Artwork policy", "Flat purple; no decorative gradients in production UI", "The current mockups contain tonal rendering that should not become a token"),
    ],
    widths=[1.45, 2.65, 2.55],
    header_color=INK,
)
add_heading("Reference basis", 1)
add_body(
    "This manual is derived from DESIGN.md, the Irada brand asset README, the selected Soft Outline icon documentation, "
    "the supplied logo files, and the current iOS, Android, and system-state mockups. It intentionally does not add "
    "accounts, projects, tags, recurrence, collaboration, or other features excluded from the v1 brief."
)


output_path = OUT / "Irada-Brand-Manual.docx"
doc.save(output_path)
print(output_path)
