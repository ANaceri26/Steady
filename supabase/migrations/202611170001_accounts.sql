-- Steady account snapshots. Apply once through Supabase's SQL editor.
create table public.steady_workspaces (
 user_id uuid primary key references auth.users(id) on delete cascade,
 revision bigint not null check (revision > 0),
 object_path text not null,
 settings jsonb not null default '{"displayName":"","signalFirst":false}'::jsonb,
 updated_at timestamptz not null default now()
);
alter table public.steady_workspaces enable row level security;
revoke all on public.steady_workspaces from anon, authenticated;
grant select on public.steady_workspaces to authenticated;
create policy "Read only your workspace" on public.steady_workspaces for select to authenticated using ((select auth.uid()) = user_id);
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('steady-backups','steady-backups',false,20971520,array['application/zip']) on conflict(id) do nothing;
create policy "Read your backup files" on storage.objects for select to authenticated
using(bucket_id='steady-backups' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy "Upload your backup files" on storage.objects for insert to authenticated
with check(bucket_id='steady-backups' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy "Delete unused backup files" on storage.objects for delete to authenticated
using(bucket_id='steady-backups' and (storage.foldername(name))[1]=(select auth.uid())::text
 and not exists(select 1 from public.steady_workspaces w where w.object_path=name));
-- Immutable objects plus compare-and-swap prevent silent overwrites from two devices.
create function public.steady_commit(expected_revision bigint, new_path text, new_settings jsonb)
returns setof public.steady_workspaces language plpgsql security definer set search_path='' as $$
declare uid uuid := auth.uid(); changed public.steady_workspaces;
begin
 if uid is null then raise exception 'Authentication required'; end if;
 if expected_revision < 0 or new_path !~ ('^'||uid::text||'/[0-9a-f-]+\.zip$') then raise exception 'Invalid snapshot'; end if;
 if jsonb_typeof(new_settings) <> 'object' or jsonb_typeof(new_settings->'displayName') is distinct from 'string'
 or length(new_settings->>'displayName') > 80 or jsonb_typeof(new_settings->'signalFirst') is distinct from 'boolean'
 or (select count(*) from jsonb_object_keys(new_settings)) <> 2 then raise exception 'Invalid preferences'; end if;
 if not exists(select 1 from storage.objects where bucket_id='steady-backups' and name=new_path) then raise exception 'Missing snapshot'; end if;
 if expected_revision = 0 then
  insert into public.steady_workspaces(user_id,revision,object_path,settings) values(uid,1,new_path,new_settings)
  on conflict do nothing returning * into changed;
 else
  update public.steady_workspaces set revision=revision+1,object_path=new_path,settings=new_settings,updated_at=now()
  where user_id=uid and revision=expected_revision returning * into changed;
 end if;
 if changed.user_id is null then raise exception 'SYNC_CONFLICT'; end if;
 return next changed;
end; $$;
revoke all on function public.steady_commit(bigint,text,jsonb) from public,anon;
grant execute on function public.steady_commit(bigint,text,jsonb) to authenticated;
