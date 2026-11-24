'use strict';

const api = window.NotesNative;
const screen = document.querySelector('#screen');
const dialog = document.querySelector('#dialog');
const sheet = document.querySelector('#sheet');
const notice = document.querySelector('#notice');
const icons = {
  menu:'<path d="M4 6h16M4 12h16M4 18h10"/>',
  search:'<circle cx="10.8" cy="10.8" r="6.3"/><path d="m16 16 4 4"/>',
  x:'<path d="m6 6 12 12M18 6 6 18"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  board:'<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M12 4v16M4 11h8m0 3h8"/>',
  single:'<rect x="4" y="4" width="16" height="6" rx="2"/><rect x="4" y="14" width="16" height="6" rx="2"/>',
  sort:'<path d="M5 6h14M7 12h10m-8 6h6"/>',
  note:'<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9zm0 0v6h6M8 13h8m-8 4h5"/>',
  list:'<rect x="3.5" y="3.5" width="17" height="17" rx="4"/><path d="m7 12 2 2 3.5-4m1 5h3.5m-2.5-5H17"/>',
  box:'<rect x="4" y="4" width="16" height="16" rx="3"/>',
  checked:'<rect x="4" y="4" width="16" height="16" rx="3"/><path d="m8 12 3 3 5-6"/>',
  tag:'<path d="M20 12.4 11.6 4H4v7.6l8.4 8.4a2 2 0 0 0 2.8 0l4.8-4.8a2 2 0 0 0 0-2.8Z"/><circle cx="8.2" cy="8.2" r=".85"/>',
  image:'<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="9" cy="9" r="1.5"/><path d="m4 17 4-4 3 3 4-5 6 6"/>',
  back:'<path d="m13 6 6 6-6 6M19 12H5"/>',
  pin:'<path d="M9 3h6l-.5 6 3.5 3v2H6v-2l3.5-3ZM12 14v7"/>',
  archive:'<rect x="3" y="4" width="18" height="4" rx="1"/><path d="M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8M10 12h4"/>',
  trash:'<path d="M5 7h14m-9 0V4h4v3m4 0-1 13H7L6 7m4 3v6m4-6v6"/>',
  more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  colors:'<path d="M12 3a9 9 0 1 0 0 18h1a2 2 0 0 0 1.3-3.5 1.2 1.2 0 0 1 .8-2.1H17a4 4 0 0 0 4-4A9 9 0 0 0 12 3Z"/><path d="M7 11h.01m3-4h.01m4-.2h.01m3.5 3h.01"/>',
  gear:'<circle cx="12" cy="12" r="3"/><path d="m10 3-1 3-3 1-2 1 1 3-1 3 2 2 3 1 1 3h4l1-3 3-1 2-2-1-3 1-3-2-1-3-1-1-3z"/>',
  down:'<path d="M12 3v12m-4-4 4 4 4-4M5 16v4h14v-4"/>',
  up:'<path d="M12 15V3m-4 4 4-4 4 4M5 16v4h14v-4"/>',
  share:'<circle cx="18" cy="5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="19" r="2.5"/><path d="m8 11 8-5m-8 7 8 5"/>',
  check:'<path d="m5 12 5 5L20 7"/>',
  chevron:'<path d="m14 6-6 6 6 6"/>'
};
const colors = {paper:'ورقي',gold:'أصفر',peach:'خوخي',rose:'وردي',mint:'نعناعي',sky:'سماوي',lilac:'بنفسجي'};
const orders = {edited:'آخر تعديل',created:'الأحدث إنشاءً',title:'العنوان'};
const regionNames = {active:'كل الملاحظات',archive:'الأرشيف',trash:'سلة المحذوفات'};
const defaults = {layout:'grid',order:'edited',font:'standard'};
const prefs = {...defaults,...read(api.preferences(),{})};
const dateFormat = new Intl.DateTimeFormat('ar',{day:'numeric',month:'long',year:'numeric'});
const countFormat = new Intl.NumberFormat('ar');
let region = 'active', type = '', label = '', query = '', view = 'board';
let draft = null, dirty = false, fresh = false, commitTimer, searchTimer, toastTimer, toastUndo;
let displayed = 0, boardScroll = 0, boardCount = 0;
const observer = new ResizeObserver(entries => sizeCards(entries.map(entry=>entry.target)));

