// Deliberately conservative, deterministic development stub. It handles a small
// English vocabulary; it never invents a deadline for an unrecognised phrase.
function localParts(now, timeZone) {
  return Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(now).filter(({ type }) => type !== 'literal').map(({ type, value }) => [type, value]));
}

function plusDays(date, days) {
  const result = new Date(`${date}T12:00:00Z`);
  result.setUTCDate(result.getUTCDate() + days);
  return result.toISOString().slice(0, 10);
}

export function validTimeZone(value) {
  try { new Intl.DateTimeFormat('en', { timeZone: value }).format(); return true; }
  catch { return false; }
}

export function validLocale(value) {
  try { return Intl.getCanonicalLocales(value).length === 1; }
  catch { return false; }
}

export async function createPlanPreview(input, context = {}, now = new Date()) {
  const timeZone = context.timeZone ?? 'UTC';
  const parts = localParts(now, timeZone);
  const today = `${parts.year}-${parts.month}-${parts.day}`;
  const current = `${today}T${parts.hour}:${parts.minute}:${parts.second}`;
  const warnings = [];
  if (!/^en(?:-|$)/i.test(context.locale ?? 'en')) {
    warnings.push('This placeholder understands only a small set of English phrases. Check every proposal.');
  }
  const clauses = input.split(/\n+|[,;]+|\s+and\s+/i).map(value => value.trim()).filter(Boolean);
  const tasks = clauses.slice(0, 100).map(clause => {
    const normalized = clause.toLowerCase();
    // Only explicit date words assign dates. Context never leaks between clauses.
    let dueDate = /\btomorrow\b/.test(normalized) ? plusDays(today, 1)
      : /\btoday\b/.test(normalized) ? today : null;
    let reminderAt = null;
    let reason = dueDate ? `Based on '${/\btomorrow\b/.test(normalized) ? 'tomorrow' : 'today'}'.` : null;
    if (/\bremind\s+me\b/.test(normalized)) {
      const match = normalized.match(/\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?(?=$|[\s,.!?])/);
      let time = context.defaultReminderTime ?? '09:00';
      let validTime = !/\bat\b/.test(normalized) || match !== null;
      if (match) {
        let hour = Number(match[1]);
        const minute = Number(match[2] ?? 0);
        validTime = minute <= 59 && (match[3] ? hour >= 1 && hour <= 12 : hour <= 23);
        if (validTime) {
          if (match[3]) hour = hour % 12 + (match[3] === 'pm' ? 12 : 0);
          time = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
        }
      }
      if (dueDate && validTime) {
        reminderAt = `${dueDate}T${time}:00`;
        if (reminderAt <= current) {
          reminderAt = `${plusDays(today, 1)}T${time}:00`;
          warnings.push('A requested reminder time has passed. The reminder was moved to the next day; check it before approval.');
        }
        reason = match ? `${reason} Reminder based on the requested time.` : `${reason} Reminder uses your default time.`;
      } else {
        warnings.push('A reminder needs a clear date and valid time. No reminder was proposed for this item.');
      }
    }
    let title = /\bgrocer(?:y|ies)\b/.test(normalized) ? 'Buy groceries'
      : clause.replace(/^remind\s+me\s+/i, '').replace(/[.!?]+$/, '').slice(0, 200);
    if (!title.trim()) title = clause.slice(0, 200);
    return { title, notes: null, dueDate, reminderAt, suggested: true, reason };
  });
  if (clauses.length > 100) warnings.push('Only the first 100 items are shown. Split your input to review the rest.');
  return {
    input, tasks, isStub: true, warnings,
    message: 'This is a placeholder preview. No AI service was called. Nothing has been saved or scheduled.',
  };
}
