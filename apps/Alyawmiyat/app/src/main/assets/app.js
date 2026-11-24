"use strict";

const icons = {
  book:'<rect x="4" y="3" width="16" height="18" rx="3"/><path d="M8 3v18m4-13h5m-5 4h4"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  settings:'<path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="3" fill="white"/><circle cx="15" cy="17" r="3" fill="white"/>',
  text:'<path d="M5 6h14M5 10h14m-8 4h8m-14 4h14"/>',
  pen:'<path d="m13 6 5 5M4 20l1-5L16 4a2 2 0 0 1 3 0l1 1a2 2 0 0 1 0 3L10 19z"/>',
  cal:'<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 10h18m-12 5h1m4 0h1"/>',
  left:'<path d="m14 6-6 6 6 6"/>',
  right:'<path d="m10 6 6 6-6 6"/>',
  close:'<path d="m5 5 14 14M19 5 5 19"/>',
  check:'<path d="m5 12 4 4L19 6"/>',
  search:'<circle cx="10.5" cy="10.5" r="5.5"/><path d="m15 15 4.5 4.5"/>',
  image:'<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8.4" cy="8.5" r="1.4"/><path d="m4 17 4-4 3 3 4-5 5 6"/>',
  bell:'<path d="M18 8a6 6 0 0 0-12 0v4.5c0 1.5-.7 3-2 4.5h16c-1.3-1.5-2-3-2-4.5V8Zm-8 12a2 2 0 0 0 4 0"/>',
  more:'<circle cx="5" cy="12" r="1" fill="currentColor"/><circle cx="12" cy="12" r="1" fill="currentColor"/><circle cx="19" cy="12" r="1" fill="currentColor"/>',
  erase:'<path d="m4 13 8-9a2 2 0 0 1 3 0l5 4a2 2 0 0 1 0 3l-7 8H9l-5-4a2 2 0 0 1 0-2Zm4-4 8 7m-3 3h7"/>',
  undo:'<path d="m9 6-5 5 5 5m-5-5h10a5 5 0 0 1 5 5v2"/>',
  redo:'<path d="m15 6 5 5-5 5m5-5H10a5 5 0 0 0-5 5v2"/>',
  shield:'<path d="M12 3 4 6v6c0 4 3 7 8 9 5-2 8-5 8-9V6l-8-3Z"/><path d="m8 12 3 3 5-6"/>',
  download:'<path d="M12 3v12m-4-4 4 4 4-4M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/>',
  upload:'<path d="M12 15V3m-4 4 4-4 4 4M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/>',
  file:'<path d="M6 3h8l4 4v14H6zM14 3v5h4m-8 5h4m-4 4h4"/>',
  trash:'<path d="M4 7h16m-11 0V4h6v3m-9 0 1 13h10l1-13m-8 4v5m4-5v5"/>'
};

const api = window.JournalNative;
const $ = id => document.getElementById(id);
const escapeHTML = value => String(value).replace(/[&<>"']/g,ch => ({
  "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
})[ch]);
const icon = name => '<svg viewBox="0 0 24 24" class="i" aria-hidden="true">' + icons[name] + '</svg>';
const tile = (name, tone = "") => '<span class="tile ' + tone + '">' + icon(name) + '</span>';
const months = ["كانون الثاني","شباط","آذار","نيسان","أيار","حزيران","تموز","آب","أيلول","تشرين الأول","تشرين الثاني","كانون الأول"];
const weekdays = ["الأحد","الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت"];
const prompts = [
  ["لو لم يكن المال عائقًا", "ماذا ستفعل لو لم يكن المال عائقًا؟ ولو أتيح لك كل الوقت والمال، كيف ستستخدم مواهبك لخدمة الآخرين؟"],
  ["كيف تحب أن تُذكَر؟", "بماذا تحب أن يتذكّرك الناس بعد رحيلك؟ إلى أي مدى تنسجم أفعالك اليوم مع هذه الصورة؟"],
  ["أسبوع يتكرّر عشر سنوات", "لو تكرّر ما فعلته هذا الأسبوع على مدى عشر سنوات، فأين ستصل؟ ما الشيء الواحد الذي يستحق أن تغيّره هذا الأسبوع؟"],
  ["ما يمنحك الطاقة", "ما الأنشطة التي منحتك طاقة خلال الأسبوعين الماضيين؟ وما الذي استنزفها؟ يمكنك تمييز النوعين بلونين في جدولك."],
  ["عجلة الحياة", "كيف تقرّبك أفعالك من الحياة التي تريدها في العمل (الرسالة والمال والنمو)، والصحة (الجسد والعقل والقلب)، والعلاقات (العائلة والأصدقاء والشريك)؟ ما المجال الذي يحتاج إلى اهتمامك الآن؟"],
  ["ثلاثة مسارات للمستقبل", "كيف ستبدو حياتك بعد خمس سنوات في كل مسار؟\nأ. تابعت طريقك الحالي.\nب. سلكت طريقًا مختلفًا تمامًا.\nج. اخترت بحرية بعيدًا عن ضغط المال ورأي الناس."],
  ["الهدف والعقبة", "ما هدفي؟ وما الغاية الحقيقية من ورائه؟ ما العقبة الأساسية التي تعوقه الآن؟"],
  ["هدف واحد يحرّك البقية", "اكتب عشرة أهداف لهذه السنة بصيغة الحاضر. أيّ هدف لو تحقّق غدًا لكان له أكبر أثر إيجابي على بقية الأهداف؟"],
  ["علاقتك بعملك", "إلى أي مدى تدير عملك وتبنيه؟ ومتى يبدأ عملك بتوجيه وقتك وحياتك؟ ما الحدّ الذي ترغب في وضعه؟"],
  ["أفق الوقت", "كيف ستقضي وقتك لو بقي لك سنتان؟ ماذا سيتغيّر لو بقيت لك خمس سنوات أو عشر أو عشرون أو خمسون أو مئة؟ ما الذي ستغيّره في طريقة قضائك لوقتك اليوم؟"]
];
const dailyTemplate = ["ما لحظة اليوم التي أريد أن أتذكّرها؟", "ما الذي شعرت به، وما الذي تعلّمته؟", "ما خطوة صغيرة أحب أن أجرّبها غدًا؟"];
const revisitPeriods = [["week","أسبوع"],["month","شهر"],["year","سنة"]];
const penSizes = ["رفيع","متوسط","عريض"];

