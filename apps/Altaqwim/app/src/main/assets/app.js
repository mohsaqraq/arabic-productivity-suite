"use strict";

const drawings={
  calendar:'<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 10h18m-12 5h1m4 0h1"/>',
  clock:'<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/>',
  task:'<rect x="4" y="4" width="16" height="16" rx="4"/><path d="m8 12 3 3 5-6"/>',
  square:'<rect x="4" y="4" width="16" height="16" rx="4"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  left:'<path d="m14 6-6 6 6 6"/>', right:'<path d="m10 6 6 6-6 6"/>',
  search:'<circle cx="10.5" cy="10.5" r="5.5"/><path d="m15 15 4.5 4.5"/>',
  more:'<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',
  sliders:'<path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="3" fill="white"/><circle cx="15" cy="17" r="3" fill="white"/>',
  close:'<path d="m5 5 14 14M19 5 5 19"/>',
  bell:'<path d="M18 8a6 6 0 0 0-12 0v4.5c0 1.5-.7 3-2 4.5h16c-1.3-1.5-2-3-2-4.5V8Zm-8 12a2 2 0 0 0 4 0"/>',
  shield:'<path d="M12 3 4 6v6c0 4 3 7 8 9 5-2 8-5 8-9V6l-8-3Z"/><path d="m8 12 3 3 5-6"/>',
  down:'<path d="M12 3v12m-4-4 4 4 4-4M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/>',
  up:'<path d="M12 15V3m-4 4 4-4 4 4M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/>',
  file:'<path d="M6 3h8l4 4v14H6zM14 3v5h4m-8 5h4m-4 4h4"/>'
};
const api=window.CalendarNative;
const $=id=>document.getElementById(id);
const icon=name=>'<svg viewBox="0 0 24 24" class="i" aria-hidden="true">'+drawings[name]+'</svg>';
const escapeHTML=text=>String(text).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[c]);
const tile=(mark,tone="")=>'<span class="tile '+tone+'">'+icon(mark)+'</span>';
const names=["كانون الثاني","شباط","آذار","نيسان","أيار","حزيران","تموز","آب","أيلول","تشرين الأول","تشرين الثاني","كانون الأول"];
const days=["الأحد","الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت"];
const shortDays=["أحد","اثنين","ثلاثاء","أربعاء","خميس","جمعة","سبت"];
const validId=id=>/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(id);
const dateFloor="1900-01-01",dateCeiling="9998-12-31";
const PAGE_SIZE=40;
const iso=(date=new Date())=>[date.getFullYear(),String(date.getMonth()+1).padStart(2,"0"),String(date.getDate()).padStart(2,"0")].join("-");
function dateOf(day){const [y,m,d]=day.split("-").map(Number);return new Date(y,m-1,d,12);}
function shift(day,delta){const date=dateOf(day);date.setDate(date.getDate()+delta);return iso(date);}
function monthShift(day,delta){
  const date=dateOf(day),n=date.getDate();
  date.setDate(1);date.setMonth(date.getMonth()+delta);
  const last=new Date(date.getFullYear(),date.getMonth()+1,0).getDate();
  date.setDate(Math.min(n,last));return iso(date);
}
const clampDate=day=>day<dateFloor?dateFloor:day>dateCeiling?dateCeiling:day;
function longDay(day,year=false){
  const date=dateOf(day);return date.getDate()+" "+names[date.getMonth()]+(year?" "+date.getFullYear():"");
}
const withDay=day=>days[dateOf(day).getDay()]+"، "+longDay(day);
const validDay=day=>/^\d{4}-\d{2}-\d{2}$/.test(day)&&day>=dateFloor&&day<=dateCeiling&&iso(dateOf(day))===day;
const validClock=value=>/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value);

let page="calendar", mode="month", selected=iso(), weekStart=api?.weekStart()??6;
let sheet="", oldFocus=null, working=false, pendingOpen="", draft=null, addDay=selected,addTime="";
let threeStart=selected,dialogDay="",dayItems=[];
let searchText="", searchItems=[], searchMore=false, searchTimer=0, toastTimer=0;

