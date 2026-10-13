#!/usr/bin/env python3
"""Verify public metadata plus latest and version-pinned APK; no login or install."""
import argparse,hashlib,json,urllib.request
from config import load
from release_lib import MAX_APK
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args):return None

def verify(base,expected):
    opener=urllib.request.build_opener(NoRedirect())
    def get(path,limit):
        with opener.open(base+path,timeout=15) as r:
            if r.status!=200 or 'no-store' not in r.headers.get('Cache-Control',''):raise ValueError('Expected HTTP 200 and no-store')
            body=r.read(limit+1)
            if len(body)>limit:raise ValueError('Response too large')
            return body
    metadata=json.loads(get('app-version',4096))
    if metadata!=expected:raise ValueError('Remote metadata differs from the inspected release')
    for route in ('app.apk','app.apk?version='+metadata['versionName']+'&sha256='+metadata['sha256']):
        data=get(route,MAX_APK)
        if len(data)!=metadata['size'] or hashlib.sha256(data).hexdigest()!=metadata['sha256']:raise ValueError('Remote APK differs')
    return dict(version=metadata['versionName'],sha256=metadata['sha256'],verifiedDownloads=2)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--manifest',required=True);a=p.parse_args()
    try:
        from pathlib import Path
        cfg=load(Path(__file__).resolve().parents[1]/'app.json')
        print(json.dumps(verify(cfg['updateBaseUrl'],json.loads(Path(a.manifest).read_text())),sort_keys=True))
    except Exception as e:p.exit(1,str(e)+'\n')
