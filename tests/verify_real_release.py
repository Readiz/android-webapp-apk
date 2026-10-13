"""Run against separately built notes v1/v2 and library fixtures; no network beyond loopback."""
import argparse,copy,hashlib,importlib.util,json,os,subprocess,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];TOOLS=ROOT/'skills/readiz-webapp-android/assets/starter/tools';sys.path.insert(0,str(TOOLS))
from config import load
from publish import publish
from release_lib import inspect
from verify_deployment import verify
p=argparse.ArgumentParser();p.add_argument('--fixtures',required=True);a=p.parse_args();f=Path(a.fixtures)
c1=load(ROOT/'examples/notes.json');c2=dict(c1,versionCode=2,versionName='0.1.1');cl=load(ROOT/'examples/library.json')
n1=f/'notes-release/app-0.1.0.apk';n2=f/'notes-final-release/app-0.1.1.apk';lib=f/'library-final-release/app-1.2.3.apk'
with tempfile.TemporaryDirectory(prefix='webapp-real-release-') as tmp:
 d=Path(tmp)/'releases';m1=publish(n1,d,c1);assert publish(n1,d,c1)==m1
 before=(d/'app.apk.json').read_bytes()
 try:publish(lib,d,cl);raise AssertionError('Foreign package/signer accepted')
 except ValueError:pass
 assert (d/'app.apk.json').read_bytes()==before
 m2=publish(n2,d,c2);assert m2['versionCode']==2 and inspect(n1)[1]==inspect(n2)[1]
 try:publish(n1,d,c1);raise AssertionError('Downgrade accepted')
 except ValueError:pass
 assert json.loads((d/'app.apk.json').read_text())==m2
 # Re-sign the valid new APK with the other fixture key; identity/version remain the same.
 wrong=Path(tmp)/'wrong.apk';sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0/apksigner';key=f/'library-signing'
 subprocess.run([str(sdk),'sign','--ks',str(key/'release.jks'),'--ks-pass','file:'+str(key/'password'),'--ks-key-alias','library-release','--out',str(wrong),str(n2)],check=True,capture_output=True)
 assert inspect(wrong)[0]['packageName']==c2['applicationId'] and inspect(wrong)[1]!=inspect(n2)[1]
 try:publish(wrong,d,c2);raise AssertionError('Foreign signer accepted')
 except ValueError:pass
 # Use the actual shipped server to verify both stable and version-pinned downloads.
 cfg=Path(tmp)/'app.json';cfg.write_text(json.dumps(c2))
 command=['node',str(ROOT/'skills/readiz-webapp-android/assets/starter/server/server.mjs'),'--config',str(cfg),'--releases',str(d),'--port','0']
 server=subprocess.Popen(command,stdout=subprocess.PIPE,text=True)
 try:
  line=server.stdout.readline().strip();base=line.split('APK update server: ',1)[1]
  result=verify(base,m2)
  import urllib.request
  old=urllib.request.urlopen(base+'app.apk?version=0.1.0&sha256='+m1['sha256']).read()
  assert hashlib.sha256(old).hexdigest()==m1['sha256']
  print(json.dumps(dict(**result,checks=['signed publish','idempotence','package isolation','same-key upgrade','downgrade refused','foreign signer refused','live metadata and 2 APK paths','retained prior version'])))
 finally:server.terminate();server.wait(timeout=10)