function sizeCards(cards) {
  const heights = Array.from(cards, card=>card.getBoundingClientRect().height);
  cards.forEach((card,index)=>{
    if (heights[index]>0) card.style.gridRowEnd='span '+Math.ceil((heights[index]+9)/3);
  });
}

function read(data, fallback) {
  try { return JSON.parse(data) ?? fallback; } catch (_) { return fallback; }
}
function html(value) {
  return String(value ?? '').replace(/[&<>"']/g, sign => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[sign]);
}
function fold(value) {
  return value.normalize('NFKC').toLowerCase().replace(/[\u0610-\u061a\u0640\u064b-\u065f\u0670\u06d6-\u06ed]/g,'')
    .replace(/[أإآٱ]/g,'ا').replace(/ى/g,'ي').replace(/ؤ/g,'و').replace(/ئ/g,'ي');
}
function svg(name) {
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+icons[name]+'</svg>';
}
function icon(action, name, title, attrs='') {
  return '<button type="button" class="icon-btn" data-act="'+action+'" aria-label="'+html(title)+'" title="'+html(title)+'" '+attrs+'>'+svg(name)+'</button>';
}
function menu(action, name, title, attrs='', danger=false) {
  return '<button type="button" class="menu-row'+(danger?' danger':'')+'" data-act="'+action+'" '+attrs+'>'+svg(name)+html(title)+'</button>';
}
function chip(action, title, pressed, name, value='') {
  return '<button type="button" class="chip" data-act="'+action+'" data-value="'+html(value)+'" aria-pressed="'+pressed+'">'+(name?svg(name):'')+'<span>'+html(title)+'</span></button>';
}
function photoURL(id) { return 'photo/'+encodeURIComponent(id); }
function number(num) { return countFormat.format(num); }
function showToast(message, undo) {
  clearTimeout(toastTimer);
  notice.replaceChildren(document.createTextNode(message));
  toastUndo = undo;
  if (undo) {
    const button = document.createElement('button');
    button.dataset.act = 'undo';
    button.textContent = 'تراجع';
    notice.append(button);
  }
  toastTimer = setTimeout(() => { notice.replaceChildren(); toastUndo = null; }, undo ? 6400 : 3800);
}
function sheetOpen(title, content) {
  sheet.innerHTML = '<div class="sheet-head"><h2 id="dialog-title">'+html(title)+'</h2>'+icon('close','x','إغلاق','autofocus')+'</div>'+content;
  if (!dialog.open) dialog.showModal();
}
function sheetClose() { if (dialog.open) dialog.close(); }
function applyPrefs() { document.documentElement.classList.toggle('large-font',prefs.font==='large'); }
function setting(name, value) { prefs[name]=value; api.preference(name,value); applyPrefs(); }

function showBoard() {
  view = 'board';
  observer.disconnect();
  screen.innerHTML = '<div class="strip"><div class="shell"><header class="header"><img class="brand" src="brand.png" alt="">'
    +'<h1>الملاحظات</h1>'+icon('navigation','menu','القائمة')+'</header>'
    +'<label class="search">'+svg('search')+'<input id="query" type="search" maxlength="180" dir="auto" placeholder="ابحث في الملاحظات" aria-label="ابحث في الملاحظات">'
    +icon('clear-search','x','مسح البحث','id="clear-search" hidden')+'</label>'
    +'<nav class="filters" aria-label="تصفية الملاحظات"></nav></div></div>'
    +'<section class="shell board"><header class="boardbar"><h2 id="board-title"></h2><div class="board-actions" id="board-actions"></div></header>'
    +'<div id="matches"></div></section>'
    +(region==='active'?'<div class="fab-wrap"><div class="shell fab-inner"><button class="fab" data-act="new-text">'+svg('plus')+'ملاحظة جديدة</button>'
      +'<button class="fab alt" data-act="new-list" aria-label="قائمة تحقق جديدة" title="قائمة تحقق جديدة">'+svg('list')+'</button></div></div>':'');
  document.querySelector('#query').value=query;
  showFilters();
  renderResults();
}
function showFilters() {
  const filter = document.querySelector('.filters');
  const offset = filter.scrollLeft;
  const labels = read(api.labels(region),[]);
  const choices = labels.map(tag => chip('filter-tag',tag,fold(label)===fold(tag),'tag',tag));
  filter.innerHTML = chip('filter-all','الكل',!type&&!label)+chip('filter-type','قوائم',type==='list','list','list')
    +chip('filter-type','صور',type==='photo','image','photo')+(choices.length?'<div class="divider"></div>'+choices.join(''):'');
  filter.scrollLeft=offset;
}
function emptyState() {
  if (query || label || type) return '<div class="empty">'+svg('search')+'<h2>ما في نتائج مطابقة</h2><p>جرّب عبارة أخرى أو غيّر التصفية.</p></div>';
  if (region==='active') return '<div class="empty"><img src="blank.webp" alt=""><h2>أول ملاحظة إلك</h2><p>اكتب فكرة سريعة أو اعمل قائمة تحقق من الأزرار بالأسفل.</p></div>';
  return '<div class="empty">'+svg(region==='archive'?'archive':'trash')+'<h2>'+html(region==='archive'?'الأرشيف فارغ':'سلة المحذوفات فارغة')+'</h2></div>';
}
function card(note) {
  const cover = note.photos[0] ? '<img class="card-cover" loading="lazy" src="'+photoURL(note.photos[0])+'" alt="صورة مرفقة">' : '';
  const name = note.title.trim() ? '<div dir="auto" class="card-title cut">'+html(note.title)+'</div>' : '';
  const body = note.body.trim() ? '<p dir="auto" class="excerpt cut">'+html(note.body)+'</p>' : '';
  const rows = note.items.map(row => '<li class="'+(row.done?'checked':'')+'">'+svg(row.done?'checked':'box')+'<span dir="auto" class="cut">'+html(row.text)+'</span></li>').join('');
  const list = rows ? '<ul class="card-lines">'+rows+'</ul>' : '';
  const labels = note.labels.slice(0,2).map(tag => '<span class="card-tag"><span dir="auto">'+html(tag)+'</span></span>').join('');
  const additional = note.labels.length>2 ? '<span class="card-tag">+'+number(note.labels.length-2)+'</span>' : '';
  const tags = labels ? '<div class="card-tags">'+labels+additional+'</div>' : '';
  const progress = note.kind==='list' && note.total>0 ? '<div class="card-meta">'+svg('list')+'<bdi dir="ltr">'+number(note.doneCount)+' / '+number(note.total)+'</bdi> مكتمل</div>' : '';
  const attachments = note.photos.length>1 ? '<div class="card-meta">'+svg('image')+number(note.photos.length)+' صور</div>' : '';
  return '<button class="card" data-act="open" data-id="'+html(note.id)+'" data-color="'+html(note.color)+'" type="button">'
    +cover+name+body+list+(name||body||rows||cover?'':'<div class="card-title">ملاحظة</div>')+progress+attachments+tags+'</button>';
}
function gridSection(id, title, symbol, notes) {
  return '<section id="'+id+'" '+(notes.length?'':'hidden')+'>'+(title?'<h3 class="group-title">'+svg(symbol)+html(title)+'</h3>':'')
    +'<div class="grid'+(prefs.layout==='single'?' single':'')+'">'+notes.map(card).join('')+'</div></section>';
}
function renderResults() {
  clearTimeout(searchTimer);
  displayed = 0;
  observer.disconnect();
  const target = document.querySelector('#matches');
  if (!target) return;
  document.querySelector('#clear-search').hidden=!query;
  document.querySelector('#board-title').innerHTML = region!=='active'
    ? svg(region==='archive'?'archive':'trash')+html(regionNames[region])
    : html(query?'نتائج البحث':label||regionNames.active);
  document.querySelector('#board-actions').innerHTML = icon('sort','sort','الترتيب · '+orders[prefs.order])
    +icon('layout',prefs.layout==='grid'?'single':'board',prefs.layout==='grid'?'عرض قائمة':'عرض بطاقات');
  const data = read(api.list(region,type,label,query,prefs.order,0),null);
  if (!data) { target.innerHTML = '<div class="empty"><h2>تعذّر فتح الملاحظات</h2>'+menu('retry','note','إعادة المحاولة')+'</div>'; return; }
  if (!data.length) { target.innerHTML=emptyState(); return; }
  const entries = data.slice(0,36);
  const pinned = region==='active'?entries.filter(note=>note.pinned):[];
  const rest = region==='active'?entries.filter(note=>!note.pinned):entries;
  target.innerHTML = gridSection('pinned','المثبّتة','pin',pinned)
    +gridSection('other',pinned.length?'باقي الملاحظات':'','note',rest)
    +'<button class="more" data-act="more-results" '+(data.length>36?'':'hidden')+'>عرض المزيد</button>';
  displayed += entries.length;
  const cards=target.querySelectorAll('.card');
  sizeCards(cards);
  cards.forEach(card=>observer.observe(card));
}
function moreResults() {
  const page = read(api.list(region,type,label,query,prefs.order,displayed),null);
  if (!page) { showToast('تعذّر تحميل ملاحظات إضافية.'); return; }
  const added=[];
  for (const note of page.slice(0,36)) {
    const section = document.querySelector(region==='active'&&note.pinned?'#pinned':'#other');
    const list = section.querySelector('.grid');
    const last = list.children.length;
    list.insertAdjacentHTML('beforeend',card(note));
    section.hidden=false;
    added.push(list.children[last]);
    if (section.id==='other' && !section.querySelector('h3') && !document.querySelector('#pinned').hidden)
      section.insertAdjacentHTML('afterbegin','<h3 class="group-title">'+svg('note')+'باقي الملاحظات</h3>');
    displayed++;
  }
  sizeCards(added);
  added.forEach(card=>observer.observe(card));
  document.querySelector('.more').hidden=page.length<=36;
}
function setRegion(next) {
  sheetClose(); region=next; query=''; label=''; type=''; boardScroll=0; boardCount=0;
  showBoard(); window.scrollTo(0,0);
}
function showNavigation() {
  const rows=Object.entries(regionNames).map(([key,title])=>menu('region',key==='active'?'note':key==='archive'?'archive':'trash',
    title,'data-value="'+key+'" aria-current="'+(key===region)+'"'));
  sheetOpen('الملاحظات',rows.join('')+menu('settings','gear','الإعدادات'));
}
function showSort() {
  sheetOpen('ترتيب الملاحظات', Object.entries(orders).map(([key,title])=>menu('order',key===prefs.order?'check':'sort',
    title,'data-value="'+key+'" aria-current="'+(key===prefs.order)+'"')).join(''));
}
function flush(showError=false) {
  clearTimeout(commitTimer);
  if (!draft || !dirty) return true;
  if (!api.save(JSON.stringify(draft))) {
    if (showError) showToast('تعذّر الحفظ. جرّب مرة أخرى.');
    const sync=document.querySelector('.sync');
    if (sync) sync.textContent='تعذّر الحفظ';
    return false;
  }
  dirty = false;
  const current=read(api.get(draft.id),null);
  if (current) {
    draft.created=current.created; draft.edited=current.edited;
    fresh=false;
  }
  updateSync();
  return true;
}
function updateSync() {
  const text = document.querySelector('.sync');
  if (text) text.textContent = dirty ? 'جارٍ الحفظ…' : fresh ? '' : 'محفوظة تلقائيًا';
}
function changed() {
  dirty=true;
  updateSync();
  clearTimeout(commitTimer);
  commitTimer=setTimeout(()=>flush(),220);
}
function editor(id, kind, autofocus=false) {
  if (view==='board') rememberBoard();
  observer.disconnect();
  let current = id ? read(api.get(id),null) : null;
  if (id&&!current) { showToast('تعذّر فتح الملاحظة.'); return; }
  fresh = !current;
  draft = current || {id:api.newId(),title:'',body:'',kind,color:'paper',state:'active',pinned:false,
    items:[],photos:[],labels:label?[label]:[],created:Date.now(),edited:Date.now()};
  dirty=false;
  view='editor';
  showEditor();
  window.scrollTo(0,0);
  if (autofocus) requestAnimationFrame(()=>document.querySelector(draft?.kind==='list'?'[data-row="0"]':'#note-body')?.focus());
}
function showEditor() {
  const frozen = draft.state==='trash';
  const isList=draft.kind==='list';
  const multiple= draft.photos.length >= 4;
  const header = '<header class="editorbar"><div class="header shell">'+icon('editor-back','back','العودة')
    +'<strong>'+html(frozen?'المحذوفات':isList?'قائمة تحقق':'ملاحظة')+'</strong>'
    +(frozen?'':icon('pin','pin', draft.pinned?'إلغاء التثبيت':'تثبيت', 'aria-pressed="'+draft.pinned+'"')
      +icon('archive','archive',draft.state==='archive'?'إخراج من الأرشيف':'نقل إلى الأرشيف'))
    +'<button class="done-btn" data-act="editor-back">تم</button></div></header>';
  screen.innerHTML = '<section class="write'+(isList?' list-editor':'')+'" data-color="'+html(draft.color)+'">'+header+'<div class="shell editorbody">'
    +(frozen?'<div class="binned"><span>هذه الملاحظة في سلة المحذوفات</span><button class="done-btn" data-act="restore-note">استعادة</button></div>':'')
    +'<div id="photos"></div>'
    +'<input id="note-title" aria-label="العنوان" maxlength="200" dir="auto" placeholder="العنوان" '+(frozen?'readonly':'')+'>'
    +'<textarea id="note-body" aria-label="نص الملاحظة" maxlength="30000" dir="auto" placeholder="'+(isList?'ملاحظة للقائمة (اختياري)':'اكتب ملاحظتك…')+'" '+(isList?'rows="2"':'rows="5"')+' '+(frozen?'readonly':'')+'></textarea>'
    +(isList?'<div id="items"></div>':'')+'<div class="editor-tags"></div>'
    +(!fresh?'<div class="editor-time">آخر تعديل · '+html(dateFormat.format(draft.edited))+'</div>':'')
    +'</div><footer class="editor-foot"><div class="shell tools">'
    +(frozen?icon('erase','trash','حذف نهائيًا'):icon('choose-photo','image','إضافة صورة',multiple?'disabled':'')
      +icon('tags','tag','تعديل الوسوم')+icon('palette','colors','اختيار اللون')+icon('editor-menu','more','خيارات الملاحظة'))
    +'<span class="sync" aria-live="polite"></span></div></footer></section>';
  document.querySelector('#note-title').value=draft.title;
  document.querySelector('#note-body').value=draft.body;
  renderPhotos();
  if(isList) renderItems();
  renderTags();
  updateSync();
  resizeBody();
}
function renderItems() {
  const node=document.querySelector('#items');
  if (!node) return;
  const frozen=draft.state==='trash';
  if (!draft.items.length && !frozen) draft.items.push({text:'',done:false});
  node.innerHTML=draft.items.map((row,index)=>'<div class="checkrow '+(row.done?'done-row':'')+'"><input type="checkbox" data-check="'+index+'" aria-label="حالة البند '+number(index+1)+'" '+(row.done?'checked ':'')+(frozen?'disabled':'')+'>'
    +'<label><input type="text" data-row="'+index+'" dir="auto" aria-label="البند '+number(index+1)+'" maxlength="1000" placeholder="اكتب بندًا…" '+(frozen?'readonly':'')+'></label>'
    +(frozen?'':icon('remove-row','x','حذف البند '+number(index+1),'data-index="'+index+'"'))+'</div>').join('')
    +(frozen||draft.items.length>=150?'':'<button type="button" class="add-row" data-act="add-row">'+svg('plus')+'إضافة بند</button>');
  node.querySelectorAll('[data-row]').forEach((input,i)=>input.value=draft.items[i].text);
}
function addRow(index=draft.items.length) {
  if (draft.items.length>=150) return;
  draft.items.splice(index,0,{text:'',done:false});
  renderItems();
  document.querySelector('[data-row="'+index+'"]')?.focus();
}
function renderPhotos() {
  const target=document.querySelector('#photos');
  if (!target) return;
  const remove = draft.state!=='trash';
  target.innerHTML = draft.photos.map(id=>'<figure><button class="picture" data-act="show-photo" data-id="'+html(id)+'" aria-label="عرض الصورة"><img src="'+photoURL(id)+'" alt=""></button>'
    +(remove?'<button class="icon-btn remove-photo" data-act="remove-photo" data-id="'+html(id)+'" aria-label="إزالة الصورة">'+svg('x')+'</button>':'')+'</figure>').join('');
}
function renderTags() {
  document.querySelector('.editor-tags').innerHTML = draft.labels.map(tag=>'<span class="editor-tag">'+svg('tag')+'<bdi>'+html(tag)+'</bdi></span>').join('');
}
function closeEditor() {
  if (!flush(true)) return;
  sheetClose(); draft=null; openBoard();
}
function changeState(next) {
  if (!flush(true)) return;
  if (fresh) { closeEditor(); return; }
  const id=draft.id, old=draft.state, pinned=draft.pinned;
  draft.state=next;
  if (next==='archive') draft.pinned=false;
  dirty=true;
  if (!flush(true)) return;
  closeEditor();
  const messages={active:'عادت الملاحظة إلى الملاحظات.',archive:'نُقلت الملاحظة إلى الأرشيف.',trash:'نُقلت الملاحظة إلى المحذوفات.'};
  showToast(messages[next],()=>{
    const saved=read(api.get(id),null);
    if (!saved || saved.state!==next) return;
    saved.state=old; saved.pinned=pinned;
    if (!api.save(JSON.stringify(saved))) { showToast('تعذّر التراجع.'); return; }
    if (view==='board') { showFilters(); renderResults(); }
    showToast('تم التراجع.');
  });
}
function editTags() {
  const tags = [...draft.labels];
  for (const group of ['active','archive']) {
    for (const tag of read(api.labels(group),[]))
      if(!tags.some(existing=>fold(existing)===fold(tag))) tags.push(tag);
  }
  sheetOpen('الوسوم','<form class="field" id="tag-form"><input id="tag-name" maxlength="40" dir="auto" placeholder="وسم جديد" aria-label="اسم الوسم">'
    +'<button class="done-btn" type="submit">إضافة</button></form><div class="tag-choices">'
    +tags.map(tag=>chip('select-tag',tag,draft.labels.some(item=>fold(item)===fold(tag)),'tag',tag)).join('')+'</div>');
}
function toggleTag(raw, addOnly=false) {
  const tag=raw.replace(/[\u0000-\u001f\u007f\u00a0]+/g,' ').trim();
  if (!tag) return;
  const index=draft.labels.findIndex(existing=>fold(existing)===fold(tag));
  if (index===-1) {
    if (draft.labels.length>=16) { sheetClose(); showToast('تقدر تضيف حتى ١٦ وسمًا للملاحظة.'); return; }
    draft.labels.push(tag);
  } else if (!addOnly) draft.labels.splice(index,1);
  changed(); flush(); renderTags(); editTags();
}
function editorOptions() {
  sheetOpen('خيارات الملاحظة',menu('send','share','مشاركة النص')+menu('delete','trash','نقل إلى سلة المحذوفات','',true));
}
function noteText(note) {
  const items = note.items.filter(row=>row.text.trim()).map(row=>(row.done?'☑ ':'☐ ')+row.text);
  return [note.title,note.body,items.join('\n')].filter(value=>value.trim()).join('\n\n');
}
function fieldSetting(name, symbol, title, description, choices) {
  return '<label class="setting-row">'+svg(symbol)+'<span><b>'+title+'</b><small>'+description+'</small></span>'
    +'<select data-pref="'+name+'" aria-label="'+title+'">'+Object.entries(choices).map(([value,text])=>
      '<option value="'+value+'" '+(prefs[name]===value?'selected':'')+'>'+html(text)+'</option>').join('')+'</select></label>';
}
function fileSetting(action, symbol, title, extra) {
  return '<button class="setting-row" data-act="'+action+'">'+svg(symbol)+'<span><b>'+title+'</b><small>'+extra+'</small></span>'+svg('chevron')+'</button>';
}
function settings() {
  sheetClose(); view='settings'; observer.disconnect();
  screen.innerHTML='<div class="strip"><header class="header shell">'+icon('settings-back','back','رجوع')+'<h1>الإعدادات</h1></header></div>'
    +'<section class="settings shell"><h2>العرض</h2><div class="setting-card">'
    +fieldSetting('layout','board','طريقة العرض','البطاقات أو قائمة بعمود واحد',{grid:'بطاقات',single:'قائمة'})
    +fieldSetting('order','sort','ترتيب الملاحظات','يُحفظ للمرات القادمة',orders)
    +fieldSetting('font','note','حجم خط الكتابة','في المحرّر ومعاينات الملاحظات',{standard:'عادي',large:'كبير'})+'</div>'
    +'<h2>النسخ والتصدير</h2><div class="setting-card">'
    +fileSetting('backup','down','إنشاء نسخة احتياطية','ملف ZIP يضم الملاحظات والقوائم والصور')
    +fileSetting('restore','up','استيراد نسخة احتياطية','يضيف الملاحظات الناقصة ويحافظ على الموجودة')
    +fileSetting('markdown','down','تصدير Markdown','ملف مستقل لكل ملاحظة مع الصور داخل ZIP')
    +fileSetting('keep','up','استيراد من Google Keep','نصوص وقوائم ووسوم من ملف Google Takeout بصيغة ZIP')
    +'</div><p class="about">الملاحظات ١٫٠ · يعمل أوفلاين وتُحفظ ملاحظاتك على الجهاز.</p></section>';
  window.scrollTo(0,0);
}

function rememberBoard() { boardScroll=window.scrollY; boardCount=displayed; }
function openBoard() {
  showBoard();
  while (displayed<boardCount && document.querySelector('.more:not([hidden])')) {
    const before=displayed;
    moreResults();
    if (displayed===before) break;
  }
  window.scrollTo(0,boardScroll);
}
function filterChanged() { showFilters(); renderResults(); window.scrollTo(0,0); }

document.addEventListener('click',event=>{
  const button=event.target.closest('[data-act]');
  if (!button || !document.querySelector('#working').hidden) return;
  const action=button.dataset.act, value=button.dataset.value, id=button.dataset.id;
  switch(action) {
    case 'new-text': case 'new-list': editor(null,action==='new-list'?'list':'text',true); break;
    case 'open': editor(id); break;
    case 'editor-back': closeEditor(); break;
    case 'navigation': showNavigation(); break;
    case 'close': sheetClose(); break;
    case 'region': setRegion(value); break;
    case 'settings': rememberBoard(); settings(); break;
    case 'settings-back': openBoard(); break;
    case 'clear-search':
      query=''; document.querySelector('#query').value='';
      document.querySelector('#query').focus(); filterChanged();
      break;
    case 'filter-all': type=''; label=''; filterChanged(); break;
    case 'filter-type': type=type===value?'':value; filterChanged(); break;
    case 'filter-tag': label=fold(label)===fold(value)?'':value; filterChanged(); break;
    case 'layout': setting('layout',prefs.layout==='grid'?'single':'grid'); renderResults(); break;
    case 'sort': showSort(); break;
    case 'order': setting('order',value); sheetClose(); renderResults(); window.scrollTo(0,0); break;
    case 'retry': renderResults(); break;
    case 'more-results': moreResults(); break;
    case 'pin':
      if (draft.state==='archive') { showToast('أخرجها من الأرشيف لتثبيتها.'); break; }
      draft.pinned=!draft.pinned; changed(); flush(true);
      button.setAttribute('aria-pressed',String(draft.pinned));
      button.setAttribute('aria-label',draft.pinned?'إلغاء التثبيت':'تثبيت');
      button.title=button.getAttribute('aria-label');
      break;
    case 'archive': changeState(draft.state==='archive'?'active':'archive'); break;
    case 'add-row': addRow(); break;
    case 'remove-row':
      draft.items.splice(Number(button.dataset.index),1);
      renderItems(); changed(); flush(true);
      break;
    case 'choose-photo': if(flush(true)) api.pickPhoto(JSON.stringify(draft)); break;
    case 'show-photo':
      sheetOpen('الصورة','<img class="photo-view" src="'+photoURL(id)+'" alt="الصورة المرفقة">');
      break;
    case 'remove-photo':
      sheetOpen('إزالة الصورة','<p class="sheet-text">إزالة هذه الصورة من الملاحظة؟</p>'
        +menu('remove-photo-confirm','trash','إزالة','data-id="'+html(id)+'"',true)); break;
    case 'remove-photo-confirm':
      draft.photos=draft.photos.filter(photo=>photo!==id); changed();
      sheetClose(); if(flush(true)) showEditor();
      break;
    case 'palette':
      sheetOpen('لون الملاحظة','<div class="swatches">'+Object.entries(colors).map(([key,title])=>
        '<button class="swatch" data-act="color" data-color="'+key+'" aria-pressed="'+(draft.color===key)+'">'+title+'</button>').join('')+'</div>');
      break;
    case 'color':
      draft.color=button.dataset.color; document.querySelector('.write').dataset.color=draft.color;
      changed(); flush(); sheetClose(); break;
    case 'tags': editTags(); break;
    case 'select-tag': toggleTag(value); break;
    case 'editor-menu': editorOptions(); break;
    case 'send': {
      const text=noteText(draft);
      sheetClose(); if(flush(true)&&text) api.share(text);
      break;
    }
    case 'delete': changeState('trash'); break;
    case 'restore-note': changeState('active'); break;
    case 'erase':
      sheetOpen('حذف نهائي؟','<p class="sheet-text">سيتم حذف الملاحظة وصورها نهائيًا.</p>'+menu('erase-confirm','trash','حذف نهائيًا','',true));
      break;
    case 'erase-confirm':
      if (!api.erase(draft.id)) { sheetClose(); showToast('تعذّر الحذف.'); break; }
      closeEditor(); showToast('حُذفت الملاحظة.');
      break;
    case 'undo': if (toastUndo) toastUndo(); break;
    case 'backup': case 'markdown': api.document(action); break;
    case 'restore':
      sheetOpen('استيراد نسخة احتياطية','<p class="sheet-text">اختر ملف ZIP صادرًا من هذا التطبيق. ستُضاف الملاحظات الناقصة إلى ملاحظاتك الحالية.</p>'
        +menu('pick-restore','up','اختيار الملف'));
      break;
    case 'keep':
      sheetOpen('استيراد من Google Keep','<p class="sheet-text">اختر ملف Google Takeout بصيغة ZIP. ينقل التطبيق النصوص والقوائم والوسوم، ويتجاوز المرفقات.</p>'
        +menu('pick-keep','up','اختيار الملف'));
      break;
    case 'pick-restore': case 'pick-keep': sheetClose(); api.document(action.slice(5)); break;
    default: break;
  }
});
document.addEventListener('input',event=>{
  const input=event.target;
  if (input.id==='query') {
    query=input.value;
    document.querySelector('#clear-search').hidden=!query;
    clearTimeout(searchTimer);
    searchTimer=setTimeout(()=>{ renderResults(); window.scrollTo(0,0); },140);
  } else if(draft && (input.id==='note-title'||input.id==='note-body'||input.dataset.row!==undefined)) {
    if(input.id==='note-title') draft.title=input.value;
    if(input.id==='note-body') draft.body=input.value;
    if(input.dataset.row!==undefined) draft.items[Number(input.dataset.row)].text=input.value;
    if(input.id==='note-body') resizeBody();
    changed();
  }
});
document.addEventListener('change',event=>{
  const input=event.target;
  if (input.dataset.pref) setting(input.dataset.pref,input.value);
  if (draft && input.dataset.check!==undefined) {
    draft.items[Number(input.dataset.check)].done=input.checked;
    input.closest('.checkrow').classList.toggle('done-row',input.checked);
    changed(); flush(true);
  }
});
document.addEventListener('keydown',event=>{
  const target=event.target;
  if (!draft || event.isComposing || event.key!=='Enter' || target.dataset.row===undefined || target.readOnly) return;
  event.preventDefault();
  const next=Number(target.dataset.row)+1;
  if (target.value.trim()) addRow(next);
});
document.addEventListener('submit',event=>{
  if(event.target.id!=='tag-form') return;
  event.preventDefault(); toggleTag(document.querySelector('#tag-name').value,true);
});
dialog.addEventListener('click',event=>{
  if(event.target===dialog) sheetClose();
});
function resizeBody() {
  if (CSS.supports('field-sizing','content')) return;
  const body=document.querySelector('#note-body');
  if (!body) return;
  body.style.height='auto';
  body.style.height=body.scrollHeight+'px';
}
window.addEventListener('resize',resizeBody);
document.addEventListener('visibilitychange',()=>{ if(document.hidden) flush(); });
window.notesFlush=()=>flush();
window.notesBack=()=>{
  if (dialog.open) { sheetClose(); return true; }
  if (view==='editor') { closeEditor(); return true; }
  if (view==='settings') { openBoard(); return true; }
  if (region!=='active') { setRegion('active'); return true; }
  if (query || label || type) { setRegion('active'); return true; }
  return false;
};
window.notesWorking=working=>{
  if (working) document.activeElement?.blur();
  document.querySelector('#working').hidden=!working;
};
window.notesResult=(success,message,id)=>{
  window.notesWorking(false);
  if (success && id) editor(id);
  else if (success && view==='board') { showFilters(); renderResults(); }
  showToast(message);
};
applyPrefs();
showBoard();