function weekFirst(day){return shift(day,-((dateOf(day).getDay()-weekStart+7)%7));}
function windowDays(){
  if(mode==="three") return Array.from({length:3},(_,i)=>shift(threeStart,i));
  if(mode==="week"){const first=weekFirst(selected);return Array.from({length:7},(_,i)=>shift(first,i));}
  const at=dateOf(selected);
  const first=iso(new Date(at.getFullYear(),at.getMonth(),1,12));
  const start=weekFirst(first);
  const length=new Date(at.getFullYear(),at.getMonth()+1,0).getDate();
  const cells=Math.ceil((((dateOf(first).getDay()-weekStart+7)%7)+length)/7)*7;
  return Array.from({length:cells},(_,i)=>shift(start,i));
}
const button=(action,label,type="primary")=>'<button class="'+type+'" type="button" data-act="'+action+'">'+label+'</button>';
function iconButton(action,label,name,id=""){
  return '<button class="icon-button" type="button" aria-label="'+escapeHTML(label)+'" title="'+escapeHTML(label)+
    '" data-act="'+action+'"'+(id?' data-id="'+id+'"':"")+'>'+icon(name)+'</button>';
}
function option(action,title,sub,mark,tone=""){
  return '<button type="button" class="option" data-act="'+action+'">'+tile(mark,tone)+'<span><strong>'+title+
    '</strong><small>'+sub+'</small></span>'+icon("left")+'</button>';
}
function notify(text){
  $("toast").textContent=text;$("toast").classList.add("show");clearTimeout(toastTimer);
  toastTimer=setTimeout(()=>$("toast").classList.remove("show"),2300);
}
const priorityNames=["بلا أهمية","قليل الأهمية","متوسط الأهمية","عالي الأهمية"];
const priorityShort=["بلا أهمية","قليل","متوسط","عالي"];
const modes=[["month","الشهر"],["week","الأسبوع"],["three","٣ أيام"]];
const modeNames={month:"الشهر",week:"الأسبوع",three:"الأيام الثلاثة"};
const HOUR_HEIGHT=60;
const MIN_BLOCK=28;

