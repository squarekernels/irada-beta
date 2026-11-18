# App UX Brief

## Product overview

Design the UX for a native-first personal task app, implemented separately with native iOS and Android SDKs. Follow each platform's conventions for navigation, back behavior, notifications, accessibility, appearance, and system controls while keeping product behavior consistent.

## Product purpose

The app helps an individual turn an unstructured natural-language brain dump into a realistic personal task plan.

Core promise:

> Describe what needs doing, review the proposed plan, and approve it before anything is saved or scheduled.

The app is for individuals experiencing mental overload. It is not a team tool, project-management suite, or general-purpose AI chat assistant.

## Core UX principles

- The user remains in control.
- AI output is always a proposal, never an automatic action.
- Nothing is saved and no reminder is scheduled before explicit approval.
- Preserve the user's words during failures and interruptions.
- Prefer conservative recommendations over invented urgency.
- Make uncertainty visible without exposing confidence scores or AI jargon.
- Keep the experience task-first rather than chat-first.
- Use native, accessible controls on each platform.
- Ask the user follow-up questions only when a usable proposal is otherwise impossible.

## Primary loop

1. The user enters a natural-language brain dump.
2. The app sends it for AI processing.
3. The AI extracts actionable tasks and proposes relevant dates and reminders.
4. The app presents the proposal as an editable batch.
5. The user edits, removes, or adds tasks.
6. The user explicitly approves the batch.
7. Approved tasks are saved locally and eligible reminders are scheduled.
8. The user later completes, reschedules, edits, or deletes tasks.

## Main destinations

Use one true home: the task list.

Supporting screens and flows:

- First-run introduction
- Brain-dump capture
- AI processing state
- Batch review and approval
- Task detail and editing
- Settings

Capture and review are focused flows, not permanent tabs. Do not add dashboards, calendars, chat history, or a separate inbox.

## First-run experience

Show one concise introduction explaining:

- Describe what needs doing.
- Review the proposed plan.
- Nothing is saved until approval.
- AI processing requires an internet connection.
- Approved tasks remain on the device.

Then lead directly into capture.

Do not place account creation, notification permission, a tutorial carousel, sample tasks, or a paywall before the user experiences the core loop.

## Home and task discovery

Group open tasks into:

- Overdue
- Today
- Upcoming
- Anytime

Show completed tasks in a collapsed section below open tasks.

Within each section, sort by due date and then creation order. Provide search and a simple Open / Completed / All filter. Do not include configurable sorting.

Each task row should clearly communicate:

- Completion state
- Title
- Due date or Anytime
- Reminder status when relevant
- Overdue state using more than color alone

Use a prominent action to start a new brain dump.

## Task mechanics

A task contains only:

- Title
- Optional notes
- Optional due date
- Optional single reminder
- Status: open or completed

Support:

- Edit
- Complete and uncomplete
- Reschedule
- Delete

Overdue is derived from the date; it is not a separate status.

Tapping a task opens its detail screen. Its completion control works directly from the list. Platform-appropriate swipe actions may expose reschedule and delete as shortcuts.

Frequent changes happen immediately and show a brief Undo option. Confirm only irreversible bulk actions such as deleting all local data. Deleting a task must also cancel its pending notification.

## Capture experience

The primary input is typed natural language. Standard system keyboard dictation is supported, but the app does not include custom voice recording.

Example input:

> I need to renew my passport before my trip next month, buy groceries tomorrow, and call Sara sometime this week.

Preserve the input locally as a draft before beginning AI processing.

Only one pending capture batch exists in v1. If the user starts another capture while one exists, offer:

- Resume draft
- Discard and start over

## AI processing

After submission, show a focused, cancellable “Making your plan…” state.

The original input must remain safe locally.

On timeout, network failure, service failure, or invalid AI output:

- Explain the problem in plain language.
- Offer Retry.
- Offer Edit Input.
- Never discard the draft.
- Never create partial tasks.
- Never save tasks before review.

## Review and approval

The review screen is the app's central trust boundary.

Show:

- The original brain dump
- Every extracted task
- Proposed due dates
- Proposed reminder times
- Uncertainty or interpretation warnings
- The final approval action

Allow the user to:

- Edit any field
- Add a task
- Remove a proposed task
- Approve the complete batch

Label AI-added values as “Suggested.” Where useful, provide a brief human-readable reason, such as:

> Friday — based on “before the weekend.”

Do not display confidence percentages, model terminology, or raw reasoning.

