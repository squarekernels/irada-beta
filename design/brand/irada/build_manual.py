"""Build the editable Markdown guide and vector-based sharing PDF."""
from pathlib import Path
import json
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.lib.utils import simpleSplit

ROOT = Path(__file__).resolve().parent
PURPLE = '#4B32F4'
def luminance(h):
    channels = [int(h[i:i+2],16)/255 for i in (1,3,5)]
    linear = [v/12.92 if v <= .04045 else ((v+.055)/1.055)**2.4 for v in channels]
    return sum(a*b for a,b in zip(linear,(.2126,.7152,.0722)))
def ratio(a,b):
    x,y = sorted((luminance(a),luminance(b)))
    return (y+.05)/(x+.05)

PAIRS = [
 ('Button label','#FFFFFF',PURPLE), ('Light primary text','#11121A','#FFFFFF'),
 ('Light secondary text','#5C5B6D','#FFFFFF'), ('Light card secondary text','#5C5B6D','#F6F4FF'),
 ('Light accent text',PURPLE,'#FFFFFF'), ('Dark primary text','#F5F3FF','#11121A'),
 ('Dark card secondary text','#BCB8CE','#20212F'), ('Dark accent text','#C4B5FD','#11121A'),
 ('Selected label','#E9E2FF','#302754'), ('Light destructive text','#B4233B','#FFFFFF'),
 ('Dark destructive text','#FF9DAE','#20212F'), ('Light warning text','#7A4A00','#FFF4D6'),
 ('Dark warning text','#FFD58A','#3A2C13'), ('Light success text','#146C4D','#FFFFFF'),
 ('Dark success text','#83DDB8','#20212F'), ('Avoid: base purple text on dark',PURPLE,'#11121A'),
 ('Avoid: white text on lavender','#FFFFFF','#C4B5FD')]
measurements = [dict(role=n,foreground=f,background=b,ratio=round(ratio(f,b),2),normal_text=ratio(f,b)>=4.5) for n,f,b in PAIRS]
(ROOT/'contrast-report.json').write_text(json.dumps(measurements,indent=2)+'\n')

