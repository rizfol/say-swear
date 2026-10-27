"""Build the Stage 2 documentation PDF; does not run the game or alter diagrams.

Documentation-only dependencies: reportlab, svglib. Run from any working directory.
The report includes other maintained Markdown documents and embeds existing SVGs.
"""

from __future__ import annotations

import argparse
from html import escape
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Frame, LongTable, NextPageTemplate,
    PageBreak, PageTemplate, Paragraph, Spacer, TableStyle,
)
from reportlab.platypus.tableofcontents import TableOfContents
from svglib.svglib import svg2rlg


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/stage2-report.md"
REPORT_DATE = re.search(
    r"Status date:\s*([^\n(]+)", SOURCE.read_text(encoding="utf-8")
).group(1).strip()
INK = colors.HexColor("#182b3b")
ACCENT = colors.HexColor("#176c82")
MUTED = colors.HexColor("#536472")
PALE = colors.HexColor("#edf3f6")
MARGIN = 45


def normalize(text: str) -> str:
    for old, new in {
        "\u2010": "-", "\u2011": "-", "\u2013": "-", "\u2014": " - ",
        "\u2018": "'", "\u2019": "'", "\u201c": '"', "\u201d": '"',
        "\u2192": " -> ", "\u00d7": " x ", "\u2264": "<=", "\u2265": ">=",
        "\u2026": "...", "\u00a0": " ",
    }.items():
        text = text.replace(old, new)
    return text


def expand(path: Path, base_level: int = 0, before: str | None = None) -> list[tuple[str, Path]]:
    """Preserve each line's origin for correct repository-relative links."""
    source = path.read_text(encoding="utf-8")
    if before:
        if before not in source:
            raise ValueError(f"Missing include boundary in {path.name}: {before}")
        source = source.split(before, 1)[0]
    result = []
    for line in source.splitlines():
        match = re.fullmatch(r"<!-- report-include: ([^|]+?)(?: \| before=(.+))? -->", line)
        if match:
            include = (path.parent / match.group(1).strip()).resolve()
            if not include.is_relative_to(ROOT / "docs"):
                raise ValueError("Report includes must remain in docs/")
            result += expand(include, 1, match.group(2))
            continue
        heading = re.match(r"^(#{1,6}) (.*)$", line)
        if base_level and heading:
            if len(heading.group(1)) == 1:
                continue
            line = "#" * min(6, len(heading.group(1)) + base_level) + " " + heading.group(2)
        result.append((normalize(line), path))
    return result


def inline(text: str, origin: Path) -> str:
    fragments: list[str] = []

    def token(markup: str) -> str:
        fragments.append(markup)
        return f"\x00{len(fragments) - 1}\x00"

    def link(match: re.Match) -> str:
        label, target = match.group(1), match.group(2).strip("<>")
        if not re.match(r"^[a-z]+://", target, re.I) and not target.startswith("#"):
            pathname, separator, anchor = target.partition("#")
            resolved = (origin.parent / pathname).resolve()
            if not resolved.is_relative_to(ROOT):
                raise ValueError(f"Link leaves repository: {target}")
            if not resolved.exists():
                raise FileNotFoundError(f"Missing report link: {target} from {origin.name}")
            target = resolved.relative_to(ROOT).as_posix() + (separator + anchor if separator else "")
        elif target.startswith("#"):
            # Document-local heading fragments are printed in their source context.
            target = origin.relative_to(ROOT).as_posix() + target
        return token(f'<a href="{escape(target, quote=True)}" color="#176c82">{escape(label)}</a>')

    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", link, text)
    text = re.sub(r"`([^`]+)`", lambda m: token('<font name="Courier" size="8">' + escape(m.group(1)) + '</font>'), text)
    text = escape(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"(?<!\*)\*([^*]+)\*(?!\*)", r"<i>\1</i>", text)
    text = text.replace("&lt;br&gt;", "<br/>").replace("&lt;br/&gt;", "<br/>")
    for i, fragment in enumerate(fragments):
        text = text.replace(f"\x00{i}\x00", fragment)
    return text


def register_fonts() -> None:
    import reportlab
    font_dir = Path(reportlab.__file__).parent / "fonts"
    for name, filename in [("Vera", "Vera.ttf"), ("Vera-Bold", "VeraBd.ttf"),
                           ("Vera-Italic", "VeraIt.ttf"), ("Vera-BoldItalic", "VeraBI.ttf")]:
        pdfmetrics.registerFont(TTFont(name, str(font_dir / filename)))
    pdfmetrics.registerFontFamily("Vera", normal="Vera", bold="Vera-Bold",
                                 italic="Vera-Italic", boldItalic="Vera-BoldItalic")


