#!/usr/bin/env python3
import html,json,os,re,subprocess,sys,time,unicodedata,xml.etree.ElementTree as ET
from pathlib import Path
PKG='com.rabpit.backroom'; ACT=PKG+'/.MainActivity'; APK=sys.argv[1]; OUT=Path(os.getenv('EMU_OUT','emu-level0-30turn-results')); OUT.mkdir(parents=True,exist_ok=True); (OUT/'ui').mkdir(exist_ok=True); (OUT/'screens').mkdir(exist_ok=True)
TARGET=int(os.getenv('TARGET_ACTIONS','30')); TURN_TIMEOUT=int(os.getenv('TURN_TIMEOUT_SECONDS','180')); POLL=float(os.getenv('POLL_SECONDS','1.5'))
issues=[]; trace=[]; providers=set(); done=0
cov={k:False for k in ['launch','freedom','keyboard','gm_choices','save','load','new_game','delete_save','inventory','party','snapshot','provider','combat']}
def run(*a,check=True): return subprocess.run(a,text=True,capture_output=True,check=check)
def adb(*a,check=True): return run('adb',*a,check=check)
def norm(s):
 s=(s or '').replace('đ','d').replace('Đ','D'); s=unicodedata.normalize('NFKD',s); return ''.join(c for c in s if not unicodedata.combining(c)).casefold().strip()
def issue(sev,code,msg,**d): issues.append({'severity':sev,'code':code,'message':msg,**d}); print(f'[{sev.upper()}] {code}: {msg}',flush=True)
def shot(n):
 with (OUT/'screens'/f'{n}.png').open('wb') as f: subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,stderr=subprocess.DEVNULL)
def dump(n):
 adb('shell','uiautomator','dump','/sdcard/window.xml',check=False); p=OUT/'ui'/f'{n}.xml'; adb('pull','/sdcard/window.xml',str(p),check=False)
 try:return ET.parse(p).getroot()
 except:return None
