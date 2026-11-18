import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = dirname(fileURLToPath(import.meta.url));
const paths = {
  inbox: '<path d="M4 8 7 4h10l3 4v11H4Z M4 13h5l1.5 3h3l1.5-3h5"/>',
  triage: '<rect x="8" y="5" width="8" height="14" rx="2"/><path d="M5 10 2 12l3 2m14-4 3 2-3 2"/>',
  suggestions: '<path d="M4 6h7M4 11h5M4 16h7M15 5l5 5-5 5m-3-5h8"/><circle cx="17" cy="19" r="1"/>',
  focus: '<path d="M8 3H4v4m12-4h4v4M4 17v4h4m12-4v4h-4"/><circle cx="12" cy="12" r="3"/>',
  'mind-map': '<rect x="9" y="9" width="6" height="6" rx="2"/><path d="m9 9-3-3m9 3 3-3m-9 9-3 3m9-3 3 3"/><circle cx="4" cy="4" r="2"/><circle cx="20" cy="4" r="2"/><circle cx="4" cy="20" r="2"/><circle cx="20" cy="20" r="2"/>',
  settings: '<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="9" cy="6" r="2"/><circle cx="16" cy="12" r="2"/><circle cx="8" cy="18" r="2"/>',
  search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/>',
  filter: '<path d="M4 5h16l-6 7v7l-4 2v-9Z"/>',
  upvote: '<path d="m6 10 6-6 6 6m-6-6v16"/>',
  downvote: '<path d="m6 14 6 6 6-6m-6 6V4"/>',
  unread: '<rect x="3" y="6" width="16" height="14" rx="2"/><path d="m3 7 8 6 5-4"/><circle cx="20" cy="4" r="2"/>',
  undo: '<path d="M8 5 3 10l5 5M3 10h10a6 6 0 0 1 0 12"/>',
  history: '<path d="M3 5v5h5M3 10a9 9 0 1 1 1 8m8-11v5l3 2"/>',
  back: '<path d="m11 5-7 7 7 7M4 12h16"/>',
  'sound-on': '<path d="m4 9 5 0 5-5v16l-5-5H4Zm13-1a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/>',
  'sound-off': '<path d="M4 9h5l5-5v16l-5-5H4Zm14 1 4 4m0-4-4 4"/>',
  pause: '<rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/>',
  tips: '<path d="M5 4h14v16H5ZM9 8h6m-6 4h6m-6 4h3"/>',
};
const labels = { inbox:'Tasks', triage:'Review tasks', suggestions:'Suggested plan', focus:'Start focus', 'mind-map':'Mind map', settings:'Settings', search:'Search tasks', filter:'Filter tasks', upvote:'Helpful suggestion', downvote:'Unhelpful suggestion', unread:'Mark unread', undo:'Undo last action', history:'Activity history', back:'Back', 'sound-on':'Mute sound', 'sound-off':'Enable sound', pause:'Pause focus', tips:'Planning tips' };
const styles = [ ['soft','Soft outline · Selected family',1.8,'round'] ];
const svg = (name, width, cap, selected=false) => `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><title>${labels[name]}</title><g fill="none" stroke="currentColor" stroke-width="${width}" stroke-linecap="${cap}" stroke-linejoin="${cap==='round'?'round':'miter'}">${paths[name]}</g>${selected?'<circle cx="12" cy="23" r="1" fill="currentColor"/>':''}</svg>`;
for (const [key,, width, cap] of styles) {
  mkdirSync(join(root,key),{recursive:true});
  for (const name of Object.keys(paths)) writeFileSync(join(root,key,`${name}.svg`),svg(name,width,cap));
}
let body='';
const reps=['inbox','triage','suggestions','focus','mind-map'];
for (const [key,title,width,cap] of styles) {
  body+=`<section><h2>${title}</h2><p>24px / ${width}px stroke / ${cap} terminals</p>`;
  for (const theme of ['light','dark']) body+=`<div class="surface ${theme}">${reps.map(name=>`<div class="sample"><span>${name}</span><div class="states"><div><button aria-label="${labels[name]}">${svg(name,width,cap)}</button><small>Default</small></div><div><button class="selected" aria-label="${labels[name]}" aria-pressed="true">${svg(name,width,cap,true)}</button><small>Selected</small></div></div></div>`).join('')}</div>`;
  body+='</section>';
}
body+='<section><h2>Complete selected family · Soft outline</h2><div class="family">'+Object.keys(paths).map(name=>`<div>${svg(name,1.8,'round')}<b>${name}</b><span>${labels[name]}</span></div>`).join('')+'</div></section>';
writeFileSync(join(root,'overview.html'),`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Irada icon review</title><style>*{box-sizing:border-box}body{margin:0;padding:32px;background:#ececf4;color:#181923;font:14px system-ui}main{max-width:1120px;margin:auto}h1{font-size:32px}h2{margin-bottom:6px}section{margin:32px 0}p{color:#565969}.surface{display:grid;grid-template-columns:repeat(5,1fr);padding:24px 12px;margin-top:12px;border-radius:16px;gap:8px}.light{background:#fff;color:#30323e}.dark{background:#11121a;color:#ebebf4}.sample{text-align:center}.sample>span{display:block;margin-bottom:14px}.states{display:flex;justify-content:center;gap:10px}button{display:grid;place-items:center;width:48px;height:48px;border:0;border-radius:16px;background:transparent;color:inherit}.selected{background:#e6e0ff;color:#4930c9}.dark .selected{background:#3c3262;color:#e9e2ff}small{display:block;margin-top:6px}.family{background:white;padding:24px;border-radius:16px;display:grid;grid-template-columns:repeat(6,1fr);gap:28px}.family>div{display:flex;align-items:center;flex-direction:column;gap:8px;text-align:center}.family span{font-size:12px;color:#565969}svg{flex-shrink:0}@media(max-width:700px){body{padding:16px}.surface{grid-template-columns:repeat(2,1fr);gap:24px}.family{grid-template-columns:repeat(3,1fr)}}</style><main><h1>Irada · Icon family review</h1><p>Three directions. Actual 24px glyphs inside 48px targets. Selected states add a tonal container and structural marker; color is not the sole cue.</p>${body}</main></html>`);