def make_styles():
    styles = getSampleStyleSheet()
    common = dict(fontName="Vera", textColor=INK, splitLongWords=True, spaceAfter=5)
    styles.add(ParagraphStyle("Body", fontSize=9.1, leading=12.6, **common))
    styles.add(ParagraphStyle("SmallBody", fontSize=7.8, leading=10.7, **common))
    styles.add(ParagraphStyle("BulletBody", fontSize=9, leading=13, leftIndent=10,
                              firstLineIndent=-8, **common))
    styles.add(ParagraphStyle("ReportTitle", fontName="Vera-Bold", fontSize=29,
                              leading=35, textColor=INK, spaceAfter=14))
    styles.add(ParagraphStyle("Section", fontName="Vera-Bold", fontSize=17, leading=22,
                              textColor=ACCENT, spaceBefore=5, spaceAfter=13, keepWithNext=True))
    styles.add(ParagraphStyle("Subsection", fontName="Vera-Bold", fontSize=11, leading=15,
                              textColor=INK, spaceBefore=12, spaceAfter=7, keepWithNext=True))
    styles.add(ParagraphStyle("Minor", fontName="Vera-Bold", fontSize=9.5, leading=13,
                              textColor=INK, spaceBefore=8, spaceAfter=5, keepWithNext=True))
    styles.add(ParagraphStyle("TableHeader", fontName="Vera-Bold", fontSize=7.5,
                              leading=10, textColor=INK, spaceAfter=0, splitLongWords=True))
    styles.add(ParagraphStyle("TableCell", fontName="Vera", fontSize=7.4,
                              leading=10.2, textColor=INK, spaceAfter=0, splitLongWords=True))
    return styles


def page_furniture(canvas, doc):
    w, h = canvas._pagesize
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#cedae0"))
    canvas.line(MARGIN, h - 31, w - MARGIN, h - 31)
    canvas.setFont("Vera", 7.4)
    canvas.setFillColor(MUTED)
    canvas.drawString(MARGIN, h - 23, "SAY SWEAR  /  STAGE 2 IMPLEMENTATION REPORT")
    canvas.drawString(MARGIN, 25, f"Review draft - pending evidence is explicitly marked  |  {REPORT_DATE}")
    canvas.drawRightString(w - MARGIN, 25, str(doc.page))
    canvas.restoreState()


class ReportDoc(BaseDocTemplate):
    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name in {"Section", "Subsection"}:
            label = flowable.getPlainText()
            key = f"section-{id(flowable)}"
            self.canv.bookmarkPage(key)
            if flowable.style.name == "Section":
                self.canv.addOutlineEntry(label, key, level=0, closed=False)
                self.notify("TOCEntry", (0, label, self.page, key))


def table(lines: list[tuple[str, Path]], width: float, styles):
    rows = []
    for line, origin in lines:
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if all(re.fullmatch(r":?-+:?", cell) for cell in cells):
            continue
        style = styles["TableHeader"] if not rows else styles["TableCell"]
        rows.append([Paragraph(inline(cell, origin), style) for cell in cells])
    cols = len(rows[0])
    if any(len(row) != cols for row in rows):
        raise ValueError("Markdown table has inconsistent columns")
    weights = {2: [0.27, 0.73], 3: [0.23, 0.38, 0.39],
               4: [0.20, 0.30, 0.25, 0.25], 5: [0.17, 0.27, 0.26, 0.15, 0.15],
               6: [0.08, 0.20, 0.09, 0.11, 0.36, 0.16]}.get(cols, [1 / cols] * cols)
    if cols == 5 and "Interface" in lines[0][0] and "Video" in lines[0][0]:
        weights = [0.18, 0.30, 0.30, 0.11, 0.11]
    elif cols == 5 and "Stage 1 reference" in lines[0][0]:
        weights = [0.23, 0.22, 0.28, 0.16, 0.11]
    grid = LongTable(rows, colWidths=[width * value for value in weights], repeatRows=1,
                     hAlign="LEFT", splitByRow=1)
    grid.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BACKGROUND", (0, 0), (-1, 0), PALE),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f8fafb")]),
        ("LINEBELOW", (0, 0), (-1, 0), 0.7, colors.HexColor("#aebfc8")),
        ("LINEBELOW", (0, 1), (-1, -1), 0.3, colors.HexColor("#dee6ea")),
        ("LEFTPADDING", (0, 0), (-1, -1), 6), ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6), ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    return grid