const CHUNK = 32000;
const PAGE_SIZE = 30;
const MAX_PHOTOS = 6;
const MAX_POINTS = 24000;
const WIDTH = 800;
const HEIGHT = 1024;
const validId = value => /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(value);
const photoURL = value => "/media/" + value;

let page = "home";
let entry = null;
let persisted = false;
let dirty = false;
let saveTimer;
let listTimer;
let toastTimer;
let filterDate = "";
let search = "";
let entries = [];
let more = false;
let listFailed = false;
let dialog = "";
let beforeDialog = null;
let dialogValue = "";
let work = false;
let inkController = null;
let undoStack = [];
let isErasing = false;
let penSize = api?.penSize ? api.penSize() : 1;
let waitingRevisit = "";

function today() {
  const date = new Date();
  return [date.getFullYear(),String(date.getMonth() + 1).padStart(2,"0"),String(date.getDate()).padStart(2,"0")].join("-");
}
function localDate(value) {
  const [y,m,d] = value.split("-").map(Number);
  return new Date(y,m-1,d,12);
}
function friendlyDate(day,year = false) {
  const date = localDate(day);
  return date.getDate() + " " + months[date.getMonth()] + (year ? " " + date.getFullYear() : "");
}
function generateId() {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  const random = crypto.getRandomValues(new Uint8Array(16));
  random[6] = (random[6] & 15) | 64;
  random[8] = (random[8] & 63) | 128;
  const hex = [...random].map(n => n.toString(16).padStart(2,"0")).join("");
  return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join("-");
}
function notify(text) {
  const status = $("toast");
  status.textContent = text;
  status.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => status.classList.remove("show"),2700);
}
function iconButton(action,label,symbol) {
  return '<button type="button" class="icon-button" data-act="' + action + '" aria-label="' +
    label + '" title="' + label + '">' + icon(symbol) + '</button>';
}
function option(action,title,caption,symbol,tone = "") {
  return '<button type="button" class="option" data-act="' + action + '">' + tile(symbol,tone) +
    '<span><strong>' + title + '</strong><small>' + caption + '</small></span>' + icon("left") + '</button>';
}
function button(action,label,type = "primary") {
  return '<button class="' + type + '" type="button" data-act="' + action + '">' + label + '</button>';
}
function frame() {
  const editing = page === "editor";
  $("shell").classList.toggle("page-editor",editing);
  $("nav").hidden = editing;
  if (editing) {
    $("top").innerHTML = '<div class="editor-right">' + iconButton("done","العودة إلى اليوميات","right") +
      '<div class="editor-heading"><h1>' + (entry.mode === "ink" ? "يومية بالقلم" : "يومية نصية") +
      '</h1></div></div><div class="editor-left">' + iconButton("entry-menu","خيارات اليومية","more") +
      '<button type="button" class="done" data-act="done">تم</button></div>';
    return;
  }
  $("top").innerHTML = '<div class="brand"><span class="brand-mark">' + icon("book") +
    '</span><span>اليوميات</span></div>' + (page === "home" ? '<span class="date-pill">' + icon("cal") +
    weekdays[localDate(today()).getDay()] + '، ' + friendlyDate(today()) + '</span>' : "");
  const links = [["home","السجل","book"],["new","يومية جديدة","plus"],["settings","الإعدادات","settings"]];
  $("nav").innerHTML = links.map(([act,label,mark]) =>
    '<button class="nav-item' + (act === "new" ? ' nav-add' : '') + '" type="button" data-act="' + act + '"' +
    (act === page ? ' aria-current="page"' : '') + '><span class="nav-icon">' + icon(mark) +
    '</span><span>' + label + '</span></button>').join("");
}
function journalHome() {
  return '<section class="home-hero"><img class="hero-art" src="journal.webp" alt="" width="496" height="432"><div class="hero-copy">' +
    '<div class="hero-label">' + icon("book") + ' يومياتك الخاصة</div><h1>كيف كان يومك؟</h1>' +
    '<p>دوّن لحظة اليوم بالطريقة التي تناسبك.</p></div></section>' +
    '<div class="start-grid"><button class="start-card" data-act="new-text" type="button">' + tile("text") +
    '<span><strong>اكتب نصًا</strong><small>مع صور إذا أحببت</small></span></button>' +
    '<button class="start-card" data-act="new-ink" type="button">' + tile("pen","sand") +
    '<span><strong>اكتب بالقلم</strong><small>صفحة للكتابة بخط اليد</small></span></button></div>' +
    '<div class="section-head"><h2>سجلّ اليوميات</h2><button class="filter-button' + (filterDate ? " active" : "") +
    '" data-act="filter" type="button">' + icon("cal") + (filterDate ? friendlyDate(filterDate,true) : "كل الأيام") +
    '</button></div><label class="search-field">' + icon("search") + '<input id="search" type="search" placeholder="ابحث في عناوينك ونصوصك" ' +
    'aria-label="البحث في اليوميات" maxlength="120" value="' + escapeHTML(search) + '"></label><section id="timeline" aria-label="سجلّ اليوميات"></section>';
}
function settingsPage() {
  const reminder = reminderSettings();
  return '<div class="page-title"><h1>الإعدادات</h1><p>وقت التدوين ونسخ يومياتك.</p></div>' +
    '<section class="panel"><h2>التفضيلات</h2>' + option("reminder","التذكيرات",
      reminder.enabled ? "التذكير اليومي عند " + escapeHTML(reminder.time) : "وقت التدوين والعودة إلى يومية","bell") +
    '</section><section class="panel"><h2>البيانات والنسخ الاحتياطية</h2>' +
    option("backup","حفظ نسخة احتياطية","ملف ZIP يشمل النصوص والخط والصور","download") +
    option("restore","استعادة نسخة","استبدال السجل من نسخة احتياطية لليوميات","upload","sand") +
    option("export","تصدير اليوميات للقراءة","ملف HTML يفتح في المتصفح دون إنترنت","file","blue") +
    '</section><section class="panel empty">' + tile("shield") +
    '<div><h3>يومياتك على جهازك</h3><p>تُحفظ الكتابات والصور محليًا ويعمل التطبيق دون حساب أو اتصال.</p></div></section>';
}
function loadList(append = false) {
  if (!append) entries = [];
  listFailed = false;
  try {
    const source = JSON.parse(api.list(search.trim(),filterDate,entries.length));
    entries.push(...source.entries);
    more = source.more;
  } catch (_) {
    listFailed = true;
    more = false;
  }
  renderTimeline();
}
function row(item) {
  const title = item.title.trim() || (item.mode === "ink" ? "يومية بخط اليد" : item.photos ? "يومية مصوّرة" : "يومية بلا عنوان");
  const excerpt = item.mode === "ink" ? "كتابة بالقلم" : item.excerpt.trim() || "كتابة نصية";
  const image = item.photos ? '<span class="entry-aside" aria-label="' + item.photos + ' صور">' +
    icon("image") + '<bdi>' + item.photos + '</bdi></span>' : "";
  return '<button class="entry-row" type="button" data-act="open" data-id="' + item.id + '">' +
    tile(item.mode === "ink" ? "pen" : "text",item.mode === "ink" ? "sand" : "") +
    '<span class="entry-copy"><strong>' + escapeHTML(title) + '</strong><small>' + escapeHTML(excerpt) +
    '</small></span>' + image + '<span class="entry-aside">' + icon("left") + '</span></button>';
}
function renderTimeline() {
  const target = $("timeline");
  if (!target) return;
  if (!entries.length) {
    const filtered = Boolean(search.trim() || filterDate);
    const title = listFailed ? "تعذّر قراءة السجل" : filtered ? "لا يوميات مطابقة" : "صفحتك الأولى بانتظارك";
    const copy = listFailed ? "أعد المحاولة." : filtered ? "جرّب بحثًا آخر أو غيّر التاريخ." : "ابدأ بسطر أو اكتب أول ذكرى بالقلم.";
    target.innerHTML = '<div class="panel empty">' + tile("book") + '<div><h3>' + title +
      '</h3><p>' + copy + '</p></div></div>';
    return;
  }
  const groups = new Map();
  for (const item of entries) {
    if (!groups.has(item.day)) groups.set(item.day,[]);
    groups.get(item.day).push(item);
  }
  target.innerHTML = [...groups].map(([day,items]) => '<h3 class="group-day">' +
    (day === today() ? "اليوم · " : "") + friendlyDate(day,true) + '</h3><div class="panel rows">' +
    items.map(row).join("") + '</div>').join("") + (more ? button("more","عرض يوميات أقدم","secondary") : "");
}
function switchPage(next) {
  clearTimeout(listTimer);
  page = next;
  frame();
  if (page === "home") {
    $("view").innerHTML = journalHome();
    loadList();
  } else if (page === "settings") {
    $("view").innerHTML = settingsPage();
  } else {
    $("view").innerHTML = editorMarkup();
    if (entry.mode === "text") {
      $("entryBody").value = entry.body;
      sizeBody();
      renderPhotos();
    } else inkController = setupInk($("paperCanvas"));
    showStatus();
  }
  window.scrollTo(0,0);
}
function readEntry(id) {
  let text = "";
  for (let offset = 0; offset <= 1_050_000;) {
    const part = api.entry(id,offset);
    if (!part) break;
    text += part;
    offset += part.length;
  }
  return text ? JSON.parse(text) : null;
}
function openEditor(mode,id = "") {
  if (id && !validId(id)) return;
  let next;
  try {
    next = id ? readEntry(id) : {id:generateId(),day:(page === "home" && filterDate) || today(),title:"",body:"",mode,ink:[],photos:[],updated:0};
  } catch (_) { next = null; }
  if (!next) { notify("تعذّر فتح اليومية."); return; }
  closeSheet();
  if (inkController) inkController.destroy();
  inkController = null;
  entry = next;
  persisted = Boolean(id);
  dirty = false;
  undoStack = [];
  isErasing = false;
  switchPage("editor");
}
function editorMarkup() {
  const date = friendlyDate(entry.day,true);
  return '<div class="editor-meta"><button class="filter-button" type="button" data-act="entry-date" aria-label="تغيير تاريخ اليومية">' +
    icon("cal") + '<span id="entry-date-label">' + date + '</span></button><span id="saveState" class="status" role="status"></span></div>' +
    '<section class="panel paper"><input id="entryTitle" class="title-input" dir="rtl" placeholder="عنوان اختياري" ' +
    'aria-label="عنوان اليومية" maxlength="100" value="' + escapeHTML(entry.title) + '">' +
    (entry.mode === "ink" ? inkMarkup() : '<textarea id="entryBody" class="body-input" dir="rtl" rows="9" maxlength="80000" ' +
      'placeholder="ابدأ من أي لحظة تحب أن تتذكّرها…" aria-label="نص اليومية"></textarea><div id="entryPhotos" class="photos"></div><div class="between editor-footer"><button type="button" class="attach" data-act="add-photo">' +
      icon("image") + 'إضافة صور</button><small id="photoCount"></small></div>') + '</section>' +
    (entry.mode === "text" ? '<button class="prompt-launch" type="button" data-act="prompts">' +
      tile("text") + '<span><strong>أسئلة تساعدك على الكتابة</strong><small>قالب يومي وأسئلة للتأمّل</small></span>' +
      icon("left") + '</button>' : "");
}
function inkMarkup() {
  return '<div class="ink-tools" aria-label="أدوات الكتابة بالقلم">' +
    [['tool-pen','pen','قلم'],['tool-erase','erase','ممحاة'],['undo','undo','تراجع'],['redo','redo','إعادة']].map(([act,name,label]) =>
      '<button data-act="' + act + '" type="button"' + (act.startsWith("tool") ? ' aria-pressed="' + (act === "tool-pen") + '"' : "") +
      '>' + icon(name) + label + '</button>').join("") + '</div><div class="pen-sizes"><strong>سماكة القلم</strong>' +
    '<div aria-label="سماكة القلم" role="group">' + penSizes.map((label,index) =>
      '<button type="button" class="size-choice" data-act="pen-size" data-id="' + index +
      '" aria-pressed="' + (index === penSize) + '"><span aria-hidden="true" class="sample-dot dot-' +
      index + '"></span>' + label + '</button>').join("") + '</div></div><div class="canvas-frame">' +
    '<canvas id="paperCanvas" width="' + WIDTH + '" height="' + HEIGHT + '" aria-label="صفحة الكتابة بالقلم"></canvas></div>' +
    '<p class="ink-note">اكتب بالقلم أو بإصبعك. للتمرير اسحب من جانب الورقة.</p>';
}
function sizeBody() {
  const area = $("entryBody");
  if (area) {
    area.style.height = "0px";
    area.style.height = area.scrollHeight + "px";
  }
}
function renderPhotos() {
  if (!$("entryPhotos")) return;
  $("entryPhotos").innerHTML = entry.photos.filter(validId).map((id,index) =>
    '<button type="button" class="photo-tile" data-act="view-photo" data-id="' + id + '" aria-label="عرض الصورة ' +
    (index + 1) + '"><img loading="lazy" alt="" src="' + photoURL(id) + '"></button>').join("");
  $("photoCount").textContent = entry.photos.length ? entry.photos.length + " من " + MAX_PHOTOS + " صور" : "حتى ٦ صور";
  const control = $("view").querySelector('[data-act="add-photo"]');
  control.disabled = entry.photos.length >= MAX_PHOTOS;
}
function showStatus(error = false) {
  const state = $("saveState");
  if (!state) return;
  state.classList.toggle("error",error);
  state.textContent = error ? "تعذّر الحفظ، حاول مجددًا" : dirty ? "جارٍ الحفظ…" : persisted ? "حُفظت على جهازك" : "حفظ تلقائي";
}
function changed() {
  dirty = true;
  showStatus();
  clearTimeout(saveTimer);
  saveTimer = setTimeout(commit,550);
}
function meaningful() {
  return Boolean(entry.title.trim() || entry.body.trim() || entry.ink.length || entry.photos.length);
}
function commit() {
  clearTimeout(saveTimer);
  if (!entry || !dirty) return true;
  if (!persisted && !meaningful()) {
    dirty = false;
    showStatus();
    return true;
  }
  const {id,day,title,body,mode,ink} = entry;
  const content = JSON.stringify({id,day,title,body,mode,ink,updated:Date.now()});
  try {
    for (let from = 0; from < content.length;) {
      let to = Math.min(from + CHUNK,content.length);
      const last = content.charCodeAt(to - 1);
      if (to < content.length && last >= 0xD800 && last <= 0xDBFF) to--;
      api.stage(content.slice(from,to),from === 0);
      from = to;
    }
    if (!api.save()) throw new Error("save");
    persisted = true;
    dirty = false;
    showStatus();
    return true;
  } catch (_) { showStatus(true); return false; }
}
function leaveEditor() {
  if (inkController) inkController.finish();
  if (!commit()) { notify("تعذّر حفظ اليومية. حاول مرة أخرى."); return; }
  if ($("toast").textContent.startsWith("تعذّر حفظ اليومية")) $("toast").classList.remove("show");
  if (inkController) inkController.destroy();
  inkController = null;
  entry = null;
  switchPage("home");
}
function showSheet(name,title,content) {
  if (!dialog) beforeDialog = document.activeElement;
  dialog = name;
  $("sheet").setAttribute("aria-label",title);
  $("sheet").innerHTML = '<div class="handle"></div><div class="sheet-head"><h2>' + title +
    '</h2>' + (name === "busy" ? "" : iconButton("close","إغلاق","close")) + '</div>' + content;
  $("shade").hidden = false;
  $("shell").inert = true;
  document.body.classList.add("locked");
  $("sheet").focus();
}
function closeSheet() {
  if (!dialog) return;
  $("shade").hidden = true;
  $("shell").inert = false;
  document.body.classList.remove("locked");
  dialog = "";
  dialogValue = "";
  if (beforeDialog?.isConnected) beforeDialog.focus({preventScroll:true});
  beforeDialog = null;
}
function chooseMode() {
  showSheet("new","يومية جديدة",option("new-text","اكتب نصًا","أضف صورًا من يومك إذا أحببت","text") +
    option("new-ink","اكتب بالقلم","اكتب بخط يدك على صفحة مسطّرة","pen","sand"));
}
function dateSheet(isEntry) {
  const value = isEntry ? entry.day : filterDate || today();
  showSheet(isEntry ? "entry-date" : "filter","اختيار التاريخ",
    '<label class="field">التاريخ<input id="pickedDate" type="date" min="1900-01-01" max="' +
    (value > today() ? value : today()) + '" value="' + value + '" dir="ltr"></label><p id="dateError" class="error" hidden></p>' +
    '<div class="sheet-actions">' + button("save-date",isEntry ? "حفظ التاريخ" : "عرض هذا اليوم") +
    (isEntry ? "" : button("clear-date","كل الأيام","secondary")) + '</div>');
}
function saveDate() {
  const input = $("pickedDate");
  if (!input.value || !input.checkValidity()) {
    $("dateError").textContent = "اختر تاريخًا صحيحًا حتى اليوم.";
    $("dateError").hidden = false;
    return;
  }
  const chosen = input.value;
  if (dialog === "entry-date") {
    entry.day = chosen;
    $("entry-date-label").textContent = friendlyDate(chosen,true);
    changed();
    if (commit() && persisted && api.revisitData) savedRevisit();
    closeSheet();
    return;
  }
  filterDate = chosen;
  closeSheet();
  switchPage("home");
}
function entryMenu() {
  showSheet("entry-menu","خيارات اليومية", option("entry-date","تغيير التاريخ",friendlyDate(entry.day,true),"cal") +
    (persisted ? option("entry-revisit","ارجع لهذه اليومية",
      entry.revisit && !entry.revisit.delivered ? friendlyDate(entry.revisit.due,true) : "تذكير بعد أسبوع أو شهر أو سنة",
      "bell") + option("entry-export","تصدير هذه اليومية","ملف HTML مع كتابة اليد أو الصور","download") +
      option("delete-entry","حذف اليومية","إزالة هذه اليومية ومرفقاتها","trash","sand") : ""));
}
function openImage(id) {
  if (!validId(id) || !entry.photos.includes(id)) return;
  showSheet("photo","الصورة المرفقة",'<img class="photo-preview" src="' + photoURL(id) + '" alt="الصورة المرفقة">' +
    button("remove-photo","إزالة الصورة","secondary danger"));
  dialogValue = id;
}
function confirm(name,title,copy,label,action) {
  showSheet(name,title,'<p class="sheet-copy">' + copy + '</p><div class="sheet-actions">' +
    button("close","إلغاء","secondary") + button(action,label,"secondary danger") + '</div>');
}
function reminderSettings() {
  try { return JSON.parse(api.reminderSettings()); }
  catch (_) { return {enabled:false,allowed:false,time:"20:30"}; }
}
function reminderSheet() {
  const prefs = reminderSettings();
  showSheet("reminder","التذكيرات",'<div class="note">إذا كنت قد دوّنت في اليوم نفسه، تتخطّى اليوميات تذكير ذلك اليوم.</div>' +
    '<label class="toggle"><span><strong>تذكير يومي</strong><small>وقت قصير تكتب فيه لنفسك</small></span>' +
    '<input type="checkbox" role="switch" id="remindOn"' + (prefs.enabled ? " checked" : "") + '></label>' +
    '<label class="field">وقت التذكيرات<input id="remindTime" type="time" dir="ltr" value="' +
    (/^\d{2}:\d{2}$/.test(prefs.time) ? prefs.time : "20:30") + '"><small>للتدوين اليومي والعودة إلى يومية. وقت الوصول تقريبي.</small></label>' +
    '<p class="error" id="reminderError" hidden></p>' + button("remind-save","حفظ الإعدادات") + '<div id="reminderState" class="reminder-state"></div>');
  refreshReminder();
}
function refreshReminder() {
  refreshRevisitPermission();
  if (page === "settings") $("view").innerHTML = settingsPage();
  const element = $("reminderState");
  if (!element) return;
  const prefs = reminderSettings();
  if (!prefs.enabled) element.innerHTML = "";
  else if (prefs.allowed) element.innerHTML = button("remind-test","إرسال تذكير تجريبي","secondary");
  else element.innerHTML = '<p class="sheet-copy">اسمح بإشعارات اليوميات في أندرويد لتصلك التذكيرات.</p>' +
    button("remind-allow","السماح بالإشعارات","secondary");
}
function saveReminder() {
  const value = $("remindTime").value;
  if (!/^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(value)) {
    $("reminderError").textContent = "اختر وقتًا صحيحًا.";
    $("reminderError").hidden = false;
    return;
  }
  $("reminderError").hidden = true;
  const [hour,minute] = value.split(":").map(Number);
  api.configureReminder($("remindOn").checked,hour * 60 + minute);
  notify("حُفظت إعدادات التذكير");
}

