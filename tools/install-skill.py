#!/usr/bin/env python3
"""Link this repository's self-contained skill into local Codex discovery."""
import os
from pathlib import Path
source=Path(__file__).resolve().parents[1]/'skills/readiz-webapp-android'
base=Path(os.environ.get('CODEX_HOME',str(Path.home()/'.codex')))/'skills'
target=base/source.name
if target.exists() or target.is_symlink():
    if target.resolve()!=source:raise SystemExit('Existing skill is preserved: '+str(target))
else:
    base.mkdir(parents=True,exist_ok=True);target.symlink_to(source,target_is_directory=True)
print(target)
