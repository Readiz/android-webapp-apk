#!/usr/bin/env python3
"""Explicit first-install key creation. Never invoked by the build command."""
import argparse,os,secrets,shutil,subprocess,tempfile
from pathlib import Path
from config import load
from release_lib import outside_project,run
ROOT=Path(__file__).resolve().parents[1]
def initialize(cfg,destination,keytool):
    dest=outside_project(destination,ROOT)
    if dest.exists():raise ValueError('Signing directory already exists; restore the original key instead of replacing it')
    dest.parent.mkdir(parents=True,exist_ok=True)
    stage=Path(tempfile.mkdtemp(prefix='.signing-',dir=dest.parent));stage.chmod(0o700)
    try:
        password=stage/'password';password.write_text(secrets.token_urlsafe(36)+'\n');password.chmod(0o600)
        run([keytool,'-genkeypair','-noprompt','-keystore',stage/'release.jks','-storetype','PKCS12','-storepass:file',password,'-keypass:file',password,'-alias',cfg['signingAlias'],'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN='+cfg['applicationId']])
        (stage/'release.jks').chmod(0o600)
        # Never print passwords or key bytes.
        certificate=run([keytool,'-exportcert','-keystore',stage/'release.jks','-storepass:file',password,'-alias',cfg['signingAlias'],'-rfc'])
        (stage/'certificate.pem').write_text(certificate)
        stage.rename(dest)
    finally:
        if stage.exists():shutil.rmtree(stage)
    return dest
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--new-app',action='store_true',required=True,help='Explicitly confirm this is a new application identity');p.add_argument('--signing-dir');a=p.parse_args()
    try:
        c=load(ROOT/'app.json');target=a.signing_dir or os.environ.get('WEBAPP_SIGNING_DIR') or str(Path.home()/'.config/android-webapp-apk'/c['applicationId']/'signing')
        java=os.environ.get('JAVA_HOME');keytool=Path(java)/'bin/keytool' if java else 'keytool'
        print('Signing directory:',initialize(c,target,keytool))
    except (ValueError,OSError,subprocess.CalledProcessError) as e:p.exit(1,str(e)+'\n')
