import glob,re
mods=['api','core','io','rule','ui', 'bot']
tot=0
for m in mods:
 s=0
 for f in glob.glob(f'modules/{m}/build/test-results/test/TEST-*.xml'):
  txt=open(f,encoding='utf-8').read(300)
  m2=re.search(r'tests="(\d+)"',txt)
  if m2:s+=int(m2.group(1))
 print(f'{m}: {s}')
 tot+=s
print('overall:',tot)