Closing or navigating back from review preserves the original input and current edits as the single pending draft. If the user explicitly attempts to abandon edited content, offer Keep Editing or Discard.

## AI recommendation rules

- Explicit dates always take precedence.
- Resolve relative dates using the user's current locale and time zone.
- Suggest a due date only when the input provides reasonable context.
- Leave a task as Anytime when the context does not justify a deadline.
- Never invent urgency merely to complete a field.
- Treat reminders separately from due dates.
- Recommend a reminder only when it provides meaningful value.
- Default to one reminder at a sensible waking-hour time.
- Never propose a reminder in the past.
- If a calculated time has passed, move it to the next sensible future time and flag the change during review.
- Ask a follow-up question only when a usable proposal is impossible, such as “Remind me before the appointment” when no appointment date exists.

## Dates and time zones

Due dates are calendar dates rather than midnight timestamps. A date-only task remains due through that local day.

Reminder times use local wall-clock time. If the user changes time zones, ordinary due dates and reminder times remain anchored to their intended local date and time.

## Notifications

Request notification permission only when the user first approves a task containing a reminder.

If permission is denied:

- Still save the task.
- Preserve the reminder intent.
- Clearly state that notifications are disabled.
- Provide a route to the platform's system settings.
- Do not repeatedly request permission.
- Never imply that the reminder will fire.

## Offline experience

AI extraction requires an internet connection.

Previously approved tasks remain fully usable offline, including viewing, editing, completing, rescheduling, and deleting them.

If the user submits a brain dump while offline:

- Save it as the pending draft.
- Show “Needs internet to make a plan.”
- Provide a manual Retry action.
- Do not silently process it when connectivity returns.

On the first-ever offline launch:

- Explain that plan generation requires a connection.
- Allow access to the empty task list and Settings.
- Do not trap the user on an error screen.

## Settings

Keep Settings limited to:

- Notification permission and current status
- Default reminder time
- Appearance: System, Light, or Dark
- Privacy and local-data explanation
- Delete all local data

Persist tasks, the pending draft, and preferences across launches.

## Navigation

Use native navigation patterns on each platform.

- iOS should support standard navigation bars, Back, and swipe-back where appropriate.
- Android should support system Back and predictive-back behavior.
- Navigating normally should not lose work.
- Preserve unsaved drafts automatically.
- Ask for confirmation only when the user explicitly abandons or destroys work.
- Restore the user to a sensible location after interruptions or app relaunches.

## Accessibility and responsiveness

Treat accessibility as a release requirement.

Support:

- Screen-reader labels and logical focus order
- Dynamic or scalable text without losing essential content
- Sufficient contrast
- Status indicators that do not rely on color alone
- Reduced-motion preferences
- Platform-standard minimum touch targets
- Keyboard-safe layouts
- Clear loading, empty, error, disabled, offline, and permission-denied states
- Supported phone sizes and orientations on both platforms

Use adaptive or compatibility layouts on tablets, but do not design a dedicated tablet experience for v1.

## Visual direction

Use the supplied reference as mood inspiration only: dark, focused, energetic, and card-oriented.

Do not copy its weaknesses:

- Avoid excessive italics.
- Avoid low-contrast secondary text.
- Avoid ambiguous icon-only actions.
- Avoid persistent filter clutter.
- Avoid decorative effects that reduce readability.

Support both light and dark appearances. Prefer native typography and controls, clear hierarchy, restrained motion, and a calm sense of control.

## V1 scope guardrails

Do not introduce:

- Accounts
- Cloud sync or backup
- Apple Reminders or Google Tasks integration
- Calendar integration
- Collaboration
- Projects
- Tags
- Priorities
- Subtasks
- Dependencies
- Recurrence
- Attachments
- Custom voice recording
- Widgets
- Share-sheet capture
- Email forwarding
- Gamification
- Analytics-driven recommendations
- Conversational AI after approval
- Monetization or paywalls

A feature belongs in v1 only if removing it would break natural-language capture, trustworthy review and approval, local task management, or reminders.

## UX specification deliverables

The complete mobile UX specification should cover:

- Information architecture
- Screen inventory
- Primary user flows
- Navigation and back behavior
- Screen-level layouts and hierarchy
- Interaction details
- Recommendation presentation
- Empty, loading, offline, error, permission, and success states
- Accessibility annotations
- Platform-specific iOS and Android adaptations
- Microcopy for critical decisions and failures

Do not expand the feature set beyond the stated v1 scope.