PAGES = [
 ('01 / Foundation','Intention, made clear.',[
 ('Final identity','Irada. Thought to Intent / Balanced Loop symbol. Primary purple #4B32F4. Soft Outline icon family. These decisions are approved.'),
 ('Personality','Calm, capable, warm and respectful. A thoughtful guide with a protective quality, never a boss. Reduce mental load; keep the person in control.'),
 ('Product promise','Describe what needs doing, review the proposed plan, and approve it before anything is saved or scheduled. Preserve drafts. Explain uncertainty. Suggest realistic dates without invented urgency.'),
 ('Voice','Use direct, reassuring language: "Review your plan", "Suggested", "Your draft is saved", "Retry". Avoid "AI knows best", guilt, streak pressure and claims that work is already done before approval.'),
 ('Approval status','Locked: name, core symbol, purple and icon style. Proposed for approval: supporting palette, Inter wordmark at 600, size/spacing rules and illustration system. Focus mode and any standalone triage destination require product approval; they are not part of the current v1 scope.')]),
 ('02 / Logo','A thought becomes intent.',[
 ('Construction','Use the existing editable SVG path. The loop holds a thought; the upright terminal suggests deliberate progress. Preserve its proportions, counter, rounded terminals and stroke relationship.'),
 ('Clear space','Define S as the visible symbol height, excluding SVG canvas padding. Keep at least 0.125 S clear on every side. For a horizontal lockup, use the same clearance and a symbol-to-wordmark gap of 0.20 S. This proposed rule supersedes the earlier half-stroke note.'),
 ('Minimum sizes','Proposed: standalone symbol at least 24dp/pt in UI or 8mm in print; horizontal lockup at least 112px wide or 30mm in print. At smaller sizes use the symbol alone. Confirm on real devices and print proofs.'),
 ('Correct','Purple symbol on white. White symbol on #11121A or on #4B32F4. Brand icon belongs in onboarding, app identity and launcher artwork. Keep action labels and recognizable completion controls.'),
 ('Incorrect','Do not stretch, rotate, add a dot or arrowhead, fill the inner counter, outline the ribbon, add glow, or use blue as the primary accent. Do not treat the app symbol as an unlabeled completion state. Symbol is not mirrored for RTL.')]),
 ('03 / Color','Purple leads. Contrast guides.',[
 ('Light palette','Primary #4B32F4; canvas #FFFFFF; card #F6F4FF; primary text #11121A; secondary text #5C5B6D; control border #777184; selection fill #E6E0FF with #4B32F4 text.'),
 ('Dark palette','Primary action #4B32F4 with #FFFFFF text; canvas #11121A; card #20212F; primary text #F5F3FF; secondary #BCB8CE; link/outline accent #C4B5FD; control border #817A95; selection #302754 with #E9E2FF text.'),
 ('Semantic colors','Light: error #B4233B on white, warning #7A4A00 on #FFF4D6, success #146C4D on white. Dark: error #FF9DAE on #20212F, warning #FFD58A on #3A2C13, success #83DDB8 on #20212F.'),
 ('Usage','Use base purple for filled actions on both themes; lavender is a supporting dark-theme foreground, not a replacement brand hue. Pair all status colors with text or shape. Do not use opacity to create untested text colors.'),
 ('Measurement limits','Ratios use WCAG relative luminance for opaque sRGB colors. Normal text target: 4.5:1; large text and essential non-text components: 3:1. These calculations do not establish app accessibility compliance; real font sizes, focus states, system controls, images and screen-reader behavior need testing.')]),
 ('04 / Type and craft','Quietly recognizable.',[
 ('Typography proposal','Brand wordmark: Inter SemiBold 600, title case Irada; retain live text in editable source. Marketing/body: Inter 400; labels 500; headings 600. Avoid heavy italics, condensed cuts and artificially stretched letters.'),
 ('Native UI','iOS: system SF Pro with Dynamic Type. Android: system Roboto with scalable sp. Suggested starting hierarchy: title 28/34, section 20/26, body 16/24, label 14/20. Treat these as starting values; allow wrapping and system scaling.'),
 ('Font sources and licenses','Inter: https://rsms.me/inter/ ; SIL OFL 1.1 at https://github.com/rsms/inter/blob/master/LICENSE.txt . Roboto: https://fonts.google.com/specimen/Roboto ; current upstream OFL 1.1 at https://github.com/googlefonts/roboto-3-classic/blob/main/OFL.txt . Retain license/copyright notices when bundling font files; follow reserved-name conditions for modifications.'),
 ('Apple font use','SF Pro source and license: https://developer.apple.com/fonts/ . Apple proprietary terms apply; use the platform system font for native Apple UI. Do not redistribute SF Pro as a cross-platform brand font. Reference: https://developer.apple.com/design/human-interface-guidelines/typography . Guide PDF uses Helvetica for portable review; final Inter typesetting remains proposed.'),
 ('Spacing and icons','4-unit spacing grid: 4, 8, 12, 16, 24, 32, 48. Start with 16-unit screen gutters and card padding, 12-unit gaps, 16-unit card radius. Soft Outline: 24x24 grid, 1.8-unit stroke, round caps/joins; 48dp Android and 44pt iOS minimum interactive targets. Never stretch glyphs to fill targets.'),
 ('Artwork direction','Flat, restrained loops and paths echo the logo: loose input becomes organized lanes. Generous negative space, no decorative AI sparks, glowing brains, gears or lightbulbs. Motion should clarify a transition and respect Reduce Motion. Use factual task examples rather than decorative dashboards.')]),
 ('05 / In context','One identity, four moments.',[
 ('Home / Inbox','Visible destination: Tasks. Show Overdue, Today, Upcoming and Anytime, followed by collapsed completed tasks. Small Irada identity, quiet cards, purple Add tasks action. A separate inbox is outside v1. Search and filter remain clearly labeled.'),
 ('Swipe / Triage','Use swipe as a shortcut within the task list, with equivalent labeled actions in task detail. Reschedule and Delete remain explicit; show Undo after routine changes. No swipe-only interaction or new triage tab. A dedicated triage mode needs approval.'),
 ('AI suggestions','Label values Suggested; keep original input reachable and uncertainty readable. Purple Approve tasks action marks the trust boundary. Do not imply suggestions are already saved. Keep reasons in plain language, not confidence percentages.'),
 ('Focus mode - concept only','A quiet single-task card, the Soft Outline focus brackets, lavender secondary controls and a purple Start focus action. Pause and sound use action labels. No countdown pressure, scores or streaks. Focus is an illustrative extension requiring scope approval.'),
 ('State and symbol rules','Selected state uses a tonal container plus native selected semantics and a visible label. Brand app icon replaces the onboarding checkmark; actual completed tasks keep conventional completion indicators. For voting, suggestions, triage, history, tips and mind map, preserve visible labels because their symbols can be ambiguous.')]),
 ('06 / Contrast audit','Measured pairs, not visual guesses.',[]),
 ('07 / Use and handoff','Consistency earns trust.',[
 ('Correct use','Use approved loop geometry, one purple filled primary action, light/dark tokens, native text scaling, visible Suggested labels and explicit approval. Keep task content readable and offline drafts recoverable.'),
 ('Incorrect use','Avoid distorted logos, purple body text on dark cards, white labels on lavender fills, unlabeled icon-only triage, permanent AI chat, and illustrations that imply automatic scheduling. Never use color alone to show overdue or completion.'),
 ('Mockup status','Updated iOS, Android and system-state raster boards use Irada purple and app-symbol branding. Their pixels are illustrative and may include generated text or color variance. Exact colors, task semantics and accessible interactions follow this manual and DESIGN.md, not sampled raster pixels.'),
 ('Review decisions','Approve or revise: supporting neutrals/lavender/semantic colors; Inter 600 wordmark; minimum size and clear-space rules; proposed spacing and artwork direction. Product decision separately required for Focus mode and a standalone triage destination. No additional approval needed for the already selected name, logo direction, purple or Soft Outline.'),
 ('Editable handoff','The guide source is brand-manual.md with a reproducible build_manual.py. Existing symbol and wordmark SVGs are editable vectors, not raster wrappers. The sharing PDF contains selectable text and vector logo examples; raster mockup boards remain PNG. No commit, push or publication has been performed.')])]