function priorityOf(item){return Number.isInteger(item.priority)&&item.priority>=0&&item.priority<=3?item.priority:0;}
function viewHeader(){
  const top=$("top");
  if(page!=="calendar"){
    top.innerHTML='<div class="subnav">'+iconButton("calendar","العودة إلى التقويم","right")+
      '<h1>'+(page==="search"?"البحث في التقويم":"الإعدادات")+'</h1><span aria-hidden="true"></span></div>';
    return;
  }
  const window=windowDays();
  const first=window[0],last=window[window.length-1];
  const sameMonth=first.slice(0,7)===last.slice(0,7);
  const at=dateOf(selected);
  const range=mode==="month" ? names[at.getMonth()] :
    sameMonth ? '<bdi dir="ltr">'+dateOf(first).getDate()+"–"+dateOf(last).getDate()+'</bdi> '+names[dateOf(first).getMonth()] :
      longDay(first)+" – "+longDay(last);
  const rangeYear=mode!=="month"&&first.slice(0,4)!==last.slice(0,4)?
    '<bdi dir="ltr">'+first.slice(0,4)+"–"+last.slice(0,4)+'</bdi>':at.getFullYear();
  top.innerHTML='<div class="toolbar"><button class="today-button" type="button" data-act="today" aria-label="اذهب إلى اليوم" title="اذهب إلى اليوم">'+
    '<span>'+dateOf(iso()).getDate()+'</span></button><div class="period">'+
    iconButton("prev",modeNames[mode]+" السابق","right")+
    '<button class="period-label" type="button" data-act="jump" aria-label="انتقال إلى تاريخ"><strong>'+range+
    '</strong><small>'+rangeYear+'</small></button>'+iconButton("next",modeNames[mode]+" التالي","left")+
    '</div><div class="toolbar-actions">'+iconButton("add","إضافة موعد أو مهمة","plus")+
    iconButton("menu","البحث والإعدادات","more")+'</div></div>'+
    '<div class="tabs" role="group" aria-label="طريقة عرض التقويم">'+modes.map(([key,label])=>
      '<button type="button" data-act="'+key+'" aria-pressed="'+(mode===key)+'">'+label+'</button>').join("")+'</div>';
}
function openPage(next,focus=false){
  clearTimeout(searchTimer);page=next;
  $("shell").classList.toggle("calendar-shell",page==="calendar");
  if(page==="calendar") showCalendar();
  else {
    viewHeader();
    if(page==="settings") $("view").innerHTML=settingsPage();
    else {
      $("view").innerHTML='<label class="search-entry">'+icon("search")+
        '<input id="search" type="search" maxlength="140" placeholder="عنوان أو كلمة من الملاحظات" aria-label="بحث في التقويم" value="'+
        escapeHTML(searchText)+'"></label><div id="results"></div>';
      showSearch();
      if(focus)$("search").focus();
    }
  }
  window.scrollTo(0,0);
}
function boardData(window,cap){
  const first=clampDate(window[0]),last=clampDate(window[window.length-1]);
  const totals=new Map(JSON.parse(api.grid(first,last)).map(([day,...counts])=>[day,counts.reduce((a,b)=>a+b,0)]));
  const groups=new Map();
  for(const item of JSON.parse(api.board(first,last,cap))){
    if(!groups.has(item.day))groups.set(item.day,[]);
    groups.get(item.day).push(item);
  }
  return {items:day=>groups.get(day)||[],total:day=>totals.get(day)||0};
}
function legend(){
  return '<div class="legend" aria-label="ألوان الأولوية">'+[3,2,1,0].map(key=>
    '<span class="prio-'+key+'"><i aria-hidden="true"></i>'+priorityShort[key]+'</span>').join("")+'</div>';
}
function dayLabel(day,total){return withDay(day)+(total?"، "+total+" عناصر":"");}
function dayHeader(day,total,full){
  const active=day===selected;
  const dayName=shortDays[dateOf(day).getDay()];
  return '<button class="'+(full?"day-heading":"cell-date")+'" type="button" data-act="day" data-id="'+day+
    '" aria-label="'+dayLabel(day,total)+'" aria-pressed="'+active+'"'+
    (day===iso()?' aria-current="date"':"")+(validDay(day)?"":" disabled")+'>'+
    (full?'<span class="day-name">'+dayName+'</span>':"")+
    '<span class="day-number">'+dateOf(day).getDate()+'</span></button>';
}
function chip(item,kind="cell-chip"){
  return '<button class="'+kind+' prio-'+priorityOf(item)+(item.done?' done':'')+'" type="button" data-act="open" data-id="'+item.id+
    '" aria-label="'+escapeHTML((item.kind==="task"?"مهمة":"موعد")+" "+item.title+"، "+priorityNames[priorityOf(item)])+'">'+
    icon(item.kind==="task"?(item.done?"task":"square"):"clock")+'<span>'+escapeHTML(item.title)+'</span></button>';
}
function moreFor(day,count){
  return count>0?'<button class="board-more" type="button" data-act="day" data-id="'+day+
    '" aria-label="عرض '+count+' عناصر إضافية في '+longDay(day)+'"><bdi dir="ltr">+'+count+'</bdi></button>':"";
}
function monthBoard(window,data,limit){
  const headers=Array.from({length:7},(_,i)=>'<span class="weekday">'+shortDays[(weekStart+i)%7]+'</span>').join("");
  return '<section class="calendar-board month-board" aria-label="شبكة الشهر"><div class="month-labels">'+headers+
    '</div><div class="month-days" data-weeks="'+window.length/7+'">'+window.map(day=>{
      const items=data.items(day), total=data.total(day);
      return '<div class="month-cell'+(day.slice(0,7)!==selected.slice(0,7)?" outside":"")+
        (day===selected?" active":"")+'">'+dayHeader(day,total,false)+
        '<div class="cell-items">'+items.slice(0,limit).map(item=>chip(item)).join("")+moreFor(day,total-limit)+'</div>'+
        '<button class="day-space" type="button" data-act="day" data-id="'+day+'" tabindex="-1" aria-label="عرض يوم '+
        longDay(day)+'"'+(validDay(day)?"":" disabled")+'></button></div>';
    }).join("")+'</div></section>';
}
function weekBoard(window,data,limit){
  return '<section class="calendar-board week-board" aria-label="أعمدة الأسبوع">'+window.map(day=>{
    const items=data.items(day),total=data.total(day);
    return '<div class="week-column'+(day===selected?" active":"")+'">'+dayHeader(day,total,true)+
      '<div class="week-items">'+items.map(item=>chip(item)).join("")+moreFor(day,total-limit)+'</div>'+
      '<button class="day-space" type="button" data-act="day" data-id="'+day+'" tabindex="-1" aria-label="عرض يوم '+
      longDay(day)+'"'+(validDay(day)?"":" disabled")+'></button></div>';
  }).join("")+'</section>';
}
function minute(time){return Number(time.slice(0,2))*60+Number(time.slice(3));}
function timeText(minutes){return String(Math.floor(minutes/60)).padStart(2,"0")+":"+String(minutes%60).padStart(2,"0");}
function renderedIntervals(items){
  return items.filter(item=>item.start).map(item=>{
    const start=minute(item.start);
    const end=item.kind==="event"?minute(item.end):start+MIN_BLOCK;
    const top=Math.min(start,1440-MIN_BLOCK);
    return {item,top,bottom:Math.min(1440,Math.max(end,top+MIN_BLOCK))};
  }).sort((a,b)=>a.top-b.top||b.bottom-a.bottom);
}
function overlapGroups(items){
  const groups=[];
  let group=null;
  for(const block of renderedIntervals(items)){
    if(!group||block.top>=group.bottom){
      group={blocks:[],ends:[],top:block.top,bottom:block.bottom};
      groups.push(group);
    }
    let lane=group.ends.findIndex(end=>end<=block.top);
    if(lane<0)lane=group.ends.length;
    group.ends[lane]=block.bottom;
    group.bottom=Math.max(group.bottom,block.bottom);
    group.blocks.push({...block,lane});
  }
  return groups;
}
function timeBlock(block,lanes){
  const item=block.item;
  return '<button type="button" class="time-block prio-'+priorityOf(item)+(item.done?" done":"")+
    '" data-act="open" data-id="'+item.id+'" data-top="'+block.top+'" data-bottom="'+block.bottom+
    '" data-lane="'+block.lane+'" data-lanes="'+lanes+'" aria-label="'+escapeHTML(item.start+"، "+
      (item.kind==="task"?"مهمة ":"موعد ")+item.title+"، "+priorityNames[priorityOf(item)])+'">'+
    '<span class="time-block-line">'+icon(item.kind==="task"?(item.done?"task":"square"):"clock")+
    '<strong>'+escapeHTML(item.title)+'</strong></span>'+
    (block.bottom-block.top>=42?'<small dir="ltr">'+item.start+(item.end?"–"+item.end:"")+'</small>':"")+'</button>';
}
function timedBoard(window,data){
  const headers=window.map(day=>dayHeader(day,data.total(day),true)).join("");
  const allDay=window.map(day=>data.items(day).filter(item=>!item.start));
  const others=window.map(day=>Math.max(0,data.total(day)-data.items(day).length));
  const allRow=allDay.some(items=>items.length)||others.some(count=>count>0) ?
    '<div class="time-columns all-day-strip"><span class="axis-note">بلا ساعة</span>'+allDay.map((items,i)=>
      '<div class="all-day-cell">'+(items.length?chip(items[0]):"")+moreFor(window[i],Math.max(0,items.length-1)+others[i])+
      '</div>').join("")+'</div>':"";
  const hours=Array.from({length:24},(_,i)=>'<span dir="ltr">'+timeText(i*60)+'</span>').join("");
  return '<section class="calendar-board timed-board" aria-label="جدول ثلاث أيام بالساعات">'+
    '<div class="time-columns time-headers"><div></div>'+headers+'</div>'+allRow+
    '<div id="hours" class="hours-scroll"><div class="time-columns time-grid"><div class="hour-axis">'+hours+'</div>'+
    window.map(day=>{
      const slots=Array.from({length:24},(_,i)=>'<button type="button" class="hour-slot" data-act="slot" data-id="'+day+
        'T'+timeText(i*60)+'" aria-label="إضافة في '+longDay(day)+' الساعة '+timeText(i*60)+'"'+
        (validDay(day)?'':' disabled')+'></button>').join("");
      const blocks=overlapGroups(data.items(day)).map(group=>group.ends.length>2 ?
        '<button type="button" class="time-block collision" data-act="day" data-id="'+day+'" data-top="'+group.top+
        '" data-bottom="'+group.bottom+'" data-lane="0" data-lanes="1" aria-label="عرض '+group.blocks.length+
        ' عناصر متداخلة">'+group.blocks.length+' عناصر</button>':
        group.blocks.map(block=>timeBlock(block,group.ends.length)).join("")).join("");
      return '<div class="time-day'+(day===selected?" active":"")+'">'+slots+blocks+
        (day===iso()?'<div id="now" class="now" aria-hidden="true"></div>':"")+'</div>';
    }).join("")+'</div></div></section>';
}
function placeBlocks(){
  for(const block of $("view").querySelectorAll(".time-block")){
    const {top,bottom,lane,lanes}=block.dataset;
    block.style.top=top+"px";
    block.style.height=(Number(bottom)-Number(top)-2)+"px";
    block.style.insetInlineStart="calc("+(Number(lane)*100/Number(lanes))+"% + 2px)";
    block.style.width="calc("+(100/Number(lanes))+"% - 4px)";
  }
  refreshNow();
}
function refreshNow(){
  const line=$("now");
  if(!line)return;
  const now=new Date();
  line.style.top=(now.getHours()*HOUR_HEIGHT+now.getMinutes())+"px";
}
function initialHour(window,data,goNow){
  if(goNow){
    const now=new Date();
    return Math.max(0,(now.getHours()-2)*HOUR_HEIGHT);
  }
  const starts=window.flatMap(day=>data.items(day).filter(item=>item.start).map(item=>minute(item.start)));
  return starts.length?Math.max(0,Math.min(480,...starts)-HOUR_HEIGHT):480;
}
function showCalendar(reset=false,goNow=false){
  const lastScroll=reset?null:$("hours")?.scrollTop;
  viewHeader();
  const window=windowDays();
  const height=$("view").clientHeight||globalThis.innerHeight||600;
  const rows=window.length/7;
  const limit=mode==="month"?Math.max(1,Math.min(5,Math.floor(((height-55)/rows-38)/20))):
    mode==="week"?Math.max(2,Math.min(18,Math.floor((height-93)/30))):80;
  try{
    const data=boardData(window,limit);
    $("view").innerHTML=(mode==="month"?monthBoard(window,data,limit):
      mode==="week"?weekBoard(window,data,limit):timedBoard(window,data))+legend();
    if(mode==="three"){
      placeBlocks();
      $("hours").scrollTop=lastScroll??initialHour(window,data,goNow);
    }
  }catch(_){$("view").innerHTML=empty("تعذّر عرض التقويم","جرّب فتح الفترة مجددًا.");}
}
function movePeriod(direction){
  if(mode==="three"){
    threeStart=clampDate(shift(threeStart,3*direction));selected=threeStart;
  }else selected=clampDate(mode==="month"?monthShift(selected,direction):shift(selected,7*direction));
  showCalendar(true);
}
function showDay(day,append=false){
  if(!validDay(day))return;
  if(!append && selected!==day){selected=day;if(page==="calendar")showCalendar();}
  if(!append){dialogDay=day;dayItems=[];}
  else if(dialogDay!==day)return;
  try{
    const values=JSON.parse(api.list(day,day,dayItems.length));
    dayItems.push(...values.slice(0,PAGE_SIZE));
    const previous=append?$("sheet").scrollTop:0;
    showSheet("day",withDay(day),button("add-date","إضافة موعد أو مهمة","primary")+
      (dayItems.length?'<div class="day-list">'+dayItems.map(item=>row(item)).join("")+'</div>':
        empty("لا توجد عناصر في هذا اليوم"))+
      (values.length>PAGE_SIZE?button("day-more","عرض المزيد","secondary"):""));
    $("sheet").querySelector('[data-act="add-date"]').dataset.id=day;
    $("sheet").scrollTop=previous;
  }catch(_){notify("تعذّر قراءة عناصر اليوم.");}
}

