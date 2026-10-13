#!/usr/bin/env python3
"""Create an independent project. Never overwrite an existing destination."""
import argparse,importlib.util,json,os,shutil,struct,tempfile
from pathlib import Path
STARTER=Path(__file__).resolve().parents[1]/'assets/starter'
spec=importlib.util.spec_from_file_location('config',STARTER/'tools/config.py');config=importlib.util.module_from_spec(spec);spec.loader.exec_module(config)
def create(config_file,destination,icon=None):
    cfg=config.load(config_file);out=Path(destination).expanduser().absolute()
    if out.exists() or out.is_symlink():raise ValueError('Destination exists; choose a new project directory')
    if out==STARTER or STARTER in out.parents:raise ValueError('Do not generate inside the starter')
    png=None
    if icon:
        p=Path(icon)
        if not p.is_file() or p.stat().st_size>1024*1024:raise ValueError('Use a PNG icon up to 1 MiB')
        png=p.read_bytes()
        if len(png)<24 or png[:8]!=b'\x89PNG\r\n\x1a\n':raise ValueError('Invalid PNG icon')
        width,height=struct.unpack('>II',png[16:24])
        if width!=height or not 48<=width<=1024:raise ValueError('Use a square PNG, 48 to 1024 pixels')
    out.parent.mkdir(parents=True,exist_ok=True)
    stage=Path(tempfile.mkdtemp(prefix='.'+out.name+'-',dir=out.parent))
    try:
        shutil.copytree(STARTER,stage,dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc','.gradle','build','local.properties','*.apk','*.jks'))
        (stage/'app.json').write_bytes(config.encoded(cfg))
        (stage/'docs').mkdir(exist_ok=True)
        shutil.copyfile(STARTER.parents[1]/'references/signing.md',stage/'docs/signing.md')
        if png:
            target=stage/'android/app/src/main/res/drawable';(target/'icon.xml').unlink();(target/'icon.png').write_bytes(png)
        # Rename only after all validation/copying; do not replace nonempty projects.
        if out.exists():raise ValueError('Destination appeared during generation')
        stage.rename(out)
    finally:
        if stage.exists():shutil.rmtree(stage)
    return out
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',required=True);p.add_argument('--out',required=True);p.add_argument('--icon')
    a=p.parse_args()
    try:print(create(a.config,a.out,a.icon))
    except (ValueError,OSError) as e:p.exit(1,str(e)+'\n')
