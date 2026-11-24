"use strict";

const drawings = {
  wallet: '<rect x="3" y="6" width="17" height="14" rx="3"/><path d="M4 7V6a2 2 0 0 1 2-2h11m5 7h-6a2 2 0 0 0 0 4h6v-4Z"/>',
  home: '<path d="m3 10 9-7 9 7v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 21v-7h6v7"/>',
  list: '<rect x="5" y="3" width="14" height="18" rx="3"/><path d="M9 8h6m-6 4h6m-6 4h4"/>',
  chart: '<path d="M3 3v16a2 2 0 0 0 2 2h16M8 16v-4m5 4V6m5 10V9"/>',
  settings: '<path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="3" fill="white"/><circle cx="15" cy="17" r="3" fill="white"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  cal: '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 10h18m-12 5h1m4 0h1"/>',
  arrow: '<path d="m14 6-6 6 6 6"/>',
  right: '<path d="m10 6 6 6-6 6"/>',
  down: '<path d="m6 9 6 6 6-6"/>',
  eye: '<path d="M2 12s3.7-6 10-6 10 6 10 6-3.7 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/>',
  hide: '<path d="m3 3 18 18M11 6c6.5-.5 11 6 11 6a17 17 0 0 1-3 3.3M6 7.5A14 14 0 0 0 2 12s3.7 6 10 6c1.2 0 2.3-.2 3.3-.6"/>',
  target: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.8"/><circle cx="12" cy="12" r="1.3"/>',
  leaf: '<path d="M20 4c-.4 5.8-2.2 12-9 12-2.7 0-5-2.1-5-4.8C6 5 14 7 20 4ZM4 20c2.5-4 5.8-6.4 9.6-8.5"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  dot: '<circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0v4.5c0 1.5-.7 3-2 4.5h16c-1.3-1.5-2-3-2-4.5V8Zm-8 12a2 2 0 0 0 4 0"/>',
  spark: '<path d="m12 2 1.9 6.1L20 10l-6.1 1.9L12 18l-1.9-6.1L4 10l6.1-1.9L12 2Zm7 14v5m-2.5-2.5h5"/>',
  food: '<path d="M4 4v5c0 1.5 1 2.5 3 2.5s3-1 3-2.5V4M7 4v16m10 0V4c-2 1.1-3 3.8-3 7h3"/>',
  transport: '<path d="m5 10 1.5-4A2 2 0 0 1 8.4 5h7.2A2 2 0 0 1 17.5 6l1.5 4M5 17v2m14-2v2"/><rect x="3" y="10" width="18" height="7" rx="2"/><path d="M7 13.5h1m8 0h1"/>',
  market: '<path d="M5 8h14l1 12H4L5 8zm4 1V6a3 3 0 0 1 6 0v3"/>',
  bill: '<path d="M5 3h14v18l-3-2-4 2-4-2-3 2zM9 8h6m-6 4h6m-6 4h3"/>',
  health: '<rect x="4" y="4" width="16" height="16" rx="5"/><path d="M12 8v8m-4-4h8"/>',
  study: '<path d="m2 9 10-5 10 5-10 5-10-5zm4 2v5c2.8 2.4 9.2 2.4 12 0v-5m4-2v6"/>',
  play: '<path d="m12 3 2.9 5.7 6.3.9-4.6 4.5 1.1 6.3-5.7-3-5.7 3 1.1-6.3-4.6-4.5 6.3-.9L12 3Z"/>',
  other: '<rect x="4" y="4" width="16" height="16" rx="5"/><path d="M8 12h.1m3.9 0h.1m3.9 0h.1" stroke-width="2.5"/>',
  search: '<circle cx="10.5" cy="10.5" r="5.5"/><path d="m15 15 4.5 4.5"/>',
  close: '<path d="m5 5 14 14M19 5 5 19"/>',
  erase: '<path d="M9 5h11a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9l-7-7 7-7Zm4 4 6 6m0-6-6 6"/>',
  edit: '<path d="m13 6 5 5m-13 8 1-5L16 4a2 2 0 0 1 3 0l1 1a2 2 0 0 1 0 3L10 18l-5 1Z"/>',
  download: '<path d="M12 3v12m-4-4 4 4 4-4M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/>',
  upload: '<path d="M12 15V3m-4 4 4-4 4 4M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/>',
  shield: '<path d="M12 3 4 6v6c0 4 3 7 8 9 5-2 8-5 8-9V6l-8-3Z"/><path d="m8 12 3 3 5-6"/>',
  repeat: '<path d="m15 3 4 4-4 4m4-4H7a3 3 0 0 0-3 3v1m5 10-4-4 4-4m-4 4h12a3 3 0 0 0 3-3v-1"/>'
};
const categories = [
  {id:"food", label:"أكل وشرب", bg:"#ffebdd", ink:"#9c4725", accent:"#e78355"},
  {id:"transport", label:"مواصلات", bg:"#e8e9ff", ink:"#4d54a4", accent:"#828aee"},
  {id:"market", label:"تسوّق", bg:"#fce8f2", ink:"#963d72", accent:"#d774a7"},
  {id:"bill", label:"فواتير", bg:"#e5f4ff", ink:"#236289", accent:"#5dacdc"},
  {id:"health", label:"صحة", bg:"#e1f5eb", ink:"#247451", accent:"#65b991"},
  {id:"study", label:"دراسة", bg:"#eee7ff", ink:"#6341b0", accent:"#9a7bea"},
  {id:"play", label:"ترفيه", bg:"#fff2ce", ink:"#845b19", accent:"#e6b354"},
  {id:"other", label:"أخرى", bg:"#efecf3", ink:"#675d75", accent:"#9d91ae"}
];
const catById = Object.fromEntries(categories.map(item => [item.id, item]));
const currencies = {SYP:"ل.س", USD:"$", EUR:"€", SAR:"ر.س", AED:"د.إ", TRY:"₺", JOD:"د.أ", EGP:"ج.م"};
const months = ["كانون الثاني","شباط","آذار","نيسان","أيار","حزيران","تموز","آب","أيلول","تشرين الأول","تشرين الثاني","كانون الأول"];
const weekday = ["الأحد","الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت"];
const maxAmount = 999999999999;
const maxRecords = 20000;
const format = new Intl.NumberFormat("en-US", {maximumFractionDigits:2});
const native = window.WalletNative || null;
const $ = id => document.getElementById(id);
const icon = name => '<svg viewBox="0 0 24 24" class="i" aria-hidden="true">' + drawings[name] + '</svg>';
const esc = text => String(text).replace(/[&<>"']/g, char => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[char]);
const cleanInput = value => value.replace(/[٠-٩۰-۹]/g, digit => String((digit.charCodeAt(0) & 15))).replace(/[٫،,]/g, ".").replace(/٬/g,"");
const hasOwn = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
const pad = value => String(value).padStart(2, "0");

function localDate(date = new Date()) {
  return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
}
function dateObject(date) {
  const [year, month, day] = date.split("-").map(Number);
  return new Date(year, month - 1, day || 1, 12);
}
function validDate(date) {
  return typeof date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(date) &&
    date >= "1900-01-01" && localDate(dateObject(date)) === date;
}
function moveMonth(month, direction) {
  const date = dateObject(month);
  date.setMonth(date.getMonth() + direction);
  return localDate(date).slice(0, 7);
}
function movedDay(date, direction) {
  const time = dateObject(date);
  time.setDate(time.getDate() + direction);
  return localDate(time);
}
const monthNow = () => localDate().slice(0, 7);
function monthlyName(month) {
  const [year, index] = month.split("-").map(Number);
  return months[index - 1] + " " + year;
}
function shortDate(date) {
  return Number(date.slice(8)) + " " + months[Number(date.slice(5,7)) - 1];
}
const humanDate = date => date === localDate() ? "اليوم" : date === movedDay(localDate(),-1) ? "أمس" : shortDate(date);
const validAmount = amount => Number.isSafeInteger(amount) && amount > 0 && amount <= maxAmount;
const amountText = cents => format.format(cents / 100);
const amountMarkup = cents => '<bdi class="num" dir="ltr">' + amountText(cents) + '</bdi>';
const total = records => records.reduce((sum, item) => sum + item.cents, 0);
function parseAmount(text) {
  const edited = cleanInput(text.trim()).replace(/^(?:\.)/, "0.");
  if (!/^\d{1,10}(?:\.\d{0,2})?$/.test(edited)) return 0;
  const [whole, frac = ""] = edited.split(".");
  return Number(whole) * 100 + Number(frac.padEnd(2, "0"));
}
function parseState(text) {
  const raw = JSON.parse(text);
  if (!raw || raw.version !== 1 || !hasOwn(currencies, raw.currency) ||
      !Array.isArray(raw.records) || raw.records.length > maxRecords ||
      !raw.budgets || typeof raw.budgets !== "object" || Array.isArray(raw.budgets)) throw new Error("format");
  const ids = new Set();
  const records = raw.records.map(record => {
    if (!record || typeof record.id !== "string" || !/^[\da-z-]{1,80}$/.test(record.id) ||
      ids.has(record.id) || !validDate(record.date) || !validAmount(record.cents) ||
      !hasOwn(catById, record.cat) || typeof record.note !== "string" ||
      record.note.length > 140 || !Number.isSafeInteger(record.created) || record.created < 0) throw new Error("record");
    ids.add(record.id);
    return {id:record.id, date:record.date, cents:record.cents, cat:record.cat, note:record.note, created:record.created};
  });
  const budgets = {};
  for (const [month, budget] of Object.entries(raw.budgets)) {
    if (!/^(?:19\d{2}|[2-9]\d{3})-(?:0[1-9]|1[0-2])$/.test(month) || !validAmount(budget)) throw new Error("budget");
    budgets[month] = budget;
  }
  return {version:1, currency:raw.currency, records, budgets, hide:raw.hide === true,
    recent:raw.recent && hasOwn(catById, raw.recent) ? raw.recent : "food"};
}

const emptyState = () => ({version:1,currency:"SYP",records:[],budgets:{},hide:false,recent:"food"});
function readNative(backup = false) {
  let result = "";
  for (let offset = 0; ;) {
    const part = native.read(backup, offset);
    if (!part) return result;
    result += part;
    offset += part.length;
  }
}
function sendNative(text) {
  for (let offset = 0; offset < text.length;) {
    let end = Math.min(text.length, offset + 40000);
    const last = text.charCodeAt(end - 1);
    if (end < text.length && last >= 0xD800 && last <= 0xDBFF) end--;
    native.stage(text.slice(offset,end), offset === 0);
    offset = end;
  }
}
let state = emptyState();
let loadFailed = false;
try {
  const saved = native ? readNative() : localStorage.getItem("almahfaza");
  if (saved) state = parseState(saved);
} catch (_) { loadFailed = true; }
let demo = null;
let page = "home";
let selectedMonth = monthNow();
let chartPeriod = -1;
let filter = "";
let query = "";
let activeSheet = null;
let draft = null;
let previousFocus = null;
let toastTimer;
let sheetAction = null;
let openedReview = null;
const hasCoach = Boolean(native?.coachSettings);
const current = () => demo || state;
const currencyLabel = () => currencies[current().currency];
const money = cents => '<span class="amount">' + amountMarkup(cents) + " " + esc(currencyLabel()) + '</span>';
const monthRecords = month => current().records.filter(record => record.date.startsWith(month));
const sortRecords = records => [...records].sort((a,b) => b.date.localeCompare(a.date) || b.created - a.created);
const tile = (name, tone = "") => '<span class="tile ' + tone + '">' + icon(name) + '</span>';
function catTile(cat) {
  const c = catById[cat];
  return '<span class="tile" style="--tile-bg:' + c.bg + ';--tile-ink:' + c.ink + '">' + icon(c.id) + '</span>';
}
function notify(message) {
  $("toast").textContent = message;
  $("toast").classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $("toast").classList.remove("show"), 2700);
}
function feedback() {
  if (native) native.feedback();
}
function save(next, allowRepair = false) {
  if (demo) { demo = next; return true; }
  if (loadFailed && !allowRepair) {
    notify("تعذّر قراءة السجل. يمكنك استعادة نسخة احتياطية من الإعدادات.");
    return false;
  }
  try {
    const json = JSON.stringify(next);
    if (native) {
      sendNative(json);
      if (!native.save()) throw new Error("storage");
    } else {
      localStorage.setItem("almahfaza", json);
    }
    state = next;
    loadFailed = false;
    return true;
  } catch (_) {
    notify("تعذّر الحفظ. حاول مجددًا أو صدّر نسخة من سجلك.");
    return false;
  }
}
function createDemo() {
  const base = emptyState();
  base.budgets[monthNow()] = 45000000;
  const rows = [
    [0,1800000,"food","غداء الجامعة"],[0,1000000,"transport","مواصلات إلى المشفى"],
    [1,2800000,"study","مستلزمات الدراسة"],[2,8400000,"market","مشتريات البيت"],
    [3,1400000,"food","قهوة وفطور"],[5,7000000,"bill","فاتورة الإنترنت"],
    [7,4600000,"health","الصيدلية"],[10,1900000,"play","طلعة مع الأصدقاء"],
    [13,1800000,"food","وجبة بعد الدوام"],[17,6300000,"market","مشتريات أسبوعية"]
  ];
  base.records = rows.map(([offset,cents,cat,note], index) => ({
    id:"demo-" + index,date:movedDay(localDate(),-offset),cents,cat,note,created:100-index
  }));
  return base;
}
const navItems = [
  ["home","الرئيسية","home"],["log","السجل","list"],["add","إضافة","plus"],
  ["insights","التحليلات","chart"],["settings","الإعدادات","settings"]
];
const arrowButton = (action, label, mark, disabled = false) =>
  '<button type="button" class="icon-button" data-act="' + action + '" aria-label="' + label + '"' +
  (disabled ? ' disabled' : '') + '>' + icon(mark) + '</button>';
const textButton = (action, title, mark = "arrow") =>
  '<button type="button" class="text-button" data-act="' + action + '">' + title + icon(mark) + '</button>';
const smallButton = (action, title) =>
  '<button type="button" class="small-button" data-act="' + action + '">' + title + '</button>';
function appFrame() {
  $("top").innerHTML = '<div class="brand"><span class="brand-mark">' + icon("wallet") +
    '</span><span>المحفظة</span></div><div class="top-tools">' +
    (demo ? '<button class="demo" type="button" data-act="demo" aria-label="إنهاء وضع التجربة">وضع التجربة&nbsp; ×</button>' : "") +
    arrowButton("privacy", current().hide ? "إظهار المبالغ في الرئيسية" : "إخفاء المبالغ في الرئيسية",current().hide ? "hide" : "eye") +
    '</div>';
  $("nav").innerHTML = navItems.map(([key, title, symbol]) => '<button type="button" class="nav-item' +
    (key === "add" ? ' nav-add' : '') + '" data-act="' + key + '"' + (key === page ? ' aria-current="page"' : '') +
    '><span class="nav-icon">' + icon(symbol) + '</span><span>' + title + '</span></button>').join("");
}
function emptyCard(title, copy, action = "", label = "") {
  return '<div class="panel empty">' + tile("list") + '<div class="empty-text"><h3 class="panel-title">' +
    title + '</h3><p>' + copy + '</p></div>' + (action ? smallButton(action,label) : "") + '</div>';
}
function row(record, masked = false) {
  const label = catById[record.cat].label;
  return '<button type="button" class="row" data-act="edit" data-id="' + record.id + '">' + catTile(record.cat) +
    '<span class="row-copy"><strong>' + esc(record.note || label) + '</strong><small>' +
    (record.note ? label + " · " : "") + humanDate(record.date) + '</small></span><strong class="amount">' +
    (masked ? '<bdi aria-label="المبلغ مخفي">••••</bdi>' : amountMarkup(record.cents) + '<small>' + esc(currencyLabel()) + '</small>') +
    '</strong></button>';
}
function budgetCard(month, spent, masked = false) {
  const cap = current().budgets[month];
  let content = "";
  if (cap) {
    const pct = Math.min(100, spent / cap * 100);
    const remainder = cap - spent;
    const budgetLabel = masked ? "المبلغ مخفي" : money(cap);
    const remainingLabel = masked ? "••••" : money(Math.abs(remainder));
    content = '<div class="progress' + (remainder < 0 ? ' over' : '') + '" role="progressbar" aria-label="استخدام الميزانية" aria-valuemin="0" aria-valuemax="100" aria-valuenow="' + Math.round(pct) +
      '"><span style="--value:' + pct + '%"></span></div><div class="between progress-label">' +
      '<span>السقف ' + budgetLabel + '</span><strong class="' + (remainder < 0 ? 'danger' : '') + '">' +
      (remainder < 0 ? 'تجاوزت السقف بـ ' : 'متبقٍ ') + remainingLabel + '</strong></div>';
  }
  return '<section class="panel"><div class="panel-head"><div class="title-group">' + tile("target") +
    '<div><h2 class="panel-title">ميزانية الشهر</h2>' +
    (!cap ? '<p class="panel-sub">حدّد سقفًا للصرف وتابع المتبقّي.</p>' : '') +
    '</div></div>' + smallButton("budget",cap ? "تعديل" : "ضع ميزانية") + '</div>' + content + '</section>';
}
function activityCard() {
  const dates = new Set(current().records.map(record => record.date));
  const today = localDate();
  let count = 0;
  const days = Array.from({length:7}, (_, index) => {
    const date = movedDay(today,index-6);
    const logged = dates.has(date);
    count += Number(logged);
    const day = weekday[dateObject(date).getDay()];
    return '<div class="day" aria-label="' + day + (logged ? '، سُجّل مصروف' : '، لا مصروف مسجّل') + '"><span class="daybox' +
      (logged ? ' logged' : '') + (index === 6 ? ' today' : '') + '">' + icon(logged ? "check" : "dot") +
      '</span>' + (index === 6 ? "اليوم" : day) + '</div>';
  }).join("");
  return '<section class="panel"><div class="panel-head"><div class="title-group">' + tile("leaf","green") +
    '<div><h2 class="panel-title">عادة التسجيل</h2><p class="panel-sub">آخر ٧ أيام</p></div></div>' +
    '<span class="count-label"><bdi>' + count + ' / 7</bdi> أيام</span></div><div class="week">' + days + '</div></section>';
}
function home() {
  const today = localDate();
  const month = monthNow();
  const records = monthRecords(month);
  const spent = total(records);
  const text = amountText(spent);
  const masked = current().hide;
  const size = text.length > 11 ? " extra" : text.length > 7 ? " long" : "";
  const recent = sortRecords(current().records).slice(0,4);
  return '<div class="intro"><div><h1>نظرة على مصروفك</h1><p>' + weekday[dateObject(today).getDay()] +
    '، ' + shortDate(today) + '</p></div><span class="date-pill">' + icon("cal") + monthlyName(month) + '</span></div>' +
    '<section class="hero"><img src="wallet.webp" alt="" width="504" height="420" style="height:auto"><span class="hero-label">' +
    icon("wallet") + ' إجمالي مصروف هذا الشهر</span><div class="hero-money"><b class="' + (masked ? '' : size) +
    '"><bdi dir="ltr" aria-label="' + (masked ? "المبلغ مخفي" : text) + '">' + (masked ? "••••" : text) +
    '</bdi></b><small>' + esc(currencyLabel()) + '</small></div><button class="hero-link" type="button" data-act="insights">' +
    'تحليلات الشهر' + icon("arrow") + '</button></section><div class="split">' +
    budgetCard(month,spent,masked) + activityCard() + '</div><div class="section-head"><h2>آخر المصروفات</h2>' + textButton("log","كل السجل") +
    '</div>' + (recent.length ? '<div class="panel rows">' + recent.map(item => row(item,masked)).join("") + '</div>' :
    emptyCard("ابدأ بأول مصروف","اختَر فئة وأدخل المبلغ.","add","سجّل الآن"));
}
function monthPicker() {
  const previous = moveMonth(selectedMonth,-1);
  const next = moveMonth(selectedMonth,1);
  return '<div class="month-picker">' +
    arrowButton("prev-month","الشهر السابق","right",previous < "1900-01") +
    '<button type="button" class="month-title" data-act="select-month" aria-label="اختيار الشهر">' + monthlyName(selectedMonth) + icon("down") + '</button>' +
    arrowButton("next-month","الشهر التالي","arrow",next > monthNow()) + '</div>';
}
function log() {
  return '<div class="page-title"><h1>سجل المصروفات</h1><p>ابحث في عملياتك أو عدّل أي مصروف من القائمة.</p></div>' +
    monthPicker() + '<label class="search-field">' + icon("search") +
    '<input id="search" type="search" aria-label="البحث في المصروفات" placeholder="ابحث عن مصروف أو فئة" maxlength="140" value="' +
    esc(query) + '"></label><div class="chips" aria-label="تصفية حسب الفئة">' +
    [{id:"",label:"الكل"}, ...categories].map(item =>
      '<button type="button" class="chip" data-act="filter" data-id="' + item.id + '" aria-pressed="' +
      (filter === item.id) + '">' + item.label + '</button>').join("") + '</div><div id="results"></div>';
}
function renderLogRows() {
  const search = query.trim().toLowerCase();
  const list = sortRecords(monthRecords(selectedMonth).filter(record => (!filter || record.cat === filter) &&
    (!search || (record.note + " " + catById[record.cat].label).toLowerCase().includes(search))));
  if (!list.length) {
    $("results").innerHTML = emptyCard(search || filter ? "لا نتائج مطابقة" : "السجل فارغ",
      search || filter ? "جرّب كلمة أو فئة أخرى." : "ستظهر مصروفات هذا الشهر هنا.", search || filter ? "clear" : "add",
      search || filter ? "مسح التصفية" : "إضافة");
    return;
  }
  const groups = new Map();
  for (const record of list) {
    if (!groups.has(record.date)) groups.set(record.date, []);
    groups.get(record.date).push(record);
  }
  $("results").innerHTML = '<p class="group-title">' + list.length + ' عملية · ' + money(total(list)) + '</p>' +
    [...groups].map(([date, records]) => '<div class="between group-title"><strong>' + humanDate(date) +
      '</strong><span>' + money(total(records)) + '</span></div><div class="panel rows">' + records.map(item => row(item)).join("") + '</div>').join("");
}
function insights() {
  const rows = monthRecords(selectedMonth);
  const spent = total(rows);
  const prior = selectedMonth > "1900-01" ? total(monthRecords(moveMonth(selectedMonth,-1))) : 0;
  const monthLength = new Date(Number(selectedMonth.slice(0,4)),Number(selectedMonth.slice(5,7)),0).getDate();
  const days = selectedMonth === monthNow() ? Number(localDate().slice(8)) : monthLength;
  const peak = rows.reduce((top,item) => Math.max(top,item.cents),0);
  const delta = prior && Math.round((spent - prior) / prior * 100);
  const sections = [0,0,0,0];
  const shares = {};
  for (const row of rows) {
    sections[Math.min(3,Math.floor((Number(row.date.slice(8)) - 1) / 7))] += row.cents;
    shares[row.cat] = (shares[row.cat] || 0) + row.cents;
  }
  const cap = Math.max(...sections,1);
  const ranges = ["1–7","8–14","15–21","22–" + monthLength];
  const stats = Object.entries(shares).sort((a,b) => b[1] - a[1]).map(([id,cents]) => {
    const cat = catById[id];
    const percent = Math.round(cents / spent * 100);
    return '<div class="breakdown">' + catTile(id) + '<div><strong>' + cat.label +
      '</strong><div class="progress" aria-hidden="true"><span style="--value:' + percent + '%;--accent:' + cat.accent + '"></span></div></div><strong class="amount">' +
      money(cents) + '<br><small><bdi dir="ltr">' + percent + '%</bdi></small></strong></div>';
  }).join("");
  let comparison = "تظهر المقارنة عند توفر مصروفات في الشهر السابق.";
  if (prior && selectedMonth === monthNow()) {
    const matched = monthRecords(moveMonth(selectedMonth,-1)).filter(item => Number(item.date.slice(8)) <= days);
    const matchedTotal = total(matched);
    if (matchedTotal) {
      const difference = Math.round((spent - matchedTotal) / matchedTotal * 100);
      comparison = changeLabel(difference, "مقارنة بالفترة نفسها من الشهر الماضي");
    } else {
      comparison = "لا مصروفات في الفترة المقابلة من الشهر الماضي.";
    }
  } else if (prior) {
    comparison = changeLabel(delta, "مقارنة بالشهر السابق");
  }
  const bars = sections.map((cents,index) =>
    '<button class="bar-col" type="button" data-act="bar" data-id="' + index + '" aria-pressed="' +
    (chartPeriod === index) + '" aria-label="أيام ' + ranges[index] + '، ' + amountText(cents) + ' ' + esc(currencyLabel()) +
    '"><span class="bar-track"><span class="bar-fill" style="--height:' + (cents ? Math.max(2,cents / cap * 100) : 0) +
    '%"></span></span><span><bdi>' + ranges[index] + '</bdi></span></button>').join("");
  const review = openedReview?.month === selectedMonth ? noticeCard(openedReview) : "";
  return '<div class="page-title"><h1>تحليلات المصروف</h1><p>راقب تغيّر الصرف واعرف أين ذهب.</p></div>' + monthPicker() + review +
    '<section class="panel summary"><small>إجمالي المصروفات · ' + rows.length + ' عملية</small><div class="summary-money">' +
    money(spent) + '</div><div class="panel-sub">' + comparison + '</div></section>' +
    '<div class="statistics"><div class="panel stat"><strong>' + money(Math.round(spent / days)) +
    '</strong><span>متوسط الصرف لكل يوم</span></div><div class="panel stat"><strong>' + money(peak) +
    '</strong><span>أكبر مصروف منفرد</span></div></div>' +
    budgetCard(selectedMonth,spent) + '<div class="split"><section class="panel"><h2>الصرف خلال الشهر</h2>' +
    (rows.length ? '<div class="chart" aria-label="أربعة أجزاء من الشهر">' + bars + '</div><p class="chart-read" aria-live="polite">' +
       (chartPeriod < 0 ? 'اضغط على عمود لعرض إجمالي الفترة.' : 'الفترة ' + ranges[chartPeriod] + ' · ' + money(sections[chartPeriod])) +
       '</p>' : '<p class="panel-sub">يظهر الرسم بعد تسجيل أول مصروف في هذا الشهر.</p>') +
    '</section><section class="panel"><h2>أين تصرف أكثر؟</h2>' +
    (stats ? '<div class="category-chart">' + stats + '</div>' : '<p class="panel-sub">سيظهر توزيع المصروف بحسب الفئات هنا.</p>') + '</section></div>';
}
function changeLabel(delta, basis) {
  const change = delta === 0 ? "صرف مماثل تقريبًا" : (delta > 0 ? "أعلى بـ " : "أقل بـ ") +
    '<bdi dir="ltr">' + Math.abs(delta) + '%</bdi>';
  return '<span class="change' + (delta > 0 ? ' up' : '') + '">' + change + ' · ' + basis + '</span>';
}
function option(action,title,desc,symbol,tone = "") {
  return '<button class="option" type="button" data-act="' + action + '">' + tile(symbol,tone) +
    '<span><strong>' + title + '</strong><small>' + desc + '</small></span>' + icon("arrow") + '</button>';
}
function settings() {
  return '<div class="page-title"><h1>الإعدادات</h1><p>اضبط عملتك واحتفظ بنسخة من مصروفاتك.</p></div>' +
    '<section class="panel"><h2>التفضيلات</h2>' +
    (hasCoach ? option("coach","تنبيهات الإنفاق","تنبيهات مبنية على نمط صرفك وميزانيتك","bell","green") : "") +
    option("currency","عملة السجل",current().currency + ' · ' + esc(currencyLabel()),"wallet") +
    option("demo",demo ? "إنهاء وضع التجربة" : "جرّب ببيانات نموذجية", demo ? "العودة إلى سجلك الشخصي" :
      "استكشف التطبيق ببيانات منفصلة عن سجلك","spark","amber") + '</section>' +
    '<section class="panel"><h2>البيانات والنسخ الاحتياطية</h2>' +
    option("backup","حفظ نسخة احتياطية","ملف JSON يمكنك استعادته في التطبيق","download","green") +
    option("restore","استعادة نسخة","استبدال السجل بملف محفوظ لديك","upload") +
    option("csv","تصدير المصروفات","جميع الشهور في ملف CSV قابل للفتح في Excel","list","pink") +
    '</section><section class="panel"><div class="panel-head"><div class="title-group">' + tile("shield","green") +
    '<div><h2 class="panel-title">بياناتك على جهازك</h2><p class="panel-sub">يعمل التطبيق دون حساب أو اتصال بالإنترنت.</p></div></div></div></section>';
}
const renderers = {home,log,insights,settings};
function render() {
  appFrame();
  $("view").innerHTML = renderers[page]();
  if (page === "log") renderLogRows();
}
function navigate(next) {
  if (page === "home" && (next === "log" || next === "insights")) selectedMonth = monthNow();
  chartPeriod = -1;
  page = next;
  if (next !== "insights") openedReview = null;
  render();
  window.scrollTo(0,0);
  $("view").focus({preventScroll:true});
}
function noticeCard(review) {
  return '<div class="coach-card">' + tile("spark","green") +
    '<div><h3>' + esc(review.title) + '</h3><p>' + esc(review.body) + '</p></div></div>';
}
function readCoach() {
  return JSON.parse(native.coachSettings());
}
function coachSheet() {
  if (demo) {
    showSheet("تنبيهات الإنفاق", '<p class="sheet-copy">عُد إلى سجلك الشخصي لتفعيل التنبيهات. بيانات التجربة لا تدخل في التحليل.</p>');
    return;
  }
  const info = readCoach();
  const raw = native.coachPreview();
  const preview = raw ? JSON.parse(raw) : null;
  const time = /^\d{2}:\d{2}$/.test(info.time) ? info.time : "09:00";
  showSheet("تنبيهات الإنفاق",
    '<div class="coach-card">' + tile("bell","green") +
    '<div><h3>ملاحظة عندما يستحق الصرف انتباهك</h3><p>تراجع المحفظة مصروفاتك مرة يوميًا. إذا ظهر تغيّر واضح، ترسل إشعارًا، بحد أقصى مرة كل يومين.</p></div></div>' +
    '<label class="coach-switch"><span><strong>تنبيهات ذكية</strong><small>الميزانية، الفئات، وتغيّر الصرف</small></span>' +
    '<input id="coachEnabled" role="switch" type="checkbox"' + (info.enabled ? " checked" : "") + '></label>' +
    '<label class="settings-field coach-time">وقت مراجعة المصروفات<input id="coachTime" type="time" dir="ltr" value="' +
    time + '"><small>وقت تقريبي بحسب ساعة جهازك. قد يؤخّر النظام الإشعار قليلًا.</small></label>' +
    '<p class="error" id="coachError" hidden></p>' +
    '<div class="sheet-actions"><button type="button" class="primary" data-act="coach-save">حفظ الإعدادات</button></div>' +
    '<div id="coachPermission" class="coach-permission"></div>' +
    '<div class="coach-foot"><h3>ملاحظة من سجلك</h3>' +
    (preview ? noticeCard(preview) : '<p class="sheet-copy">تظهر هنا ملاحظة عندما يرصد التطبيق تغيّرًا واضحًا في السجل.</p>') + '</div>');
  showCoachPermission(info);
}
function showCoachPermission(info) {
  const status = $("coachPermission");
  if (!status) return;
  if (info.enabled && !info.allowed) {
    status.innerHTML = '<p class="sheet-copy">اسمح بإشعارات المحفظة من أندرويد ليظهر التنبيه على جهازك.</p>' +
      '<button type="button" class="secondary" data-act="coach-allow">السماح بالإشعارات</button>';
  } else if (info.enabled) {
    status.innerHTML = '<button type="button" class="secondary" data-act="coach-test">إرسال إشعار تجريبي</button>';
  } else {
    status.innerHTML = "";
  }
}
function saveCoachSettings() {
  const time = $("coachTime").value;
  if (!/^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(time)) {
    showError("coachError","اختر وقتًا لمراجعة المصروفات.");
    return;
  }
  $("coachError").hidden = true;
  const [hours, minutes] = time.split(":").map(Number);
  native.configureCoach($("coachEnabled").checked, hours * 60 + minutes);
  notify($("coachEnabled").checked ? "حُفظ وقت المراجعة" : "تنبيهات الإنفاق متوقفة");
}
window.walletCoachRefresh = () => {
  if ($("coachPermission")) showCoachPermission(readCoach());
};
window.walletCoachOpened = raw => {
  const review = raw ? JSON.parse(raw) : null;
  if (activeSheet) closeSheet();
  if (demo) demo = null;
  openedReview = review;
  page = "insights";
  selectedMonth = review?.month || monthNow();
  chartPeriod = -1;
  render();
  window.scrollTo(0,0);
  $("view").focus({preventScroll:true});
};
function showSheet(title, content) {
  if (!activeSheet) previousFocus = document.activeElement;
  activeSheet = title;
  $("shade").hidden = false;
  const sheet = $("sheet");
  sheet.setAttribute("aria-label",title);
  sheet.innerHTML = '<div class="handle"></div><div class="sheet-head"><h2>' + title + '</h2>' +
    arrowButton("close","إغلاق","close") + '</div>' + content;
  document.body.classList.add("locked");
  document.querySelector(".shell").inert = true;
  sheet.scrollTop = 0;
  sheet.focus();
}
function closeSheet() {
  $("shade").hidden = true;
  document.body.classList.remove("locked");
  document.querySelector(".shell").inert = false;
  activeSheet = null;
  draft = null;
  sheetAction = null;
  if (previousFocus?.isConnected) previousFocus.focus({preventScroll:true});
  previousFocus = null;
}
function expenseForm(record = null, repeating = false) {
  const isEdit = record && !repeating;
  const rawAmount = record ? String(record.cents / 100) : "";
  draft = {
    id:isEdit ? record.id : null, raw:rawAmount,
    cat:record?.cat || current().recent, date:isEdit ? record.date : localDate(),
    note:record?.note || "", created:isEdit ? record.created : Date.now()
  };
  const expanded = Boolean(record?.note || (isEdit && record.date !== localDate()));
  const choices = categories.map(({id,label}) => '<button type="button" class="cat-choice" data-act="choose" data-id="' + id +
    '" aria-pressed="' + (id === draft.cat) + '">' + catTile(id) + '<span>' + label + '</span></button>').join("");
  const keys = ["1","2","3","4","5","6","7","8","9",".","0","erase"].map(key =>
    '<button type="button" data-act="key" data-id="' + key + '"' +
    (key === "erase" ? ' aria-label="مسح آخر رقم"' : key === "." ? ' aria-label="الفاصلة العشرية"' : "") +
    '>' + (key === "erase" ? icon("erase") : key) + '</button>').join("");
  showSheet(isEdit ? "تعديل مصروف" : "إضافة مصروف",
    '<div class="amount-entry"><bdi dir="ltr" id="entryAmount">' + (rawAmount ? displayDraft(rawAmount) : "0") +
    '</bdi><small>' + esc(currencyLabel()) + '</small></div><p class="form-caption">الفئة</p>' +
    '<div class="cat-grid" aria-label="الفئة">' + choices + '</div><button class="details-button" type="button" data-act="details" aria-expanded="' +
    expanded + '" aria-controls="fields"><span>' + icon("edit") + 'التاريخ والملاحظة</span>' + icon("down") + '</button>' +
    '<div class="fields" id="fields"' + (expanded ? "" : " hidden") + '><label>التاريخ<input id="date" type="date" dir="ltr" min="1900-01-01" max="' +
    (draft.date > localDate() ? draft.date : localDate()) + '" value="' + draft.date + '"></label><label>ملاحظة اختيارية<input id="note" type="text" maxlength="140" placeholder="مثلًا قهوة الصباح" value="' +
    esc(draft.note) + '"></label></div><div class="keypad" aria-label="لوحة الأرقام">' + keys +
    '</div><p class="error" id="entryError" hidden></p><button id="saveEntry" type="button" class="primary" data-act="save-expense"' +
    (validAmount(parseAmount(rawAmount)) ? "" : " disabled") + '>' + (isEdit ? "حفظ التعديل" : "حفظ المصروف") + '</button>' +
    (isEdit ? '<div class="sheet-actions"><button type="button" class="secondary" data-act="repeat">تكرار لليوم</button>' +
       '<button type="button" class="secondary danger" data-act="delete">حذف المصروف</button></div>' : ""));
}
function displayDraft(raw) {
  if (!raw) return "0";
  const [whole, fraction] = raw.split(".");
  return format.format(Number(whole)) + (fraction === undefined ? "" : "." + fraction);
}
function useKey(key) {
  let raw = draft.raw;
  if (key === "erase") raw = raw.slice(0,-1);
  else if (key === "." && !raw.includes(".")) raw = (raw || "0") + ".";
  else if (/^[0-9]$/.test(key)) {
    const [whole, fraction] = raw.split(".");
    if (fraction === undefined && whole.length < 10) raw = (whole === "0" ? "" : whole) + key;
    if (fraction !== undefined && fraction.length < 2) raw += key;
  }
  draft.raw = raw;
  $("entryAmount").textContent = displayDraft(raw);
  $("saveEntry").disabled = !validAmount(parseAmount(raw));
}
function showError(id, message) {
  $(id).textContent = message;
  $(id).hidden = false;
}
function insertExpense() {
  const cents = parseAmount(draft.raw);
  const date = $("date").value;
  const note = $("note").value.trim();
  if (!validAmount(cents)) return;
  if (!validDate(date) || (date > localDate() && date !== draft.date)) {
    $("fields").hidden = false;
    $("sheet").querySelector('[data-act="details"]').setAttribute("aria-expanded","true");
    showError("entryError","اختر تاريخًا صحيحًا حتى اليوم.");
    return;
  }
  if (!draft.id && current().records.length >= maxRecords) {
    showError("entryError","وصل السجل إلى حدّه الأقصى. يمكنك تصدير مصروفاتك من الإعدادات.");
    return;
  }
  const id = draft.id || Date.now().toString(36) + "-" + Math.random().toString(36).slice(2,12);
  const record = {id,date,cents,cat:draft.cat,note,created:draft.created};
  const edited = Boolean(draft.id);
  const records = edited ? current().records.map(item => item.id === id ? record : item) : [...current().records,record];
  if (!save({...current(),records,recent:draft.cat})) return;
  closeSheet();
  if (page === "log" && !edited) {
    selectedMonth = date.slice(0,7);
    filter = "";
    query = "";
  }
  render();
  feedback();
  notify(edited ? "حُفظ التعديل" : "تم تسجيل المصروف ✓");
}
function openBudget() {
  const month = page === "home" ? monthNow() : selectedMonth;
  const budget = current().budgets[month];
  sheetAction = action => {
    const budgets = {...current().budgets};
    if (action === "remove-budget") delete budgets[month];
    else {
      const cents = parseAmount($("budgetValue").value);
      if (!validAmount(cents)) {
        showError("budgetError","أدخل مبلغًا أكبر من صفر وبمنزلتين عشريتين كحد أقصى.");
        return;
      }
      budgets[month] = cents;
    }
    if (save({...current(),budgets})) {
      closeSheet();
      render();
      feedback();
      notify(action === "remove-budget" ? "أزيلت الميزانية" : "حُفظت ميزانية الشهر");
    }
  };
  showSheet("ميزانية الشهر", '<p class="sheet-copy">' + monthlyName(month) + '</p><label class="settings-field">سقف المصروفات · ' +
    esc(currencyLabel()) + '<input id="budgetValue" type="text" inputmode="decimal" dir="ltr" maxlength="16" placeholder="0" value="' +
    (budget ? budget / 100 : "") + '"></label><p id="budgetError" class="error" hidden></p><div class="sheet-actions">' +
    '<button class="primary" type="button" data-act="save-budget">حفظ الميزانية</button>' +
    (budget ? '<button class="secondary danger" type="button" data-act="remove-budget">إزالة الميزانية</button>' : "") + '</div>');
}
function currencyForm() {
  if (current().records.length || Object.keys(current().budgets).length) {
    showSheet("عملة السجل", '<p class="sheet-copy">العملة الحالية ' + current().currency + ' · ' + esc(currencyLabel()) +
      '. لحماية قيمة المبالغ المسجّلة، يمكنك تغيير العملة عندما يخلو السجل من المصروفات والميزانيات.</p>');
    return;
  }
  sheetAction = () => {
    const currency = $("curr").value;
    if (hasOwn(currencies,currency) && save({...current(),currency})) {
      closeSheet();
      render();
      notify("حُفظت العملة");
    }
  };
  const names = {SYP:"ليرة سورية",USD:"دولار أمريكي",EUR:"يورو",SAR:"ريال سعودي",AED:"درهم إماراتي",
    TRY:"ليرة تركية",JOD:"دينار أردني",EGP:"جنيه مصري"};
  showSheet("عملة السجل", '<label class="settings-field">اختر العملة<select id="curr">' +
    Object.keys(currencies).map(code => '<option value="' + code + '"' + (current().currency === code ? " selected" : "") +
      '>' + names[code] + ' (' + code + ')</option>').join("") +
    '</select></label><button type="button" class="primary" data-act="save-currency">حفظ العملة</button>');
}
function selectMonth() {
  sheetAction = () => {
    const value = $("monthValue").value;
    if (!/^(?:19\d{2}|[2-9]\d{3})-(?:0[1-9]|1[0-2])$/.test(value) || value > monthNow()) {
      showError("monthError","اختر شهرًا حتى الشهر الحالي.");
      return;
    }
    selectedMonth = value;
    chartPeriod = -1;
    closeSheet();
    render();
  };
  showSheet("اختيار الشهر", '<label class="settings-field">الشهر<input id="monthValue" type="month" dir="ltr" min="1900-01" max="' +
    monthNow() + '" value="' + selectedMonth + '"></label><p id="monthError" class="error" hidden></p>' +
    '<button class="primary" type="button" data-act="save-month">عرض الشهر</button>');
}
function confirmDelete() {
  const id = draft.id;
  const record = current().records.find(item => item.id === id);
  if (!record) return;
  sheetAction = () => {
    if (!save({...current(),records:current().records.filter(item => item.id !== id)})) return;
    closeSheet();
    render();
    notify("حُذف المصروف");
  };
  showSheet("حذف المصروف؟", '<p class="sheet-copy">' + esc(record.note || catById[record.cat].label) + " · " + money(record.cents) +
    '</p><div class="sheet-actions"><button type="button" class="secondary" data-act="cancel-delete" data-id="' + id +
    '">رجوع</button><button type="button" class="secondary danger" data-act="confirm-delete">حذف المصروف</button></div>');
}

function csvCell(value) {
  const text = String(value);
  const safe = /^\s*[=+@-]|^[\t\r\n]/.test(text) ? "'" + text : text;
  return '"' + safe.replace(/"/g,'""') + '"';
}
function exportFile(backup) {
  if (demo) { notify("أنه وضع التجربة لتصدير بياناتك."); return; }
  if (loadFailed) { notify("تعذّرت قراءة السجل المحفوظ."); return; }
  let body;
  if (backup) {
    body = JSON.stringify(state);
  } else {
    const lines = sortRecords(state.records).map(item => [
      item.date,(item.cents / 100).toFixed(2),state.currency,catById[item.cat].label,item.note
    ].map(csvCell).join(","));
    body = "\ufeff" + ["التاريخ,المبلغ,العملة,الفئة,الملاحظة",...lines].join("\r\n");
  }
  if (native) {
    sendNative(body);
    native.exportFile(backup);
  } else {
    const href = URL.createObjectURL(new Blob([body],{type:backup ? "application/json" : "text/csv;charset=utf-8"}));
    const link = document.createElement("a");
    link.download = backup ? "almahfaza-backup.json" : "almahfaza-expenses.csv";
    link.href = href;
    link.click();
    setTimeout(() => URL.revokeObjectURL(href),1000);
  }
}
function startRestore() {
  if (demo) { notify("أنه وضع التجربة لاستعادة بياناتك."); return; }
  if (native) {
    native.openBackup();
  } else {
    showSheet("استعادة نسخة احتياطية", '<label class="settings-field">اختر ملف JSON<input id="restoreFile" type="file" accept=".json,application/json"></label>');
  }
}
function importBackup(text) {
  try {
    const next = parseState(text.replace(/^\ufeff/,""));
    sheetAction = () => {
      if (!save(next,true)) return;
      if (hasCoach) native.dismissSpendingNotice();
      selectedMonth = monthNow();
      closeSheet();
      render();
      feedback();
      notify("استُعيدت النسخة الاحتياطية");
    };
    showSheet("استعادة النسخة؟", '<p class="sheet-copy">تحتوي النسخة على ' + next.records.length + ' مصروفًا. ستستبدل جميع المصروفات والميزانيات الحالية.</p>' +
      '<div class="sheet-actions"><button type="button" class="secondary" data-act="close">إلغاء</button>' +
      '<button type="button" class="primary" data-act="confirm-restore">استعادة النسخة</button></div>');
  } catch (_) {
    notify("ملف النسخة غير صالح أو غير متوافق.");
  }
}
window.walletImport = () => {
  try { importBackup(readNative(true)); }
  catch (_) { notify("تعذّرت قراءة ملف النسخة."); }
  finally { native.clearImport(); }
};
window.walletFileResult = ok => notify(ok ? "حُفظ الملف" : "تعذّر فتح الملف أو حفظه.");
window.walletBack = () => {
  if (activeSheet) { closeSheet(); return true; }
  if (page !== "home") { navigate("home"); return true; }
  return false;
};

function handle(action,id) {
  switch (action) {
    case "home": case "log": case "insights": case "settings": navigate(action); break;
    case "privacy":
      if (save({...current(),hide:!current().hide})) {
        if (!demo && hasCoach && current().hide) native.dismissSpendingNotice();
        if (page !== "home") navigate("home");
        else render();
      }
      break;
    case "demo":
      demo = demo ? null : createDemo();
      query = "";
      filter = "";
      selectedMonth = monthNow();
      navigate(demo ? "home" : "settings");
      notify(demo ? "أنت في وضع التجربة" : "عدت إلى سجلك الشخصي");
      break;
    case "prev-month": case "next-month": {
      const next = moveMonth(selectedMonth,action === "prev-month" ? -1 : 1);
      if (next >= "1900-01" && next <= monthNow()) {
        selectedMonth = next;
        chartPeriod = -1;
        render();
      }
      break;
    }
    case "select-month": selectMonth(); break;
    case "clear": query = ""; filter = ""; render(); break;
    case "filter": filter = id;
      document.querySelectorAll('[data-act="filter"]').forEach(button => {
        button.setAttribute("aria-pressed",String(button.dataset.id === filter));
      });
      renderLogRows();
      break;
    case "bar":
      chartPeriod = Number(id);
      $("view").innerHTML = insights();
      $("view").querySelector('[aria-pressed="true"]')?.focus({preventScroll:true});
      break;
    case "add": expenseForm(); break;
    case "edit": case "cancel-delete": {
      const record = current().records.find(item => item.id === id);
      if (record) expenseForm(record);
      break;
    }
    case "repeat": {
      const record = current().records.find(item => item.id === draft.id);
      if (record) expenseForm(record,true);
      break;
    }
    case "delete": confirmDelete(); break;
    case "close": closeSheet(); break;
    case "choose":
      draft.cat = id;
      $("sheet").querySelectorAll(".cat-choice").forEach(button =>
        button.setAttribute("aria-pressed",String(button.dataset.id === id)));
      break;
    case "details":
      $("fields").hidden = !$("fields").hidden;
      $("sheet").querySelector('[data-act="details"]').setAttribute("aria-expanded",String(!$("fields").hidden));
      break;
    case "key": useKey(id); break;
    case "save-expense": insertExpense(); break;
    case "budget": openBudget(); break;
    case "currency": currencyForm(); break;
    case "coach": coachSheet(); break;
    case "coach-save": saveCoachSettings(); break;
    case "coach-allow": native.requestCoachPermission(); break;
    case "coach-test":
      notify(native.testCoachNotification() ? "أُرسل إشعار تجريبي" : "اسمح بإشعارات المحفظة أولًا");
      break;
    case "backup": exportFile(true); break;
    case "csv": exportFile(false); break;
    case "restore": startRestore(); break;
    case "save-budget": case "remove-budget": case "save-currency": case "save-month":
    case "confirm-delete": case "confirm-restore": sheetAction?.(action); break;
  }
}

document.addEventListener("click",event => {
  if (event.target === $("shade")) { closeSheet(); return; }
  const button = event.target.closest("button[data-act]");
  if (button) handle(button.dataset.act,button.dataset.id);
});
document.addEventListener("input",event => {
  if (event.target.id === "search") {
    query = event.target.value;
    renderLogRows();
  }
});
document.addEventListener("change",async event => {
  if (event.target.id !== "restoreFile" || !event.target.files.length) return;
  const file = event.target.files[0];
  if (file.size > 5 * 1024 * 1024) { notify("ملف النسخة أكبر من الحدّ المسموح."); return; }
  try { importBackup(await file.text()); }
  catch (_) { notify("تعذّرت قراءة الملف."); }
});
document.addEventListener("keydown",event => {
  if (!activeSheet) return;
  if (event.key === "Escape") { event.preventDefault(); closeSheet(); return; }
  if (event.key === "Tab") {
    const focusable = [...$("sheet").querySelectorAll("button:not(:disabled), input, select")].filter(el => el.getClientRects().length);
    if (focusable.length && (event.shiftKey ? [focusable[0],$("sheet")].includes(document.activeElement) :
      document.activeElement === focusable[focusable.length - 1])) {
      event.preventDefault();
      (event.shiftKey ? focusable[focusable.length - 1] : focusable[0]).focus();
    }
    return;
  }
  if (!draft || !$("entryAmount") || /INPUT|SELECT|TEXTAREA/.test(event.target.tagName)) return;
  const key = cleanInput(event.key);
  if (/^[\d.]$/.test(key) || key === "Backspace") {
    event.preventDefault();
    useKey(key === "Backspace" ? "erase" : key);
  } else if (key === "Enter" && event.target.tagName !== "BUTTON" && !$("saveEntry").disabled) {
    event.preventDefault();
    insertExpense();
  }
});
render();
if (loadFailed) notify("تعذّرت قراءة سجل المصروفات الحالي.");