md=['# Irada brand manual','Version 1.0 | 18 November 2026','Approved foundation; supporting decisions marked as proposals.']
for kicker,title,blocks in PAGES:
    md += ['\n## '+kicker+' - '+title]
    for h,t in blocks: md+=['\n### '+h,t]
    if kicker.startswith('06'):
        md+=['| Role | Foreground | Background | Ratio | Normal text 4.5:1 |','|---|---|---|---|---|']
        for m in measurements: md.append(f"| {m['role']} | {m['foreground']} | {m['background']} | {m['ratio']:.2f}:1 | {'Meets numerical target' if m['normal_text'] else 'Below target'} |")
md+=['\n## Review assets','![Updated iOS mockups](../../ui-mockups/irada-ios-screens.png)','![Updated Android mockups](../../ui-mockups/irada-android-screens.png)','![Updated system states](../../ui-mockups/irada-system-states.png)']
(ROOT/'brand-manual.md').write_text('\n\n'.join(md)+'\n')

W,H=595,842
c=canvas.Canvas(str(ROOT/'Irada-brand-manual.pdf'),pagesize=(W,H))
c.setTitle('Irada | Brand manual')
def text(x,y,t,size=10,color='#11121A',font='Helvetica'):
    c.setFillColor(HexColor(color));c.setFont(font,size);c.drawString(x,y,t)
def logo(x,y,size,color=PURPLE,stretch=1):
    c.saveState();c.translate(x,y);c.scale(size/512*stretch,-size/512)
    p=c.beginPath();p.moveTo(368,72);p.curveTo(372,157,348,232,298,287);p.curveTo(253,337,191,363,130,356);p.curveTo(76,350,49,309,61,262);p.curveTo(75,209,133,179,186,197);p.curveTo(233,213,270,249,301,284)
    c.setStrokeColor(HexColor(color));c.setLineWidth(82);c.setLineCap(1);c.setLineJoin(1);c.drawPath(p);c.restoreState()
