import { createClient } from '@supabase/supabase-js';
import { get, set } from 'idb-keyval';
import { empty, encode, decode, type Workspace } from './model';
const url=import.meta.env.VITE_SUPABASE_URL||'';
const key=import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY||'';
export const cloud=url&&key?createClient(url,key,{auth:{flowType:'pkce'}}):null;
export type Local={workspace:Workspace;revision:number;dirty:boolean};
export const loadLocal=async(owner:string):Promise<Local>=>await get('steady:'+owner)||{workspace:empty(),revision:0,dirty:false};
export const saveLocal=(owner:string,value:Local)=>set('steady:'+owner,value);
export async function sync(owner:string,local:Local):Promise<Local>{
 if(!cloud)throw Error('Account connection is not configured yet.');
 const {data:auth,error:authError}=await cloud.auth.getUser();if(authError||auth.user?.id!==owner)throw Error('Sign in again before syncing.');
 const {data:remote,error}=await cloud.from('steady_workspaces').select('*').eq('user_id',owner).maybeSingle();if(error)throw error;
 if(remote&&remote.revision!==local.revision){
  if(local.dirty)throw Error('SYNC_CONFLICT');
  return pull(owner);
 }
 if(!local.dirty&&remote)return local;
 const bytes=encode(local.workspace);if(bytes.length>20*1024*1024)throw Error('Cloud transfer supports up to 20 MB. Use offline backup for larger workspaces.');
 const path=owner+'/'+crypto.randomUUID()+'.zip';
 const upload=await cloud.storage.from('steady-backups').upload(path,new Blob([bytes.slice().buffer],{type:'application/zip'}),{upsert:false});if(upload.error)throw upload.error;
 const result=await cloud.rpc('steady_commit',{expected_revision:local.revision,new_path:path,new_settings:local.workspace.settings});
 if(result.error){await cloud.storage.from('steady-backups').remove([path]);throw result.error;}
 if(remote?.object_path)await cloud.storage.from('steady-backups').remove([remote.object_path]);
 return {...local,revision:result.data[0].revision,dirty:false};
}
export async function pull(owner:string):Promise<Local>{
 if(!cloud)throw Error('Account connection is not configured yet.');
 const {data:row,error}=await cloud.from('steady_workspaces').select('*').eq('user_id',owner).single();if(error)throw error;
 const result=await cloud.storage.from('steady-backups').download(row.object_path);if(result.error)throw result.error;
 const workspace=decode(new Uint8Array(await result.data.arrayBuffer()));workspace.settings=row.settings;
 return {workspace,revision:row.revision,dirty:false};
}
