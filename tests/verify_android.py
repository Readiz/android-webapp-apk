#!/usr/bin/env python3
"""Compile, test and lint two disposable apps without signing or building APKs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SKILL = ROOT / 'skills/readiz-webapp-android'
STARTER = SKILL / 'assets/starter'
TASKS = [':app:testDebugUnitTest', ':app:compileReleaseJavaWithJavac', ':app:lintRelease']


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def collect_reports(project, report):
    build = project / 'android/app/build'
    junit = report / 'junit'
    junit.mkdir(exist_ok=True)
    for path in (build / 'test-results/testDebugUnitTest').glob('TEST-*.xml'):
        shutil.copyfile(path, junit / path.name)
    for name in ('lint-results-release.xml', 'lint-results-release.html'):
        path = build / 'reports' / name
        if path.is_file():
            shutil.copyfile(path, report / name)


def check_reports(report):
    suites = [ET.parse(path).getroot() for path in (report / 'junit').glob('TEST-*.xml')]
    totals = {key: sum(int(suite.get(key, '0')) for suite in suites)
              for key in ('tests', 'failures', 'errors', 'skipped')}
    if not totals['tests'] or any(totals[key] for key in ('failures', 'errors', 'skipped')):
        raise RuntimeError(f'{report.name}: unexpected JUnit result: {totals}')
    lint = ET.parse(report / 'lint-results-release.xml').getroot()
    if any(issue.get('severity') in ('Error', 'Fatal') for issue in lint.findall('issue')):
        raise RuntimeError(f'{report.name}: release lint reported an error')
    print(f'{report.name}: {totals["tests"]} tests passed; release lint errors 0', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True, help='New disposable output directory')
    parser.add_argument('--prepare-only', action='store_true', help='Generate fixtures and identity reports only')
    args = parser.parse_args()
    out = args.out.expanduser().absolute()
    out.mkdir(parents=True, exist_ok=False)
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    projects = []
    for name in ('notes', 'library'):
        project = out / name
        subprocess.run([sys.executable, '-B', str(SKILL / 'scripts/create_app.py'),
                        '--config', str(ROOT / 'examples' / (name + '.json')),
                        '--out', str(project)], check=True)
        report = out / 'reports' / name
        report.mkdir(parents=True)
        shutil.copyfile(project / 'app.json', report / 'config.json')
        hashes = {}
        for source in sorted(STARTER.rglob('*.java')):
            relative = source.relative_to(STARTER)
            if source.read_bytes() != (project / relative).read_bytes():
                raise RuntimeError(f'Generated Java differs from template: {relative}')
            hashes[str(relative)] = sha256(source)
        identity = {'sourceCommit': commit, 'configSha256': sha256(project / 'app.json'),
                    'templateJavaSha256': hashes, 'tasks': TASKS}
        (report / 'source.json').write_text(json.dumps(identity, indent=2) + '\n')
        projects.append((project, report))
    if args.prepare_only:
        print('Prepared both fixtures; Android compilation/tests/lint were not run.', flush=True)
        return
    env = dict(os.environ, WEBAPP_SIGNING_DIR=str(out / 'unused-signing'))
    for project, report in projects:
        try:
            subprocess.run(['gradle', '-p', str(project / 'android'), '--no-daemon',
                            '--max-workers=1', '--console=plain', '--stacktrace', *TASKS],
                           env=env, check=True)
        finally:
            collect_reports(project, report)
        check_reports(report)


if __name__ == '__main__':
    main()
