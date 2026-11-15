import json,glob,re,os,sys,collections
FX=sys.argv[1]
KNOWN={'stocks','valueinvesting','investing','inwestowanie','gielda'}
def norm(s):
    s=s.strip().lower()
    s=re.sub(r'^[#]+','',s); s=re.sub(r'^/?r/','',s); return s.strip()
gold={p['post_id']:p for p in json.load(open(f'{FX}/assessor/gold/posts.json'))}
red={}
for f in glob.glob(f'{FX}/reddit/search-posts-*.json'):
    for p in json.load(open(f))['data']: red[p['id']]=p
stats=collections.defaultdict(lambda: collections.Counter())
ex=collections.defaultdict(list)
def count(group,src,channels,targets,fname,okflag=True):
    c=stats[group]; c['ocen']+=1
    if not targets: c['ocen_bez_celow']+=1
    ch={norm(x) for x in channels} if channels else set()
    for t in targets:
        q=t['anchor']['quote']; n=norm(q); c['cele']+=1
        if q.strip().startswith('#'): c['cele_z_#']+=1
        if channels is not None and n in ch: c['naruszenie_item.channels']+=1; ex[group].append(('channels',fname,q,sorted(ch)))
        if n in KNOWN: c['cel_=_znana_nazwa_kanalu']+=1; ex[group].append(('known',fname,q,sorted(ch)))
        if channels is not None and n not in ch and n in KNOWN: c['znana_nazwa_ale_inny_kanal']+=1
# A: recordings (Wykop #gielda, gold inputs)
for f in sorted(glob.glob(f'{FX}/llm/recordings/*/*.json')):
    d=json.load(open(f)); model=f.split('/')[-2]
    g=f'rec:{model}:{d["promptVersion"]}'
    if d['status']!='ok': stats[g]['status_failed_pominiete']+=1; continue
    inp=gold[d['post_id']]['input']['item']
    r=json.loads(d['rawText'])
    count(g,'wykop',inp['channels'],r['targets'],os.path.basename(f))
# scenario days
for f in sorted(glob.glob(f'{FX}/llm/scenario-days/*.json')):
    d=json.load(open(f)); r=json.loads(d['choices'][0]['message']['content']); b=os.path.basename(f)
    if b.startswith('reddit-'):
        p=red[b[7:-5]]; ch=[p['subreddit']]; g='scenario:reddit:luna'
    else: ch=['gielda']; g='scenario:wykop:luna'
    count(g,'',ch,r['targets'],b)
for g in sorted(stats): print(g,dict(stats[g]))
print()
for g in sorted(ex):
    for e in ex[g][:8]: print(g,e)
