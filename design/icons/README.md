# Irada icon family

**Selected family: Soft Outline.** Use the individual SVGs in `soft/` as the canonical Irada icons.

Open `overview.html` for the labeled review sheet. The five representative icons appear at actual 24px Android UI size on light and dark surfaces, with default and selected treatments. The complete selected family appears below.

## Directions

1. **Soft outline — selected:** 1.8-unit stroke, rounded terminals and joins. Calm, friendly, aligned with the continuous Irada logo.
2. **Precise geometry:** 1.65-unit stroke, square terminals and miter joins. More technical and structured.
3. **Confident stroke:** 2.5-unit stroke with rounded terminals. More prominent; dense symbols may require simplification at smaller sizes.

All three directories contain individual editable SVGs for all 18 glyphs (sound has separate on/off assets). Each has a 24×24 viewBox and uses currentColor. These are authored vectors with editable shapes and paths, with no embedded raster images or icon-internal text. The selected treatment is a UI container plus a small structural indicator; selected state is not communicated by color alone. Expose selected state through native accessibility semantics. SVG titles are documentation; assign the accessible label in the native UI as well. Decorative icons beside equivalent text should not receive duplicate announcements.

The overview is a style review, not a proposal to add five navigation destinations. DESIGN.md specifies one home task list and no separate inbox. Focus mode, mind map, voting, unread state, activity history, sound controls, and tips are exploratory additions rather than agreed v1 features.

## Labels and ambiguity

| Asset | Suggested accessible label | Ambiguity / usage |
| --- | --- | --- |
| inbox | Tasks | Tray can imply messages; call the destination Tasks, not Inbox. |
| triage | Review tasks | Opposing arrows can mean horizontal scrolling; retain visible Review text. |
| suggestions | Suggested plan | Lines and an emerging path can imply export; keep a visible Suggested label. |
| focus | Start focus | Brackets can imply scan or fullscreen; pair with Focus text. |
| settings | Settings | Sliders can mean filter; reserve funnel for filtering. |
| search | Search tasks | Conventional magnifier. |
| filter | Filter tasks | Expose active filters and count in text/semantics. |
| upvote | Helpful suggestion | Arrow can mean move up; require visible Helpful label. |
| downvote | Unhelpful suggestion | Arrow can mean move down; require visible feedback label. |
| unread | Mark unread | Envelope implies messaging; use only if an unread concept is introduced. |
| undo | Undo last action | Prefer the specific action, such as Undo deletion, when known. |
| history | Activity history | Clock with return arc can imply restore; retain History text. |
| back | Back | Mirror for RTL when it represents navigation. |
| sound-on | Mute sound | Label names the action; expose current sound state separately. |
| sound-off | Enable sound | Label names the action; expose current sound state separately. |
| pause | Pause focus | Specify what is being paused. |
| tips | Planning tips | Card can mean document; retain Tips text. |
| mind-map | Mind map | Nodes can imply sharing or hierarchy; retain visible label. |

## Implementation

Keep glyphs at 24dp and interactive targets at least 48dp in Android. Tune by visual mass rather than stretching individual icons to identical bounds. Use the same stroke system throughout a selected family. Selected navigation uses a tonal container, structural marker, visible label, and native selected semantics. Do not mix the three directions. Test with TalkBack, increased font size, and actual device rendering before adoption.

`generate.mjs` reproduces the selected Soft Outline SVG family and HTML sheet using Node.js with no external dependencies. The `precise/` and `bold/` folders retain earlier explorations for reference only. No app screens, navigation, or features have been changed.
