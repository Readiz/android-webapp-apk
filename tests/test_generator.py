import importlib.util,json,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
P=ROOT/'skills/readiz-webapp-android/scripts/create_app.py'
spec=importlib.util.spec_from_file_location('generator',P);g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
class GeneratorTests(unittest.TestCase):
 def setUp(self):self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
 def config(self,patch=None):
  c=json.loads((ROOT/'examples/notes.json').read_text());c.update(patch or {});p=self.root/'config.json';p.write_text(json.dumps(c));return p
 def test_two_apps_are_independent_and_portable(self):
  for name in ('notes','library'):
   out=g.create(ROOT/f'examples/{name}.json',self.root/name)
   self.assertEqual(json.loads((out/'app.json').read_text())['applicationId'],f'com.readiz.example.{name}')
   self.assertTrue((out/'tools/build.py').is_file());self.assertTrue((out/'server/update-handler.mjs').is_file())
   for p in out.rglob('*'):
    if p.is_file():
     self.assertNotIn(p.suffix,('.apk','.jks','.pyc'))
     self.assertNotIn('aoa.readiz.com',p.read_text());self.assertNotIn('invest.readiz.com',p.read_text())
 def test_existing_project_is_never_overwritten(self):
  p=self.root/'app';p.mkdir();(p/'mine').write_text('keep')
  with self.assertRaises(ValueError):g.create(self.config(),p)
  self.assertEqual((p/'mine').read_text(),'keep')
 def test_bad_config_has_no_output(self):
  for patch in ({'applicationId':"com.x';exec"},{'versionCode':True},{'versionCode':1.1},{'homeUrl':'http://notes.example.com/app/home'},{'homeUrl':'https://user@notes.example.com/app/home'},{'homeUrl':'https://notes.example.com/application'},{'internalUrls':['https://notes.example.com/app/']},{'updateBaseUrl':'https://updates.example.com/notes/?secret=x'},{'homeUrl':'https://notes.example.com/app/%252e%252e/private'},{'appName':'invalid<xml>'}):
   with self.subTest(patch=patch),self.assertRaises((ValueError,TypeError)):g.create(self.config(patch),self.root/'bad')
   self.assertFalse((self.root/'bad').exists())
 def test_invalid_icon_does_not_create_project(self):
  icon=self.root/'bad.png';icon.write_text('not a png')
  with self.assertRaises(ValueError):g.create(self.config(),self.root/'app',icon)
  self.assertFalse((self.root/'app').exists())
if __name__=='__main__':unittest.main()
