#!/usr/bin/env python3
"""Publish a verified APK; immutable version first, latest metadata last."""
import argparse,json,os,re,subprocess
from pathlib import Path
from config import load,encoded,VERSION
from release_lib import inspect,check_identity,atomic,outside_project
ROOT=Path(__file__).resolve().parents[1]
def publish(apk,directory,cfg):
    directory=outside_project(directory,ROOT);metadata,cert=inspect(apk);check_identity(metadata,cfg)
    directory.mkdir(parents=True,exist_ok=True,mode=0o700)
    lock=directory/'.publish-lock'
    try:fd=os.open(lock,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
    except FileExistsError:raise ValueError('Publication already active; inspect a stale .publish-lock before manually removing it')
    try:
        os.write(fd,str(os.getpid()).encode());os.close(fd)
        pointer=directory/'app.apk.json'
        if pointer.exists():
            previous=json.loads(pointer.read_text());prior=directory/('app-'+previous['versionName']+'.apk')
            actual,oldcert=inspect(prior)
            if actual!=previous:raise ValueError('Existing publication does not verify')
            if oldcert!=cert or actual['packageName']!=metadata['packageName']:raise ValueError('Signing certificate or application identity changed')
            if metadata['versionCode']<=actual['versionCode'] and metadata!=actual:raise ValueError('Increase versionCode and use a fresh versionName')
        file=directory/('app-'+metadata['versionName']+'.apk')
        if file.exists() and file.read_bytes()!=Path(apk).read_bytes():raise ValueError('Version already exists with different APK bytes')
        if not file.exists():atomic(file,Path(apk).read_bytes())
        checked,signer=inspect(file)
        if checked!=metadata or signer!=cert:raise ValueError('Published APK readback failed')
        atomic(Path(str(file)+'.json'),encoded(metadata))
        atomic(pointer,encoded(metadata))
        return metadata
    finally:
        lock.unlink(missing_ok=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',required=True);p.add_argument('--releases',required=True);a=p.parse_args()
    try:print(json.dumps(publish(a.apk,a.releases,load(ROOT/'app.json')),sort_keys=True))
    except (ValueError,OSError,KeyError,subprocess.CalledProcessError) as e:p.exit(1,str(e)+'\n')