function promptChooser() {
  showSheet("prompts","أسئلة تساعدك على الكتابة",
    '<div class="prompt-template">' + tile("text") +
      '<div><h3>قالب يومي سريع</h3><p>لحظة من اليوم، شعور أو تعلّم، وخطوة للغد.</p></div>' +
      '<button class="filter-button" type="button" data-act="prompt-template">استخدم القالب</button></div>' +
    '<h3 class="prompt-heading">أسئلة للتأمّل</h3><div class="prompt-grid">' +
    prompts.map(([title],index) => '<button class="prompt-item" type="button" data-act="prompt-preview" data-id="' +
      index + '">' + escapeHTML(title) + icon("left") + '</button>').join("") + '</div>');
}
function promptPreview(index) {
  const prompt = prompts[index];
  if (!prompt) return;
  showSheet("prompt-preview",prompt[0],'<p class="prompt-question">' + escapeHTML(prompt[1]) + '</p>' +
    '<div class="sheet-actions">' + button("prompts","كل الأسئلة","secondary") +
    button("prompt-add","أضف السؤال إلى اليومية") + '</div>');
  dialogValue = index;
}
function insertPrompt(withTemplate) {
  const area = $("entryBody");
  if (!area) return;
  const text = withTemplate ? dailyTemplate.join("\n\n") : prompts[dialogValue]?.[1];
  if (!text) return;
  const addition = (entry.body ? "\n\n" : "") + text + "\n\n";
  if (entry.body.length + addition.length > 80000) {
    notify("لا توجد مساحة كافية في هذه اليومية لإضافة السؤال.");
    return;
  }
  entry.body += addition;
  area.value = entry.body;
  closeSheet();
  sizeBody();
  changed();
  area.focus({preventScroll:true});
  area.setSelectionRange(area.value.length,area.value.length);
  area.scrollIntoView({block:"nearest"});
  notify(withTemplate ? "أُضيف القالب" : "أُضيف السؤال");
}
function savedRevisit() {
  const saved = api.revisitData(entry.id);
  if (saved) entry.revisit = JSON.parse(saved);
}
function revisitSheet() {
  if (!persisted) { notify("اكتب شيئًا أولًا حتى تحفظ اليومية وتضبط تذكيرها."); return; }
  savedRevisit();
  const clock = reminderSettings().time;
  let available = false;
  const options = revisitPeriods.map(([period,label]) => {
    const date = api.revisitDate(entry.day,period);
    const past = !date || date < today();
    if (!past) available = true;
    const selected = period === entry.revisit?.period;
    return '<label class="revisit-option' + (past ? ' past' : '') + '">' +
      '<span><strong>بعد ' + label + '</strong><small>' +
      (date ? friendlyDate(date,true) : "تاريخ غير متاح") + (past && !selected ? " · مضت المدة" : "") +
      '</small></span><input type="radio" name="revisit-period" value="' + period + '"' +
      (selected ? " checked" : "") + (past ? " disabled" : "") + '></label>';
  }).join("");
  const current = entry.revisit;
  const state = current ? (current.delivered ? "أُرسل التذكير." :
    "التذكير الحالي يوم " + friendlyDate(current.due,true) + ".") : "";
  showSheet("revisit","ارجع لهذه اليومية",
    '<p class="sheet-copy">تذكير مرة واحدة بعد هذه المدة من تاريخ اليومية. وقت التذكيرات ' +
    '<bdi dir="ltr">' + escapeHTML(clock) + '</bdi>، وتقدر تعدّله في الإعدادات.</p>' + options +
    (state ? '<div class="note revisit-current">' + state + '</div>' : "") +
    '<div id="revisitState"></div><div class="sheet-actions">' +
    (current ? button("revisit-delete","إلغاء التذكير","secondary") : "") +
    (available ? button("revisit-save","حفظ الموعد") : "") + '</div>');
  refreshRevisitPermission();
}
function refreshRevisitPermission() {
  const node = $("revisitState");
  if (!node) return;
  const waiting = entry?.revisit && !entry.revisit.delivered;
  node.innerHTML = waiting && !api.revisitAllowed() ?
    '<p class="sheet-copy">اسمح بالإشعارات لتصلك الذكرى في موعدها.</p>' +
    button("revisit-allow","السماح بالإشعارات","secondary") : "";
}
function saveRevisit(remove = false) {
  const interval = remove ? "" : $("sheet").querySelector('input[name="revisit-period"]:checked:not(:disabled)')?.value;
  if (interval === undefined) { notify("اختر مدة للتذكير."); return; }
  const data = api.setRevisit(entry.id,interval);
  if (!data) { notify("تعذّر حفظ موعد العودة."); return; }
  entry.revisit = JSON.parse(data);
  if (remove || api.revisitAllowed()) closeSheet();
  else revisitSheet();
  notify(remove ? "أُلغي تذكير العودة" : "حُفظ تذكير العودة");
}

