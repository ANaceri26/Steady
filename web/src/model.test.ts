import {describe,it,expect} from 'vitest';
import {readFileSync,writeFileSync} from 'node:fs';
import {unzipSync,strFromU8} from 'fflate';
import {empty,encode,decode,validate,due,period,signal,setStatus,status,journalExport} from './model';
const task={id:1,title:'Ship prototype',notes:'',section:'',project:null,goal:1,scheduled:1,enabled:1,repeat:2,minute:630,start:'2026-11-17',signal_override:null};
describe('Steady data and offline transfers',()=>{
 it('round-trips empty and populated workspaces',()=>{const w=empty();w.data.items.push({...task});expect(decode(encode(w)).data).toEqual(w.data)});
 it('keeps starting a goal separate from reflection',()=>{const w=empty();w.data.items.push({...task});setStatus(w,task,'started','2026-11-17');expect(status(w,task,'2026-11-17')).toBe('started');expect(w.data.journal).toHaveLength(0);expect(period(task,'2026-11-17')).toBe('2026-11-16')});
 it('inherits Signal but allows task overrides',()=>{const w=empty();w.data.projects.push({id:2,title:'Rover',notes:'',signal:1});expect(signal(w,{...task,project:2})).toBe(1);expect(signal(w,{...task,project:2,signal_override:0})).toBe(0)});
 it('handles monthly end dates and weekly schedules',()=>{expect(due({...task,repeat:3,start:'2026-01-31'},'2026-02-28')).toBe(true);expect(due(task,'2026-11-18')).toBe(false);expect(due(task,'2026-11-24')).toBe(true)});
 it('rejects broken references, bad times and missing photos',()=>{const w=empty();w.data.items.push({...task,project:50});expect(()=>validate(w)).toThrow();w.data.items[0].project=null;w.data.items[0].minute=1440;expect(()=>validate(w)).toThrow();w.data.items[0].minute=500;w.data.photos.push({id:1,item:1,path:'media/1.jpg'});expect(()=>validate(w)).toThrow()});
 it('exports safe project documentation',()=>{const w=empty();w.data.journal.push({id:1,item:null,title:'<script>bad()</script>',period:'2026-11-17',progress:'Learned <b>HTML</b>',obstacle:'A lesson',next:'Next',created:1,group_id:null,kind:'Project log',project_title:'Rover'});const html=strFromU8(unzipSync(journalExport(w))['journal.html']);expect(html).toContain('&lt;script&gt;');expect(html).not.toContain('<script>');expect(html).toContain('Rover')});
 it('imports Android backup and writes a web backup for Android',()=>{const w=decode(new Uint8Array(readFileSync('../app/build/interop/android.zip')));expect(w.data.items).toHaveLength(2);expect(w.data.projects[0].title).toBe('Solar rover');expect(w.data.gym_sets[0].weight).toBe(60.5);expect(w.data.journal[0].progress).toBe('Motor tested');w.data.items[0].title='Edited on web';writeFileSync('../app/build/interop/web.zip',encode(w))});
});