def nodes(r): return list(r.iter('node')) if r is not None else []
def text(n): return (n.attrib.get('text') or '')+' '+(n.attrib.get('content-desc') or '')
def blob(r): return '\n'.join(norm(text(n)) for n in nodes(r))
def center(n):
 m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds') or '')
 if not m:return None
 x1,y1,x2,y2=map(int,m.groups()); return ((x1+x2)//2,(y1+y2)//2) if x2>x1 and y2>y1 else None
def tap(n):
 c=center(n)
 if not c:return False
 adb('shell','input','tap',str(c[0]),str(c[1])); return True
def btn(r,label,enabled=None):
 w=norm(label)
 for n in nodes(r):
  if not (n.attrib.get('class') or '').endswith('Button'):continue
  if enabled is not None and ((n.attrib.get('enabled')=='true')!=enabled):continue
  t=norm(text(n))
  if t==w or t.startswith(w+' '):return n
 return None
def edit(r):
 return next((n for n in nodes(r) if (n.attrib.get('class') or '').endswith('EditText') and n.attrib.get('enabled','true')=='true'),None)
def turn(r):
 a=[]; ts=[norm(text(n)) for n in nodes(r)]
 for i,t in enumerate(ts):
  m=re.search(r'\bturn\s*(\d+)\b',t)
  if m:a.append(int(m.group(1)))
  if t=='turn' and i+1<len(ts) and ts[i+1].isdigit():a.append(int(ts[i+1]))
 return max(a) if a else None
def resumed(): return bool(re.search(r'(mResumedActivity|topResumedActivity).*com\.rabpit\.backroom/\.MainActivity',adb('shell','dumpsys','activity','activities',check=False).stdout))
def wait_ready(tag,timeout=45):
 end=time.time()+timeout; last=None
 while time.time()<end:
  r=dump(tag); last=r
  if r is not None and resumed() and edit(r) is not None and btn(r,'Thực hiện',False) is not None:return r,turn(r)
  time.sleep(POLL)
 shot(tag+'-timeout'); return last,turn(last)
def core():
 p=adb('exec-out','run-as',PKG,'cat','shared_prefs/backroom_game_state_core.xml',check=False)
 if p.returncode or not p.stdout.strip():return None
 try:
  x=ET.fromstring(p.stdout); raw=next((html.unescape(c.text or '') for c in x if c.attrib.get('name')=='game_state'),None); return json.loads(raw) if raw else None
 except Exception as e: issue('warning','core_parse',str(e)); return None
def obj(v):
 if isinstance(v,dict):return v
 try:return json.loads(v) if isinstance(v,str) else {}
 except:return {}
def facts(c):
 if not c:return {'beat':'','completed':[],'level':None,'party':[],'lucia':False,'firstContact':False,'decision':False,'joinAllowed':False,'combat':False}
 w=c.get('world') or {}; f=obj(w.get('flagsJson')); l=obj(w.get('levelJson')); a=f.get('storyArc') if isinstance(f.get('storyArc'),dict) else {}
 party=list((c.get('party') or {}).get('memberIds') or []); pnorm=[norm(str(x)) for x in party]
 enc=f.get('luciaEncounter') if isinstance(f.get('luciaEncounter'),dict) else {}; status=norm(str(enc.get('status') or ''))
 beat=str(a.get('currentBeat') or ''); completed=list(a.get('completed') or [])
 first='STORY.LEVEL0.FIRST_CONTACT_COMPLETE' in completed
 decision='STORY.LEVEL0.LUCIA_DECISION_COMPLETE' in completed
 join_allowed=first and decision and status=='joined' and enc.get('partyEligible') is True and enc.get('joinPending') is False
 lucia_party=any(x=='lucia' or 'lucia' in x or 'hua thuy mai' in x for x in pnorm)
 lucia_encounter=bool(enc) and (status in {'met','contact','contacted','joined','active'} or bool(enc.get('partyEligible')) or bool(enc.get('joinPending')))
 serialized=json.dumps(c,ensure_ascii=False).casefold().replace(' ','')
 return {'beat':beat,'completed':completed,'level':l.get('number'),'party':party,'lucia':lucia_party or lucia_encounter or first or decision,'firstContact':first,'decision':decision,'joinAllowed':join_allowed,'combat':'"active":true' in serialized}
def scan(r):
 b=blob(r); cov['inventory']|='inventory' in b; cov['party']|='party' in b; cov['snapshot']|=any((n.attrib.get('class') or '').endswith('Image') for n in nodes(r)); cov['combat']|='combat' in b
 for x in ['gemini','haiku']:
  if x in b:providers.add(x)
 cov['provider']=bool(providers)
 for n in nodes(r):
  if (n.attrib.get('class') or '').endswith('Button') and re.match(r'^[ABC][\.\)]\s+',n.attrib.get('text') or '',re.I):cov['gm_choices']=True
def validate(i,r,c,before_f):
 f=facts(c); before_comp=set((before_f or {}).get('completed') or []); comp=set(f['completed']); arr_before='STORY.LEVEL0.ARRIVAL' in before_comp; arr='STORY.LEVEL0.ARRIVAL' in comp; first=f['firstContact']; decision=f['decision']; lucui='lucia' in blob(r) or 'hua thuy mai' in blob(r); lucia_party=any(x=='lucia' or 'lucia' in x or 'hua thuy mai' in x for x in [norm(str(v)) for v in f['party']])
 if not arr_before and (lucui or f['lucia'] or first):issue('hard','lucia_before_arrival','Lucia/first-contact xuất hiện trong lượt bắt đầu trước STORY.LEVEL0.ARRIVAL',action=i,beat=f['beat'])
 if first and not arr:issue('hard','first_contact_before_arrival','FIRST_CONTACT xảy ra khi ARRIVAL chưa hoàn tất',action=i)
 if decision and not first:issue('hard','lucia_decision_before_first_contact','LUCIA_DECISION_COMPLETE tồn tại khi FIRST_CONTACT_COMPLETE chưa hoàn tất',action=i)
 if lucia_party and not f['joinAllowed']:issue('hard','lucia_party_early','Lucia vào Party trước decision/join confirmation',action=i,firstContact=first,decision=decision,joinAllowed=f['joinAllowed'])
 if f['level'] is not None and str(f['level']) not in ('0','0.0'):issue('hard','left_level0',f'Đã rời Level 0: {f["level"]}',action=i)
 cov['combat']|=f['combat']
def keyboard(r):
 e=edit(r)
 if e is None or not tap(e):issue('medium','keyboard','Không focus được ô nhập');return
 time.sleep(.5); s=norm(adb('shell','dumpsys','input_method',check=False).stdout); cov['keyboard']='mcurmethodid' in s or 'minputshown=true' in s or 'mshowrequested=true' in s; adb('shell','input','keyevent','4')
def wait_advance(i,before):
 end=time.time()+TURN_TIMEOUT; last=None
 while time.time()<end:
  r=dump(f'a{i:02d}-wait'); last=r
  if r is None:time.sleep(POLL);continue
  be=blob(r)
  if 'loi gemini' in be or 'game state core tu choi' in be:
   details=[(n.attrib.get('text') or '').strip() for n in nodes(r) if 'loi gemini' in norm(text(n)) or 'game state core tu choi' in norm(text(n))]
   issue('hard','turn_error','UI báo lỗi xử lý lượt',action=i,detail=' | '.join(x for x in details if x)[:1000]);return False,r
  t=turn(r)
  if t is not None and before is not None and t>before:scan(r);return True,r
  time.sleep(POLL)
 issue('hard','turn_timeout',f'Lượt {i} quá {TURN_TIMEOUT}s',action=i);shot(f'a{i:02d}-timeout');return False,last
def freedom(i,action):
 r,before=wait_ready(f'a{i:02d}-ready'); e=edit(r)
 if e is None or not tap(e):issue('hard','editor_missing','Không dùng được Freedom input',action=i);return False,r
 cov['freedom']=True; safe=re.sub(r'[^A-Za-z0-9 ]+','',action).strip().replace(' ','%s'); adb('shell','input','text',safe); adb('shell','input','keyevent','4'); time.sleep(.25); r=dump(f'a{i:02d}-typed'); x=btn(r,'Thực hiện',True)
 if x is None or not tap(x):issue('hard','execute_disabled','Nút Thực hiện không bật',action=i);return False,r
 return wait_advance(i,before)
def choice(i):
 r,before=wait_ready(f'a{i:02d}-choice'); cs=[n for n in nodes(r) if (n.attrib.get('class') or '').endswith('Button') and n.attrib.get('enabled','true')=='true' and re.match(r'^[ABC][\.\)]\s+',n.attrib.get('text') or '',re.I) and center(n)]
 if not cs:return freedom(i,'continue exploring level zero carefully')
 cov['gm_choices']=True; tap(cs[0]); return wait_advance(i,before)
def page(to_management):
 # Canonical Android UI has two horizontal pages: Gameplay -> Management. Save/Load lives
 # on Management, so navigate there before doing any vertical search. The old harness only
 # scrolled the Gameplay page and could never prove the controls were broken.
 if to_management:adb('shell','input','swipe','980','1180','120','1180','300')
 else:adb('shell','input','swipe','120','1180','980','1180','300')
 time.sleep(.55)
def control(label):
 page(True)
 for j in range(6):
  r=dump('ctrl-'+norm(label)+str(j)); x=btn(r,label,True)
  if x is not None and center(x) is not None:return x
  adb('shell','input','swipe','540','1950','540','620','250');time.sleep(.35)
 return None
def save_load(t0):
 try:
  x=control('Lưu')
  if x is not None and tap(x):time.sleep(.5);cov['save']='da luu turn' in blob(dump('saved'))
  else:issue('medium','save_button','Không dùng được Lưu sau khi đã mở Management page')
  x=control('Tải')
  if x is not None and tap(x):time.sleep(.7);r=dump('loaded');cov['load']='da tai save turn' in blob(r); t=turn(r); issue('hard','load_turn',f'Load đổi turn {t0}->{t}') if t0 and t and t!=t0 else None
  else:issue('medium','load_button','Không dùng được Tải sau khi đã mở Management page')
 finally:page(False)
def destructive(label,key):
 x=control(label)
 if x is None or not tap(x):issue('medium',key,'Không dùng được '+label);return
 time.sleep(.3);x=control(label)
 if x is None or not tap(x):issue('medium',key+'_confirm','Không xác nhận được '+label);return
 time.sleep(.8);r=dump('after-'+key); cov[key]=turn(r)==1
 if turn(r)!=1:issue('hard',key+'_turn',label+' không về Turn 1',turn=turn(r))
 if 'lucia' in blob(r):issue('hard',key+'_lucia',label+' làm Lucia rò vào startup')
def finish(r):
 scan(r); log=adb('logcat','-d',check=False).stdout; (OUT/'logcat.txt').write_text(log,encoding='utf-8',errors='replace')
 if re.search(r'FATAL EXCEPTION|ANR in com\.rabpit\.backroom',log,re.I):issue('hard','android_crash','Logcat có FATAL EXCEPTION/ANR')
 hard=[x for x in issues if x['severity'] in ('hard','critical')]; summary={'actions_completed':done,'target_actions':TARGET,'coverage':cov,'providers':sorted(providers),'issues':issues,'trace':trace}; (OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8'); lines=[f'actions={done}/{TARGET}','coverage='+json.dumps(cov,sort_keys=True),'providers='+','.join(sorted(providers)),f'issues={len(issues)} hard={len(hard)}']+[f"{x['severity'].upper()} {x['code']}: {x['message']}" for x in issues]; (OUT/'result.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8'); print('\n'.join(lines));return 1 if hard else 0
def main():
 global done
 adb('install','-r','-t',APK);adb('shell','pm','clear',PKG,check=False);adb('logcat','-c',check=False);adb('shell','am','start','-W','-n',ACT);r,t=wait_ready('startup');shot('startup')
 if r is None or not resumed():issue('hard','launch','MainActivity không resumed');return finish(r)
 cov['launch']=True;scan(r)
 if t!=1:issue('hard','startup_turn',f'Startup turn={t}')
 if 'lucia' in blob(r) or 'hua thuy mai' in blob(r):issue('hard','startup_lucia','Lucia xuất hiện ngay Prologue')
 if btn(r,'Tìm kiếm',None) is not None or btn(r,'Khám phá',None) is not None:issue('warning','legacy_buttons','Search/Explore legacy vẫn hiện')
 keyboard(r)
 acts=['explore level zero slowly and stay in level zero','search current room carefully and stay in level zero','inspect yellow walls lights floor and corners','listen for sounds and verify current route','continue exploring level zero carefully','check equipment and surroundings and stay in level zero']
 for i in range(1,TARGET+1):
  before_f=facts(core())
  ok,r=choice(i) if i%5==0 and cov['gm_choices'] else freedom(i,acts[(i-1)%len(acts)])
  if not ok:break
  done+=1;c=core();validate(i,r,c,before_f);f=facts(c);trace.append({'action':i,'turn':turn(r),'beat':f['beat'],'completed':f['completed'],'party':f['party'],'level':f['level'],'joinAllowed':f['joinAllowed']});print(f"ACTION {i}/{TARGET}: turn={turn(r)} beat={f['beat']} party={f['party']} joinAllowed={f['joinAllowed']}",flush=True)
  if i==8:save_load(turn(r))
  if i in (5,10,15,20,25,30):shot(f'after-{i:02d}')
 f=facts(core())
 if done<TARGET:issue('hard','short_soak',f'Chỉ hoàn tất {done}/{TARGET} hành động')
 if 'STORY.LEVEL0.ARRIVAL' not in set(f['completed']):issue('hard','arrival_missing','Sau soak chưa hoàn tất STORY.LEVEL0.ARRIVAL',beat=f['beat'])
 if done>=TARGET and 'PROLOGUE' in f['beat']:issue('hard','stuck_prologue','Sau 30 hành động vẫn kẹt Prologue')
 if done>=TARGET and 'STORY.LEVEL0.FIRST_CONTACT_COMPLETE' not in set(f['completed']):issue('medium','lucia_not_reached','Sau 30 hành động chưa tới Lucia first-contact',beat=f['beat'])
 if done>=TARGET:destructive('Bắt đầu lại từ đầu','new_game');destructive('Xóa save trên máy','delete_save')
 return finish(r)
if __name__=='__main__':raise SystemExit(main())