function setupInk(canvas) {
  const ctx = canvas.getContext("2d");
  let active = null;
  let frameId = 0;
  let penAt = 0;
  let points = entry.ink.reduce((sum,stroke) => sum + stroke[1].length,0);

  function renderStroke(stroke,start = 0) {
    const [erase,path,size = 1] = stroke;
    if (!path.length || start >= path.length) return;
    ctx.globalCompositeOperation = erase ? "destination-out" : "source-over";
    ctx.strokeStyle = ctx.fillStyle = "#193d35";
    ctx.lineCap = "round";
    const multiplier = [.65,1,1.8][size];
    const weight = (a,b) => erase ? 24 : (1.6 + (a[2] + b[2]) / 200 * 5.2) * multiplier;
    if (start === 0) {
      ctx.beginPath();
      ctx.arc(path[0][0],path[0][1],weight(path[0],path[0]) / 2,0,Math.PI * 2);
      ctx.fill();
    }
    for (let i = Math.max(1,start); i < path.length; i++) {
      const a = path[i-1], b = path[i];
      ctx.lineWidth = weight(a,b);
      ctx.beginPath();
      ctx.moveTo(a[0],a[1]);
      ctx.lineTo(b[0],b[1]);
      ctx.stroke();
    }
    ctx.globalCompositeOperation = "source-over";
  }
  function repaint() {
    ctx.clearRect(0,0,WIDTH,HEIGHT);
    for (const stroke of entry.ink) renderStroke(stroke);
    if (active) {
      renderStroke(active.stroke);
      active.drawn = active.stroke[1].length;
    }
  }
  function resize() {
    const {width,height} = canvas.getBoundingClientRect();
    const dpr = Math.min(3,window.devicePixelRatio || 1);
    const w = Math.round(width * dpr), h = Math.round(height * dpr);
    if (!w || !h || (canvas.width === w && canvas.height === h)) return;
    canvas.width = w;
    canvas.height = h;
    ctx.setTransform(w / WIDTH,0,0,h / HEIGHT,0,0);
    repaint();
  }
  function drawPending() {
    frameId = 0;
    if (!active) return;
    renderStroke(active.stroke,active.drawn);
    active.drawn = active.stroke[1].length;
  }
  function append(samples) {
    const rect = canvas.getBoundingClientRect();
    const path = active.stroke[1];
    for (const event of samples) {
      if (points + path.length >= MAX_POINTS) break;
      const x = Math.round(Math.max(0,Math.min(WIDTH,(event.clientX - rect.left) / rect.width * WIDTH)));
      const y = Math.round(Math.max(0,Math.min(HEIGHT,(event.clientY - rect.top) / rect.height * HEIGHT)));
      const pressure = active.kind === "pen" ? Math.round((event.pressure || .45) * 100) : 55;
      const last = path[path.length-1];
      if (!last || last[0] !== x || last[1] !== y) path.push([x,y,pressure]);
    }
    if (!frameId) frameId = requestAnimationFrame(drawPending);
  }
  function start(event) {
    if (event.pointerType === "pen") penAt = performance.now();
    if (event.button !== 0 || (event.pointerType === "touch" && penAt && performance.now() - penAt < 900)) return;
    if (active && active.kind === "touch" && event.pointerType === "pen") {
      active = null;
      repaint();
    }
    if (active) return;
    if (points >= MAX_POINTS) { notify("امتلأت الصفحة. يمكنك بدء يومية جديدة."); return; }
    event.preventDefault();
    const stroke = isErasing || penSize === 1 ? [isErasing,[]] : [false,[],penSize];
    active = {id:event.pointerId,kind:event.pointerType,stroke,drawn:0};
    canvas.setPointerCapture(event.pointerId);
    append([event]);
  }
  function move(event) {
    if (event.pointerType === "pen") penAt = performance.now();
    if (!active || active.id !== event.pointerId) return;
    const samples = event.getCoalescedEvents?.() || [];
    append(samples.length ? samples : [event]);
  }
  function finish(event) {
    if (!active || (event && active.id !== event.pointerId)) return;
    if (frameId) cancelAnimationFrame(frameId);
    drawPending();
    const stroke = active.stroke;
    active = null;
    if (stroke[1].length) {
      entry.ink.push(stroke);
      points += stroke[1].length;
      undoStack = [];
      changed();
      syncTools();
    }
  }
  function syncTools() {
    const tools = $("view").querySelector(".ink-tools");
    if (!tools) return;
    tools.querySelector('[data-act="tool-pen"]').setAttribute("aria-pressed",String(!isErasing));
    tools.querySelector('[data-act="tool-erase"]').setAttribute("aria-pressed",String(isErasing));
    tools.querySelector('[data-act="undo"]').disabled = !entry.ink.length;
    tools.querySelector('[data-act="redo"]').disabled = !undoStack.length;
    $("view").querySelectorAll(".size-choice").forEach(choice => {
      choice.setAttribute("aria-pressed",String(Number(choice.dataset.id) === penSize));
    });
  }
  canvas.addEventListener("pointerdown",start);
  canvas.addEventListener("pointermove",move);
  canvas.addEventListener("pointerup",finish);
  canvas.addEventListener("pointercancel",finish);
  canvas.addEventListener("lostpointercapture",finish);
  const observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
  repaint();
  syncTools();
  return {
    finish,
    undo() {
      finish();
      if (!entry.ink.length) return;
      const stroke = entry.ink.pop();
      points -= stroke[1].length;
      undoStack.push(stroke);
      repaint();
      changed();
      syncTools();
    },
    redo() {
      finish();
      if (!undoStack.length) return;
      const stroke = undoStack.pop();
      entry.ink.push(stroke);
      points += stroke[1].length;
      repaint();
      changed();
      syncTools();
    },
    syncTools,
    destroy() {
      observer.disconnect();
      if (frameId) cancelAnimationFrame(frameId);
    }
  };
}