for index,(kicker,title,blocks) in enumerate(PAGES):
    c.setFillColor(HexColor('#FFFFFF'));c.rect(0,0,W,H,fill=1,stroke=0)
    c.setFillColor(HexColor(PURPLE));c.rect(36,H-45,30,4,fill=1,stroke=0)
    text(36,H-76,kicker,11,PURPLE,'Helvetica-Bold');text(36,H-116,title,27,font='Helvetica-Bold')
    y=H-153
    if index==0:
        logo(48,y,150);text(215,y-85,'Irada',56,font='Helvetica-Bold');y-=165
    if index==1:
        for x,bg,fg in [(36,'#FFFFFF',PURPLE),(210,'#11121A','#FFFFFF'),(384,PURPLE,'#FFFFFF')]:
            c.setFillColor(HexColor(bg));c.roundRect(x,y-100,156,100,12,fill=1,stroke=0);logo(x+37,y-4,100,fg)
        y-=125
    if index==2:
        for j,col in enumerate([PURPLE,'#C4B5FD','#11121A','#20212F','#F6F4FF','#FFFFFF']):
            c.setFillColor(HexColor(col));c.roundRect(36+j*87,y-35,78,35,5,fill=1,stroke=0);text(36+j*87,y-49,col,9)
        y-=75
    if index==4:
        examples=[('Tasks','Today','Buy groceries','Add tasks'),('Review','Swipe shortcut','Call Sara','Reschedule'),('Suggested plan','Suggested','Renew passport','Approve tasks'),('Focus concept','One thing at a time','Read a chapter','Start focus')]
        for j,(heading,meta,task,action) in enumerate(examples):
            x=36+j*132
            c.setFillColor(HexColor('#11121A'));c.roundRect(x,y-126,123,126,10,fill=1,stroke=0)
            text(x+9,y-20,heading,10,'#F5F3FF','Helvetica-Bold');text(x+9,y-39,meta,7,'#C4B5FD')
            c.setFillColor(HexColor('#20212F'));c.roundRect(x+8,y-79,107,29,5,fill=1,stroke=0)
            text(x+13,y-67,task,8,'#F5F3FF')
            c.setFillColor(HexColor(PURPLE));c.roundRect(x+8,y-113,107,23,6,fill=1,stroke=0)
            text(x+14,y-105,action,8,'#FFFFFF','Helvetica-Bold')
        y-=150
    if index==6:
        for x,caption,stretch in [(36,'Correct / original proportions',1),(305,'Incorrect / stretched mark',1.6)]:
            logo(x+15,y,75,PURPLE,stretch);text(x,y-83,caption,9)
        y-=111
    if index==5:
        text(36,y,'Opaque sRGB pairs / relative luminance / ratio rounded to 2 decimals',9);y-=28
        for m in measurements:
            c.setFillColor(HexColor(m['background']));c.roundRect(36,y-25,140,28,4,fill=1,stroke=0)
            text(44,y-14,'Aa '+m['foreground'],10,m['foreground']);text(190,y-4,m['role'],9)
            text(190,y-18,f"on {m['background']}   {m['ratio']:.2f}:1   "+('>= 4.5' if m['normal_text'] else '< 4.5'),9,'#5C5B6D');y-=32
        text(36,y-12,'Numerical targets only. Not a claim of accessibility compliance.',9);y-=40
    for h,t in blocks:
        text(36,y,h,11,PURPLE,'Helvetica-Bold');y-=17
        lines=simpleSplit(t,'Helvetica',10,W-72)
        for line in lines:text(36,y,line,10);y-=14
        y-=14
    c.setStrokeColor(HexColor('#E6E0FF'));c.line(36,42,W-36,42)
    text(36,25,'Irada / Brand manual / Supporting proposals await approval',8,'#5C5B6D');text(W-48,25,str(index+1),9)
    if y<50: raise RuntimeError(f'Page {index+1} overflow: {y}')
    c.showPage()
c.save()
print('Built brand-manual.md, Irada-brand-manual.pdf and contrast-report.json')
for m in measurements: print(m['role'],m['ratio'])
