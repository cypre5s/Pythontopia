import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ExperienceTools from '../src/features/ExperienceTools.vue';
import { useSession } from '../src/shared/session';
import { api } from '../src/shared/api';
import { captureExperience, restoreExperienceCaches } from '../src/shared/devTools';

let wrapper: VueWrapper | undefined;
const overview = {chapters:[{id:179,title:'第一章',available:true,status:'published'}],materials:[],activities:[{id:'practice',chapter_id:179,title:'练习',kind:'practice',published:true,questions:3}],quests:[{id:'arrival',title:'初来小镇',chapter_id:179,nodes:[{id:'welcome',title:'欢迎',status:'ready'}]}],checkpoints:[]};
beforeEach(() => {
  setActivePinia(createPinia());
  const session = useSession();
  session.user = {id:7,name:'学生',student_id:'7',role:'student',dev_tools_enabled:true};
  session.classroomId = 2;
  vi.spyOn(api,'get').mockResolvedValue(overview);
});
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.restoreAllMocks(); sessionStorage.clear(); localStorage.clear(); });
async function tool() { wrapper = mount(ExperienceTools); await flushPromises(); return wrapper; }

it('opens an authorized activity at the requested question without marking progress', async () => {
  const post = vi.spyOn(api,'post').mockResolvedValue({workspace:{kind:'practice',activity_id:'practice',question_no:2}});
  const session = useSession(), navigate = vi.spyOn(session,'navigate');
  const w = await tool();
  await w.findAll('select')[2]!.setValue('practice');
  await w.findAll('input[type=number]')[1]!.setValue('2');
  await w.findAll('button').find(b=>b.text()==='打开活动')!.trigger('click');
  await flushPromises();
  expect(post).toHaveBeenCalledExactlyOnceWith('/dev/experience/open', {classroom_id:2,kind:'activity',target:'practice',page_no:1,question_no:2,video_time:0});
  expect(navigate).toHaveBeenCalledWith({kind:'practice',activity_id:'practice',question_no:2});
});

it('requires a stage preview and passes its concurrency token to the state-changing operation', async () => {
  vi.spyOn(api,'post').mockResolvedValue({expected:'token',completed:[],target:'欢迎'});
  const session = useSession(), restart = vi.spyOn(session,'restartCurrentClassroom').mockResolvedValue(true);
  const w = await tool();
  await w.get('[role=tab]:nth-child(2)').trigger('click');
  await w.findAll('select')[0]!.setValue('arrival');
  await w.findAll('select')[1]!.setValue('welcome');
  await w.findAll('button').find(b=>b.text()==='预览阶段调整')!.trigger('click');
  await flushPromises();
  expect(restart).not.toHaveBeenCalled();
  await w.get('.experience-confirm .town-primary').trigger('click');
  expect(restart).toHaveBeenCalledWith({path:'/dev/experience/stage',body:{classroom_id:2,quest_id:'arrival',node_id:'welcome',choices:{},any_dependencies:{},expected:'token'},restore:false});
});

it('ignores a late content response after switching account', async () => {
  let finish!: (value: unknown) => void;
  vi.spyOn(api,'post').mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));
  const session = useSession(), navigate = vi.spyOn(session,'navigate');
  const w = await tool();
  await w.findAll('select')[2]!.setValue('practice');
  await w.findAll('button').find(b=>b.text()==='打开活动')!.trigger('click');
  session.user = {...session.user!,id:8};
  await flushPromises();
  finish({workspace:{kind:'practice',activity_id:'practice'}});
  await flushPromises();
  expect(navigate).not.toHaveBeenCalled();
});

it('captures the last activity and restores only the owning classroom reading and draft caches', () => {
  const session = useSession();
  session.open({kind:'practice',activity_id:'practice'});
  session.open({kind:'developer'});
  sessionStorage.setItem('read:7:2:question:practice','2');
  sessionStorage.setItem('read:8:2:question:practice','3');
  localStorage.setItem('draft:7:2:question','my draft');
  localStorage.setItem('auth-token','private');
  const saved = captureExperience(7,2);
  expect(saved.session!['read:7:2:workspace']).toContain('practice');
  expect(saved.local!['auth-token']).toBeUndefined();
  sessionStorage.setItem('read:7:2:question:practice','1');
  restoreExperienceCaches(7,2,{...saved,local:{...saved.local,'auth-token':'injected','draft:8:2:question':'other'}});
  expect(sessionStorage.getItem('read:7:2:question:practice')).toBe('2');
  expect(sessionStorage.getItem('read:8:2:question:practice')).toBe('3');
  expect(localStorage.getItem('auth-token')).toBe('private');
  expect(localStorage.getItem('draft:8:2:question')).toBeNull();
});