function itemTime(item){
  if(!item.start)return item.kind==="event"?"طوال اليوم":"دون وقت";
  return '<bdi dir="ltr">'+item.start+(item.end?" – "+item.end:"")+'</bdi>';
}
function row(item,showDay=false){
  const task=item.kind==="task";
  const level=priorityOf(item);
  const info=(showDay?longDay(item.day,true)+" · ":"")+(task?"مهمة":"موعد")+" · "+itemTime(item)+
    " · "+priorityNames[level]+(item.done?" · منجزة":"");
  return '<div class="item'+(item.done?" done":"")+'"><button type="button" class="item-open" data-act="open" data-id="'+item.id+'">'+
    tile(task?"task":"clock","prio-"+level)+'<span><strong>'+escapeHTML(item.title)+'</strong><small>'+
    info+'</small></span>'+icon("left")+'</button>'+(task?'<button type="button" class="task-check" data-act="toggle" data-id="'+item.id+
    '" aria-pressed="'+item.done+'" aria-label="'+escapeHTML((item.done?"إعادة فتح مهمة ":"إنجاز مهمة ")+item.title)+'">'+
    icon(item.done?"task":"square")+'</button>':"")+'</div>';
}
function empty(title,copy="",symbol="calendar"){
  return '<div class="panel empty">'+tile(symbol)+'<div><strong>'+title+'</strong>'+(copy?'<p>'+copy+'</p>':"")+'</div></div>';
}
function showSearch(append=false){
  if(!append)searchItems=[];
  if(!searchText.trim()){
    $("results").innerHTML=empty("ابحث عن موعد أو مهمة","اكتب كلمة من العنوان أو الملاحظات.","search");
    return;
  }
  try{
    const values=JSON.parse(api.search(searchText.trim(),searchItems.length));
    searchMore=values.length>PAGE_SIZE;searchItems.push(...values.slice(0,PAGE_SIZE));
    $("results").innerHTML=searchItems.length?'<div class="panel divided">'+searchItems.map(item=>row(item,true)).join("")+'</div>'+
      (searchMore?button("search-more","عرض المزيد","secondary"):""):empty("ما في نتائج مطابقة","جرّب كلمة ثانية.","search");
  }catch(_){$("results").innerHTML=empty("تعذّر البحث");}
}
function settingsPage(){
  return ''+
    '<section class="panel"><h2>التفضيلات</h2>'+option("week-start","بداية الأسبوع",days[weekStart],"calendar")+
    option("notifications","الإشعارات","السماح بالإشعارات ودقة التذكير","bell")+'</section>'+
    '<section class="panel"><h2>البيانات والنسخ الاحتياطية</h2>'+
    option("backup","حفظ نسخة احتياطية","ملف JSON يشمل المواعيد والمهام والتذكيرات","down")+
    option("restore","استعادة نسخة","إعادة جدولك من نسخة محفوظة","up","task")+
    option("ical","تصدير التقويم","ملف ICS للمشاركة مع تطبيقات التقويم","file")+'</section>'+
    '<section class="panel empty">'+tile("shield","good")+'<div><h3>تقويمك على جهازك</h3><p>يعمل دون حساب أو إنترنت.</p></div></section>';
}
function showSheet(name,title,content){
  if(!sheet)oldFocus=document.activeElement;
  sheet=name;$("sheet").setAttribute("aria-label",title);
  $("sheet").innerHTML='<div class="handle"></div><div class="sheet-head"><h2>'+title+'</h2>'+
    (name==="busy"?"":iconButton("close","إغلاق","close"))+'</div>'+content;
  $("shade").hidden=false;$("shell").inert=true;document.body.classList.add("locked");
  $("sheet").scrollTop=0;$("sheet").focus();
}
function closeSheet(usePending=true){
  if(!sheet)return;
  sheet="";$("shade").hidden=true;$("shell").inert=false;document.body.classList.remove("locked");
  if(oldFocus?.isConnected)oldFocus.focus({preventScroll:true});
  oldFocus=null;
  if(usePending&&pendingOpen){const id=pendingOpen;pendingOpen="";openNotice(id);}
}
function addFor(day,time=""){
  addDay=day;addTime=time;
  showSheet("choose","إضافة في "+longDay(day),option("new-event","موعد","وقت بداية ونهاية أو طوال اليوم","clock")+
    option("new-task","مهمة","حدّدها وعلّم عليها عند الإنجاز","task","task"));
}
function fresh(kind){
  const start=addTime||(kind==="event"?"09:00":"");
  return {id:"",kind,title:"",day:addDay,start,end:kind==="event"?timeText(Math.min(1439,minute(start)+60)):"",
    note:"",done:false,remind:-1,priority:0};
}
function loadItem(id){
  if(!validId(id))return null;
  try{const saved=api.item(id);return saved?JSON.parse(saved):null;}catch(_){return null;}
}
function reminderLabel(value,timed){
  if(value===-1)return "دون تذكير";
  if(value===0)return timed?"في الوقت المحدّد":"يومها الساعة 09:00";
  return ({10:"قبل 10 دقائق",30:"قبل 30 دقيقة",60:"قبل ساعة",1440:"قبل يوم"})[value];
}
function form(value){
  draft={...value};
  const event=value.kind==="event",timed=Boolean(value.start);
  const title=(value.id?'تعديل ':'إضافة ')+(event?"موعد":"مهمة");
  showSheet("form",title,
    '<label class="field">'+(event?"عنوان الموعد":"عنوان المهمة")+'<input id="title" maxlength="120" placeholder="'+
    (event?"مثال، محاضرة الجراحة":"مثال، مراجعة المحاضرة")+'" value="'+escapeHTML(value.title)+'"></label>'+
    '<label class="field">التاريخ<input id="date" type="date" dir="ltr" min="'+dateFloor+'" max="'+dateCeiling+
    '" value="'+value.day+'"></label>'+
    '<label class="toggle"><span>'+(event?"طوال اليوم":"تحديد وقت للمهمة")+'</span><input id="time-toggle" type="checkbox"'+
    (event?!timed?" checked":"":timed?" checked":"")+'></label>'+
    '<div class="'+(event?"split-fields":"")+'" id="time-fields"'+(timed?"":" hidden")+'>'+
    '<label class="field">'+(event?"البداية":"وقت المهمة")+'<input id="start" type="time" value="'+(value.start||"09:00")+'" dir="ltr"></label>'+
    (event?'<label class="field">النهاية<input id="end" type="time" value="'+(value.end||"10:00")+'" dir="ltr"></label>':"")+'</div>'+
    '<p class="warning" id="conflict" hidden></p>'+
    '<fieldset class="priority-field"><legend>الأولوية</legend><div class="priority-choices">'+[3,2,1,0].map(level=>
      '<label class="prio-'+level+'"><input type="radio" name="priority" value="'+level+'"'+
      (priorityOf(value)===level?' checked':'')+'><span><i aria-hidden="true"></i>'+priorityShort[level]+'</span></label>').join("")+
      '</div></fieldset>'+
    (event?'':'<label class="toggle"><span>تم إنجاز المهمة</span><input id="done" type="checkbox"'+(value.done?' checked':'')+'></label>')+
    '<label class="field">التذكير<select id="remind"></select><small id="reminder-hint" hidden></small></label>'+
    '<label class="field"><span>ملاحظات <small>اختياري</small></span><textarea id="note" rows="2" maxlength="3000">'+escapeHTML(value.note)+
    '</textarea></label><p class="error" id="form-error" role="alert" hidden></p>'+
    button("save",event?"حفظ الموعد":"حفظ المهمة")+(value.id?
      '<div class="sheet-actions">'+button("duplicate","نسخ","secondary smaller")+
      button("delete","حذف","secondary danger smaller")+'</div>':""));
  $("note").value=value.note;
  timeFields(value.remind);
}
function isTimed(){return draft.kind==="event"?!$("time-toggle").checked:$("time-toggle").checked;}
function timeFields(initial=Number($("remind").value)){
  const timed=isTimed();
  $("time-fields").hidden=!timed;
  $("remind").innerHTML=[-1,0,10,30,60,1440].map(value=>'<option value="'+value+'"'+
    (value===initial?" selected":"")+'>'+reminderLabel(value,timed)+'</option>').join("");
  updateHint();
  checkConflict();
}
function noticeState(){try{return JSON.parse(api.notices());}catch(_){return {};}}
function updateHint(){
  const node=$("reminder-hint");
  if(!node)return;
  const active=Number($("remind").value)>=0;
  const allowed=noticeState().allowed;
  node.textContent=!allowed?"تحتاج التذكيرات إلى إذن الإشعارات.":!isTimed()?"يُحسب التذكير ابتداءً من الساعة 09:00.":"";
  node.hidden=!active||!node.textContent;
}
function formValues(){
  return {...draft,title:$("title").value.trim(),day:$("date").value,start:isTimed()?$("start").value:"",
    end:isTimed()&&draft.kind==="event"?$("end").value:"",note:$("note").value.trim(),remind:Number($("remind").value),
    priority:Number($("sheet").querySelector('input[name="priority"]:checked').value),done:Boolean($("done")?.checked)};
}
function checkConflict(){
  const node=$("conflict");
  if(!node)return;
  const item=formValues();
  let conflict="";
  if(item.kind==="event"&&item.start&&item.end>item.start&&validDay(item.day)){
    try{conflict=api.conflict(item.id,item.day,item.start,item.end);}catch(_){}
  }
  node.hidden=!conflict;node.textContent=conflict?"يتداخل الوقت مع «"+conflict+"».":"";
}
function saveForm(){
  const item=formValues();
  const error=$("form-error");
  const message=!item.title?"اكتب عنوانًا.":!validDay(item.day)?"اختر تاريخًا صحيحًا.":
    isTimed()&&!validClock(item.start)?"حدّد الوقت.":item.kind==="event"&&isTimed()&&
      (!validClock(item.end)||item.end<=item.start)?"خلّي وقت النهاية بعد وقت البداية.":"";
  if(message){error.textContent=message;error.hidden=false;return;}
  if(!item.id)item.id=api.newId();
  if(!api.save(JSON.stringify(item))){error.textContent="تعذّر حفظ العنصر.";error.hidden=false;return;}
  selected=item.day;
  if(mode==="three"&&!windowDays().includes(selected))threeStart=selected;
  closeSheet(false);
  if(page==="search")showSearch();else openPage("calendar");
  notify(item.kind==="event"?"حُفظ الموعد":"حُفظت المهمة");
  checkPending();
}
function editItem(id){const item=loadItem(id);if(item)form(item);else notify("تعذّر فتح هذا العنصر.");}
function toggleTask(id){
  const item=loadItem(id);
  if(!item||item.kind!=="task"||!api.status(id,!item.done)){notify("تعذّر تغيير حالة المهمة.");return;}
  if(page==="search")showSearch();else showCalendar();
  if(sheet==="day")showDay(dialogDay);
  notify(item.done?"أُعيد فتح المهمة":"أُنجزت المهمة");
}
function dateSheet(){
  showSheet("jump","الانتقال إلى تاريخ",'<label class="field">التاريخ<input id="jump-date" type="date" dir="ltr" min="'+
    dateFloor+'" max="'+dateCeiling+'" value="'+selected+'"></label><p class="error" id="jump-error" hidden></p>'+
    button("jump-save","عرض التاريخ"));
}
function notificationsSheet(){
  const state=noticeState();
  showSheet("notices","الإشعارات",
    '<p class="sheet-copy">'+(state.allowed?"الإشعارات مسموحة. اختر موعد التذكير أثناء إضافة الموعد أو المهمة.":
    "اسمح للتقويم بإرسال التذكيرات التي تضبطها.")+'</p>'+
    (state.allowed?button("notice-test","إرسال تذكير تجريبي","secondary"):button("notice-allow","السماح بالإشعارات"))+
    (state.exactSetting?'<p class="sheet-copy">'+(state.exact?"الوصول إلى المواعيد الدقيقة مفعّل.":
    "قد تتأخر التذكيرات قليلًا. يمكنك السماح بالمواعيد الدقيقة من أندرويد.")+'</p>'+
    (state.exact?"":button("notice-exact","السماح بالمواعيد الدقيقة","secondary")):""));
}
function weekStartSheet(){
  showSheet("week-start","بداية الأسبوع",'<label class="field">اليوم الأول<select id="week-start">'+
    [6,0,1].map(day=>'<option value="'+day+'"'+(day===weekStart?" selected":"")+'>'+days[day]+'</option>').join("")+
    '</select></label>'+button("week-start-save","حفظ"));
}
function confirmation(name,title,copy,action,ok){
  showSheet(name,title,'<p class="sheet-copy">'+copy+'</p><div class="sheet-actions">'+
    button(name==="delete"?"delete-cancel":"close","إلغاء","secondary")+button(action,ok,"secondary danger")+'</div>');
}
function openNotice(id){
  if(working||sheet){pendingOpen=id;return;}
  const item=loadItem(id);
  if(!item){notify("هذا العنصر غير موجود في التقويم.");return;}
  selected=item.day;if(mode==="three")threeStart=selected;
  openPage("calendar");form(item);
}
function checkPending(){if(pendingOpen){const id=pendingOpen;pendingOpen="";openNotice(id);}}