function handle(action,id) {
  if (work) return;
  switch (action) {
    case "home": case "settings": switchPage(action); break;
    case "new": chooseMode(); break;
    case "new-text": openEditor("text"); break;
    case "new-ink": openEditor("ink"); break;
    case "done": leaveEditor(); break;
    case "entry-menu": if (commit()) entryMenu(); break;
    case "open": openEditor(null,id); break;
    case "more": loadList(true); break;
    case "filter": dateSheet(false); break;
    case "entry-date": dateSheet(true); break;
    case "entry-revisit": revisitSheet(); break;
    case "revisit-save": saveRevisit(); break;
    case "revisit-delete": saveRevisit(true); break;
    case "revisit-allow": api.revisitPermission(); break;
    case "prompts": promptChooser(); break;
    case "prompt-preview": promptPreview(Number(id)); break;
    case "prompt-add": insertPrompt(false); break;
    case "prompt-template": insertPrompt(true); break;
    case "save-date": saveDate(); break;
    case "clear-date": filterDate = ""; closeSheet(); switchPage("home"); break;
    case "close": closeSheet(); break;
    case "add-photo":
      if (commit()) api.addPhotos(entry.id,entry.day,MAX_PHOTOS - entry.photos.length);
      break;
    case "view-photo": openImage(id); break;
    case "remove-photo": {
      const photo = dialogValue;
      confirm("remove-photo","إزالة الصورة؟","ستُزال هذه الصورة من اليومية.","إزالة","confirm-photo");
      dialogValue = photo;
      break;
    }
    case "confirm-photo":
      if (api.removePhoto(entry.id,dialogValue)) {
        entry.photos = JSON.parse(api.photos(entry.id));
        closeSheet();
        renderPhotos();
        notify("أُزيلت الصورة");
      } else notify("تعذّرت إزالة الصورة.");
      break;
    case "delete-entry":
      confirm("delete-entry","حذف اليومية؟","ستُحذف اليومية ومرفقاتها من السجل.","حذف","confirm-delete");
      break;
    case "confirm-delete":
      if (api.delete(entry.id)) {
        dirty = false;
        closeSheet();
        leaveEditor();
        notify("حُذفت اليومية");
      } else notify("تعذّر حذف اليومية.");
      break;
    case "tool-pen": case "tool-erase":
      isErasing = action === "tool-erase";
      inkController.syncTools();
      break;
    case "pen-size":
      penSize = Number(id);
      if (api.setPenSize) api.setPenSize(penSize);
      isErasing = false;
      inkController.syncTools();
      break;
    case "undo": inkController.undo(); break;
    case "redo": inkController.redo(); break;
    case "backup": api.document("backup",""); break;
    case "export": api.document("html",""); break;
    case "entry-export": if (commit()) { closeSheet(); api.document("html",entry.id); } break;
    case "restore":
      confirm("restore","استعادة نسخة احتياطية؟","سيحلّ السجل الموجود في النسخة محلّ يومياتك الحالية. يمكنك حفظ نسخة من سجلّك أولًا.","اختيار النسخة","confirm-restore");
      break;
    case "confirm-restore": closeSheet(); api.document("restore",""); break;
    case "reminder": reminderSheet(); break;
    case "remind-save": saveReminder(); break;
    case "remind-allow": api.reminderPermission(); break;
    case "remind-test": notify(api.reminderTest() ? "أُرسل تذكير تجريبي" : "اسمح بإشعارات اليوميات أولًا."); break;
  }
}

