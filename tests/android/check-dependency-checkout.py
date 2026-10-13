"""Read-only validation of nested Git checkouts; never repairs/reset local sources."""
from pathlib import Path
import subprocess, sys, tempfile, unittest

def git(repo,*args):return subprocess.check_output(['git','-C',str(repo),*args],stderr=subprocess.STDOUT)
def verify(repo):
    repo=Path(repo).resolve();count=0
    for entry in git(repo,'ls-tree','-r','-z','HEAD').split(b'\0'):
        if not entry:continue
        meta,name=entry.split(b'\t',1);mode,kind,pin=meta.split()
        if mode!=b'160000':continue
        path=repo/name.decode('utf-8');resolved=path.resolve()
        if not resolved.is_relative_to(repo):raise ValueError('Submodule path escapes its parent')
        try:
            top=Path(git(path,'rev-parse','--show-toplevel').decode().strip()).resolve()
            head=git(path,'rev-parse','HEAD').strip()
        except subprocess.CalledProcessError as failure:raise ValueError('Uninitialized submodule: '+str(path)) from failure
        if top!=resolved or head!=pin:raise ValueError('Submodule is not checked out at its pin: '+str(path))
        missing=git(path,'ls-files','--deleted').decode().strip()
        if missing:raise ValueError('Incomplete submodule working tree: '+str(path)+'; missing '+missing.splitlines()[0])
        count+=1+verify(path)
    if (repo/'.gitmodules').is_file() and 'jlatexmath' in (repo/'.gitmodules').read_text(encoding='utf-8'):
        required=repo/'TMessagesProj/lib/jlatexmath/jlatexmath/build.gradle'
        if not required.is_file():raise ValueError('jlatexmath Android build file is missing')
    return count

class CheckoutTests(unittest.TestCase):
    def test_metadata_only_clone_is_rejected_without_modifying_it(self):
        with tempfile.TemporaryDirectory() as folder:
            parent=Path(folder);child=parent/'dependency';child.mkdir()
            for repo in [parent,child]:
                git(repo,'init','--quiet');git(repo,'config','user.name','Test');git(repo,'config','user.email','test@local')
            source=child/'build.gradle';source.write_text('library\n')
            git(child,'add','build.gradle');git(child,'commit','--quiet','-m','Dependency')
            pin=git(child,'rev-parse','HEAD').decode().strip()
            git(parent,'update-index','--add','--cacheinfo','160000,'+pin+',dependency');git(parent,'commit','--quiet','-m','Pin')
            self.assertEqual(verify(parent),1)
            source.unlink() # Reproduce the interrupted-clone state in this owned fixture.
            with self.assertRaisesRegex(ValueError,'Incomplete submodule'):verify(parent)
            self.assertFalse(source.exists());self.assertEqual(git(child,'rev-parse','HEAD').decode().strip(),pin)
            source.write_text('library\n');self.assertEqual(verify(parent),1)

if __name__=='__main__':
    if len(sys.argv)>1 and sys.argv[1]!='--test':print('Verified complete pinned nested checkouts:',verify(Path(sys.argv[1])))
    else:unittest.main(argv=[sys.argv[0]])