function action(act,id){
  if(working)return;
  switch(act){
    case "calendar": case "settings": openPage(act);break;
    case "search": openPage("search",true);break;
    case "today":
      selected=iso();threeStart=selected;showCalendar(true,true);break;
    case "month": case "week": case "three":
      if(mode!==act){mode=act;if(mode==="three")threeStart=selected;showCalendar(true);}
      break;
    case "prev": case "next": movePeriod(act==="prev"?-1:1);break;
    case "day": showDay(id);break;
    case "day-more": showDay(dialogDay,true);break;
    case "slot": {const [day,time]=id.split("T");addFor(day,time);break;}
    case "jump": dateSheet();break;
    case "jump-save": {
      const value=$("jump-date").value;
      if(!validDay(value)){$("jump-error").textContent="اختر تاريخًا صحيحًا.";$("jump-error").hidden=false;break;}
      selected=value;threeStart=value;closeSheet(false);showCalendar(true);checkPending();break;
    }
    case "menu":
      showSheet("menu","التقويم",option("find","البحث","العناوين والملاحظات","search")+
        option("open-settings","الإعدادات","التذكيرات وملفات التقويم","sliders"));
      break;
    case "find": closeSheet(false);openPage("search",true);break;
    case "open-settings": closeSheet(false);openPage("settings");break;
    case "search-more": showSearch(true);break;
    case "add": addFor(selected);break;
    case "add-date": addFor(id);break;
    case "new-event": case "new-task": form(fresh(act==="new-event"?"event":"task"));break;
    case "open": editItem(id);break;
    case "toggle": toggleTask(id);break;
    case "save": saveForm();break;
    case "duplicate": form({...formValues(),id:"",done:false});break;
    case "delete":
      draft=formValues();
      confirmation("delete","حذف "+(draft.kind==="event"?"الموعد؟":"المهمة؟"),"سيُزال العنصر وتذكيره من التقويم.",
        "delete-confirm","حذف");
      break;
    case "delete-cancel": form(draft);break;
    case "delete-confirm":
      if(api.delete(draft.id)){
        closeSheet(false);if(page==="search")showSearch();else showCalendar();notify("حُذف العنصر");checkPending();
      }else notify("تعذّر حذف العنصر.");
      break;
    case "week-start": weekStartSheet();break;
    case "week-start-save":
      weekStart=Number($("week-start").value);api.setWeekStart(weekStart);closeSheet(false);
      $("view").innerHTML=settingsPage();notify("حُفظت بداية الأسبوع");checkPending();break;
    case "notifications": notificationsSheet();break;
    case "notice-test": notify(api.testNotice()?"أُرسل تذكير تجريبي":"تعذّر إرسال التذكير.");break;
    case "notice-allow": api.noticePermission();break;
    case "notice-exact": api.exactPermission();break;
    case "backup": case "ical": api.document(act);break;
    case "restore": confirmation("restore","استعادة نسخة احتياطية؟",
      "ستحلّ المواعيد والمهام الموجودة في النسخة محلّ تقويمك الحالي.","restore-confirm","اختيار النسخة");break;
    case "restore-confirm": closeSheet(false);api.document("restore");break;
    case "close": closeSheet();break;
  }
}
document.addEventListener("click",event=>{
  if(event.target===$("shade")&&!working){closeSheet();return;}
  const control=event.target.closest("button[data-act]");
  if(control&&!control.disabled)action(control.dataset.act,control.dataset.id);
});
document.addEventListener("input",event=>{
  if(event.target.id!=="search")return;
  searchText=event.target.value;clearTimeout(searchTimer);searchTimer=setTimeout(showSearch,130);
});
document.addEventListener("change",event=>{
  if(sheet!=="form")return;
  const id=event.target.id;
  if(id==="time-toggle")timeFields();
  if(id==="remind")updateHint();
  if(["start","end","date"].includes(id))checkConflict();
});
document.addEventListener("keydown",event=>{
  if(!sheet||working)return;
  if(event.key==="Escape"){event.preventDefault();closeSheet();return;}
  if(event.key!=="Tab")return;
  const nodes=[...$("sheet").querySelectorAll("button,input,select,textarea")]
    .filter(node=>!node.disabled&&node.getClientRects().length);
  if(!nodes.length){event.preventDefault();return;}
  if(event.shiftKey&&[nodes[0],$("sheet")].includes(document.activeElement)){event.preventDefault();nodes[nodes.length-1].focus();}
  else if(!event.shiftKey&&document.activeElement===nodes[nodes.length-1]){event.preventDefault();nodes[0].focus();}
});
window.calendarBack=()=>{
  if(working)return true;
  if(sheet){closeSheet();return true;}
  if(page!=="calendar"){openPage("calendar");return true;}
  return false;
};
window.calendarOpened=openNotice;
window.calendarPermissions=()=>{if(sheet==="notices")notificationsSheet();else if(sheet==="form")updateHint();};
window.calendarProcessing=active=>{
  working=active;if(active)showSheet("busy","جارٍ العمل",'<p class="sheet-copy">لحظات ويجهز الملف.</p>');
  else if(sheet==="busy")closeSheet(false);
};
window.calendarResult=(ok,message,kind)=>{
  if(ok&&kind==="restore"){searchText="";openPage("calendar");}
  notify(message);checkPending();
};
let gesture=null,skipTapUntil=0,resizeTimer=0;
$("view").addEventListener("touchstart",event=>{
  if(page!=="calendar"||sheet||event.touches.length!==1)return;
  const finger=event.touches[0];gesture={x:finger.clientX,y:finger.clientY};
},{passive:true});
$("view").addEventListener("touchend",event=>{
  const from=gesture;
  gesture=null;
  if(!from||sheet||event.changedTouches.length!==1)return;
  const finger=event.changedTouches[0],dx=finger.clientX-from.x,dy=finger.clientY-from.y;
  if(Math.abs(dx)>58&&Math.abs(dx)>1.8*Math.abs(dy)){
    skipTapUntil=Date.now()+270;
    movePeriod(dx>0?1:-1);
  }
},{passive:true});
$("view").addEventListener("touchcancel",()=>{gesture=null;},{passive:true});
$("view").addEventListener("click",event=>{
  if(Date.now()<skipTapUntil){event.preventDefault();event.stopPropagation();}
},true);
window.addEventListener("resize",()=>{
  clearTimeout(resizeTimer);
  resizeTimer=setTimeout(()=>{if(page==="calendar"&&!sheet)showCalendar();},120);
});
setInterval(refreshNow,30000);
openPage("calendar");
