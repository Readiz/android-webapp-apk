"""Bounded APK inspection and durable metadata writes; no credentials in output."""
import hashlib,json,os,re,subprocess,tempfile
from pathlib import Path
MAX_APK=16*1024*1024

def run(args,**kwargs):return subprocess.run([str(x) for x in args],check=True,text=True,capture_output=True,**kwargs).stdout

def sdk_tool(name):
    root=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not root:raise ValueError('Set ANDROID_HOME to the SDK with build-tools 35.0.0 and platform 36')
    p=Path(root)/'build-tools/35.0.0'/name
    if not p.is_file():raise ValueError('Missing SDK tool: '+str(p))
    return p

def inspect(apk):
    apk=Path(apk)
    if apk.is_symlink() or not apk.is_file() or not 0<apk.stat().st_size<=MAX_APK:raise ValueError('Expected a regular APK up to 16 MiB')
    cert=run([sdk_tool('apksigner'),'verify','--print-certs',apk])
    fingerprints=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$',cert,re.M)
    if len(fingerprints)!=1:raise ValueError('Expected a single APK signing certificate')
    badging=run([sdk_tool('aapt'),'dump','badging',apk])
    m=re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",badging,re.M)
    if not m:raise ValueError('Cannot inspect APK package/version')
    data=apk.read_bytes()
    return dict(packageName=m[1],versionCode=int(m[2]),versionName=m[3],size=len(data),sha256=hashlib.sha256(data).hexdigest()),fingerprints[0].lower()

def check_identity(metadata,cfg):
    if any(metadata[k]!=cfg[v] for k,v in [('packageName','applicationId'),('versionCode','versionCode'),('versionName','versionName')]):raise ValueError('APK does not match app.json')

def atomic(path,data):
    path=Path(path);fd,name=tempfile.mkstemp(prefix='.'+path.name+'-',dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as out:out.write(data);out.flush();os.fsync(out.fileno())
        os.replace(name,path)
    finally:
        if os.path.exists(name):os.unlink(name)

def outside_project(path,project):
    path=Path(path).expanduser().resolve();project=Path(project).resolve()
    if path==project or project in path.parents:raise ValueError('Keep keys and release output outside the project')
    return path
