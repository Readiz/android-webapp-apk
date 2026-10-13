#!/usr/bin/env python3
"""Test, lint, build and inspect a release APK. Does not publish it."""
import argparse,os,shutil,subprocess
from pathlib import Path
from config import load,encoded
from release_lib import inspect,check_identity,atomic,outside_project
ROOT=Path(__file__).resolve().parents[1]
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);a=p.parse_args()
    try:
        cfg=load(ROOT/'app.json');out=outside_project(a.output,ROOT)
        if out.exists():raise ValueError('Use a fresh output directory')
        signing=outside_project(os.environ.get('WEBAPP_SIGNING_DIR',str(Path.home()/'.config/android-webapp-apk'/cfg['applicationId']/'signing')),ROOT)
        if not all((signing/n).is_file() for n in ('release.jks','password')):raise ValueError('Signing key missing. Restore the original; for a NEW app use tools/init_signing.py --new-app')
        if shutil.disk_usage(ROOT).free<2*1024**3:raise ValueError('Keep at least 2 GiB free before an Android build')
        gradle=Path(os.environ['GRADLE_HOME'])/'bin/gradle';env=dict(os.environ,WEBAPP_SIGNING_DIR=str(signing))
        subprocess.run([str(gradle),'-p',str(ROOT/'android'),'--no-daemon',':app:testDebugUnitTest',':app:lintRelease',':app:assembleRelease'],env=env,check=True)
        apk=ROOT/'android/app/build/outputs/apk/release/app-release.apk';metadata,cert=inspect(apk);check_identity(metadata,cfg)
        out.mkdir(parents=True,mode=0o700)
        name='app-'+metadata['versionName']+'.apk';atomic(out/name,apk.read_bytes());atomic(out/(name+'.json'),encoded(metadata))
        atomic(out/'verification.json',encoded(dict(**metadata,certificateSha256=cert)))
        print('Verified release:',out/name);print('Certificate SHA-256:',cert)
    except (ValueError,OSError,KeyError,subprocess.CalledProcessError) as e:p.exit(1,str(e)+'\n')