document.addEventListener("click",event => {
  if (work) return;
  if (event.target === $("shade")) closeSheet();
  const action = event.target.closest("button[data-act]");
  if (action) handle(action.dataset.act,action.dataset.id);
});
document.addEventListener("input",event => {
  const {id,value} = event.target;
  if (id === "search") {
    search = value;
    clearTimeout(listTimer);
    listTimer = setTimeout(loadList,130);
  }
  if (entry && id === "entryTitle") { entry.title = value; changed(); }
  if (entry && id === "entryBody") { entry.body = value; sizeBody(); changed(); }
});
window.addEventListener("resize",sizeBody);
document.addEventListener("keydown",event => {
  if (!dialog || work) return;
  if (event.key === "Escape") { event.preventDefault(); closeSheet(); return; }
  if (event.key !== "Tab") return;
  const focusable = [...$("sheet").querySelectorAll("button:not(:disabled),input")];
  if (!focusable.length) { event.preventDefault(); return; }
  if (event.shiftKey && [focusable[0],$("sheet")].includes(document.activeElement)) {
    event.preventDefault();
    focusable[focusable.length - 1].focus();
  } else if (!event.shiftKey && document.activeElement === focusable[focusable.length - 1]) {
    event.preventDefault();
    focusable[0].focus();
  }
});
window.journalBack = () => {
  if (work) return true;
  if (dialog === "prompt-preview") { promptChooser(); return true; }
  if (dialog) { closeSheet(); return true; }
  if (page === "editor") { leaveEditor(); return true; }
  if (page === "settings") { switchPage("home"); return true; }
  return false;
};
window.journalFlush = () => { if (inkController) inkController.finish(); return commit(); };
document.addEventListener("visibilitychange",() => { if (document.hidden) window.journalFlush(); });
window.journalPhotosChanged = id => {
  if (entry?.id === id) {
    entry.photos = JSON.parse(api.photos(id));
    persisted = true;
    renderPhotos();
    showStatus();
  } else if (page === "home") loadList();
};
window.journalProcessing = active => {
  work = active;
  if (active) showSheet("busy","جارٍ العمل",'<p class="sheet-copy">لحظات ويكتمل حفظ الملف.</p>');
  else if (dialog === "busy") closeSheet();
};
window.journalNativeResult = (ok,text,kind) => {
  if (ok && kind === "restore") {
    filterDate = "";
    search = "";
    switchPage("home");
  }
  notify(text);
  if (waitingRevisit) {
    const id = waitingRevisit;
    waitingRevisit = "";
    window.journalRevisitOpened(id);
  }
};
window.journalRevisitOpened = id => {
  if (!validId(id) || entry?.id === id) return;
  if (work) { waitingRevisit = id; return; }
  if (window.journalFlush()) openEditor(null,id);
};
window.journalReminderChanged = refreshReminder;
window.journalReminderOpened = () => {
  if (page !== "editor" && !work) chooseMode();
};

switchPage("home");
