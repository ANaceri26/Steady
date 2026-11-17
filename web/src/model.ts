import { zipSync, Unzip, UnzipInflate, strToU8, strFromU8 } from 'fflate';
export type Row = Record<string, any>;
export const tables = ['projects','items','entries','journal_groups','journal','photos','journal_photos','gym_routines','gym_exercises','gym_sessions','gym_sets','snoozes'] as const;
export type Table = typeof tables[number];
export type Data = Record<Table, Row[]>;
export type Workspace = {data:Data; media:Record<string,Uint8Array>; settings:{displayName:string; signalFirst:boolean}};
export const fields:Record<Table,string[]> = {
 projects:['id','title','notes','signal'],items:['id','title','notes','section','project','goal','scheduled','enabled','repeat','minute','start','signal_override'],
 entries:['item','period','status','updated'],journal_groups:['id','name'],journal:['id','item','title','period','progress','obstacle','next','created','group_id','kind','project_title'],
 photos:['id','item','path'],journal_photos:['id','journal','path'],gym_routines:['id','name'],gym_exercises:['id','routine','name','unit'],gym_sessions:['id','routine','name','date','notes'],gym_sets:['id','session','exercise','name','position','reps','weight','unit'],snoozes:['item','period','at']
};
const numeric=new Set(['id','item','project','goal','scheduled','enabled','repeat','minute','signal','signal_override','updated','created','group_id','journal','routine','session','exercise','position','reps','weight','at']);
const nullable=new Set(['items.project','items.signal_override','journal.item','journal.group_id','gym_sessions.routine','gym_sets.exercise']);
export const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`};
export const empty=():Workspace=>({data:Object.fromEntries(tables.map(t=>[t,[]])) as unknown as Data,media:{},settings:{displayName:'',signalFirst:false}});
export const nextId=(rows:Row[])=>Math.max(0,...rows.map(x=>x.id||0))+1;
export function validDate(s:string){return /^\d{4}-\d{2}-\d{2}$/.test(s)&&new Date(s+'T12:00:00Z').toISOString().slice(0,10)===s}
export function due(i:Row,date:string){if(!i.scheduled||date<i.start)return false;const d=new Date(date+'T12:00:00Z'),s=new Date(i.start+'T12:00:00Z');return i.repeat===0?date===i.start:i.repeat===1?true:i.repeat===2?d.getUTCDay()===s.getUTCDay():d.getUTCDate()===Math.min(s.getUTCDate(),new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0)).getUTCDate())}
export function period(i:Row,date:string){if(i.repeat===0)return i.scheduled?i.start:'once';if(!i.goal||i.repeat===1)return date;const d=new Date(date+'T12:00:00Z');if(i.repeat===2)d.setUTCDate(d.getUTCDate()-((d.getUTCDay()+6)%7));else d.setUTCDate(1);return d.toISOString().slice(0,10)}
export function signal(w:Workspace,i:Row){return i.signal_override??w.data.projects.find(p=>p.id===i.project)?.signal??0}
export const status=(w:Workspace,i:Row,date=today())=>w.data.entries.find(e=>e.item===i.id&&e.period===period(i,date))?.status||'';
export function setStatus(w:Workspace,i:Row,value:string,date=today()){const key=period(i,date);w.data.entries=w.data.entries.filter(e=>!(e.item===i.id&&e.period===key));w.data.entries.push({item:i.id,period:key,status:value,updated:Date.now()});}
export function removeItem(w:Workspace,id:number){w.data.items=w.data.items.filter(x=>x.id!==id);for(const t of ['entries','photos','snoozes'] as Table[])w.data[t]=w.data[t].filter(x=>x.item!==id);w.data.journal.forEach(x=>{if(x.item===id)x.item=null})}
export function validate(w:Workspace){
 for(const table of tables){const rows=w.data[table];if(!Array.isArray(rows)||rows.length>100000)throw Error('Invalid backup collection.');const ids=new Set();
  for(const row of rows){if(Object.keys(row).length!==fields[table].length)throw Error('Unexpected backup fields.');for(const key of fields[table]){const v=row[key];if(v===null&&nullable.has(`${table}.${key}`))continue;if(numeric.has(key)){if(typeof v!=='number'||!Number.isFinite(v)||(key!=='weight'&&!Number.isSafeInteger(v)))throw Error('Invalid numeric field.')}else if(typeof v!=='string')throw Error('Invalid text field.');}
   const pk='id'in row?row.id:row.item+'|'+row.period;if(ids.has(pk))throw Error('Duplicate backup record.');ids.add(pk);
   if('id'in row&&row.id<=0)throw Error('Invalid ID.');
  }
 }
 const refs: [Table,string,Table][]=[['items','project','projects'],['entries','item','items'],['journal','item','items'],['journal','group_id','journal_groups'],['photos','item','items'],['journal_photos','journal','journal'],['gym_exercises','routine','gym_routines'],['gym_sessions','routine','gym_routines'],['gym_sets','session','gym_sessions'],['gym_sets','exercise','gym_exercises'],['snoozes','item','items']];
 for(const [t,col,target] of refs){const ids=new Set(w.data[target].map(r=>r.id));for(const row of w.data[t])if(row[col]!==null&&!ids.has(row[col]))throw Error('Backup has a broken reference.');}
 for(const row of w.data.items)if(!validDate(row.start)||row.minute<0||row.minute>1439||row.repeat<0||row.repeat>3||![row.goal,row.enabled,row.scheduled].every(v=>v===0||v===1)||![null,0,1].includes(row.signal_override))throw Error('Invalid task schedule.');
 if(w.data.projects.some(p=>![0,1].includes(p.signal)))throw Error('Invalid project priority.');
 const groups=new Set();for(const g of w.data.journal_groups){const name=g.name.trim().toLowerCase();if(!name||name.length>40||groups.has(name))throw Error('Invalid group name.');groups.add(name)}
 for(const j of w.data.journal)if(!['Journal','Advice','Project log'].includes(j.kind))throw Error('Invalid journal type.');
 for(const s of w.data.gym_sessions)if(!validDate(s.date))throw Error('Invalid workout date.');
 for(const s of w.data.gym_sets)if(s.reps<=0||s.weight<0)throw Error('Invalid workout set.');
 for(const p of [...w.data.photos,...w.data.journal_photos])if(!/^media\/[1-9][0-9]*\.jpg$/.test(p.path)||!w.media[p.path])throw Error('A backup photo is missing.');
}
export function encode(w:Workspace){validate(w);const json=strToU8(JSON.stringify({format:'steady-backup',version:1,data:w.data}));if(json.length>16*1024*1024)throw Error('Backup manifest exceeds 16 MB.');const files:Record<string,Uint8Array>={'steady.json':json};for(const p of [...w.data.photos,...w.data.journal_photos])files[p.path]=w.media[p.path];if(Object.keys(files).length>10000||Object.values(files).reduce((n,b)=>n+b.length,0)>256*1024*1024)throw Error('Backup exceeds supported limits.');return zipSync(files,{level:6,mtime:new Date(1980,0,1)});}
export function decode(bytes:Uint8Array):Workspace{
 const files:Record<string,Uint8Array>={};let total=0,error:Error|undefined;const seen=new Set<string>();
 const unzip=new Unzip(file=>{if(seen.has(file.name)||seen.size>=10000||!(file.name==='steady.json'||/^media\/[1-9][0-9]*\.jpg$/.test(file.name)))throw Error('Unsafe or duplicate archive path.');seen.add(file.name);const chunks:Uint8Array[]=[];let length=0;
 file.ondata=(err,chunk,final)=>{if(err){error=err;file.terminate();return}length+=chunk.length;total+=chunk.length;if(total>256*1024*1024||(file.name==='steady.json'&&length>16*1024*1024)){error=Error('Backup exceeds supported limits.');file.terminate();return}chunks.push(chunk);if(final){const out=new Uint8Array(length);let offset=0;for(const c of chunks){out.set(c,offset);offset+=c.length}files[file.name]=out}};file.start()});unzip.register(UnzipInflate);
 for(let n=0;n<bytes.length;n+=16384){unzip.push(bytes.subarray(n,n+16384),n+16384>=bytes.length);if(error)throw error;}
 if(!files['steady.json'])throw Error('Choose a Steady full backup ZIP.');const doc=JSON.parse(strFromU8(files['steady.json']));if(doc.format!=='steady-backup'||doc.version!==1||Object.keys(doc.data||{}).length!==tables.length)throw Error('Unsupported backup format.');delete files['steady.json'];const w={...empty(),data:doc.data,media:files};validate(w);return w;
}
export const escapeHtml=(s:string)=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
export function journalExport(w:Workspace){const files:Record<string,Uint8Array>={};const html=`<!doctype html><html lang="en"><meta charset="utf-8"><title>Steady Journal</title><style>body{font:16px/1.6 system-ui;max-width:840px;margin:40px auto;padding:24px}article{padding:24px 0;border-bottom:1px solid #ddd}p{white-space:pre-wrap}img{max-width:100%}</style><h1>Steady Journal</h1>`+w.data.journal.map(j=>{const group=w.data.journal_groups.find(g=>g.id===j.group_id)?.name||'Ungrouped';return `<article><h2>${escapeHtml(j.title)}</h2><p>${escapeHtml(j.kind+' · '+j.period+' · '+group)}</p><p>Project: ${escapeHtml(j.project_title)}</p>`+[['Progress / reflection',j.progress],['Obstacles & lessons',j.obstacle],['Next step',j.next]].map(([k,v])=>`<h3>${k}</h3><p>${escapeHtml(v)}</p>`).join('')+w.data.journal_photos.filter(p=>p.journal===j.id).map(p=>{files[p.path]=w.media[p.path];return `<img src="${p.path}" alt="Journal photo">`}).join('')+'</article>'}).join('')+'</html>';files['journal.html']=strToU8(html);return zipSync(files);}
export function download(bytes:Uint8Array,name:string){const url=URL.createObjectURL(new Blob([bytes.slice().buffer],{type:'application/zip'}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),10000)}