def build(output: Path):
    register_fonts()
    styles = make_styles()
    doc = ReportDoc(str(output), pagesize=A4, leftMargin=MARGIN, rightMargin=MARGIN,
                    topMargin=48, bottomMargin=46, title="Say Swear - Stage 2 Implementation Report",
                    author="", subject="EECS 3311 Fall 2026 - public Stage 2 implementation and pending evidence")
    width = A4[0] - 2 * MARGIN
    doc.addPageTemplates(PageTemplate(id="body", frames=[Frame(
        MARGIN, 46, width, A4[1] - 94, leftPadding=0, rightPadding=0,
        topPadding=0, bottomPadding=0)], onPage=page_furniture))
    lines = expand(SOURCE)
    story = []
    diagrams = []
    i = 0
    seen_section = False
    toc = TableOfContents()
    toc.levelStyles = [ParagraphStyle("Contents", fontName="Vera", fontSize=9,
                                      leading=14, textColor=INK, spaceBefore=5)]
    while i < len(lines):
        line, origin = lines[i]
        if not line.strip():
            i += 1
            continue
        diagram = re.fullmatch(r"<!-- report-diagram: ([^|]+?) \| (.+) -->", line)
        if diagram:
            diagrams.append(((origin.parent / diagram.group(1)).resolve(), diagram.group(2)))
            i += 1
            continue
        if line.startswith("<!--"):
            i += 1
            continue
        heading = re.match(r"^(#{1,6}) (.*)$", line)
        if heading:
            level, label = len(heading.group(1)), heading.group(2)
            if level == 2:
                if not seen_section:
                    story += [Spacer(1, 17), Paragraph("Contents", styles["Subsection"]), toc]
                story.append(PageBreak())
                seen_section = True
            style = "ReportTitle" if level == 1 else "Section" if level == 2 else "Subsection" if level == 3 else "Minor"
            story.append(Paragraph(inline(label, origin), styles[style]))
            i += 1
            continue
        if line.startswith("|"):
            block = []
            while i < len(lines) and lines[i][0].startswith("|"):
                block.append(lines[i]); i += 1
            story += [table(block, width, styles), Spacer(1, 9)]
            continue
        if line.startswith("```"):
            i += 1
            while i < len(lines) and not lines[i][0].startswith("```"):
                story.append(Paragraph('<font name="Courier" size="8">' + escape(lines[i][0]) + '</font>', styles["Body"]))
                i += 1
            i += 1
            continue
        bullet = re.match(r"^(?:[-*] |\d+\. )(.*)$", line)
        if bullet:
            value = bullet.group(1).replace("[x]", "[DONE]").replace("[ ]", "[PENDING]")
            story.append(Paragraph("- " + inline(value, origin), styles["BulletBody"]))
            i += 1
            continue
        block = [line]
        i += 1
        while i < len(lines) and lines[i][0].strip() and not re.match(r"^(#|\||[-*] |\d+\. |```|<!--)", lines[i][0]):
            block.append(lines[i][0]); i += 1
        paragraph = " ".join(block)
        if paragraph.startswith("!["):
            paragraph = "Diagram source: " + paragraph[1:] + " (embedded in the appendix)."
        story.append(Paragraph(inline(paragraph, origin), styles["Body"]))
    for index, (path, caption) in enumerate(diagrams):
        root = ET.parse(path).getroot()
        svg_width, svg_height = float(root.attrib["width"]), float(root.attrib["height"])
        # Preserve approximately 10pt member text from the UMLet 14px originals.
        scale = 0.72
        page_width = max(A4[0], svg_width * scale + 2 * MARGIN)
        page_height = max(A4[1], svg_height * scale + 150)
        template_id = f"diagram-{index}"
        doc.addPageTemplates(PageTemplate(id=template_id, pagesize=(page_width, page_height),
            frames=[Frame(MARGIN, 46, page_width - 2 * MARGIN, page_height - 94,
                          leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)],
            onPage=page_furniture))
        story += [NextPageTemplate(template_id), PageBreak(), Paragraph(caption, styles["Section"])]
        story.append(Paragraph("Existing UMLet SVG | large-format vector page | zoom to inspect. Source: "
                               + escape(path.relative_to(ROOT).as_posix()), styles["Body"]))
        drawing = svg2rlg(str(path))
        if drawing is None:
            raise ValueError(f"Cannot load SVG: {path.name}")
        # svglib converts SVG CSS pixels to PDF points at 0.75 pt/px.
        # Scale the converted drawing, rather than allocating unscaled SVG units.
        converted_scale = svg_width * scale / drawing.width
        drawing.scale(converted_scale, converted_scale)
        drawing.width *= converted_scale
        drawing.height *= converted_scale
        def embed_fonts(node):
            for child in getattr(node, "contents", []):
                if hasattr(child, "fontName"):
                    old_font = child.fontName
                    child.fontName = "Vera-Bold" if "Bold" in old_font else "Vera"
                embed_fonts(child)
        embed_fonts(drawing)
        story.append(drawing)
    doc.multiBuild(story)
    print(f"Created {output.relative_to(ROOT) if output.is_relative_to(ROOT) else output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "stage2-report.pdf")
    args = parser.parse_args()
    build(args.output.resolve())
