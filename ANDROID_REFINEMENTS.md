# Android discovery and Arcade refinements

Status: approved by the owner for commit and push to the existing origin/main on November 18, 2026. Authorized by the owner's November 17, 2026 voice review. This is a scoped refinement of stages 1–8; stage 9 and iOS remain unstarted.

## Resulting behavior

- **Explore:** defaults to `year desc`, including Reset filters and fallback when no valid saved sort exists. An explicit saved sort still wins. API paging and search behavior remain intact.
- **Swipe:** supplied artwork uses Fit inside a bounded 360 dp neutral frame, preserving the entire image at any source aspect ratio. All three rating buttons use equal neutral outlined styling. The former permanent coral Like fill caused the apparent selected state; it was not a stuck saved rating. Gesture actions, local choices, snapshots and Undo retain their existing implementation.
- **Genie:** one recommendation is visible at a time. Next advances through the existing finite scored selection; the last pick offers an explicit restart and guidance to record more choices. The selected movie id survives screen recreation/navigation. If it leaves the recommendation set after a rating change, the view falls back to the first current pick. Next does not rate or dismiss a film. Scores still use genre ±1, director ±3, actor ±2; every saved id, including UNSEEN, is excluded. Up to three actual positive reasons remain visible.
- **Trivia:** a welcoming illustration and explicit Let's play precede the round. The saved Golden Ticket direction informs the notched question panel and editorial layout. Segmented progress, a prominent score/streak, animated question transitions, answer-color transitions, check/cross feedback and an animated encouraging reveal make the round feel more like a game. 50:50 is alongside the question counter. Auto-advance returns the scroll position to the next question; feedback scrolls into view. Pause hides the question. Five questions, 20 seconds per question, one 50:50, the original streak/points system, 4-second feedback, Keep reading, manual Next and replay remain in place. Compose animations follow system animation scaling.
- **Poster Puzzle:** drag a piece onto another to swap exactly those two, with the moving piece following the finger and a target outline. Releasing outside the board, cancelling, or dropping onto the same position does not count a move. Two-tap swapping remains available. On compact screens the page scrolls around the board so its controls and pieces remain reachable; drags inside the board move pieces. Sizes 3×3, 4×4 and 6×6 yield 9/16/36 pieces. Changing difficulty reshuffles the same supplied poster and resets board progress, moves, selection and reference. Next retains difficulty during the live process. A missing/invalid URL never becomes a puzzle; an artwork failure blocks moves and reference, with Retry and Next retained. No generated pattern is used as puzzle artwork.

## Design provenance

Reviewed README, branding decisions, exact palette/type and icon specifications, the Android plan, and the accessible chats **Lumio Change Requests** and **Design three trivia game options**. The latter confirms automatic progression and the owner's correction to put 50:50 near the question counter. No specific one of the three directions had been selected. This implementation adapts Golden Ticket because its paper, coral and ticket question card fit the approved Editorial identity; the owner approved the completed native adaptation on November 18, 2026.

- [Original editable comparison](../design/previews/sources/lumio-trivia-directions.html)
- [Standalone browser comparison](../design/previews/lumio-trivia-directions.html)
- [Preview usage and limitations](../design/previews/README.md)
- [Generated illustration provenance and prompts](../design/previews/assets/trivia/README.md)
- Native editable UI: `android/app/src/main/java/dev/master/lumio/ui/trivia/TriviaScreen.kt`
- Bundled decorative illustration: `android/app/src/main/res/drawable-nodpi/trivia_ticket.jpg`, copied from the preview's `ticket.jpg`. This is original generated decoration, not catalog content or approved production branding.

The inherited exploratory preview files and unrelated design assets were already present when this isolated worktree was created. They have been preserved. The original preview has not been rewritten to imply owner approval or native fidelity.

## Scope and delivery

English, Spanish, German and Portuguese resource keys and placeholders remain aligned. No catalog/trivia/poster downloads, persisted catalog, machine configuration, dependency upgrade or new service were introduced. Existing ratings/settings storage and downloaded-source attribution remain intact. Actual checks, screenshots and limitations are recorded in [Android validation](../validation/android.md).

The owner approved this completed work and explicitly requested commit and push to the existing origin on main on November 18, 2026. The baseline branch remains untouched. The other two Trivia directions remain exploratory; this approval does not authorize Stage 9, iOS, or unrelated branding work.