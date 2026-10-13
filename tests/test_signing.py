import importlib.util,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];TOOLS=ROOT/'skills/readiz-webapp-android/assets/starter/tools'
sys.path.insert(0,str(TOOLS))
import init_signing
from config import load
class SigningTests(unittest.TestCase):
 def setUp(self):self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.cfg=load(ROOT/'examples/notes.json')
 def test_existing_signing_directory_is_preserved(self):
  dest=self.root/'signing';dest.mkdir();(dest/'release.jks').write_bytes(b'original')
  with self.assertRaises(ValueError):init_signing.initialize(self.cfg,dest,'must-not-execute')
  self.assertEqual((dest/'release.jks').read_bytes(),b'original');self.assertFalse((dest/'password').exists())
 def test_failed_key_generation_leaves_no_partial_credentials(self):
  dest=self.root/'signing'
  with self.assertRaises(OSError):init_signing.initialize(self.cfg,dest,str(self.root/'missing-keytool'))
  self.assertFalse(dest.exists());self.assertEqual(list(self.root.iterdir()),[])
if __name__=='__main__':unittest.main()
